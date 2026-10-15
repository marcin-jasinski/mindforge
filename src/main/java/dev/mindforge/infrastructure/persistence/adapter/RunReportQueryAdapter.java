package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.LogEntry;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.model.RunSummary;
import dev.mindforge.domain.model.RunSupersession;
import dev.mindforge.domain.port.RunReportQuery;
import dev.mindforge.infrastructure.persistence.jpa.IngestRunJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageSupersessionJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.IngestRunEntityMapper;

@Transactional(readOnly = true)
public class RunReportQueryAdapter implements RunReportQuery {

    private final IngestRunJpaRepository runs;
    private final PageSupersessionJpaRepository supersessions;
    private final IngestRunEntityMapper mapper;

    public RunReportQueryAdapter(IngestRunJpaRepository runs, PageSupersessionJpaRepository supersessions,
                                 IngestRunEntityMapper mapper) {
        this.runs = runs;
        this.supersessions = supersessions;
        this.mapper = mapper;
    }

    @Override
    public List<LogEntry> logEntries(UUID kbId) {
        return runs.findLogEntries(kbId, RunStatus.COMPLETED).stream().map(mapper::toLogEntry).toList();
    }

    @Override
    public List<RunSummary> listRuns(UUID kbId, int limit) {
        return runs.findRunSummaries(kbId, Limit.of(limit)).stream().map(mapper::toRunSummary).toList();
    }

    @Override
    public List<RunSupersession> supersessionsOf(UUID kbId, UUID runId) {
        return supersessions.findByRun(kbId, runId);
    }
}
