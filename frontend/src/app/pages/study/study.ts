import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { ApiError, Evaluation, Flashcard, NextQuestion, QuizSession, StudyScope } from '../../core/models/api';
import { StudyApi } from '../../core/services/study-api';
import { WikiApi } from '../../core/services/wiki-api';
import { Icon } from '../../shared/icon';
import { apiErrorMessage } from '../../shared/labels';

type ScopeKind = 'whole' | 'lesson' | 'page';
type Mode = 'setup' | 'cards' | 'quiz';

@Component({
  selector: 'app-study',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, Icon],
  templateUrl: './study.html',
  styleUrl: './study.scss',
  host: { '(document:keydown)': 'onKey($event)' },
})
export class Study implements OnInit {
  private readonly study = inject(StudyApi);
  private readonly wiki = inject(WikiApi);
  protected readonly Math = Math;

  readonly kbId = input.required<string>();
  /** Set when a page's own "study this page" link opened the view. */
  readonly pageId = input<string>();
  readonly lessons = signal<{ id: string; title: string }[]>([]);
  readonly mode = signal<Mode>('setup');
  readonly cards = signal<Flashcard[]>([]);
  readonly cardIndex = signal(0);
  readonly showBack = signal(false);
  readonly card = computed(() => this.cards()[this.cardIndex()] ?? null);
  /** This session's tally, so the learner sees what the session achieved. */
  readonly reviewed = signal(0);
  readonly remembered = signal(0);
  readonly session = signal<QuizSession | null>(null);
  readonly question = signal<NextQuestion | null>(null);
  readonly evaluation = signal<Evaluation | null>(null);
  readonly finished = signal(false);
  /** Each answered question with its grade, so the summary can point at what to review. */
  readonly results = signal<{ question: string; score: number; feedback: string }[]>([]);
  readonly average = computed(() => {
    const results = this.results();
    return results.length ? (results.reduce((sum, result) => sum + result.score, 0) / results.length).toFixed(1) : '0';
  });
  readonly weakestFirst = computed(() => [...this.results()].sort((a, b) => a.score - b.score));
  /** SM-2 grades: 0-2 forget the card, 3-5 remember it with growing ease. */
  readonly ratings = [
    { value: 0, short: 'Forgot', label: 'No memory at all' },
    { value: 1, short: 'Vague', label: 'Vague memory' },
    { value: 2, short: 'Almost', label: 'Almost remembered' },
    { value: 3, short: 'Hard', label: 'Remembered with difficulty' },
    { value: 4, short: 'Good', label: 'Remembered' },
    { value: 5, short: 'Easy', label: 'Remembered with no effort' },
  ];
  /** Cards and quiz questions are cut by a model on demand and can take minutes on a slow one. */
  readonly loadingDeck = signal(false);
  readonly startingQuiz = signal(false);
  readonly grading = signal(false);
  readonly error = signal<string | null>(null);
  readonly scope = signal<ScopeKind>('whole');
  readonly lessonId = signal('');
  readonly answer = signal('');
  readonly scopeOptions = computed(() => [
    { value: 'whole' as const, label: 'Whole knowledge base' },
    { value: 'lesson' as const, label: 'One lesson' },
    ...(this.pageId() ? [{ value: 'page' as const, label: 'This page and its links' }] : []),
  ]);
  readonly scopeReady = computed(() => this.scope() !== 'lesson' || !!this.lessonId());

  ngOnInit(): void {
    // the reserved conversation lesson is not a document a learner uploaded, so it never appears here
    this.wiki.documents(this.kbId()).subscribe((docs) => {
      const lessons = new Map(docs.map((doc) => [doc.lessonId, doc.lessonTitle]));
      this.lessons.set([...lessons].map(([id, title]) => ({ id, title })));
    });
    if (this.pageId()) {
      this.scope.set('page');
    }
  }

