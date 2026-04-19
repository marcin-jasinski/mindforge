import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { KnowledgeBaseService } from '../../core/services/knowledge-base.service';
import { KbCreateDialogComponent } from './kb-create-dialog';
import type { KnowledgeBaseResponse } from '../../core/models/api.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatCardModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, MatChipsModule, MatDialogModule,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent implements OnInit {
  private readonly kbService = inject(KnowledgeBaseService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  readonly kbs = signal<KnowledgeBaseResponse[]>([]);
  readonly loading = signal(true);

  ngOnInit() {
    this.loadKBs();
  }

  loadKBs() {
    this.loading.set(true);
    this.kbService.list().subscribe({
      next: kbs => { this.kbs.set(kbs); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  openCreateDialog() {
    const ref = this.dialog.open(KbCreateDialogComponent, { width: '440px' });
    ref.afterClosed().subscribe(result => {
      if (result) this.loadKBs();
    });
  }

  navigateToKb(kbId: string) {
    this.router.navigate(['/kb', kbId, 'documents']);
  }
}
