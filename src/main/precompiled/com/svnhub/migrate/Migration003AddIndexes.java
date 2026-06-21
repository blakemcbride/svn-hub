package com.svnhub.migrate;

import org.kissweb.database.Connection;

/**
 * v3 — add useful indexes that were missing.  {@code repository.owner_id} is
 * filtered by getRepositories / Discover profiles but had no index.  Additive +
 * idempotent.
 */
public class Migration003AddIndexes implements Migration {

    public int version() {
        return 3;
    }

    public String name() {
        return "AddIndexes";
    }

    public void apply(Connection db) throws Exception {
        db.execute("CREATE INDEX IF NOT EXISTS repository_owner_idx ON repository(owner_id)");
    }
}
