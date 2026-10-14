package il.ac.mta.gm.client.ui;

import il.ac.mta.gm.client.http.ApiClient;
import il.ac.mta.gm.dto.Exercise3EventDTO;
import il.ac.mta.gm.dto.Exercise3EventDetailsDTO;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Real JavaFX stage using the production ClientView and production HTTP API. */
public final class Phase5ClientFxTest extends Application {
    private static volatile Throwable failure;
    private ClientView view;
    private ApiClient maker;
    private Timeline timeline;
    private CompletableFuture<Void> setup;
    private int phase;
    private long selectionCycles;
    private long deadline;

    public static void main(String[] args) {
        launch(args);
        if (failure != null) throw new AssertionError("Phase5ClientFxTest failed", failure);
        System.out.println("Phase5ClientFxTest PASSED");
    }

    @Override public void start(Stage stage) {
        String base = getParameters().getRaw().isEmpty()
            ? "http://localhost:8080/guess-market-ex3/api"
            : getParameters().getRaw().get(0);
        maker = new ApiClient(base);
        view = new ClientView(stage, new ApiClient(base));
        view.show();
        assert stage.isResizable();
        assert !stage.getScene().getStylesheets().isEmpty();
        assert view.loggedInUser() == null;
        assert view.selectedEvent() == null;
        assert !view.pollingActive();
        assert field("status", Label.class).getText().equals(
            "Enter a unique username. No password is needed.");
        field("loginName", TextField.class).setText("FxUser");
        field("loginButton", Button.class).fire();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        timeline = new Timeline(new KeyFrame(Duration.millis(150), event -> tick()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void tick() {
        try {
            if (System.nanoTime() > deadline) throw new AssertionError("GUI smoke timed out at phase " + phase);
            switch (phase) {
                case 0 -> {
                    if (!"FxUser".equals(view.loggedInUser())) return;
                    view.stage().setWidth(600);
                    view.stage().setHeight(440);
                    setup = CompletableFuture.runAsync(this::createServerEvents);
                    phase++;
                }
                case 1 -> {
                    if (!setup.isDone()) return;
                    setup.join();
                    Button logout = button("Log out");
                    assert logout.localToScene(logout.getBoundsInLocal()).getMaxX()
                        <= view.stage().getScene().getWidth() : "Header clips at minimum width.";
                    phase++;
                }
                case 2 -> {
                    ListView<Exercise3EventDTO> events = field("eventList", ListView.class);
                    var tri = events.getItems().stream().filter(row -> row.name.equals("Fx Three"))
                        .findFirst().orElse(null);
                    if (tri == null) return;
                    events.getSelectionModel().select(tri);
                    phase++;
                }
                case 3 -> {
                    ComboBox<?> choices = field("buyOption", ComboBox.class);
                    Exercise3EventDetailsDTO detail = field("currentDetail", Exercise3EventDetailsDTO.class);
                    if (detail == null || !"Fx Three".equals(detail.name) || choices.getItems().size() != 3
                            || detail.status.name().equals("NOT_STARTED")) return;
                    choices.getSelectionModel().select(2);
                    assert choices.getValue().toString().startsWith("3 · C");
                    field("buyQuantity", TextField.class).setText("2");
                    field("buyButton", Button.class).fire();
                    phase++;
                }
                case 4 -> {
                    Exercise3EventDetailsDTO detail = field("currentDetail", Exercise3EventDetailsDTO.class);
                    if (detail == null || detail.options.get(2).lmsrSharesBought != 2) return;
                    ListView<Exercise3EventDTO> events = field("eventList", ListView.class);
                    var four = events.getItems().stream().filter(row -> row.name.equals("Fx Four"))
                        .findFirst().orElseThrow();
                    events.getSelectionModel().select(four);
                    phase++;
                }
                case 5 -> {
                    ComboBox<?> winners = field("winnerOption", ComboBox.class);
                    Exercise3EventDetailsDTO detail = field("currentDetail", Exercise3EventDetailsDTO.class);
                    if (detail == null || !"Fx Four".equals(detail.name)
                            || winners.getItems().size() != 4) return;
                    winners.getSelectionModel().select(3);
                    assert winners.getValue().toString().startsWith("4 · D");
                    selectionCycles = view.pollCycles();
                    phase++;
                }
                case 6 -> {
                    if (view.pollCycles() < selectionCycles + 3) return;
                    assert "Fx Four".equals(view.selectedEvent());
                    assert field("winnerOption", ComboBox.class).getSelectionModel().getSelectedIndex() == 3;
                    ListView<Exercise3EventDTO> events = field("eventList", ListView.class);
                    var binary = events.getItems().stream().filter(row -> row.name.equals("Fx Binary"))
                        .findFirst().orElseThrow();
                    events.getSelectionModel().select(binary);
                    phase++;
                }
                case 7 -> {
                    Exercise3EventDetailsDTO detail = field("currentDetail", Exercise3EventDetailsDTO.class);
                    if (detail == null || !"Fx Binary".equals(detail.name)
                            || field("orderOption", ComboBox.class).getItems().size() != 2
                            || !field("orderBookText", TextArea.class).getText().contains("SPREAD")) return;
                    field("orderButton", Button.class).fire();
                    phase++;
                }
                case 8 -> {
                    if (!field("orderBookText", TextArea.class).getText().contains("FxUser")) return;
                    button("Log out").fire();
                    phase++;
                }
                case 9 -> {
                    if (view.loggedInUser() != null) return;
                    assert !view.pollingActive();
                    finish(null);
                }
                default -> throw new AssertionError("Unexpected phase");
            }
        } catch (Throwable error) { finish(error); }
    }

    private void createServerEvents() {
        maker.login("FxMaker");
        maker.deposit(5000);
        String xml = "<Guess-Market><GM-events>"
            + event("Fx Three", 20, "A", "B", "C")
            + event("Fx Four", 25, "A", "B", "C", "D")
            + "<GM-event name=\"Fx Binary\"><description>GUI book</description>"
            + "<commission type=\"on-purchase\">5</commission><GM-options>"
            + "<GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>"
            + "<GM-method><GM-order-book initial=\"10\" d=\"1\" "
            + "allow-mint=\"false\"/></GM-method></GM-event>"
            + "</GM-events></Guess-Market>";
        maker.upload(xml.getBytes(StandardCharsets.UTF_8));
        maker.start("Fx Three");
        maker.start("Fx Binary");
        ApiClient viewApi = field("api", ApiClient.class);
        viewApi.deposit(1000);
    }

    private static String event(String name, int b, String... options) {
        StringBuilder out = new StringBuilder("<GM-event name=\"").append(name)
            .append("\"><description>GUI smoke</description><commission type=\"on-purchase\">5")
            .append("</commission><GM-options>");
        for (String option : options) out.append("<GM-option>").append(option).append("</GM-option>");
        return out.append("</GM-options><GM-method><GM-LMSR><b>").append(b)
            .append("</b></GM-LMSR></GM-method></GM-event>").toString();
    }

    private Button button(String text) {
        @SuppressWarnings("unchecked") List<Button> buttons = field("actionButtons", List.class);
        return buttons.stream().filter(button -> button.getText().equals(text))
            .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name, Class<T> type) {
        try {
            Field field = ClientView.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(view);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private void finish(Throwable error) {
        if (timeline != null) timeline.stop();
        if (error != null) failure = error;
        view.close();
        maker.close();
        view.stage().close();
        Platform.exit();
    }
}
