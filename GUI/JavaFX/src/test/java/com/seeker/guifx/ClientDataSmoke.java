package com.seeker.guifx;

import java.util.Map;

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
        var jobs = data.loadJobs(Map.of(
                "statuses", statuses,
                "show_zero", true,
                "id_query", "",
                "added_from", "",
                "reasons", java.util.List.of()
        ));
        if (!jobs.isEmpty()) {
            data.loadDetail(jobs.getFirst().sourceUrl());
        }
        System.out.println("Data connection OK: " + statuses + "; jobs " + jobs.size()
                + "; reason codes " + reasonCodes.size());
    }
}
