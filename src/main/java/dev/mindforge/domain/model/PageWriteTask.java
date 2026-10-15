package dev.mindforge.domain.model;

import java.util.List;

/** The one write a run makes to one path: a create or a revision, with the claims it integrates. The title is code's. */
public record PageWriteTask(String path, PageType type, String title, boolean create, List<Claim> claims) {

    public PageWriteTask {
        claims = List.copyOf(claims);
    }
}
