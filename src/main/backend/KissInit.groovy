import org.kissweb.database.Connection
import org.kissweb.restServer.MainServlet
import org.kissweb.restServer.UserCache
import org.kissweb.restServer.UserData
import java.util.function.Consumer
import com.svnhub.migrate.SchemaMigrator
import com.svnhub.migrate.RecordMigrator
import com.svnhub.migrate.SchemaStatus

class KissInit {

    /**
     * Configure the system.
     */
    static void init() {

        MainServlet.readIniFile "application.ini", "main"

        // Public self-registration is the one method callable without authentication.
        MainServlet.allowWithoutAuthentication("services.Register", "register")

        // Set up a global logout handler that runs whenever any user logs out
        // This can be used for cleanup tasks like logging, closing resources, etc.
        UserCache.setLogoutHandler({ UserData ud ->
            // Example: Log the logout event
            println "User ${ud.getUsername()} (ID: ${ud.getUserId()}) is logging out"

            // Add any custom cleanup code here
            // Examples:
            // - Close user-specific resources
            // - Update database logout timestamp
            // - Send notifications
            // - Clean up temporary files
        } as Consumer<UserData>)

    }

    /**
     * Code to run once the database is open but before the app is running.
     *
     * Auto-update (see AutoUpdate.md): bring the database current with the
     * deployed code.  Stage 1 (schema) runs first because it may create the
     * columns Stage 2 (per-row) reads.  A schema-migration failure marks the
     * schema not-ready, which blocks logins (fail-closed); the per-row stage
     * runs only when the schema is ready and never blocks startup itself.
     */
    static void init2(Connection db) {
        SchemaStatus.reset()
        try {
            SchemaMigrator.runOnStartup()
            SchemaStatus.markReady()
        } catch (Throwable t) {
            SchemaStatus.fail(t.getMessage())
            println "* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *"
            println "* * * SCHEMA MIGRATION FAILED — logins are blocked until fixed"
            println "* * * " + t.getMessage()
            println "* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *"
            t.printStackTrace()
        }
        if (SchemaStatus.isReady())
            RecordMigrator.runOnStartup()
    }
}
