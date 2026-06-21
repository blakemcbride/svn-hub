package com.svnhub.migrate;

import org.kissweb.database.Connection;

/**
 * v4 — add email verification + password-reset support.
 *
 * <p>Adds {@code users.email_verified} and the {@code verification_code} table,
 * then grandfathers pre-existing accounts to verified (they registered before
 * email verification existed and must not be locked out).  Additive and
 * idempotent in the DDL; the grandfather UPDATE runs exactly once because the
 * migration framework applies each version a single time (the {@code db_version}
 * bump commits with the migration), so it never re-verifies later accounts.</p>
 */
public class Migration004AddEmailVerification implements Migration {

    public int version() {
        return 4;
    }

    public String name() {
        return "AddEmailVerification";
    }

    public void apply(Connection db) throws Exception {
        db.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified character(1) NOT NULL DEFAULT 'N'");

        db.execute(
            "CREATE TABLE IF NOT EXISTS verification_code (" +
            "    code_id     serial                 NOT NULL PRIMARY KEY," +
            "    user_id     integer                NOT NULL REFERENCES users(user_id)," +
            "    purpose     character varying(20)  NOT NULL," +
            "    code        character(6)           NOT NULL," +
            "    expires_ts  bigint                 NOT NULL," +
            "    attempts    integer                NOT NULL DEFAULT 0," +
            "    created_ts  bigint                 NOT NULL," +
            "    CONSTRAINT verification_code_uniq UNIQUE (user_id, purpose)" +
            ")");

        // Grandfather accounts that existed before verification was introduced.
        db.execute("UPDATE users SET email_verified = 'Y' WHERE email_verified = 'N'");
    }
}
