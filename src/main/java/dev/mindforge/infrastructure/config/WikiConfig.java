package dev.mindforge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.HealthService;
import dev.mindforge.application.service.RevertService;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.WikiHealthQuery;
import dev.mindforge.domain.port.WikiStore;

/** Wires the wiki's application services. */
@Configuration
public class WikiConfig {

    @Bean
    RevertService revertService(IngestRunRepository ingestRunRepository, WikiStore wikiStore,
                                TransactionOperations transactionOperations) {
        return new RevertService(ingestRunRepository, wikiStore, transactionOperations);
    }

    @Bean
    HealthService healthService(WikiHealthQuery wikiHealthQuery, WikiStore wikiStore) {
        return new HealthService(wikiHealthQuery, wikiStore);
    }
}
