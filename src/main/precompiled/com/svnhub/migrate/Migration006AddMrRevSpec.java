package com.svnhub.migrate;

import org.kissweb.database.Connection;

/**
 * v6 — add {@code merge_request.rev_spec}: an optional "commits to include"
 * specification for a merge request.
 *
 * <p>NULL (the default, and every pre-existing row) means "all eligible
 * revisions" — the prior behavior.  A non-null value is a spec like
 * {@code 3-7,10,12-15} selecting specific source revisions to merge (parsed by
 * {@link com.svnhub.RevSpec}).  Additive and idempotent.</p>
 */
public class Migration006AddMrRevSpec implements Migration {

    public int version() {
        return 6;
    }

    public String name() {
        return "AddMrRevSpec";
    }

    public void apply(Connection db) throws Exception {
        db.execute("ALTER TABLE merge_request ADD COLUMN IF NOT EXISTS rev_spec character varying(500) NULL");
    }
}
