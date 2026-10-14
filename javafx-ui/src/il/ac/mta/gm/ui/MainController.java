package il.ac.mta.gm.ui;

import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.UserSummaryDTO;
import il.ac.mta.gm.engine.api.EngineInterface;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

public final class MainController {
    @FXML private Label loadedFileLabel;
    @FXML private Label loadingLabel;
    @FXML private Button loadButton;
    @FXML private ProgressBar loadProgress;
    @FXML private EventsController eventsViewController;
    @FXML private UsersController usersViewController;

    private EngineInterface engine;
    private Stage stage;
    private File lastDirectory;
    private boolean loading;

    public void initializeApplication(EngineInterface engine, Stage stage) {
        this.engine = engine;
        this.stage = stage;
        eventsViewController.initializeEngine(engine, this::refreshAll);
        usersViewController.initializeEngine(engine);
        refreshAll();
    }

    @FXML
    private void onLoadFile() {
        if (loading) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Guess Market XML");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File initialDirectory = usableDirectory(lastDirectory);
        if (initialDirectory == null) {
            initialDirectory = usableDirectory(new File(System.getProperty("user.dir", ".")));
        }
        if (initialDirectory != null) {
            chooser.setInitialDirectory(initialDirectory);
        }
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            lastDirectory = selected.getParentFile();
            loadFile(selected, null);
        }
    }

    private static File usableDirectory(File candidate) {
        return candidate != null && candidate.isDirectory() ? candidate : null;
    }

    private void loadFile(File file, Runnable successCallback) {
        loading = true;
        loadButton.setDisable(true);
        loadProgress.setVisible(true);
        loadingLabel.setVisible(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Preparing XML…");
                updateProgress(0, 5);
                for (int step = 1; step <= 4; step++) {
                    Thread.sleep(250);
                    updateProgress(step, 5);
                    updateMessage(step < 4 ? "Loading and validating…" : "Refreshing views…");
                }
                engine.loadSystemData(file.getAbsolutePath());
                updateProgress(5, 5);
                return null;
            }
        };
        loadProgress.progressProperty().bind(task.progressProperty());
        loadingLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            finishLoading(task);
            loadedFileLabel.setText(file.getAbsolutePath());
            refreshAll();
            if (successCallback != null) {
                successCallback.run();
            }
        });
        task.setOnFailed(event -> {
            finishLoading(task);
            refreshAll();
            UiAlerts.showError("Could not load XML", task.getException());
        });
        Thread worker = new Thread(task, "guess-market-xml-loader");
        worker.setDaemon(true);
        worker.start();
    }

    private void finishLoading(Task<?> task) {
        loadProgress.progressProperty().unbind();
        loadingLabel.textProperty().unbind();
        loading = false;
        loadButton.setDisable(false);
        loadProgress.setVisible(false);
        loadingLabel.setVisible(false);
        loadProgress.setProgress(0);
        loadingLabel.setText("");
    }

    private void refreshAll() {
        if (engine == null) {
            return;
        }
        List<EventDisplayDTO> events = engine.getEvents();
        List<UserSummaryDTO> users = engine.getUsers();
        eventsViewController.refresh(events, users);
        usersViewController.refresh(users, events);
    }

    void runSmoke(File file, Runnable completed) {
        loadFile(file, () -> {
            eventsViewController.verifySmokeSelections();
            usersViewController.verifySmokeSelection();
            completed.run();
        });
    }
}
