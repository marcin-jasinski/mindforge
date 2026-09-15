import { Component, computed, input } from '@angular/core';

import { lineDiff } from './diff';

@Component({
  selector: 'app-diff-view',
  standalone: true,
  template: `
    <pre class="diff" aria-label="Zmiany w treści">@for (part of parts(); track $index) {<span [class]="'diff-' + part.kind">{{ part.text }}</span>}</pre>
  `,
  styles: `.diff { white-space: pre-wrap; overflow-x: auto; padding: 0.75rem; background: var(--mat-sys-surface-container); }`,
})
export class DiffView {
  readonly before = input<string | null | undefined>(null);
  readonly after = input<string | null | undefined>(null);
  readonly parts = computed(() => lineDiff(this.before(), this.after()));
}
