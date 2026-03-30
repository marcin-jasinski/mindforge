import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { LessonDetail, QuizQuestion, QuizEvaluation } from '../../core/models/api.models';

interface QuizResult {
  question: QuizQuestion;
  answer: string;
  evaluation: QuizEvaluation;
}

@Component({
  selector: 'app-quiz',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './quiz.component.html',
  styleUrl: './quiz.component.css',
})
export class QuizComponent implements OnInit {
  lessons = signal<LessonDetail[]>([]);
  selectedLesson = signal('');
  questionCount = signal(5);
  questions = signal<QuizQuestion[]>([]);
  currentIndex = signal(0);
  userAnswer = signal('');
  selectedOption = signal('');
  loading = signal(false);
  evaluating = signal(false);
  currentEval = signal<QuizEvaluation | null>(null);
  results = signal<QuizResult[]>([]);
  phase = signal<'setup' | 'quiz' | 'summary'>('setup');

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.getLessons().subscribe({
      next: lessons => this.lessons.set(lessons),
      error: () => {},
    });
  }

  startQuiz() {
    this.loading.set(true);
    this.results.set([]);
    this.currentIndex.set(0);
    this.currentEval.set(null);

    this.api.startQuiz({
      lesson: this.selectedLesson() || undefined,
      count: this.questionCount(),
    }).subscribe({
      next: q => {
        this.questions.set(q);
        this.phase.set('quiz');
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

  get currentQuestion(): QuizQuestion | null {
    const qs = this.questions();
    const i = this.currentIndex();
    return i < qs.length ? qs[i] : null;
  }

  submitAnswer() {
    const q = this.currentQuestion;
    if (!q) return;

    const answer = q.question_type === 'multiple_choice' ? this.selectedOption() : this.userAnswer();
    if (!answer.trim()) return;

    this.evaluating.set(true);
    this.api.answerQuiz({
      session_id: q.session_id,
      question_id: q.question_id,
      user_answer: answer,
    }).subscribe({
      next: ev => {
        this.currentEval.set(ev);
        this.results.update(r => [...r, { question: q, answer, evaluation: ev }]);
        this.evaluating.set(false);
      },
      error: () => {
        this.evaluating.set(false);
      },
    });
  }

  nextQuestion() {
    this.currentEval.set(null);
    this.userAnswer.set('');
    this.selectedOption.set('');
    const nextIdx = this.currentIndex() + 1;
    if (nextIdx >= this.questions().length) {
      this.phase.set('summary');
    } else {
      this.currentIndex.set(nextIdx);
    }
  }

  resetQuiz() {
    this.phase.set('setup');
    this.questions.set([]);
    this.results.set([]);
    this.currentIndex.set(0);
    this.currentEval.set(null);
    this.userAnswer.set('');
    this.selectedOption.set('');
  }

  avgScore(): number {
    const r = this.results();
    if (!r.length) return 0;
    return r.reduce((sum, x) => sum + x.evaluation.score, 0) / r.length;
  }
}
