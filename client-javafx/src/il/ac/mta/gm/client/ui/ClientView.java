package il.ac.mta.gm.client.ui;

import com.google.gson.Gson;
import il.ac.mta.gm.client.http.ApiClient;
import il.ac.mta.gm.client.http.ApiException;
import il.ac.mta.gm.client.http.ClientPoller;
import il.ac.mta.gm.client.http.ClientSnapshot;
import il.ac.mta.gm.dto.AccountTransactionDTO;
import il.ac.mta.gm.dto.EventMethodType;
import il.ac.mta.gm.dto.EventStatus;
import il.ac.mta.gm.dto.Exercise3EventDTO;
import il.ac.mta.gm.dto.Exercise3EventDetailsDTO;
import il.ac.mta.gm.dto.Exercise3LmsrParticipationDTO;
import il.ac.mta.gm.dto.Exercise3OrderBookDTO;
import il.ac.mta.gm.dto.Exercise3OrderBookPositionDTO;
import il.ac.mta.gm.dto.Exercise3UserDTO;
import il.ac.mta.gm.dto.Exercise3UserDetailsDTO;
import il.ac.mta.gm.dto.OrderBookOptionDTO;
import il.ac.mta.gm.dto.OrderSide;
import il.ac.mta.gm.dto.RestingOrderDTO;
import il.ac.mta.gm.dto.TradeRecordDTO;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** All UI state lives here; it sees DTOs and an HTTP service, never Engine/domain. */
public final class ClientView implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private final Stage stage;
    private final ApiClient api;
    private final ActionRunner actions = new ActionRunner(Platform::runLater);
    private final AtomicReference<String> selectedEventName = new AtomicReference<>();
    private final List<Button> actionButtons = new ArrayList<>();
    private ClientPoller poller;
    private int sessionGeneration;
    private String currentUser;
    private Scene scene;
    private Label status;
    private Label balance;
    private ProgressIndicator progress;
    private TextField loginName;
    private Button loginButton;
    private TabPane tabs;
    private ListView<Exercise3EventDTO> eventList;
    private ListView<Exercise3UserDTO> publicUsers;
    private ComboBox<String> methodFilter;
    private ComboBox<String> statusFilter;
    private ComboBox<String> commissionFilter;
    private VBox detailFacts;
    private TextArea orderBookText;
    private ComboBox<OptionChoice> buyOption;
    private ComboBox<OptionChoice> winnerOption;
    private ComboBox<OptionChoice> orderOption;
    private ComboBox<OrderSide> orderSide;
    private TextField buyQuantity;
    private TextField orderQuantity;
    private TextField orderPrice;
    private Button startButton;
    private Button buyButton;
    private Button orderButton;
    private Button closeButton;
    private TextArea ownPositions;
    private TextArea ownLedger;
    private Label ownHeader;
    private List<Exercise3EventDTO> allEvents = List.of();
    private String eventSignature = "";
    private String detailSignature = "";
    private String usersSignature = "";
    private String ownSignature = "";
    private Exercise3EventDetailsDTO currentDetail;
    private Exercise3OrderBookDTO currentBook;

    public ClientView(Stage stage, ApiClient api) {
        this.stage = stage;
        this.api = api;
    }

    public void show() {
        stage.setTitle("Guess Market Exercise 3");
        stage.setResizable(true);
        stage.setMinWidth(600);
        stage.setMinHeight(440);
        scene = new Scene(new BorderPane(), 1140, 760);
        var css = getClass().getResource("/il/ac/mta/gm/client/ui/guess-market.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        showLogin();
        stage.show();
        stage.setOnCloseRequest(event -> close());
    }

    private void showLogin() {
        actionButtons.clear();
        loginName = new TextField();
        loginName.setPromptText("Username");
        loginName.setMaxWidth(340);
        loginButton = button("Log in", () -> login(loginName.getText()));
        loginName.setOnAction(event -> loginButton.fire());
        status = new Label("Enter a unique username. No password is needed.");
        status.getStyleClass().add("muted-label");
        progress = new ProgressIndicator();
        progress.setPrefSize(26, 26);
        progress.setVisible(false);
        VBox card = new VBox(15, title("Guess Market"), new Label("Exercise 3 · client/server"),
            loginName, new HBox(10, loginButton, progress), status);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(430);
        BorderPane root = new BorderPane(card);
        BorderPane.setAlignment(card, Pos.CENTER);
        scene.setRoot(root);
        Platform.runLater(loginName::requestFocus);
    }

    private void login(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) { message("Enter a username."); return; }
        submit(() -> api.login(name), result -> {
            currentUser = result.user.name;
            sessionGeneration++;
            buildMain();
            startPolling();
        });
    }

    private void buildMain() {
        actionButtons.clear();
        eventSignature = detailSignature = usersSignature = ownSignature = "";
        allEvents = List.of();
        selectedEventName.set(null);
        Label user = new Label("Signed in: " + currentUser);
        balance = new Label("Balance: …");
        Button upload = button("Upload XML", this::chooseUpload);
        Button deposit = button("Deposit", this::deposit);
        Button logout = button("Log out", this::logout);
        FlowPane header = new FlowPane(12, 8, title("Guess Market"), user, balance,
            deposit, upload, logout);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");
        progress = new ProgressIndicator();
        progress.setPrefSize(22, 22);
        progress.setVisible(false);
        status = new Label("Connected. Waiting for server data…");
        status.getStyleClass().add("muted-label");
        HBox footer = new HBox(8, progress, status);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 14, 8, 14));
        tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(new Tab("Events", buildEvents()), new Tab("My Account", buildUsers()));
        BorderPane root = new BorderPane(tabs);
        root.setTop(header);
        root.setBottom(footer);
        scene.setRoot(root);
    }

    private SplitPane buildEvents() {
        methodFilter = filter("All", "LMSR", "ORDER_BOOK");
        statusFilter = filter("All", "NOT_STARTED", "ACTIVE", "CLOSED");
        commissionFilter = filter("All", "ON_PURCHASE", "ON_CLOSE");
        methodFilter.setOnAction(event -> updateEventList());
        statusFilter.setOnAction(event -> updateEventList());
        commissionFilter.setOnAction(event -> updateEventList());
        FlowPane filters = new FlowPane(7, 7, new Label("Method"), methodFilter, new Label("Status"),
            statusFilter, new Label("Commission"), commissionFilter);
        filters.setPadding(new Insets(8));
        eventList = new ListView<>();
        eventList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Exercise3EventDTO item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name + "  ·  " + item.status
                    + "  ·  " + item.methodType + "  ·  " + item.commissionPercentage
                    + "% " + item.commissionType + "  ·  "
                    + money(item.eventAccountBalance) + "  ·  MM " + item.marketMakerName);
            }
        });
        eventList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (old != null && selected != null && old.name.equals(selected.name)) return;
            selectedEventName.set(selected == null ? null : selected.name);
            detailSignature = "";
            if (selected == null) clearDetail();
            if (poller != null) poller.refreshNow();
        });
        VBox left = new VBox(8, filters, eventList);
        VBox.setVgrow(eventList, Priority.ALWAYS);
        left.setMinWidth(220);

        detailFacts = new VBox(7);
        detailFacts.getStyleClass().add("detail-pane");
        buyOption = new ComboBox<>();
        buyQuantity = new TextField("1");
        buyQuantity.setPromptText("Shares");
        buyButton = button("Buy LMSR shares", this::buy);
        startButton = button("Start event", this::startEvent);
        winnerOption = new ComboBox<>();
        closeButton = button("Close event", this::closeEvent);
        orderOption = new ComboBox<>();
        orderSide = new ComboBox<>(FXCollections.observableArrayList(OrderSide.BUY, OrderSide.SELL));
        orderSide.setValue(OrderSide.BUY);
        orderQuantity = new TextField("1");
        orderPrice = new TextField("0.50");
        orderButton = button("Place order", this::placeOrder);
        HBox buyRow = new HBox(8, new Label("Option"), buyOption,
            new Label("Quantity"), buyQuantity, buyButton);
        buyRow.setAlignment(Pos.CENTER_LEFT);
        HBox orderRow = new HBox(8, orderOption, orderSide,
            new Label("Quantity"), orderQuantity, new Label("Price"), orderPrice, orderButton);
        orderRow.setAlignment(Pos.CENTER_LEFT);
        HBox closeRow = new HBox(8, winnerOption, closeButton);
        closeRow.setAlignment(Pos.CENTER_LEFT);
        orderBookText = new TextArea();
        orderBookText.setEditable(false);
        orderBookText.setWrapText(false);
        orderBookText.setPrefRowCount(14);
        VBox right = new VBox(14, detailFacts, startButton, buyRow, orderRow,
            closeRow, orderBookText);
        right.setPadding(new Insets(12));
        right.setMinWidth(280);
        ScrollPane details = new ScrollPane(right);
        details.setFitToWidth(true);
        details.setPannable(true);
        SplitPane split = new SplitPane(left, details);
        split.setDividerPositions(.36);
        clearDetail();
        return split;
    }

    private SplitPane buildUsers() {
        ownHeader = new Label("Waiting for account data…");
        ownHeader.getStyleClass().add("section-title");
        ownPositions = new TextArea();
        ownPositions.setEditable(false);
        ownPositions.setWrapText(true);
        ownPositions.setPrefRowCount(14);
        ownLedger = new TextArea();
        ownLedger.setEditable(false);
        ownLedger.setWrapText(true);
        ownLedger.setPrefRowCount(14);
        VBox mine = new VBox(10, ownHeader, new Label("Participations and holdings"),
            ownPositions, new Label("Transaction ledger (newest first)"), ownLedger);
        mine.setPadding(new Insets(14));
        ScrollPane mineScroll = new ScrollPane(mine);
        mineScroll.setFitToWidth(true);
        publicUsers = new ListView<>();
        publicUsers.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Exercise3UserDTO item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name + " · balance "
                    + money(item.balance) + (item.marketMaker ? " · MM" : ""));
            }
        });
        VBox others = new VBox(8, section("Public users (summary only)"), publicUsers);
        others.setPadding(new Insets(14));
        VBox.setVgrow(publicUsers, Priority.ALWAYS);
        SplitPane split = new SplitPane(mineScroll, others);
        split.setDividerPositions(.68);
        return split;
    }

    private void startPolling() {
        int generation = sessionGeneration;
        poller = new ClientPoller(api, selectedEventName::get,
            snapshot -> Platform.runLater(() -> {
                if (currentUser != null && generation == sessionGeneration) apply(snapshot);
            }), error -> Platform.runLater(() -> {
                if (currentUser != null && generation == sessionGeneration) {
                    if (error instanceof ApiException apiError && apiError.status() == 401) {
                        poller.close(); poller = null;
                        currentUser = null;
                        sessionGeneration++;
                        showLogin();
                        message("Session expired. Please log in again.");
                    } else message("Connection interrupted; retrying automatically.");
                }
            }));
        poller.start();
    }

    private void apply(ClientSnapshot snapshot) {
        balance.setText("Balance: " + money(snapshot.me().balance));
        String nextEvents = GSON.toJson(snapshot.events());
        if (!eventSignature.equals(nextEvents)) {
            eventSignature = nextEvents;
            allEvents = List.copyOf(snapshot.events());
            updateEventList();
        }
        String nextUsers = GSON.toJson(snapshot.users());
        if (!usersSignature.equals(nextUsers)) {
            usersSignature = nextUsers;
            String selected = publicUsers.getSelectionModel().getSelectedItem() == null ? null
                : publicUsers.getSelectionModel().getSelectedItem().name;
            boolean sameNames = publicUsers.getItems().size() == snapshot.users().size();
            if (sameNames) for (int i = 0; i < snapshot.users().size(); i++) {
                if (!publicUsers.getItems().get(i).name.equals(snapshot.users().get(i).name)) {
                    sameNames = false; break;
                }
            }
            if (sameNames) {
                for (int i = 0; i < snapshot.users().size(); i++)
                    publicUsers.getItems().set(i, snapshot.users().get(i));
                publicUsers.refresh();
            } else {
                publicUsers.getItems().setAll(snapshot.users());
                if (selected != null) publicUsers.getItems().stream()
                    .filter(user -> user.name.equals(selected)).findFirst()
                    .ifPresent(user -> publicUsers.getSelectionModel().select(user));
            }
        }
        String nextOwn = GSON.toJson(snapshot.me()) + GSON.toJson(snapshot.ledger());
        if (!ownSignature.equals(nextOwn)) {
            ownSignature = nextOwn;
            renderOwn(snapshot.me(), snapshot.ledger());
        }
        Exercise3EventDetailsDTO detail = snapshot.selectedEvent();
        if (detail != null && detail.name.equals(selectedEventName.get())) {
            String nextDetail = GSON.toJson(detail) + GSON.toJson(snapshot.selectedOrderBook());
            if (!detailSignature.equals(nextDetail)) {
                detailSignature = nextDetail;
                renderDetail(detail, snapshot.selectedOrderBook());
            }
        }
        if (status.getText().startsWith("Connection interrupted")) message("Connected.");
    }

    private void updateEventList() {
        if (eventList == null) return;
        String selected = selectedEventName.get();
        List<Exercise3EventDTO> filtered = allEvents.stream().filter(item ->
            matches(methodFilter.getValue(), item.methodType.name())
            && matches(statusFilter.getValue(), item.status.name())
            && matches(commissionFilter.getValue(), item.commissionType == null
                ? "All" : item.commissionType.name()))
            .toList();
        String old = eventList.getItems().stream().map(event -> event.name).toList().toString();
        String fresh = filtered.stream().map(event -> event.name).toList().toString();
        if (old.equals(fresh)) {
            // Replace rows only if status/method text changed, never on identical pulls.
            if (eventList.getItems().size() == filtered.size()) {
                boolean same = true;
                for (int i = 0; i < filtered.size(); i++) {
                    var oldRow = eventList.getItems().get(i);
                    var newRow = filtered.get(i);
                    if (oldRow.status != newRow.status || oldRow.methodType != newRow.methodType
                        || oldRow.eventAccountBalance != newRow.eventAccountBalance
                        || !java.util.Objects.equals(oldRow.marketMakerName, newRow.marketMakerName)
                        || oldRow.commissionType != newRow.commissionType) {
                        same = false; break;
                    }
                }
                if (same) return;
                for (int i = 0; i < filtered.size(); i++) eventList.getItems().set(i, filtered.get(i));
                eventList.refresh();
                return;
            }
        }
        eventList.getItems().setAll(filtered);
        if (selected != null) filtered.stream().filter(item -> item.name.equals(selected))
            .findFirst().ifPresent(item -> eventList.getSelectionModel().select(item));
    }

    private static boolean matches(String filter, String value) {
        return filter == null || "All".equals(filter) || "All".equals(value)
            || filter.equals(value);
    }

    private void renderDetail(Exercise3EventDetailsDTO detail, Exercise3OrderBookDTO book) {
        currentDetail = detail;
        currentBook = book;
        detailFacts.getChildren().clear();
        detailFacts.getChildren().add(section(detail.name));
        detailFacts.getChildren().add(new Label(detail.description));
        detailFacts.getChildren().add(new Label("Status: " + detail.status
            + "   Method: " + detail.methodType + "   MM: " + detail.marketMakerName));
        detailFacts.getChildren().add(new Label("Commission: " + detail.commissionPercentage
            + "% " + detail.commissionType + "   Event account: "
            + money(detail.eventAccountBalance)));
        if (detail.winningOptionName != null)
            detailFacts.getChildren().add(new Label("Winner: " + detail.winningOptionName));
        GridPane optionGrid = new GridPane();
        optionGrid.setHgap(18); optionGrid.setVgap(5);
        optionGrid.addRow(0, new Label("#"), new Label("Option"),
            new Label("LMSR price"), new Label("q"));
        for (int i = 0; i < detail.options.size(); i++) {
            var option = detail.options.get(i);
            optionGrid.addRow(i + 1, new Label(Integer.toString(i + 1)),
                new Label(option.name), new Label(option.lmsrPrice == null ? "—"
                    : money(option.lmsrPrice)), new Label(option.lmsrSharesBought == null
                    ? "—" : option.lmsrSharesBought.toString()));
        }
        detailFacts.getChildren().add(optionGrid);
        updateChoices(buyOption, detail);
        updateChoices(winnerOption, detail);
        updateChoices(orderOption, detail);
        boolean maker = currentUser != null && currentUser.equals(detail.marketMakerName);
        boolean active = detail.status == EventStatus.ACTIVE;
        startButton.setVisible(maker && detail.status == EventStatus.NOT_STARTED);
        startButton.setManaged(startButton.isVisible());
        closeButton.setDisable(!maker || !active || actions.isBusy());
        buyButton.setDisable(!active || detail.methodType != EventMethodType.LMSR || actions.isBusy());
        orderButton.setDisable(!active || detail.methodType != EventMethodType.ORDER_BOOK || actions.isBusy());
        orderBookText.setVisible(detail.methodType == EventMethodType.ORDER_BOOK);
        orderBookText.setManaged(orderBookText.isVisible());
        double scroll = orderBookText.getScrollTop();
        orderBookText.setText(book == null ? "" : formatBook(book));
        orderBookText.setScrollTop(scroll);
    }

    private static void updateChoices(ComboBox<OptionChoice> control,
                                      Exercise3EventDetailsDTO detail) {
        int selected = control.getValue() == null ? 0 : control.getValue().index();
        List<OptionChoice> choices = new ArrayList<>();
        for (int i = 0; i < detail.options.size(); i++)
            choices.add(new OptionChoice(i, detail.options.get(i).name));
        control.getItems().setAll(choices);
        control.setValue(choices.get(Math.min(selected, choices.size() - 1)));
    }

    private static String formatBook(Exercise3OrderBookDTO book) {
        StringBuilder out = new StringBuilder("Order Book  ·  d=" + book.d + "\n");
        for (OrderBookOptionDTO option : book.options) {
            out.append("\n").append(option.optionName).append("\n");
            var stats = option.stats;
            out.append("LAST ").append(optional(stats.last)).append("  BID ")
                .append(optional(stats.bid)).append("  ASK ").append(optional(stats.ask))
                .append("  MID ").append(optional(stats.mid)).append("  SPREAD ")
                .append(optional(stats.spread)).append("\n");
            for (RestingOrderDTO order : option.orders) out.append("  #")
                .append(order.orderId).append(" ").append(order.side).append(" · ")
                .append(order.userName).append(" · ").append(order.remainingQuantity)
                .append(" @ ").append(money(order.price)).append("\n");
        }
        out.append("\nPositions\n");
        for (Exercise3OrderBookPositionDTO position : book.positions) {
            out.append(position.userName).append(": holdings ")
                .append(position.holdings).append(", reserved shares ")
                .append(position.reservedShares).append(", cash ")
                .append(money(position.reservedCash)).append("\n");
        }
        return out.toString();
    }

    private void renderOwn(Exercise3UserDetailsDTO me, List<AccountTransactionDTO> ledger) {
        ownHeader.setText(me.name + " · " + money(me.balance)
            + (me.marketMaker ? " · Market Maker" : ""));
        StringBuilder positions = new StringBuilder();
        for (Exercise3LmsrParticipationDTO part : me.lmsrParticipations) {
            positions.append(part.eventName).append(" · LMSR · ")
                .append(part.eventStatus).append("\n");
            for (int i = 0; i < part.optionNames.size(); i++)
                positions.append("  ").append(part.optionNames.get(i)).append(": ")
                    .append(part.holdings.get(i)).append(" shares\n");
            for (TradeRecordDTO trade : part.tradeHistory)
                positions.append("  Buy ").append(trade.quantity).append(" ")
                    .append(trade.optionName).append(" · paid ")
                    .append(money(trade.totalPaid)).append("\n");
            if (part.settled) positions.append("  Settled: ")
                .append(money(part.netPayout)).append("\n");
        }
        for (Exercise3OrderBookPositionDTO part : me.orderBookPositions) {
            positions.append(part.eventName).append(" · Order Book · holdings ")
                .append(part.holdings).append(" · reserved ")
                .append(part.reservedShares).append(" / ")
                .append(money(part.reservedCash)).append(" cash\n");
            for (var fill : part.executions) positions.append("  ")
                .append(fill.type).append(" ").append(fill.side).append(" ")
                .append(fill.quantity).append(" option ").append(fill.optionIndex + 1)
                .append(" @ ").append(money(fill.price)).append("\n");
        }
        ownPositions.setText(positions.length() == 0 ? "No participations yet." : positions.toString());
        StringBuilder transactions = new StringBuilder();
        for (AccountTransactionDTO row : ledger) transactions.append("#")
            .append(row.sequence).append("  ").append(row.type).append("  ")
            .append(money(row.signedAmount)).append("  →  ")
            .append(money(row.resultingBalance))
            .append(row.eventName == null ? "" : "  ·  " + row.eventName)
            .append(row.description == null ? "" : "  ·  " + row.description)
            .append("\n");
        ownLedger.setText(transactions.length() == 0 ? "No transactions yet." : transactions.toString());
    }

    private void clearDetail() {
        currentDetail = null;
        currentBook = null;
        detailFacts.getChildren().setAll(new Label("Choose an event to view its details."));
        orderBookText.clear();
        startButton.setVisible(false);
        startButton.setManaged(false);
        buyButton.setDisable(true);
        orderButton.setDisable(true);
        closeButton.setDisable(true);
    }

    private void chooseUpload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select an Exercise 3 XML file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        submit(() -> api.upload(Files.readAllBytes(file.toPath())), added -> {
            message("Uploaded " + added.size() + " event(s). Existing events remain.");
            refresh();
        });
    }

    private void deposit() {
        TextField amount = new TextField();
        amount.setPromptText("Amount");
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Deposit"); dialog.setHeaderText("Add funds to your account");
        dialog.getDialogPane().setContent(amount);
        if (dialog.showAndWait().isEmpty() || dialog.getResult().getButtonData()
                .isCancelButton()) return;
        double value;
        try { value = Double.parseDouble(amount.getText().trim()); }
        catch (RuntimeException error) { message("Enter a valid amount."); return; }
        if (!Double.isFinite(value) || value <= 0) { message("Amount must be positive."); return; }
        submit(() -> api.deposit(value), result -> {
            message("Deposit complete. Balance: " + money(result.balance)); refresh();
        });
    }

    private void startEvent() {
        String name = selectedEventName.get();
        if (name != null) submit(() -> api.start(name), ignored -> {
            message("Event started: " + name); refresh();
        });
    }

    private void buy() {
        String name = selectedEventName.get();
        OptionChoice choice = buyOption.getValue();
        if (name == null || choice == null) return;
        int quantity = positiveInt(buyQuantity.getText(), "quantity");
        if (quantity <= 0) return;
        submit(() -> api.buy(name, choice.index(), quantity), result -> {
            message("Bought " + result.quantity + " " + result.optionName
                + " shares for " + money(result.totalPaid) + " (commission "
                + money(result.commissionPaid) + ").");
            refresh();
        });
    }

    private void placeOrder() {
        String name = selectedEventName.get();
        OptionChoice choice = orderOption.getValue();
        if (name == null || choice == null) return;
        int quantity = positiveInt(orderQuantity.getText(), "quantity");
        if (quantity <= 0) return;
        double price;
        try { price = Double.parseDouble(orderPrice.getText().trim()); }
        catch (RuntimeException error) { message("Enter a valid price."); return; }
        if (!Double.isFinite(price) || price <= 0) { message("Price must be positive."); return; }
        OrderSide side = orderSide.getValue();
        submit(() -> api.order(name, choice.index(), side, quantity, price), result -> {
            message("Order #" + result.orderId + ": " + result.executedQuantity
                + " executed, " + result.restingQuantity + " resting.");
            refresh();
        });
    }

    private void closeEvent() {
        String name = selectedEventName.get();
        OptionChoice winner = winnerOption.getValue();
        if (name == null || winner == null) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
            "Close " + name + " with winner " + winner.name() + "?");
        if (confirmation.showAndWait().isEmpty() || confirmation.getResult()
                .getButtonData().isCancelButton()) return;
        submit(() -> api.closeEvent(name, winner.index()), result -> {
            message("Closed " + name + ". Winner: " + result.winningOptionName + ".");
            refresh();
        });
    }

    private void logout() {
        if (poller != null) { poller.close(); poller = null; }
        submit(() -> { api.logout(); return null; }, ignored -> {
            sessionGeneration++;
            currentUser = null;
            showLogin();
        });
    }

    private void refresh() { if (poller != null) poller.refreshNow(); }

    private <T> void submit(Callable<T> operation, Consumer<T> success) {
        actions.submit(operation, success, error -> {
            if (error instanceof ApiException apiError) message(apiError.userMessage());
            else message("The action could not be completed. Please try again.");
            if (currentUser != null && poller == null) startPolling();
        }, busy -> {
            progress.setVisible(busy);
            for (Button button : actionButtons) button.setDisable(busy);
            if (!busy && currentDetail != null) renderDetail(currentDetail, currentBook);
        });
    }

    private int positiveInt(String raw, String label) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value > 0) return value;
        } catch (RuntimeException ignored) { }
        message("Enter a positive " + label + ".");
        return -1;
    }

    private void message(String value) { if (status != null) status.setText(value); }
    private Button button(String label, Runnable action) {
        Button button = new Button(label);
        button.setOnAction(event -> action.run());
        button.getStyleClass().add("primary-button");
        actionButtons.add(button);
        return button;
    }
    private static Label title(String text) {
        Label label = new Label(text); label.getStyleClass().add("app-title"); return label;
    }
    private static Label section(String text) {
        Label label = new Label(text); label.getStyleClass().add("section-title"); return label;
    }
    private static ComboBox<String> filter(String... values) {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(values));
        box.setValue(values[0]); return box;
    }
    private static String money(double amount) { return String.format(Locale.US, "%.2f", amount); }
    private static String optional(Double amount) { return amount == null ? "—" : money(amount); }

    public Stage stage() { return stage; }
    public String loggedInUser() { return currentUser; }
    public String selectedEvent() { return selectedEventName.get(); }
    public long pollCycles() { return poller == null ? 0 : poller.cycles(); }
    public boolean pollingActive() { return poller != null && poller.isActive(); }

    @Override public void close() {
        sessionGeneration++;
        if (poller != null) poller.close();
        actions.close();
        api.close();
    }

    private record OptionChoice(int index, String name) {
        @Override public String toString() { return (index + 1) + " · " + name; }
    }
}
