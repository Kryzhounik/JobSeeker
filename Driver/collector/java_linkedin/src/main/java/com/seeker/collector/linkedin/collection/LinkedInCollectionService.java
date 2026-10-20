package com.seeker.collector.linkedin.collection;

import com.seeker.collector.linkedin.browser.BrowserSettings;
import com.seeker.collector.linkedin.browser.PersistentLinkedInSession;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.python.JpyPythonGateway;
import com.seeker.collector.linkedin.python.PythonGateway;
import com.seeker.collector.linkedin.python.PythonRuntime;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Application use case for collecting vacancies. It has no CLI dependency. */
public final class LinkedInCollectionService {
    private final ProjectPaths paths;
    private final CollectorConfig config;
    private final BrowserSettings browserSettings;
    private final PythonRuntime pythonRuntime;
    private final Consumer<CollectionProgress> progress;

    public LinkedInCollectionService(
            ProjectPaths paths,
            CollectorConfig config,
            BrowserSettings browserSettings,
            PythonRuntime pythonRuntime
    ) {
        this(paths, config, browserSettings, pythonRuntime, ignored -> { });
    }

    public LinkedInCollectionService(
            ProjectPaths paths, CollectorConfig config, BrowserSettings browserSettings,
            PythonRuntime pythonRuntime, Consumer<CollectionProgress> progress
    ) {
        this.paths = paths;
        this.config = config;
        this.browserSettings = browserSettings;
        this.pythonRuntime = pythonRuntime;
        this.progress = Objects.requireNonNull(progress);
    }

    public CollectionReport collectBatch(String runId) throws IOException {
        progress.accept(CollectionProgress.starting(runId, "starting", config.limit()));
        try (PythonGateway python = new JpyPythonGateway(paths, pythonRuntime)) {
            python.startRun(runId, config);
            progress.accept(CollectionProgress.starting(runId, "starting_browser", config.limit()));
            try (PersistentLinkedInSession session = new PersistentLinkedInSession(
                    paths.profileDirectory(),
                    browserSettings
            )) {
                LinkedInPageClient browser = new LinkedInPageClient(session, config);
                progress.accept(CollectionProgress.starting(runId, "checking_session", config.limit()));
                browser.preflight();
                return new LinkedInCollector(browser, python, config, runId, progress)
                        .runBatch();
            } catch (IOException | RuntimeException error) {
                python.finishRun(
                        runId,
                        "blocked",
                        "java_exception",
                        0,
                        message(error)
                );
                throw error;
            }
        }
    }

    public CollectionReport collectFromUrl(String runId, String url) throws IOException {
        return collectFromUrls(runId, List.of(url));
    }

    public CollectionReport collectFromUrls(String runId, List<String> urls) throws IOException {
        progress.accept(CollectionProgress.starting(runId, "starting", config.limit()));
        try (PythonGateway python = new JpyPythonGateway(paths, pythonRuntime)) {
            python.startRun(runId, config);
            progress.accept(CollectionProgress.starting(runId, "starting_browser", config.limit()));
            // TODO: Check the existing duplicate rule before opening the browser
            // if starting a session for already collected URLs becomes a problem.
            try (PersistentLinkedInSession session = new PersistentLinkedInSession(
                    paths.profileDirectory(),
                    browserSettings
            )) {
                LinkedInPageClient browser = new LinkedInPageClient(session, config);
                progress.accept(CollectionProgress.starting(runId, "checking_session", config.limit()));
                browser.preflight();
                return new LinkedInCollector(browser, python, config, runId, progress)
                        .runFromUrls(urls);
            } catch (IOException | RuntimeException error) {
                python.finishRun(
                        runId,
                        "blocked",
                        "java_exception",
                        0,
                        message(error)
                );
                throw error;
            }
        }
    }

    private String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank()
                ? error.getClass().getSimpleName()
                : value;
    }
}
