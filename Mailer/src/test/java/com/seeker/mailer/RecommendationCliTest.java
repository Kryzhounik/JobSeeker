package com.seeker.mailer;

import com.google.gson.JsonParser;
import com.seeker.tools.cli.CliRunner;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationCliTest {
    private final Mailer api = mock(Mailer.class);
    private final StringWriter output = new StringWriter();
    private final StringWriter errors = new StringWriter();

    private int run(String... args) {
        return CliRunner.run(new MailerAdapter(api), new PrintWriter(output, true), new PrintWriter(errors, true), args);
    }

    @Test
    void delegatesOnceAndPrintsOnlyApiUrlArray() throws MailException {
        when(api.getRecommendationUrls()).thenReturn(List.of("https://www.linkedin.com/jobs/view/12345/"));
        assertEquals(0, run("recommendation-urls"));
        var json = JsonParser.parseString(output.toString()).getAsJsonArray();
        assertEquals("https://www.linkedin.com/jobs/view/12345/", json.get(0).getAsString());
        verify(api).getRecommendationUrls();
        verifyNoMoreInteractions(api);
        assertEquals("", errors.toString());
    }

    @Test
    void emptyResultIsSuccessfulAndExtraArgumentsNeverCallApi() throws MailException {
        when(api.getRecommendationUrls()).thenReturn(List.of());
        assertEquals(0, run("recommendation-urls"));
        assertEquals("[]", output.toString().strip());
        clearInvocations(api);
        assertEquals(2, run("recommendation-urls", "extra"));
        verifyNoInteractions(api);
    }

    @Test
    void failureDoesNotPrintPartialJsonOrProviderCause() throws MailException {
        when(api.getRecommendationUrls()).thenThrow(new MailException("Recommendation pass failed", new Exception("private")));
        assertEquals(1, run("recommendation-urls"));
        assertEquals("", output.toString());
        assertEquals("Recommendation pass failed", errors.toString().strip());
    }

    @Test
    void helpDoesNotConsumeRecommendations() {
        assertEquals(0, run("recommendation-urls", "--help"));
        verifyNoInteractions(api);
        assertTrue(output.toString().contains("Usage:"));
    }
}
