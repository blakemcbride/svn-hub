package com.svnhub.migrate;

/**
 * Shared startup-migration status.
 *
 * The schema migration ({@link SchemaMigrator}) runs at startup from
 * KissInit.init2.  Kiss swallows exceptions thrown from init2, so on its own a
 * failed migration would be invisible and the app would serve on a stale schema.
 * To emulate OwnSona's "never serve a half-migrated DB", this holder records
 * whether the schema is current; {@code Login.login} refuses logins when it is
 * not — a single chokepoint that fails the app closed.
 */
public final class SchemaStatus {

    private static volatile boolean ready = false;
    private static volatile String error = null;

    private SchemaStatus() {
    }

    /** Called before migrations run. */
    public static void reset() {
        ready = false;
        error = null;
    }

    /** Called after the schema migration completes successfully. */
    public static void markReady() {
        ready = true;
        error = null;
    }

    /** Called when the schema migration fails (or the DB is ahead of the code). */
    public static void fail(String message) {
        ready = false;
        error = message;
    }

    /** True once the schema is at the version the code expects. */
    public static boolean isReady() {
        return ready;
    }

    /** A human-readable reason the schema is not ready, or null. */
    public static String getError() {
        return error;
    }
}
