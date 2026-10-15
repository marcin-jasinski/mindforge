import { ApiError } from '../core/models/api';

const RUN_KINDS: Record<string, string> = {
  INGEST: 'Ingest',
  REVERT: 'Revert',
  LINT: 'Review',
};

const RUN_STATUSES: Record<string, string> = {
  QUEUED: 'queued',
  RUNNING: 'running',
  WRITTEN: 'written',
  COMPLETED: 'completed',
  FAILED: 'failed',
};

const PAGE_TYPES: Record<string, string> = {
  Concept: 'Concept',
  'Source Summary': 'Source summary',
};

/** What the server says in its own words, by error code; a code the page knows reads better than the server's own message. */
const ERROR_MESSAGES: Record<string, string> = {
  NOT_FOUND: 'The requested resource was not found.',
  FORBIDDEN: 'You do not have access to this resource.',
  EMAIL_TAKEN: 'An account with this e-mail address already exists.',
  INVALID_CREDENTIALS: 'Incorrect e-mail or password.',
  KNOWLEDGE_BASE_BUSY: 'Another run is using this knowledge base — try again in a moment.',
  REVERT_NOT_ALLOWED: 'This change can no longer be reverted.',
  RETRY_NOT_ALLOWED: 'This run cannot be retried.',
  DOCUMENT_NOT_REMOVABLE: 'This document cannot be removed — it has a run in progress or written pages.',
  LINT_ALREADY_QUEUED: 'A review is already queued.',
  QUIZ_FINISHED: 'This quiz is already finished.',
  RATE_LIMITED: 'Slow down — too many requests. Try again in a moment.',
  LESSON_IDENTITY: 'The lesson identity could not be found.',
  UNKNOWN_LESSON: 'There is no such lesson.',
  UPLOAD_REJECTED: 'This file cannot be accepted.',
  UPLOAD_TOO_LARGE: 'The file is too large.',
  MODEL_UNAVAILABLE: 'The language model is unavailable or did not reply in time. Try again in a moment.',
  VALIDATION: 'Correct the form data and try again.',
  REQUEST_ERROR: 'The request failed.',
  INTERNAL: 'Something went wrong on the server. Try again.',
};

const FAILURE_REASONS: [RegExp, string][] = [
  [/^unexpected error$/, 'Unexpected error — the details are in the server log.'],
  [/^AI provider call failed/, 'The model server did not reply. Check its configuration and retry the run.'],
  [/^AI gateway call exceeded/, 'The model did not reply in time. Retry the run.'],
  [/^AI gateway circuit is open/, 'The model is temporarily unavailable. Retry the run in a moment.'],
  [/^the document has no text/, 'The document contains no text.'],
  [/^the run's document is gone/, 'The document of this run was removed.'],
  [/^edit named no page/, 'No page to change was named.'],
  [/^no applicable change/, 'There is no change to apply.'],
  [/^no page task succeeded/, 'No page was written.'],
  [/^not learning material/, 'The document does not look like learning material.'],
];

const FAILURE_STEPS: Record<string, string> = {
  resolve: 'resolve pages',
  linkCheck: 'link check',
  review: 'review',
  write: 'page write',
  supersede: 'supersede',
};

/** One skipped or failed item of a run as a sentence fragment: "link check — 2 skipped". */
export function failureLine(failure: {
  step: string;
  item?: string | null;
  path?: string | null;
  reason?: string | null;
  count?: number | null;
}): string {
  const where = [FAILURE_STEPS[failure.step] ?? failure.step, failure.item, failure.path].filter(Boolean).join(' · ');
  const why = failure.reason ? `: ${failureReasonLabel(failure.reason)}` : '';
  const count = failure.count ? ` — ${failure.count} skipped` : '';
  return where + why + count;
}

const FINDING_KINDS: Record<string, string> = {
  contradiction: 'Contradiction',
  unmarked_supersession: 'Unmarked supersession',
  missing_page: 'Missing page',
  question: 'Open question',
};

export function findingKindLabel(kind: string): string {
  return FINDING_KINDS[kind] ?? kind;
}

export function runKindLabel(kind: string): string {
  return RUN_KINDS[kind] ?? kind;
}

export function runStatusLabel(status: string): string {
  return RUN_STATUSES[status] ?? status;
}

export function pageTypeLabel(type: string): string {
  return PAGE_TYPES[type] ?? type;
}

/** A run's recorded failure reason in the interface's language; an unknown reason is shown as recorded. */
export function failureReasonLabel(reason: string): string {
  return FAILURE_REASONS.find(([pattern]) => pattern.test(reason))?.[1] ?? reason;
}

/** The text a page shows for an API error, chosen by its code. */
export function apiErrorMessage(error: ApiError | null | undefined, fallback = 'Something went wrong.'): string {
  return (error?.code && ERROR_MESSAGES[error.code]) || fallback;
}
