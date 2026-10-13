package com.planora.ui;

import com.planora.sync.SyncServer;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Consumer;

/** Shows what to type on the phone, and when it last synced. Updates live while open. */
final class SyncDialog {
    private SyncDialog() {
    }

    static void show(Window owner, SyncServer sync) {
        Dialog<ButtonType> dialog = Dialogs.decorate(new Dialog<>(), owner);
        dialog.setHeaderText("Sincronizar con el teléfono");
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.OK_DONE));

        VBox content = new VBox(14);
        content.getStyleClass().add("dialog-body");
        content.setMaxWidth(380);

        if (!sync.isRunning()) {
            content.getChildren().add(message(sync.error()));
            dialog.getDialogPane().setContent(content);
            dialog.showAndWait();
            return;
        }

        List<String> addresses = SyncServer.localAddresses();
        content.getChildren().add(message(
                "En el teléfono abre Zyora → Listas → «Sincronizar con el PC» y escribe:"));

        GridPane grid = new GridPane();
        grid.getStyleClass().add("sync-grid");
        grid.addRow(0, fieldName("Dirección"), value(addresses.isEmpty()
                ? "Sin red: conecta el PC a Wi‑Fi"
                : String.join("\no ", addresses)));
        grid.addRow(1, fieldName("Código"), value(sync.code().substring(0, 3) + " " + sync.code().substring(3)));
        content.getChildren().add(grid);

        Label status = new Label();
        status.getStyleClass().add("sync-status");
        Consumer<SyncServer.Result> update = r -> status.setText(statusText(r));
        update.accept(sync.lastSync());
        sync.addListener(update);
        dialog.setOnHidden(e -> sync.removeListener(update));
        content.getChildren().add(status);

        content.getChildren().add(message(
                "El teléfono y el PC deben estar en la misma red Wi‑Fi y Zyora abierta en el PC. "
                        + "Después de la primera vez, el teléfono se sincroniza solo al abrir la app."));

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    static String statusText(SyncServer.Result result) {
        if (result == null) {
            return "Esperando al teléfono…";
        }
        ZonedDateTime at = result.at().atZone(ZoneId.systemDefault());
        String day = at.toLocalDate().equals(LocalDate.now()) ? "hoy" : Fmt.capitalize(Fmt.SHORT_DATE.format(at));
        return "✓ Sincronizado " + day + " a las " + Fmt.TIME.format(at) + " con " + result.device();
    }

    private static Label message(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("dialog-message");
        label.setWrapText(true);
        label.setMaxWidth(380);
        return label;
    }

    private static Label fieldName(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sync-field");
        return label;
    }

    private static Label value(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sync-value");
        return label;
    }
}
