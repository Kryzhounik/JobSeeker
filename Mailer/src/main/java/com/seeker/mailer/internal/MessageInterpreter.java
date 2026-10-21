package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import com.seeker.mailer.MailMessage;
import com.seeker.mailer.MailMessageType;
import jakarta.mail.Address;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;

/** Internal offline decoder, shared by future mail access and recommendation operations. */
public final class MessageInterpreter {
    private static final Pattern TEXT_URL = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    private final MailLog log;

    public MessageInterpreter() {
        this(Path.of(""));
    }

    public MessageInterpreter(Path projectRoot) {
        log = new MailLog(Objects.requireNonNull(projectRoot, "projectRoot"));
    }

    /**
     * The caller owns the stream and supplies an opaque identifier and receipt time.
     * The Date header is a sender timestamp and is not substituted for receipt time.
     */
    public MailMessage interpret(InputStream source, String identifier, Instant receivedAt) throws MailException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(identifier, "identifier");
        String subject = null;
        try {
            log.write("PARSE_START", identifier, null, null, "Reading message for interpretation");
            var message = new MimeMessage(Session.getInstance(new Properties()), source);
            Address[] from = message.getFrom();
            String sender = "";
            if (from != null && from.length == 1 && from[0] instanceof InternetAddress address) {
                sender = address.getAddress();
            }
            subject = Objects.requireNonNullElse(message.getSubject(), "");
            var body = new Body();
            decode(message, body);
            String text = String.join("\n", body.text);
            boolean recommendation = LinkedInRecommendations.recognizes(sender, subject, text);
            List<String> urls = recommendation ? LinkedInRecommendations.extract(body.links) : List.of();
            log.write(recommendation ? "EXTRACTED" : "FILTERED", identifier, subject, null,
                    recommendation ? "Vacancy URLs: " + urls : "Provisional sender/content recognition did not match");
            return new MailMessage(identifier, sender, subject, receivedAt, text,
                    recommendation ? MailMessageType.LINKEDIN_RECOMMENDATION : MailMessageType.OTHER,
                    urls);
        } catch (MailException e) {
            log.failure("PARSE_ERROR", identifier, subject, null, e);
            throw e;
        } catch (jakarta.mail.MessagingException | IOException | RuntimeException e) {
            var failure = new MailException("Cannot decode email message", e);
            log.failure("PARSE_ERROR", identifier, subject, null, failure);
            throw failure;
        }
    }

    private static void decode(Part part, Body body) throws jakarta.mail.MessagingException, IOException {
        for (var decoded : MimeBodyDecoder.decode(part)) {
            String value = decoded.text();
            if (decoded.html()) {
                var document = Jsoup.parse(value);
                document.select("script, style").remove();
                body.text.add(document.text());
                // Walk DOM order so visible URLs and hrefs have a reproducible content order.
                document.traverse((node, nodeDepth) -> {
                    if (node instanceof org.jsoup.nodes.Element element && element.normalName().equals("a")) {
                        body.links.add(element.attr("href"));
                    } else if (node instanceof org.jsoup.nodes.TextNode textNode) {
                        addTextUrls(textNode.getWholeText(), body.links);
                    }
                });
            } else {
                body.text.add(value);
                addTextUrls(value, body.links);
            }
        }
        // Binary parts and encapsulated messages are not this message's recommendation content.
    }

    private static void addTextUrls(String text, List<String> links) {
        var matcher = TEXT_URL.matcher(text);
        while (matcher.find()) {
            links.add(matcher.group().replaceFirst("[.,;!?)\\]]+$", ""));
        }
    }

    private static final class Body {
        final List<String> text = new ArrayList<>();
        final List<String> links = new ArrayList<>();
    }
}
