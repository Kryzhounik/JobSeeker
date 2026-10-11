package com.seeker.guifx;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

import java.util.function.Consumer;
import java.util.function.Function;

final class FilterLinkCell<S> extends TableCell<S, String> {
    private final TextField textField = new TextField();
    private final Consumer<String> action;
    private final Function<String, String> tooltipText;

    private FilterLinkCell(Consumer<String> action, Function<String, String> tooltipText) {
        this.action = action;
        this.tooltipText = tooltipText;
        textField.setEditable(false);
        textField.setFocusTraversable(true);
        textField.getStyleClass().add("filter-link-cell");
        textField.setCursor(Cursor.HAND);
        CellTextFieldWidth.fitToText(textField, this);
        textField.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1
                    && textField.getSelectedText().isEmpty()) {
                action.accept(textField.getText());
            }
        });
        setPadding(new Insets(0, 4, 0, 4));
    }

    static <S> void install(
            TableColumn<S, String> column,
            Function<S, String> value,
            Consumer<String> action
    ) {
        install(column, value, action, ignored -> "");
    }

    static <S> void install(
            TableColumn<S, String> column,
            Function<S, String> value,
            Consumer<String> action,
            Function<String, String> tooltipText
    ) {
        column.setCellValueFactory(cell -> {
            String text = value.apply(cell.getValue());
            return new javafx.beans.property.ReadOnlyStringWrapper(
                    text == null ? "" : text
            );
        });
        column.setCellFactory(ignored -> new FilterLinkCell<>(action, tooltipText));
    }

    @Override
    protected void updateItem(String value, boolean empty) {
        super.updateItem(value, empty);
        if (empty) {
            textField.clear();
            textField.setTooltip(null);
            setGraphic(null);
        } else {
            textField.setText(value == null ? "" : value);
            String description = tooltipText.apply(textField.getText());
            textField.setTooltip(description == null || description.isBlank()
                    ? null : new Tooltip(description));
            setGraphic(textField);
        }
    }
}
