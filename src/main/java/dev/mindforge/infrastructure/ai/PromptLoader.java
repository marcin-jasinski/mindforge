package dev.mindforge.infrastructure.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads prompt templates from {@code prompts/<locale>/<name>.<locale>.md} on the classpath and fills their
 * {@code {{placeholder}}}s. Values are inserted verbatim, never re-scanned for placeholders.
 */
public class PromptLoader {

    private final String locale;
    private final Map<String, String> templates = new ConcurrentHashMap<>();

    public PromptLoader(String locale) {
        this.locale = locale;
    }

    public String render(String name, Map<String, String> values) {
        String template = templates.computeIfAbsent(name, this::read);
        StringBuilder prompt = new StringBuilder();
        int position = 0;
        while (true) {
            int open = template.indexOf("{{", position);
            int close = open < 0 ? -1 : template.indexOf("}}", open);
            if (close < 0) {
                return prompt.append(template, position, template.length()).toString();
            }
            String key = template.substring(open + 2, close);
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("Prompt " + name + " needs a value for {{" + key + "}}");
            }
            prompt.append(template, position, open).append(values.get(key));
            position = close + 2;
        }
    }

    private String read(String name) {
        String resource = "prompts/" + locale + "/" + name + "." + locale + ".md";
        try (InputStream in = PromptLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("No prompt " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
