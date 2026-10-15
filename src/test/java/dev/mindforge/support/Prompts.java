package dev.mindforge.support;

import tools.jackson.databind.json.JsonMapper;

/** Fragments that route a {@link StubAIGateway} answer to one prompt, and JSON for the answers. */
public final class Prompts {

    public static final String GUARD = "# Task: document relevance check";
    public static final String EXTRACT = "# Task: claim extraction";
    public static final String EDIT = "# Task: wiki edit from a conversation";
    public static final String WRITE = "# Task: wiki page writing";
    public static final String LINKS = "# Task: link insertion";
    public static final String SUPERSEDE = "# Task: detection of out-of-date sections";
    public static final String REVIEW = "# Task: wiki review";
    public static final String FLASHCARDS = "# Task: flashcard writing";
    public static final String QUIZ = "# Task: quiz questions";
    public static final String GRADE = "# Task: answer grading";
    public static final String SELECT = "# Task: page selection for an answer";
    public static final String ANSWER = "# Task: answer from the wiki";

    public static final String RELEVANT = "{\"relevant\": true, \"reason\": \"Biology notes.\", \"confidence\": 0.9}";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Prompts() {}

    /** Routes to the writer prompt of one page. */
    public static String writing(String path) {
        return "- Path: " + path + "\n";
    }

    public static String json(Object value) {
        return JSON.writeValueAsString(value);
    }
}
