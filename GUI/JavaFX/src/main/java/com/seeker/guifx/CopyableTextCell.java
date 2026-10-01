package com.seeker.guifx;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.geometry.Insets;

final class CopyableTextCell<S> extends TableCell<S, String> {
    private final TextField textField = new TextField();

    CopyableTextCell() {
        textField.setEditable(false);
        textField.setFocusTraversable(true);
        textField.getStyleClass().add("copyable-cell");
        textField.setMaxWidth(Region.USE_COMPUTED_SIZE);
        textField.prefWidthProperty().bind(widthProperty().subtract(8));
        setPadding(new Insets(0, 4, 0, 4));
    }

    static <S> void install(TableColumn<S, String> column) {
        column.setCellFactory(ignored -> new CopyableTextCell<>());
    }

    @Override
    protected void updateItem(String value, boolean empty) {
        super.updateItem(value, empty);
        if (empty) {
            textField.clear();
            setGraphic(null);
            return;
        }
        textField.setText(value == null ? "" : value);
        setGraphic(textField);
    }
}
