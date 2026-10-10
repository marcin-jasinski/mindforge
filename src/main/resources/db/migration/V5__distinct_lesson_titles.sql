-- Two lessons of one knowledge base must not read alike in a citation. A lesson whose title (ignoring case) an
-- earlier lesson already carries takes its id as a suffix, as an upload does from now on ("mitoza (mitoza-2)").
-- Only the lesson's documents and its Source Summary's current title change; revisions keep what they recorded.
WITH lesson AS (
    SELECT knowledge_base_id, lesson_id, lower(lesson_title) AS title_key, min(created_at) AS first_upload
    FROM documents
    WHERE upload_source <> 'CONVERSATION'
    GROUP BY knowledge_base_id, lesson_id, lower(lesson_title)
),
ranked AS (
    SELECT knowledge_base_id, lesson_id,
           row_number() OVER (PARTITION BY knowledge_base_id, title_key ORDER BY first_upload, lesson_id) AS position
    FROM lesson
),
later AS (
    SELECT knowledge_base_id, lesson_id FROM ranked WHERE position > 1
),
renamed AS (
    UPDATE documents d
    SET lesson_title = left(d.lesson_title, 200 - length(d.lesson_id) - 3) || ' (' || d.lesson_id || ')'
    FROM later l
    WHERE d.knowledge_base_id = l.knowledge_base_id
      AND d.lesson_id = l.lesson_id
      AND d.upload_source <> 'CONVERSATION'
    RETURNING d.knowledge_base_id, d.lesson_id
)
UPDATE wiki_pages w
SET title = left(w.title, 200 - length(l.lesson_id) - 3) || ' (' || l.lesson_id || ')'
FROM later l
WHERE w.knowledge_base_id = l.knowledge_base_id
  AND w.path = 'sources/' || l.lesson_id
  AND w.page_type = 'Source Summary';
