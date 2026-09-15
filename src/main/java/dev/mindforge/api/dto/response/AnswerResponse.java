package dev.mindforge.api.dto.response;

import java.util.List;

/** An answer and the pages it cites; never the text it was grounded on. */
public record AnswerResponse(String answer, List<String> citedPaths) {}
