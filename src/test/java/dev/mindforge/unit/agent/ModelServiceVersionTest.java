package dev.mindforge.unit.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.mindforge.agent.AnswerWriter;
import dev.mindforge.agent.ClaimExtractor;
import dev.mindforge.agent.FlashcardGenerator;
import dev.mindforge.agent.LinkChecker;
import dev.mindforge.agent.PageSelector;
import dev.mindforge.agent.PageWriter;
import dev.mindforge.agent.QuizEvaluator;
import dev.mindforge.agent.QuizGenerator;
import dev.mindforge.agent.RelevanceGuard;
import dev.mindforge.agent.SupersessionDetector;
import dev.mindforge.agent.WikiReviewer;

class ModelServiceVersionTest {

    @ParameterizedTest
    @ValueSource(classes = {RelevanceGuard.class, ClaimExtractor.class, PageWriter.class, LinkChecker.class,
        SupersessionDetector.class, WikiReviewer.class, FlashcardGenerator.class, QuizGenerator.class,
        QuizEvaluator.class, PageSelector.class, AnswerWriter.class})
    void everyModelServiceDeclaresAStaticFinalVersion(Class<?> service) throws Exception {
        Field version = service.getField("VERSION");

        assertThat(Modifier.isStatic(version.getModifiers()) && Modifier.isFinal(version.getModifiers())).isTrue();
        assertThat(version.get(null)).isInstanceOf(String.class).asString().isNotBlank();
    }
}
