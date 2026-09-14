package dev.mindforge.domain.model;

/** Thrown when an upload marked as a new version names a lesson that has no document to version. */
public class UnknownLessonException extends IllegalArgumentException {

    public UnknownLessonException(String lessonId) {
        super("There is no lesson '" + lessonId + "' to add a new version to");
    }
}
