package com.seeker.collector.linkedin.collection;

/** Plain saved data; safe to pass to the comparison worker. */
public record VacancySnapshot(long id, String jobId, Long companyId, String title, String text) { }
