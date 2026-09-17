package com.seeker.collector.linkedin.collection;

import com.seeker.collector.linkedin.browser.BrowserSettings;
import com.seeker.collector.linkedin.browser.PersistentLinkedInSession;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.python.JpyPythonGateway;
import com.seeker.collector.linkedin.python.PythonGateway;
import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.collector.linkedin.support.JsonSupport;

import java.io.IOException;

/** Application use case for collecting vacancies. It has no CLI dependency. */
public final class LinkedInCollectionService {
    private final ProjectPaths paths;
    private final CollectorConfig config;
    private final BrowserSettings browserSettings;
    private final PythonRuntime pythonRuntime;

    public LinkedInCollectionService(
            ProjectPaths paths,
            CollectorConfig config,
            BrowserSettings browserSettings,
            PythonRuntime pythonRuntime
    ) {
        this.paths = paths;
        this.config = config;
        this.browserSettings = browserSettings;
        this.pythonRuntime = pythonRuntime;
    }

    public CollectionReport collectBatch(String runId) throws IOException {
        try (PythonGateway python = new JpyPythonGateway(paths, pythonRuntime)) {
            python.startRun(runId, JsonSupport.GSON.toJson(config));
            try (PersistentLinkedInSession session = new PersistentLinkedInSession(
                    paths.profileDirectory(),
                    browserSettings
            )) {
                LinkedInPageClient browser = new LinkedInPageClient(session, config);
                browser.preflight();
                return new LinkedInCollector(browser, python, config, runId)
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
        try (PythonGateway python = new JpyPythonGateway(paths, pythonRuntime)) {
            python.startRun(runId, JsonSupport.GSON.toJson(config));
            try (PersistentLinkedInSession session = new PersistentLinkedInSession(
                    paths.profileDirectory(),
                    browserSettings
            )) {
                LinkedInPageClient browser = new LinkedInPageClient(session, config);
                browser.preflight();
                return new LinkedInCollector(browser, python, config, runId)
                        .runFromUrl(url);
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
