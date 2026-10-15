-- A failed attempt that a later run of the same document got past is not a problem any more; it wrote no pages, so
-- nothing refers to it. From now on completing a run does this itself.
DELETE FROM ingest_runs failed
WHERE failed.status = 'FAILED'
  AND failed.document_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM ingest_runs later
      WHERE later.document_id = failed.document_id
        AND later.status = 'COMPLETED'
        AND later.created_at > failed.created_at);
