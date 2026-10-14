package il.ac.mta.gm.ui;

import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.EventMethodType;
import il.ac.mta.gm.dto.OrderBookDetailsDTO;
import il.ac.mta.gm.dto.OrderBookOptionDTO;
import il.ac.mta.gm.dto.OrderResultDTO;
import il.ac.mta.gm.dto.OrderSide;
import il.ac.mta.gm.dto.UserSummaryDTO;
import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.engine.core.GuessMarketEngine;
import il.ac.mta.gm.engine.core.SystemState;
import il.ac.mta.gm.engine.exception.TradingErrorCode;
import il.ac.mta.gm.engine.exception.TradingException;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class Phase5JavaFxFunctionalTest extends Application {
    private static final Path ROOT = Path.of("C:/GuessMarket");
    private static final File SMALL = ROOT.resolve("test_files/ex2/small.xml").toFile();
    private static final File MULTIPLE = ROOT.resolve("test_files/ex2/multiple.xml").toFile();
    private static final File ERROR_2 = ROOT.resolve("test_files/ex2/error-2.xml").toFile();
    private static final File ERROR_3 = ROOT.resolve("test_files/ex2/error-3.xml").toFile();

    private Stage stage;
    private MainController main;
    private EventsController events;
    private UsersController users;
    private EngineInterface engine;
    private String multipleSnapshot;
    private String multiplePath;
    private final List<String> errorMessages = new ArrayList<>();

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/il/ac/mta/gm/ui/main-view.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1180, 760);
        scene.getStylesheets().add(
            getClass().getResource("/il/ac/mta/gm/ui/guess-market.css").toExternalForm());
        this.engine = new GuessMarketEngine(new SystemState());
        this.main = loader.getController();
        main.initializeApplication(engine, stage);
        this.events = field(main, "eventsViewController", EventsController.class);
        this.users = field(main, "usersViewController", UsersController.class);

        stage.setTitle("Guess Market Phase 5 Functional Test");
        stage.setScene(scene);
        stage.setMinWidth(560);
        stage.setMinHeight(420);
        stage.setResizable(true);
        stage.show();
        Platform.runLater(this::begin);
    }

    private void begin() {
        try {
            check(stage.isResizable(), "Stage must remain resizable");
            check(!stage.getScene().getStylesheets().isEmpty(), "CSS must be attached");
            check(eventTable().getItems().isEmpty(), "Startup events must be empty");
            check(userTable().getItems().isEmpty(), "Startup users must be empty");
            loadValid(SMALL, this::afterSmall);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void afterSmall() {
        try {
            check(engine.getEvents().size() == 2, "small.xml event count");
            check(engine.getUsers().size() == 3, "small.xml user count");
            check(eventTable().getItems().size() == 2, "Events view population");
            check(userTable().getItems().size() == 3, "Users view population");
            check(loadedPath().equals(SMALL.getAbsolutePath()), "Loaded path after small.xml");
            events.verifySmokeSelections();
            users.verifySmokeSelection();
            verifyInitialFilters();
            engine.startEvent(1, "Tikva");
            check(engine.getEventStatus(1).status.name().equals("ACTIVE"),
                "Pre-replacement mutation");
            loadValid(MULTIPLE, this::afterMultiple);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void afterMultiple() {
        try {
            List<String> eventNames = engine.getEvents().stream().map(event -> event.name).toList();
            List<String> userNames = engine.getUsers().stream().map(user -> user.name).toList();
            check(eventNames.size() == 4, "Official multiple.xml event count");
            check(userNames.equals(List.of("Avrum", "Tikva", "Menash")),
                "Official multiple.xml user replacement");
            check(engine.getEvents().stream().allMatch(event -> event.status.name().equals("NOT_STARTED")),
                "Second load must discard the previously active lifecycle state");
            check(Math.abs(engine.getUser("Tikva").balance - 10000.0) < 1e-9,
                "Second load must restore XML user balances");
            check(eventTable().getItems().size() == eventNames.size(), "Second-load event view count");
            check(userTable().getItems().size() == userNames.size(), "Second-load user view count");
            multipleSnapshot = snapshot();
            multiplePath = loadedPath();
            loadInvalid(ERROR_2, this::afterError2);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void afterError2() {
        try {
            verifyInvalidPreserved("error-2.xml");
            loadInvalid(ERROR_3, this::afterError3);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void afterError3() {
        try {
            verifyInvalidPreserved("error-3.xml");
            check(errorMessages.size() == 2, "Both invalid-load dialogs must be shown");
            loadValid(SMALL, this::afterReloadSmall);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void afterReloadSmall() {
        try {
            exerciseLmsrAndOrderBook();
            invoke(main, "refreshAll", new Class<?>[0]);
            events.verifySmokeSelections();
            users.verifySmokeSelection();
            verifyClosedFilter();
            verifyResizeAndSnapshot();
            System.out.println("PHASE5_JAVAFX_FUNCTIONAL_TEST_PASSED");
            System.out.println("INVALID_DIALOGS=" + errorMessages.size());
            System.out.println("FINAL_EVENTS=" + engine.getEvents().size());
            System.out.println("FINAL_USERS=" + engine.getUsers().size());
            Platform.exit();
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void verifyInitialFilters() {
        ComboBox<String> method = field(events, "methodFilter", ComboBox.class);
        ComboBox<String> status = field(events, "statusFilter", ComboBox.class);
        ComboBox<String> commission = field(events, "commissionFilter", ComboBox.class);

        method.setValue("LMSR");
        check(eventTable().getItems().size() == 1, "LMSR filter");
        method.setValue("Order Book");
        check(eventTable().getItems().size() == 1, "Order Book filter");
        method.setValue("All");
        status.setValue("Not Started");
        check(eventTable().getItems().size() == 2, "Not Started filter");
        status.setValue("Active");
        check(eventTable().getItems().isEmpty(), "Active filter before start");
        status.setValue("All");
        commission.setValue("On Purchase");
        check(eventTable().getItems().size() == 1, "On Purchase filter");
        method.setValue("LMSR");
        check(eventTable().getItems().size() == 1, "Combined method and commission filter");
        method.setValue("Order Book");
        check(eventTable().getItems().isEmpty(), "Combined empty filter result");
        method.setValue("All");
        commission.setValue("All");
    }

    private void exerciseLmsrAndOrderBook() {
        selectEvent(1);
        String beforeWrongStart = snapshot();
        fireWithDialogs(button("startButton"), pane -> combo(pane, 0).setValue("Avrum"));
        check(snapshot().equals(beforeWrongStart), "Wrong-MM start atomicity through UI");
        fireWithDialogs(button("startButton"), pane -> combo(pane, 0).setValue("Tikva"));
        check(engine.getEventStatus(1).status.name().equals("ACTIVE"), "LMSR start through UI");

        fireWithDialogs(button("buyButton"), pane -> {
            combo(pane, 0).setValue("Menash");
            combo(pane, 1).getSelectionModel().select(0);
            spinner(pane, 0).getValueFactory().setValue(5);
        });
        check(!engine.getUser("Menash").participations.isEmpty(), "LMSR purchase through UI");
        String beforeFailedBuy = snapshot();
        fireWithDialogs(button("buyButton"), pane -> {
            combo(pane, 0).setValue("Menash");
            combo(pane, 1).getSelectionModel().select(0);
            spinner(pane, 0).getValueFactory().setValue(100000);
        });
        check(snapshot().equals(beforeFailedBuy), "Failed LMSR purchase atomicity");

        String beforeWrongClose = snapshot();
        fireWithDialogs(button("closeButton"), pane -> {
            combo(pane, 0).setValue("Avrum");
            combo(pane, 1).getSelectionModel().select(0);
        });
        check(snapshot().equals(beforeWrongClose), "Wrong-MM close atomicity through UI");
        fireWithDialogs(button("closeButton"), pane -> {
            combo(pane, 0).setValue("Tikva");
            combo(pane, 1).getSelectionModel().select(0);
        });
        check(engine.getEventStatus(1).status.name().equals("CLOSED"), "LMSR close through UI");

        selectEvent(2);
        fireWithDialogs(button("startButton"), pane -> combo(pane, 0).setValue("Avrum"));
        check(engine.getEventStatus(2).status.name().equals("ACTIVE"), "OB start through UI");
        placeOrder("Avrum", 0, OrderSide.SELL, 10, 0.40);
        placeOrder("Menash", 0, OrderSide.BUY, 6, 0.50);
        OrderBookDetailsDTO afterPartial = engine.getOrderBook(2);
        check(orderCount(afterPartial) == 1, "Partial fill remainder must rest");

        placeOrder("Tikva", 1, OrderSide.BUY, 10, 0.42);
        placeOrder("Menash", 0, OrderSide.BUY, 10, 0.62);
        OrderBookDetailsDTO activeBook = engine.getOrderBook(2);
        check(activeBook.positions.stream().mapToInt(position -> position.holdings.get(1)).sum() > 100,
            "Peer mint shares must be created through UI");
        check(activeBook.options.stream().anyMatch(option -> option.stats.last != null),
            "LAST statistic must be available after executions");
        check(activeBook.options.stream().anyMatch(option -> option.stats.bid != null),
            "BID statistic must reflect a resting order");

        String beforeInvalidPrice = snapshot();
        placeOrder("Menash", 0, OrderSide.BUY, 1, 1.01);
        check(snapshot().equals(beforeInvalidPrice), "Invalid OB price atomicity");
        fireWithDialogs(button("closeButton"), pane -> {
            combo(pane, 0).setValue("Avrum");
            combo(pane, 1).getSelectionModel().select(0);
        });
        check(orderCount(engine.getOrderBook(2)) == 0, "Close must clear open orders");
        check(engine.getOrderBook(2).positions.stream()
            .filter(position -> position.participant)
            .allMatch(position -> position.profitLoss != null), "Closed OB P/L visibility");
    }

    private void placeOrder(String user, int option, OrderSide side, int quantity, double price) {
        fireWithDialogs(button("orderButton"), pane -> {
            combo(pane, 0).setValue(user);
            combo(pane, 1).getSelectionModel().select(option);
            combo(pane, 2).setValue(side);
            spinner(pane, 0).getValueFactory().setValue(quantity);
            spinner(pane, 1).getValueFactory().setValue(price);
        });
    }

    private void selectEvent(int eventId) {
        EventsController.EventRow row = eventTable().getItems().stream()
            .filter(candidate -> candidate.event.id == eventId).findFirst().orElseThrow();
        eventTable().getSelectionModel().select(row);
    }

    private Button button(String fieldName) {
        return field(events, fieldName, Button.class);
    }

    private void fireWithDialogs(Button action, Consumer<DialogPane> configureRequest) {
        check(!action.isDisabled(), action.getText() + " must be enabled");
        final boolean[] configured = {false};
        final int[] dialogs = {0};
        Timeline automation = new Timeline(new KeyFrame(Duration.millis(45), event -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (window == stage || !window.isShowing() || window.getScene() == null
                        || !(window.getScene().getRoot() instanceof DialogPane pane)) {
                    continue;
                }
                if (!configured[0] && !pane.lookupAll(".combo-box").isEmpty()) {
                    configureRequest.accept(pane);
                    configured[0] = true;
                }
                if (pane.lookupButton(javafx.scene.control.ButtonType.OK)
                        instanceof javafx.scene.control.ButtonBase ok) {
                    dialogs[0]++;
                    ok.fire();
                }
            }
        }));
        automation.setCycleCount(Timeline.INDEFINITE);
        automation.play();
        action.fire();
        automation.stop();
        check(configured[0], action.getText() + " request dialog must appear");
        check(dialogs[0] >= 2, action.getText() + " result/error dialog must appear");
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<Object> combo(DialogPane pane, int index) {
        return (ComboBox<Object>) pane.lookupAll(".combo-box").stream()
            .filter(ComboBox.class::isInstance).map(ComboBox.class::cast).toList().get(index);
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Object> spinner(DialogPane pane, int index) {
        return (Spinner<Object>) pane.lookupAll(".spinner").stream()
            .filter(Spinner.class::isInstance).map(Spinner.class::cast).toList().get(index);
    }

    private void verifyClosedFilter() {
        ComboBox<String> status = field(events, "statusFilter", ComboBox.class);
        status.setValue("Closed");
        check(eventTable().getItems().size() == 2, "Closed filter after resolutions");
        status.setValue("All");
    }

    private void verifyResizeAndSnapshot() throws Exception {
        stage.setWidth(560);
        stage.setHeight(420);
        stage.centerOnScreen();
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        check(stage.getScene().getRoot().lookupAll(".scroll-pane").size() >= 2,
            "Scrollable detail panes must exist");
        for (String fieldName : List.of("startButton", "buyButton", "orderButton", "closeButton")) {
            Button button = field(events, fieldName, Button.class);
            Bounds bounds = button.localToScene(button.getBoundsInLocal());
            check(button.isVisible() && bounds.getMaxY() <= stage.getScene().getHeight() + 1,
                fieldName + " must remain reachable at minimum size");
        }
        File screenshot = ROOT.resolve(".tmp_phase5_java25_20260913/phase5-small-window.png").toFile();
        ImageIO.write(SwingFXUtils.fromFXImage(stage.getScene().snapshot(null), null),
            "png", screenshot);
        check(screenshot.isFile() && screenshot.length() > 0, "Small-window screenshot");
    }

    private void loadValid(File file, Runnable callback) {
        long started = System.nanoTime();
        invoke(main, "loadFile", new Class<?>[]{File.class, Runnable.class}, file, (Runnable) () -> {
            try {
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
                check(elapsedMillis >= 900, "Artificial background delay");
                verifyLoadingRestored();
                callback.run();
            } catch (Throwable error) {
                fail(error);
            }
        });
        check(field(main, "loading", Boolean.class), "Loading flag must be set");
        check(field(main, "loadButton", Button.class).isDisabled(), "Load button disabled during Task");
        check(field(main, "loadProgress", ProgressBar.class).isVisible(), "Progress visible during Task");
        check(field(main, "loadingLabel", Label.class).isVisible(), "Loading message visible during Task");
    }

    private void loadInvalid(File file, Runnable callback) {
        invoke(main, "loadFile", new Class<?>[]{File.class, Runnable.class}, file, null);
        check(field(main, "loading", Boolean.class), "Invalid load still runs through Task");
        final boolean[] sawDialog = {false};
        Timeline watcher = new Timeline();
        watcher.getKeyFrames().add(new KeyFrame(Duration.millis(80), event -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (window != stage && window.isShowing() && window.getScene() != null
                        && window.getScene().getRoot() instanceof DialogPane pane) {
                    String text = pane.lookupAll(".label").stream()
                        .filter(Label.class::isInstance).map(Label.class::cast)
                        .map(Label::getText).filter(value -> value != null && !value.isBlank())
                        .reduce((a, b) -> a + " | " + b).orElse("");
                    errorMessages.add(text);
                    sawDialog[0] = true;
                    window.hide();
                }
            }
            if (sawDialog[0] && !field(main, "loading", Boolean.class)) {
                watcher.stop();
                verifyLoadingRestored();
                callback.run();
            }
        }));
        watcher.setCycleCount(Timeline.INDEFINITE);
        watcher.play();
    }

    private void verifyInvalidPreserved(String label) {
        check(snapshot().equals(multipleSnapshot), label + " must preserve Engine state");
        check(loadedPath().equals(multiplePath), label + " must preserve displayed path");
        check(eventTable().getItems().size() == engine.getEvents().size(),
            label + " must preserve Events view");
        check(userTable().getItems().size() == engine.getUsers().size(),
            label + " must preserve Users view");
    }

    private void verifyLoadingRestored() {
        check(!field(main, "loading", Boolean.class), "Loading flag restored");
        check(!field(main, "loadButton", Button.class).isDisabled(), "Load button restored");
        check(!field(main, "loadProgress", ProgressBar.class).isVisible(), "Progress hidden");
    }

    private String loadedPath() {
        return field(main, "loadedFileLabel", Label.class).getText();
    }

    @SuppressWarnings("unchecked")
    private TableView<EventsController.EventRow> eventTable() {
        return field(events, "eventTable", TableView.class);
    }

    @SuppressWarnings("unchecked")
    private TableView<UserSummaryDTO> userTable() {
        return field(users, "userTable", TableView.class);
    }

    private String snapshot() {
        return engine.getEvents().stream().map(event -> event.id + ":" + event.name + ":" + event.status)
            .toList() + "|" + engine.getUsers().stream().map(user -> user.name + ":" + user.balance
                + ":" + user.marketMakerEventIds + ":" + user.participations.size()
                + ":" + user.orderBookPositions.size()).toList();
    }

    private static int orderCount(OrderBookDetailsDTO book) {
        return book.options.stream().mapToInt(option -> option.orders.size()).sum();
    }

    private static void expectTrade(TradingErrorCode code, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected " + code);
        } catch (TradingException error) {
            check(error.getErrorCode() == code,
                "Expected " + code + " but received " + error.getErrorCode());
        }
    }

    private void fail(Throwable error) {
        error.printStackTrace(System.err);
        Platform.exit();
        System.exit(1);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes,
                                 Object... arguments) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(error);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
