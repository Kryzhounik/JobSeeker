package com.seeker.guifx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jpy.PyLib;
import org.jpy.PyModule;
import org.jpy.PyObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ClientData {
    private final Path projectRoot;
    private final Path databasePath;
    private final PyModule module;
    private final ObjectMapper json = new ObjectMapper();

    ClientData() throws Exception {
        projectRoot = findProjectRoot();
        databasePath = projectRoot.resolve("Data").resolve("jobs.sqlite");
        if (!Files.isRegularFile(databasePath)) {
            throw new IllegalStateException("Database not found: " + databasePath);
        }
        try {
            if (!PyLib.isPythonRunning()) {
                PyLib.startPython(projectRoot.toString());
            }
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    "jpy could not load its configured Python runtime: " + error.getMessage(),
                    error
            );
        }
        module = PyModule.importModule("GUI.client_data");
    }

    synchronized List<String> loadStatusValues() throws Exception {
        JsonNode values = readJson(call("load_status_values", databasePath.toString()));
        List<String> statuses = new ArrayList<>();
        values.forEach(value -> statuses.add(value.asText()));
        return statuses;
    }

    synchronized List<JobRecord> loadJobs(List<String> statuses, boolean showZero)
            throws Exception {
        String filters = json.writeValueAsString(java.util.Map.of(
                "statuses", statuses,
                "show_zero", showZero
        ));
        JsonNode rows = readJson(call("load_jobs", databasePath.toString(), filters));
        List<JobRecord> jobs = new ArrayList<>();
        for (JsonNode row : rows) {
            String company = text(row, "company");
            int applicationCount = integer(row, "company_application_count");
            String companyDisplay = applicationCount > 0
                    ? company + " (" + applicationCount + ")"
                    : company;
            jobs.add(new JobRecord(
                    text(row, "score"), text(row, "fit"), text(row, "interest"),
                    text(row, "status"), text(row, "remote_scope"),
                    text(row, "relocation"), text(row, "location"), companyDisplay,
                    text(row, "title"), text(row, "role"), text(row, "seniority"),
                    text(row, "primary_language"), text(row, "salary"),
                    text(row, "added_at"), text(row, "candidate_fit_reason_code"),
                    text(row, "candidate_fit_reason"), text(row, "source_url")
            ));
        }
        return jobs;
    }

    synchronized JobDetail loadDetail(String sourceUrl) throws Exception {
        JsonNode result = readJson(call(
                "load_job_detail", databasePath.toString(), sourceUrl
        ));
        JsonNode detail = result.path("detail");
        List<TechnologyRecord> technologies = new ArrayList<>();
        for (JsonNode row : result.path("technologies")) {
            technologies.add(new TechnologyRecord(
                    text(row, "technology"), text(row, "req"),
                    text(row, "level"), text(row, "raw_value")
            ));
        }
        return new JobDetail(
                text(detail, "id"), text(detail, "source_job_id"),
                text(detail, "title"), text(detail, "company"),
                text(detail, "location"), text(detail, "remote_type"),
                text(detail, "remote_scope"), text(detail, "status"),
                text(detail, "relocation"), text(detail, "seniority"),
                text(detail, "role"), text(detail, "salary"),
                text(detail, "interest"), text(detail, "fit"),
                text(detail, "score"), text(detail, "source_url"),
                text(detail, "summary"), text(detail, "readable_text"),
                text(detail, "added_at"), text(detail, "languages"), technologies
        );
    }

    private String call(String method, Object... arguments) {
        try (PyObject result = module.call(method, arguments)) {
            return result.getStringValue();
        }
    }

    private JsonNode readJson(String value) throws Exception {
        return json.readTree(value);
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static int integer(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return 0;
        }
        try {
            return value.asInt();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static Path findProjectRoot() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("GUI/client_data.py"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException(
                "Could not locate repository root containing GUI/client_data.py"
        );
    }
}
