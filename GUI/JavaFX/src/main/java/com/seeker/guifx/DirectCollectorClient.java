package com.seeker.guifx;

import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.CollectionReport;

import java.nio.file.Path;
import java.util.function.Consumer;

final class DirectCollectorClient implements CollectorClient {
    private final Path projectRoot;

    DirectCollectorClient(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    @Override
    public CollectionReport collect(Consumer<CollectionProgress> progress) throws Exception {
        return new LinkedInApplication(projectRoot, progress).collectBatch();
    }
}
