package dev.mindforge.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public abstract class TestContainerBase {

    @Container
    static final PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("mindforge_test")
            .withUsername("mindforge")
            .withPassword("mindforge");

    @Container
    static final Neo4jContainer neo4j =
        new Neo4jContainer("neo4j:5")
            .withoutAuthentication();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "");
        registry.add("spring.neo4j.authentication.password", () -> "");
    }
}
