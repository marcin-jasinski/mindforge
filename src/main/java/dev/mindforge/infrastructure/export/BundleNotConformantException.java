package dev.mindforge.infrastructure.export;

import java.util.List;

/** Thrown when a rendered bundle breaks OKF; the export fails with 500 instead of shipping it. */
public class BundleNotConformantException extends IllegalStateException {

    public BundleNotConformantException(List<String> violations) {
        super("The rendered bundle is not OKF-conformant: " + String.join("; ", violations));
    }
}
