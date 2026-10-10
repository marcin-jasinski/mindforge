import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';

import { ApiError, Evaluation, Flashcard, NextQuestion, QuizSession, StudyScope } from '../../core/models/api';
import { StudyApi } from '../../core/services/study-api';
import { WikiApi } from '../../core/services/wiki-api';
import { apiErrorMessage } from '../../shared/labels';

type ScopeKind = 'whole' | 'lesson' | 'page';

@Component({
  selector: 'app-study',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatRadioModule, MatSelectModule],
  templateUrl: './study.html',
  styles: `.back { margin: 0; padding-top: 1rem; border-top: 1px solid var(--mat-sys-outline-variant); }`,
})
export class Study implements OnInit {
  private readonly study = inject(StudyApi);
  private readonly wiki = inject(WikiApi);

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
  /** SM-2 grades: 0-2 forget the card, 3-5 remember it with growing ease. */
  readonly ratings = [
    { value: 0, label: 'Zupełnie nie pamiętam' },
    { value: 1, label: 'Pamiętam mgliście' },
    { value: 2, label: 'Prawie pamiętam' },
    { value: 3, label: 'Pamiętam z trudem' },
    { value: 4, label: 'Pamiętam' },
    { value: 5, label: 'Pamiętam bez wysiłku' },
  ];
  /** Cards and quiz questions are cut by a model on demand and can take minutes on a slow one. */
  readonly loadingDeck = signal(false);
  readonly startingQuiz = signal(false);
  readonly grading = signal(false);
  readonly error = signal<string | null>(null);
  readonly scope = signal<ScopeKind>('whole');
  readonly lessonId = signal('');
  readonly answer = signal('');

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
        this.finished.set(false);
        this.next();
      },
      error: (response: HttpErrorResponse) => this.fail(response, () => this.startingQuiz.set(false)),
    });
  }

  submitAnswer(): void {
    this.error.set(null);
    this.grading.set(true);
    this.study.answer(this.kbId(), this.sessionId(), this.answer()).subscribe({
      next: (evaluation) => {
        this.grading.set(false);
        this.evaluation.set(evaluation);
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

  private fail(response: HttpErrorResponse, settle: () => void): void {
    settle();
    const error = response.error as ApiError | null;
    this.error.set(error?.code === 'NOT_FOUND'
      ? 'Brak stron do nauki w tym zakresie — najpierw prześlij dokument i poczekaj na koniec przetwarzania.'
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
