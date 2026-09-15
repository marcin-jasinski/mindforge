import { diffLines } from 'diff';

export interface DiffPart {
  text: string;
  kind: 'added' | 'removed' | 'same';
}

/** A line diff for showing what a revision changed; a tombstone or a create diffs against nothing. */
export function lineDiff(before: string | null | undefined, after: string | null | undefined): DiffPart[] {
  return diffLines(before ?? '', after ?? '').map((part) => ({
    text: part.value,
    kind: part.added ? 'added' : part.removed ? 'removed' : 'same',
  }));
}

/** A page path as its two route segments. */
export function pageRoute(path: string): string[] {
  return ['pages', ...path.split('/')];
}
