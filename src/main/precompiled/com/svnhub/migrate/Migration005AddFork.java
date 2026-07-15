package com.svnhub.migrate;

import org.kissweb.database.Connection;

/**
 * v5 — add repository forking.
 *
 * <p>A fork is a full-history copy of another repository under the forker's own
 * namespace (a distinct FSFS repo with a fresh UUID).  Two nullable columns on
 * {@code repository} record the provenance:</p>
 * <ul>
 *   <li>{@code fork_origin_id} — the repo this one was forked from (NULL for a
 *       normal repo).  Nulled out, not cascaded, when the origin is deleted.</li>
 *   <li>{@code fork_base_rev} — the origin's HEAD revision at fork time.  This is
 *       the merge base for a later fork&nbsp;&rarr;&nbsp;origin merge request:
 *       only revisions after it are the fork's divergent work.</li>
 * </ul>
 *
 * <p>{@code merge_request.source_repo_id} lets a merge request draw its source
 * from a <i>different</i> repository (a fork).  NULL means the source and target
 * are the same repo — the pre-existing intra-repo (branch&nbsp;&rarr;&nbsp;trunk)
 * behavior — so every existing row needs no backfill.  {@code repo_id} continues
 * to identify the repo the request lives in (the target).</p>
 *
 * <p>Additive and idempotent (all {@code IF NOT EXISTS}).</p>
 */
public class Migration005AddFork implements Migration {

    public int version() {
        return 5;
    }

    public String name() {
        return "AddFork";
    }

    public void apply(Connection db) throws Exception {
        db.execute("ALTER TABLE repository ADD COLUMN IF NOT EXISTS fork_origin_id integer NULL");
        db.execute("ALTER TABLE repository ADD COLUMN IF NOT EXISTS fork_base_rev integer NULL");
        db.execute("CREATE INDEX IF NOT EXISTS repository_fork_origin_idx ON repository(fork_origin_id)");

        db.execute("ALTER TABLE merge_request ADD COLUMN IF NOT EXISTS source_repo_id integer NULL");
        db.execute("CREATE INDEX IF NOT EXISTS merge_request_source_repo_idx ON merge_request(source_repo_id)");
    }
}
