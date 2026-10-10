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

  it('shows the projected Citations section under its Polish name', () => {
    expect(renderMarkdown('# Citations\n\n[1] a', 'kb1')).toContain('<h1>Źródła</h1>');
  });

  it('leaves other headings alone', () => {
    expect(renderMarkdown('# Definicja', 'kb1')).toContain('<h1>Definicja</h1>');
  });

  it('escapes raw HTML instead of executing it', () => {
    const html = renderMarkdown('<script>alert(1)</script>', 'kb1');
    expect(html).not.toContain('<script>');
    expect(html).toContain('&lt;script&gt;');
  });
});
