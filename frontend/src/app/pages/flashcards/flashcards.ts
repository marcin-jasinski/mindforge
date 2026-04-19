import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FlashcardService } from '../../core/services/flashcard.service';
import type { FlashcardResponse } from '../../core/models/api.models';

@Component({
  selector: 'app-flashcards',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatCardModule, MatButtonModule, MatIconModule, MatProgressBarModule,
  ],
  templateUrl: './flashcards.html',
  styleUrl: './flashcards.scss',
})
export class FlashcardsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly flashcardService = inject(FlashcardService);
  private readonly snack = inject(MatSnackBar);

  readonly kbId = signal('');
  readonly cards = signal<FlashcardResponse[]>([]);
  readonly currentIndex = signal(0);
  readonly flipped = signal(false);
  readonly loading = signal(true);
  readonly done = signal(false);

  readonly currentCard = computed(() => this.cards()[this.currentIndex()] ?? null);
  readonly progress = computed(() =>
    this.cards().length ? Math.round((this.currentIndex() / this.cards().length) * 100) : 0,
  );

  ngOnInit() {
    this.kbId.set(this.route.snapshot.paramMap.get('kbId') ?? '');
    this.loadDueCards();
  }

  loadDueCards() {
    this.loading.set(true);
    this.done.set(false);
    this.currentIndex.set(0);
    this.flipped.set(false);
    this.flashcardService.getDueCards(this.kbId()).subscribe({
      next: cards => { this.cards.set(cards); this.loading.set(false); if (!cards.length) this.done.set(true); },
      error: () => this.loading.set(false),
    });
  }

  flip() {
    this.flipped.update(v => !v);
  }

  rate(rating: number) {
    const card = this.currentCard();
    if (!card) return;
    this.flashcardService.reviewCard(this.kbId(), { card_id: card.card_id, rating }).subscribe({
      error: (err: Error) => this.snack.open(err.message, 'Close', { duration: 3000 }),
    });
    this.next();
  }

  private next() {
    this.flipped.set(false);
    const nextIdx = this.currentIndex() + 1;
    if (nextIdx >= this.cards().length) {
      this.done.set(true);
    } else {
      this.currentIndex.set(nextIdx);
    }
  }
}
