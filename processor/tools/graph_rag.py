"""
Graph-RAG — Neo4j integration for lesson indexing and retrieval.

Schema
------
Nodes:
  (:Lesson {number, title, processed_at})
  (:Concept {name, definition, normalized_key, confidence})
  (:Chunk {id, text, position, lesson_number, embedding})
  (:Fact {text, lesson_number})

Relationships:
  (:Lesson)-[:HAS_CONCEPT]->(:Concept)
  (:Lesson)-[:HAS_CHUNK]->(:Chunk)
  (:Lesson)-[:HAS_FACT]->(:Fact)
  (:Concept)-[:RELATES_TO {label, description}]->(:Concept)
  (:Chunk)-[:MENTIONS]->(:Concept)

Retrieval strategy (graph-first):
  1. Graph traversal: concept → related concepts → chunks
  2. Full-text search on chunk text (lexical fallback)
  3. Vector similarity on chunk embeddings (embedding fallback)
"""
from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

log = logging.getLogger(__name__)


@dataclass
class GraphConfig:
    uri: str = "bolt://localhost:7687"
    username: str = "neo4j"
    password: str = "password"
    database: str = "neo4j"


def connect(cfg: GraphConfig) -> Any:
    """Create a Neo4j driver instance."""
    from neo4j import GraphDatabase  # type: ignore[import-untyped]

    driver = GraphDatabase.driver(cfg.uri, auth=(cfg.username, cfg.password))
    driver.verify_connectivity()
    log.info("Connected to Neo4j at %s", cfg.uri)
    return driver


def ensure_indexes(driver: Any) -> None:
    """Create constraints and indexes if they don't exist."""
    queries = [
        "CREATE CONSTRAINT lesson_number IF NOT EXISTS FOR (l:Lesson) REQUIRE l.number IS UNIQUE",
        "CREATE CONSTRAINT concept_name IF NOT EXISTS FOR (c:Concept) REQUIRE c.name IS UNIQUE",
        "CREATE CONSTRAINT chunk_id IF NOT EXISTS FOR (ch:Chunk) REQUIRE ch.id IS UNIQUE",
        "CREATE INDEX chunk_lesson IF NOT EXISTS FOR (ch:Chunk) ON (ch.lesson_number)",
        "CREATE INDEX fact_lesson IF NOT EXISTS FOR (f:Fact) ON (f.lesson_number)",
        # Full-text index for lexical search
        """
        CREATE FULLTEXT INDEX chunk_text IF NOT EXISTS
        FOR (ch:Chunk) ON EACH [ch.text]
        """,
    ]
    with driver.session() as session:
        for q in queries:
            session.run(q.strip())
    log.info("Neo4j indexes ensured")


def _try_create_vector_index(driver: Any, dimensions: int = 1536) -> bool:
    """Attempt to create a vector index for chunk embeddings. Returns True on success."""
    try:
        with driver.session() as session:
            session.run(
                """
                CREATE VECTOR INDEX chunk_embedding IF NOT EXISTS
                FOR (ch:Chunk) ON (ch.embedding)
                OPTIONS {indexConfig: {
                    `vector.dimensions`: $dims,
                    `vector.similarity_function`: 'cosine'
                }}
                """,
                dims=dimensions,
            )
        log.info("Vector index created (dimensions=%d)", dimensions)
        return True
    except Exception:
        log.debug("Vector index creation skipped (not supported or already exists)", exc_info=True)
        return False


# ── Indexing ────────────────────────────────────────────────────────


