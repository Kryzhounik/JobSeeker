package com.seeker.guifx;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.python.JpyRuntime;
import com.seeker.chatevaluator.GptEvaluation;
import org.jpy.PyModule;
import org.jpy.PyObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ClientData {
    private static final ObjectMapper json = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    static {
        json.configOverride(String.class)
                .setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY));
    }

    private final Path projectRoot;
    private final Path databasePath;
    private final PyModule module;

    ClientData() throws Exception {
        projectRoot = findProjectRoot();
        databasePath = projectRoot.resolve("Data").resolve("jobs.sqlite");
        if (!Files.isRegularFile(databasePath)) {
            throw new IllegalStateException("Database not found: " + databasePath);
        }
        try {
            initializePythonRuntime();
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    "jpy could not load its configured Python runtime: " + error.getMessage(),
                    error
            );
        }
        module = PyModule.importModule("GUI.client_data");
    }

    private void initializePythonRuntime() throws Exception {
        PythonRuntime runtime = PythonRuntime.load(
                projectRoot.resolve("Driver/collector/java_linkedin/runtime.properties"));
        JpyRuntime.ensureStarted(runtime.pythonLibrary(), runtime.jpyLibrary(), runtime.jdlLibrary(),
                runtime.pythonHome(), runtime.pythonExecutable(), projectRoot,
                projectRoot.resolve("Driver"), projectRoot.resolve("Driver/collector"));
    }

    synchronized Optional<GptEvaluation.Result> loadGptEvaluation(int jobId) throws IOException {
        return Optional.ofNullable(json.readValue(
                call("load_gpt_evaluation", databasePath.toString(), jobId), GptEvaluation.Result.class));
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

    synchronized List<CompanyDto> loadCompanies() throws Exception {
        return readRows(call("load_companies", databasePath.toString()), CompanyDto.class);
    }

    synchronized int createCompany(String name, String linkedinId) {
        try (PyObject result = module.call(
                "create_company", databasePath.toString(), name, linkedinId
        )) {
            return result.getIntValue();
        }
    }

    synchronized void setCompanyLinkedInId(int companyId, String linkedinId) {
        callVoid("set_company_linkedin_id", databasePath.toString(), companyId, linkedinId);
    }

    synchronized void setCompanyPriority(int companyId, boolean priority) {
        callVoid("set_company_priority", databasePath.toString(), companyId, priority);
    }

    synchronized void setCompanyBlacklisted(int companyId, boolean blacklisted) {
        callVoid("set_company_blacklisted", databasePath.toString(), companyId, blacklisted);
    }

    synchronized List<ApplicationDto> loadApplications() throws Exception {
        return readRows(call("load_applications", databasePath.toString()), ApplicationDto.class);
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

    synchronized List<CollectionRunDto> loadCollectionRuns() throws Exception {
        return readRows(call("load_collection_runs", databasePath.toString()), CollectionRunDto.class);
    }

    synchronized List<CollectedJobDto> loadCollectedJobs() throws Exception {
        return readRows(call("load_collected_jobs", databasePath.toString()), CollectedJobDto.class);
    }

    synchronized List<ConfigEntryDto> loadConfigRows() throws Exception {
        return readRows(call("load_config_rows", databasePath.toString()), ConfigEntryDto.class);
    }

    synchronized void setConfigValue(int key, boolean enabled) {
        try (PyObject ignored = module.call(
                "set_config_value", databasePath.toString(), key, enabled
        )) {
            // Close the Python result wrapper after the immediate database update.
        }
    }

    synchronized List<JobRecord> loadJobs(JobFilters filters) throws Exception {
        String filterJson = jobFiltersRequest(filters);
        JsonNode rows = readJson(call("load_jobs", databasePath.toString(), filterJson));
        List<JobRecord> jobs = new ArrayList<>();
        for (JsonNode row : rows) {
            String company = text(row, "company");
            int applicationCount = integer(row, "company_application_count");
            String companyDisplay = applicationCount > 0
                    ? company + " (" + applicationCount + ")"
                    : company;
            jobs.add(new JobRecord(
                    row.path("job_id").asInt(),
                    text(row, "score"), text(row, "fit"), text(row, "interest"),
                    text(row, "status"), text(row, "remote_scope"),
                    text(row, "relocation"), text(row, "location"), companyDisplay,
                    text(row, "title"), text(row, "role"), text(row, "seniority"),
                    text(row, "primary_language"), text(row, "salary"),
                    text(row, "added_at"), text(row, "candidate_fit_reason_code"),
                    text(row, "candidate_fit_reason"), text(row, "company_id"),
                    text(row, "application_id"),
                    text(row, "source_url"), row.path("easy_apply").asInt() == 1
            ));
        }
        return jobs;
    }

    synchronized int saveScores(int jobId, int fit, int interest) {
        try (PyObject result = module.call(
                "save_scores", databasePath.toString(), jobId, fit, interest
        )) {
            return result.getIntValue();
        }
    }

    synchronized StatusUpdateResult setJobStatus(List<String> sourceUrls, String status, String appliedAt)
            throws Exception {
        return readResponse(call("set_job_status", databasePath.toString(),
                json.writeValueAsString(sourceUrls), status, appliedAt), StatusUpdateResult.class);
    }

    synchronized StatusUpdateResult setJobStatusByIds(List<Integer> jobIds, String status, String appliedAt)
            throws Exception {
        return readResponse(call("set_job_status_by_ids", databasePath.toString(),
                json.writeValueAsString(jobIds), status, appliedAt), StatusUpdateResult.class);
    }

    synchronized List<AvailabilityCandidate> loadLinkedInAvailabilityCandidates() throws Exception {
        return readRows(call("load_linkedin_availability_candidates", databasePath.toString()),
                AvailabilityCandidate.class);
    }

    synchronized AvailabilityUpdateResult recordLinkedInAvailability(String jobId, String outcome)
            throws Exception {
        return readResponse(call("record_linkedin_availability", databasePath.toString(), jobId, outcome),
                AvailabilityUpdateResult.class);
    }

    synchronized List<RefilterCandidate> collectRefilterCandidates() throws Exception {
        return readRows(call("collect_refilter_candidates", databasePath.toString()), RefilterCandidate.class);
    }

    synchronized int applyRefilterCandidates(List<RefilterCandidate> candidates) throws Exception {
        try (PyObject result = module.call("apply_refilter_candidates",
                databasePath.toString(), refilterRequest(candidates))) {
            return result.getIntValue();
        }
    }

    synchronized JobDetail loadDetail(int jobId) throws Exception {
        JsonNode result = readJson(call(
                "load_job_detail", databasePath.toString(), jobId
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

    private void callVoid(String method, Object... arguments) {
        try (PyObject ignored = module.call(method, arguments)) {
            // Release the result wrapper even when the Python operation returns None.
        }
    }

    private JsonNode readJson(String value) throws Exception {
        return json.readTree(value);
    }

    static <T> T readResponse(String value, Class<T> type) throws IOException {
        T result = json.readValue(value, type);
        if (result == null) throw new IOException("Expected " + type.getSimpleName() + ", got null");
        return result;
    }

    static <T> List<T> readRows(String value, Class<T> type) throws IOException {
        List<T> result = json.readValue(value,
                json.getTypeFactory().constructCollectionType(List.class, type));
        if (result == null) throw new IOException("Expected rows of " + type.getSimpleName() + ", got null");
        return List.copyOf(result);
    }

    static String refilterRequest(List<RefilterCandidate> candidates) throws IOException {
        return json.writeValueAsString(candidates);
    }

    static String jobFiltersRequest(JobFilters filters) throws IOException {
        return json.writeValueAsString(filters);
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
