package com.seeker.guifx;

import java.util.List;

record JobDetail(
        String id,
        String sourceId,
        String title,
        String company,
        String location,
        String remoteType,
        String remoteScope,
        String status,
        String relocation,
        String seniority,
        String role,
        String salary,
        String interest,
        String fit,
        String score,
        String sourceUrl,
        String summary,
        String readableText,
        String addedAt,
        String languages,
        List<TechnologyRecord> technologies
) { }
