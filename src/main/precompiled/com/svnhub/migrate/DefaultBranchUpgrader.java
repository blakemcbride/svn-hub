package com.svnhub.migrate;

import org.kissweb.database.Connection;
import org.kissweb.database.Record;
import com.svnhub.SvnRepo;

/**
 * v1 -> v2 — backfill {@code repository.default_branch = 'trunk'} for rows where
 * it is NULL and the repository actually has a {@code /trunk} (checked via
 * SVNKit).  Additive (only fills NULLs) and idempotent.  Demonstrates a per-row
 * upgrade that derives a value from data the row already has.
 */
public class DefaultBranchUpgrader implements RecordUpgrader {

    public int fromVersion() {
        return 1;
    }

    public int toVersion() {
        return 2;
    }

    public String name() {
        return "DefaultBranchBackfill";
    }

    public void upgrade(Connection db, int repoId) throws Exception {
        Record r = db.fetchOne("SELECT fs_path, default_branch FROM repository WHERE repo_id = ?", repoId);
        if (r == null)
            return;
        if (r.getString("default_branch") != null)   // already set — leave it (additive)
            return;
        String fsPath = r.getString("fs_path");
        if (fsPath == null)
            return;
        if ("dir".equals(SvnRepo.nodeKind(fsPath, "trunk", -1L)))
            db.execute("UPDATE repository SET default_branch = 'trunk' WHERE repo_id = ? AND default_branch IS NULL", repoId);
    }
}
