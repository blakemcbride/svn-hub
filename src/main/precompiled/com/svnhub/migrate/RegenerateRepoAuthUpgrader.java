package com.svnhub.migrate;

import org.kissweb.database.Connection;
import org.kissweb.restServer.MainServlet;
import com.svnhub.SvnAuthManager;

/**
 * v2 -&gt; v3 — regenerate each repository's svnserve auth files so existing
 * repositories pick up the visibility-based anonymous-checkout policy: a
 * {@code public} repo now serves with {@code anon-access = read} (no username or
 * password needed to check it out), while {@code private} stays {@code anon-access = none}.
 *
 * <p>Rewrites the repo's {@code conf/authz} + {@code conf/svnserve.conf} from
 * current DB state via {@link SvnAuthManager#regenerateRepoAuth}.  Idempotent
 * (same DB state produces the same files) and per-row isolated.  A no-op if
 * {@code SvnConfDir} is not configured.</p>
 */
public class RegenerateRepoAuthUpgrader implements RecordUpgrader {

    public int fromVersion() {
        return 2;
    }

    public int toVersion() {
        return 3;
    }

    public String name() {
        return "RegenerateRepoAuth";
    }

    public void upgrade(Connection db, int repoId) throws Exception {
        Object confDir = MainServlet.getEnvironment("SvnConfDir");
        if (confDir == null || confDir.toString().isEmpty())
            return;
        SvnAuthManager.regenerateRepoAuth(db, repoId, confDir.toString() + "/passwd");
    }
}
