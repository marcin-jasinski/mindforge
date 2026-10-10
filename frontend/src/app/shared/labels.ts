import { ApiError } from '../core/models/api';

const RUN_KINDS: Record<string, string> = {
  INGEST: 'Przetwarzanie',
  REVERT: 'Cofnięcie',
  LINT: 'Przegląd',
};

const RUN_STATUSES: Record<string, string> = {
  QUEUED: 'w kolejce',
  RUNNING: 'w toku',
  WRITTEN: 'zapisany',
  COMPLETED: 'zakończony',
  FAILED: 'nieudany',
};

const PAGE_TYPES: Record<string, string> = {
  Concept: 'Pojęcie',
  'Source Summary': 'Podsumowanie źródła',
};

/** What the server says in its own words, by error code; a code the page knows is better than the server's English. */
const ERROR_MESSAGES: Record<string, string> = {
  NOT_FOUND: 'Nie znaleziono żądanego zasobu.',
  FORBIDDEN: 'Nie masz dostępu do tego zasobu.',
  EMAIL_TAKEN: 'Konto z tym adresem e-mail już istnieje.',
  INVALID_CREDENTIALS: 'Nieprawidłowy e-mail lub hasło.',
  KNOWLEDGE_BASE_BUSY: 'Baza wiedzy jest zajęta innym przebiegiem — spróbuj za chwilę.',
  REVERT_NOT_ALLOWED: 'Tej zmiany nie można już cofnąć.',
  RETRY_NOT_ALLOWED: 'Tego przebiegu nie można ponowić.',
  DOCUMENT_NOT_REMOVABLE: 'Tego dokumentu nie można usunąć — ma przebieg w toku albo zapisane strony.',
  LINT_ALREADY_QUEUED: 'Przegląd jest już w kolejce.',
  QUIZ_FINISHED: 'Ten quiz jest już zakończony.',
  RATE_LIMITED: 'Zwolnij — zbyt wiele zapytań. Spróbuj ponownie za chwilę.',
  LESSON_IDENTITY: 'Nie udało się ustalić tożsamości lekcji.',
  UNKNOWN_LESSON: 'Nie ma takiej lekcji.',
  UPLOAD_REJECTED: 'Ten plik nie może zostać przyjęty.',
  UPLOAD_TOO_LARGE: 'Plik jest za duży.',
  MODEL_UNAVAILABLE: 'Model językowy jest niedostępny lub nie odpowiedział na czas. Spróbuj ponownie za chwilę.',
  VALIDATION: 'Popraw dane w formularzu i spróbuj ponownie.',
  REQUEST_ERROR: 'Nie udało się wykonać żądania.',
  INTERNAL: 'Coś poszło nie tak po stronie serwera. Spróbuj ponownie.',
};

const FAILURE_REASONS: [RegExp, string][] = [
  [/^unexpected error$/, 'Nieoczekiwany błąd — szczegóły są w logu serwera.'],
  [/^AI provider call failed/, 'Serwer modelu nie odpowiedział. Sprawdź jego konfigurację i ponów przebieg.'],
  [/^AI gateway call exceeded/, 'Model nie odpowiedział w wyznaczonym czasie. Ponów przebieg.'],
  [/^AI gateway circuit is open/, 'Model jest chwilowo niedostępny. Ponów przebieg za chwilę.'],
  [/^the document has no text/, 'Dokument nie zawiera tekstu.'],
  [/^the run's document is gone/, 'Dokument tego przebiegu został usunięty.'],
  [/^edit named no page/, 'Nie wskazano strony do zmiany.'],
  [/^no applicable change/, 'Brak zmiany do zastosowania.'],
  [/^no page task succeeded/, 'Żadna strona nie została zapisana.'],
  [/^not learning material/, 'Dokument nie wygląda na materiał do nauki.'],
];

const FAILURE_STEPS: Record<string, string> = {
  resolve: 'ustalanie stron',
  linkCheck: 'sprawdzanie linków',
  review: 'przegląd',
  write: 'zapis strony',
  supersede: 'poprawki sekcji',
};

/** One skipped or failed item of a run as a sentence fragment: "sprawdzanie linków — pominięto 2". */
export function failureLine(failure: {
  step: string;
  item?: string | null;
  path?: string | null;
  reason?: string | null;
  count?: number | null;
}): string {
  const where = [FAILURE_STEPS[failure.step] ?? failure.step, failure.item, failure.path].filter(Boolean).join(' · ');
  const why = failure.reason ? `: ${failureReasonLabel(failure.reason)}` : '';
  const count = failure.count ? ` — pominięto ${failure.count}` : '';
  return where + why + count;
}

const FINDING_KINDS: Record<string, string> = {
  contradiction: 'Sprzeczność',
  unmarked_supersession: 'Nieoznaczona poprawka',
  missing_page: 'Brakująca strona',
  question: 'Pytanie do zbadania',
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
export function apiErrorMessage(error: ApiError | null | undefined, fallback = 'Coś poszło nie tak.'): string {
  return (error?.code && ERROR_MESSAGES[error.code]) || fallback;
}
