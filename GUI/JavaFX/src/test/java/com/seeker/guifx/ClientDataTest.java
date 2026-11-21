package com.seeker.guifx;

import java.util.List;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClientDataTest {
    @TempDir static Path directory;
    private static ClientData data;

    @BeforeAll static void prepareDatabase() throws Exception {
        data = new ClientData(TestDatabase.create(directory));
    }

    @Test void readsFixtureDataThroughJpy() throws Exception {
        var statuses = data.loadStatusValues();
        if (statuses.isEmpty()) {
            throw new IllegalStateException("No vacancy statuses returned from the database.");
        }
        var reasonCodes = data.loadReasonCodeDescriptions();
        if (reasonCodes.isEmpty()) {
            throw new IllegalStateException("No Reason code descriptions returned from the database.");
        }
        var jobs = data.loadJobs(new JobFilters(statuses, true, true, "", "", List.of()));
        assertEquals(3, jobs.size());
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
        assertEquals(1, companies.size());
        assertEquals(1, applications.size());
        assertEquals(3, collected.size());
        assertFalse(config.isEmpty());
        assertFalse(availability.isEmpty());
    }
}
