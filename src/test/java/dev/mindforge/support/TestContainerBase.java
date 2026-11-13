package dev.mindforge.support;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(StubAiConfig.class)
// Cached contexts share the database: a periodic sweep would settle runs another test class holds open. A test property,
// not a dynamic one, so that a subclass's @DynamicPropertySource can override it.
@TestPropertySource(properties = "mindforge.runs.sweep-interval=PT1H")
public abstract class TestContainerBase {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:15")
            .withDatabaseName("mindforge_test")
            .withUsername("mindforge")
            .withPassword("mindforge");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-google-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-google-client-secret");
        registry.add("spring.security.oauth2.client.registration.github.client-id", () -> "test-github-client-id");
        registry.add("spring.security.oauth2.client.registration.github.client-secret", () -> "test-github-client-secret");
    }
}
