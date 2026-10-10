package com.seeker.applier.shared;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Connects clients to one visible Chromium process that outlives those clients. */
public final class ManagedBrowser implements AutoCloseable {
    public static final String CDP_ENDPOINT = "http://127.0.0.1:9222";
    private static final String VERSION_ENDPOINT = CDP_ENDPOINT + "/json/version";
    private static final Duration START_TIMEOUT = Duration.ofSeconds(90);

    private final Playwright playwright;
    private final BrowserContext context;
    private final Set<Page> ownedPages = ConcurrentHashMap.newKeySet();

    private ManagedBrowser(Playwright playwright, BrowserContext context) {
        this.playwright = playwright;
        this.context = context;
        context.onPage(page -> {
            Page opener = page.opener();
            if (opener != null && ownedPages.contains(opener)) ownedPages.add(page);
        });
    }

    public static ManagedBrowser connect(Path profileDirectory, String browserChannel)
            throws IOException {
        Path profile = profileDirectory.toAbsolutePath().normalize();
        Files.createDirectories(profile);
        if (!isManagedBrowserRunning()) {
            startBrowser(profile, browserChannel);
        }
        awaitBrowser();
        Playwright playwright = Playwright.create();
        try {
            var browser = playwright.chromium().connectOverCDP(CDP_ENDPOINT);
            BrowserContext context = browser.contexts().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Managed browser has no default context"));
            return new ManagedBrowser(playwright, context);
        } catch (RuntimeException error) {
            playwright.close();
            throw new IOException("Could not attach to managed browser at "
                    + CDP_ENDPOINT + ": " + error.getMessage(), error);
        }
    }

    public BrowserContext context() {
        return context;
    }

    public Page newPage() {
        Page page = context.newPage();
        ownedPages.add(page);
        return page;
    }

    /** Leaves a page open after this client disconnects. */
    public void keepOpen(Page page) {
        ownedPages.remove(page);
    }

    public int tabIndex(Page page) throws IOException {
        int index = context.pages().indexOf(page);
        if (index < 0) throw new IOException("Application tab is no longer open.");
        return index;
    }

    @Override
    public void close() {
        for (Page page : List.copyOf(ownedPages)) {
            try {
                if (!page.isClosed()) page.close();
            } catch (RuntimeException ignored) {
                // Closing one tab must not prevent cleanup of the others.
            }
        }
        playwright.close();
    }

    private static boolean isManagedBrowserRunning() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(VERSION_ENDPOINT))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            return HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void startBrowser(Path profile, String browserChannel) throws IOException {
        Path executable = findBrowser(browserChannel);
        List<String> command = new ArrayList<>(List.of(
                executable.toString(),
                "--user-data-dir=" + profile,
                "--remote-debugging-port=9222",
                "--remote-debugging-address=127.0.0.1",
                "--no-first-run",
                "--no-default-browser-check",
                "--new-window",
                "about:blank"
        ));
        new ProcessBuilder(command).directory(profile.getParent().getParent().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    private static Path findBrowser(String channel) throws IOException {
        String executableName = "msedge".equalsIgnoreCase(channel) ? "msedge.exe" : "chrome.exe";
        List<Path> candidates = new ArrayList<>();
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        String localAppData = System.getenv("LOCALAPPDATA");
        if (programFiles != null) {
            candidates.add(Path.of(programFiles, "Google", "Chrome", "Application", "chrome.exe"));
            candidates.add(Path.of(programFiles, "Microsoft", "Edge", "Application", "msedge.exe"));
        }
        if (programFilesX86 != null) {
            candidates.add(Path.of(programFilesX86, "Google", "Chrome", "Application", "chrome.exe"));
            candidates.add(Path.of(programFilesX86, "Microsoft", "Edge", "Application", "msedge.exe"));
        }
        if (localAppData != null) {
            candidates.add(Path.of(localAppData, "Google", "Chrome", "Application", "chrome.exe"));
            candidates.add(Path.of(localAppData, "Microsoft", "Edge", "Application", "msedge.exe"));
        }
        for (Path candidate : candidates) {
            if (candidate.getFileName().toString().equalsIgnoreCase(executableName)
                    && Files.isRegularFile(candidate)) return candidate;
        }
        throw new IOException("Could not find " + executableName + " to start the managed browser.");
    }

    private static void awaitBrowser() throws IOException {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (isManagedBrowserRunning()) return;
            try {
                Thread.sleep(250);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for managed browser", error);
            }
        }
        throw new IOException("Managed browser did not expose CDP at " + CDP_ENDPOINT);
    }
}
