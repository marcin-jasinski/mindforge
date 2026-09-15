package dev.mindforge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.HealthService;
import dev.mindforge.application.service.RevertService;
import dev.mindforge.application.service.RunReportService;
import dev.mindforge.application.service.WikiService;
import dev.mindforge.domain.port.BundleQuery;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;
import dev.mindforge.domain.port.RunReportQuery;
import dev.mindforge.domain.port.WikiHealthQuery;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.export.BundleExporter;

/** Wires the wiki's application services. */
@Configuration
public class WikiConfig {

    @Bean
    RevertService revertService(IngestRunRepository ingestRunRepository, WikiStore wikiStore,
                                ProgressNotifier progressNotifier, TransactionOperations transactionOperations) {
        return new RevertService(ingestRunRepository, wikiStore, progressNotifier, transactionOperations);
    }

    @Bean
    BundleExporter bundleExporter(WikiStore wikiStore, BundleQuery bundleQuery, RunReportQuery runReportQuery) {
        return new BundleExporter(wikiStore, bundleQuery, runReportQuery);
    }

    @Bean
    WikiService wikiService(WikiStore wikiStore, BundleQuery bundleQuery) {
        return new WikiService(wikiStore, bundleQuery);
    }

    @Bean
    RunReportService runReportService(RunReportQuery runReportQuery, IngestRunRepository ingestRunRepository,
                                      WikiStore wikiStore, RevertService revertService) {
        return new RunReportService(runReportQuery, ingestRunRepository, wikiStore, revertService);
    }

    @Bean
    HealthService healthService(WikiHealthQuery wikiHealthQuery, WikiStore wikiStore) {
        return new HealthService(wikiHealthQuery, wikiStore);
    }
}
