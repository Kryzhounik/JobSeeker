package com.seeker.mailer;

import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationCliTest {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    private int run(String[] args, MailerCli.RecommendationSource source) {
        return MailerCli.run(args, new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                () -> { fail("Must not list folders"); return null; },
                folder -> { fail("Must not list messages"); return null; },
                (folder, id) -> { fail("Must not read individual messages"); return null; },
                (folder, id, path) -> { fail("Must not export"); return null; }, source);
    }

    @Test
    void delegatesOnceAndPrintsOnlyCommonApiUrlArray() {
        var calls = new AtomicInteger();
        assertEquals(0, run(new String[]{"recommendation-urls"}, () -> {
            calls.incrementAndGet();
            return List.of("https://www.linkedin.com/jobs/view/12345/");
        }));
        assertEquals(1, calls.get());
        var json = JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonArray();
        assertEquals("https://www.linkedin.com/jobs/view/12345/", json.get(0).getAsString());
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void emptyOutputIsSuccessfulAndExtraArgumentsNeverCallApi() {
        assertEquals(0, run(new String[]{"recommendation-urls"}, List::of));
        assertEquals("[]", output.toString(StandardCharsets.UTF_8).strip());
        assertEquals(2, run(new String[]{"recommendation-urls", "extra"}, () -> { fail("Invalid syntax must not call mail"); return null; }));
    }

    @Test
    void failurePropagatesWithoutPartialJsonOrProviderSecret() {
        assertEquals(1, run(new String[]{"recommendation-urls"}, () -> {
            throw new MailException("Recommendation pass failed", new Exception("private provider response"));
        }));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertEquals("Recommendation pass failed", errors.toString(StandardCharsets.UTF_8).strip());
    }
}
