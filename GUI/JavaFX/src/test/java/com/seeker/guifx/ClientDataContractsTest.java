package com.seeker.guifx;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class ClientDataContractsTest {

    @Test
    void jobFilters() throws IOException {
        List<String> statuses = new ArrayList<>(List.of("New", "Checked"));
        List<String> reasons = new ArrayList<>(List.of("tech", "lang"));
        JobFilters filters = new JobFilters(statuses, true, false, "123", "2026-10-20", reasons);
        statuses.clear();
        reasons.clear();
        var request = new ObjectMapper().readTree(ClientData.jobFiltersRequest(filters));
        assertTrue(request.size() == 6 && request.path("statuses").size() == 2
                        && request.path("reasons").size() == 2,
                "Filter fields or list snapshots changed");
        assertTrue(request.path("show_zero").isBoolean() && request.path("show_zero").asBoolean()
                        && request.path("show_unavailable").isBoolean()
                        && !request.path("show_unavailable").asBoolean()
                        && request.path("id_query").asText().equals("123")
                        && request.path("added_from").asText().equals("2026-10-20"),
                "Typed filters were not serialized with the Python contract");
        var empty = new ObjectMapper().readTree(ClientData.jobFiltersRequest(
                new JobFilters(List.of(), false, true, "", "", List.of())));
        assertTrue(empty.size() == 6 && empty.path("statuses").isEmpty()
                        && empty.path("reasons").isEmpty() && !empty.path("show_zero").asBoolean()
                        && empty.path("show_unavailable").asBoolean()
                        && empty.path("id_query").asText().isEmpty()
                        && empty.path("added_from").asText().isEmpty(),
                "Empty filters or explicit availability setting changed");
    }

    @Test
    void runs() throws IOException {
        CollectionRunDto row = ClientData.readRows("""
                [{"run_id":"test-run","started_at":"2026-10-28 10:00:00","finished_at":null,
                  "status":"running","stage":"Job facts","collection_status":"complete",
                  "collection_finished_at":"2026-10-28 10:30:00","accepted_count":50,
                  "stop_reason":"limit_reached","message":"Error\\nURL: https://example.com"}]
                """, CollectionRunDto.class).getFirst();
        assertTrue(row.runId().equals("test-run") && row.acceptedCount() == 50
                        && row.finishedAt().isEmpty() && row.stage().equals("Job facts")
                        && row.status().equals("running") && row.collectionStatus().equals("complete")
                        && row.message().contains("\nURL:"),
                "Run fields, nullable times, or separate collection/workflow status were lost");
    }

    @Test
    void companies() throws IOException {
        CompanyDto row = ClientData.readRows("""
                [{"id":7,"priority":1,"name":"Acme","linkedin_id":null,
                  "blacklisted":0,"application_count":3}]
                """, CompanyDto.class).getFirst();
        assertTrue(row.id() == 7 && row.priority() == 1 && row.applicationCount() == 3,
                "Company numeric fields were lost");
        assertTrue(row.name().equals("Acme") && row.linkedinId().isEmpty(),
                "Company text or null LinkedIn ID was not mapped");
    }

    @Test
    void applications() throws IOException {
        ApplicationDto row = ClientData.readRows("""
                [{"id":8,"job_id":91,"applied_at":"2026-10-20","status":"Applied",
                  "source_url":"https://example.com/91","title":"Java Developer",
                  "company_id":null,"company":"Acme"}]
                """, ApplicationDto.class).getFirst();
        assertTrue(row.id() == 8 && row.jobId() == 91 && row.companyId() == null,
                "Application IDs or nullable company ID changed");
        assertTrue(row.appliedAt().equals("2026-10-20") && row.title().equals("Java Developer")
                        && row.sourceUrl().endsWith("/91"), "Application text was not mapped");
    }

    @Test
    void collected() throws IOException {
        CollectedJobDto row = ClientData.readRows("""
                [{"source_job_ref":10,"source":"linkedin","source_job_id":"4474071200",
                  "processing_status":"CLEANED","collection_method":"playwright",
                  "source_url":"https://www.linkedin.com/jobs/view/4474071200/",
                  "title":"Java Developer","company":"Acme","collected_location":"Poland",
                  "collected_workplace":"remote","collected_salary":null,
                  "readable_text":"First line\\nSecond line"}]
                """, CollectedJobDto.class).getFirst();
        assertTrue(row.sourceJobRef() == 10 && row.sourceJobId().equals("4474071200"),
                "Source job ID was truncated or remapped");
        assertTrue(row.processingStatus().equals("CLEANED") && row.collectionMethod().equals("playwright")
                        && row.collectedLocation().equals("Poland")
                        && row.collectedWorkplace().equals("remote")
                        && row.collectedSalary().isEmpty() && row.readableText().contains("\n"),
                "Collected fields or null text were not mapped");
    }

    @Test
    void config() throws IOException {
        ConfigEntryDto row = ClientData.readRows("""
                [{"key":4,"config_name":"Title filter","value":"1"}]
                """, ConfigEntryDto.class).getFirst();
        assertTrue(row.key() == 4 && row.configName().equals("Title filter") && row.value().equals("1"),
                "Config entry was not mapped");
    }

    @Test
    void statusUpdates() throws IOException {
        StatusUpdateResult byId = ClientData.readResponse("""
                {"updated_count":3,"created_applications":1}
                """, StatusUpdateResult.class);
        assertTrue(byId.updatedCount() == 3 && byId.createdApplications() == 1
                        && byId.applicationIds().isEmpty() && byId.companyApplicationCounts().isEmpty(),
                "Status result counts or optional maps changed");
        StatusUpdateResult byUrl = ClientData.readResponse("""
                {"updated_count":1,"created_applications":1,
                 "application_ids":{"https://example.com/91":"8"},
                 "company_application_counts":{"7":"3"}}
                """, StatusUpdateResult.class);
        assertTrue(byUrl.applicationIds().get("https://example.com/91").equals("8")
                        && byUrl.companyApplicationCounts().get("7").equals("3"),
                "URL-based status result lost its additional fields");
    }

    @Test
    void availability() throws IOException {
        AvailabilityCandidate row = ClientData.readRows("""
                [{"source_job_id":"4474071200","source_url":"https://example.com/91",
                  "title":"Java Developer","company":"Acme","score":827}]
                """, AvailabilityCandidate.class).getFirst();
        assertTrue(row.sourceJobId().equals("4474071200") && row.score() == 827
                        && row.sourceUrl().endsWith("/91"), "Availability candidate was not mapped");
        AvailabilityUpdateResult result = ClientData.readResponse("""
                {"availability":"deleted","unavailable_checks":3}
                """, AvailabilityUpdateResult.class);
        assertTrue(result.availability().equals("deleted") && result.unavailableChecks() == 3,
                "Availability update was not mapped");
    }

    @Test
    void refilter() throws IOException {
        List<RefilterCandidate> rows = ClientData.readRows("""
                [{"source_job_ref":10,"source":"linkedin","source_job_id":"4474071200",
                  "previous_status":"SAVED","action":"DELETE","id":91,
                  "title":"Lead Product Engineer","fit":47,"source_url":"https://example.com/91",
                  "original":"Original text","matched":"Product","rule":"title","reason":"Rejected"},
                 {"source_job_ref":11,"source":"linkedin","source_job_id":"4474071201",
                  "previous_status":"CLEANED","action":"CONTENT_REJECTED","id":null,
                  "title":"Other job","fit":null,"source_url":"https://example.com/92",
                  "original":"Other text","matched":"Python","rule":"technology","reason":"Mismatch"}]
                """, RefilterCandidate.class);
        assertTrue(rows.getFirst().id() == 91 && rows.getFirst().fit() == 47,
                "Refilter numeric fields were not mapped");
        assertTrue(rows.getLast().id() == null && rows.getLast().fit() == null,
                "Null Refilter ID/Fit must stay null, not zero");
        String request = ClientData.refilterRequest(List.of(rows.getLast()));
        var selected = new ObjectMapper().readTree(request);
        assertTrue(selected.size() == 1 && selected.get(0).path("source_job_ref").asInt() == 11
                        && selected.get(0).path("previous_status").asText().equals("CLEANED")
                        && selected.get(0).path("action").asText().equals("CONTENT_REJECTED"),
                "Selected Refilter transition was not serialized with Python field names");
        assertTrue(ClientData.readRows(request, RefilterCandidate.class).equals(List.of(rows.getLast())),
                "Refilter round trip lost fields");
        assertTrue(ClientData.refilterRequest(List.of()).equals("[]"), "Empty selection changed");
    }

    @Test
    void invalidResponses() throws IOException {
        assertTrue(ClientData.readRows("[]", CompanyDto.class).isEmpty(), "Empty rows changed");
        for (String invalid : List.of("null", "{}", "not JSON")) {
            assertThrows(IOException.class, () -> ClientData.readRows(invalid, CompanyDto.class), invalid);
        }
        assertThrows(IOException.class, () -> ClientData.readResponse("null", StatusUpdateResult.class));
    }

}
