package com.svnhub;

/**
 * Centralized HTML bodies for SvnHub's transactional emails so the wording and
 * styling of the verification and password-reset messages stay consistent.
 */
public final class EmailBodies {

    private EmailBodies() {
    }

    /** Email-verification message containing the 6-digit code. */
    public static String verifyEmail(String code) {
        return wrap("Verify your email",
                "Welcome to Svn-Hub! Use this code to verify your email address:",
                code,
                "If you did not create a Svn-Hub account, you can safely ignore this email.");
    }

    /** Password-reset message containing the 6-digit code. */
    public static String passwordReset(String code) {
        return wrap("Reset your password",
                "We received a request to reset your Svn-Hub password. Use this code to continue:",
                code,
                "If you did not request a password reset, you can safely ignore this email.");
    }

    private static String wrap(String heading, String intro, String code, String footer) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:0 auto;\">"
             + "<h2 style=\"color:#24292f;\">" + heading + "</h2>"
             + "<p style=\"font-size:15px;color:#24292f;\">" + intro + "</p>"
             + "<p style=\"font-size:32px;font-weight:bold;letter-spacing:6px;color:#0969da;"
             +   "background:#f6f8fa;padding:14px 0;text-align:center;border-radius:6px;margin:18px 0;\">"
             +   code + "</p>"
             + "<p style=\"font-size:13px;color:#57606a;\">This code expires in 15 minutes.</p>"
             + "<p style=\"font-size:12px;color:#8b949e;\">" + footer + "</p>"
             + "</div>";
    }
}
