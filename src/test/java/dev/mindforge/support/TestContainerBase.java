package dev.mindforge.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class TestContainerBase {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer postgres =
        new PostgreSQLContainer("pgvector/pgvector:pg15")
            .withDatabaseName("mindforge_test")
            .withUsername("mindforge")
            .withPassword("mindforge");

    @SuppressWarnings("resource")
    static final Neo4jContainer neo4j =
        new Neo4jContainer("neo4j:5")
            .withoutAuthentication();

    static {
        postgres.start();
        neo4j.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "");
        registry.add("spring.neo4j.authentication.password", () -> "");
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-google-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-google-client-secret");
        registry.add("spring.security.oauth2.client.registration.github.client-id", () -> "test-github-client-id");
        registry.add("spring.security.oauth2.client.registration.github.client-secret", () -> "test-github-client-secret");
    }
}
