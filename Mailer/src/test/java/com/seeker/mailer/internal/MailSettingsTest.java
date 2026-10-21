package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import com.seeker.mailer.Mailer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MailSettingsTest {
    @TempDir Path root;
    static final String CONFIG = """
            imap.host=imap.gmail.com
            imap.port=993
            imap.username=fixture@example.invalid
            imap.appPassword=abcd efgh ijkl mnop
            """;

    static void writeConfig(Path root, String content) throws Exception {
        Files.createDirectories(root.resolve("Data/mailer"));
        Files.writeString(root.resolve("Data/mailer/config.properties"), content);
    }

    @Test
    void constructionDoesNotLoadSettingsOrCreateState() {
        assertDoesNotThrow(() -> new Mailer(root));
        assertFalse(Files.exists(root.resolve("Data")));
    }

    @Test
    void missingSettingsFailAtOperationTimeWithoutCreatingData() {
        MailException error = assertThrows(MailException.class, () -> new Mailer(root).listFolders());
        assertNotNull(error.getCause());
        assertFalse(Files.exists(root.resolve("Data")));
    }

    @Test
    void loadsGmailSettingsAndRequiresVerifiedTlsWithBoundedTimeouts() throws Exception {
        writeConfig(root, CONFIG);
        MailSettings settings = MailSettings.load(root);
        assertEquals("imap.gmail.com", settings.host);
        assertEquals(993, settings.port);
        assertEquals("abcdefghijklmnop", settings.appPassword);
        assertEquals(15000, settings.timeoutMillis);
        var props = settings.sessionProperties();
        assertEquals("imaps", props.getProperty("mail.store.protocol"));
        assertEquals("true", props.getProperty("mail.imaps.ssl.enable"));
        assertEquals("true", props.getProperty("mail.imaps.ssl.checkserveridentity"));
        assertEquals("PLAIN LOGIN", props.getProperty("mail.imaps.auth.mechanisms"));
        assertEquals("true", props.getProperty("mail.imaps.peek"));
        assertEquals("false", props.getProperty("mail.debug.auth"));
        for (String key : new String[]{"connectiontimeout", "timeout", "writetimeout"}) {
            assertEquals("15000", props.getProperty("mail.imaps." + key));
        }
        assertFalse(props.toString().contains(settings.appPassword));
        assertFalse(settings.toString().contains(settings.appPassword));
    }

    @ParameterizedTest
    @ValueSource(strings = {"imap.host", "imap.port", "imap.username", "imap.appPassword"})
    void requiredSettingsAreValidatedBeforeCreatingAStore(String key) throws Exception {
        writeConfig(root, CONFIG.replaceAll("(?m)^" + java.util.regex.Pattern.quote(key) + "=.*$", key + "="));
        var access = new ImapMailAccess(root, session -> {
            fail("Must validate settings before creating a store");
            return null;
        });
        assertThrows(MailException.class, access::listFolders);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "imap.port=0", "imap.port=65536", "imap.port=secret-value", "imap.timeoutMillis=0",
            "imap.timeoutMillis=300001", "imap.host=evil.invalid", "imap.username=invalid",
            "imap.appPassword=secret-value", "mail.debug=true", "accidentally-pasted-secret="
    })
    void rejectsBadValuesAndUnknownKeysWithoutEchoingThem(String setting) throws Exception {
        // Properties use the last occurrence of a key.
        writeConfig(root, CONFIG + setting + "\n");
        var error = assertThrows(MailException.class, () -> MailSettings.load(root));
        assertFalse(error.getMessage().contains("secret-value"));
        assertFalse(error.getMessage().contains("accidentally-pasted-secret"));
    }

    @Test
    void reloadsConfigAtEachOperationAndReportsMalformedProperties() throws Exception {
        writeConfig(root, CONFIG + "imap.timeoutMillis=20000\n");
        assertEquals(20000, MailSettings.load(root).timeoutMillis);
        writeConfig(root, "imap.username=\\uQQQQ");
        assertNotNull(assertThrows(MailException.class, () -> MailSettings.load(root)).getCause());
    }
}
