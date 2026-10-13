package com.planora;

import com.planora.service.JsonPlannerStore;
import com.planora.service.Planner;
import com.planora.sync.SyncServer;
import com.planora.ui.MainWindow;
import com.planora.ui.Profile;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Locale;

public class PlanoraApp extends Application {
    private SyncServer sync;

    /** Data lives in ~/.planora/planora.json unless -Dplanora.data=<file> says otherwise. */
    static Path dataFile() {
        String custom = System.getProperty("planora.data");
        if (custom != null && !custom.isBlank()) {
            return Path.of(custom);
        }
        return Path.of(System.getProperty("user.home"), ".planora", "planora.json");
    }

    @Override
    public void start(Stage stage) {
        Locale.setDefault(Locale.forLanguageTag("es"));

        JsonPlannerStore store = new JsonPlannerStore(dataFile());
        Planner planner;
        try {
            planner = new Planner(store);
        } catch (RuntimeException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "No se pudieron cargar los datos desde:\n" + store.file()
                            + "\n\nEl archivo no se modificó. Revísalo o muévelo y vuelve a abrir Zyora.");
            alert.setHeaderText("Error al abrir Zyora");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        sync = new SyncServer(planner, store.file());
        sync.start();

        MainWindow window = new MainWindow(planner, new Profile(store.file()), sync);
        Scene scene = new Scene(window, 1280, 800);
        scene.getStylesheets().add(
                PlanoraApp.class.getResource("/com/planora/ui/planora.css").toExternalForm());
        stage.setTitle("Zyora");
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(new Image(
                    PlanoraApp.class.getResource("/com/planora/icons/planora-" + size + ".png").toExternalForm()));
        }
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
        Platform.runLater(window::welcome);
    }

    @Override
    public void stop() {
        if (sync != null) {
            sync.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
