package dev.mindforge.infrastructure.config;

import dev.mindforge.domain.port.ArtifactRepository;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.infrastructure.persistence.ArtifactJpaRepository;
import dev.mindforge.infrastructure.persistence.ArtifactRepositoryAdapter;
import dev.mindforge.infrastructure.persistence.DocumentJpaRepository;
import dev.mindforge.infrastructure.persistence.DocumentRepositoryAdapter;
import dev.mindforge.infrastructure.persistence.StepCheckpointJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers persistence-layer adapter beans against their domain port interfaces. */
@Configuration
public class PersistenceConfig {

    @Bean
    DocumentRepository documentRepository(DocumentJpaRepository jpaRepository) {
        return new DocumentRepositoryAdapter(jpaRepository);
    }

    @Bean
    ArtifactRepository artifactRepository(
        ArtifactJpaRepository artifactJpaRepository,
        StepCheckpointJpaRepository checkpointJpaRepository) {
        return new ArtifactRepositoryAdapter(artifactJpaRepository, checkpointJpaRepository);
    }
}
