package com.seeker.guifx;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static com.seeker.guifx.DataViewTestSupport.onFx;

class JobSortRefreshTest {
    @TempDir static Path directory;
    private static DataViewTestSupport view;

    @BeforeAll static void prepare() throws Exception {
        Path database = TestDatabase.create(directory);
        Path settings = directory.resolve("settings.json");
        Files.writeString(settings, "{\"job_sort\":{\"column\":\"score\",\"descending\":true}}");
        view = DataViewTestSupport.open(database, settings);
    }

    @AfterAll static void close() throws Exception { if (view != null) view.close(); }

    @Test void refreshAndFilterKeepSavedSort() throws Exception {
        assertSorted();
        onFx(() -> { view.<Button>get("refreshButton").fire(); return null; });
        view.awaitLoaded();
        assertSorted();
        onFx(() -> {
            view.<HBox>get("statusFilters").getChildren().stream()
                    .filter(node -> node instanceof CheckBox box && box.getText().equals("Checked"))
                    .map(CheckBox.class::cast).findFirst().orElseThrow().fire();
            return null;
        });
        view.awaitLoaded();
        assertSorted();
    }

    private static void assertSorted() throws Exception {
        onFx(() -> {
            TableView<JobRecord> table = view.get("jobsTable");
            assertFalse(table.getItems().isEmpty());
            assertSame(table.getColumns().getFirst(), table.getSortOrder().getFirst());
            assertEquals(TableColumn.SortType.DESCENDING, table.getSortOrder().getFirst().getSortType());
            int previous = Integer.MAX_VALUE;
            for (JobRecord row : table.getItems()) {
                if (row.duplicateParent() != null) continue;
                int score = Integer.parseInt(row.score());
                assertTrue(score <= previous);
                previous = score;
            }
            return null;
        });
    }
}
