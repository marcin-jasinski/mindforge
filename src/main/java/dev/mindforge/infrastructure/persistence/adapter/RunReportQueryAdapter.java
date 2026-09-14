package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.LogEntry;
import dev.mindforge.domain.model.RunStatus;
import dev.mindforge.domain.port.RunReportQuery;
import dev.mindforge.infrastructure.persistence.jpa.IngestRunJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.IngestRunEntityMapper;

@Transactional(readOnly = true)
public class RunReportQueryAdapter implements RunReportQuery {

    private final IngestRunJpaRepository runs;
    private final IngestRunEntityMapper mapper;

    public RunReportQueryAdapter(IngestRunJpaRepository runs, IngestRunEntityMapper mapper) {
        this.runs = runs;
        this.mapper = mapper;
    }

    @Override
    public List<LogEntry> logEntries(UUID kbId) {
        return runs.findLogEntries(kbId, RunStatus.COMPLETED).stream().map(mapper::toLogEntry).toList();
    }
}
