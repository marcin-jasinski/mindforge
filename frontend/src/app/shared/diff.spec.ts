import { lineDiff, pageRoute } from './diff';

describe('lineDiff', () => {
  it('marks added, removed and unchanged lines', () => {
    expect(lineDiff('a\nb\n', 'a\nc\n')).toEqual([
      { text: 'a\n', kind: 'same' },
      { text: 'b\n', kind: 'removed' },
      { text: 'c\n', kind: 'added' },
    ]);
  });

  it('diffs a create and a tombstone against nothing', () => {
    expect(lineDiff(null, 'x\n')).toEqual([{ text: 'x\n', kind: 'added' }]);
    expect(lineDiff('x\n', undefined)).toEqual([{ text: 'x\n', kind: 'removed' }]);
  });

  it('reports no change as only unchanged parts', () => {
    expect(lineDiff('x\n', 'x\n').every((part) => part.kind === 'same')).toBe(true);
  });
});

describe('pageRoute', () => {
  it('splits a page path into route segments', () => {
    expect(pageRoute('concepts/foo')).toEqual(['pages', 'concepts', 'foo']);
  });
});
