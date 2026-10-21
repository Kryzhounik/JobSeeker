package com.seeker.mailer.internal;

import com.google.gson.Gson;
import com.seeker.mailer.MailException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationParserTest {
    static MimeMessage fixture(String name) throws Exception {
        try (var input = RecommendationParserTest.class.getResourceAsStream("/mail/recommendations/" + name + ".eml")) {
            return new MimeMessage(Session.getInstance(new Properties()), java.util.Objects.requireNonNull(input));
        }
    }

    static List<String> expected(String name) throws Exception {
        try (var input = RecommendationParserTest.class.getResourceAsStream("/mail/recommendations/" + name + "-ids.json")) {
            String[] ids = new Gson().fromJson(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), String[].class);
            return Arrays.stream(ids).map(id -> "https://www.linkedin.com/jobs/view/" + id + "/").toList();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"recommendation-439", "turnitin-437", "luxoft-438", "intellias-432"})
    void extractsExactOrderedCardsFromSanitizedRealStructures(String name) throws Exception {
        var message = fixture(name);
        var settings = new RecommendationSettings("INBOX", "original@example.invalid", 425L);
        assertNull(RecommendationParser.rejection(message, settings, "fixture@example.invalid"));
        assertEquals(expected(name), RecommendationParser.extract(message, RecommendationParser.header(message, "X-LinkedIn-Template")));
    }

    @Test
    void excludesReferenceVacancyAndFooterLinks() throws Exception {
        var message = fixture("recommendation-439");
        var urls = RecommendationParser.extract(message, RecommendationParser.REMINDER);
        assertEquals(10, urls.size());
        assertFalse(urls.contains("https://www.linkedin.com/jobs/view/4479296621/"));
    }

    @Test
    void rejectsBrokenCardAndEmptyKnownFormatInsteadOfReturningEmptyUrls() throws Exception {
        var message = fixture("luxoft-438");
        message.setText("No HTML cards", "UTF-8");
        assertThrows(MailException.class, () -> RecommendationParser.extract(message, RecommendationParser.DIGEST));
        message.setContent("<a class='font-bold text-md leading-regular text-system-blue-50' href='https://evil.invalid/jobs/view/123/'>Broken</a>", "text/html; charset=UTF-8");
        assertThrows(MailException.class, () -> RecommendationParser.extract(message, RecommendationParser.DIGEST));
    }
}
