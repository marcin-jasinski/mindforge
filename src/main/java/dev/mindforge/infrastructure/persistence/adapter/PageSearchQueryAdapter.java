package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.port.PageSearchQuery;
import dev.mindforge.infrastructure.persistence.jpa.PageSearchJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.WikiEntityMapper;

@Transactional(readOnly = true)
public class PageSearchQueryAdapter implements PageSearchQuery {

    private final PageSearchJpaRepository pages;
    private final WikiEntityMapper mapper;

    public PageSearchQueryAdapter(PageSearchJpaRepository pages, WikiEntityMapper mapper) {
        this.pages = pages;
        this.mapper = mapper;
    }

    @Override
    public List<IndexEntry> search(UUID kbId, String query, int limit) {
        String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return pages.search(kbId, pattern, query, limit).stream().map(mapper::toIndexEntry).toList();
    }
}
