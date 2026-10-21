package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/** Credentials are intentionally absent from toString and diagnostics. */
final class MailSettings {
    private static final Set<String> KEYS = Set.of("imap.host", "imap.port", "imap.username",
            "imap.appPassword", "imap.timeoutMillis");
    final String host;
    final int port;
    final String username;
    final String appPassword;
    final int timeoutMillis;

    private MailSettings(String host, int port, String username, String appPassword, int timeoutMillis) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.appPassword = appPassword;
        this.timeoutMillis = timeoutMillis;
    }

    static MailSettings load(Path projectRoot) throws MailException {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(projectRoot.resolve("Data/mailer/config.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            throw new MailException("Cannot read Data/mailer/config.properties", e);
        }
        if (!KEYS.containsAll(properties.stringPropertyNames())) {
            // Do not echo unknown keys: a misplaced secret may have become a key.
            throw new MailException("Unknown setting in Data/mailer/config.properties");
        }
        String host = required(properties, "imap.host");
        // This implementation is scoped to the Gmail account selected by the user.
        if (!host.equalsIgnoreCase("imap.gmail.com")) {
            throw new MailException("imap.host must be imap.gmail.com for Gmail app-password access");
        }
        int port = integer(properties, "imap.port", null, 1, 65535);
        String username = required(properties, "imap.username");
        if (!username.matches("[^\\s@]+@[^\\s@]+")) {
            throw new MailException("imap.username must be a complete email address");
        }
        String password = required(properties, "imap.appPassword").replace(" ", "");
        if (!password.matches("[A-Za-z0-9]{16}")) {
            throw new MailException("imap.appPassword must contain the 16-character Google app password");
        }
        int timeout = integer(properties, "imap.timeoutMillis", "15000", 1, 300000);
        return new MailSettings(host, port, username, password, timeout);
    }

    private static String required(Properties properties, String key) throws MailException {
        String value = properties.getProperty(key, "").strip();
        if (value.isEmpty()) {
            throw new MailException("Missing setting: " + key);
        }
        return value;
    }

    private static int integer(Properties properties, String key, String fallback, int min, int max) throws MailException {
        String raw = properties.getProperty(key, fallback);
        if (raw == null || raw.isBlank()) {
            throw new MailException("Missing setting: " + key);
        }
        try {
            int value = Integer.parseInt(raw.strip());
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // The value itself is deliberately not included in diagnostics.
        }
        throw new MailException("Invalid integer setting: " + key);
    }

    Properties sessionProperties() {
        var properties = new Properties();
        properties.setProperty("mail.store.protocol", "imaps");
        properties.setProperty("mail.imaps.ssl.enable", "true");
        properties.setProperty("mail.imaps.ssl.checkserveridentity", "true");
        properties.setProperty("mail.imaps.ssl.protocols", "TLSv1.3 TLSv1.2");
        properties.setProperty("mail.imaps.auth.mechanisms", "PLAIN LOGIN");
        properties.setProperty("mail.imaps.sasl.enable", "false");
        properties.setProperty("mail.imaps.peek", "true");
        properties.setProperty("mail.debug", "false");
        properties.setProperty("mail.debug.auth", "false");
        properties.setProperty("mail.imaps.connectiontimeout", Integer.toString(timeoutMillis));
        properties.setProperty("mail.imaps.timeout", Integer.toString(timeoutMillis));
        properties.setProperty("mail.imaps.writetimeout", Integer.toString(timeoutMillis));
        return properties;
    }
}
