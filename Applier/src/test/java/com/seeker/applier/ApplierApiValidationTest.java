package com.seeker.applier;

import com.seeker.applier.coverletter.CoverLetterWriter;
import com.seeker.tools.cli.CliRunner;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplierApiValidationTest {
    private static final String[] INVALID_URLS = {
            null,
            "",
            "   ",
            "http://[invalid",
            "file:///tmp/job",
            "ftp://example.com/job"
    };

    @Test
    void prepareRejectsInvalidUrlsBeforeCallingRunner() throws Exception {
        var runner = mock(ApplierRunner.class);
        var api = new ApplierApi(runner, new CoverLetterWriter(Path.of("unused")));

        for (String url : INVALID_URLS) {
            assertThrows(IllegalArgumentException.class, () -> api.prepare(url), String.valueOf(url));
        }

        verifyNoInteractions(runner);
    }

    @Test
    void coverLetterRejectsNonPositiveIdsBeforeLoadingConfiguration(@TempDir Path root) {
        var runner = mock(ApplierRunner.class);
        var api = new ApplierApi(runner, new CoverLetterWriter(root));

        assertThrows(IllegalArgumentException.class, () -> api.coverLetter(0));
        assertThrows(IllegalArgumentException.class, () -> api.coverLetter(-1));
        assertThrows(IllegalArgumentException.class, () -> api.coverLetter(0, Path.of("letter.txt")));

        verifyNoInteractions(runner);
    }

    @Test
    void cliReturnsOperationFailureForInvalidUrlsAndIds(@TempDir Path root) {
        var runner = mock(ApplierRunner.class);
        var adapter = new ApplierAdapter(new ApplierApi(runner, new CoverLetterWriter(root)));

        for (String url : INVALID_URLS) {
            assertCliOperationFailure(adapter, "prepare", String.valueOf(url));
        }
        assertCliOperationFailure(adapter, "cover-letter", "0");
        assertCliOperationFailure(adapter, "cover-letter", "-1");

        verifyNoInteractions(runner);
    }

    private static void assertCliOperationFailure(ApplierAdapter adapter, String... args) {
        var output = new Output();

        assertEquals(1, CliRunner.run(adapter, output.out(), output.err(), args));
        assertEquals("", output.stdout());
        assertFalse(output.stderr().isBlank());
    }

    private static final class Output {
        private final StringWriter stdout = new StringWriter();
        private final StringWriter stderr = new StringWriter();

        PrintWriter out() { return new PrintWriter(stdout, true); }
        PrintWriter err() { return new PrintWriter(stderr, true); }
        String stdout() { return stdout.toString(); }
        String stderr() { return stderr.toString(); }
    }
}
