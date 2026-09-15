package dev.mindforge.domain.model;

/** Thrown when a model's completion is not the structured output its service asked for. */
public class ModelOutputException extends IllegalStateException {

    public ModelOutputException(String service, Throwable cause) {
        super(service + " returned output it could not read: " + cause.getMessage(), cause);
    }
}
