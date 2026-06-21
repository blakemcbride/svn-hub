package com.svnhub.migrate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kissweb.database.Connection;
import org.kissweb.database.Record;
import org.kissweb.restServer.MainServlet;

/**
 * Stage 1 of the auto-update facility: bring the database schema up to
 * {@link MigrationRegistry#CURRENT_DB_VERSION} by applying registered
 * {@link Migration}s in order, at server startup.
 *
 * Each migration runs on its own connection/transaction: the migration's DDL
 * and the {@code db_version} row that records it commit together, so db_version
 * is never advanced past a migration that did not fully apply.  On any failure
 * the migrator throws; the caller (KissInit.init2) marks {@link SchemaStatus}
 * not-ready, which blocks logins (fail-closed).
 */
public final class SchemaMigrator {

    private static final Logger logger = LogManager.getLogger(SchemaMigrator.class);

    private SchemaMigrator() {
    }

    /** Run the schema migration.  Throws on failure (DB left at a consistent earlier version). */
    public static void runOnStartup() throws Exception {
        MigrationRegistry.validate();
        int target = MigrationRegistry.CURRENT_DB_VERSION;
        int current = bootstrap();

        if (current > target)
            throw new IllegalStateException("Database db_version (" + current + ") is AHEAD of this code ("
                    + target + "). Deploy code with CURRENT_DB_VERSION >= " + current
                    + " or restore a database snapshot. Refusing to run.");

        if (current == target) {
            logger.info("* * * Schema is current (db_version=" + current + "); no migrations to apply");
            return;
        }

        logger.info("* * * Migrating schema from db_version=" + current + " to " + target);
        for (Migration mig : MigrationRegistry.all()) {
            if (mig.version() <= current)
                continue;
            applyOne(mig);
            current = mig.version();
            logger.info("* * * Applied migration v" + mig.version() + " (" + mig.name() + ")");
        }
        logger.info("* * * Schema migration complete; db_version=" + current);
    }

    /** Create the db_version table if needed and seed the v1 baseline; return MAX(version). */
    private static int bootstrap() throws Exception {
        Connection db = MainServlet.openNewConnection();
        boolean ok = false;
        try {
            db.execute("CREATE TABLE IF NOT EXISTS db_version (" +
                    "version integer NOT NULL PRIMARY KEY, " +
                    "applied_ts bigint NOT NULL, " +
                    "name character varying(200))");
            db.execute("INSERT INTO db_version (version, applied_ts, name) VALUES (1, ?, 'baseline') " +
                    "ON CONFLICT (version) DO NOTHING", System.currentTimeMillis());
            Record r = db.fetchOne("SELECT max(version) AS v FROM db_version");
            Integer v = r == null ? null : r.getInt("v");
            ok = true;
            return v == null ? 1 : v;
        } finally {
            MainServlet.closeConnection(db, ok);
        }
    }

    /** Apply one migration: its DDL and the db_version row commit together (or both roll back). */
    private static void applyOne(Migration mig) throws Exception {
        Connection db = MainServlet.openNewConnection();
        boolean ok = false;
        try {
            mig.apply(db);
            db.execute("INSERT INTO db_version (version, applied_ts, name) VALUES (?, ?, ?)",
                    mig.version(), System.currentTimeMillis(), mig.name());
            ok = true;
        } finally {
            MainServlet.closeConnection(db, ok);
        }
    }
}
