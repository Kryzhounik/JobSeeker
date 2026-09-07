package com.seeker.collector.linkedin.collection;

import com.seeker.collector.linkedin.browser.AuthenticationRequiredException;
import com.seeker.collector.linkedin.browser.PersistentLinkedInSession;
import com.seeker.collector.linkedin.python.JpyPythonGateway;
import com.seeker.collector.linkedin.python.PythonGateway;
import com.seeker.collector.linkedin.python.PythonGatewayFactory;

import java.io.IOException;
import java.util.Objects;

/** Application use case for collecting vacancies. It has no CLI dependency. */
public final class LinkedInCollectionService {
    private final PythonGatewayFactory pythonGatewayFactory;

    public LinkedInCollectionService() {
        this(JpyPythonGateway::new);
    }

    public LinkedInCollectionService(PythonGatewayFactory pythonGatewayFactory) {
        this.pythonGatewayFactory = Objects.requireNonNull(pythonGatewayFactory);
    }

    public CollectionReport collect(CollectionRequest request) {
        try (PersistentLinkedInSession session = new PersistentLinkedInSession(
                request.paths().profileDirectory(),
                request.browserSettings()
        )) {
            LinkedInPageClient browser = new LinkedInPageClient(
                    session,
                    request.collectorConfig()
            );
            browser.preflight();

            try (PythonGateway python = pythonGatewayFactory.open(
                    request.paths(),
                    request.pythonRuntime()
            )) {
                LinkedInCollector collector = new LinkedInCollector(
                        browser,
                        python,
                        request.collectorConfig(),
                        request.runId()
                );
                return request.mode() == CollectionRequest.Mode.BATCH
                        ? collector.runBatch(request.location())
                        : collector.runFromUrl(request.url());
            }
        } catch (AuthenticationRequiredException error) {
            return CollectionReport.failure(
                    request.runId(),
                    "login_required",
                    message(error)
            );
        } catch (IOException | RuntimeException error) {
            return CollectionReport.failure(
                    request.runId(),
                    "blocked",
                    message(error)
            );
        }
    }

    private String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank()
                ? error.getClass().getSimpleName()
                : value;
    }
}
