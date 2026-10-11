package com.seeker.guifx;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseButton;
import javafx.stage.Popup;
import javafx.stage.Screen;

final class CopyableTextCell<S> extends TableCell<S, String> {
    private final TextField textField = new TextField();
    private Popup expandedPopup;

    private CopyableTextCell(boolean expandable) {
        textField.setEditable(false);
        textField.setFocusTraversable(true);
        textField.getStyleClass().add("copyable-cell");
        CellTextFieldWidth.fitToText(textField, this);
        setPadding(new Insets(0, 4, 0, 4));
        if (expandable) {
            textField.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    showExpandedText(event.getScreenX(), event.getScreenY());
                    event.consume();
                }
            });
        }
    }

    static <S> void install(TableColumn<S, String> column) {
        column.setCellFactory(ignored -> new CopyableTextCell<>(false));
    }

    static <S> void installExpandable(TableColumn<S, String> column) {
        column.setCellFactory(ignored -> new CopyableTextCell<>(true));
    }

    private void showExpandedText(double screenX, double screenY) {
        if (textField.getText().isEmpty() || getScene() == null) return;
        if (expandedPopup != null) expandedPopup.hide();

        Rectangle2D screen = Screen.getScreensForRectangle(screenX, screenY, 1, 1)
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        TextArea area = new TextArea(textField.getText());
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefSize(Math.min(720, screen.getWidth() - 40), 220);
        expandedPopup = new Popup();
        expandedPopup.setAutoHide(true);
        expandedPopup.setHideOnEscape(true);
        expandedPopup.getContent().add(area);
        expandedPopup.show(textField, screenX, screenY);
        area.requestFocus();
    }

    @Override
    protected void updateItem(String value, boolean empty) {
        super.updateItem(value, empty);
        if (expandedPopup != null && expandedPopup.isShowing()) expandedPopup.hide();
        if (empty) {
            textField.clear();
            setGraphic(null);
            return;
        }
        textField.setText(value == null ? "" : value);
        setGraphic(textField);
    }
}
