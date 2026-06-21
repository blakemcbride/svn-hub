package com.svnhub.migrate;

import org.kissweb.database.Connection;

/**
 * A single schema migration that brings the database up to {@link #version()}.
 *
 * Contract (see AutoUpdate.md):
 * <ul>
 *   <li><b>Additive only</b> — add columns/indexes/seed rows; never DROP, RENAME,
 *       or rewrite existing values. Use {@code IF NOT EXISTS}.</li>
 *   <li><b>Idempotent</b> — safe to re-run after a partial failure.</li>
 *   <li><b>Permanent</b> — once shipped, never edit it; write a new migration.</li>
 * </ul>
 * {@link #apply(Connection)} runs inside the transaction {@link SchemaMigrator}
 * manages; throw to abort (the whole migration, including its db_version row,
 * rolls back).
 */
public interface Migration {

    /** The db_version this migration brings the database to (>= 2; v1 is the schema.sql baseline). */
    int version();

    /** Short description for the deploy log. */
    String name();

    /** Apply the additive change.  Runs in the migrator's transaction. */
    void apply(Connection db) throws Exception;
}
