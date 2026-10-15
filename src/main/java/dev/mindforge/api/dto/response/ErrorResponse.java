package dev.mindforge.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The one error shape. {@code lessonId} and {@code lessonTitle} are set only on a lesson collision. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String error,
    String code,
    String detail,
    String lessonId,
    String lessonTitle
) {

    public static ErrorResponse of(String error, String code, String detail) {
        return new ErrorResponse(error, code, detail, null, null);
    }
}
