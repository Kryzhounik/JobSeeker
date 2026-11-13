package com.seeker.collector.linkedin.collection;

import com.seeker.collector.common.*;
import com.seeker.collector.linkedin.python.LinkedInGateway;
import com.seeker.easyreasoning.EasyReasoningApi;

/** LinkedIn owns HTML preparation and its optional LLM cleaner. */
public final class LinkedInVacancyPreparer {
    private final LinkedInGateway python;
    private final EasyReasoningApi reasoning;

    public LinkedInVacancyPreparer(LinkedInGateway python, EasyReasoningApi reasoning) {
        this.python = python;
        this.reasoning = reasoning;
    }

    public ProcessResult process(Preview preview, String html, boolean easyApply, VacancyProcessor processor) {
        LinkedInGateway.Preparation prepared = python.prepare(preview, html, easyApply);
        if (prepared.rejection() != null) return prepared.rejection();
        PreparedVacancy vacancy = prepared.vacancy();
        return processor.process(vacancy.withDescription(reasoning.cleanVacancy(vacancy.description())));
    }
}
