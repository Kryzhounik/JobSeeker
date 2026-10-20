package com.seeker.guifx;

import java.util.List;

public final class ClientDataSmoke {
    public static void main(String[] args) throws Exception {
        ClientData data = new ClientData();
        var statuses = data.loadStatusValues();
        if (statuses.isEmpty()) {
            throw new IllegalStateException("No vacancy statuses returned from the database.");
        }
        var reasonCodes = data.loadReasonCodeDescriptions();
        if (reasonCodes.isEmpty()) {
            throw new IllegalStateException("No Reason code descriptions returned from the database.");
        }
        var jobs = data.loadJobs(new JobFilters(statuses, true, true, "", "", List.of()));
        if (!jobs.isEmpty()) {
            data.loadDetail(jobs.getFirst().jobId());
            jobs.stream().filter(job -> job.sourceUrl().isBlank()).findFirst()
                    .ifPresent(job -> {
                        try {
                            data.loadDetail(job.jobId());
                        } catch (Exception error) {
                            throw new IllegalStateException("Could not load job without URL", error);
                        }
                    });
        }
        var companies = data.loadCompanies();
        var applications = data.loadApplications();
        var collected = data.loadCollectedJobs();
        var config = data.loadConfigRows();
        var availability = data.loadLinkedInAvailabilityCandidates();
        System.out.println("Data connection OK: " + statuses + "; jobs " + jobs.size()
                + "; reason codes " + reasonCodes.size() + "; companies " + companies.size()
                + "; applications " + applications.size() + "; collected " + collected.size()
                + "; config " + config.size() + "; availability candidates " + availability.size());
    }
}