def index_lesson(
    driver: Any,
    artifact: Any,
    chunks: list[Any],
    embeddings: list[list[float]] | None = None,
) -> dict[str, int]:
    """Index a LessonArtifact into the Neo4j graph.

    Args:
        driver: Neo4j driver.
        artifact: LessonArtifact instance.
        chunks: List of Chunk objects from the chunker.
        embeddings: Optional embedding vectors aligned with chunks.

    Returns:
        Dict with counts: {lessons, concepts, chunks, facts, relationships}.
    """
    stats: dict[str, int] = {
        "lessons": 0, "concepts": 0, "chunks": 0,
        "facts": 0, "relationships": 0,
    }

    with driver.session() as session:
        # Lesson node
        session.run(
            """
            MERGE (l:Lesson {number: $number})
            SET l.title = $title, l.processed_at = $processed_at
            """,
            number=artifact.lesson_number,
            title=artifact.title,
            processed_at=artifact.processed_at,
        )
        stats["lessons"] = 1

        # Concepts from summary
        if artifact.summary:
            for concept in artifact.summary.key_concepts:
                from processor.tools.concept_normalizer import dedupe_key
                session.run(
                    """
                    MERGE (c:Concept {name: $name})
                    SET c.definition = $definition,
                        c.normalized_key = $normalized_key
                    MERGE (l:Lesson {number: $lesson})
                    MERGE (l)-[:HAS_CONCEPT]->(c)
                    """,
                    name=concept.name,
                    definition=concept.definition,
                    normalized_key=dedupe_key(concept.name),
                    lesson=artifact.lesson_number,
                )
                stats["concepts"] += 1

            # Facts
            for fact_text in artifact.summary.key_facts:
                session.run(
                    """
                    MERGE (l:Lesson {number: $lesson})
                    CREATE (f:Fact {text: $text, lesson_number: $lesson})
                    MERGE (l)-[:HAS_FACT]->(f)
                    """,
                    text=fact_text,
                    lesson=artifact.lesson_number,
                )
                stats["facts"] += 1

        # Concept map relationships
        if artifact.concept_map:
            node_labels = {n.id: n.label for n in artifact.concept_map.nodes}
            for rel in artifact.concept_map.relationships:
                src_label = node_labels.get(rel.source_id, rel.source_id)
                tgt_label = node_labels.get(rel.target_id, rel.target_id)
                session.run(
                    """
                    MERGE (src:Concept {name: $src_name})
                    MERGE (tgt:Concept {name: $tgt_name})
                    MERGE (src)-[r:RELATES_TO]->(tgt)
                    SET r.label = $label, r.description = $description
                    """,
                    src_name=src_label,
                    tgt_name=tgt_label,
                    label=rel.label,
                    description=rel.description,
                )
                stats["relationships"] += 1

        # Chunks
        for i, chunk in enumerate(chunks):
            embedding = embeddings[i] if embeddings and i < len(embeddings) else None
            params: dict[str, Any] = {
                "id": chunk.id,
                "text": chunk.text,
                "position": chunk.position,
                "lesson": chunk.lesson_number,
            }
            if embedding:
                params["embedding"] = embedding
                session.run(
                    """
                    MERGE (ch:Chunk {id: $id})
                    SET ch.text = $text, ch.position = $position,
                        ch.lesson_number = $lesson, ch.embedding = $embedding
                    MERGE (l:Lesson {number: $lesson})
                    MERGE (l)-[:HAS_CHUNK]->(ch)
                    """,
                    **params,
                )
            else:
                session.run(
                    """
                    MERGE (ch:Chunk {id: $id})
                    SET ch.text = $text, ch.position = $position,
                        ch.lesson_number = $lesson
                    MERGE (l:Lesson {number: $lesson})
                    MERGE (l)-[:HAS_CHUNK]->(ch)
                    """,
                    **params,
                )
            stats["chunks"] += 1

            # Link chunks to concepts they mention
            if artifact.summary:
                for concept in artifact.summary.key_concepts:
                    if concept.name.lower() in chunk.text.lower():
                        session.run(
                            """
                            MATCH (ch:Chunk {id: $chunk_id})
                            MERGE (c:Concept {name: $concept_name})
                            MERGE (ch)-[:MENTIONS]->(c)
                            """,
                            chunk_id=chunk.id,
                            concept_name=concept.name,
                        )

    log.info(
        "Indexed lesson %s: %d concepts, %d chunks, %d facts, %d relationships",
        artifact.lesson_number,
        stats["concepts"], stats["chunks"], stats["facts"], stats["relationships"],
    )
    return stats


