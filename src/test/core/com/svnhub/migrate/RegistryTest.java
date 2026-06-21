package com.svnhub.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests the auto-update registry invariants (pure, no database).  The shipped
 * registries must validate; the validate() logic must reject gaps, duplicates,
 * out-of-order entries, and version/list mismatch.
 */
public class RegistryTest {

    // ---- the real, shipped registries are well-formed ----

    @Test
    void shippedMigrationRegistryValidates() {
        MigrationRegistry.validate();   // throws if broken
        assertEquals(MigrationRegistry.all().size() + 1, MigrationRegistry.CURRENT_DB_VERSION);
    }

    @Test
    void shippedUpgraderRegistryValidates() {
        RecordUpgraderRegistry.validate();
        assertEquals(RecordUpgraderRegistry.all().size() + 1, RecordUpgraderRegistry.CURRENT_RECORD_VERSION);
    }

    @Test
    void migrationChainIsContiguousFromTwo() {
        int expected = 2;
        for (Migration m : MigrationRegistry.all())
            assertEquals(expected++, m.version());
    }

    @Test
    void upgraderChainAdvancesOneVersion() {
        for (RecordUpgrader u : RecordUpgraderRegistry.all())
            assertEquals(u.fromVersion() + 1, u.toVersion());
    }

    // ---- validate() rejects malformed chains (mirrors the registry logic) ----

    /** A standalone copy of the migration-chain check, to exercise the failure paths. */
    private static void checkMigrations(int[] versions, int current) {
        int expected = 2;
        for (int v : versions) {
            if (v != expected)
                throw new IllegalStateException("out of order at " + v);
            expected++;
        }
        int last = versions.length == 0 ? 1 : expected - 1;
        if (last != current)
            throw new IllegalStateException("version mismatch");
    }

    @Test
    void rejectsGap() {
        assertThrows(IllegalStateException.class, () -> checkMigrations(new int[] {2, 4}, 4));
    }

    @Test
    void rejectsDuplicate() {
        assertThrows(IllegalStateException.class, () -> checkMigrations(new int[] {2, 2}, 3));
    }

    @Test
    void rejectsOutOfOrder() {
        assertThrows(IllegalStateException.class, () -> checkMigrations(new int[] {3, 2}, 3));
    }

    @Test
    void rejectsVersionListMismatch() {
        assertThrows(IllegalStateException.class, () -> checkMigrations(new int[] {2, 3}, 5));
    }

    @Test
    void acceptsWellFormed() {
        checkMigrations(new int[] {2, 3, 4}, 4);   // no throw
    }
}
