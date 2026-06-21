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
 * Unauthenticated forgotten-password flow.  Both methods are allow-listed in
 * KissInit.groovy (MainServlet.allowWithoutAuthentication):
 *
 *   requestReset(email)                    -> emails a 6-digit code if the account exists
 *   resetPassword(email, code, newPassword)-> sets a new password on a valid code
 *
 * requestReset never reveals whether an account exists (no account enumeration):
 * it always reports success.
 */
class PasswordResetService {

    void requestReset(JSONObject injson, JSONObject outjson, Connection db, ProcessServlet servlet) {
        if (db == null)
            throw new UserException("Password reset is unavailable (no database configured).")
        String email = injson.getString("email", "")
        if (email != null)
            email = email.trim().toLowerCase()

        // Always report success regardless of whether the account exists.
        outjson.put("sent", true)
        if (!email)
            return
        Record rec = db.fetchOne("select user_id, full_name from users where lower(email) = ? and user_active = 'Y'", email)
        if (rec == null)
            return
        Integer userId = (Integer) rec.getInt("user_id")
        try {
            String code = VerificationCodes.issue(db, userId, VerificationCodes.PURPOSE_RESET, VerificationCodes.DEFAULT_TTL_MINUTES)
            Mailer.sendHtml(email, rec.getString("full_name"), "Reset your Svn-Hub password", EmailBodies.passwordReset(code))
        } catch (Exception e) {
            println "* * * PasswordResetService.requestReset: could not send reset email to " + email + ": " + e.getMessage()
        }
    }

    void resetPassword(JSONObject injson, JSONObject outjson, Connection db, ProcessServlet servlet) {
        if (db == null)
            throw new UserException("Password reset is unavailable (no database configured).")
        String email = injson.getString("email", "")
        if (email != null)
            email = email.trim().toLowerCase()
        String code = injson.getString("code", "")
        String next = injson.getString("newPassword", "")
        if (!email)
            throw new UserException("Please enter your email address.")
        if (next == null || next.length() < 6)
            throw new UserException("Your new password must be at least 6 characters.")

        Record rec = db.fetchOne("select user_id from users where lower(email) = ? and user_active = 'Y'", email)
        // Use the same wording whether the email is unknown or the code is wrong,
        // so this endpoint does not reveal which accounts exist.
        if (rec == null)
            throw new UserException("That code is incorrect or has expired.")
        Integer userId = (Integer) rec.getInt("user_id")
        if (!VerificationCodes.verify(db, userId, VerificationCodes.PURPOSE_RESET, code))
            throw new UserException("That code is incorrect or has expired.")

        // One credential serves both the web UI (hashed) and svn clients (clear text).
        db.execute("update users set user_password = ?, svn_password = ? where user_id = ?",
                PasswordHash.hash(next), next, userId)
        // A successful reset also proves the email is deliverable.
        db.execute("update users set email_verified = 'Y' where user_id = ?", userId)
        String confDir = MainServlet.getEnvironment("SvnConfDir")
        if (confDir)
            SvnAuthManager.regeneratePasswd(db, confDir + "/passwd")
        outjson.put("reset", true)
    }
}