def clear_lesson(driver: Any, lesson_number: str) -> None:
    """Remove all data for a lesson (for re-indexing)."""
    with driver.session() as session:
        session.run(
            """
            MATCH (l:Lesson {number: $lesson})
            OPTIONAL MATCH (l)-[:HAS_CHUNK]->(ch:Chunk)
            OPTIONAL MATCH (l)-[:HAS_FACT]->(f:Fact)
            DETACH DELETE ch, f
            """,
            lesson=lesson_number,
        )
        # Don't delete concepts — they may be shared across lessons
        # But remove HAS_CONCEPT edges for this lesson
        session.run(
            """
            MATCH (l:Lesson {number: $lesson})-[r:HAS_CONCEPT]->(c:Concept)
            DELETE r
            """,
            lesson=lesson_number,
        )
        log.info("Cleared graph data for lesson %s", lesson_number)


# ── Retrieval ───────────────────────────────────────────────────────


@dataclass
class RetrievalResult:
    chunks: list[dict[str, Any]]
    concepts: list[dict[str, Any]]
    facts: list[str]
    source_lessons: list[str]


def retrieve_by_concept(
    driver: Any,
    concept_name: str,
    *,
    max_chunks: int = 10,
    max_hops: int = 2,
) -> RetrievalResult:
    """Graph traversal retrieval: find concept, traverse relations, gather chunks."""
    concepts: list[dict[str, Any]] = []
    chunks: list[dict[str, Any]] = []
    facts: list[str] = []
    lessons: set[str] = set()

    with driver.session() as session:
        # Find concept and related concepts (up to max_hops)
        result = session.run(
            """
            MATCH (c:Concept)
            WHERE toLower(c.name) CONTAINS toLower($name)
            OPTIONAL MATCH path = (c)-[:RELATES_TO*1..""" + str(max_hops) + """]->(related:Concept)
            WITH c, collect(DISTINCT related) AS related_list
            RETURN c {.name, .definition} AS concept,
                   [r IN related_list | r {.name, .definition}] AS related
            LIMIT 5
            """,
            name=concept_name,
        )
        for record in result:
            concept = record["concept"]
            concepts.append(dict(concept))
            for rel in record["related"]:
                concepts.append(dict(rel))

        if not concepts:
            return RetrievalResult(chunks=[], concepts=[], facts=[], source_lessons=[])

        # Get chunks mentioning these concepts
        concept_names = list({c["name"] for c in concepts})
        result = session.run(
            """
            UNWIND $names AS concept_name
            MATCH (ch:Chunk)-[:MENTIONS]->(c:Concept {name: concept_name})
            RETURN DISTINCT ch.id AS id, ch.text AS text,
                   ch.lesson_number AS lesson, ch.position AS position
            ORDER BY ch.position
            LIMIT $limit
            """,
            names=concept_names,
            limit=max_chunks,
        )
        for record in result:
            chunks.append({
                "id": record["id"],
                "text": record["text"],
                "lesson_number": record["lesson"],
                "position": record["position"],
            })
            lessons.add(record["lesson"])

        # Get facts from same lessons
        if lessons:
            result = session.run(
                """
                MATCH (f:Fact)
                WHERE f.lesson_number IN $lessons
                RETURN f.text AS text
                """,
                lessons=list(lessons),
            )
            facts = [record["text"] for record in result]

    return RetrievalResult(
        chunks=chunks,
        concepts=concepts,
        facts=facts,
        source_lessons=sorted(lessons),
    )


