package com.seeker.collector.linkedin.cli;

import com.seeker.collector.linkedin.browser.BrowserSettings;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.CollectionRequest;
import com.seeker.collector.linkedin.collection.LinkedInCollectionService;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.login.LinkedInLoginService;
import com.seeker.collector.linkedin.login.LoginRequest;
import com.seeker.collector.linkedin.login.LoginResult;
import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.collector.linkedin.support.JsonSupport;

import java.nio.file.Path;

/** Maps command-line input/output to application use cases. */
public final class CliController {
    private static final int LOGIN_TIMEOUT_SECONDS = 600;

    private final LinkedInLoginService loginService;
    private final LinkedInCollectionService collectionService;

    public CliController() {
        this(new LinkedInLoginService(), new LinkedInCollectionService());
    }

    public CliController(
            LinkedInLoginService loginService,
            LinkedInCollectionService collectionService
    ) {
        this.loginService = loginService;
        this.collectionService = collectionService;
    }

    public int execute(String[] args) {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (CliOptions.HelpRequested ignored) {
            System.out.print(CliOptions.usage());
            return 0;
        } catch (RuntimeException error) {
            System.err.println(message(error));
            System.err.print(CliOptions.usage());
            return 2;
        }

        try {
            ProjectPaths paths = ProjectPaths.fromProjectRoot(Path.of("."));
            CollectorConfig config = CollectorConfig
                    .load(paths.configPath())
                    .withLimit(options.limit());
            BrowserSettings browserSettings = new BrowserSettings(
                    config.browserChannel(),
                    false,
                    config.pageTimeoutSeconds()
            );

            if (options.command() == CliOptions.Command.LOGIN) {
                System.err.println(
                        "Waiting for LinkedIn authentication in the opened browser..."
                );
                LoginResult result = loginService.login(new LoginRequest(
                        paths.profileDirectory(),
                        browserSettings,
                        LOGIN_TIMEOUT_SECONDS
                ));
                printJson(result);
                return result.ready() ? 0 : 2;
            }

            CollectionReport report = collectionService.collect(new CollectionRequest(
                    options.command() == CliOptions.Command.BATCH
                            ? CollectionRequest.Mode.BATCH
                            : CollectionRequest.Mode.FROM_URL,
                    paths,
                    config,
                    browserSettings,
                    PythonRuntime.load(
                            paths.collectorRoot().resolve("java_linkedin/runtime.properties")
                    ),
                    options.runId(),
                    options.location(),
                    options.url()
            ));
            printJson(report);
            return "complete".equals(report.status()) ? 0 : 1;
        } catch (RuntimeException | java.io.IOException error) {
            error.printStackTrace(System.err);
            printJson(CollectionReport.failure(
                    options.runId(),
                    "blocked",
                    message(error)
            ));
            return 1;
        }
    }

    private void printJson(Object value) {
        System.out.println(JsonSupport.GSON.toJson(value));
    }

    private String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank()
                ? error.getClass().getSimpleName()
                : value;
    }
}
