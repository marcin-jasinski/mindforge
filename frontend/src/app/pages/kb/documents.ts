import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { Subject, switchMap } from 'rxjs';

import { ApiError, DocumentView, RunSummary } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';
import { RunApi } from '../../core/services/run-api';
import { ProgressService } from '../../core/services/progress.service';
import { Icon } from '../../shared/icon';
import { apiErrorMessage, failureReasonLabel, progressStepLabel, runKindLabel, runStatusLabel } from '../../shared/labels';

interface Collision {
  lessonId: string;
  lessonTitle: string;
}

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, Icon],
  templateUrl: './documents.html',
  styles: `
    .upload { display: flex; flex-direction: column; gap: 1rem; margin-bottom: 1.5rem; }
    .dropzone { position: relative; display: grid; justify-items: center; gap: 0.35rem; padding: 2.25rem 1rem;
      text-align: center; cursor: pointer; color: var(--mf-muted); border: 1.5px dashed var(--mf-border-strong);
      border-radius: var(--mf-radius); background: rgb(255 255 255 / 0.015); transition: border-color 150ms, background-color 150ms; }
    .dropzone strong { color: var(--mf-text); }
    .dropzone:hover, .dropzone.over { border-color: var(--mf-accent); background: var(--mf-accent-soft); color: var(--mf-accent); }
    .dropzone.has-file { border-style: solid; border-color: rgb(94 234 212 / 0.4); color: var(--mf-cyan); }
    .dropzone:focus-within { outline: 2px solid var(--mf-accent); outline-offset: 2px; }
    .dropzone input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
    .end { justify-content: flex-end; }
    .collision > app-icon { color: var(--mf-warn); }
    .columns { display: grid; gap: 1.5rem; align-items: start; }
    @media (min-width: 60rem) { .columns { grid-template-columns: 3fr 2fr; } }
    .item { display: flex; align-items: center; gap: 0.85rem; }
    .grow { flex: 1; min-width: 0; }
    .run { gap: 0.45rem; }
    .title { color: var(--mf-text); font-weight: 550; }
    .added { color: var(--mf-success); }
    .info-text { color: var(--mf-info); }
  `,
})
export class Documents {
  private readonly wiki = inject(WikiApi);
  private readonly runs = inject(RunApi);
  protected readonly progress = inject(ProgressService);

  readonly kbId = input.required<string>();
  readonly documents = signal<DocumentView[]>([]);
  readonly file = signal<File | null>(null);
  readonly collision = signal<Collision | null>(null);
  readonly uploadError = signal<string | null>(null);
  readonly dragging = signal(false);
  readonly newLessonId = signal('');
  readonly uploading = signal(false);
  /** switchMap drops a slower, older answer, so a stale list never overwrites a newer one. */
  private readonly reloads = new Subject<void>();

  constructor() {
    this.reloads
      .pipe(switchMap(() => this.wiki.documents(this.kbId())), takeUntilDestroyed())
      .subscribe((docs) => this.documents.set(docs));
    // a run that changed state may have changed a document's latest run; the first pass loads the list
    effect(() => {
      this.progress.runs();
      if (this.kbId()) {
        this.loadDocuments();
      }
    });
  }

  choose(event: Event): void {
    this.file.set((event.target as HTMLInputElement).files?.[0] ?? null);
    this.collision.set(null);
  }

  drop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.file.set(event.dataTransfer?.files?.[0] ?? null);
    this.collision.set(null);
  }

  upload(options: { lessonId?: string; newVersion?: boolean } = {}): void {
    const file = this.file();
    if (!file) {
      return;
    }
    const form = new FormData();
    form.append('file', new Blob([file], { type: mimeType(file) }), file.name);
    if (options.lessonId) {
      form.append('lessonId', options.lessonId);
    }
    form.append('newVersion', String(options.newVersion ?? false));
    this.uploadError.set(null);
    this.uploading.set(true);
    this.wiki.uploadDocument(this.kbId(), form).subscribe({
      next: () => {
        this.uploading.set(false);
        this.file.set(null);
        this.collision.set(null);
        this.newLessonId.set('');
        this.progress.reload(this.kbId());
      },
      error: (response: HttpErrorResponse) => {
        this.uploading.set(false);
        const error = response.error as ApiError | null;
        if (error?.code === 'LESSON_EXISTS') {
          this.collision.set({ lessonId: error.lessonId ?? '', lessonTitle: error.lessonTitle ?? '' });
          this.newLessonId.set(`${error.lessonId}-2`);
        } else {
          this.uploadError.set(apiErrorMessage(error, 'The file could not be uploaded.'));
        }
      },
    });
  }

  remove(doc: DocumentView): void {
    // ponytail: native confirm, as for a revert; a MatDialog when it needs to say what else goes with the document
    if (confirm(`Remove the document “${doc.lessonTitle}”? You can upload the file again.`)) {
      this.wiki.removeDocument(this.kbId(), doc.documentId).subscribe(() => {
        this.loadDocuments();
        this.progress.reload(this.kbId());
      });
    }
  }

  retry(run: RunSummary): void {
    this.runs.retryDocument(this.kbId(), run.documentId).subscribe(() =>
      this.progress.reload(this.kbId()),
    );
  }

  protected readonly statusLabel = runStatusLabel;
  protected readonly reasonLabel = failureReasonLabel;
  protected readonly stepLabel = progressStepLabel;

  /** "Ingest · photosynthesis"; a revert has no subject, so no dangling separator. */
  runTitle(run: RunSummary): string {
    const subject = run.conversation ? 'conversation' : run.lessonTitle;
    return subject ? `${runKindLabel(run.kind)} · ${subject}` : runKindLabel(run.kind);
  }

  /** Only a document's newest run can be retried; an older failure was already answered by a later run. */
  canRetry(run: RunSummary): boolean {
    return run.status === 'FAILED' && run.retryable && !!run.documentId
      && this.progress.runs().find((other) => other.documentId === run.documentId)?.runId === run.runId;
  }

  percent(runId: string): number | null {
    const step = this.progress.steps()[runId];
    return step?.total ? Math.round(((step.done ?? 0) / step.total) * 100) : null;
  }

  size(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  private loadDocuments(): void {
    this.reloads.next();
  }
}

/** Browsers often leave .md files untyped; the server admits only the types it has a parser for. */
function mimeType(file: File): string {
  const name = file.name.toLowerCase();
  if (name.endsWith('.md') || name.endsWith('.markdown')) {
    return 'text/markdown';
  }
  if (name.endsWith('.txt')) {
    return 'text/plain';
  }
  return file.type || 'application/octet-stream';
}