  /** Space reveals a card, 0-5 grade it, Enter moves to the next quiz question; typing in a field is never caught. */
  onKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (target?.closest('input, textarea, select, [contenteditable], mat-select')) {
      return;
    }
    if (this.mode() === 'cards' && this.card()) {
      if (!this.showBack() && (event.key === ' ' || event.key === 'Enter')) {
        event.preventDefault();
        this.showBack.set(true);
      } else if (this.showBack() && /^[0-5]$/.test(event.key)) {
        this.rate(Number(event.key));
      }
    } else if (this.mode() === 'quiz' && this.evaluation() && event.key === 'Enter') {
      event.preventDefault();
      this.next();
    }
  }

  /** Shows the due cards at once, then cuts the missing ones and adds them to the end of the deck. */
  openDeck(): void {
    this.error.set(null);
    this.loadingDeck.set(true);
    const scope = this.studyScope();
    this.study.flashcards(this.kbId(), scope).subscribe({
      next: (cards) => {
        this.cards.set(cards);
        this.cardIndex.set(0);
        this.showBack.set(false);
        this.reviewed.set(0);
        this.remembered.set(0);
        this.session.set(null);
        this.mode.set('cards');
        this.refreshDeck(scope);
      },
      error: (response: HttpErrorResponse) => this.fail(response, () => this.loadingDeck.set(false)),
    });
  }

  private refreshDeck(scope: StudyScope): void {
    this.study.refreshFlashcards(this.kbId(), scope).subscribe({
      next: (fresh) => {
        this.loadingDeck.set(false);
        const known = new Set(this.cards().map((card) => card.cardId));
        const added = fresh.filter((card) => !known.has(card.cardId));
        if (added.length) {
          this.cards.update((cards) => [...cards, ...added]);
        }
      },
      // the deck already shown stays usable when the model cannot cut more cards
      error: (response: HttpErrorResponse) => this.fail(response, () => this.loadingDeck.set(false)),
    });
  }

  rate(rating: number): void {
    const card = this.card();
    if (!card) {
      return;
    }
    this.study.review(this.kbId(), card.cardId, rating).subscribe(() => {
      this.reviewed.update((n) => n + 1);
      if (rating >= 3) {
        this.remembered.update((n) => n + 1);
      }
      this.cardIndex.update((i) => i + 1);
      this.showBack.set(false);
    });
  }

  startQuiz(): void {
    this.error.set(null);
    this.startingQuiz.set(true);
    this.study.startQuiz(this.kbId(), this.studyScope()).subscribe({
      next: (session) => {
        this.session.set(session);
        this.question.set(null);
        this.results.set([]);
        this.finished.set(false);
        this.mode.set('quiz');
        this.next();
      },
      error: (response: HttpErrorResponse) => this.fail(response, () => this.startingQuiz.set(false)),
    });
  }

  submitAnswer(): void {
    if (!this.answer().trim() || this.grading()) {
      return;
    }
    this.error.set(null);
    this.grading.set(true);
    this.study.answer(this.kbId(), this.sessionId(), this.answer()).subscribe({
      next: (evaluation) => {
        this.grading.set(false);
        this.evaluation.set(evaluation);
        const question = this.question()?.question ?? '';
        this.results.update((results) => [...results, { question, score: evaluation.score, feedback: evaluation.feedback }]);
        this.answer.set('');
      },
      error: (response: HttpErrorResponse) => this.fail(response, () => this.grading.set(false)),
    });
  }

  next(): void {
    this.evaluation.set(null);
    this.study.next(this.kbId(), this.sessionId()).subscribe({
      next: (question) => {
        this.startingQuiz.set(false);
        this.question.set(question);
        this.finished.set(question === null);
      },
      error: (response: HttpErrorResponse) => this.fail(response, () => this.startingQuiz.set(false)),
    });
  }

  exit(): void {
    this.mode.set('setup');
    this.cards.set([]);
    this.session.set(null);
    this.question.set(null);
    this.evaluation.set(null);
    this.error.set(null);
  }

  verdict(score: number): string {
    return ['Not yet', 'Weak', 'Partial', 'Good', 'Very good', 'Excellent'][score] ?? '';
  }

  private fail(response: HttpErrorResponse, settle: () => void): void {
    settle();
    const error = response.error as ApiError | null;
    this.error.set(error?.code === 'NOT_FOUND'
      ? 'There are no pages to study in this scope — upload a document first and wait until the ingest ends.'
      : apiErrorMessage(error));
  }

  private sessionId(): string {
    return this.session()?.sessionId ?? '';
  }

  private studyScope(): StudyScope {
    if (this.scope() === 'lesson' && this.lessonId()) {
      return { lessonId: this.lessonId() };
    }
    const pageId = this.pageId();
    return this.scope() === 'page' && pageId ? { pageId } : {};
  }
}
