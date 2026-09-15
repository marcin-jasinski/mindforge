package dev.mindforge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.mindforge.agent.FlashcardGenerator;
import dev.mindforge.agent.QuizEvaluator;
import dev.mindforge.agent.QuizGenerator;
import dev.mindforge.application.service.FlashcardService;
import dev.mindforge.application.service.QuizService;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.ai.PromptLoader;
import dev.mindforge.infrastructure.event.QuizSessionCleanup;

/** Wires study: its model services call the gateway directly, outside the background permit pool. */
@Configuration
public class StudyConfig {

    @Bean
    FlashcardGenerator flashcardGenerator(AIGateway aiGateway, PromptLoader promptLoader) {
        return new FlashcardGenerator(aiGateway, promptLoader);
    }

    @Bean
    QuizGenerator quizGenerator(AIGateway aiGateway, PromptLoader promptLoader) {
        return new QuizGenerator(aiGateway, promptLoader);
    }

    @Bean
    QuizEvaluator quizEvaluator(AIGateway aiGateway, PromptLoader promptLoader) {
        return new QuizEvaluator(aiGateway, promptLoader);
    }

    @Bean
    FlashcardService flashcardService(WikiStore wikiStore, StudyProgressStore studyProgressStore,
                                      FlashcardGenerator flashcardGenerator, ProcessingSettings processingSettings) {
        return new FlashcardService(wikiStore, studyProgressStore, flashcardGenerator, processingSettings);
    }

    @Bean
    QuizService quizService(WikiStore wikiStore, StudyProgressStore studyProgressStore,
                            QuizSessionStore quizSessionStore, QuizGenerator quizGenerator,
                            QuizEvaluator quizEvaluator, ProcessingSettings processingSettings,
                            AppProperties properties) {
        return new QuizService(wikiStore, studyProgressStore, quizSessionStore, quizGenerator, quizEvaluator,
            processingSettings, properties.getStudy().getQuizSessionTtl());
    }

    @Bean
    QuizSessionCleanup quizSessionCleanup(QuizSessionStore quizSessionStore) {
        return new QuizSessionCleanup(quizSessionStore);
    }
}
