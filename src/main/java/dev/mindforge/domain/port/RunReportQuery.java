package dev.mindforge.domain.port;

import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.LogEntry;

/** Read-only projections of a knowledge base's runs. */
public interface RunReportQuery {

    /** Every {@code COMPLETED} run, newest first, with the counts {@code log.md} shows. */
    List<LogEntry> logEntries(UUID kbId);
}
