package com.seeker.tools.api;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgressSnapshotTest {
    @Test
    void preservesFieldOrderAndIsIndependentOfLaterInputChanges() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Accepted", "7");
        fields.put("Limit", "100");
        ProgressSnapshot snapshot = new ImmutableProgressSnapshot(
                "run-1", "Collection", "Loading vacancy", fields, "Current details");

        fields.put("Accepted", "8");
        fields.remove("Limit");
        fields.put("Another field", "value");

        assertEquals(List.of("Accepted", "Limit"), new ArrayList<>(snapshot.fields().keySet()));
        assertEquals(Map.of("Accepted", "7", "Limit", "100"), snapshot.fields());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.fields().put("Accepted", "9"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.fields().entrySet().iterator().next().setValue("9"));
    }

    @Test
    void serializesTheCommonSnapshotAndOrderedFieldsWithOrdinaryGson() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Current vacancy", "123");
        fields.put("Completed", "2 / 5");
        ProgressSnapshot snapshot = new ImmutableProgressSnapshot(
                "run-1", "Job facts", "Extracting facts", fields, "Java Developer");

        var json = new Gson().toJsonTree(snapshot).getAsJsonObject();

        assertEquals("run-1", json.get("runId").getAsString());
        assertEquals("Job facts", json.get("stage").getAsString());
        assertEquals("Extracting facts", json.get("action").getAsString());
        assertEquals("Java Developer", json.get("details").getAsString());
        assertEquals(List.of("Current vacancy", "Completed"),
                new ArrayList<>(json.getAsJsonObject("fields").keySet()));
        assertEquals("2 / 5", json.getAsJsonObject("fields").get("Completed").getAsString());
    }
}
