import { components } from './api.generated';

type Schemas = components['schemas'];

export type User = Schemas['UserResponse'];
export type KnowledgeBase = Schemas['KnowledgeBaseResponse'];
export type DocumentView = Schemas['DocumentResponse'];
export type DocumentAccepted = Schemas['DocumentAcceptedResponse'];
export type RunAccepted = Schemas['RunAcceptedResponse'];
export type IndexView = Schemas['IndexResponse'];
export type IndexEntry = Schemas['IndexEntryResponse'];
export type Page = Schemas['PageResponse'];
export type Revision = Schemas['RevisionResponse'];
export type Graph = Schemas['GraphResponse'];
export type RunSummary = Schemas['RunSummaryResponse'];
export type RunReport = Schemas['RunReportResponse'];
export type PageChange = Schemas['PageChangeResponse'];
export type Health = Schemas['HealthResponse'];
export type Flashcard = Schemas['FlashcardResponse'];
export type QuizSession = Schemas['QuizSessionResponse'];
export type NextQuestion = Schemas['NextQuestionResponse'];
export type Evaluation = Schemas['EvaluationResponse'];
export type QuerySession = Schemas['QuerySessionResponse'];
export type Answer = Schemas['AnswerResponse'];
export type TurnSummary = Schemas['TurnSummaryResponse'];
export type ApiError = Schemas['ErrorResponse'];

/**
 * The SSE payload of GET /api/knowledge-bases/{kbId}/progress. An SSE event body has no OpenAPI schema, so this one
 * type mirrors the backend's RunProgress record by hand.
 */
export interface RunProgress {
  runId: string;
  kind: 'INGEST' | 'REVERT' | 'LINT';
  documentId: string | null;
  status: 'QUEUED' | 'RUNNING' | 'WRITTEN' | 'COMPLETED' | 'FAILED';
  step: string | null;
  done: number | null;
  total: number | null;
  at: string;
}
