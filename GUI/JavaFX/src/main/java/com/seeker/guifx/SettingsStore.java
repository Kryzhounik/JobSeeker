package com.seeker.guifx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class SettingsStore {
    private final Path path;
    private final ObjectMapper json = new ObjectMapper();
    private final ObjectNode values;

    SettingsStore(Path projectRoot) {
        path = projectRoot.resolve("GUI/JavaFX/gui-settings.json");
        ObjectNode loaded;
        try {
            JsonNode node = json.readTree(path.toFile());
            loaded = node instanceof ObjectNode objectNode
                    ? objectNode
                    : json.createObjectNode();
        } catch (Exception ignored) {
            loaded = json.createObjectNode();
        }
        values = loaded;
    }

    boolean statusEnabled(String status) {
        return values.path("status_filters").path(status).asBoolean(true);
    }

    boolean showZero() {
        return values.path("show_zero").asBoolean(false);
    }

    String sortColumn() {
        return values.path("job_sort").path("column").asText("");
    }

    boolean sortDescending() {
        return values.path("job_sort").path("descending").asBoolean(false);
    }

    double windowWidth() {
        return boundedSize("width", 1440, 900);
    }

    double windowHeight() {
        return boundedSize("height", 900, 620);
    }

    double mainDivider() {
        return boundedDivider("main_divider", 0.48);
    }

    double detailDivider() {
        return boundedDivider("detail_divider", 0.34);
    }

    double windowWidth(String key, double fallback) {
        return boundedSize(key + "_width", fallback, 320);
    }

    double windowHeight(String key, double fallback) {
        return boundedSize(key + "_height", fallback, 240);
    }

    String tableSortColumn(String key) {
        return values.path(key + "_sort").path("column").asText("");
    }

    boolean tableSortDescending(String key) {
        return values.path(key + "_sort").path("descending").asBoolean(false);
    }

    String collectedStage() {
        return values.path("collected_stage_filter").asText("All");
    }

    synchronized void saveStatusFilters(java.util.Map<String, Boolean> filters, boolean showZero)
            throws IOException {
        ObjectNode statusValues = json.createObjectNode();
        filters.forEach(statusValues::put);
        values.set("status_filters", statusValues);
        values.put("show_zero", showZero);
        save();
    }

    synchronized void saveJobSort(String column, boolean descending) throws IOException {
        ObjectNode sort = json.createObjectNode();
        sort.put("column", column);
        sort.put("descending", descending);
        values.set("job_sort", sort);
        save();
    }

    synchronized void saveWindowSize(double width, double height) throws IOException {
        values.put("width", width);
        values.put("height", height);
        save();
    }

    synchronized void saveDividers(double main, double detail) throws IOException {
        values.put("main_divider", main);
        values.put("detail_divider", detail);
        save();
    }

    synchronized void saveWindowSize(String key, double width, double height) throws IOException {
        values.put(key + "_width", width);
        values.put(key + "_height", height);
        save();
    }

    synchronized void saveTableSort(String key, String column, boolean descending)
            throws IOException {
        ObjectNode sort = json.createObjectNode();
        sort.put("column", column);
        sort.put("descending", descending);
        values.set(key + "_sort", sort);
        save();
    }

    synchronized void saveCollectedStage(String stage) throws IOException {
        values.put("collected_stage_filter", stage);
        save();
    }

    private double boundedSize(String key, double fallback, double minimum) {
        double value = values.path(key).asDouble(fallback);
        return Double.isFinite(value) ? Math.max(minimum, value) : fallback;
    }

    private double boundedDivider(String key, double fallback) {
        double value = values.path(key).asDouble(fallback);
        return Double.isFinite(value) ? Math.max(0.1, Math.min(0.9, value)) : fallback;
    }

    private void save() throws IOException {
        Files.createDirectories(path.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), values);
    }
}
