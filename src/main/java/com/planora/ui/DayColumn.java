package com.planora.ui;

import com.planora.service.AgendaItem;
import com.planora.service.Planner;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One day of the week/day views: 24 hour slots with timed items laid out side by side when they
 * overlap. Double-click creates an event; tasks and events can be dropped onto a time.
 */
class DayColumn extends Pane {
    static final double HOUR_HEIGHT = 48;
    private static final double MIN_BLOCK_HEIGHT = 20;

    private record Placed(Node node, int startMinute, int endMinute, int column, int columns) {
    }

    private final List<Region> slots = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private final Region nowLine = new Region();
    private final int nowMinute;

    DayColumn(LocalDate day, List<AgendaItem> items, LocalDateTime now, Planner planner, CalendarActions actions) {
        getStyleClass().add("day-column");
        setMinSize(0, 24 * HOUR_HEIGHT);
        setPrefSize(10, 24 * HOUR_HEIGHT);

        for (int h = 0; h < 24; h++) {
            Region slot = new Region();
            slot.getStyleClass().add("hour-slot");
            slots.add(slot);
            getChildren().add(slot);
        }
        place(day, items, planner, actions);

        boolean isToday = now != null && now.toLocalDate().equals(day);
        nowMinute = isToday ? now.getHour() * 60 + now.getMinute() : -1;
        if (isToday) {
            getStyleClass().add("today");
            nowLine.getStyleClass().add("now-line");
            nowLine.setMouseTransparent(true);
            getChildren().add(nowLine);
        }

        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                actions.createEvent(day.atStartOfDay().plusMinutes(minuteAt(e.getY(), 30)), false);
            }
        });
        setOnDragOver(e -> {
            if (DragPayload.accepts(e.getDragboard())) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        setOnDragDropped(e -> {
            actions.dropAt(e.getDragboard(), day.atStartOfDay().plusMinutes(minuteAt(e.getY(), 15)));
            e.setDropCompleted(true);
            e.consume();
        });
    }

    /** Groups overlapping items into clusters and gives each item the first free column. */
    private void place(LocalDate day, List<AgendaItem> items, Planner planner, CalendarActions actions) {
        List<AgendaItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(AgendaItem::start)
                .thenComparing(AgendaItem::end, Comparator.reverseOrder()));

        int i = 0;
        while (i < sorted.size()) {
            List<AgendaItem> cluster = new ArrayList<>();
            int clusterEnd = endMinute(day, sorted.get(i));
            while (i < sorted.size() && (cluster.isEmpty() || startMinute(sorted.get(i)) < clusterEnd)) {
                clusterEnd = Math.max(clusterEnd, endMinute(day, sorted.get(i)));
                cluster.add(sorted.get(i++));
            }

            List<Integer> columnEnds = new ArrayList<>();
            int[] columns = new int[cluster.size()];
            for (int k = 0; k < cluster.size(); k++) {
                int start = startMinute(cluster.get(k));
                int col = 0;
                while (col < columnEnds.size() && columnEnds.get(col) > start) {
                    col++;
                }
                if (col == columnEnds.size()) {
                    columnEnds.add(0);
                }
                columnEnds.set(col, endMinute(day, cluster.get(k)));
                columns[k] = col;
            }

            for (int k = 0; k < cluster.size(); k++) {
                AgendaItem item = cluster.get(k);
                Node block = AgendaNodes.block(item, planner, actions);
                placed.add(new Placed(block, startMinute(item), endMinute(day, item), columns[k], columnEnds.size()));
                getChildren().add(block);
            }
        }
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        for (int h = 0; h < slots.size(); h++) {
            slots.get(h).resizeRelocate(0, h * HOUR_HEIGHT, width, HOUR_HEIGHT);
        }
        double usable = width - 6;
        for (Placed p : placed) {
            double columnWidth = usable / p.columns();
            double y = y(p.startMinute());
            double height = Math.max(MIN_BLOCK_HEIGHT, y(p.endMinute()) - y - 2);
            p.node().resizeRelocate(2 + p.column() * columnWidth, y + 1, columnWidth - 2, height);
        }
        if (nowMinute >= 0) {
            nowLine.resizeRelocate(0, y(nowMinute) - 1, width, 2);
        }
    }

    private static double y(int minute) {
        return minute * HOUR_HEIGHT / 60;
    }

    private static int minuteAt(double y, int step) {
        int minute = (int) (y / HOUR_HEIGHT * 60) / step * step;
        return Math.max(0, Math.min(minute, 24 * 60 - step));
    }

    private static int startMinute(AgendaItem item) {
        return item.start().getHour() * 60 + item.start().getMinute();
    }

    private static int endMinute(LocalDate day, AgendaItem item) {
        if (item.end().toLocalDate().isAfter(day)) {
            return 24 * 60;
        }
        return Math.max(startMinute(item) + 15, item.end().getHour() * 60 + item.end().getMinute());
    }
}
