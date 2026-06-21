package com.svnhub;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kissweb.RestClient;
import org.kissweb.json.JSONObject;
import org.kissweb.restServer.MainServlet;

/**
 * Outbound transactional email via Postmark's REST API (sending only).
 *
 * <p>Mirrors the approach used elsewhere (a single POST to
 * {@code https://api.postmarkapp.com/email} authenticated with the Postmark
 * server token).  SvnHub uses it for the two short-lived code emails:
 * email verification and password reset.</p>
 *
 * <p>All configuration lives in {@code application.ini} (see
 * {@code application.template.ini}) and is read via
 * {@link MainServlet#getEnvironment(String)} — secrets are never hard-coded:</p>
 * <ul>
 *   <li>{@code PostmarkApiToken} — the X-Postmark-Server-Token value</li>
 *   <li>{@code MailFrom} — the From address (a confirmed Postmark sender)</li>
 *   <li>{@code MailFromName} — optional display name for the From address</li>
 *   <li>{@code MailMessageStream} — Postmark stream (default {@code outbound})</li>
 *   <li>{@code MailEnabled} — when not {@code true}, the body is logged instead of sent</li>
 * </ul>
 */
public final class Mailer {

    private static final Logger logger = LogManager.getLogger(Mailer.class);
    private static final String POSTMARK_URL = "https://api.postmarkapp.com/email";

    private Mailer() {
    }

    /** application.ini value as a String (getEnvironment returns Object). */
    private static String env(String key) {
        Object v = MainServlet.getEnvironment(key);
        return v == null ? null : v.toString();
    }

    /** Thrown when an email cannot be sent (misconfiguration or a Postmark rejection). */
    public static class MailException extends RuntimeException {
        public MailException(String msg) {
            super(msg);
        }
    }

    /**
     * Send an HTML email to a single recipient via Postmark.
     *
     * @param toEmail recipient address (required)
     * @param toName  recipient display name (may be null/empty)
     * @param subject subject line
     * @param html    HTML body
     * @throws MailException if configuration is missing or Postmark rejects the send
     */
    public static void sendHtml(String toEmail, String toName, String subject, String html) {
        if (toEmail == null || toEmail.trim().isEmpty())
            throw new MailException("No recipient email address.");

        String from = env("MailFrom");
        if (from == null || from.isEmpty())
            throw new MailException("MailFrom is not configured in application.ini.");
        String fromName = env("MailFromName");
        if (fromName != null && !fromName.isEmpty())
            from = fromName + " <" + from + ">";

        String stream = env("MailMessageStream");
        if (stream == null || stream.isEmpty())
            stream = "outbound";

        // Development escape hatch: log instead of actually sending.
        if (!"true".equalsIgnoreCase(env("MailEnabled"))) {
            logger.info("MailEnabled is not true — not sending. To=" + toEmail
                    + " Subject=\"" + subject + "\"\n" + html);
            return;
        }

        String token = env("PostmarkApiToken");
        if (token == null || token.isEmpty())
            throw new MailException("PostmarkApiToken is not configured in application.ini.");

        String to = (toName != null && !toName.trim().isEmpty())
                ? (toName.trim() + " <" + toEmail + ">")
                : toEmail;

        JSONObject headers = new JSONObject();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("X-Postmark-Server-Token", token);

        JSONObject data = new JSONObject();
        data.put("From", from);
        data.put("To", to);
        data.put("Subject", subject);
        data.put("HtmlBody", html);
        data.put("MessageStream", stream);

        try {
            // Use performService (not jsonCall) so we can read Postmark's error
            // body on a non-2xx response (e.g. 422 for an unconfirmed sender).
            RestClient rc = new RestClient();
            int status = rc.performService("POST", POSTMARK_URL, data.toString(), headers);
            String body = rc.getResponseString();
            JSONObject resp = null;
            if (body != null && !body.isEmpty()) {
                try {
                    resp = new JSONObject(body);
                } catch (Exception ignore) {
                    // non-JSON body — leave resp null and report the raw body
                }
            }
            int errorCode = resp != null ? resp.getInt("ErrorCode", -1) : -1;
            if (status >= 200 && status < 300 && errorCode == 0) {
                logger.info("Sent email to " + toEmail + " (subject: \"" + subject + "\")");
                return;
            }
            String message = resp != null ? resp.getString("Message", body) : body;
            throw new MailException("Postmark rejected the send (HTTP " + status
                    + ", ErrorCode " + errorCode + "): " + message);
        } catch (MailException me) {
            throw me;
        } catch (Exception e) {
            throw new MailException("Failed to send email to " + toEmail + ": " + e.getMessage());
        }
    }
}
