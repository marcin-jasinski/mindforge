package dev.mindforge.infrastructure.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import dev.mindforge.domain.model.ModelOutputException;

/** Reads a model's JSON answer into a record, tolerating prose or a code fence around it, trailing text after it and unknown fields. */
public final class ModelJson {

    private static final JsonMapper JSON = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    private ModelJson() {}

    /** @throws ModelOutputException when the completion holds no JSON object of that shape */
    public static <T> T read(String service, String completion, Class<T> type) {
        int start = completion.indexOf('{');
        if (start < 0) {
            throw new ModelOutputException(service, new IllegalArgumentException("no JSON object"));
        }
        try {
            return JSON.readValue(completion.substring(start), type);
        } catch (JacksonException e) {
            throw new ModelOutputException(service, e);
        }
    }
}
