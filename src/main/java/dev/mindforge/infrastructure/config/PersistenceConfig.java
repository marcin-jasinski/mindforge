package dev.mindforge.infrastructure.config;

import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.infrastructure.persistence.adapter.DocumentRepositoryAdapter;
import dev.mindforge.infrastructure.persistence.jpa.DocumentJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.DocumentEntityMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers persistence-layer adapter beans against their domain port interfaces. */
@Configuration
public class PersistenceConfig {

    @Bean
    DocumentRepository documentRepository(DocumentJpaRepository jpaRepository,
                                          DocumentEntityMapper mapper) {
        return new DocumentRepositoryAdapter(jpaRepository, mapper);
    }
}
