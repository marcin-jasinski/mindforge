package dev.mindforge.domain.port;

import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.LogEntry;
import dev.mindforge.domain.model.RunSummary;
import dev.mindforge.domain.model.RunSupersession;

/** Read-only projections of a knowledge base's runs. */
public interface RunReportQuery {

    /** Every {@code COMPLETED} run, newest first, with the counts {@code log.md} shows. */
    List<LogEntry> logEntries(UUID kbId);

    /** The newest runs first, at most {@code limit}. */
    List<RunSummary> listRuns(UUID kbId, int limit);

    /** The supersessions the run inserted that still exist. */
    List<RunSupersession> supersessionsOf(UUID kbId, UUID runId);
}
