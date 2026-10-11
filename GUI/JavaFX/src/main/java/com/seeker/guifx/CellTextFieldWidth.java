package com.seeker.guifx;

import javafx.beans.binding.Bindings;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

final class CellTextFieldWidth {
    private CellTextFieldWidth() { }

    static void fitToText(TextField field, TableCell<?, ?> cell) {
        field.setMinWidth(0);
        field.setMaxWidth(Region.USE_PREF_SIZE);
        field.prefWidthProperty().bind(Bindings.createDoubleBinding(() -> {
            Text measure = new Text(field.getText());
            if (field.getFont() != null) measure.setFont(field.getFont());
            double textWidth = Math.ceil(measure.getLayoutBounds().getWidth()) + 12;
            return Math.max(0, Math.min(textWidth, cell.getWidth() - 8));
        }, field.textProperty(), field.fontProperty(), cell.widthProperty()));
    }
}
