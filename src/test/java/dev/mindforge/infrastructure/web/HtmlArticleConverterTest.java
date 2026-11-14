package dev.mindforge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.BlockType;
import dev.mindforge.domain.model.ContentBlock;

class HtmlArticleConverterTest {

    private static final String BASE_URL = "https://example.org/articles/mitosis";

    private final HtmlArticleConverter converter = new HtmlArticleConverter();

    @Test
    void shouldMapHeadingsToHeadingBlocksWithTheirLevelInDocumentOrder() {
        ArticleText article = converter.convert("""
            <html><body>
              <h1>Mitosis</h1>
              <h2>Phases</h2>
              <h3>Prophase</h3>
            </body></html>
            """, BASE_URL);

        assertThat(article.blocks())
            .extracting(ContentBlock::blockType, ContentBlock::content, b -> b.metadata().get(ContentBlock.LEVEL),
                ContentBlock::position)
            .containsExactly(
                tuple(BlockType.HEADING, "Mitosis", 1, 0),
                tuple(BlockType.HEADING, "Phases", 2, 1),
                tuple(BlockType.HEADING, "Prophase", 3, 2));
    }

    @Test
    void shouldKeepWhitespaceAndLineBreaksOfPreformattedCode() {
        ArticleText article = converter.convert(
            "<p>Run:</p><pre><code>def f(x):\n    if x &lt; 1:\n        return x\n</code></pre>", BASE_URL);

        assertThat(article.blocks())
            .extracting(ContentBlock::blockType, ContentBlock::content)
            .containsExactly(
                tuple(BlockType.TEXT, "Run:"),
                tuple(BlockType.CODE, "def f(x):\n    if x < 1:\n        return x\n"));
    }

    @Test
    void shouldMapParagraphsAndListItemsToTextBlocksAndSkipEmptyElements() {
        ArticleText article = converter.convert("""
            <body>
              <p>Cells <em>divide</em> in two.</p>
              <p>   </p>
              <p>&nbsp;</p>
              <ul><li>prophase</li><li></li><li>metaphase</li></ul>
            </body>
            """, BASE_URL);

        assertThat(article.blocks())
            .extracting(ContentBlock::blockType, ContentBlock::content, ContentBlock::position)
            .containsExactly(
                tuple(BlockType.TEXT, "Cells divide in two.", 0),
                tuple(BlockType.TEXT, "prophase", 1),
                tuple(BlockType.TEXT, "metaphase", 2));
    }

    @Test
    void shouldDropTextInsideBoilerplateElements() {
        ArticleText article = converter.convert("""
            <html><head><style>p { color: red }</style><script>var head = 1;</script></head>
            <body>
              <header>Site header</header>
              <nav>Menu link</nav>
              <script>alert('body script')</script>
              <noscript>Enable JavaScript</noscript>
              <template><p>Template text</p></template>
              <iframe src="https://ads.example">Frame text</iframe>
              <p>Article text.</p>
              <aside>Related posts</aside>
              <footer>Copyright</footer>
            </body></html>
            """, BASE_URL);

        assertThat(article.blocks()).extracting(ContentBlock::content).containsExactly("Article text.");
        assertThat(article.text())
            .doesNotContain("color", "head", "Site header", "Menu link", "alert", "body script", "JavaScript",
                "Template text", "Frame text", "Related posts", "Copyright");
    }

    @Test
    void shouldDecodeEntitiesAndNeverEmitRealMarkup() {
        ArticleText article = converter.convert("""
            <div class="post"><p>Salt &amp; pepper&nbsp;mix, it&#x27;s fine.</p>
            <p>Write &lt;script&gt;alert(1)&lt;/script&gt; to test.</p>
            <p><a href="https://example.org/x">a link</a> and <span>inline</span></p>
            <script>stolen()</script></div>
            """, BASE_URL);

        assertThat(article.blocks())
            .extracting(ContentBlock::content)
            .containsExactly(
                "Salt & pepper mix, it's fine.",
                "Write <script>alert(1)</script> to test.",
                "a link and inline");
        assertThat(article.blocks())
            .extracting(ContentBlock::content)
            .allSatisfy(content -> assertThat(content).doesNotContain("<div", "<a href", "<span", "<p>", "stolen"));
        assertThat(article.text()).containsOnlyOnce("<script");
    }

    @Test
    void shouldStillYieldTextFromMalformedHtml() {
        ArticleText article = converter.convert("<html><body><p>First<p>Second <b>bold<div>Third", BASE_URL);

        assertThat(article.blocks())
            .extracting(ContentBlock::blockType, ContentBlock::content)
            .containsExactly(
                tuple(BlockType.TEXT, "First"),
                tuple(BlockType.TEXT, "Second bold"),
                tuple(BlockType.TEXT, "Third"));
    }

    @Test
    void shouldPreferTheTitleElement() {
        ArticleText article = converter.convert("""
            <head><title>  Mitosis explained  </title><meta property="og:title" content="OG title"></head>
            <body><h1>Heading title</h1></body>
            """, BASE_URL);

        assertThat(article.title()).isEqualTo("Mitosis explained");
    }

    @Test
    void shouldFallBackToOgTitleWhenTheTitleElementIsBlank() {
        ArticleText article = converter.convert("""
            <head><title>   </title><meta property="og:title" content="OG title"></head>
            <body><h1>Heading title</h1></body>
            """, BASE_URL);

        assertThat(article.title()).isEqualTo("OG title");
    }

    @Test
    void shouldFallBackToTheFirstH1WhenThereIsNoTitleOrOgTitle() {
        ArticleText article = converter.convert("""
            <head><meta property="og:title" content=" "></head>
            <body><h1>Heading title</h1><h1>Second heading</h1></body>
            """, BASE_URL);

        assertThat(article.title()).isEqualTo("Heading title");
    }

    @Test
    void shouldReturnNullTitleWhenThePageHasNone() {
        ArticleText article = converter.convert("<p>Only text.</p>", BASE_URL);

        assertThat(article.title()).isNull();
    }

    @Test
    void shouldGiveNoTitleNoBlocksAndEmptyTextForEmptyInput() {
        ArticleText article = converter.convert("", BASE_URL);

        assertThat(article.title()).isNull();
        assertThat(article.blocks()).isEmpty();
        assertThat(article.text()).isEmpty();
    }

    @Test
    void shouldJoinBlockContentWithBlankLines() {
        ArticleText article = converter.convert("<h1>Mitosis</h1><p>Cells divide.</p>", BASE_URL);

        assertThat(article.text()).isEqualTo("Mitosis\n\nCells divide.");
    }

    @Test
    void shouldBeDeterministicForTheSameHtml() {
        String html = """
            <html><head><title>Mitosis</title></head>
            <body><h1>Mitosis</h1><p>Cells divide.</p><pre>code  here</pre><ul><li>one</li></ul></body></html>
            """;

        assertThat(converter.convert(html, BASE_URL)).isEqualTo(converter.convert(html, BASE_URL));
    }
}
