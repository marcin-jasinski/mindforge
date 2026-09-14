package dev.mindforge.unit.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.mindforge.agent.ClaimExtractor;
import dev.mindforge.agent.LinkChecker;
import dev.mindforge.agent.PageWriter;
import dev.mindforge.agent.RelevanceGuard;
import dev.mindforge.agent.SupersessionDetector;

class ModelServiceVersionTest {

    @ParameterizedTest
    @ValueSource(classes = {RelevanceGuard.class, ClaimExtractor.class, PageWriter.class, LinkChecker.class,
        SupersessionDetector.class})
    void everyModelServiceDeclaresAStaticFinalVersion(Class<?> service) throws Exception {
        Field version = service.getField("VERSION");

        assertThat(Modifier.isStatic(version.getModifiers()) && Modifier.isFinal(version.getModifiers())).isTrue();
        assertThat(version.get(null)).isInstanceOf(String.class).asString().isNotBlank();
    }
}
