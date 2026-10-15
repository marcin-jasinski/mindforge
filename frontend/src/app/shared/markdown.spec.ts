import { renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
  it('opens a wiki page link inside the SPA', () => {
    expect(renderMarkdown('[Foo](/concepts/foo.md)', 'kb1')).toContain('href="/kb/kb1/pages/concepts/foo"');
  });

  it('drops the fragment of a page link', () => {
    expect(renderMarkdown('[Foo](/sources/a-b.md#sec)', 'kb1')).toContain('href="/kb/kb1/pages/sources/a-b"');
  });

  it('marks an external link noopener and nofollow', () => {
    expect(renderMarkdown('[x](https://example.com)', 'kb1')).toContain('rel="noopener noreferrer nofollow"');
  });

  it('renders any other link target as plain text', () => {
    const html = renderMarkdown('[x](javascript:alert(1))', 'kb1');
    expect(html).not.toContain('href');
    expect(html).toContain('x');
  });

  it('escapes raw HTML instead of executing it', () => {
    const html = renderMarkdown('<script>alert(1)</script>', 'kb1');
    expect(html).not.toContain('<script>');
    expect(html).toContain('&lt;script&gt;');
  });
});
