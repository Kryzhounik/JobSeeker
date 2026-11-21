package com.seeker.guifx;

import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static com.seeker.guifx.DataViewTestSupport.onFx;

class UiDataTest {
    @TempDir static Path directory;
    private static DataViewTestSupport view;

    @BeforeAll static void prepare() throws Exception {
        view = DataViewTestSupport.open(TestDatabase.create(directory), directory.resolve("settings.json"));
    }
    @AfterAll static void close() throws Exception { if (view != null) view.close(); }

    @Test void filtersDetailsAndApplicationsNavigation() throws Exception {
        TableView<JobRecord> jobs = view.get("jobsTable");
        DataViewTestSupport.await(() -> !view.<TextField>get("idField").getText().isBlank());
        onFx(() -> {
            assertEquals(3, jobs.getItems().size());
            assertEquals(16, jobs.getColumns().size());
            assertFalse(view.<TextField>get("titleField").getText().isBlank());
            view.<ToggleButton>get("textToggle").fire();
            assertTrue(view.<TextArea>get("fullTextArea").isVisible());
            view.<ToggleButton>get("textToggle").fire();
            assertFalse(view.<TextArea>get("fullTextArea").isVisible());
            view.<TextField>get("idFilterField").setText("1001");
            view.<Button>get("refreshButton").fire();
            return null;
        });
        view.awaitLoaded();
        onFx(() -> {
            assertEquals(java.util.List.of(1), jobs.getItems().stream().map(JobRecord::jobId).toList());
            view.<Button>get("clearButton").fire();
            return null;
        });
        view.awaitLoaded();
        onFx(() -> {
            assertEquals(3, jobs.getItems().size());
            view.<HBox>get("statusFilters").getChildren().stream().filter(CheckBox.class::isInstance)
                    .map(CheckBox.class::cast).forEach(box -> box.setSelected("New".equals(box.getText())));
            view.<Button>get("refreshButton").fire();
            return null;
        });
        view.awaitLoaded();
        onFx(() -> {
            assertTrue(jobs.getItems().stream().allMatch(row -> row.status().equals("New")));
            view.controller().focusJobFromAuxiliary(3);
            return null;
        });
        DataViewTestSupport.await(() -> jobs.getSelectionModel().getSelectedItem() != null
                && jobs.getSelectionModel().getSelectedItem().jobId() == 3
                && view.<TextField>get("idField").getText().equals("3"));
        onFx(() -> {
            assertEquals("3", view.<TextField>get("idFilterField").getText());
            assertTrue(view.<HBox>get("statusFilters").getChildren().stream()
                    .anyMatch(node -> node instanceof CheckBox box
                            && box.getText().equals("Applied") && box.isSelected()));
            return null;
        });
    }
}
