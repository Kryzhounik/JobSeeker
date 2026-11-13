package com.seeker.collector.common;

import com.seeker.collector.common.python.PythonGateway;

/** Shared operations, available to sources before and after description loading. */
public final class VacancyProcessor {
    private final PythonGateway python;

    public VacancyProcessor(PythonGateway python) { this.python = python; }

    public PreviewDecision decidePreview(Preview preview) { return python.decidePreview(preview); }

    public String saveRaw(Preview preview, String content, String extension, String method) {
        return python.saveRaw(preview, content, extension, method);
    }

    public ProcessResult process(PreparedVacancy vacancy) {
        return python.processVacancy(vacancy);
    }

    public void logOutcome(Preview preview, String status, String reason) {
        python.logOutcome(preview, status, reason);
    }
}
