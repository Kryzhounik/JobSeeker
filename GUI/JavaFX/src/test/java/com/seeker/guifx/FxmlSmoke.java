package com.seeker.guifx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class FxmlSmoke {
    public static void main(String[] args) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(FxmlSmoke.class.getResource(
                        "/com/seeker/guifx/main-view.fxml"));
                loader.load();
                Button open = (Button) loader.getNamespace().get("openButton");
                Button eval = (Button) loader.getNamespace().get("chatEvalButton");
                Button letter = (Button) loader.getNamespace().get("letterButton");
                Button applyAction = (Button) loader.getNamespace().get("applyButton");
                var sourceActions = ((HBox) open.getParent()).getChildren();
                int index = sourceActions.indexOf(open);
                if (!sourceActions.subList(index, index + 4).equals(java.util.List.of(open, eval, letter, applyAction))
                        || letter.getOnAction() == null) {
                    throw new AssertionError("Source action order or Letter handler is incorrect");
                }
                Button batch = (Button) loader.getNamespace().get("batchButton");
                Button collect = (Button) loader.getNamespace().get("collectButton");
                Button recommendations = (Button) loader.getNamespace().get("recommendationsButton");
                Button config = (Button) loader.getNamespace().get("configButton");
                Button runs = (Button) loader.getNamespace().get("runsButton");
                Button companies = (Button) loader.getNamespace().get("companiesButton");
                Button refresh = (Button) loader.getNamespace().get("refreshButton");
                HBox statusFilters = (HBox) loader.getNamespace().get("statusFilters");
                if (statusFilters.getSpacing() < 16) {
                    throw new AssertionError("Status checkboxes are not visually separated");
                }
                javafx.scene.control.Label statusLabel = (javafx.scene.control.Label) loader.getNamespace().get("statusLabel");
                Button blackTitles = (Button) loader.getNamespace().get("blackTitlesButton");
                if (!(statusLabel.getParent() instanceof WindowStatusBar footer)
                        || footer.getStatus() != statusLabel || footer.getLeft() != blackTitles) {
                    throw new AssertionError("Main window does not use the shared status bar");
                }
                Button refilter = (Button) loader.getNamespace().get("refilterButton");
                Button refilterDetail = (Button) loader.getNamespace().get("refilterDetailButton");
                HBox collectionGroup = (HBox) batch.getParent();
                HBox refilterGroup = (HBox) refilter.getParent();
                HBox actions = (HBox) collectionGroup.getParent();
                HBox filters = (HBox) refresh.getParent();
                if (actions.getChildren().getFirst() != collectionGroup
                        || !collectionGroup.getChildren().equals(java.util.List.of(batch, collect, recommendations))
                        || recommendations.getOnAction() == null
                        || !refilterGroup.getChildren().equals(java.util.List.of(refilter, refilterDetail))
                        || !collectionGroup.getStyleClass().contains("toolbar-button-group")
                        || !refilterGroup.getStyleClass().contains("toolbar-button-group")
                        || actions.getChildren().getLast() != config
                        || actions.getChildren().indexOf(runs) + 1 != actions.getChildren().indexOf(companies)
                        || !runs.getText().equals("Runs") || runs.getOnAction() == null
                        || actions == filters || filters.getChildren().getFirst() != refresh
                        || batch.getOnAction() == null
                        || !batch.getStyleClass().containsAll(java.util.List.of("collect-button", "batch-button"))
                        || loader.getNamespace().containsKey("searchButton")) {
                    throw new AssertionError("Toolbar order or Batch action/style changed");
                }
                checkApplyIsolation(loader);
                checkDuplicateTitles();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                finished.countDown();
            }
        }));
        if (!finished.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FXML did not load within 20 seconds.");
        }
        Platform.exit();
        if (failure.get() != null) {
            throw new IllegalStateException("FXML load failed", failure.get());
        }
        System.out.println("FXML load OK");
    }

    private static void checkApplyIsolation(FXMLLoader loader) throws Exception {
        MainController controller = loader.getController();
        JobRecord job = ClientData.readResponse("""
                {"job_id":123,"source_url":"https://example.com/job","easy_apply":false}
                """, JobRecord.class);
        set(controller, "selectedJob", job);
        set(controller, "detailLoadedJobId", 123);
        set(controller, "applyRunning", true);
        var buttons = MainController.class.getDeclaredMethod("setOperationButtonsDisabled", boolean.class);
        buttons.setAccessible(true);
        buttons.invoke(controller, false);
        Button apply = (Button) loader.getNamespace().get("applyButton");
        for (String name : java.util.List.of("linkedinCheckButton", "refilterButton", "refilterDetailButton")) {
            if (((Button) loader.getNamespace().get(name)).isDisabled()) {
                throw new AssertionError("Apply disabled unrelated action: " + name);
            }
        }
        if (((javafx.scene.Node) loader.getNamespace().get("statusActions")).isDisabled()
                || !apply.isDisabled()) {
            throw new AssertionError("Apply did not isolate its own busy state");
        }
        var detail = MainController.class.getDeclaredMethod("setDetailEnabled", boolean.class);
        detail.setAccessible(true);
        detail.invoke(controller, true);
        if (!apply.isDisabled()) throw new AssertionError("Detail refresh re-enabled active Apply");
        set(controller, "applyRunning", false);
        buttons.invoke(controller, true);
        if (apply.isDisabled()) throw new AssertionError("Unrelated operation disabled Apply");
    }

    private static void set(MainController controller, String name, Object value) throws Exception {
        var field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static void checkDuplicateTitles() throws Exception {
        var table = new javafx.scene.control.TableView<JobRecord>();
        var title = new javafx.scene.control.TableColumn<JobRecord, String>("Title");
        title.setPrefWidth(350);
        title.setCellValueFactory(value -> new javafx.beans.property.ReadOnlyStringWrapper(value.getValue().title()));
        TitleTextCell.install(title, value -> { }, row -> row.duplicateParent() != null);
        table.getColumns().add(title);
        var child = ClientData.readResponse("""
                {"job_id":1,"title":"Duplicate","duplicate_parent":{"job_id":2,"title":"Parent"}}
                """, JobRecord.class);
        table.getItems().setAll(JobRows.sorted(java.util.List.of(child), null));
        var root = new javafx.scene.layout.StackPane(table);
        new javafx.scene.Scene(root, 400, 240);
        root.resize(400, 240);
        root.applyCss();
        root.layout();
        var fields = root.lookupAll(".title-text-cell").stream()
                .map(javafx.scene.control.TextField.class::cast).toList();
        var parentField = fields.stream().filter(field -> field.getText().equals("Parent")).findFirst().orElseThrow();
        var childField = fields.stream().filter(field -> field.getText().equals("Duplicate")).findFirst().orElseThrow();
        if (childField.localToScene(0, 0).getX() <= parentField.localToScene(0, 0).getX()
                || root.lookupAll(".label").stream().noneMatch(node ->
                        node instanceof javafx.scene.control.Label label && label.getText().equals("\u21b3"))) {
            throw new AssertionError("Duplicate title has no visible indentation/marker");
        }
        table.getItems().add(ClientData.readResponse("{\"job_id\":3,\"title\":\"Other\"}", JobRecord.class));
        table.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        table.setSortPolicy(view -> { JobRows.sort(view.getItems(), view.getComparator()); return true; });
        table.getSortOrder().setAll(title);
        for (int pass = 0; pass < 8; pass++) {
            table.getSelectionModel().clearAndSelect(pass % table.getItems().size());
            JobRecord selected = table.getSelectionModel().getSelectedItem();
            title.setSortType(pass % 2 == 0 ? javafx.scene.control.TableColumn.SortType.DESCENDING
                    : javafx.scene.control.TableColumn.SortType.ASCENDING);
            table.sort();
            root.layout();
            var selection = table.getSelectionModel();
            if (selection.getSelectedItem() != selected || selection.getSelectedIndices().size() != 1
                    || table.getItems().get(selection.getSelectedIndices().getFirst()) != selected) {
                throw new AssertionError("Sorting desynchronized selected item and indices");
            }
            for (var node : table.lookupAll(".table-row-cell")) {
                if (node instanceof javafx.scene.control.TableRow<?> row && !row.isEmpty()
                        && row.isSelected() != selection.isSelected(row.getIndex())) {
                    throw new AssertionError("Selected row rendering is stale");
                }
            }
        }
    }
}
