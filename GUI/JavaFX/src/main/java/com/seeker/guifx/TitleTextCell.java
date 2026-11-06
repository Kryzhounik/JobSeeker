package com.seeker.guifx;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;

import java.util.function.Consumer;

final class TitleTextCell<S> extends TableCell<S, String> {
    private final TextField textField = new TextField();
    private final Consumer<String> addToBlacklist;
    private final java.util.function.Predicate<S> indented;
    private String contextSelection = "";

    private TitleTextCell(Consumer<String> addToBlacklist) {
        this(addToBlacklist, row -> false);
    }

    private TitleTextCell(Consumer<String> addToBlacklist, java.util.function.Predicate<S> indented) {
        this.addToBlacklist = addToBlacklist;
        this.indented = indented;
        textField.setEditable(false);
        textField.setFocusTraversable(true);
        textField.getStyleClass().addAll("copyable-cell", "title-text-cell");
        CellTextFieldWidth.fitToText(textField, this);
        textField.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                contextSelection = textField.getSelectedText();
            }
        });
        textField.setOnContextMenuRequested(event -> {
            String selected = contextSelection.isBlank()
                    ? textField.getSelectedText() : contextSelection;
            String value = selected.isBlank() ? textField.getText() : selected;
            ContextMenu menu = new ContextMenu();
            MenuItem copy = new MenuItem(selected.isBlank() ? "Copy title" : "Copy");
            copy.setStyle("-fx-text-fill: #111;");
            copy.setOnAction(action -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(value);
                Clipboard.getSystemClipboard().setContent(content);
            });
            MenuItem blacklist = new MenuItem(selected.isBlank()
                    ? "Title to blacklist" : "Selection to blacklist");
            blacklist.setStyle("-fx-text-fill: #111;");
            blacklist.setOnAction(action -> addToBlacklist.accept(value));
            menu.getItems().addAll(copy, blacklist);
            menu.show(textField, event.getScreenX(), event.getScreenY());
            contextSelection = "";
            event.consume();
        });
        setPadding(new Insets(0, 4, 0, 4));
    }

    static <S> void install(TableColumn<S, String> column, Consumer<String> addToBlacklist) {
        column.setCellFactory(ignored -> new TitleTextCell<>(addToBlacklist));
    }

    static <S> void install(TableColumn<S, String> column, Consumer<String> addToBlacklist,
                            java.util.function.Predicate<S> indented) {
        column.setCellFactory(ignored -> new TitleTextCell<>(addToBlacklist, indented));
    }

    static boolean isTitleTarget(Object target) {
        if (!(target instanceof Node node)) return false;
        while (node != null) {
            if (node.getStyleClass().contains("title-text-cell")) return true;
            node = node.getParent();
        }
        return false;
    }

    @Override protected void updateItem(String value, boolean empty) {
        super.updateItem(value, empty);
        if (empty) {
            textField.clear();
            setGraphic(null);
        } else {
            textField.setText(value == null ? "" : value);
            S row = getTableRow() == null ? null : getTableRow().getItem();
            boolean duplicate = row != null && indented.test(row);
            setPadding(new Insets(0, 4, 0, duplicate ? 20 : 4));
            if (duplicate) {
                javafx.scene.control.Label marker = new javafx.scene.control.Label("\u21b3");
                marker.setTooltip(new javafx.scene.control.Tooltip("Duplicate of the vacancy above"));
                setGraphic(new javafx.scene.layout.HBox(5, marker, textField));
            } else {
                setGraphic(textField);
            }
        }
    }
}
