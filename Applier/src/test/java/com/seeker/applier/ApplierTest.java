package com.seeker.applier;

import com.seeker.applier.coverletter.CoverLetterWriter;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplierTest {
    @Test
    void apiDelegatesToItsServices() throws Exception {
        var runner = mock(ApplierRunner.class);
        var writer = mock(CoverLetterWriter.class);
        var api = new ApplierApi(runner, writer);
        var prepared = new ApplierRunner.Result("skipped", "Easy Apply", false);
        Path file = Path.of("letter.txt");
        when(runner.run("https://example.com/job")).thenReturn(prepared);
        when(writer.generate(42)).thenReturn("Letter");
        when(writer.generate(42, file)).thenReturn(file);
        assertSame(prepared, api.prepare("https://example.com/job"));
        assertEquals("Letter", api.coverLetter(42));
        assertEquals(file, api.coverLetter(42, file));
        verify(runner).run("https://example.com/job");
        verify(writer).generate(42);
        verify(writer).generate(42, file);
        verifyNoMoreInteractions(runner, writer);
    }

    @Test
    void cliReturnsTextAndDelegatesFileOutput() throws Exception {
        var api = mock(ApplierApi.class);
        var cli = new ApplierCli(api);
        Path file = Path.of("letter with spaces.txt");
        when(api.coverLetter(42)).thenReturn("Saved letter");
        when(api.coverLetter(42, file)).thenReturn(file.toAbsolutePath());
        assertEquals("Saved letter", cli.handle(new String[]{"cover-letter", "42"}));
        assertEquals(file.toAbsolutePath().toString(), cli.handle(new String[]{"cover-letter", "42", file.toString()}));
        verify(api).coverLetter(42);
        verify(api).coverLetter(42, file);
        verifyNoMoreInteractions(api);
    }

    @Test
    void cliKeepsVacancyUrlRoute() throws Exception {
        var api = mock(ApplierApi.class);
        String url = "https://example.com/jobs/123";
        when(api.prepare(url)).thenReturn(new ApplierRunner.Result("filled", "Review", false));
        assertEquals("{\"status\":\"filled\",\"note\":\"Review\",\"submissionObserved\":false}", new ApplierCli(api).handle(new String[]{url}));
        verify(api).prepare(url);
    }

    @Test
    void cliRejectsMalformedCommandsBeforeCallingApi() {
        var api = mock(ApplierApi.class);
        var cli = new ApplierCli(api);
        for (String[] args : new String[][]{{}, {"cover-letter"}, {"cover-letter", "x"}, {"cover-letter", "42", "a", "b"}, {"file:///tmp/job"}, {"bad url"}})
            assertThrows(IllegalArgumentException.class, () -> cli.handle(args));
        verifyNoInteractions(api);
    }
}
