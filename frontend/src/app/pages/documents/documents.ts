import {
  Component,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DatePipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { DocumentService } from '../../core/services/document.service';
import { EventService } from '../../core/services/event.service';
import type { DocumentResponse } from '../../core/models/api.models';

@Component({
  selector: 'app-documents',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, MatChipsModule, MatTooltipModule, DatePipe,
  ],
  templateUrl: './documents.html',
  styleUrl: './documents.scss',
})
export class DocumentsComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly docService = inject(DocumentService);
  private readonly eventService = inject(EventService);
  private readonly snack = inject(MatSnackBar);

  readonly kbId = signal('');
  readonly documents = signal<DocumentResponse[]>([]);
  readonly loading = signal(true);
  readonly uploading = signal(false);
  readonly dragOver = signal(false);

  readonly displayedColumns = ['title', 'status', 'mime_type', 'created_at', 'actions'];

  private eventSub?: Subscription;

  ngOnInit() {
    const kbId = this.route.snapshot.paramMap.get('kbId') ?? '';
    this.kbId.set(kbId);
    this.loadDocuments();
    this.subscribeToEvents();
  }

  ngOnDestroy() {
    this.eventSub?.unsubscribe();
  }

  loadDocuments() {
    this.loading.set(true);
    this.docService.list(this.kbId()).subscribe({
      next: docs => { this.documents.set(docs); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  private subscribeToEvents() {
    this.eventSub = this.eventService.connect(this.kbId()).subscribe(evt => {
      if (['PipelineStepCompleted', 'ProcessingCompleted', 'ProcessingFailed'].includes(evt.event_type)) {
        this.loadDocuments();
      }
    });
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(true);
  }

  onDragLeave() {
    this.dragOver.set(false);
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(false);
    const files = event.dataTransfer?.files;
    if (files?.length) {
      this.uploadFiles(Array.from(files));
    }
  }

  onFileInput(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.uploadFiles(Array.from(input.files));
      input.value = '';
    }
  }

  private uploadFiles(files: File[]) {
    this.uploading.set(true);
    let completed = 0;
    for (const file of files) {
      this.docService.upload(this.kbId(), file).subscribe({
        next: resp => {
          this.snack.open(`"${file.name}" queued for processing (task: ${resp.task_id.slice(0, 8)}…)`, 'OK', { duration: 5000 });
          completed++;
          if (completed === files.length) {
            this.uploading.set(false);
            this.loadDocuments();
          }
        },
        error: (err: Error) => {
          this.snack.open(err.message, 'Close', { duration: 5000 });
          completed++;
          if (completed === files.length) this.uploading.set(false);
        },
      });
    }
  }

  reprocess(docId: string) {
    this.docService.reprocess(this.kbId(), docId, { force: true }).subscribe({
      next: () => { this.snack.open('Reprocessing queued', 'OK', { duration: 3000 }); this.loadDocuments(); },
      error: (err: Error) => this.snack.open(err.message, 'Close', { duration: 4000 }),
    });
  }

  statusClass(status: string): string {
    return `status-badge status-${status.toLowerCase()}`;
  }
}
