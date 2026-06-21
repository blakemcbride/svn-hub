package services

import org.kissweb.json.JSONObject
import org.kissweb.database.Connection
import org.kissweb.database.Record
import org.kissweb.restServer.ProcessServlet
import org.kissweb.restServer.MainServlet
import org.kissweb.PasswordHash
import org.kissweb.UserException
import com.svnhub.SvnAuthManager
import com.svnhub.VerificationCodes
import com.svnhub.Mailer
import com.svnhub.EmailBodies

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

        String email = injson.getString("email", "")
        if (email != null)
            email = email.trim().toLowerCase()
        String handle = injson.getString("handle", "")
        if (handle != null)
            handle = handle.trim().toLowerCase()
        String password = injson.getString("password", "")
        String fullName = injson.getString("fullName", "")

        // The email is the login identifier; the handle is the URL-safe namespace name.
        if (!email || !(email ==~ /[^@\s]+@[^@\s]+\.[^@\s]+/))
            throw new UserException("Please enter a valid email address.")
        if (!handle || !(handle ==~ /[a-z0-9][a-z0-9_-]{0,63}/))
            throw new UserException("Invalid username. Use 1-64 characters (letters, digits, dash or underscore), starting with a letter or digit.")
        if (!password || password.length() < 6)
            throw new UserException("Password must be at least 6 characters.")
        if (db.exists("select 1 from users where lower(user_name) = ?", email))
            throw new UserException("An account with that email already exists.")
        if (db.exists("select 1 from users where lower(handle) = ?", handle))
            throw new UserException("That username is already taken.")

        Record rec = db.newRecord("users")
        rec.set("user_name", email)
        rec.set("handle", handle)
        rec.set("user_password", PasswordHash.hash(password))
        rec.set("svn_password", password)        // same credential, for svn client auth
        rec.set("full_name", fullName)
        rec.set("email", email)
        rec.set("is_admin", "N")
        rec.set("user_active", "Y")
        rec.set("email_verified", "N")           // confirmed via the emailed 6-digit code
        rec.set("created_ts", System.currentTimeMillis())
        rec.addRecord()

        // Make the SVN credential live immediately.
        String confDir = MainServlet.getEnvironment("SvnConfDir")
        if (confDir)
            SvnAuthManager.regeneratePasswd(db, confDir + "/passwd")

        // Email a verification code.  A send failure must not fail registration —
        // the account exists and the user can resend from the verify screen.
        Integer userId = (Integer) db.fetchOne("select user_id from users where user_name = ?", email).getInt("user_id")
        try {
            String code = VerificationCodes.issue(db, userId, VerificationCodes.PURPOSE_EMAIL, VerificationCodes.DEFAULT_TTL_MINUTES)
            Mailer.sendHtml(email, fullName, "Verify your Svn-Hub email", EmailBodies.verifyEmail(code))
        } catch (Exception e) {
            println "* * * Register: could not send verification email to " + email + ": " + e.getMessage()
        }

        outjson.put("username", email)
    }
}
