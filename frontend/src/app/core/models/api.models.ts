// API response/request types matching FastAPI schemas

export interface UserInfo {
  discord_id: string;
  username: string;
  avatar: string | null;
}

export interface HealthResponse {
  status: string;
  neo4j: string;
}

export interface LessonDetail {
  number: string;
  title: string;
  processed_at: string;
  concept_count: number;
  flashcard_count: number;
  chunk_count: number;
}

export interface ConceptNode {
  id: string;
  label: string;
  group: string;
  color: string;
}

export interface ConceptEdge {
  source: string;
  target: string;
  label: string;
  description: string;
}

export interface ConceptGraphResponse {
  nodes: ConceptNode[];
  edges: ConceptEdge[];
}

export interface QuizStartRequest {
  lesson?: string;
  count: number;
}

export interface QuizQuestion {
  session_id: string;
  question_id: number;
  question: string;
  topic: string;
  question_type: string;
  options: string[] | null;
  source_lessons: string[];
}

export interface QuizAnswerRequest {
  session_id: string;
  question_id: number;
  user_answer: string;
}

export interface QuizEvaluation {
  score: number;
  feedback: string;
  correct_answer: string;
  grounding_sources: string[];
}

export interface FlashcardReview {
  id: string;
  front: string;
  back: string;
  card_type: string;
  tags: string[];
  lesson_number: string;
  ease: number;
  interval: number;
  repetitions: number;
  due_date: string;
}

export interface FlashcardsDueResponse {
  cards: FlashcardReview[];
  total_due: number;
}

export interface ReviewRequest {
  card_id: string;
  rating: number;
}

export interface ReviewResponse {
  card_id: string;
  new_ease: number;
  new_interval: number;
  next_due: string;
}

export interface SearchRequest {
  query: string;
  max_results?: number;
}

export interface ChunkResult {
  id: string;
  text: string;
  lesson_number: string;
  score: number | null;
}

export interface ConceptResult {
  name: string;
  definition: string;
}

export interface SearchResponse {
  chunks: ChunkResult[];
  concepts: ConceptResult[];
  facts: string[];
  source_lessons: string[];
}

export interface UploadResponse {
  filename: string;
  message: string;
}
