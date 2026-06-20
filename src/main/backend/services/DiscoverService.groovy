package services

import org.kissweb.json.JSONArray
import org.kissweb.json.JSONObject
import org.kissweb.database.Connection
import org.kissweb.database.Record
import org.kissweb.restServer.ProcessServlet
import org.kissweb.restServer.MainServlet
import org.kissweb.UserException
import com.svnhub.RepoAccess

/**
 * GitHub-style discovery: find people and browse their public repositories.
 *
 * Any logged-in user may use this.  Email addresses are never exposed here
 * (email is the login credential) — only the public handle, full name, and
 * public-repo counts.  A profile shows a user's public repos to everyone; the
 * owner (and admins) additionally see that user's private repos.
 */
class DiscoverService {

    /** Search active users by handle or full name.  Returns public, non-PII fields only. */
    void searchUsers(JSONObject injson, JSONObject outjson, Connection db, ProcessServlet servlet) {
        String q = injson.getString("query", "")
        if (q != null)
            q = q.trim().toLowerCase()
        String like = "%" + (q == null ? "" : q) + "%"
        List<Record> recs = db.fetchAll("""
                select u.user_id, u.handle, u.full_name,
                       (select count(*) from repository r
                          where r.owner_id = u.user_id and r.visibility = 'public' and r.is_active = 'Y') as public_repos
                from users u
                where u.user_active = 'Y'
                  and (lower(u.handle) like ? or lower(coalesce(u.full_name,'')) like ?)
                order by public_repos desc, u.handle""", like, like)
        JSONArray rows = new JSONArray()
        for (Record r : recs) {
            JSONObject o = new JSONObject()
            o.put("handle", r.getString("handle"))
            o.put("fullName", r.getString("full_name"))
            o.put("publicRepoCount", r.getLong("public_repos"))
            rows.put(o)
        }
        outjson.put("rows", rows)
    }

    /**
     * Search public repositories by name, description, or key (all substring, case-insensitive).
     * Admins additionally see private repos.
     */
    void searchRepos(JSONObject injson, JSONObject outjson, Connection db, ProcessServlet servlet) {
        Integer viewerId = (Integer) servlet.getUserData().getUserId()
        boolean admin = RepoAccess.isAdmin(db, viewerId)
        String q = injson.getString("query", "")
        if (q != null)
            q = q.trim().toLowerCase()
        String like = "%" + (q == null ? "" : q) + "%"
        String match = "(lower(name) like ? or lower(coalesce(description,'')) like ? or lower(repo_key) like ?)"
        List<Record> recs
        if (admin)
            recs = db.fetchAll("select * from repository where is_active = 'Y' and " + match + " order by name",
                    like, like, like)
        else
            recs = db.fetchAll("select * from repository where is_active = 'Y' and visibility = 'public' and " + match + " order by name",
                    like, like, like)
        String base = baseUrl()
        JSONArray rows = new JSONArray()
        for (Record r : recs)
            rows.put(repoJson(r, base))
        outjson.put("rows", rows)
    }

    /** A user's public profile: their info plus their public repositories (and private ones if the viewer owns them or is an admin). */
    void getProfile(JSONObject injson, JSONObject outjson, Connection db, ProcessServlet servlet) {
        Integer viewerId = (Integer) servlet.getUserData().getUserId()
        String handle = injson.getString("handle", "")
        if (handle != null)
            handle = handle.trim().toLowerCase()
        if (!handle)
            throw new UserException("No username given.")
        Record u = db.fetchOne("select user_id, handle, full_name, created_ts from users where lower(handle) = ? and user_active = 'Y'", handle)
        if (u == null)
            throw new UserException("User not found.")
        int ownerId = u.getInt("user_id")

        JSONObject profile = new JSONObject()
        profile.put("handle", u.getString("handle"))
        profile.put("fullName", u.getString("full_name"))
        profile.put("memberSince", u.getLong("created_ts"))
        outjson.put("profile", profile)

        boolean privileged = (ownerId == viewerId) || RepoAccess.isAdmin(db, viewerId)
        List<Record> repos
        if (privileged)
            repos = db.fetchAll("select * from repository where owner_id = ? and is_active = 'Y' order by name", ownerId)
        else
            repos = db.fetchAll("select * from repository where owner_id = ? and is_active = 'Y' and visibility = 'public' order by name", ownerId)

        String base = baseUrl()
        JSONArray rows = new JSONArray()
        for (Record r : repos)
            rows.put(repoJson(r, base))
        outjson.put("repos", rows)
    }

    /** Repo summary JSON shared by searchRepos and getProfile.  ownerHandle is the repo_key prefix. */
    private static JSONObject repoJson(Record r, String base) {
        JSONObject o = new JSONObject()
        String key = r.getString("repo_key")
        o.put("repoId", r.getInt("repo_id"))
        o.put("repoKey", key)
        o.put("ownerHandle", key != null && key.contains("/") ? key.substring(0, key.indexOf("/")) : null)
        o.put("name", r.getString("name"))
        o.put("description", r.getString("description"))
        o.put("visibility", r.getString("visibility"))
        o.put("headRevision", r.getInt("head_revision"))
        o.put("createdTs", r.getLong("created_ts"))
        o.put("checkoutUrl", (base ? base : "") + "/" + key)
        return o
    }

    private static String baseUrl() {
        String b = MainServlet.getEnvironment("SvnBaseUrl")
        return b == null ? "" : b.replaceAll('/$', '')
    }
}
