package dev.mindforge.support;

import tools.jackson.databind.json.JsonMapper;

/** Fragments that route a {@link StubAIGateway} answer to one prompt, and JSON for the answers. */
public final class Prompts {

    public static final String GUARD = "# Zadanie: ocena przydatności dokumentu";
    public static final String EXTRACT = "# Zadanie: ekstrakcja twierdzeń";
    public static final String EDIT = "# Zadanie: edycja wiki z rozmowy";
    public static final String WRITE = "# Zadanie: napisanie strony wiki";
    public static final String LINKS = "# Zadanie: wstawianie linków";
    public static final String SUPERSEDE = "# Zadanie: wykrywanie zdezaktualizowanych sekcji";

    public static final String RELEVANT = "{\"relevant\": true, \"reason\": \"Notatki z biologii.\", \"confidence\": 0.9}";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Prompts() {}

    /** Routes to the writer prompt of one page. */
    public static String writing(String path) {
        return "- Ścieżka: " + path + "\n";
    }

    public static String json(Object value) {
        return JSON.writeValueAsString(value);
    }
}
