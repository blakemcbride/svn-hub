package com.svnhub.migrate;

import org.kissweb.database.Connection;

/**
 * Upgrades a single {@code repository} row from {@link #fromVersion()} to
 * {@link #toVersion()} (= fromVersion + 1).
 *
 * Contract (see AutoUpdate.md):
 * <ul>
 *   <li><b>Strictly additive</b> — fill a new column/field from data the row
 *       already has; never overwrite, transform, or delete existing values.</li>
 *   <li><b>Idempotent</b> — re-running on the same row yields the same result.</li>
 *   <li><b>Per-row isolation</b> — a thrown upgrade rolls back just that row.</li>
 *   <li>Must <b>not</b> bump {@code record_version} itself — {@link RecordMigrator} does that.</li>
 * </ul>
 */
public interface RecordUpgrader {

    /** The row version this upgrader applies to. */
    int fromVersion();

    /** The row version after this upgrader (must equal fromVersion + 1). */
    int toVersion();

    /** Short description for the deploy log. */
    String name();

    /** Upgrade one repository row (by id), inside the migrator's per-row transaction. */
    void upgrade(Connection db, int repoId) throws Exception;
}
