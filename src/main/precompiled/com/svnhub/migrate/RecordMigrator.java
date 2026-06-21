package com.svnhub.migrate;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kissweb.database.Connection;
import org.kissweb.database.Record;
import org.kissweb.restServer.MainServlet;

/**
 * Stage 2 of the auto-update facility: walk every {@code repository} row whose
 * {@code record_version} is below {@link RecordUpgraderRegistry#CURRENT_RECORD_VERSION}
 * and apply the registered {@link RecordUpgrader} chain to bring it forward.
 *
 * Each row is upgraded on its own connection/transaction; the data change and
 * the {@code record_version} bump commit together.  Per-row failures are caught,
 * logged, and counted — they never throw and never block startup (this stage
 * runs only after the schema migration has succeeded).
 */
public final class RecordMigrator {

    private static final Logger logger = LogManager.getLogger(RecordMigrator.class);
    private static final int CHUNK_SIZE = 500;

    private RecordMigrator() {
    }

    public static void runOnStartup() {
        try {
            RecordUpgraderRegistry.validate();
        } catch (RuntimeException e) {
            logger.error("* * * Record-upgrader registry invalid; skipping per-row upgrade", e);
            return;
        }
        int target = RecordUpgraderRegistry.CURRENT_RECORD_VERSION;
        if (RecordUpgraderRegistry.all().isEmpty())
            return;   // target 1 — no row can be below 1

        int upgraded = 0;
        int failed = 0;
        int lastId = 0;
        while (true) {
            List<Integer> ids = fetchChunk(target, lastId);
            if (ids.isEmpty())
                break;
            for (int id : ids) {
                lastId = id;
                try {
                    upgradeOne(id, target);
                    upgraded++;
                } catch (Exception e) {
                    failed++;
                    logger.error("* * * Record upgrade failed for repository repo_id=" + id + " (continuing)", e);
                }
            }
            if (ids.size() < CHUNK_SIZE)
                break;
        }
        if (upgraded > 0 || failed > 0)
            logger.info("* * * Per-row upgrade complete: " + upgraded + " upgraded, " + failed + " failed (target record_version=" + target + ")");
    }

    private static List<Integer> fetchChunk(int target, int lastId) {
        List<Integer> ids = new ArrayList<>();
        Connection db = MainServlet.openNewConnection();
        try {
            List<Record> recs = db.fetchAll(0, CHUNK_SIZE,
                    "SELECT repo_id FROM repository WHERE coalesce(record_version,1) < ? AND repo_id > ? ORDER BY repo_id",
                    target, lastId);
            for (Record r : recs)
                ids.add(r.getInt("repo_id"));
        } catch (Exception e) {
            logger.error("* * * Failed to scan repository rows for upgrade", e);
        } finally {
            MainServlet.closeConnection(db);
        }
        return ids;
    }

    /** Upgrade one row through its chain, then bump record_version — all in one transaction. */
    private static void upgradeOne(int repoId, int target) throws Exception {
        Connection db = MainServlet.openNewConnection();
        boolean ok = false;
        try {
            Record r = db.fetchOne("SELECT record_version FROM repository WHERE repo_id = ?", repoId);
            if (r == null) {
                ok = true;   // row deleted between scan and upgrade — treat as done
                return;
            }
            Integer cur = r.getInt("record_version");
            int current = cur == null ? 1 : cur;
            while (current < target) {
                RecordUpgrader up = RecordUpgraderRegistry.forVersion(current);
                if (up == null)
                    throw new IllegalStateException("No record upgrader registered for version " + current);
                up.upgrade(db, repoId);
                current = up.toVersion();
            }
            db.execute("UPDATE repository SET record_version = ? WHERE repo_id = ?", target, repoId);
            ok = true;
        } finally {
            MainServlet.closeConnection(db, ok);
        }
    }
}
