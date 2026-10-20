package com.seeker.guifx;

import java.util.Map;

record StatusUpdateResult(int updatedCount, int createdApplications,
                          Map<String, String> applicationIds,
                          Map<String, String> companyApplicationCounts) {
    StatusUpdateResult {
        applicationIds = applicationIds == null ? Map.of() : Map.copyOf(applicationIds);
        companyApplicationCounts = companyApplicationCounts == null
                ? Map.of() : Map.copyOf(companyApplicationCounts);
    }
}
