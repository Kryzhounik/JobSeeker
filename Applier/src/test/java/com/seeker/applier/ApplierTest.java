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

}
