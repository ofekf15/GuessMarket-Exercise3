package il.ac.mta.gm.ui;

import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.engine.core.GuessMarketEngine;
import il.ac.mta.gm.engine.core.SystemState;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Path;

public final class GuessMarketApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/il/ac/mta/gm/ui/main-view.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1180, 760);
        scene.getStylesheets().add(
            getClass().getResource("/il/ac/mta/gm/ui/guess-market.css").toExternalForm());

        EngineInterface engine = new GuessMarketEngine(new SystemState());
        MainController controller = loader.getController();
        controller.initializeApplication(engine, stage);

        stage.setTitle("Guess Market");
        stage.setScene(scene);
        stage.setMinWidth(560);
        stage.setMinHeight(420);
        stage.setResizable(true);
        stage.show();

        String smokeFile = getParameters().getNamed().get("smoke");
        if (smokeFile != null) {
            controller.runSmoke(Path.of(smokeFile).toFile(), () -> {
                System.out.println("JAVAFX_SMOKE_PASSED");
                PauseTransition pause = new PauseTransition(Duration.millis(350));
                pause.setOnFinished(event -> Platform.exit());
                pause.play();
            });
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
