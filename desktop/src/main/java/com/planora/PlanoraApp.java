package com.planora;

import com.planora.service.JsonPlannerStore;
import com.planora.service.Planner;
import com.planora.sync.SyncServer;
import com.planora.ui.MainWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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
                            + "\n\nEl archivo no se modificó. Revísalo o muévelo y vuelve a abrir Planora.");
            alert.setHeaderText("Error al abrir Planora");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        sync = new SyncServer(planner, store.file());
        sync.start();

        Scene scene = new Scene(new MainWindow(planner, sync), 1280, 800);
        scene.getStylesheets().add(
                PlanoraApp.class.getResource("/com/planora/ui/planora.css").toExternalForm());
        stage.setTitle("Planora");
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
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
