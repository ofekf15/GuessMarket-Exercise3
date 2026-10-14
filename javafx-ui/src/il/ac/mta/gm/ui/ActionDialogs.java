package il.ac.mta.gm.ui;

import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.OrderSide;
import il.ac.mta.gm.dto.UserSummaryDTO;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Optional;

public final class ActionDialogs {
    private ActionDialogs() {}

    public record BuyRequest(String userName, int optionIndex, int quantity) {}
    public record OrderRequest(String userName, int optionIndex, OrderSide side,
                               int quantity, double limitPrice) {}
    public record CloseRequest(String userName, int winningOptionIndex) {}

    public static Optional<String> start(EventDisplayDTO event, List<UserSummaryDTO> users) {
        Dialog<String> dialog = base("Start Event", "Start “" + event.name + "”?");
        ComboBox<String> actor = userBox(users);
        dialog.getDialogPane().setContent(form(row("Acting user", actor)));
        dialog.setResultConverter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE
            ? actor.getValue() : null);
        disableWhenEmpty(dialog, actor);
        return dialog.showAndWait();
    }

    public static Optional<BuyRequest> buy(EventDisplayDTO event, List<UserSummaryDTO> users) {
        Dialog<BuyRequest> dialog = base("Buy LMSR Shares", event.name);
        ComboBox<String> actor = userBox(users);
        ComboBox<String> option = optionBox(event);
        Spinner<Integer> quantity = integerSpinner();
        dialog.getDialogPane().setContent(form(
            row("Acting user", actor), row("Option", option), row("Quantity", quantity)));
        dialog.setResultConverter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE
            ? new BuyRequest(actor.getValue(), option.getSelectionModel().getSelectedIndex(),
                quantity.getValue()) : null);
        disableWhenEmpty(dialog, actor);
        return dialog.showAndWait();
    }

    public static Optional<OrderRequest> order(EventDisplayDTO event,
                                                List<UserSummaryDTO> users) {
        Dialog<OrderRequest> dialog = base("Place Order", event.name);
        ComboBox<String> actor = userBox(users);
        ComboBox<String> option = optionBox(event);
        ComboBox<OrderSide> side = new ComboBox<>(FXCollections.observableArrayList(OrderSide.values()));
        side.getSelectionModel().select(OrderSide.BUY);
        Spinner<Integer> quantity = integerSpinner();
        int d = event.orderBookConfig.d;
        Spinner<Double> price = new Spinner<>(
            new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, d, d / 2.0, 0.01));
        price.setEditable(true);
        Label range = new Label("Valid range: 0 to " + d);
        range.getStyleClass().add("hint");
        dialog.getDialogPane().setContent(form(
            row("Acting user", actor), row("Option", option), row("Side", side),
            row("Quantity", quantity), row("Limit price", price), row("", range)));
        dialog.setResultConverter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE
            ? new OrderRequest(actor.getValue(), option.getSelectionModel().getSelectedIndex(),
                side.getValue(), quantity.getValue(), price.getValue()) : null);
        disableWhenEmpty(dialog, actor);
        return dialog.showAndWait();
    }

    public static Optional<CloseRequest> close(EventDisplayDTO event,
                                                List<UserSummaryDTO> users) {
        Dialog<CloseRequest> dialog = base("Resolve Event", "Choose the winning option for “"
            + event.name + "”. This cannot be undone.");
        ComboBox<String> actor = userBox(users);
        ComboBox<String> option = optionBox(event);
        dialog.getDialogPane().setContent(form(
            row("Acting user", actor), row("Winning option", option)));
        dialog.setResultConverter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE
            ? new CloseRequest(actor.getValue(), option.getSelectionModel().getSelectedIndex()) : null);
        disableWhenEmpty(dialog, actor);
        return dialog.showAndWait();
    }

    private static <T> Dialog<T> base(String title, String header) {
        Dialog<T> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        return dialog;
    }

    private static ComboBox<String> userBox(List<UserSummaryDTO> users) {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(
            users.stream().map(user -> user.name).toList()));
        box.setMaxWidth(Double.MAX_VALUE);
        if (!box.getItems().isEmpty()) {
            box.getSelectionModel().selectFirst();
        }
        return box;
    }

    private static ComboBox<String> optionBox(EventDisplayDTO event) {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(event.optionNames));
        box.setMaxWidth(Double.MAX_VALUE);
        box.getSelectionModel().selectFirst();
        return box;
    }

    private static Spinner<Integer> integerSpinner() {
        Spinner<Integer> spinner = new Spinner<>(1, Integer.MAX_VALUE, 1);
        spinner.setEditable(true);
        return spinner;
    }

    private static GridPane form(Node[]... rows) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");
        for (int i = 0; i < rows.length; i++) {
            grid.addRow(i, rows[i]);
        }
        return grid;
    }

    private static Node[] row(String label, Node control) {
        return new Node[]{new Label(label), control};
    }

    private static void disableWhenEmpty(Dialog<?> dialog, ComboBox<String> actor) {
        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(actor.valueProperty().isNull());
    }
}
