import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { LessonDetail } from '../../core/models/api.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent implements OnInit {
  lessons = signal<LessonDetail[]>([]);
  dueCount = signal(0);
  loading = signal(true);
  error = signal('');

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.getLessons().subscribe({
      next: lessons => {
        this.lessons.set(lessons);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Nie udało się załadować lekcji.');
        this.loading.set(false);
      },
    });

    this.api.getDueFlashcards().subscribe({
      next: res => this.dueCount.set(res.total_due),
      error: () => {},
    });
  }

  totalConcepts(): number {
    return this.lessons().reduce((sum, l) => sum + l.concept_count, 0);
  }

  totalFlashcards(): number {
    return this.lessons().reduce((sum, l) => sum + l.flashcard_count, 0);
  }

  totalChunks(): number {
    return this.lessons().reduce((sum, l) => sum + l.chunk_count, 0);
  }
}
