package com.seeker.guifx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jpy.PyLib;
import org.jpy.PyLibInitializer;
import org.jpy.PyModule;
import org.jpy.PyObject;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
            initializePythonRuntime();
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

    private void initializePythonRuntime() throws Exception {
        Path runtime = projectRoot.resolve("Driver/collector/java_linkedin/runtime.properties");
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(runtime, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (!PyLibInitializer.isPyLibInitialized()) {
            PyLibInitializer.initPyLib(
                    runtimePath(properties, "python.library"),
                    runtimePath(properties, "jpy.library"),
                    runtimePath(properties, "jdl.library")
            );
        }
        if (!PyLib.isPythonRunning()) {
            PyLib.setProgramName(runtimePath(properties, "python.executable"));
            PyLib.setPythonHome(runtimePath(properties, "python.home"));
        }
    }

    private static String runtimePath(Properties properties, String key) {
        String value = properties.getProperty(key, "").strip();
        if (value.isEmpty()) {
            throw new IllegalStateException("Missing " + key + " in Python runtime configuration.");
        }
        return Path.of(value).toAbsolutePath().normalize().toString();
    }

    synchronized List<String> loadStatusValues() throws Exception {
        JsonNode values = readJson(call("load_status_values", databasePath.toString()));
        List<String> statuses = new ArrayList<>();
        values.forEach(value -> statuses.add(value.asText()));
        return statuses;
    }

    synchronized Map<String, String> loadReasonCodeDescriptions() throws Exception {
        JsonNode rows = readJson(call("load_reason_code_descriptions", databasePath.toString()));
        Map<String, String> descriptions = new java.util.LinkedHashMap<>();
        rows.fields().forEachRemaining(entry ->
                descriptions.put(entry.getKey(), entry.getValue().asText("")));
        return descriptions;
    }

    synchronized JsonNode loadCompanies() throws Exception {
        return readJson(call("load_companies", databasePath.toString()));
    }

    synchronized int createCompany(String name, String linkedinId) {
        try (PyObject result = module.call(
                "create_company", databasePath.toString(), name, linkedinId
        )) {
            return result.getIntValue();
        }
    }

    synchronized void setCompanyValue(String method, int companyId, Object value) {
        try (PyObject ignored = module.call(
                method, databasePath.toString(), companyId, value
        )) {
            // Close the Python result wrapper after the immediate database update.
        }
    }

    synchronized JsonNode loadApplications() throws Exception {
        return readJson(call("load_applications", databasePath.toString()));
    }

    synchronized List<String> loadApplicationStatuses() throws Exception {
        JsonNode values = readJson(call(
                "load_application_status_values", databasePath.toString()
        ));
        List<String> statuses = new ArrayList<>();
        values.forEach(value -> statuses.add(value.asText()));
        return statuses;
    }

    synchronized void setApplicationStatus(int applicationId, String status) {
        try (PyObject ignored = module.call(
                "set_application_status", databasePath.toString(), applicationId, status
        )) {
            // Close the Python result wrapper after the immediate database update.
        }
    }

    synchronized JsonNode loadCollectedJobs() throws Exception {
        return readJson(call("load_collected_jobs", databasePath.toString()));
    }

    synchronized JsonNode loadConfigRows() throws Exception {
        return readJson(call("load_config_rows", databasePath.toString()));
    }

    synchronized void setConfigValue(int key, boolean enabled) {
        try (PyObject ignored = module.call(
                "set_config_value", databasePath.toString(), key, enabled
        )) {
            // Close the Python result wrapper after the immediate database update.
        }
    }

    synchronized List<JobRecord> loadJobs(Map<String, Object> filters) throws Exception {
        String filterJson = json.writeValueAsString(filters);
        JsonNode rows = readJson(call("load_jobs", databasePath.toString(), filterJson));
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
                    text(row, "candidate_fit_reason"), text(row, "company_id"),
                    text(row, "application_id"),
                    text(row, "source_url")
            ));
        }
        return jobs;
    }

    synchronized int saveScores(String sourceUrl, int fit, int interest) {
        try (PyObject result = module.call(
                "save_scores", databasePath.toString(), sourceUrl, fit, interest
        )) {
            return result.getIntValue();
        }
    }

    synchronized JsonNode setJobStatus(List<String> sourceUrls, String status, String appliedAt)
            throws Exception {
        return readJson(call("set_job_status", databasePath.toString(),
                json.writeValueAsString(sourceUrls), status, appliedAt));
    }

    synchronized JsonNode loadLinkedInAvailabilityCandidates() throws Exception {
        return readJson(call("load_linkedin_availability_candidates", databasePath.toString()));
    }

    synchronized int markJobsClosed(List<String> sourceUrls, String closedStatus) throws Exception {
        try (PyObject result = module.call("mark_jobs_closed", databasePath.toString(),
                json.writeValueAsString(sourceUrls), closedStatus)) {
            return result.getIntValue();
        }
    }

    synchronized JsonNode collectRefilterCandidates() throws Exception {
        return readJson(call("collect_refilter_candidates", databasePath.toString()));
    }

    synchronized int applyRefilterCandidates(JsonNode candidates) throws Exception {
        try (PyObject result = module.call("apply_refilter_candidates",
                databasePath.toString(), json.writeValueAsString(candidates))) {
            return result.getIntValue();
        }
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

    static Path findProjectRoot() {
        try {
            Path codeLocation = Path.of(
                    ClientData.class.getProtectionDomain().getCodeSource()
                            .getLocation().toURI()
            ).toAbsolutePath();
            Path candidate = findProjectRootFrom(codeLocation);
            if (candidate != null) {
                return candidate;
            }
        } catch (Exception ignored) {
            // Fall back to the launch directory when code source is unavailable.
        }
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path candidate = findProjectRootFrom(directory);
        if (candidate != null) {
            return candidate;
        }
        throw new IllegalStateException(
                "Could not locate repository root containing GUI/client_data.py"
        );
    }

    private static Path findProjectRootFrom(Path location) {
        Path directory = Files.isDirectory(location) ? location : location.getParent();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("GUI/client_data.py"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        return null;
    }
}
