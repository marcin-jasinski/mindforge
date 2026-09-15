import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';

import { DocumentView, Evaluation, Flashcard, NextQuestion, QuizSession } from '../../core/models/api';
import { ApiService } from '../../core/services/api.service';

type ScopeKind = 'whole' | 'lesson' | 'page';

@Component({
  selector: 'app-study',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatRadioModule, MatSelectModule],
  templateUrl: './study.html',
})
export class Study implements OnInit {
  private readonly api = inject(ApiService);

  readonly kbId = input.required<string>();
  /** Set when a page's own "study this page" link opened the view. */
  readonly pageId = input<string>();
  readonly lessons = signal<{ id: string; title: string }[]>([]);
  readonly cards = signal<Flashcard[]>([]);
  readonly cardIndex = signal(0);
  readonly showBack = signal(false);
  readonly card = computed(() => this.cards()[this.cardIndex()] ?? null);
  readonly session = signal<QuizSession | null>(null);
  readonly question = signal<NextQuestion | null>(null);
  readonly evaluation = signal<Evaluation | null>(null);
  readonly finished = signal(false);
  readonly ratings = [0, 1, 2, 3, 4, 5];
  readonly scope = signal<ScopeKind>('whole');
  readonly lessonId = signal('');
  readonly answer = signal('');

  ngOnInit(): void {
    // the reserved conversation lesson is not a document a learner uploaded, so it never appears here
    this.api.get<DocumentView[]>(`/knowledge-bases/${this.kbId()}/documents`).subscribe((docs) => {
      const lessons = new Map(docs.map((doc) => [doc.lessonId, doc.lessonTitle]));
      this.lessons.set([...lessons].map(([id, title]) => ({ id, title })));
    });
    if (this.pageId()) {
      this.scope.set('page');
    }
  }

  openDeck(): void {
    this.api.get<Flashcard[]>(`/knowledge-bases/${this.kbId()}/flashcards${this.scopeQuery()}`).subscribe((cards) => {
      this.cards.set(cards);
      this.cardIndex.set(0);
      this.showBack.set(false);
    });
  }

  rate(rating: number): void {
    const card = this.card();
    if (!card) {
      return;
    }
    this.api.post(`/knowledge-bases/${this.kbId()}/flashcards/${card.cardId}/reviews`, { rating }).subscribe(() => {
      this.cardIndex.update((i) => i + 1);
      this.showBack.set(false);
    });
  }

  startQuiz(): void {
    const body = this.scope() === 'lesson' ? { lessonId: this.lessonId() } : this.scope() === 'page' ? { pageId: this.pageId() } : {};
    this.api.post<QuizSession>(`/knowledge-bases/${this.kbId()}/quiz-sessions`, body).subscribe((session) => {
      this.session.set(session);
      this.finished.set(false);
      this.next();
    });
  }

  submitAnswer(): void {
    this.api
      .post<Evaluation & { finished: boolean }>(this.sessionPath() + '/answers', { answer: this.answer() })
      .subscribe((evaluation) => {
        this.evaluation.set(evaluation);
        this.answer.set('');
      });
  }

  next(): void {
    this.evaluation.set(null);
    this.api.get<NextQuestion | null>(this.sessionPath() + '/next').subscribe((question) => {
      this.question.set(question);
      this.finished.set(question === null);
    });
  }

  private sessionPath(): string {
    return `/knowledge-bases/${this.kbId()}/quiz-sessions/${this.session()?.sessionId}`;
  }

  private scopeQuery(): string {
    if (this.scope() === 'lesson' && this.lessonId()) {
      return `?lessonId=${encodeURIComponent(this.lessonId())}`;
    }
    const pageId = this.pageId();
    return this.scope() === 'page' && pageId ? `?pageId=${encodeURIComponent(pageId)}` : '';
  }
}
