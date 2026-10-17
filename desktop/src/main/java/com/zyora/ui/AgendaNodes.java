package com.zyora.ui;

import com.zyora.service.AgendaItem;
import com.zyora.service.Planner;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Builds the nodes that represent agenda items on the calendar views. */
final class AgendaNodes {
    private AgendaNodes() {
    }

    /** A one-line chip, used in the month view and on the all-day row. */
    static Label chip(AgendaItem item, boolean showTime, CalendarActions actions) {
        String prefix = item.isTask() ? (item.completed() ? "✓ " : "☐ ") : "";
        String time = showTime && !item.allDay() ? Fmt.TIME.format(item.start()) + " " : "";
        Label chip = new Label(prefix + time + item.title());
        chip.getStyleClass().add("cal-chip");
        if (item.completed()) {
            chip.getStyleClass().add("completed");
        }
        chip.setStyle(Palette.itemStyle(item.color()));
        chip.setMaxWidth(Double.MAX_VALUE);
        chip.setMinWidth(0);
        wire(chip, item, actions);
        return chip;
    }

    /** A time block for the week and day views. Task blocks get a checkbox to complete them. */
    static VBox block(AgendaItem item, Planner planner, CalendarActions actions) {
        Label title = new Label(item.title());
        title.getStyleClass().add("cal-block-title");
        title.setMinWidth(0);
        Label time = new Label(Fmt.TIME.format(item.start()) + " – " + Fmt.TIME.format(item.end()));
        time.getStyleClass().add("cal-block-time");
        time.setMinWidth(0);

        Node top = title;
        if (item.isTask()) {
            CheckBox check = new CheckBox();
            check.setSelected(item.completed());
            check.setOnAction(e -> planner.setCompleted(item.task(), check.isSelected()));
            HBox row = new HBox(4, check, title);
            row.setMinWidth(0);
            top = row;
        }

        VBox block = new VBox(0, top, time);
        block.getStyleClass().addAll("cal-block", item.isTask() ? "task-block" : "event-block");
        if (item.completed()) {
            block.getStyleClass().add("completed");
        }
        block.setStyle(Palette.itemStyle(item.color()));
        block.setMinSize(0, 0);
        wire(block, item, actions);
        return block;
    }

    private static void wire(Node node, AgendaItem item, CalendarActions actions) {
        node.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                if (item.isTask()) {
                    actions.openTask(item.task());
                } else {
                    actions.editEvent(item.event());
                }
            }
            e.consume();
        });
        node.setOnDragDetected(e -> {
            Dragboard db = node.startDragAndDrop(TransferMode.MOVE);
            db.setContent(item.isTask() ? DragPayload.of(item.task()) : DragPayload.of(item.event()));
            db.setDragView(node.snapshot(null, null));
            e.consume();
        });
    }
}
