package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import com.seeker.mailer.MailMessage;
import com.seeker.mailer.MailMessageType;
import com.seeker.mailer.MailQuery;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/*
 * TODO: Validate LinkedIn recognition and URL extraction against real messages
 * after mailbox access works and the target message subset is agreed. All
 * LinkedIn messages below are invented; passing these tests only verifies
 * provisional assumptions, not compatibility with actual LinkedIn emails.
 */
class MessageInterpreterTest {
    private static final String FIRST = "https://www.linkedin.com/jobs/view/12345/";
    private static final String SECOND = "https://www.linkedin.com/jobs/view/67890/";
    @org.junit.jupiter.api.io.TempDir Path root;
    private MessageInterpreter interpreter;

    @org.junit.jupiter.api.BeforeEach
    void setupInterpreter() {
        interpreter = new MessageInterpreter(root);
    }

    // TODO: Verify the recommendation/link assertions with an actual LinkedIn HTML email.
    @Test
    void decodesHtmlHeadersAndBodyAndExcludesUnrelatedLinks() throws Exception {
        Instant receipt = Instant.parse("2026-10-21T09:02:00Z");
        try (var input = fixture("synthetic-html")) {
            MailMessage message = interpreter.interpret(input, "opaque-id", receipt);
            assertEquals("opaque-id", message.identifier());
            assertEquals(receipt, message.receivedAt());
            assertEquals("jobs-noreply@linkedin.com", message.sender());
            assertEquals("Jobs for you", message.subject());
            assertTrue(message.text().contains("Разработчик"));
            assertEquals(MailMessageType.LINKEDIN_RECOMMENDATION, message.type());
            assertEquals(List.of(FIRST, SECOND), message.vacancyUrls());
            assertThrows(UnsupportedOperationException.class, () -> message.vacancyUrls().add(FIRST));
        }
    }

    // TODO: Check the actual link representation in real plain-text recommendation emails.
    @Test
    void preservesPlainTextLinkOrderAndRemovesDuplicates() throws Exception {
        assertEquals(List.of(SECOND, FIRST), parseFixture("synthetic-plain").vacancyUrls());
    }

    // TODO: Verify the assumed redirect wrapper against a real recommendation email.
    @Test
    void decodesNestedMultipartBase64AndRedirectWithoutReadingAttachments() throws Exception {
        MailMessage message = parseFixture("synthetic-multipart");
        assertEquals(List.of(FIRST, SECOND), message.vacancyUrls());
        assertTrue(message.text().contains(FIRST));
        assertFalse(message.text().contains("99999"));
        assertFalse(message.text().contains("88888"));
    }

    @Test
    void decodesNonUtf8AndLeavesOtherMailUnclassified() throws Exception {
        MailMessage message = parseFixture("synthetic-latin");
        assertEquals("Café", message.subject());
        assertTrue(message.text().contains("Café and piñata"));
        assertEquals(MailMessageType.OTHER, message.type());
        assertTrue(message.vacancyUrls().isEmpty());
        assertNull(message.receivedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jobs@linkedin.com.evil.invalid", "jobs@evil-linkedin.com", "jobs@example.invalid"})
    void senderDisplayNameAndBodyDoNotEstablishLinkedInOrigin(String sender) throws Exception {
        MailMessage message = parse("From: LinkedIn <" + sender + ">\nSubject: Jobs for you\n\n" + FIRST);
        assertEquals(MailMessageType.OTHER, message.type());
        assertEquals(List.of(), message.vacancyUrls());
    }

    // TODO: Derive message-selection rules from real recommendation and non-recommendation emails.
    @Test
    void unrelatedLinkedInMailDoesNotBecomeRecommendationFromJobUrlAlone() throws Exception {
        var message = parse("From: jobs@linkedin.com\nSubject: Application received\n\n" + FIRST);
        assertEquals(MailMessageType.OTHER, message.type());
        assertTrue(message.vacancyUrls().isEmpty());
    }

    // TODO: Verify recognition and unsupported-format behavior using actual message formats.
    @Test
    void unsupportedRecommendationIsAnError() {
        assertThrows(MailException.class, () -> parseFixture("synthetic-unsupported"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.linkedin.com/jobs/search/?url=https%3A%2F%2Fwww.linkedin.com%2Fjobs%2Fview%2F12345%2F",
            "https://www.linkedin.com/e/unsubscribe?url=https%3A%2F%2Fwww.linkedin.com%2Fjobs%2Fview%2F12345%2F",
            "https://example.invalid/redir/redirect?url=https%3A%2F%2Fwww.linkedin.com%2Fjobs%2Fview%2F12345%2F",
            "https://www.linkedin.com/redir/redirect?url=%ZZ",
            "https://www.linkedin.com@evil.invalid/jobs/view/12345/",
            "https://www.linkedin.com/jobs/view/not-an-id/"
    })
    void unrelatedOrMalformedLinksAreNotDecodedAsRecommendations(String link) {
        assertThrows(MailException.class, () -> parse("From: jobs@linkedin.com\nSubject: Jobs for you\n\n" + link));
    }

    @Test
    void preservesDecodeFailureCauseWithoutIncludingMessageInError() {
        MailException error = assertThrows(MailException.class, () -> parse(
                "From: fixture@example.invalid\nContent-Type: text/plain; charset=unknown-fixture-charset\n\nprivate body"));
        assertNotNull(error.getCause());
        assertFalse(error.getMessage().contains("private body"));
    }

    @Test
    void preservesStreamFailureCauseAndDoesNotOwnInputStream() throws Exception {
        var failure = new IOException("fixture read failed");
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw failure; }
        };
        assertInstanceOf(jakarta.mail.MessagingException.class,
                assertThrows(MailException.class, () -> interpreter.interpret(broken, "id", null)).getCause());
        class TrackedInput extends ByteArrayInputStream {
            boolean closed;
            TrackedInput() { super("Subject: Other\n\ntext".getBytes(StandardCharsets.UTF_8)); }
            @Override public void close() { closed = true; }
        }
        var tracked = new TrackedInput();
        interpreter.interpret(tracked, "id", null);
        assertFalse(tracked.closed);
    }

    @Test
    void dtosCopyCollectionsAndDefineOmittedCriteria() {
        var urls = new ArrayList<>(List.of(FIRST));
        var message = new MailMessage("id", "", "", null, "", MailMessageType.OTHER, urls);
        urls.clear();
        assertEquals(List.of(FIRST), message.vacancyUrls());
        var query = new MailQuery(null, null);
        assertNull(query.sender());
        assertNull(query.receivedAfter());
        assertThrows(IllegalArgumentException.class, () -> new MailQuery("  ", null));
    }

    private InputStream fixture(String name) {
        return java.util.Objects.requireNonNull(getClass().getResourceAsStream("/mail/" + name + ".eml"));
    }

    private MailMessage parseFixture(String name) throws Exception {
        try (var input = fixture(name)) {
            return interpreter.interpret(input, "fixture", null);
        }
    }

    private MailMessage parse(String raw) throws MailException {
        return interpreter.interpret(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)), "fixture", null);
    }
}
