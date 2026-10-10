import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Evaluation, Flashcard, NextQuestion, QuizSession } from '../models/api';
import { ApiService } from './api.service';

/** At most one of the two is set; neither means the whole knowledge base. */
export interface StudyScope {
  lessonId?: string;
  pageId?: string;
}

/** Owns the endpoint paths of flashcards and quizzes. */
@Injectable({ providedIn: 'root' })
export class StudyApi {
  private readonly api = inject(ApiService);

  flashcards(kbId: string, scope: StudyScope): Observable<Flashcard[]> {
    const query = scope.lessonId
      ? `?lessonId=${encodeURIComponent(scope.lessonId)}`
      : scope.pageId ? `?pageId=${encodeURIComponent(scope.pageId)}` : '';
    return this.api.get(`/knowledge-bases/${kbId}/flashcards${query}`);
  }

  review(kbId: string, cardId: string, rating: number): Observable<unknown> {
    return this.api.post(`/knowledge-bases/${kbId}/flashcards/${cardId}/reviews`, { rating });
  }

  startQuiz(kbId: string, scope: StudyScope): Observable<QuizSession> {
    return this.api.post(`/knowledge-bases/${kbId}/quiz-sessions`, scope);
  }

  answer(kbId: string, sessionId: string, answer: string): Observable<Evaluation & { finished: boolean }> {
    return this.api.post(`/knowledge-bases/${kbId}/quiz-sessions/${sessionId}/answers`, { answer });
  }

  next(kbId: string, sessionId: string): Observable<NextQuestion | null> {
    return this.api.get(`/knowledge-bases/${kbId}/quiz-sessions/${sessionId}/next`);
  }
}
