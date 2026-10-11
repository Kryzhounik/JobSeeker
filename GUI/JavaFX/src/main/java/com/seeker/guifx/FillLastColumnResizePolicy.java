package com.seeker.guifx;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;

final class FillLastColumnResizePolicy
        implements Callback<TableView.ResizeFeatures, Boolean> {
    private TableColumn<?, ?> lastColumn;
    private double minimumLastWidth;

    static void install(TableView<?> table) {
        table.setColumnResizePolicy(new FillLastColumnResizePolicy());
    }

    @Override
    public Boolean call(TableView.ResizeFeatures features) {
        var columns = features.getTable().getVisibleLeafColumns();
        if (columns.isEmpty()) return true;

        TableColumn<?, ?> last = (TableColumn<?, ?>) columns.getLast();
        if (last != lastColumn) {
            lastColumn = last;
            minimumLastWidth = last.getPrefWidth();
        }
        if (features.getColumn() == last && features.getDelta() != null) {
            minimumLastWidth = Math.max(last.getMinWidth(),
                    minimumLastWidth + features.getDelta());
        }

        boolean resized = TableView.UNCONSTRAINED_RESIZE_POLICY.call(features);
        double contentWidth = features.getContentWidth();
        if (contentWidth <= 0) return resized;

        double otherWidths = columns.stream()
                .mapToDouble(column -> column == last ? 0 : ((TableColumn<?, ?>) column).getWidth())
                .sum();
        double targetWidth = Math.max(minimumLastWidth, contentWidth - otherWidths);
        if (Math.abs(last.getWidth() - targetWidth) > 0.5) {
            features.setColumnWidth(last, targetWidth);
        }
        return resized;
    }
}
