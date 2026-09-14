package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.service.HealthService;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.KnowledgeBaseHealth;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.port.WikiHealthQuery;
import dev.mindforge.domain.port.WikiStore;

class HealthServiceTest {

    private static final UUID KB = UUID.randomUUID();

    private final WikiHealthQuery health = mock(WikiHealthQuery.class);
    private final WikiStore wiki = mock(WikiStore.class);

    @Test
    void shouldReportASupersessionWhoseAnchorExistsOnlyInsideAFenceOrWhoseSupersedingPageIsGone() {
        UUID fenced = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        String body = "# Faza\n\nProfaza.\n\n```\n# Etapy\n```\n";
        when(health.supersessionsToCheck(KB)).thenReturn(List.of(
            new WikiHealthQuery.SupersessionToCheck(fenced, "concepts/mitoza", "etapy", body, "concepts/mejoza"),
            new WikiHealthQuery.SupersessionToCheck(gone, "concepts/mitoza", "faza", body, null),
            new WikiHealthQuery.SupersessionToCheck(UUID.randomUUID(), "concepts/mitoza", "faza", body,
                "concepts/mejoza")));

        assertThat(new HealthService(health, wiki).health(KB).danglingSupersessions())
            .extracting(KnowledgeBaseHealth.DanglingSupersession::supersessionId)
            .containsExactly(fenced, gone);
    }

    @Test
    void shouldShowTheIndexAgainstItsCeilingAndSayWhenThePrefilterIsDue() {
        when(wiki.listIndex(KB)).thenReturn(List.of(
            new IndexEntry("concepts/mitoza", "Mitoza", "x".repeat(70_000), PageType.CONCEPT)));

        KnowledgeBaseHealth report = new HealthService(health, wiki).health(KB);

        assertThat(report.indexTokens()).isGreaterThan(20_000);
        assertThat(report.indexCeilingTokens()).isEqualTo(20_000);
        assertThat(report.prefilterDue()).isTrue();
    }
}
