package com.svnhub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Serializes svnserve authentication/authorization config files for SvnHub.
 *
 * SvnHub is the system of record for access. These builders turn the
 * repository_access / users data (gathered by the Groovy services) into the
 * exact file bodies svnserve reads:
 *
 *   - a shared {@code passwd} file (svnserve stores SVN passwords in the clear),
 *   - one {@code authz} file per repository (rules scoped with the bare
 *     {@code [/]} header so they apply only to that repository), and
 *   - a per-repository {@code svnserve.conf} pointing at both.
 *
 * The builders are pure (string in / string out) so they can be unit tested
 * without touching the filesystem; {@link #writeFile} performs the write.
 */
public final class SvnAuth {

    private SvnAuth() {
    }

    /**
     * Build the shared svnserve {@code passwd} body.
     *
     * @param userPasswords rows of {username, svnPassword}; rows with a null or
     *                      empty password are skipped (that user simply cannot
     *                      authenticate to SVN until a password is set)
     */
    public static String buildPasswd(List<String[]> userPasswords) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Managed by SvnHub - do not edit by hand.\n");
        sb.append("[users]\n");
        for (String[] row : userPasswords) {
            String user = row[0];
            String pw = row.length > 1 ? row[1] : null;
            if (user == null || user.isEmpty())
                continue;
            if (pw == null || pw.isEmpty())
                continue;
            sb.append(user).append(" = ").append(pw).append('\n');
        }
        return sb.toString();
    }

    /**
     * Build a per-repository {@code authz} body.
     *
     * @param userPerms  rows of {username, perm} where perm is "rw" or "r".
     * @param publicRead when true the catch-all line is {@code * = r} (any
     *                   authenticated user may read/checkout); otherwise it is a
     *                   deny-all ({@code * =}) so the repository is private.
     */
    public static String buildAuthz(List<String[]> userPerms, boolean publicRead) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Managed by SvnHub - do not edit by hand.\n");
        sb.append("[/]\n");
        for (String[] row : userPerms) {
            String user = row[0];
            String perm = row.length > 1 ? row[1] : "r";
            if (user == null || user.isEmpty())
                continue;
            sb.append(user).append(" = ").append(perm).append('\n');
        }
        sb.append(publicRead ? "* = r\n" : "* =\n");
        return sb.toString();
    }

    /**
     * Build a repository's {@code svnserve.conf}.
     *
     * @param passwdPath path to the shared passwd file (absolute recommended)
     * @param authzPath  path to this repo's authz file (relative to conf dir, or absolute)
     * @param realm      authentication realm (a shared realm lets a client cache
     *                   one credential across all SvnHub repos)
     * @param publicRead when true, anonymous (unauthenticated) read/checkout is
     *                   allowed ({@code anon-access = read}); otherwise anonymous
     *                   access is denied ({@code anon-access = none}).  Writing
     *                   always requires authentication ({@code auth-access = write}).
     */
    public static String buildSvnserveConf(String passwdPath, String authzPath, String realm, boolean publicRead) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Managed by SvnHub - do not edit by hand.\n");
        sb.append("[general]\n");
        // Public repositories permit anonymous checkout (no username/password);
        // private repositories require authentication.  Writes always require auth.
        sb.append("anon-access = ").append(publicRead ? "read" : "none").append('\n');
        sb.append("auth-access = write\n");
        sb.append("password-db = ").append(passwdPath).append('\n');
        sb.append("authz-db = ").append(authzPath).append('\n');
        sb.append("realm = ").append(realm).append('\n');
        return sb.toString();
    }

    /** Write {@code content} to {@code path} (UTF-8), creating parent dirs. */
    public static void writeFile(String path, String content) throws IOException {
        Path p = Path.of(path);
        if (p.getParent() != null)
            Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }
}
