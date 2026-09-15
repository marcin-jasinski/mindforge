import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { ApiError, DocumentView, RunSummary } from '../../core/models/api';
import { ApiService } from '../../core/services/api.service';
import { ProgressService } from '../../core/services/progress.service';

interface Collision {
  lessonId: string;
  lessonTitle: string;
}

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, MatListModule,
    MatProgressBarModule],
  templateUrl: './documents.html',
  styles: `
    .dropzone { border: 2px dashed var(--mat-sys-outline); padding: 1.5rem; text-align: center; border-radius: 0.5rem; }
    .dropzone.over { background: var(--mat-sys-surface-container-high); }
  `,
})
export class Documents implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly progress = inject(ProgressService);

  readonly kbId = input.required<string>();
  readonly documents = signal<DocumentView[]>([]);
  readonly file = signal<File | null>(null);
  readonly collision = signal<Collision | null>(null);
  readonly uploadError = signal<string | null>(null);
  readonly dragging = signal(false);
  readonly newLessonId = signal('');

  constructor() {
    // a run that changed state may have changed a document's latest run
    effect(() => {
      this.progress.runs();
      if (this.kbId()) {
        this.loadDocuments();
      }
    });
  }

  ngOnInit(): void {
    this.loadDocuments();
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
    this.api.post(`/knowledge-bases/${this.kbId()}/documents`, form, true).subscribe({
      next: () => {
        this.file.set(null);
        this.collision.set(null);
        this.newLessonId.set('');
        this.progress.reload(this.kbId());
      },
      error: (response: HttpErrorResponse) => {
        const error = response.error as ApiError | null;
        if (error?.code === 'LESSON_EXISTS') {
          this.collision.set({ lessonId: error.lessonId ?? '', lessonTitle: error.lessonTitle ?? '' });
          this.newLessonId.set(`${error.lessonId}-2`);
        } else {
          this.uploadError.set(error?.detail ?? 'Nie udało się przesłać pliku.');
        }
      },
    });
  }

  retry(run: RunSummary): void {
    this.api.post(`/knowledge-bases/${this.kbId()}/documents/${run.documentId}/runs`).subscribe(() =>
      this.progress.reload(this.kbId()),
    );
  }

  percent(runId: string): number | null {
    const step = this.progress.steps()[runId];
    return step?.total ? Math.round(((step.done ?? 0) / step.total) * 100) : null;
  }

  private loadDocuments(): void {
    this.api.get<DocumentView[]>(`/knowledge-bases/${this.kbId()}/documents`).subscribe((docs) => this.documents.set(docs));
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
