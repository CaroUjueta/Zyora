package com.zyora.ui;

import com.zyora.service.AgendaItem;
import com.zyora.service.Planner;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;

/** The week and day views: day headers, an all-day row and a scrollable 24-hour grid. */
class TimeGridView extends BorderPane {
    private static final double GUTTER = 56;
    private static final int FIRST_VISIBLE_HOUR = 7;

    private final ScrollPane scroll;

    TimeGridView(Planner planner, List<LocalDate> days, CalendarActions actions, double scrollValue) {
        getStyleClass().add("time-grid");
        LocalDate today = planner.today();

        HBox header = new HBox(fixed(new Region(), GUTTER));
        header.getStyleClass().add("grid-header");
        Label allDayLabel = new Label("Todo el día");
        allDayLabel.getStyleClass().add("hour-label");
        allDayLabel.setWrapText(true);
        HBox allDayRow = new HBox(fixed(allDayLabel, GUTTER));
        allDayRow.getStyleClass().add("all-day-row");

        VBox hours = new VBox();
        for (int h = 0; h < 24; h++) {
            Label hour = new Label(h == 0 ? "" : String.format("%02d:00", h));
            hour.getStyleClass().add("hour-label");
            hour.setAlignment(Pos.TOP_RIGHT);
            hour.setMinHeight(DayColumn.HOUR_HEIGHT);
            hour.setPrefHeight(DayColumn.HOUR_HEIGHT);
            hour.setMaxWidth(Double.MAX_VALUE);
            hours.getChildren().add(hour);
        }
        HBox body = new HBox(fixed(hours, GUTTER));

        for (LocalDate day : days) {
            List<AgendaItem> items = planner.agendaFor(day);
            header.getChildren().add(grow(dayHeader(day, today, actions)));
            allDayRow.getChildren().add(grow(allDayCell(day, items, actions)));
            body.getChildren().add(grow(new DayColumn(day,
                    items.stream().filter(i -> !i.allDay()).toList(), planner.now(), planner, actions)));
        }

        scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        // Keep the header columns aligned with the grid by reserving the scrollbar's width.
        Region headerPad = new Region();
        Region allDayPad = new Region();
        header.getChildren().add(headerPad);
        allDayRow.getChildren().add(allDayPad);
        scroll.viewportBoundsProperty().addListener((obs, old, bounds) -> {
            double bar = Math.max(0, scroll.getWidth() - bounds.getWidth());
            fixed(headerPad, bar);
            fixed(allDayPad, bar);
        });

        if (scrollValue >= 0) {
            scroll.setVvalue(scrollValue);
        } else {
            scroll.viewportBoundsProperty().addListener(new ChangeListener<>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends Bounds> obs, Bounds old, Bounds bounds) {
                    double max = 24 * DayColumn.HOUR_HEIGHT - bounds.getHeight();
                    if (bounds.getHeight() > 0 && max > 0) {
                        scroll.setVvalue(Math.min(1, FIRST_VISIBLE_HOUR * DayColumn.HOUR_HEIGHT / max));
                        scroll.viewportBoundsProperty().removeListener(this);
                    }
                }
            });
        }

        setTop(new VBox(header, allDayRow));
        setCenter(scroll);
    }

    double scrollValue() {
        return scroll.getVvalue();
    }

    private static VBox dayHeader(LocalDate day, LocalDate today, CalendarActions actions) {
        Label weekday = new Label(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Fmt.ES));
        weekday.getStyleClass().add("weekday");
        Label number = new Label(String.valueOf(day.getDayOfMonth()));
        number.getStyleClass().add("daynum");
        VBox cell = new VBox(0, weekday, number);
        cell.getStyleClass().add("grid-header-cell");
        if (day.equals(today)) {
            cell.getStyleClass().add("today");
        }
        cell.setOnMouseClicked(e -> actions.openDay(day));
        return cell;
    }

    private static VBox allDayCell(LocalDate day, List<AgendaItem> items, CalendarActions actions) {
        VBox cell = new VBox(2);
        cell.getStyleClass().add("all-day-cell");
        items.stream().filter(AgendaItem::allDay)
                .forEach(item -> cell.getChildren().add(AgendaNodes.chip(item, false, actions)));
        cell.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                actions.createEvent(day.atStartOfDay(), true);
            }
        });
        cell.setOnDragOver(e -> {
            if (DragPayload.accepts(e.getDragboard())) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        cell.setOnDragDropped(e -> {
            actions.dropOnDay(e.getDragboard(), day, true);
            e.setDropCompleted(true);
            e.consume();
        });
        return cell;
    }

    /** Equal-width growing column: same min and pref width so extra space is split evenly. */
    private static <T extends Region> T grow(T node) {
        node.setMinWidth(0);
        node.setPrefWidth(10);
        node.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(node, Priority.ALWAYS);
        return node;
    }

    private static <T extends Region> Node fixed(T node, double width) {
        node.setMinWidth(width);
        node.setPrefWidth(width);
        node.setMaxWidth(width);
        return node;
    }
}
