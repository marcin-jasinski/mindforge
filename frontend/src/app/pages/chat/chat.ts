import { HttpErrorResponse } from '@angular/common/http';
import { TextFieldModule } from '@angular/cdk/text-field';
import { Component, DestroyRef, ElementRef, Injector, OnInit, afterNextRender, computed, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { filter, interval, switchMap, take } from 'rxjs';

import { ApiError, RunReport } from '../../core/models/api';
import { QueryApi } from '../../core/services/query-api';
import { RunApi } from '../../core/services/run-api';
import { ProgressService } from '../../core/services/progress.service';
import { WikiApi } from '../../core/services/wiki-api';
import { Icon } from '../../shared/icon';
import { renderMarkdown } from '../../shared/markdown';
import { PageLinksDirective } from '../../shared/page-links.directive';
import { apiErrorMessage, failureLine, failureReasonLabel, progressStepLabel } from '../../shared/labels';

interface Message {
  role: 'user' | 'assistant' | 'edit';
  text: string;
  html?: string;
  cited?: string[];
  run?: RunReport;
  /** An answer already sent to the wiki, so it is not saved twice. */
  saved?: boolean;
  /** A notice that something did not happen. */
  failed?: boolean;
}

const EDIT_NAMED_NO_PAGE = 'edit named no page';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, TextFieldModule, PageLinksDirective, Icon],
  templateUrl: './chat.html',
  styleUrl: './chat.scss',
})
export class Chat implements OnInit {
  private readonly query = inject(QueryApi);
  private readonly runs = inject(RunApi);
  private readonly progress = inject(ProgressService);
  private readonly wiki = inject(WikiApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly end = viewChild<ElementRef<HTMLElement>>('end');

  readonly kbId = input.required<string>();
  /** A question handed over by another view, e.g. a page's "Ask about this page". */
  readonly q = input<string>();
  readonly messages = signal<Message[]>([]);
  readonly busy = signal(false);
  /** The run of a pending wiki change: '' until the server accepts it, null when no change is pending. */
  private readonly editRun = signal<string | null>(null);
  /** The answer a pending edit saves; its mark is taken back when the save fails. */
  private savingAnswer: string | null = null;
  /** An edit is a whole ingest run and takes minutes on a local model, so it says so and names its step. */
  readonly editStatus = computed(() => {
    const runId = this.editRun();
    if (runId === null) {
      return null;
    }
    const step = runId ? this.progress.steps()[runId]?.step : null;
    return step ? `${progressStepLabel(step)}…` : 'Changing the wiki…';
  });
  protected readonly sessionId = signal('');
  readonly draft = signal('');
  private readonly titles = signal<Record<string, string>>({});
  protected readonly failureLine = failureLine;
  readonly suggestions = [
    'Summarize the key ideas of this knowledge base.',
    'Which concepts should I learn first?',
    'Explain the hardest concept in simple terms.',
  ];

  ngOnInit(): void {
    const handedOver = this.q();
    if (handedOver) {
      this.draft.set(handedOver);
      // used once: a reload must not put the question back in the box
      this.router.navigate([], { queryParams: { q: null }, queryParamsHandling: 'merge', replaceUrl: true });
    }
    this.query.open(this.kbId()).subscribe((session) => {
      this.sessionId.set(session.interactionId);
    });
    // a citation is a page path; the reader should see the page's title
    this.wiki.index(this.kbId()).subscribe((index) =>
      this.titles.set(Object.fromEntries(index.pages.map((page) => [page.path, page.title]))),
    );
  }

  ask(): void {
    const question = this.draft().trim();
    this.push({ role: 'user', text: question });
    this.draft.set('');
    this.busy.set(true);
    this.query.ask(this.kbId(), this.sessionId(), question).subscribe({
      next: (answer) =>
        this.push({ role: 'assistant', text: answer.answer, html: renderMarkdown(answer.answer, this.kbId()), cited: answer.citedPaths }),
      complete: () => this.busy.set(false),
      error: () => {
        // the question goes back to the box, not the thread, so it can be sent again
        this.dropLast();
        this.draft.set(question);
        this.busy.set(false);
      },
    });
  }

  edit(quotedAnswer?: string): void {
    const instruction = quotedAnswer ? 'Save this answer to the wiki.' : this.draft().trim();
    this.push({ role: 'user', text: instruction });
    const typed = this.draft();
    this.draft.set('');
    this.busy.set(true);
    this.editRun.set('');
    this.savingAnswer = quotedAnswer ?? null;
    this.markSaved(true);
    this.query.edit(this.kbId(), this.sessionId(), instruction, quotedAnswer).subscribe({
      next: (accepted) => {
        this.editRun.set(accepted.runId);
        this.follow(accepted.runId);
      },
      error: () => {
        this.dropLast();
        this.editRun.set(null);
        this.markSaved(false);
        this.draft.set(typed);
        this.busy.set(false);
      },
    });
  }

  revert(run: RunReport): void {
    // ponytail: native confirm, as on the run report; a MatDialog when it needs to show what will be undone
    if (!confirm('Revert this change? The pages go back to their previous version.')) {
      return;
    }
    this.runs.revert(this.kbId(), run.runId).subscribe({
      next: () => {
        // a reverted change cannot be reverted again
        this.messages.update((messages) => messages.map((message) => message.run?.runId === run.runId
          ? { ...message, run: { ...message.run, revertOffered: false } } : message));
        this.push({ role: 'edit', text: 'The change was reverted.' });
        this.progress.reload(this.kbId());
      },
      error: (response: HttpErrorResponse) =>
        this.push({ role: 'edit', failed: true,
          text: apiErrorMessage(response.error as ApiError | null, 'This change can no longer be reverted.') }),
    });
  }

  /** Enter asks, Shift+Enter adds a line. */
  onEnter(event: Event): void {
    if (!(event as KeyboardEvent).shiftKey) {
      event.preventDefault();
      if (this.draft().trim() && !this.busy()) {
        this.ask();
      }
    }
  }

  citedTitle(path: string): string {
    return this.titles()[path] ?? path;
  }

  pageLink(path: string): string[] {
    return ['..', 'pages', ...path.split('/')];
  }

  citedLink(path: string): string[] {
    return ['..', 'pages', ...path.split('/')];
  }

  /** Waits for the edit's run to end, then shows its report inline. */
  private follow(runId: string): void {
    interval(1000)
      .pipe(
        switchMap(() => this.runs.report(this.kbId(), runId)),
        filter((run) => run.status === 'COMPLETED' || run.status === 'FAILED'),
        take(1),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((run) => {
        this.progress.reload(this.kbId());
        this.busy.set(false);
        this.editRun.set(null);
        const text = run.failureReason === EDIT_NAMED_NO_PAGE
          ? 'I do not know which page to change — name the page or rephrase.'
          : run.status === 'COMPLETED' ? 'The wiki was changed.' : `Failed: ${failureReasonLabel(run.failureReason ?? '')}`;
        if (run.status === 'FAILED') {
          this.markSaved(false);
        }
        this.push({ role: 'edit', text, run, failed: run.status === 'FAILED' });
      });
  }

  private markSaved(saved: boolean): void {
    const answer = this.savingAnswer;
    if (answer !== null) {
      this.messages.update((messages) => messages.map((message) =>
        message.role === 'assistant' && message.text === answer ? { ...message, saved } : message));
    }
  }

  private dropLast(): void {
    this.messages.update((messages) => messages.slice(0, -1));
  }

  private push(message: Message): void {
    this.messages.update((messages) => [...messages, message]);
    afterNextRender(() => this.end()?.nativeElement.scrollIntoView?.({ behavior: 'smooth', block: 'end' }), { injector: this.injector });
  }
}
