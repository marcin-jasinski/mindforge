-- The interface is English now; the reserved conversation lesson was titled in Polish.
UPDATE documents
SET lesson_title = 'Conversation'
WHERE upload_source = 'CONVERSATION'
  AND lesson_title = 'Rozmowa';
