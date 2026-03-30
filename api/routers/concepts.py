"""
Concepts router — concept graph data for Cytoscape.js visualization.
"""
from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, Query

from api.auth import require_auth
from api.deps import get_neo4j_driver
from api.schemas import ConceptEdgeSchema, ConceptGraphResponse, ConceptNodeSchema, UserInfo

router = APIRouter(prefix="/api/concepts", tags=["concepts"])


@router.get("/graph", response_model=ConceptGraphResponse)
async def get_concept_graph(
    lesson: str | None = Query(default=None, description="Filter by lesson number"),
    driver: Any = Depends(get_neo4j_driver),
    _user: UserInfo = Depends(require_auth),
):
    """Return concept graph in Cytoscape.js elements format."""
    nodes: list[ConceptNodeSchema] = []
    edges: list[ConceptEdgeSchema] = []
    seen_nodes: set[str] = set()

    with driver.session() as session:
        # Get concepts (filtered by lesson or all)
        if lesson:
            result = session.run(
                """
                MATCH (l:Lesson {number: $lesson})-[:HAS_CONCEPT]->(c:Concept)
                OPTIONAL MATCH (c)-[r:RELATES_TO]->(other:Concept)
                RETURN c.name AS name, c.definition AS definition,
                       c.normalized_key AS normalized_key,
                       collect({
                           target: other.name,
                           label: r.label,
                           description: r.description
                       }) AS relations
                ORDER BY c.name
                """,
                lesson=lesson,
            )
        else:
            result = session.run(
                """
                MATCH (c:Concept)
                OPTIONAL MATCH (c)-[r:RELATES_TO]->(other:Concept)
                RETURN c.name AS name, c.definition AS definition,
                       c.normalized_key AS normalized_key,
                       collect({
                           target: other.name,
                           label: r.label,
                           description: r.description
                       }) AS relations
                ORDER BY c.name
                """
            )

        for record in result:
            name = record["name"]
            if name not in seen_nodes:
                seen_nodes.add(name)
                nodes.append(ConceptNodeSchema(
                    id=record["normalized_key"] or name.lower().strip(),
                    label=name,
                    group="default",
                    color="blue",
                ))

            for rel in record["relations"]:
                target = rel.get("target")
                if not target:
                    continue
                # Create target node if not seen
                if target not in seen_nodes:
                    seen_nodes.add(target)
                    nodes.append(ConceptNodeSchema(
                        id=target.lower().strip(),
                        label=target,
                        group="related",
                        color="green",
                    ))
                edges.append(ConceptEdgeSchema(
                    source=record["normalized_key"] or name.lower().strip(),
                    target=target.lower().strip(),
                    label=rel.get("label", ""),
                    description=rel.get("description", ""),
                ))

        # Add group info from concept map data if available
        if lesson:
            _enrich_with_groups(session, lesson, nodes, seen_nodes)

    return ConceptGraphResponse(nodes=nodes, edges=edges)


def _enrich_with_groups(
    session: Any,
    lesson: str,
    nodes: list[ConceptNodeSchema],
    seen_nodes: set[str],
) -> None:
    """Try to load group/color info from the stored artifact."""
    import json
    from pathlib import Path

    try:
        artifact_dir = Path(__file__).resolve().parent.parent.parent / "state" / "artifacts"
        for f in artifact_dir.glob(f"*{lesson}*.json"):
            data = json.loads(f.read_text(encoding="utf-8"))
            concept_map = data.get("concept_map")
            if not concept_map:
                continue

            # Build lookup from artifact concept map nodes
            node_lookup: dict[str, dict] = {}
            for n in concept_map.get("nodes", []):
                node_lookup[n["label"].lower().strip()] = n

            group_lookup: dict[str, str] = {}
            for g in concept_map.get("groups", []):
                for nid in g.get("node_ids", []):
                    group_lookup[nid] = g["name"]

            # Update existing nodes with group/color
            for node in nodes:
                artifact_node = node_lookup.get(node.label.lower().strip())
                if artifact_node:
                    node.color = artifact_node.get("color", node.color)
                    node.group = group_lookup.get(artifact_node.get("id", ""), node.group)
            break
    except Exception:
        pass
