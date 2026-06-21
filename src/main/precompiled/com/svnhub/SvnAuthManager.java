package com.svnhub;

import java.util.ArrayList;
import java.util.List;

import org.kissweb.database.Connection;
import org.kissweb.database.Record;

/**
 * Regenerates svnserve auth files from the SvnHub database.
 *
 * SvnHub is the system of record for access: the {@code repository_access} and
 * {@code users} tables drive what svnserve enforces. These methods read that
 * state and (re)write the on-disk config via {@link SvnAuth}:
 *
 *   - {@link #regeneratePasswd} writes the single shared {@code passwd} file
 *     from every active user that has an SVN password set.
 *   - {@link #regenerateRepoAuth} writes one repository's {@code conf/authz}
 *     and {@code conf/svnserve.conf} (the latter points at the shared passwd
 *     and the repo-local authz).
 *
 * Lives in precompiled (not a Groovy service) so it is always loaded and can be
 * called reliably from any hot-reloaded service.
 */
public final class SvnAuthManager {

    /** Shared authentication realm; one realm lets a client cache one credential for all repos. */
    public static final String REALM = "SvnHub";

    private SvnAuthManager() {
    }

    /**
     * Rewrite the shared svnserve passwd file from all active users that have an
     * SVN password set.
     *
     * @param db               open Kiss connection
     * @param sharedPasswdPath absolute path of the shared passwd file
     */
    public static void regeneratePasswd(Connection db, String sharedPasswdPath) throws Exception {
        List<Record> users = db.fetchAll(
                "select user_name, svn_password from users " +
                "where user_active = 'Y' and svn_password is not null and svn_password <> '' " +
                "order by user_name");
        List<String[]> rows = new ArrayList<>();
        for (Record u : users)
            rows.add(new String[] {u.getString("user_name"), u.getString("svn_password")});
        SvnAuth.writeFile(sharedPasswdPath, SvnAuth.buildPasswd(rows));
    }

    /**
     * Rewrite one repository's authz and svnserve.conf from repository_access.
     *
     * @param db               open Kiss connection
     * @param repoId           repository id
     * @param sharedPasswdPath absolute path of the shared passwd file referenced by svnserve.conf
     */
    public static void regenerateRepoAuth(Connection db, int repoId, String sharedPasswdPath) throws Exception {
        Record repo = db.fetchOne("select fs_path, visibility from repository where repo_id = ?", repoId);
        if (repo == null)
            return;
        String fsPath = repo.getString("fs_path");
        boolean publicRead = "public".equals(repo.getString("visibility"));

        List<Record> access = db.fetchAll(
                "select u.user_name, ra.can_read, ra.can_write " +
                "from repository_access ra join users u on u.user_id = ra.user_id " +
                "where ra.repo_id = ? and u.user_active = 'Y' order by u.user_name",
                repoId);
        List<String[]> perms = new ArrayList<>();
        for (Record a : access) {
            String perm = null;
            if ("Y".equals(a.getString("can_write")))
                perm = "rw";
            else if ("Y".equals(a.getString("can_read")))
                perm = "r";
            if (perm != null)
                perms.add(new String[] {a.getString("user_name"), perm});
        }

        String confDir = fsPath + "/conf";
        // authz-db is relative to the repo's conf dir; password-db is the shared absolute path.
        SvnAuth.writeFile(confDir + "/authz", SvnAuth.buildAuthz(perms, publicRead));
        SvnAuth.writeFile(confDir + "/svnserve.conf",
                SvnAuth.buildSvnserveConf(sharedPasswdPath, "authz", REALM, publicRead));
    }
}
