import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';

import { Evaluation, Flashcard, NextQuestion, QuizSession } from '../../core/models/api';
import { StudyApi, StudyScope } from '../../core/services/study-api';
import { WikiApi } from '../../core/services/wiki-api';

type ScopeKind = 'whole' | 'lesson' | 'page';

@Component({
  selector: 'app-study',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatRadioModule, MatSelectModule],
  templateUrl: './study.html',
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
  readonly ratings = [0, 1, 2, 3, 4, 5];
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

  openDeck(): void {
    this.study.flashcards(this.kbId(), this.studyScope()).subscribe((cards) => {
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
    this.study.review(this.kbId(), card.cardId, rating).subscribe(() => {
      this.cardIndex.update((i) => i + 1);
      this.showBack.set(false);
    });
  }

  startQuiz(): void {
    this.study.startQuiz(this.kbId(), this.studyScope()).subscribe((session) => {
      this.session.set(session);
      this.finished.set(false);
      this.next();
    });
  }

  submitAnswer(): void {
    this.study
      .answer(this.kbId(), this.sessionId(), this.answer())
      .subscribe((evaluation) => {
        this.evaluation.set(evaluation);
        this.answer.set('');
      });
  }

  next(): void {
    this.evaluation.set(null);
    this.study.next(this.kbId(), this.sessionId()).subscribe((question) => {
      this.question.set(question);
      this.finished.set(question === null);
    });
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
