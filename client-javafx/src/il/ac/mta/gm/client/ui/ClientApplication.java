package il.ac.mta.gm.client.ui;

import il.ac.mta.gm.client.http.ApiClient;
import javafx.application.Application;
import javafx.stage.Stage;

/** Exercise 3 client composition root: DTO/HTTP only. */
public final class ClientApplication extends Application {
    private ClientView view;

    @Override public void start(Stage stage) {
        view = new ClientView(stage, new ApiClient());
        view.show();
    }

    @Override public void stop() {
        if (view != null) view.close();
    }

    public static void main(String[] args) { launch(args); }
}
