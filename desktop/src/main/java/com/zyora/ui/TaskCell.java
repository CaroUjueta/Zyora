package com.zyora.ui;

import com.zyora.model.Step;
import com.zyora.model.Task;
import com.zyora.service.Planner;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A task row: checkbox, title, a line of details and the importance star. Rows can be dragged. */
class TaskCell extends ListCell<Task> {
    private static final PseudoClass IMPORTANT = PseudoClass.getPseudoClass("important");
    private static final PseudoClass OVERDUE = PseudoClass.getPseudoClass("overdue");

    private final Planner planner;
    private final Predicate<Task> showListName;
    private final CheckBox check = new CheckBox();
    private final Label title = new Label();
    private final Label meta = new Label();
    private final Button star = new Button();
    private final HBox root;

    TaskCell(Planner planner, Consumer<Task> onOpen, Predicate<Task> showListName) {
        this.planner = planner;
        this.showListName = showListName;

        title.getStyleClass().add("task-title");
        meta.getStyleClass().add("task-meta");
        star.getStyleClass().add("star-button");
        star.setFocusTraversable(false);
        VBox text = new VBox(2, title, meta);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        root = new HBox(10, check, text, star);
        root.setAlignment(Pos.CENTER_LEFT);
        root.getStyleClass().add("task-row");

        check.setOnAction(e -> {
            if (getItem() != null) {
                planner.setCompleted(getItem(), check.isSelected());
            }
        });
        star.setOnAction(e -> {
            if (getItem() != null) {
                getItem().setImportant(!getItem().isImportant());
                planner.changed();
            }
        });
        root.setOnMouseClicked(e -> {
            if (getItem() != null && e.getButton() == MouseButton.PRIMARY
                    && !isWithin(e.getPickResult().getIntersectedNode(), check, star)) {
                onOpen.accept(getItem());
            }
        });
        setOnDragDetected(e -> {
            if (getItem() != null) {
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                db.setContent(DragPayload.of(getItem()));
                db.setDragView(root.snapshot(null, null));
                e.consume();
            }
        });
    }

    @Override
    protected void updateItem(Task task, boolean empty) {
        super.updateItem(task, empty);
        setText(null);
        if (empty || task == null) {
            setGraphic(null);
            return;
        }
        LocalDate today = planner.today();
        check.setSelected(task.isCompleted());
        title.setText(task.getTitle());
        title.getStyleClass().remove("done");
        if (task.isCompleted()) {
            title.getStyleClass().add("done");
        }
        star.setText(task.isImportant() ? "★" : "☆");
        star.pseudoClassStateChanged(IMPORTANT, task.isImportant());

        String details = details(task, today);
        meta.setText(details);
        meta.setVisible(!details.isEmpty());
        meta.setManaged(!details.isEmpty());
        meta.pseudoClassStateChanged(OVERDUE, task.isOverdue(today));
        setGraphic(root);
    }

    private String details(Task task, LocalDate today) {
        List<String> parts = new ArrayList<>();
        if (showListName.test(task)) {
            parts.add(planner.listOf(task).getName());
        }
        if (task.isInMyDay(today)) {
            parts.add("☀ Mi día");
        }
        if (task.isScheduled()) {
            parts.add("◷ " + Fmt.SHORT_DATE.format(task.getScheduledStart()) + ", "
                    + Fmt.TIME.format(task.getScheduledStart()));
        }
        if (task.getDueDate() != null) {
            parts.add(task.isOverdue(today) ? "Venció " + Fmt.SHORT_DATE.format(task.getDueDate())
                    : task.getDueDate().equals(today) ? "Vence hoy"
                    : "Vence " + Fmt.SHORT_DATE.format(task.getDueDate()));
        }
        if (!task.getSteps().isEmpty()) {
            long done = task.getSteps().stream().filter(Step::isDone).count();
            parts.add(done + " de " + task.getSteps().size());
        }
        if (task.getNotes() != null && !task.getNotes().isBlank()) {
            parts.add("✎ Nota");
        }
        return String.join("  ·  ", parts);
    }

    private static boolean isWithin(Node node, Node... containers) {
        for (Node n = node; n != null; n = n.getParent()) {
            for (Node c : containers) {
                if (n == c) {
                    return true;
                }
            }
        }
        return false;
    }
}
