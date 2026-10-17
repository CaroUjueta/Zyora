package com.planora;

import com.planora.service.JsonPlannerStore;
import com.planora.service.Planner;
import com.planora.sync.SyncServer;
import com.planora.ui.DesktopWidget;
import com.planora.ui.MainWindow;
import com.planora.ui.Profile;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;

public class PlanoraApp extends Application {
    private SyncServer sync;
    private MainWindow window;
    private DesktopWidget widget;

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

        String css = PlanoraApp.class.getResource("/com/planora/ui/planora.css").toExternalForm();
        // Zyora keeps running while the desktop widget is open, even with the main window closed.
        Platform.setImplicitExit(false);
        widget = new DesktopWidget(planner, store.file(), css, day -> openMain(stage, day), Platform::exit);
        window = new MainWindow(planner, new Profile(store.file()), sync, widget);
        Scene scene = new Scene(window, 1280, 800);
        scene.getStylesheets().add(css);
        stage.setTitle("Zyora");
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(new Image(
                    PlanoraApp.class.getResource("/com/planora/icons/planora-" + size + ".png").toExternalForm()));
        }
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.setOnHidden(e -> {
            if (!widget.isShowing()) {
                Platform.exit();
            }
        });
        widget.showingProperty().addListener((obs, was, open) -> {
            if (!open && !stage.isShowing()) {
                Platform.exit();
            }
        });

        // "--widget" (e.g. from a shortcut in the Windows startup folder) opens only the widget.
        boolean widgetOnly = getParameters().getRaw().contains("--widget");
        if (widgetOnly || widget.wasOpen()) {
            widget.show();
        }
        if (!widgetOnly) {
            stage.show();
            Platform.runLater(window::welcome);
            return;
        }
        // Asks for the name once the main window first opens.
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, new EventHandler<>() {
            @Override
            public void handle(WindowEvent e) {
                stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, this);
                Platform.runLater(window::welcome);
            }
        });
    }

    /** Brings the main window up from the widget, on that day's calendar unless the day is null. */
    private void openMain(Stage stage, LocalDate day) {
        stage.show();
        stage.setIconified(false);
        stage.toFront();
        if (day != null) {
            window.openDay(day);
        }
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
