package dev.mindforge.domain.model;

/**
 * Thrown when an upload resolves to a lesson that already has a document and was not marked as a new
 * version. An upload never becomes a version of an existing lesson by accident.
 */
public class LessonAlreadyExistsException extends IllegalStateException {

    private final LessonIdentity lesson;

    public LessonAlreadyExistsException(LessonIdentity lesson) {
        super("Lesson '" + lesson.lessonId() + "' already exists; upload it as a new version or choose another lesson id");
        this.lesson = lesson;
    }

    public LessonIdentity lesson() {
        return lesson;
    }
}
