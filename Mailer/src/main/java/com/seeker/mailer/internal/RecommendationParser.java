package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Rules verified against the four user-selected real LinkedIn messages. */
final class RecommendationParser {
    static final String REMINDER = "email_jobs_viewed_job_reminder_01";
    static final String FACETS = "email_jobs_facet_suggestions";
    static final String DIGEST = "email_job_alert_digest_01";
    private static final Map<String, String> SENDERS = Map.of(
            REMINDER, "jobs-noreply@linkedin.com", FACETS, "jobs-noreply@linkedin.com",
            DIGEST, "jobalerts-noreply@linkedin.com");

    static String header(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        return values != null && values.length == 1 ? values[0].strip() : "";
    }

    static String rejection(Message message, RecommendationSettings settings, String mailbox) throws MessagingException {
        String template = header(message, "X-LinkedIn-Template");
        if (!SENDERS.containsKey(template)) return "Unknown or excluded X-LinkedIn-Template";
        Address[] from = message.getFrom();
        if (from == null || from.length != 1 || !isAddress(from[0], SENDERS.get(template))) return "Sender does not match template";
        Address[] to = message.getRecipients(Message.RecipientType.TO);
        if (to == null || to.length != 1 || !isAddress(to[0], settings.originalRecipient())) return "Original To recipient does not match";
        String forwardedTo = header(message, "X-Forwarded-To");
        String forwardedFor = header(message, "X-Forwarded-For");
        if (!forwardedTo.equalsIgnoreCase(mailbox)) return "X-Forwarded-To does not match mailbox";
        var addresses = Arrays.asList(forwardedFor.toLowerCase(Locale.ROOT).split("\\s+"));
        if (!addresses.contains(settings.originalRecipient().toLowerCase(Locale.ROOT))
                || !addresses.contains(mailbox.toLowerCase(Locale.ROOT))) return "X-Forwarded-For does not match delivery scope";
        return null;
    }

    private static boolean isAddress(Address actual, String expected) {
        return actual instanceof InternetAddress internet && expected.equalsIgnoreCase(internet.getAddress());
    }

    static List<String> extract(Message message, String template) throws MessagingException, IOException {
        var urls = new LinkedHashSet<String>();
        int htmlParts = 0;
        for (var part : MimeBodyDecoder.decode(message)) {
            if (!part.html()) continue;
            htmlParts++;
            var doc = Jsoup.parse(part.text());
            doc.select("script, style").remove();
            List<Element> cards = switch (template) {
                case REMINDER -> doc.select("a.text-md.leading-regular.text-color-brand[href]");
                case FACETS -> doc.select("div.font-bold.text-md.leading-regular.text-system-blue-50");
                case DIGEST -> doc.select("a.font-bold.text-md.leading-regular.text-system-blue-50[href]");
                default -> throw new MailException("Unsupported recommendation template");
            };
            if (cards.isEmpty()) throw new MailException("Known recommendation template has no recognizable vacancy cards");
            for (Element card : cards) {
                Element link = template.equals(FACETS) ? card.closest("a[href]") : card;
                String canonical = link == null ? null : LinkedInRecommendations.normalize(link.attr("href"));
                if (canonical == null) throw new MailException("Recommendation card has no supported LinkedIn vacancy URL");
                urls.add(canonical);
            }
        }
        if (htmlParts == 0 || urls.isEmpty()) throw new MailException("Known recommendation template has no supported HTML body");
        return List.copyOf(urls);
    }
}
