package dev.mindforge.domain.model;

/** Thrown when a knowledge base, document, page or run does not exist where the caller looked for it. */
public class NotFoundException extends IllegalArgumentException {

    public NotFoundException(String what) {
        super(what + " not found");
    }
}
