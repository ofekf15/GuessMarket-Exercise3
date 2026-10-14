package il.ac.mta.gm.ui;

import il.ac.mta.gm.dto.CloseEventResultDTO;
import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.EventMethodType;
import il.ac.mta.gm.dto.EventStatus;
import il.ac.mta.gm.dto.EventStatusDTO;
import il.ac.mta.gm.dto.OptionStatusDTO;
import il.ac.mta.gm.dto.OrderBookDetailsDTO;
import il.ac.mta.gm.dto.OrderBookOptionDTO;
import il.ac.mta.gm.dto.OrderBookPositionDTO;
import il.ac.mta.gm.dto.OrderFillDTO;
import il.ac.mta.gm.dto.OrderResultDTO;
import il.ac.mta.gm.dto.RestingOrderDTO;
import il.ac.mta.gm.dto.TradeRecordDTO;
import il.ac.mta.gm.dto.TradeResultDTO;
import il.ac.mta.gm.dto.UserSummaryDTO;
import il.ac.mta.gm.engine.api.EngineInterface;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public final class EventsController {
    @FXML private ComboBox<String> methodFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> commissionFilter;
    @FXML private TableView<EventRow> eventTable;
    @FXML private TableColumn<EventRow, String> nameColumn;
    @FXML private TableColumn<EventRow, String> statusColumn;
    @FXML private TableColumn<EventRow, String> methodColumn;
    @FXML private TableColumn<EventRow, String> commissionColumn;
    @FXML private TableColumn<EventRow, String> balanceColumn;
    @FXML private VBox detailBox;
    @FXML private Button startButton;
    @FXML private Button buyButton;
    @FXML private Button orderButton;
    @FXML private Button closeButton;

    private EngineInterface engine;
    private Runnable refreshRequest = () -> {};
    private List<EventRow> allRows = List.of();
    private List<UserSummaryDTO> users = List.of();

    @FXML
    private void initialize() {
        methodFilter.setItems(FXCollections.observableArrayList("All", "LMSR", "Order Book"));
        statusFilter.setItems(FXCollections.observableArrayList(
            "All", "Not Started", "Active", "Closed"));
        commissionFilter.setItems(FXCollections.observableArrayList(
            "All", "On Purchase", "On Close"));
        methodFilter.getSelectionModel().selectFirst();
        statusFilter.getSelectionModel().selectFirst();
        commissionFilter.getSelectionModel().selectFirst();

        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().event.name));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            UiFormatter.status(cell.getValue().status.status)));
        methodColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            UiFormatter.method(cell.getValue().event.methodType)));
        commissionColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            UiFormatter.commission(cell.getValue().event.commissionType) + " · "
                + cell.getValue().event.commissionPercent + "%"));
        balanceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            UiFormatter.number(cell.getValue().status.accountBalance)));

        methodFilter.setOnAction(event -> applyFilters());
        statusFilter.setOnAction(event -> applyFilters());
        commissionFilter.setOnAction(event -> applyFilters());
        eventTable.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, value) -> showDetails(value));
        updateActions(null);
    }

    void initializeEngine(EngineInterface engine, Runnable refreshRequest) {
        this.engine = engine;
        this.refreshRequest = refreshRequest;
    }

    void refresh(List<EventDisplayDTO> events, List<UserSummaryDTO> users) {
        Integer selectedId = selected() == null ? null : selected().event.id;
        this.users = List.copyOf(users);
        List<EventRow> rows = new ArrayList<>();
        for (EventDisplayDTO event : events) {
            rows.add(new EventRow(event, engine.getEventStatus(event.id)));
        }
        allRows = List.copyOf(rows);
        applyFilters();
        if (selectedId != null) {
            eventTable.getItems().stream()
                .filter(row -> row.event.id == selectedId)
                .findFirst().ifPresent(row -> eventTable.getSelectionModel().select(row));
        }
        if (eventTable.getSelectionModel().getSelectedItem() == null
                && !eventTable.getItems().isEmpty()) {
            eventTable.getSelectionModel().selectFirst();
        }
        showDetails(selected());
    }

    private void applyFilters() {
        if (eventTable == null) {
            return;
        }
        String method = methodFilter.getValue();
        String status = statusFilter.getValue();
        String commission = commissionFilter.getValue();
        List<EventRow> filtered = allRows.stream()
            .filter(row -> "All".equals(method)
                || UiFormatter.method(row.event.methodType).equals(method))
            .filter(row -> "All".equals(status)
                || UiFormatter.status(row.status.status).equals(status))
            .filter(row -> "All".equals(commission)
                || UiFormatter.commission(row.event.commissionType).equals(commission))
            .toList();
        eventTable.setItems(FXCollections.observableArrayList(filtered));
        if (eventTable.getSelectionModel().getSelectedItem() == null && !filtered.isEmpty()) {
            eventTable.getSelectionModel().selectFirst();
        }
        showDetails(selected());
    }

    private EventRow selected() {
        return eventTable.getSelectionModel().getSelectedItem();
    }

    private void showDetails(EventRow row) {
        detailBox.getChildren().clear();
        updateActions(row);
        if (row == null) {
            Label empty = new Label("Select an event to see market details.");
            empty.getStyleClass().add("empty-state");
            detailBox.getChildren().add(empty);
            return;
        }

        EventDisplayDTO event = row.event;
        EventStatusDTO status = row.status;
        Label title = UiComponents.section(event.name);
        Label description = new Label(event.description);
        description.setWrapText(true);
        UiComponents.add(detailBox, title, description,
            UiComponents.facts(List.of(
                pair("Event ID", Integer.toString(event.id)),
                pair("Status", UiFormatter.status(status.status)),
                pair("Method", UiFormatter.method(event.methodType)),
                pair("Commission", event.commissionPercent + "% · "
                    + UiFormatter.commission(event.commissionType)),
                pair("Event account", UiFormatter.number(status.accountBalance)),
                pair("Fees collected", UiFormatter.number(status.totalFeesCollected)),
                pair("Winner", status.winningOption == null ? "-" : status.winningOption))));

        if (event.methodType == EventMethodType.LMSR) {
            showLmsr(status, event);
        } else {
            showOrderBook(event, status);
        }
    }

    private void showLmsr(EventStatusDTO status, EventDisplayDTO event) {
        List<List<String>> options = new ArrayList<>();
        for (OptionStatusDTO option : status.options) {
            options.add(List.of(option.name, UiFormatter.number(option.currentValue),
                Integer.toString(option.sharesBought)));
        }
        UiComponents.add(detailBox, UiComponents.section("LMSR market"),
            UiComponents.facts(List.<String[]>of(pair("Liquidity b",
                event.lmsrB == null ? "-" : event.lmsrB.toString()))),
            UiComponents.table(List.of("Option", "Current price", "Purchased"), options));

        List<List<String>> trades = new ArrayList<>();
        for (TradeRecordDTO trade : status.history) {
            trades.add(List.of(trade.optionName, Integer.toString(trade.quantity),
                UiFormatter.number(trade.sharesCost), UiFormatter.number(trade.commissionPaid),
                UiFormatter.number(trade.totalPaid)));
        }
        UiComponents.add(detailBox, UiComponents.section("Trade history · newest first"),
            UiComponents.table(List.of("Option", "Qty", "Cost", "Fee", "Total"), trades));
    }

    private void showOrderBook(EventDisplayDTO event, EventStatusDTO status) {
        OrderBookDetailsDTO book = engine.getOrderBook(event.id);
        UiComponents.add(detailBox, UiComponents.section("Order Book configuration"),
            UiComponents.facts(List.of(
                pair("d", Integer.toString(book.d)),
                pair("Initial", Integer.toString(event.orderBookConfig.initial)),
                pair("Allow mint", event.orderBookConfig.allowMint ? "Yes" : "No"))));

        for (OrderBookOptionDTO option : book.options) {
            UiComponents.add(detailBox, UiComponents.section(option.optionName + " book"),
                UiComponents.facts(List.of(
                    pair("LAST", UiFormatter.optional(option.stats.last)),
                    pair("BID", UiFormatter.optional(option.stats.bid)),
                    pair("ASK", UiFormatter.optional(option.stats.ask)),
                    pair("MID", UiFormatter.optional(option.stats.mid)),
                    pair("SPREAD", UiFormatter.optional(option.stats.spread)))));
            List<List<String>> orders = new ArrayList<>();
            for (RestingOrderDTO order : option.orders) {
                orders.add(List.of(order.userName, order.side.name(),
                    Integer.toString(order.remainingQuantity), UiFormatter.number(order.price)));
            }
            detailBox.getChildren().add(UiComponents.table(
                List.of("User", "Side", "Remaining", "Limit"), orders));
        }

        List<List<String>> participants = new ArrayList<>();
        for (OrderBookPositionDTO position : book.positions) {
            if (!position.participant) {
                continue;
            }
            participants.add(List.of(position.userName, join(position.holdings),
                join(position.reservedShares), UiFormatter.number(position.reservedCash),
                UiFormatter.number(position.buyPrincipal), UiFormatter.number(position.saleProceeds),
                UiFormatter.number(position.commissionPaid),
                position.profitLoss == null ? "-" : UiFormatter.number(position.profitLoss)));
        }
        UiComponents.add(detailBox, UiComponents.section("Participants"),
            UiComponents.table(List.of("User", "Holdings", "Reserved shares", "Reserved cash",
                "Bought", "Sold", "Fees", "P/L"), participants));
    }

    private static String join(List<Integer> values) {
        return values.stream().map(String::valueOf).reduce((a, b) -> a + " / " + b).orElse("-");
    }

    private static String[] pair(String key, String value) {
        return new String[]{key, value};
    }

    private void updateActions(EventRow row) {
        boolean present = row != null;
        startButton.setDisable(!present || row.status.status != EventStatus.NOT_STARTED);
        buyButton.setDisable(!present || row.status.status != EventStatus.ACTIVE
            || row.event.methodType != EventMethodType.LMSR);
        orderButton.setDisable(!present || row.status.status != EventStatus.ACTIVE
            || row.event.methodType != EventMethodType.ORDER_BOOK);
        closeButton.setDisable(!present || row.status.status != EventStatus.ACTIVE);
    }

    @FXML private void onStart() {
        EventRow row = selected();
        if (row == null) return;
        ActionDialogs.start(row.event, users).ifPresent(user -> execute("Start failed", () -> {
            engine.startEvent(row.event.id, user);
            UiAlerts.info("Event started", row.event.name + " is now active.");
        }));
    }

    @FXML private void onBuy() {
        EventRow row = selected();
        if (row == null) return;
        ActionDialogs.buy(row.event, users).ifPresent(request -> execute("Purchase failed", () -> {
            TradeResultDTO result = engine.buyShares(row.event.id, request.userName(),
                request.optionIndex(), request.quantity());
            UiAlerts.info("Purchase completed", "Shares cost: " + UiFormatter.number(result.sharesCost)
                + "\nCommission: " + UiFormatter.number(result.commissionPaid)
                + "\nTotal paid: " + UiFormatter.number(result.totalPaid));
        }));
    }

    @FXML private void onOrder() {
        EventRow row = selected();
        if (row == null) return;
        ActionDialogs.order(row.event, users).ifPresent(request -> execute("Order failed", () -> {
            OrderResultDTO result = engine.placeOrder(row.event.id, request.userName(),
                request.optionIndex(), request.side(), request.quantity(), request.limitPrice());
            StringBuilder message = new StringBuilder()
                .append("Submitted: ").append(result.submittedQuantity)
                .append("\nExecuted: ").append(result.executedQuantity)
                .append("\nResting: ").append(result.restingQuantity)
                .append("\nPrincipal paid: ").append(UiFormatter.number(result.principalPaid))
                .append("\nPrincipal received: ").append(UiFormatter.number(result.principalReceived))
                .append("\nCommission: ").append(UiFormatter.number(result.commissionPaid));
            for (OrderFillDTO fill : result.fills) {
                message.append("\n").append(fill.type).append(" · ")
                    .append(fill.quantity).append(" @ ").append(UiFormatter.number(fill.price));
            }
            UiAlerts.info("Order accepted", message.toString());
        }));
    }

    @FXML private void onClose() {
        EventRow row = selected();
        if (row == null) return;
        ActionDialogs.close(row.event, users).ifPresent(request -> execute("Resolve failed", () -> {
            CloseEventResultDTO result = engine.closeEvent(row.event.id, request.userName(),
                request.winningOptionIndex());
            UiAlerts.info("Event resolved", "Winner: " + result.winningOptionName
                + "\nGross payout: " + UiFormatter.number(result.grossPayout)
                + "\nCommission: " + UiFormatter.number(result.commissionPaid)
                + "\nNet payout: " + UiFormatter.number(result.netPayout));
        }));
    }

    private void execute(String errorTitle, Runnable operation) {
        try {
            operation.run();
            refreshRequest.run();
        } catch (RuntimeException error) {
            UiAlerts.showError(errorTitle, error);
        }
    }

    void verifySmokeSelections() {
        selectMethod(EventMethodType.LMSR);
        selectMethod(EventMethodType.ORDER_BOOK);
    }

    private void selectMethod(EventMethodType method) {
        EventRow row = allRows.stream().filter(value -> value.event.methodType == method)
            .findFirst().orElseThrow(() -> new IllegalStateException("Smoke XML lacks " + method));
        methodFilter.getSelectionModel().select("All");
        eventTable.getSelectionModel().select(row);
        showDetails(row);
    }

    public static final class EventRow {
        final EventDisplayDTO event;
        final EventStatusDTO status;

        EventRow(EventDisplayDTO event, EventStatusDTO status) {
            this.event = event;
            this.status = status;
        }
    }
}
