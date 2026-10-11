package com.planora.ui;

import com.planora.service.AgendaItem;
import com.planora.service.Planner;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Six weeks, Monday first, with up to three items per day. */
class MonthView extends GridPane {
    private static final int MAX_ITEMS = 3;

    MonthView(Planner planner, YearMonth month, CalendarActions actions) {
        getStyleClass().add("month-view");
        for (int c = 0; c < 7; c++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 7);
            getColumnConstraints().add(col);
            DayOfWeek dow = DayOfWeek.MONDAY.plus(c);
            Label name = new Label(Fmt.capitalize(dow.getDisplayName(TextStyle.SHORT, Fmt.ES)));
            name.getStyleClass().add("weekday-header");
            add(name, c, 0);
        }
        getRowConstraints().add(new RowConstraints());
        for (int r = 0; r < 6; r++) {
            RowConstraints row = new RowConstraints();
            row.setVgrow(Priority.ALWAYS);
            getRowConstraints().add(row);
        }

        LocalDate today = planner.today();
        LocalDate first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = 0; i < 42; i++) {
            LocalDate day = first.plusDays(i);
            add(cell(planner, day, month, today, actions), i % 7, 1 + i / 7);
        }
    }

    private VBox cell(Planner planner, LocalDate day, YearMonth month, LocalDate today, CalendarActions actions) {
        Label number = new Label(String.valueOf(day.getDayOfMonth()));
        number.getStyleClass().add("day-number");
        number.setOnMouseClicked(e -> {
            actions.openDay(day);
            e.consume();
        });

        VBox cell = new VBox(2, number);
        cell.getStyleClass().add("month-cell");
        if (!YearMonth.from(day).equals(month)) {
            cell.getStyleClass().add("other-month");
        }
        if (day.equals(today)) {
            cell.getStyleClass().add("today");
        }

        List<AgendaItem> items = planner.agendaFor(day);
        items.stream().limit(MAX_ITEMS).forEach(item -> cell.getChildren().add(AgendaNodes.chip(item, true, actions)));
        if (items.size() > MAX_ITEMS) {
            Label more = new Label("+" + (items.size() - MAX_ITEMS) + " más");
            more.getStyleClass().add("more-label");
            more.setOnMouseClicked(e -> {
                actions.openDay(day);
                e.consume();
            });
            cell.getChildren().add(more);
        }

        // Zero preferred height so every week row gets the same share of the space.
        cell.setMinSize(0, 0);
        cell.setPrefHeight(0);
        cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(cell.widthProperty());
        clip.heightProperty().bind(cell.heightProperty());
        cell.setClip(clip);

        cell.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                actions.createEvent(day.atTime(9, 0), false);
            }
        });
        cell.setOnDragOver(e -> {
            if (DragPayload.accepts(e.getDragboard())) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        cell.setOnDragEntered(e -> cell.getStyleClass().add("drop-target"));
        cell.setOnDragExited(e -> cell.getStyleClass().remove("drop-target"));
        cell.setOnDragDropped(e -> {
            actions.dropOnDay(e.getDragboard(), day, false);
            e.setDropCompleted(true);
            e.consume();
        });
        return cell;
    }
}
