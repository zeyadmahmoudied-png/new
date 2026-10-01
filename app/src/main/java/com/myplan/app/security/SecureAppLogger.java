package com.myplan.app.security;

import android.content.Context;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Redacts secrets before delegating to AppInfrastructure.log.
 * Best-effort client-side protection against accidental secret logging.
 */
public final class SecureAppLogger {
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[a-z0-9\\-._~+/]+=*");
    private static final Pattern JWT = Pattern.compile("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+");
    private static final Pattern PASSWORD_KV = Pattern.compile("(?i)(password|passwd|pwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|authorization)\\s*[:=]\\s*\\S+");

    private SecureAppLogger() {}

    public static String redact(String message) {
        if (message == null || message.isEmpty()) return "";
        String m = message;
        m = BEARER.matcher(m).replaceAll("$1[REDACTED]");
        m = JWT.matcher(m).replaceAll("[REDACTED_JWT]");
        m = PASSWORD_KV.matcher(m).replaceAll("$1=[REDACTED]");
        String lower = m.toLowerCase(Locale.US);
        if (lower.contains("private key") || lower.contains("begin rsa") || lower.contains("begin private")) {
            return "[REDACTED_SENSITIVE]";
        }
        return m;
    }

    public static void log(Context c, String category, String message) {
        com.myplan.app.AppInfrastructure.log(c, category, redact(message));
    }
}
