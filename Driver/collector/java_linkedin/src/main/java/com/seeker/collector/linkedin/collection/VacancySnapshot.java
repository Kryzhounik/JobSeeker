package com.seeker.collector.linkedin.collection;

/** Saved description used by batch comparison. */
public record VacancySnapshot(long id, String jobId, Long companyId, String title, String text) { }
