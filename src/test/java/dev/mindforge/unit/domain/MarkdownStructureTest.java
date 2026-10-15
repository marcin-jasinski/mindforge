package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.MarkdownStructure;
import dev.mindforge.domain.model.MarkdownStructure.Link;
import dev.mindforge.domain.model.MarkdownStructure.LinkKind;
import dev.mindforge.domain.model.MarkdownStructure.Section;

class MarkdownStructureTest {

    // ---------------------------------------------------------------------------
    // Sections
    // ---------------------------------------------------------------------------

    @Test
    void shouldNotTreatACommentInsideAFenceAsAHeading() {
        String body = "# Installation\n\n```bash\n# install the package\napt install x\n```\n\n~~~\n# not this either\n~~~\n";

        assertThat(MarkdownStructure.sections(body)).extracting(Section::anchor).containsExactly("installation");
    }

    @Test
    void shouldCloseAFenceOnlyWithAtLeastAsManyOfTheSameCharacter() {
        String body = "````\n```\n# still code\n~~~~\n````\n# After the code\n";

        assertThat(MarkdownStructure.sections(body)).extracting(Section::heading).containsExactly("After the code");
    }

    @Test
    void shouldRunAnUnclosedFenceToTheEndOfTheBody() {
        assertThat(MarkdownStructure.sections("# Phase\n```\n# code\n")).extracting(Section::heading)
            .containsExactly("Phase");
    }

    @Test
    void shouldTreatOnlyLevelOneHeadingsAtColumnZeroAsSections() {
        String body = "# Mitosis\n\n## Prophase\n\n #indented\n#no-space\n\n# Meiosis\n";

        assertThat(MarkdownStructure.sections(body)).extracting(Section::heading).containsExactly("Mitosis", "Meiosis");
    }

    @Test
    void shouldRangeASectionFromItsHeadingToTheNextLevelOneHeading() {
        String body = "Introduction\n# Mitosis\n## Prophase\ntext\n# Meiosis\nend\n";

        assertThat(MarkdownStructure.sections(body)).containsExactly(
            new Section("Mitosis", "mitosis", 13, 40),
            new Section("Meiosis", "meiosis", 40, body.length()));
    }

    @Test
    void shouldAnchorByTheSlugOfTheLinkStrippedHeadingWithoutClosingHashes() {
        String body = "# Phase [anaphase](/concepts/anaphase.md) ##\n";

        assertThat(MarkdownStructure.sections(body)).containsExactly(
            new Section("Phase [anaphase](/concepts/anaphase.md)", "phase-anaphase", 0, body.length()));
    }

    @Test
    void shouldFindTheFirstSectionWhenAnchorsRepeat() {
        String body = "# Phase\nfirst\n# phase!\nsecond\n";

        assertThat(MarkdownStructure.section(body, "phase")).map(Section::start).contains(0);
    }

    // ---------------------------------------------------------------------------
    // Links
    // ---------------------------------------------------------------------------

    @Test
    void shouldParseAnInternalLinkIntoItsPathAndFragment() {
        String body = "See [anaphase](/concepts/anaphase.md#course) and [lesson](/sources/bio-3.md).\n";

        assertThat(MarkdownStructure.links(body))
            .extracting(Link::kind, Link::text, Link::targetPath, Link::fragment)
            .containsExactly(
                tuple(LinkKind.INTERNAL, "anaphase", "concepts/anaphase", "course"),
                tuple(LinkKind.INTERNAL, "lesson", "sources/bio-3", null));
    }

    @Test
    void shouldClassifyHttpLinksAndAutolinksAsExternal() {
        String body = "[PubMed](https://pubmed.ncbi.nlm.nih.gov/1) <http://example.com/a> "
            + "[Wiki](https://en.wikipedia.org/wiki/Mitosis)\n";

        assertThat(MarkdownStructure.links(body))
            .extracting(Link::kind, Link::destination)
            .containsExactly(
                tuple(LinkKind.EXTERNAL, "https://pubmed.ncbi.nlm.nih.gov/1"),
                tuple(LinkKind.EXTERNAL, "http://example.com/a"),
                tuple(LinkKind.EXTERNAL, "https://en.wikipedia.org/wiki/Mitosis"));
    }

    @Test
    void shouldClassifyEveryOtherDestinationAsInvalidWithoutNormalisingIt() {
        List<String> destinations = List.of("./mitosis.md", "mitosis.md", "/concepts/mitosis", "/concepts/Mitosis.md",
            "/concepts/mito%C5%BCa.md", "/topics/mitosis.md", "/concepts/index.md", "mailto:a@b.pl", "#phase");

        for (String destination : destinations) {
            assertThat(MarkdownStructure.links("[x](" + destination + ")\n"))
                .as(destination)
                .extracting(Link::kind, Link::destination)
                .containsExactly(tuple(LinkKind.INVALID, destination));
        }
    }

    @Test
    void shouldClassifyALinkWhoseTextHoldsBracketsOrALineBreak() {
        String body = "[Mitosis [phase]](/concepts/mitosis.md) and [two\nlines](meiosis.md)\n";

        assertThat(MarkdownStructure.links(body))
            .extracting(Link::kind, Link::text)
            .containsExactly(tuple(LinkKind.INTERNAL, "Mitosis [phase]"), tuple(LinkKind.INVALID, "two\nlines"));
    }

    @Test
    void shouldClassifyEveryAutolinkButHttpAsInvalid() {
        String body = "<mailto:a@b.pl> <ftp://x.pl/a> <a@b.pl> i <b>pogrubienie</b>\n";

        assertThat(MarkdownStructure.links(body))
            .extracting(Link::kind, Link::destination)
            .containsExactly(
                tuple(LinkKind.INVALID, "mailto:a@b.pl"),
                tuple(LinkKind.INVALID, "ftp://x.pl/a"),
                tuple(LinkKind.INVALID, "a@b.pl"));
    }

    @Test
    void shouldTreatImagesAndReferenceDefinitionsAsInvalid() {
        String body = "![diagram](/concepts/mitosis.md)\n\n[mitosis]: /concepts/mitosis.md\n";

        assertThat(MarkdownStructure.links(body)).extracting(Link::kind)
            .containsExactly(LinkKind.INVALID, LinkKind.INVALID);
    }

    @Test
    void shouldIgnoreLinksInsideCodeSpansAndFences() {
        String body = "`[a](/concepts/a.md)` i ``[b](x) ` c``\n```\n[c](./c.md)\n```\n";

        assertThat(MarkdownStructure.links(body)).isEmpty();
    }

    @Test
    void shouldStripLinksToTheirTextKeepingImagesAutolinksAndCode() {
        String text = "Phase [anaphase](/concepts/anaphase.md), ![img](x.png), <https://a.pl> and `[k](y)`";

        assertThat(MarkdownStructure.stripLinks(text))
            .isEqualTo("Phase anaphase, ![img](x.png), <https://a.pl> and `[k](y)`");
    }

    // ---------------------------------------------------------------------------
    // Spans eligible for link insertion
    // ---------------------------------------------------------------------------

    @Test
    void shouldExcludeHeadingsTagsCodeSpansAutolinksLinksAndFencesFromInsertion() {
        String body = "# Mitosis\nDivision of the <b>nucleus</b> and `code` and <https://a.pl> [link](/concepts/a.md).\n"
            + "```\nmitosis\n```\n## Prophase\nEnd\n";

        assertThat(MarkdownStructure.eligibleSpans(body))
            .extracting(span -> body.substring(span.start(), span.end()))
            .containsExactly("\nDivision of the ", "nucleus", " and ", " and ", " ", ".\n", "\nEnd\n");
    }
}
