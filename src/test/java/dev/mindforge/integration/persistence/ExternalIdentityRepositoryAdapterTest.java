package dev.mindforge.integration.persistence;

import static dev.mindforge.support.TestFixtures.makeUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.mindforge.domain.model.ExternalIdentity;
import dev.mindforge.domain.model.ExternalIdentityConflictException;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.User;
import dev.mindforge.domain.port.ExternalIdentityRepository;
import dev.mindforge.support.TestContainerBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class ExternalIdentityRepositoryAdapterTest extends TestContainerBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExternalIdentityRepository adapter;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    }

    @Test
    void migration_createsTheTableWithItsKeyCascadeAndUserIndex() {
        assertThat(jdbcTemplate.queryForList(
            "SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid"
                + " AND a.attnum = ANY(i.indkey)"
                + " WHERE i.indrelid = 'external_identities'::regclass AND i.indisprimary", String.class))
            .containsExactlyInAnyOrder("platform", "external_id");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT confdeltype FROM pg_constraint WHERE conrelid = 'external_identities'::regclass AND contype = 'f'",
            String.class))
            .isEqualTo("c");
        assertThat(jdbcTemplate.queryForList(
            "SELECT indexdef FROM pg_indexes WHERE tablename = 'external_identities'", String.class))
            .anyMatch(definition -> definition.endsWith("(user_id)"));
    }

    @Test
    void createUserAndLink_insertsBothRows_andFindUserIdReturnsTheUser() {
        User user = makeUser(null, "discord-1@example.com");

        User created = adapter.createUserAndLink(ExternalPlatform.DISCORD, "1001", user);

        assertThat(created.userId()).isEqualTo(user.userId());
        assertThat(adapter.findUserId(ExternalPlatform.DISCORD, "1001")).contains(user.userId());
    }

    @Test
    void findUserId_returnsEmpty_forAnUnknownIdentity() {
        assertThat(adapter.findUserId(ExternalPlatform.DISCORD, "unknown")).isEmpty();
    }

    @Test
    void createUserAndLink_forAnIdentityAlreadyLinked_throwsAndWritesNoUser() {
        User first = makeUser(null, "discord-first@example.com");
        adapter.createUserAndLink(ExternalPlatform.DISCORD, "1001", first);
        User second = makeUser(null, "discord-second@example.com");

        assertThatExceptionOfType(ExternalIdentityConflictException.class)
            .isThrownBy(() -> adapter.createUserAndLink(ExternalPlatform.DISCORD, "1001", second));

        assertThat(countUsers()).isEqualTo(1);
        assertThat(adapter.findUserId(ExternalPlatform.DISCORD, "1001")).contains(first.userId());
    }

    @Test
    void createUserAndLink_forAnEmailAlreadyTaken_throwsAndWritesNoUserOrIdentity() {
        adapter.createUserAndLink(ExternalPlatform.DISCORD, "1001", makeUser(null, "taken@example.com"));

        assertThatExceptionOfType(ExternalIdentityConflictException.class)
            .isThrownBy(() -> adapter.createUserAndLink(
                ExternalPlatform.DISCORD, "1002", makeUser(null, "taken@example.com")));

        assertThat(countUsers()).isEqualTo(1);
        assertThat(adapter.findUserId(ExternalPlatform.DISCORD, "1002")).isEmpty();
    }

    @Test
    void findByPlatform_returnsEveryIdentityWithItsUserId() {
        User ann = makeUser(null, "ann@example.com");
        User bob = makeUser(null, "bob@example.com");
        adapter.createUserAndLink(ExternalPlatform.DISCORD, "1001", ann);
        adapter.createUserAndLink(ExternalPlatform.DISCORD, "1002", bob);

        assertThat(adapter.findByPlatform(ExternalPlatform.DISCORD)).containsExactlyInAnyOrder(
            new ExternalIdentity(ExternalPlatform.DISCORD, "1001", ann.userId()),
            new ExternalIdentity(ExternalPlatform.DISCORD, "1002", bob.userId()));
    }

    @Test
    void slackAndDiscordLinksWithTheSameRawId_coexistAndAreFoundByTheirOwnPlatform() {
        User discordUser = makeUser(null, "discord-same@example.com");
        User slackUser = makeUser(null, "slack-same@example.com");
        adapter.createUserAndLink(ExternalPlatform.DISCORD, "T1-U1", discordUser);
        adapter.createUserAndLink(ExternalPlatform.SLACK, "T1-U1", slackUser);

        assertThat(adapter.findUserId(ExternalPlatform.DISCORD, "T1-U1")).contains(discordUser.userId());
        assertThat(adapter.findUserId(ExternalPlatform.SLACK, "T1-U1")).contains(slackUser.userId());
        assertThat(adapter.findByPlatform(ExternalPlatform.SLACK))
            .containsExactly(new ExternalIdentity(ExternalPlatform.SLACK, "T1-U1", slackUser.userId()));
    }

    @Test
    void deletingTheUser_removesItsIdentity() {
        User user = makeUser(null, "gone@example.com");
        adapter.createUserAndLink(ExternalPlatform.DISCORD, "1001", user);

        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", user.userId());

        assertThat(adapter.findUserId(ExternalPlatform.DISCORD, "1001")).isEmpty();
        assertThat(adapter.findByPlatform(ExternalPlatform.DISCORD)).isEmpty();
    }

    private int countUsers() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }
}
