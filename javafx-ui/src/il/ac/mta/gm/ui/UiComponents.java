package il.ac.mta.gm.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public final class UiComponents {
    private UiComponents() {}

    public static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    public static GridPane facts(List<String[]> rows) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("facts-grid");
        for (int i = 0; i < rows.size(); i++) {
            Label key = new Label(rows.get(i)[0]);
            key.getStyleClass().add("fact-key");
            Label value = new Label(rows.get(i)[1]);
            value.setWrapText(true);
            grid.addRow(i, key, value);
        }
        return grid;
    }

    public static TableView<List<String>> table(List<String> headers,
                                                 List<List<String>> rows) {
        TableView<List<String>> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No data"));
        table.setPrefHeight(Math.min(260, 44 + rows.size() * 30));
        for (int index = 0; index < headers.size(); index++) {
            final int columnIndex = index;
            TableColumn<List<String>, String> column = new TableColumn<>(headers.get(index));
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().size() > columnIndex ? cell.getValue().get(columnIndex) : ""));
            table.getColumns().add(column);
        }
        VBox.setVgrow(table, Priority.SOMETIMES);
        return table;
    }

    public static void add(VBox box, Node... nodes) {
        box.getChildren().addAll(nodes);
    }
}
