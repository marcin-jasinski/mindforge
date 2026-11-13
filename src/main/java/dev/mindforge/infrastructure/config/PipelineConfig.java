package dev.mindforge.infrastructure.config;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.agent.ClaimExtractor;
import dev.mindforge.agent.LinkChecker;
import dev.mindforge.agent.PageWriter;
import dev.mindforge.agent.RelevanceGuard;
import dev.mindforge.agent.SupersessionDetector;
import dev.mindforge.agent.WikiReviewer;
import dev.mindforge.application.service.IngestPipeline;
import dev.mindforge.application.service.LintService;
import dev.mindforge.application.service.RunWorker;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.ai.ObservationRunMeter;
import dev.mindforge.infrastructure.ai.PermitGateway;
import dev.mindforge.infrastructure.ai.PromptLoader;
import dev.mindforge.infrastructure.ai.TracingGateway;
import dev.mindforge.infrastructure.event.RunWorkerTriggers;
import dev.mindforge.infrastructure.event.SseProgressNotifier;

/** Wires the ingest pipeline: model services behind the background permit pool, the run worker and progress. */
@Configuration
@EnableScheduling
public class PipelineConfig {

    private static final String PROMPT_LOCALE = "en";

    @Bean
    ProcessingSettings processingSettings(AppProperties properties) {
        AppProperties.Ai.Model models = properties.getAi().getModel();
        return ProcessingSettings.withModels(Map.of(
            ModelTier.SMALL, models.getSmall(), ModelTier.LARGE, models.getLarge(), ModelTier.VISION, models.getVision()));
    }

    @Bean
    PromptLoader promptLoader() {
        return new PromptLoader(PROMPT_LOCALE);
    }

    /** The one permit pool every {@code INGEST} and {@code LINT} model call takes from. */
    @Bean
    Semaphore backgroundPermits(AppProperties properties) {
        return new Semaphore(properties.getAi().getBackgroundPermits());
    }

    @Bean
    Executor runExecutor() {
        return command -> Thread.ofVirtual().name("ingest-run").start(command);
    }

    @Bean
    RelevanceGuard relevanceGuard(AIGateway aiGateway, Semaphore backgroundPermits,
                                  ObservationRegistry observationRegistry, PromptLoader promptLoader) {
        return new RelevanceGuard(
            background(aiGateway, backgroundPermits, observationRegistry, RelevanceGuard.class), promptLoader);
    }

    @Bean
    ClaimExtractor claimExtractor(AIGateway aiGateway, Semaphore backgroundPermits,
                                  ObservationRegistry observationRegistry, PromptLoader promptLoader,
                                  ProcessingSettings processingSettings) {
        return new ClaimExtractor(
            background(aiGateway, backgroundPermits, observationRegistry, ClaimExtractor.class), promptLoader,
            processingSettings.maxClaimsPerExtractCall());
    }

    @Bean
    PageWriter pageWriter(AIGateway aiGateway, Semaphore backgroundPermits, ObservationRegistry observationRegistry,
                          PromptLoader promptLoader) {
        return new PageWriter(
            background(aiGateway, backgroundPermits, observationRegistry, PageWriter.class), promptLoader);
    }

    @Bean
    LinkChecker linkChecker(AIGateway aiGateway, Semaphore backgroundPermits, ObservationRegistry observationRegistry,
                            PromptLoader promptLoader) {
        return new LinkChecker(
            background(aiGateway, backgroundPermits, observationRegistry, LinkChecker.class), promptLoader);
    }

    @Bean
    SupersessionDetector supersessionDetector(AIGateway aiGateway, Semaphore backgroundPermits,
                                              ObservationRegistry observationRegistry, PromptLoader promptLoader) {
        return new SupersessionDetector(
            background(aiGateway, backgroundPermits, observationRegistry, SupersessionDetector.class), promptLoader);
    }

    @Bean
    WikiReviewer wikiReviewer(AIGateway aiGateway, Semaphore backgroundPermits,
                              ObservationRegistry observationRegistry, PromptLoader promptLoader) {
        return new WikiReviewer(
            background(aiGateway, backgroundPermits, observationRegistry, WikiReviewer.class), promptLoader);
    }

    /** A background model service's gateway: its span and cost entry are named as its {@code step_versions} key. */
    private static AIGateway background(AIGateway aiGateway, Semaphore permits, ObservationRegistry registry,
                                        Class<?> service) {
        return new PermitGateway(TracingGateway.forService(aiGateway, service, registry), permits);
    }

    @Bean
    SseProgressNotifier progressNotifier() {
        return new SseProgressNotifier();
    }

    @Bean
    IngestPipeline ingestPipeline(DocumentRepository documentRepository, WikiStore wikiStore,
                                  IngestRunRepository ingestRunRepository, RelevanceGuard relevanceGuard,
                                  ClaimExtractor claimExtractor, PageWriter pageWriter, LinkChecker linkChecker,
                                  SupersessionDetector supersessionDetector, SseProgressNotifier progressNotifier,
                                  TransactionOperations transactionOperations, ProcessingSettings processingSettings) {
        return new IngestPipeline(documentRepository, wikiStore, ingestRunRepository, relevanceGuard, claimExtractor,
            pageWriter, linkChecker, supersessionDetector, progressNotifier, transactionOperations,
            processingSettings);
    }

    @Bean
    LintService lintService(WikiStore wikiStore, IngestRunRepository ingestRunRepository,
                            DocumentRepository documentRepository, LinkChecker linkChecker,
                            WikiReviewer wikiReviewer, EventPublisher eventPublisher,
                            SseProgressNotifier progressNotifier, TransactionOperations transactionOperations,
                            ProcessingSettings processingSettings) {
        return new LintService(wikiStore, ingestRunRepository, documentRepository, linkChecker, wikiReviewer,
            eventPublisher, progressNotifier, transactionOperations, processingSettings);
    }

    @Bean
    RunWorker runWorker(IngestRunRepository ingestRunRepository, IngestPipeline ingestPipeline,
                        LintService lintService, SseProgressNotifier progressNotifier, EventPublisher eventPublisher,
                        TransactionOperations transactionOperations, Executor runExecutor,
                        ObservationRegistry observationRegistry, AppProperties properties) {
        return new RunWorker(ingestRunRepository, ingestPipeline, lintService, progressNotifier, eventPublisher,
            transactionOperations, runExecutor, new ObservationRunMeter(observationRegistry),
            properties.getAi().getCostWarningUsd());
    }

    /** Not in the {@code cli} profile: a CLI process must not sweep or drain the runs of a live server. */
    @Bean
    @Profile("!cli")
    RunWorkerTriggers runWorkerTriggers(RunWorker runWorker, Executor runExecutor) {
        return new RunWorkerTriggers(runWorker, runExecutor);
    }
}
