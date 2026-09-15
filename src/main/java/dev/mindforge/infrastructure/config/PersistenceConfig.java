package dev.mindforge.infrastructure.config;

import dev.mindforge.domain.port.BundleQuery;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.KnowledgeBaseRepository;
import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.domain.port.RunReportQuery;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.domain.port.UserRepository;
import dev.mindforge.domain.port.WikiHealthQuery;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.persistence.adapter.BundleQueryAdapter;
import dev.mindforge.infrastructure.persistence.adapter.DocumentRepositoryAdapter;
import dev.mindforge.infrastructure.persistence.adapter.IngestRunRepositoryAdapter;
import dev.mindforge.infrastructure.persistence.adapter.KnowledgeBaseRepositoryAdapter;
import dev.mindforge.infrastructure.persistence.adapter.QuizSessionStoreAdapter;
import dev.mindforge.infrastructure.persistence.adapter.RunReportQueryAdapter;
import dev.mindforge.infrastructure.persistence.adapter.StudyProgressStoreAdapter;
import dev.mindforge.infrastructure.persistence.adapter.UserRepositoryAdapter;
import dev.mindforge.infrastructure.persistence.adapter.WikiHealthQueryAdapter;
import dev.mindforge.infrastructure.persistence.adapter.WikiStoreAdapter;
import dev.mindforge.infrastructure.persistence.jpa.DocumentJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.FlashcardJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.IngestRunJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.KnowledgeBaseJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageLinkJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageRevisionJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageSourceJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageSupersessionJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.QuizSessionJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.StudyEventJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.UserJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.WikiHealthJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.WikiPageJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.DocumentEntityMapper;
import dev.mindforge.infrastructure.persistence.mapper.IngestRunEntityMapper;
import dev.mindforge.infrastructure.persistence.mapper.KnowledgeBaseEntityMapper;
import dev.mindforge.infrastructure.persistence.mapper.StudyEntityMapper;
import dev.mindforge.infrastructure.persistence.mapper.UserEntityMapper;
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
    WikiHealthQuery wikiHealthQuery(WikiHealthJpaRepository health) {
        return new WikiHealthQueryAdapter(health);
    }

    @Bean
    RunReportQuery runReportQuery(IngestRunJpaRepository runs, PageSupersessionJpaRepository supersessions,
                                  IngestRunEntityMapper mapper) {
        return new RunReportQueryAdapter(runs, supersessions, mapper);
    }

    @Bean
    StudyProgressStore studyProgressStore(FlashcardJpaRepository cards, StudyEventJpaRepository events,
                                          StudyEntityMapper mapper) {
        return new StudyProgressStoreAdapter(cards, events, mapper);
    }

    @Bean
    QuizSessionStore quizSessionStore(QuizSessionJpaRepository sessions, StudyEntityMapper mapper,
                                      AppProperties properties) {
        return new QuizSessionStoreAdapter(sessions, mapper, properties.getStudy().getQuizSessionTtl());
    }

    @Bean
    BundleQuery bundleQuery(PageSourceJpaRepository sources) {
        return new BundleQueryAdapter(sources);
    }

    @Bean
    UserRepository userRepository(UserJpaRepository users, UserEntityMapper mapper) {
        return new UserRepositoryAdapter(users, mapper);
    }

    @Bean
    KnowledgeBaseRepository knowledgeBaseRepository(KnowledgeBaseJpaRepository knowledgeBases,
                                                    KnowledgeBaseEntityMapper mapper) {
        return new KnowledgeBaseRepositoryAdapter(knowledgeBases, mapper);
    }
}
