package services

import org.kissweb.json.JSONObject
import org.kissweb.database.Connection
import org.kissweb.database.Record
import org.kissweb.restServer.ProcessServlet
import org.kissweb.restServer.MainServlet
import org.kissweb.PasswordHash
import org.kissweb.UserException
import com.svnhub.SvnAuthManager

/**
 * Public self-registration (GitHub-style).  This is the only service method that
 * runs without authentication; it is allow-listed in KissInit.groovy via
 * MainServlet.allowWithoutAuthentication("services.Register", "register").
 *
 * A single password is captured: it is stored hashed for web login and, in the
 * clear, as the SVN password (svnserve's passwd format requires clear text), so
 * the same credential works for both the web UI and `svn` clients.  New accounts
 * are active immediately and are not administrators.
 */
class Register {

    void register(JSONObject injson, JSONObject outjson, Connection db, ProcessServlet servlet) {
        if (db == null)
            throw new UserException("Registration is unavailable (no database configured).")

        String username = injson.getString("username", "")
        if (username != null)
            username = username.trim().toLowerCase()
        String password = injson.getString("password", "")
        String fullName = injson.getString("fullName", "")
        String email = injson.getString("email", "")

        if (!username || !(username ==~ /[a-z0-9][a-z0-9_.-]{0,63}/))
            throw new UserException("Invalid username. Use 1-64 characters (letters, digits, dot, dash, underscore) and do not start with a symbol.")
        if (!password || password.length() < 6)
            throw new UserException("Password must be at least 6 characters.")
        if (db.exists("select 1 from users where lower(user_name) = ?", username))
            throw new UserException("That username is already taken.")

        Record rec = db.newRecord("users")
        rec.set("user_name", username)
        rec.set("user_password", PasswordHash.hash(password))
        rec.set("svn_password", password)        // same credential, for svn client auth
        rec.set("full_name", fullName)
        rec.set("email", email)
        rec.set("is_admin", "N")
        rec.set("user_active", "Y")
        rec.set("created_ts", System.currentTimeMillis())
        rec.addRecord()

        // Make the SVN credential live immediately.
        String confDir = MainServlet.getEnvironment("SvnConfDir")
        if (confDir)
            SvnAuthManager.regeneratePasswd(db, confDir + "/passwd")

        outjson.put("username", username)
    }
}
