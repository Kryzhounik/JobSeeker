package com.seeker.collector.linkedin.support;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Mirrors process stdout and stderr into one per-launch log file. */
public final class ProcessLog implements AutoCloseable {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final PrintStream consoleOut;
    private final PrintStream consoleErr;
    private final PrintStream fileLog;
    private final Path path;

    private ProcessLog(
            PrintStream consoleOut,
            PrintStream consoleErr,
            PrintStream fileLog,
            Path path
    ) {
        this.consoleOut = consoleOut;
        this.consoleErr = consoleErr;
        this.fileLog = fileLog;
        this.path = path;
    }

    public static ProcessLog install(Path projectRoot) throws IOException {
        Path directory = projectRoot.toAbsolutePath().normalize()
                .resolve("Data/logs/linkedin");
        Files.createDirectories(directory);
        Path path = directory.resolve(
                LOG_TIME.format(Instant.now())
                        + "-" + ProcessHandle.current().pid() + ".log"
        );
        PrintStream fileLog = new PrintStream(
                new BufferedOutputStream(Files.newOutputStream(
                        path,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE
                )),
                true,
                StandardCharsets.UTF_8
        );
        PrintStream consoleOut = System.out;
        PrintStream consoleErr = System.err;
        Object writeLock = new Object();
        System.setOut(tee(consoleOut, fileLog, writeLock));
        System.setErr(tee(consoleErr, fileLog, writeLock));
        return new ProcessLog(consoleOut, consoleErr, fileLog, path);
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        System.out.flush();
        System.err.flush();
        System.setOut(consoleOut);
        System.setErr(consoleErr);
        fileLog.close();
    }

    private static PrintStream tee(
            PrintStream console,
            PrintStream file,
            Object writeLock
    ) {
        return new PrintStream(
                new TeeOutputStream(console, file, writeLock),
                true,
                StandardCharsets.UTF_8
        );
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream console;
        private final OutputStream file;
        private final Object writeLock;

        private TeeOutputStream(
                OutputStream console,
                OutputStream file,
                Object writeLock
        ) {
            this.console = console;
            this.file = file;
            this.writeLock = writeLock;
        }

        @Override
        public void write(int value) throws IOException {
            synchronized (writeLock) {
                console.write(value);
                file.write(value);
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            synchronized (writeLock) {
                console.write(bytes, offset, length);
                file.write(bytes, offset, length);
            }
        }

        @Override
        public void flush() throws IOException {
            synchronized (writeLock) {
                console.flush();
                file.flush();
            }
        }
    }
}
