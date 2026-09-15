import { Marked, Renderer, Tokens } from 'marked';

const ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (character) => ESCAPES[character]);
}

/**
 * Renders a page body: raw HTML is escaped, never executed; a page link opens in the SPA; an external link gets
 * rel="noopener noreferrer nofollow".
 */
export function renderMarkdown(markdown: string, kbId: string): string {
  const renderer = new Renderer();
  renderer.html = ({ text }: Tokens.HTML | Tokens.Tag) => escapeHtml(text);
  renderer.image = ({ text }: Tokens.Image) => escapeHtml(text);
  renderer.link = function (this: Renderer, token: Tokens.Link) {
    const text = this.parser.parseInline(token.tokens);
    const page = /^\/(concepts|sources)\/([a-z0-9-]+)\.md(?:#[a-z0-9-]+)?$/.exec(token.href);
    if (page) {
      return `<a href="/kb/${kbId}/pages/${page[1]}/${page[2]}">${text}</a>`;
    }
    if (/^https?:\/\//.test(token.href)) {
      return `<a href="${escapeHtml(token.href)}" target="_blank" rel="noopener noreferrer nofollow">${text}</a>`;
    }
    return text;
  };
  return new Marked({ renderer, gfm: true }).parse(markdown, { async: false }) as string;
}
