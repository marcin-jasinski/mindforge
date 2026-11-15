package dev.mindforge.integration.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class ExternalIdentityProvisioningTest extends TestContainerBase {

    private static final String EXTERNAL_ID = "123456789012345678";

    @Autowired
    private ExternalIdentityService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    }

    @Test
    void firstContact_provisionsTheUserItsLinkAndADiscordKnowledgeBase() {
        UUID userId = service.resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, "Ada\nLovelace");

        Map<String, Object> user = jdbcTemplate.queryForMap(
            "SELECT email, password_hash, display_name FROM users WHERE user_id = ?", userId);
        assertThat(user.get("email")).isEqualTo("discord-" + EXTERNAL_ID + "@users.invalid");
        assertThat(user.get("password_hash")).isNull();
        assertThat(user.get("display_name")).isEqualTo("Ada Lovelace");
        assertThat(jdbcTemplate.queryForList("SELECT name FROM knowledge_bases WHERE owner_id = ?", String.class,
            userId)).containsExactly("Discord");
    }

    @Test
    void secondContact_returnsTheSameUserAndInsertsNothing() {
        UUID first = service.resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, "Ada");

        UUID second = service.resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, "Ada");

        assertThat(second).isEqualTo(first);
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("external_identities")).isEqualTo(1);
        assertThat(count("knowledge_bases")).isEqualTo(1);
    }

    @Test
    void slackContacts_provisionASlackUserOncePerHomeTeamAndUser() {
        UUID first = service.resolve(ExternalPlatform.SLACK, "T1-U1", "Ada");
        UUID again = service.resolve(ExternalPlatform.SLACK, "T1-U1", "Ada");
        UUID otherTeam = service.resolve(ExternalPlatform.SLACK, "T2-U1", "Ada");

        assertThat(again).isEqualTo(first);
        assertThat(otherTeam).isNotEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("SELECT email FROM users WHERE user_id = ?", String.class, first))
            .isEqualTo("slack-t1-u1@users.invalid");
        assertThat(jdbcTemplate.queryForList("SELECT name FROM knowledge_bases WHERE owner_id = ?", String.class,
            first)).containsExactly("Slack");
        assertThat(count("users")).isEqualTo(2);
        assertThat(count("external_identities")).isEqualTo(2);
        assertThat(count("knowledge_bases")).isEqualTo(2);
    }

    @Test
    void twoConcurrentFirstContacts_endWithOneUserOneLinkAndOneKnowledgeBase() throws Exception {
        List<UUID> userIds = resolveConcurrently(2);

        assertThat(userIds).hasSize(2);
        assertThat(userIds.get(1)).isEqualTo(userIds.get(0));
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("external_identities")).isEqualTo(1);
        assertThat(count("knowledge_bases")).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Starts every resolution of the same new identity at once and returns the user id each one got. */
    private List<UUID> resolveConcurrently(int callers) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<UUID>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.resolve(ExternalPlatform.DISCORD, EXTERNAL_ID, "Ada");
                }));
            }
            start.countDown();
            List<UUID> userIds = new ArrayList<>();
            for (Future<UUID> future : futures) {
                userIds.add(future.get());
            }
            return userIds;
        }
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
