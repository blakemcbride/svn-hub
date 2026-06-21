package com.svnhub.migrate;

import org.kissweb.database.Connection;

/**
 * v2 — add the {@code record_version} column that the per-row {@link RecordMigrator}
 * reads.  Additive + idempotent; a no-op on fresh installs (schema.sql already
 * has the column) and the bring-forward step for databases created before it.
 */
public class Migration002AddRecordVersion implements Migration {

    public int version() {
        return 2;
    }

    public String name() {
        return "AddRecordVersion";
    }

    public void apply(Connection db) throws Exception {
        db.execute("ALTER TABLE repository ADD COLUMN IF NOT EXISTS record_version integer NOT NULL DEFAULT 1");
    }
}
