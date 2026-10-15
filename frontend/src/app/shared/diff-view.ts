import { Component, computed, input } from '@angular/core';

import { lineDiff } from './diff';

@Component({
  selector: 'app-diff-view',
  standalone: true,
  template: `
    <pre class="diff" aria-label="Content changes">@for (part of parts(); track $index) {<span [class]="'diff-' + part.kind">{{ part.text }}</span>}</pre>
  `,
  styles: `.diff { margin: 0; white-space: pre-wrap; overflow-x: auto; padding: 1rem; font: 0.8125rem/1.6 var(--mf-mono);
    color: var(--mf-muted); background: #0d0f18; border: 1px solid var(--mf-border); border-radius: var(--mf-radius-sm); }`,
})
export class DiffView {
  readonly before = input<string | null | undefined>(null);
  readonly after = input<string | null | undefined>(null);
  readonly parts = computed(() => lineDiff(this.before(), this.after()));
}
