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
        String body = "# Instalacja\n\n```bash\n# zainstaluj pakiet\napt install x\n```\n\n~~~\n# też nie\n~~~\n";

        assertThat(MarkdownStructure.sections(body)).extracting(Section::anchor).containsExactly("instalacja");
    }

    @Test
    void shouldCloseAFenceOnlyWithAtLeastAsManyOfTheSameCharacter() {
        String body = "````\n```\n# wciąż kod\n~~~~\n````\n# Po kodzie\n";

        assertThat(MarkdownStructure.sections(body)).extracting(Section::heading).containsExactly("Po kodzie");
    }

    @Test
    void shouldRunAnUnclosedFenceToTheEndOfTheBody() {
        assertThat(MarkdownStructure.sections("# Faza\n```\n# kod\n")).extracting(Section::heading)
            .containsExactly("Faza");
    }

    @Test
    void shouldTreatOnlyLevelOneHeadingsAtColumnZeroAsSections() {
        String body = "# Mitoza\n\n## Profaza\n\n #wcięty\n#bez-spacji\n\n# Mejoza\n";

        assertThat(MarkdownStructure.sections(body)).extracting(Section::heading).containsExactly("Mitoza", "Mejoza");
    }

    @Test
    void shouldRangeASectionFromItsHeadingToTheNextLevelOneHeading() {
        String body = "Wstęp\n# Mitoza\n## Profaza\ntekst\n# Mejoza\nkoniec\n";

        assertThat(MarkdownStructure.sections(body)).containsExactly(
            new Section("Mitoza", "mitoza", 6, 32),
            new Section("Mejoza", "mejoza", 32, body.length()));
    }

    @Test
    void shouldAnchorByTheSlugOfTheLinkStrippedHeadingWithoutClosingHashes() {
        String body = "# Faza [anafazy](/concepts/anafaza.md) ##\n";

        assertThat(MarkdownStructure.sections(body)).containsExactly(
            new Section("Faza [anafazy](/concepts/anafaza.md)", "faza-anafazy", 0, body.length()));
    }

    @Test
    void shouldFindTheFirstSectionWhenAnchorsRepeat() {
        String body = "# Faza\npierwsza\n# faza!\ndruga\n";

        assertThat(MarkdownStructure.section(body, "faza")).map(Section::start).contains(0);
    }

    // ---------------------------------------------------------------------------
    // Links
    // ---------------------------------------------------------------------------

    @Test
    void shouldParseAnInternalLinkIntoItsPathAndFragment() {
        String body = "Zob. [anafazę](/concepts/anafaza.md#przebieg) i [lekcję](/sources/bio-3.md).\n";

        assertThat(MarkdownStructure.links(body))
            .extracting(Link::kind, Link::text, Link::targetPath, Link::fragment)
            .containsExactly(
                tuple(LinkKind.INTERNAL, "anafazę", "concepts/anafaza", "przebieg"),
                tuple(LinkKind.INTERNAL, "lekcję", "sources/bio-3", null));
    }

    @Test
    void shouldClassifyHttpLinksAndAutolinksAsExternal() {
        String body = "[PubMed](https://pubmed.ncbi.nlm.nih.gov/1) <http://example.com/a> "
            + "[Wiki](https://pl.wikipedia.org/wiki/Mitoza_(biologia))\n";

        assertThat(MarkdownStructure.links(body))
            .extracting(Link::kind, Link::destination)
            .containsExactly(
                tuple(LinkKind.EXTERNAL, "https://pubmed.ncbi.nlm.nih.gov/1"),
                tuple(LinkKind.EXTERNAL, "http://example.com/a"),
                tuple(LinkKind.EXTERNAL, "https://pl.wikipedia.org/wiki/Mitoza_(biologia)"));
    }

    @Test
    void shouldClassifyEveryOtherDestinationAsInvalidWithoutNormalisingIt() {
        List<String> destinations = List.of("./mitoza.md", "mitoza.md", "/concepts/mitoza", "/concepts/Mitoza.md",
            "/concepts/mito%C5%BCa.md", "/topics/mitoza.md", "/concepts/index.md", "mailto:a@b.pl", "#faza");

        for (String destination : destinations) {
            assertThat(MarkdownStructure.links("[x](" + destination + ")\n"))
                .as(destination)
                .extracting(Link::kind, Link::destination)
                .containsExactly(tuple(LinkKind.INVALID, destination));
        }
    }

    @Test
    void shouldClassifyALinkWhoseTextHoldsBracketsOrALineBreak() {
        String body = "[Mitoza [faza]](/concepts/mitoza.md) i [dwie\nlinie](mejoza.md)\n";

        assertThat(MarkdownStructure.links(body))
            .extracting(Link::kind, Link::text)
            .containsExactly(tuple(LinkKind.INTERNAL, "Mitoza [faza]"), tuple(LinkKind.INVALID, "dwie\nlinie"));
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
        String body = "![schemat](/concepts/mitoza.md)\n\n[mitoza]: /concepts/mitoza.md\n";

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
        String text = "Faza [anafazy](/concepts/anafaza.md), ![img](x.png), <https://a.pl> i `[k](y)`";

        assertThat(MarkdownStructure.stripLinks(text))
            .isEqualTo("Faza anafazy, ![img](x.png), <https://a.pl> i `[k](y)`");
    }

    // ---------------------------------------------------------------------------
    // Spans eligible for link insertion
    // ---------------------------------------------------------------------------

    @Test
    void shouldExcludeHeadingsTagsCodeSpansAutolinksLinksAndFencesFromInsertion() {
        String body = "# Mitoza\nPodział <b>jądra</b> i `kod` oraz <https://a.pl> [link](/concepts/a.md).\n"
            + "```\nmitoza\n```\n## Profaza\nKoniec\n";

        assertThat(MarkdownStructure.eligibleSpans(body))
            .extracting(span -> body.substring(span.start(), span.end()))
            .containsExactly("\nPodział ", "jądra", " i ", " oraz ", " ", ".\n", "\nKoniec\n");
    }
}
