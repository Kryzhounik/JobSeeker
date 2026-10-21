package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import java.nio.file.Path;

record RecommendationSettings(String folder, String originalRecipient, Long initialUid) {
    static RecommendationSettings load(Path root) throws MailException {
        var properties = MailSettings.readProperties(root);
        String folder = MailSettings.required(properties, "recommendations.folder");
        if (folder.indexOf('\r') >= 0 || folder.indexOf('\n') >= 0 || folder.indexOf('\0') >= 0) {
            throw new MailException("Invalid recommendations.folder");
        }
        String recipient = MailSettings.required(properties, "recommendations.originalRecipient");
        if (!recipient.matches("[^\\s@]+@[^\\s@]+")) {
            throw new MailException("recommendations.originalRecipient must be a complete email address");
        }
        String rawUid = properties.getProperty("recommendations.startAfterUid", "").strip();
        Long initial = null;
        if (!rawUid.isEmpty()) {
            try {
                initial = Long.parseLong(rawUid);
                if (initial < 0 || initial > 0xffffffffL) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw new MailException("Invalid recommendations.startAfterUid");
            }
        }
        return new RecommendationSettings(folder, recipient, initial);
    }
}
