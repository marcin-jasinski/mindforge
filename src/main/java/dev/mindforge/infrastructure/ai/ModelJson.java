package dev.mindforge.infrastructure.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import dev.mindforge.domain.model.ModelOutputException;

/** Reads a model's JSON answer into a record, tolerating a Markdown code fence around it and unknown fields. */
public final class ModelJson {

    private static final JsonMapper JSON = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    private ModelJson() {}

    /** @throws ModelOutputException when the completion holds no JSON object of that shape */
    public static <T> T read(String service, String completion, Class<T> type) {
        int start = completion.indexOf('{');
        int end = completion.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new ModelOutputException(service, new IllegalArgumentException("no JSON object"));
        }
        try {
            return JSON.readValue(completion.substring(start, end + 1), type);
        } catch (JacksonException e) {
            throw new ModelOutputException(service, e);
        }
    }
}
