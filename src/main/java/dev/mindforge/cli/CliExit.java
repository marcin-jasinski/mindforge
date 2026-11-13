package dev.mindforge.cli;

/** The stable exit codes of the CLI. They are a script contract: never renumber them. */
public enum CliExit {
    /** The command succeeded. */
    OK(0),
    /** The ingest ran (or ran before) and failed. */
    RUN_FAILED(1),
    /** The input was refused before any run was queued. */
    REFUSED(2);

    private final int code;

    CliExit(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
