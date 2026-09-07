package com.seeker.collector.linkedin.python;

import com.seeker.collector.linkedin.config.ProjectPaths;

@FunctionalInterface
public interface PythonGatewayFactory {
    PythonGateway open(ProjectPaths paths, PythonRuntime runtime);
}
