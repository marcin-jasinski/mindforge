import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar } from '@angular/material/snack-bar';
import { QuizService } from '../../core/services/quiz.service';
import type { QuizQuestionResponse, AnswerEvaluationResponse } from '../../core/models/api.models';

type QuizState = 'idle' | 'question' | 'evaluated' | 'loading';

@Component({
  selector: 'app-quiz',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule,
    MatProgressSpinnerModule, MatChipsModule,
  ],
  templateUrl: './quiz.html',
  styleUrl: './quiz.scss',
})
export class QuizComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly quizService = inject(QuizService);
  private readonly snack = inject(MatSnackBar);

  readonly kbId = signal('');
  readonly state = signal<QuizState>('idle');
  readonly question = signal<QuizQuestionResponse | null>(null);
  readonly evaluation = signal<AnswerEvaluationResponse | null>(null);
  readonly userAnswer = signal('');

  ngOnInit() {
    this.kbId.set(this.route.snapshot.paramMap.get('kbId') ?? '');
  }

  startQuiz() {
    this.state.set('loading');
    this.evaluation.set(null);
    this.userAnswer.set('');
    this.quizService.startSession(this.kbId()).subscribe({
      next: q => { this.question.set(q); this.state.set('question'); },
      error: (err: Error) => { this.snack.open(err.message, 'Close', { duration: 4000 }); this.state.set('idle'); },
    });
  }

  submitAnswer() {
    const q = this.question();
    if (!q || !this.userAnswer().trim()) return;
    this.state.set('loading');
    this.quizService.submitAnswer(this.kbId(), q.session_id, {
      question_id: q.question_id,
      user_answer: this.userAnswer(),
    }).subscribe({
      next: eval_ => { this.evaluation.set(eval_); this.state.set('evaluated'); },
      error: (err: Error) => { this.snack.open(err.message, 'Close', { duration: 4000 }); this.state.set('question'); },
    });
  }

  scoreLabel(score: number): string {
    if (score >= 4) return 'Excellent!';
    if (score >= 3) return 'Good';
    if (score >= 2) return 'Partial';
    return 'Needs work';
  }

  scoreClass(score: number): string {
    if (score >= 4) return 'score-excellent';
    if (score >= 3) return 'score-good';
    if (score >= 2) return 'score-partial';
    return 'score-poor';
  }
}
