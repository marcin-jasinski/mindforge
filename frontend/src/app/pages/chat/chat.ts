import {
  Component,
  OnInit,
  AfterViewChecked,
  ChangeDetectionStrategy,
  inject,
  signal,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ChatService } from '../../core/services/chat.service';

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  sourceKeys?: string[];
}

@Component({
  selector: 'app-chat',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatChipsModule,
  ],
  templateUrl: './chat.html',
  styleUrl: './chat.scss',
})
export class ChatComponent implements OnInit, AfterViewChecked {
  @ViewChild('messageList') messageList!: ElementRef<HTMLDivElement>;

  private readonly route = inject(ActivatedRoute);
  private readonly chatService = inject(ChatService);
  private readonly snack = inject(MatSnackBar);

  readonly kbId = signal('');
  readonly sessionId = signal<string | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly inputText = signal('');
  readonly sending = signal(false);
  readonly starting = signal(false);

  private shouldScrollToBottom = false;

  ngOnInit() {
    this.kbId.set(this.route.snapshot.paramMap.get('kbId') ?? '');
    this.startSession();
  }

  ngAfterViewChecked() {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  startSession() {
    this.starting.set(true);
    this.chatService.startSession(this.kbId()).subscribe({
      next: session => { this.sessionId.set(session.session_id); this.starting.set(false); },
      error: (err: Error) => { this.snack.open(err.message, 'Close', { duration: 4000 }); this.starting.set(false); },
    });
  }

  send() {
    const text = this.inputText().trim();
    const sid = this.sessionId();
    if (!text || !sid || this.sending()) return;

    this.messages.update(msgs => [...msgs, { role: 'user', content: text }]);
    this.inputText.set('');
    this.sending.set(true);
    this.shouldScrollToBottom = true;

    this.chatService.sendMessage(sid, { message: text }).subscribe({
      next: resp => {
        this.messages.update(msgs => [
          ...msgs,
          { role: 'assistant', content: resp.answer, sourceKeys: resp.source_concept_keys },
        ]);
        this.sending.set(false);
        this.shouldScrollToBottom = true;
      },
      error: (err: Error) => {
        this.snack.open(err.message, 'Close', { duration: 4000 });
        this.sending.set(false);
      },
    });
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  private scrollToBottom() {
    try {
      const el = this.messageList?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    } catch { /* noop */ }
  }
}