def retrieve_by_text(
    driver: Any,
    query: str,
    *,
    max_results: int = 10,
) -> RetrievalResult:
    """Lexical retrieval: full-text search on chunk text."""
    chunks: list[dict[str, Any]] = []
    lessons: set[str] = set()

    # Escape Lucene special characters for full-text search
    escaped = _escape_lucene(query)
    if not escaped.strip():
        return RetrievalResult(chunks=[], concepts=[], facts=[], source_lessons=[])

    with driver.session() as session:
        cypher = """
            CALL db.index.fulltext.queryNodes('chunk_text', $fts_query)
            YIELD node, score
            RETURN node.id AS id, node.text AS text,
                   node.lesson_number AS lesson, node.position AS position,
                   score
            ORDER BY score DESC
            LIMIT $limit
            """
        result = session.run(cypher, fts_query=escaped, limit=max_results)
        for record in result:
            chunks.append({
                "id": record["id"],
                "text": record["text"],
                "lesson_number": record["lesson"],
                "position": record["position"],
                "score": record["score"],
            })
            lessons.add(record["lesson"])

        # Find concepts mentioned in retrieved chunks
        chunk_ids = [c["id"] for c in chunks]
        concepts: list[dict[str, Any]] = []
        if chunk_ids:
            result = session.run(
                """
                UNWIND $ids AS chunk_id
                MATCH (ch:Chunk {id: chunk_id})-[:MENTIONS]->(c:Concept)
                RETURN DISTINCT c.name AS name, c.definition AS definition
                """,
                ids=chunk_ids,
            )
            concepts = [{"name": r["name"], "definition": r["definition"]} for r in result]

        # Facts from same lessons
        facts: list[str] = []
        if lessons:
            result = session.run(
                """
                MATCH (f:Fact)
                WHERE f.lesson_number IN $lessons
                RETURN f.text AS text
                """,
                lessons=list(lessons),
            )
            facts = [record["text"] for record in result]

    return RetrievalResult(
        chunks=chunks,
        concepts=concepts,
        facts=facts,
        source_lessons=sorted(lessons),
    )


def retrieve_by_embedding(
    driver: Any,
    query_embedding: list[float],
    *,
    max_results: int = 10,
) -> RetrievalResult:
    """Vector similarity retrieval on chunk embeddings."""
    chunks: list[dict[str, Any]] = []
    lessons: set[str] = set()

    with driver.session() as session:
        try:
            result = session.run(
                """
                CALL db.index.vector.queryNodes('chunk_embedding', $k, $embedding)
                YIELD node, score
                RETURN node.id AS id, node.text AS text,
                       node.lesson_number AS lesson, node.position AS position,
                       score
                ORDER BY score DESC
                """,
                k=max_results,
                embedding=query_embedding,
            )
            for record in result:
                chunks.append({
                    "id": record["id"],
                    "text": record["text"],
                    "lesson_number": record["lesson"],
                    "position": record["position"],
                    "score": record["score"],
                })
                lessons.add(record["lesson"])
        except Exception:
            log.debug("Vector search failed (index may not exist)", exc_info=True)
            return RetrievalResult(chunks=[], concepts=[], facts=[], source_lessons=[])

        # Concepts from retrieved chunks
        chunk_ids = [c["id"] for c in chunks]
        concepts: list[dict[str, Any]] = []
        if chunk_ids:
            result = session.run(
                """
                UNWIND $ids AS chunk_id
                MATCH (ch:Chunk {id: chunk_id})-[:MENTIONS]->(c:Concept)
                RETURN DISTINCT c.name AS name, c.definition AS definition
                """,
                ids=chunk_ids,
            )
            concepts = [{"name": r["name"], "definition": r["definition"]} for r in result]

        facts: list[str] = []
        if lessons:
            result = session.run(
                """
                MATCH (f:Fact)
                WHERE f.lesson_number IN $lessons
                RETURN f.text AS text
                """,
                lessons=list(lessons),
            )
            facts = [record["text"] for record in result]

    return RetrievalResult(
        chunks=chunks,
        concepts=concepts,
        facts=facts,
        source_lessons=sorted(lessons),
    )


