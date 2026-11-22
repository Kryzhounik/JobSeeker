package com.seeker.guifx;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FillLastColumnResizePolicyTest {
    @BeforeAll static void startJavaFx() { Platform.startup(() -> { }); }
    @AfterAll static void stopJavaFx() { Platform.exit(); }

    @Test void lastColumnFillsRemainderAndRetainsMinimumWidth() throws Exception {
        FxTestSupport.run(() -> {
                TableView<String> table = new TableView<>();
                TableColumn<String, String> first = new TableColumn<>("First");
                TableColumn<String, String> last = new TableColumn<>("Last");
                first.setPrefWidth(150);
                last.setPrefWidth(200);
                table.getColumns().addAll(first, last);
                FillLastColumnResizePolicy.install(table);
                Scene scene = new Scene(table, 600, 300);
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                if (last.getWidth() < 400) {
                    throw new AssertionError("Last column did not fill the available width: "
                            + last.getWidth());
                }

                table.resize(250, 300);
                table.layout();
                if (last.getWidth() < 200) {
                    throw new AssertionError("Last column lost its minimum width: "
                            + last.getWidth());
                }

                table.resize(700, 300);
                table.layout();
                table.resizeColumn(first, 40);
                table.layout();
                if (table.getWidth() - first.getWidth() - last.getWidth() > 20) {
                    throw new AssertionError("Empty space remained after column resize");
                }
                return null;
        });
    }
}
