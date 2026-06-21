package com.svnhub.migrate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordered list of per-row upgraders plus {@link #CURRENT_RECORD_VERSION}.
 *
 * The upgrader at index N-1 takes a row from version N to N+1.  Keep this in
 * lockstep with the list — {@link #validate()} fails fast at startup.
 */
public final class RecordUpgraderRegistry {

    /** The record_version the current code expects on each repository row. */
    public static final int CURRENT_RECORD_VERSION = 3;

    private static final List<RecordUpgrader> UPGRADERS;

    static {
        List<RecordUpgrader> u = new ArrayList<>();
        u.add(new DefaultBranchUpgrader());        // v1 -> v2
        u.add(new RegenerateRepoAuthUpgrader());   // v2 -> v3
        UPGRADERS = Collections.unmodifiableList(u);
    }

    private RecordUpgraderRegistry() {
    }

    public static List<RecordUpgrader> all() {
        return UPGRADERS;
    }

    /** Look up the upgrader that takes a row from {@code fromVersion}. */
    public static RecordUpgrader forVersion(int fromVersion) {
        for (RecordUpgrader u : UPGRADERS)
            if (u.fromVersion() == fromVersion)
                return u;
        return null;
    }

    /**
     * Enforce a contiguous chain: fromVersion = 1, 2, ..., CURRENT-1, each with
     * toVersion = fromVersion + 1, and count == CURRENT - 1.
     */
    public static void validate() {
        int expected = 1;
        for (RecordUpgrader u : UPGRADERS) {
            if (u.name() == null || u.name().isEmpty())
                throw new IllegalStateException("RecordUpgrader from v" + u.fromVersion() + " has no name");
            if (u.fromVersion() != expected)
                throw new IllegalStateException("Upgrader registry out of order: expected fromVersion=" + expected
                        + " but found " + u.fromVersion() + " (" + u.name() + ")");
            if (u.toVersion() != u.fromVersion() + 1)
                throw new IllegalStateException("Upgrader " + u.name() + " must advance exactly one version (from "
                        + u.fromVersion() + " to " + u.toVersion() + ")");
            expected++;
        }
        int last = UPGRADERS.isEmpty() ? 1 : expected;
        if (last != CURRENT_RECORD_VERSION)
            throw new IllegalStateException("CURRENT_RECORD_VERSION=" + CURRENT_RECORD_VERSION
                    + " but the registry ends at v" + last + " (registry and version must change in lockstep)");
    }
}