def retrieve(
    driver: Any,
    query: str,
    *,
    query_embedding: list[float] | None = None,
    max_results: int = 10,
) -> RetrievalResult:
    """Combined retrieval: graph-first, then lexical and embedding fallbacks.

    Strategy:
      1. Graph traversal by concept name match
      2. Full-text search on chunk text (lexical)
      3. Vector similarity (if embedding provided)

    Results are merged and deduplicated by chunk ID.
    """
    # 1. Graph traversal
    graph_result = retrieve_by_concept(driver, query, max_chunks=max_results)
    if len(graph_result.chunks) >= max_results:
        return graph_result

    # 2. Lexical fallback
    remaining = max_results - len(graph_result.chunks)
    text_result = retrieve_by_text(driver, query, max_results=remaining)

    # 3. Embedding fallback
    embedding_result = RetrievalResult(chunks=[], concepts=[], facts=[], source_lessons=[])
    if query_embedding:
        remaining = max_results - len(graph_result.chunks) - len(text_result.chunks)
        if remaining > 0:
            embedding_result = retrieve_by_embedding(
                driver, query_embedding, max_results=remaining,
            )

    # Merge and dedup
    return _merge_results(graph_result, text_result, embedding_result)


def _merge_results(*results: RetrievalResult) -> RetrievalResult:
    """Merge multiple retrieval results, deduplicating by chunk ID."""
    seen_chunks: set[str] = set()
    merged_chunks: list[dict[str, Any]] = []
    seen_concepts: set[str] = set()
    merged_concepts: list[dict[str, Any]] = []
    merged_facts: set[str] = set()
    merged_lessons: set[str] = set()

    for r in results:
        for chunk in r.chunks:
            if chunk["id"] not in seen_chunks:
                seen_chunks.add(chunk["id"])
                merged_chunks.append(chunk)
        for concept in r.concepts:
            if concept["name"] not in seen_concepts:
                seen_concepts.add(concept["name"])
                merged_concepts.append(concept)
        merged_facts.update(r.facts)
        merged_lessons.update(r.source_lessons)

    return RetrievalResult(
        chunks=merged_chunks,
        concepts=merged_concepts,
        facts=sorted(merged_facts),
        source_lessons=sorted(merged_lessons),
    )


def get_all_concepts(driver: Any) -> list[dict[str, Any]]:
    """Return all concepts in the graph (for quiz topic selection)."""
    with driver.session() as session:
        result = session.run(
            """
            MATCH (c:Concept)
            OPTIONAL MATCH (l:Lesson)-[:HAS_CONCEPT]->(c)
            RETURN c.name AS name, c.definition AS definition,
                   collect(l.number) AS lessons
            ORDER BY c.name
            """
        )
        return [
            {"name": r["name"], "definition": r["definition"], "lessons": r["lessons"]}
            for r in result
        ]


def get_lesson_concepts(driver: Any, lesson_number: str) -> list[dict[str, Any]]:
    """Return concepts for a specific lesson."""
    with driver.session() as session:
        result = session.run(
            """
            MATCH (l:Lesson {number: $lesson})-[:HAS_CONCEPT]->(c:Concept)
            RETURN c.name AS name, c.definition AS definition
            ORDER BY c.name
            """,
            lesson=lesson_number,
        )
        return [{"name": r["name"], "definition": r["definition"]} for r in result]


def get_indexed_lessons(driver: Any) -> list[dict[str, str]]:
    """Return all indexed lessons."""
    with driver.session() as session:
        result = session.run(
            """
            MATCH (l:Lesson)
            RETURN l.number AS number, l.title AS title
            ORDER BY l.number
            """
        )
        return [{"number": r["number"], "title": r["title"]} for r in result]


def _escape_lucene(text: str) -> str:
    """Escape Lucene special characters for full-text search queries."""
    special = r'+-&|!(){}[]^"~*?:\/'
    escaped = []
    for ch in text:
        if ch in special:
            escaped.append(f"\\{ch}")
        else:
            escaped.append(ch)
    return "".join(escaped)
