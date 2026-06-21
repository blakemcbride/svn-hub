package com.svnhub.migrate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordered list of schema migrations plus {@link #CURRENT_DB_VERSION}.
 *
 * v1 is the schema.sql baseline (never a Migration object); registered
 * migrations start at v2 and must be contiguous up to CURRENT_DB_VERSION.
 * Keep this in lockstep with the list — {@link #validate()} fails fast at
 * startup on a mismatch.
 */
public final class MigrationRegistry {

    /** The db_version the current code expects. */
    public static final int CURRENT_DB_VERSION = 4;

    private static final List<Migration> MIGRATIONS;

    static {
        List<Migration> m = new ArrayList<>();
        m.add(new Migration002AddRecordVersion());      // v2
        m.add(new Migration003AddIndexes());            // v3
        m.add(new Migration004AddEmailVerification());  // v4
        MIGRATIONS = Collections.unmodifiableList(m);
    }

    private MigrationRegistry() {
    }

    public static List<Migration> all() {
        return MIGRATIONS;
    }

    /**
     * Enforce that registered versions are exactly 2, 3, ..., CURRENT_DB_VERSION —
     * contiguous, in order, no gaps/dups, names present, and count == version-1.
     */
    public static void validate() {
        int expected = 2;
        for (Migration mig : MIGRATIONS) {
            if (mig.name() == null || mig.name().isEmpty())
                throw new IllegalStateException("Migration v" + mig.version() + " has no name");
            if (mig.version() != expected)
                throw new IllegalStateException("Migration registry out of order: expected v" + expected
                        + " but found v" + mig.version() + " (" + mig.name() + ")");
            expected++;
        }
        int last = expected - 1;
        if (MIGRATIONS.isEmpty())
            last = 1;
        if (last != CURRENT_DB_VERSION)
            throw new IllegalStateException("CURRENT_DB_VERSION=" + CURRENT_DB_VERSION
                    + " but the registry ends at v" + last + " (registry and version must change in lockstep)");
    }
}
