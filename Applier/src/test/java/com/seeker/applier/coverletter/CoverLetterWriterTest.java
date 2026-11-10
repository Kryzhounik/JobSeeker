package com.seeker.applier.coverletter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoverLetterWriterTest {
    @TempDir Path root;
    private CoverLetterWriter writer;
    private Path database;

    @BeforeEach
    void setup() throws Exception {
        Path runtime = root.resolve("Driver/collector/java_linkedin/runtime.properties");
        Files.createDirectories(runtime.getParent());
        Files.writeString(runtime, "python.executable=fixture-python\ndatabase.path=Data/custom jobs.sqlite\n");
        database = root.resolve("Data/custom jobs.sqlite");
        writer = spy(new CoverLetterWriter(root));
        doNothing().when(writer).saveLetter(anyString(), any(), anyLong(), anyString());
    }

    private void lookup(String json) throws Exception {
        doReturn(json.getBytes(StandardCharsets.UTF_8)).when(writer).loadContext("fixture-python", database, 42);
    }

    @Test
    void returnsSavedLetterWithoutGenerationOrStorage() throws Exception {
        lookup("{\"letter\":\"Saved letter\"}");
        assertEquals("Saved letter", writer.generate(42));
        verify(writer, never()).invokeCodex(anyList());
        verify(writer, never()).saveLetter(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void writesSavedLetterAsUtf8WithoutModel() throws Exception {
        lookup("{\"letter\":\"Letter — спасибо\"}");
        Path output = writer.generate(42, Path.of("letter with spaces.txt"));
        assertEquals(root.resolve("letter with spaces.txt"), output);
        assertArrayEquals("Letter — спасибо".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(output));
        verify(writer, never()).invokeCodex(anyList());
    }

    @Test
    void passesFullContextAndConfiguredDatabaseAndSavesNewLetter() throws Exception {
        String vacancy = "{\"job_title\":\"Engineer\",\"company\":\"Company\",\"job_description\":\"Full description\"}";
        lookup(vacancy);
        AtomicReference<Path> input = new AtomicReference<>();
        doAnswer(call -> {
            List<String> command = call.getArgument(0);
            assertEquals(database.toString(), command.get(command.indexOf("--db") + 1));
            assertEquals("42", command.get(command.indexOf("--target") + 1));
            assertEquals("xhigh", command.get(command.indexOf("--reasoning-effort") + 1));
            assertEquals(root.resolve("Applier/prompts/cover_letter_prompt.md").toString(), command.get(command.indexOf("--instruction") + 1));
            var contexts = java.util.stream.IntStream.range(0, command.size() - 1)
                    .filter(i -> command.get(i).equals("--context")).mapToObj(i -> command.get(i + 1)).toList();
            assertEquals(List.of(root.resolve("Data/Profile/resume.txt").toString(), root.resolve("Data/Profile/Applier/candidate_context.md").toString()), contexts);
            assertFalse(command.contains("--allow-browser-tools"));
            assertFalse(command.contains("--model"));
            input.set(Path.of(command.get(command.indexOf("--input") + 1)));
            assertEquals(vacancy, Files.readString(input.get()));
            return "{\"letter\":\"New letter\"}";
        }).when(writer).invokeCodex(anyList());
        assertEquals("New letter", writer.generate(42));
        verify(writer).saveLetter("fixture-python", database, 42, "New letter");
        assertFalse(Files.exists(input.get()));
    }

    @Test
    void rejectsInvalidIdBeforeExternalCalls() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> writer.generate(0));
        verify(writer, never()).loadContext(anyString(), any(), anyLong());
    }

    @Test
    void doesNotSaveInvalidModelOutput() throws Exception {
        lookup("{\"job_description\":\"Description\"}");
        doReturn("{\"letter\":\"  \"}").when(writer).invokeCodex(anyList());
        assertThrows(IllegalStateException.class, () -> writer.generate(42));
        verify(writer, never()).saveLetter(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void reportsStorageFailureAndCleansTemporaryInput() throws Exception {
        lookup("{\"job_description\":\"Description\"}");
        AtomicReference<Path> input = new AtomicReference<>();
        doAnswer(call -> {
            List<String> command = call.getArgument(0);
            input.set(Path.of(command.get(command.indexOf("--input") + 1)));
            return "{\"letter\":\"New letter\"}";
        }).when(writer).invokeCodex(anyList());
        doThrow(new IOException("Storage failed")).when(writer).saveLetter("fixture-python", database, 42, "New letter");
        assertThrows(IOException.class, () -> writer.generate(42));
        assertFalse(Files.exists(input.get()));
    }
}
