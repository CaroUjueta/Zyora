package com.zyora;

import com.zyora.service.JsonPlannerStore;
import com.zyora.service.Planner;
import com.zyora.sync.SyncServer;
import com.zyora.ui.DesktopWidget;
import com.zyora.ui.MainWindow;
import com.zyora.ui.Profile;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;

public class ZyoraApp extends Application {
    private SyncServer sync;
    private MainWindow window;
    private DesktopWidget widget;

    /** Data lives in ~/.zyora/zyora.json unless -Dzyora.data=<file> says otherwise. */
    static Path dataFile() {
        String custom = System.getProperty("zyora.data");
        if (custom != null && !custom.isBlank()) {
            return Path.of(custom);
        }
        Path home = Path.of(System.getProperty("user.home"));
        return moveFromPlanora(home.resolve(".planora"), home.resolve(".zyora"));
    }

    /**
     * Zyora used to be called Planora and kept everything in ~/.planora/planora.json. The first time
     * it runs under the new name it moves that folder (data, name, sync code, widget) to ~/.zyora.
     * If a move fails, it keeps using the old file rather than starting empty.
     */
    private static Path moveFromPlanora(Path oldDir, Path dir) {
        if (!Files.exists(dir) && Files.isDirectory(oldDir)) {
            try {
                Files.move(oldDir, dir);
            } catch (IOException e) {
                return oldDir.resolve("planora.json");
            }
        }
        Path data = dir.resolve("zyora.json");
        Path oldData = dir.resolve("planora.json");
        if (!Files.exists(data) && Files.exists(oldData)) {
            try {
                Files.move(oldData, data);
            } catch (IOException e) {
                return oldData;
            }
        }
        return data;
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

        String css = ZyoraApp.class.getResource("/com/zyora/ui/zyora.css").toExternalForm();
        // Zyora keeps running while the desktop widget is open, even with the main window closed.
        Platform.setImplicitExit(false);
        widget = new DesktopWidget(planner, store.file(), css, day -> openMain(stage, day), Platform::exit);
        window = new MainWindow(planner, new Profile(store.file()), sync, widget);
        Scene scene = new Scene(window, 1280, 800);
        scene.getStylesheets().add(css);
        stage.setTitle("Zyora");
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(new Image(
                    ZyoraApp.class.getResource("/com/zyora/icons/zyora-" + size + ".png").toExternalForm()));
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
