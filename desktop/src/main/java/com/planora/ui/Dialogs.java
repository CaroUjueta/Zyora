package com.planora.ui;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Floating windows in the app's own style: no system title bar, rounded corners and a soft
 * shadow. They can be dragged from any spot that is not a control, and Esc cancels them.
 */
final class Dialogs {
    private Dialogs() {
    }

    /** Styles a dialog and makes it draggable. Call before showing it. */
    static <T> Dialog<T> decorate(Dialog<T> dialog, Window owner) {
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.TRANSPARENT);
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().setAll(owner.getScene().getStylesheets());
        pane.getStyleClass().add("planora-dialog");
        dialog.setOnShowing(e -> pane.getScene().setFill(Color.TRANSPARENT));

        double[] grab = new double[2];
        pane.setOnMousePressed(e -> {
            grab[0] = e.getScreenX() - pane.getScene().getWindow().getX();
            grab[1] = e.getScreenY() - pane.getScene().getWindow().getY();
        });
        pane.setOnMouseDragged(e -> {
            if (!isOnControl(e.getPickResult().getIntersectedNode())) {
                pane.getScene().getWindow().setX(e.getScreenX() - grab[0]);
                pane.getScene().getWindow().setY(e.getScreenY() - grab[1]);
            }
        });
        return dialog;
    }

    /** Asks before a destructive action; the confirm button is painted as dangerous. */
    static boolean confirm(Window owner, String header, String text, String action) {
        Dialog<Boolean> dialog = decorate(new Dialog<>(), owner);
        dialog.setHeaderText(header);
        ButtonType ok = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ok);
        dialog.getDialogPane().lookupButton(ok).getStyleClass().add("danger");
        dialog.getDialogPane().setContent(message(text));
        dialog.setResultConverter(button -> button == ok);
        return dialog.showAndWait().orElse(false);
    }

    /** Asks for a single line of text. Empty answers count as cancelled. */
    static Optional<String> prompt(Window owner, String header, String initial, String placeholder, String action) {
        Dialog<String> dialog = decorate(new Dialog<>(), owner);
        dialog.setHeaderText(header);
        ButtonType ok = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ok);
        TextField field = new TextField(initial);
        field.setPromptText(placeholder);
        field.setPrefWidth(320);
        VBox content = new VBox(field);
        content.getStyleClass().add("dialog-body");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().lookupButton(ok).disableProperty().bind(field.textProperty().map(String::isBlank));
        dialog.setResultConverter(button -> button == ok ? field.getText().strip() : null);
        dialog.setOnShown(e -> {
            field.requestFocus();
            field.selectAll();
        });
        return dialog.showAndWait().filter(s -> !s.isEmpty());
    }

    static void info(Window owner, String header, String text) {
        Dialog<ButtonType> dialog = decorate(new Dialog<>(), owner);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Entendido", ButtonBar.ButtonData.OK_DONE));
        dialog.getDialogPane().setContent(message(text));
        dialog.showAndWait();
    }

    private static Node message(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("dialog-message");
        label.setWrapText(true);
        label.setMaxWidth(340);
        VBox content = new VBox(label);
        content.getStyleClass().add("dialog-body");
        return content;
    }

    private static boolean isOnControl(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n instanceof TextInputControl || n instanceof ButtonBase || n instanceof ComboBoxBase<?>) {
                return true;
            }
        }
        return false;
    }
}
