import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ConceptGraphResponse,
  FlashcardReview,
  FlashcardsDueResponse,
  LessonDetail,
  QuizAnswerRequest,
  QuizEvaluation,
  QuizQuestion,
  QuizStartRequest,
  ReviewRequest,
  ReviewResponse,
  SearchRequest,
  SearchResponse,
  UploadResponse,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  // Lessons
  getLessons(): Observable<LessonDetail[]> {
    return this.http.get<LessonDetail[]>('/api/lessons');
  }

  uploadLesson(file: File): Observable<UploadResponse> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<UploadResponse>('/api/lessons/upload', fd);
  }

  // Concepts
  getConceptGraph(lesson?: string): Observable<ConceptGraphResponse> {
    let params = new HttpParams();
    if (lesson) params = params.set('lesson', lesson);
    return this.http.get<ConceptGraphResponse>('/api/concepts/graph', { params });
  }

  // Quiz
  startQuiz(req: QuizStartRequest): Observable<QuizQuestion[]> {
    return this.http.post<QuizQuestion[]>('/api/quiz/start', req);
  }

  answerQuiz(req: QuizAnswerRequest): Observable<QuizEvaluation> {
    return this.http.post<QuizEvaluation>('/api/quiz/answer', req);
  }

  // Flashcards
  getDueFlashcards(lesson?: string): Observable<FlashcardsDueResponse> {
    let params = new HttpParams();
    if (lesson) params = params.set('lesson', lesson);
    return this.http.get<FlashcardsDueResponse>('/api/flashcards/due', { params });
  }

  getAllFlashcards(lesson?: string): Observable<FlashcardReview[]> {
    let params = new HttpParams();
    if (lesson) params = params.set('lesson', lesson);
    return this.http.get<FlashcardReview[]>('/api/flashcards/all', { params });
  }

  reviewFlashcard(req: ReviewRequest): Observable<ReviewResponse> {
    return this.http.post<ReviewResponse>('/api/flashcards/review', req);
  }

  // Search
  search(req: SearchRequest): Observable<SearchResponse> {
    return this.http.post<SearchResponse>('/api/search', req);
  }
}
