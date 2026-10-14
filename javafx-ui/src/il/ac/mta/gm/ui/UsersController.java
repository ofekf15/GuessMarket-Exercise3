package il.ac.mta.gm.ui;

import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.LmsrParticipationDTO;
import il.ac.mta.gm.dto.OrderBookPositionDTO;
import il.ac.mta.gm.dto.OrderFillDTO;
import il.ac.mta.gm.dto.UserSummaryDTO;
import il.ac.mta.gm.dto.UserTradeRecordDTO;
import il.ac.mta.gm.engine.api.EngineInterface;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UsersController {
    @FXML private TableView<UserSummaryDTO> userTable;
    @FXML private TableColumn<UserSummaryDTO, String> userNameColumn;
    @FXML private TableColumn<UserSummaryDTO, String> userBalanceColumn;
    @FXML private TableColumn<UserSummaryDTO, String> userMmColumn;
    @FXML private VBox userDetailBox;

    private EngineInterface engine;
    private Map<Integer, EventDisplayDTO> eventsById = Map.of();

    @FXML
    private void initialize() {
        userNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name));
        userBalanceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            UiFormatter.number(cell.getValue().balance)));
        userMmColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
            cell.getValue().marketMakerEventIds.stream().map(String::valueOf)
                .reduce((a, b) -> a + ", " + b).orElse("-")));
        userTable.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, value) -> showDetails(value));
        showDetails(null);
    }

    void initializeEngine(EngineInterface engine) {
        this.engine = engine;
    }

    void refresh(List<UserSummaryDTO> users, List<EventDisplayDTO> events) {
        String selectedName = userTable.getSelectionModel().getSelectedItem() == null ? null
            : userTable.getSelectionModel().getSelectedItem().name;
        Map<Integer, EventDisplayDTO> eventMap = new LinkedHashMap<>();
        for (EventDisplayDTO event : events) {
            eventMap.put(event.id, event);
        }
        eventsById = Map.copyOf(eventMap);
        userTable.setItems(FXCollections.observableArrayList(users));
        if (selectedName != null) {
            users.stream().filter(user -> user.name.equals(selectedName)).findFirst()
                .ifPresent(user -> userTable.getSelectionModel().select(user));
        }
        if (userTable.getSelectionModel().getSelectedItem() == null && !users.isEmpty()) {
            userTable.getSelectionModel().selectFirst();
        }
        showDetails(userTable.getSelectionModel().getSelectedItem());
    }

    private void showDetails(UserSummaryDTO user) {
        userDetailBox.getChildren().clear();
        if (user == null) {
            Label empty = new Label("Select a user to see balances and participation.");
            empty.getStyleClass().add("empty-state");
            userDetailBox.getChildren().add(empty);
            return;
        }
        String mmEvents = user.marketMakerEventIds.stream()
            .map(id -> eventName(id) + " (#" + id + ")")
            .reduce((a, b) -> a + ", " + b).orElse("-");
        UiComponents.add(userDetailBox, UiComponents.section(user.name),
            UiComponents.facts(List.of(
                pair("Current balance", UiFormatter.number(user.balance)),
                pair("Market Maker for", mmEvents))));

        UiComponents.add(userDetailBox, UiComponents.section("LMSR participation"));
        if (user.participations.isEmpty()) {
            userDetailBox.getChildren().add(new Label("No LMSR participation."));
        }
        for (LmsrParticipationDTO participation : user.participations) {
            List<List<String>> holdings = new ArrayList<>();
            for (int i = 0; i < participation.optionNames.size(); i++) {
                holdings.add(List.of(participation.optionNames.get(i),
                    Integer.toString(participation.holdings.get(i))));
            }
            UiComponents.add(userDetailBox, UiComponents.section(participation.eventName),
                UiComponents.facts(List.of(
                    pair("Status", UiFormatter.status(participation.eventStatus)),
                    pair("Amount paid", UiFormatter.number(participation.amountPaid)),
                    pair("Commission paid", UiFormatter.number(participation.totalCommissionPaid)),
                    pair("Winner", participation.winningOption == null ? "-" : participation.winningOption),
                    pair("Gross payout", UiFormatter.number(participation.grossPayout)),
                    pair("Closing commission", UiFormatter.number(participation.closingCommission)),
                    pair("Net payout", UiFormatter.number(participation.netPayout)))),
                UiComponents.table(List.of("Option", "Holdings"), holdings));
            List<List<String>> trades = new ArrayList<>();
            for (UserTradeRecordDTO trade : participation.tradeHistory) {
                trades.add(List.of(trade.optionName, Integer.toString(trade.quantity),
                    UiFormatter.number(trade.sharesCost), UiFormatter.number(trade.commissionPaid),
                    UiFormatter.number(trade.totalPaid)));
            }
            userDetailBox.getChildren().add(UiComponents.table(
                List.of("Option", "Qty", "Cost", "Fee", "Total"), trades));
        }

        UiComponents.add(userDetailBox, UiComponents.section("Order Book participation"));
        if (user.orderBookPositions.stream().noneMatch(position -> position.participant)) {
            userDetailBox.getChildren().add(new Label("No Order Book participation."));
        }
        for (OrderBookPositionDTO position : user.orderBookPositions) {
            if (!position.participant) {
                continue;
            }
            UiComponents.add(userDetailBox, UiComponents.section(position.eventName),
                UiComponents.facts(List.of(
                    pair("Holdings", join(position.holdings)),
                    pair("Reserved shares", join(position.reservedShares)),
                    pair("Reserved cash", UiFormatter.number(position.reservedCash)),
                    pair("Buy principal", UiFormatter.number(position.buyPrincipal)),
                    pair("Sales proceeds", UiFormatter.number(position.saleProceeds)),
                    pair("Commission paid", UiFormatter.number(position.commissionPaid)),
                    pair("Settlement received", UiFormatter.number(position.settlementReceived)),
                    pair("Profit / loss", position.profitLoss == null ? "-"
                        : UiFormatter.number(position.profitLoss)))));
            List<List<String>> executions = new ArrayList<>();
            for (OrderFillDTO fill : position.executions) {
                executions.add(List.of(fill.type.name(), Integer.toString(fill.optionIndex),
                    fill.side.name(), Integer.toString(fill.quantity),
                    UiFormatter.number(fill.price), UiFormatter.number(fill.principal),
                    UiFormatter.number(fill.commission),
                    fill.counterparty == null ? "-" : fill.counterparty));
            }
            userDetailBox.getChildren().add(UiComponents.table(
                List.of("Type", "Option", "Side", "Qty", "Price", "Principal", "Fee", "Other"),
                executions));
        }
    }

    private String eventName(int eventId) {
        EventDisplayDTO event = eventsById.get(eventId);
        return event == null ? "Event" : event.name;
    }

    private static String join(List<Integer> values) {
        return values.stream().map(String::valueOf).reduce((a, b) -> a + " / " + b).orElse("-");
    }

    private static String[] pair(String key, String value) {
        return new String[]{key, value};
    }

    void verifySmokeSelection() {
        if (userTable.getItems().isEmpty()) {
            throw new IllegalStateException("Smoke XML did not populate users");
        }
        userTable.getSelectionModel().selectFirst();
        showDetails(userTable.getSelectionModel().getSelectedItem());
    }
}
