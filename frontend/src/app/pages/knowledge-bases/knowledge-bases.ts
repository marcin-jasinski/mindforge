import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { KnowledgeBaseService } from '../../core/services/knowledge-base.service';
import { KbCreateDialogComponent } from '../dashboard/kb-create-dialog';
import type { KnowledgeBaseResponse } from '../../core/models/api.models';

@Component({
  selector: 'app-knowledge-bases',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatCardModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, MatChipsModule, MatDialogModule,
  ],
  templateUrl: './knowledge-bases.html',
  styleUrl: './knowledge-bases.scss',
})
export class KnowledgeBasesComponent implements OnInit {
  private readonly kbService = inject(KnowledgeBaseService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  readonly kbs = signal<KnowledgeBaseResponse[]>([]);
  readonly loading = signal(true);

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.kbService.list().subscribe({
      next: kbs => { this.kbs.set(kbs); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  openCreate() {
    this.dialog.open(KbCreateDialogComponent, { width: '440px' })
      .afterClosed().subscribe(res => { if (res) this.load(); });
  }

  deleteKb(kb: KnowledgeBaseResponse, event: MouseEvent) {
    event.stopPropagation();
    if (!confirm(`Delete "${kb.name}"? This cannot be undone.`)) return;
    this.kbService.delete(kb.kb_id).subscribe({
      next: () => { this.snack.open('Knowledge base deleted', 'OK', { duration: 3000 }); this.load(); },
      error: (err: Error) => this.snack.open(err.message, 'Close', { duration: 4000 }),
    });
  }
}
