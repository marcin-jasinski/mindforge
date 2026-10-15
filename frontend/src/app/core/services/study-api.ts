import { Injectable, inject } from '@angular/core';
import { Observable, defer, of, switchMap, timer } from 'rxjs';

import { Evaluation, Flashcard, NextQuestion, QuizSession, StudyScope } from '../models/api';
import { ApiService } from './api.service';

const QUIZ_POLL_MS = 1500;

/** Owns the endpoint paths of flashcards and quizzes. */
@Injectable({ providedIn: 'root' })
export class StudyApi {
  private readonly api = inject(ApiService);

  flashcards(kbId: string, scope: StudyScope): Observable<Flashcard[]> {
    const query = scope.lessonId
      ? `?lessonId=${encodeURIComponent(scope.lessonId)}`
      : scope.pageId ? `?pageId=${encodeURIComponent(scope.pageId)}` : '';
    return this.api.get(`/knowledge-bases/${kbId}/flashcards${query}`, true);
  }

  /** Cuts the missing or stale cards, which waits for the model, and returns the due deck. */
  refreshFlashcards(kbId: string, scope: StudyScope): Observable<Flashcard[]> {
    const query = scope.lessonId
      ? `?lessonId=${encodeURIComponent(scope.lessonId)}`
      : scope.pageId ? `?pageId=${encodeURIComponent(scope.pageId)}` : '';
    return this.api.post(`/knowledge-bases/${kbId}/flashcards/refresh${query}`, {}, true);
  }

  review(kbId: string, cardId: string, rating: number): Observable<unknown> {
    return this.api.post(`/knowledge-bases/${kbId}/flashcards/${cardId}/reviews`, { rating });
  }

  startQuiz(kbId: string, scope: StudyScope): Observable<QuizSession> {
    return this.api.post(`/knowledge-bases/${kbId}/quiz-sessions`, scope, true);
  }

  answer(kbId: string, sessionId: string, answer: string): Observable<Evaluation> {
    return this.api.post(`/knowledge-bases/${kbId}/quiz-sessions/${sessionId}/answers`, { answer }, true);
  }

  /** The next question; null when the quiz is over. A quiz still being written (202) is asked again shortly. */
  next(kbId: string, sessionId: string): Observable<NextQuestion | null> {
    return defer(() =>
      this.api.getResponse<NextQuestion>(`/knowledge-bases/${kbId}/quiz-sessions/${sessionId}/next`, true),
    ).pipe(
      switchMap((response) =>
        response.status === 202
          ? timer(QUIZ_POLL_MS).pipe(switchMap(() => this.next(kbId, sessionId)))
          : of(response.status === 204 ? null : response.body),
      ),
    );
  }
}
