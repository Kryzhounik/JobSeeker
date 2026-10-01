package com.seeker.guifx;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import java.util.function.Consumer;
import java.util.function.Function;

final class FilterLinkCell<S> extends TableCell<S, String> {
    private final TextField textField = new TextField();
    private final Consumer<String> action;

    private FilterLinkCell(Consumer<String> action) {
        this.action = action;
        textField.setEditable(false);
        textField.setFocusTraversable(true);
        textField.getStyleClass().add("filter-link-cell");
        textField.setCursor(Cursor.HAND);
        textField.setMaxWidth(Region.USE_COMPUTED_SIZE);
        textField.prefWidthProperty().bind(widthProperty().subtract(8));
        textField.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1
                    && textField.getSelectedText().isEmpty()
                    && clickIsOnText(event.getX())) {
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
        column.setCellValueFactory(cell -> {
            String text = value.apply(cell.getValue());
            return new javafx.beans.property.ReadOnlyStringWrapper(
                    text == null ? "" : text
            );
        });
        column.setCellFactory(ignored -> new FilterLinkCell<>(action));
    }

    private boolean clickIsOnText(double x) {
        Text measure = new Text(textField.getText());
        measure.setFont(textField.getFont());
        return x <= measure.getLayoutBounds().getWidth() + 12;
    }

    @Override
    protected void updateItem(String value, boolean empty) {
        super.updateItem(value, empty);
        if (empty) {
            textField.clear();
            setGraphic(null);
        } else {
            textField.setText(value == null ? "" : value);
            setGraphic(textField);
        }
    }
}
