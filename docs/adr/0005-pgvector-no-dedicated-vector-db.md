# pgvector in PostgreSQL instead of a dedicated vector database

MindForge stores and queries embedding vectors using the `pgvector` extension in the
existing PostgreSQL instance rather than operating a separate vector database service.
Spring AI's `PgVectorStore` integrates with pgvector out of the box, so embeddings
are stored alongside the rest of the business data in the same tables and transaction
boundary. This collapses the retrieval pipeline to a single connection pool and
eliminates a service dependency.

## Considered Options

- **Pinecone (managed cloud)** — rejected: adds a paid external service with its own
  API, latency, and availability SLA. Data lives outside the primary datastore, making
  backups and consistency more complex. Unnecessary for the expected corpus size
  (hundreds to low thousands of documents).

- **Qdrant / Weaviate / Chroma (self-hosted)** — rejected: each adds a container to
  the deployment topology, a new client library, and a separate backup/recovery concern.
  At personal-tool scale, the operational overhead is not justified.

- **pgvector** — selected: the `pgvector` extension is available on Railway's managed
  PostgreSQL and on standard distributions. Vectors live in the same database as
  `document_artifacts` and `knowledge_bases`, so embedding insertion and artifact
  persistence happen in one transaction. `ivfflat` indexing is sufficient for the
  expected data size.

## Consequences

- A single database service handles relational data, JSONB artifact storage,
  full-text search, and vector similarity queries. No additional infrastructure to
  operate, monitor, or back up.
- If the corpus grows beyond pgvector's practical limits (tens of millions of dense
  vectors), migrating to a dedicated vector store requires implementing a new adapter
  behind the `VectorStorePort` interface — no domain or application code changes.
- The retrieval cost discipline is: graph traversal first → full-text/lexical second →
  vector similarity last. pgvector is never called for tasks that cheaper retrieval
  can satisfy.
