package dev.mindforge.infrastructure.config;

import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.RunReportQuery;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.persistence.adapter.DocumentRepositoryAdapter;
import dev.mindforge.infrastructure.persistence.adapter.IngestRunRepositoryAdapter;
import dev.mindforge.infrastructure.persistence.adapter.RunReportQueryAdapter;
import dev.mindforge.infrastructure.persistence.adapter.WikiStoreAdapter;
import dev.mindforge.infrastructure.persistence.jpa.DocumentJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.IngestRunJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageLinkJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageRevisionJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageSourceJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageSupersessionJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.WikiPageJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.DocumentEntityMapper;
import dev.mindforge.infrastructure.persistence.mapper.IngestRunEntityMapper;
import dev.mindforge.infrastructure.persistence.mapper.WikiEntityMapper;
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

    @Bean
    WikiStore wikiStore(WikiPageJpaRepository pages, PageRevisionJpaRepository revisions,
                        PageLinkJpaRepository links, PageSourceJpaRepository sources,
                        PageSupersessionJpaRepository supersessions, WikiEntityMapper mapper) {
        return new WikiStoreAdapter(pages, revisions, links, sources, supersessions, mapper);
    }

    @Bean
    IngestRunRepository ingestRunRepository(IngestRunJpaRepository runs, IngestRunEntityMapper mapper) {
        return new IngestRunRepositoryAdapter(runs, mapper);
    }

    @Bean
    RunReportQuery runReportQuery(IngestRunJpaRepository runs, IngestRunEntityMapper mapper) {
        return new RunReportQueryAdapter(runs, mapper);
    }
}
