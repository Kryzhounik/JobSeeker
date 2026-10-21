package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Generic MIME traversal shared with the provisional interpreter. No sender rules. */
final class MimeBodyDecoder {
    record TextPart(boolean html, String text) {}

    static List<TextPart> decode(Part message) throws IOException, MessagingException {
        var result = new ArrayList<TextPart>();
        visit(message, result, 0);
        return List.copyOf(result);
    }

    private static void visit(Part part, List<TextPart> result, int depth) throws IOException, MessagingException {
        if (depth > 32) {
            throw new MailException("MIME nesting exceeds supported depth");
        }
        String disposition = part.getDisposition();
        // An explicitly inline body may legitimately carry a suggested filename.
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (!Part.INLINE.equalsIgnoreCase(disposition) && part.getFileName() != null)) {
            return;
        }
        if (part.isMimeType("multipart/*")) {
            if (!(part.getContent() instanceof Multipart multipart)) {
                throw new MailException("Invalid MIME multipart body");
            }
            for (int i = 0; i < multipart.getCount(); i++) {
                visit(multipart.getBodyPart(i), result, depth + 1);
            }
        } else if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            if (!(part.getContent() instanceof String value)) {
                throw new MailException("Invalid MIME text body");
            }
            result.add(new TextPart(part.isMimeType("text/html"), value));
        }
        // Binary attachments and encapsulated messages are retained by raw export,
        // but are not body alternatives of the selected message.
    }
}
