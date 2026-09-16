package com.seeker.orchestrator;

import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.collector.linkedin.collection.CollectionReport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Top-level Java workflow entry point. */
public final class WorkflowOrchestrator {
    private final LinkedInBatch linkedinBatch;

    public WorkflowOrchestrator(Path projectRoot) throws IOException {
        this(new LinkedInApplication(projectRoot)::batch);
    }

    WorkflowOrchestrator(LinkedInBatch linkedinBatch) {
        this.linkedinBatch = Objects.requireNonNull(linkedinBatch);
    }

    public CollectionReport batch(String source) throws IOException {
        if (!"linkedin".equals(source)) {
            throw new IllegalArgumentException("Unsupported batch source: " + source);
        }
        return linkedinBatch.collect();
    }

    @FunctionalInterface
    interface LinkedInBatch {
        CollectionReport collect() throws IOException;
    }
}
