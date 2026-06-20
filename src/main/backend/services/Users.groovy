package services

import org.kissweb.json.JSONArray
import org.kissweb.json.JSONObject
import org.kissweb.database.Connection
import org.kissweb.database.Record
import org.kissweb.restServer.ProcessServlet
import org.kissweb.restServer.MainServlet
import org.kissweb.PasswordHash
import org.kissweb.UserException
import com.svnhub.SvnAuthManager
import com.svnhub.RepoAccess

/**
 * User administration for SvnHub.  Manages the login credential (PBKDF2 hash),
 * profile fields, the admin flag, and the SVN password (written to svnserve's
 * passwd file).  Setting/clearing an SVN password regenerates that file.
 */
class Users {

    void getRecords(JSONObject injson, JSONObject outjson, Connection db, ProcessServlet servlet) {
        if (db == null) {
            outjson.put("nodb", true)
            return
        }
        requireAdmin(db, servlet)
        List<Record> recs = db.fetchAll("""select user_id, user_name, handle, full_name, email, is_admin,
                user_active, svn_password from users order by handle""")
        JSONArray rows = new JSONArray()
        for (Record rec : recs) {
            JSONObject row = new JSONObject()
            row.put("id", rec.getInt("user_id"))
            row.put("userName", rec.getString("user_name"))
            row.put("handle", rec.getString("handle"))
            row.put("fullName", rec.getString("full_name"))
            row.put("email", rec.getString("email"))
            row.put("isAdmin", rec.getString("is_admin"))
            row.put("userActive", rec.getString("user_active"))
            row.put("hasSvnPassword", rec.getString("svn_password") ? "Y" : "N")
            rows.put(row)
        }
        outjson.put("rows", rows)
    }

    void addRecord(JSONObject injson, JSONObject outjson, Connection db, ProcessServlet servlet) {
        requireAdmin(db, servlet)
        Record rec = db.newRecord("users")
        rec.set("user_name", injson.getString("userName"))
        rec.set("handle", normalizeHandle(db, injson.getString("handle", ""), null))
        rec.set("user_password", PasswordHash.hash(injson.getString("userPassword")))
        rec.set("full_name", injson.getString("fullName", ""))
        rec.set("email", injson.getString("email", ""))
        rec.set("is_admin", "Y".equals(injson.getString("isAdmin", "N")) ? "Y" : "N")
        rec.set("user_active", injson.getString("userActive", "Y"))
        rec.set("created_ts", System.currentTimeMillis())
        String svnPw = injson.getString("svnPassword", "")
        boolean svnSet = svnPw != null && !svnPw.isEmpty()
        if (svnSet)
            rec.set("svn_password", svnPw)
        rec.addRecord()
        if (svnSet)
            regeneratePasswd(db)
    }

    void updateRecord(JSONObject injson, JSONObject outjson, Connection db, ProcessServlet servlet) {
        requireAdmin(db, servlet)
        Record rec = db.fetchOne("select * from users where user_id = ?", injson.getInt("id"))
        rec.set("user_name", injson.getString("userName"))
        rec.set("handle", normalizeHandle(db, injson.getString("handle", ""), injson.getInt("id")))
        rec.set("full_name", injson.getString("fullName", ""))
        rec.set("email", injson.getString("email", ""))
        rec.set("is_admin", "Y".equals(injson.getString("isAdmin", "N")) ? "Y" : "N")
        rec.set("user_active", injson.getString("userActive", "Y"))
        // Only change a password when a new (non-empty) one is supplied.
        String userPassword = injson.getString("userPassword", "")
        if (userPassword != null && !userPassword.isEmpty())
            rec.set("user_password", PasswordHash.hash(userPassword))
        String svnPw = injson.getString("svnPassword", "")
        boolean svnSet = svnPw != null && !svnPw.isEmpty()
        if (svnSet)
            rec.set("svn_password", svnPw)
        rec.update()
        if (svnSet)
            regeneratePasswd(db)
    }

    void deleteRecord(JSONObject injson, JSONObject outjson, Connection db, ProcessServlet servlet) {
        requireAdmin(db, servlet)
        db.execute("delete from users where user_id = ?", injson.getInt("id"))
        regeneratePasswd(db)
    }

    private static void requireAdmin(Connection db, ProcessServlet servlet) {
        Integer userId = (Integer) servlet.getUserData().getUserId()
        if (!RepoAccess.isAdmin(db, userId))
            throw new UserException("Administrator access is required to manage users.")
    }

    /** Validate + normalize a handle; ensure it is unique (excluding the given user when editing). */
    private static String normalizeHandle(Connection db, String raw, Integer excludeUserId) {
        String h = raw == null ? "" : raw.trim().toLowerCase()
        if (!h || !(h ==~ /[a-z0-9][a-z0-9_-]{0,63}/))
            throw new UserException("Invalid username. Use 1-64 characters (letters, digits, dash or underscore), starting with a letter or digit.")
        Record dup = (excludeUserId == null)
            ? db.fetchOne("select user_id from users where lower(handle) = ?", h)
            : db.fetchOne("select user_id from users where lower(handle) = ? and user_id <> ?", h, excludeUserId)
        if (dup != null)
            throw new UserException("That username is already taken.")
        return h
    }

    private static void regeneratePasswd(Connection db) {
        String confDir = MainServlet.getEnvironment("SvnConfDir")
        if (confDir)
            SvnAuthManager.regeneratePasswd(db, confDir + "/passwd")
    }
}
