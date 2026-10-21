package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** One operation checkpoint, never a table or archive of messages. */
final class RecommendationProgress {
    private final Path file;

    RecommendationProgress(Path root) {
        file = root.resolve("Data/mailer/recommendations.properties");
    }

    long load(String account, RecommendationSettings settings, long validity) throws MailException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (settings.initialUid() == null) {
                throw new MailException("First recommendation pass requires recommendations.startAfterUid");
            }
            return settings.initialUid();
        }
        var properties = new Properties();
        try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(input);
            if (!account.equals(properties.getProperty("account"))
                    || !settings.folder().equals(properties.getProperty("folder"))
                    || !settings.originalRecipient().equalsIgnoreCase(properties.getProperty("originalRecipient", ""))
                    || Long.parseLong(properties.getProperty("uidValidity", "")) != validity) {
                throw new MailException("Recommendation checkpoint scope or UIDVALIDITY changed; explicitly reinitialize the checkpoint");
            }
            long uid = Long.parseLong(properties.getProperty("lastUid", ""));
            if (uid < 0 || uid > 0xffffffffL) throw new NumberFormatException();
            return uid;
        } catch (IOException | IllegalArgumentException e) {
            if (e instanceof MailException mail) throw mail;
            throw new MailException("Cannot read recommendation checkpoint", e);
        }
    }

    void save(String account, RecommendationSettings settings, long validity, long uid) throws MailException {
        var properties = new Properties();
        properties.setProperty("account", account);
        properties.setProperty("folder", settings.folder());
        properties.setProperty("originalRecipient", settings.originalRecipient());
        properties.setProperty("uidValidity", Long.toString(validity));
        properties.setProperty("lastUid", Long.toString(uid));
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), ".recommendations-", ".tmp");
            try (var output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(output, "Last successful recommendation pass");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
        } catch (IOException e) {
            throw new MailException("Cannot save recommendation checkpoint", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Keep the original save error. */ }
            }
        }
    }
}
