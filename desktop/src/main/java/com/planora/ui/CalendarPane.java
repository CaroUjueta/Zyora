package com.planora.ui;

import com.planora.model.CalendarEvent;
import com.planora.model.Task;
import com.planora.service.Planner;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Dragboard;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Month, week and day views of events and scheduled tasks, plus the unscheduled tasks you can drag
 * onto the calendar to block time for them.
 */
class CalendarPane extends BorderPane implements CalendarActions {
    private enum Mode {
        MONTH("Mes"), WEEK("Semana"), DAY("Día");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private final Planner planner;
    private final Consumer<Task> onOpenTask;
    private final Label range = new Label();
    private final ListView<Task> backlog = new ListView<>();
    private final ToggleGroup modes = new ToggleGroup();
    private LocalDate anchor;
    private Mode mode = Mode.WEEK;
    private TimeGridView grid;

    CalendarPane(Planner planner, Consumer<Task> onOpenTask) {
        this.planner = planner;
        this.onOpenTask = onOpenTask;
        this.anchor = planner.today();
        getStyleClass().add("calendar-pane");

        Button today = new Button("Hoy");
        today.setOnAction(e -> {
            anchor = planner.today();
            refresh();
        });
        today.getStyleClass().add("today-button");
        Button prev = new Button("‹");
        prev.getStyleClass().add("nav-arrow");
        prev.setOnAction(e -> move(-1));
        Button next = new Button("›");
        next.getStyleClass().add("nav-arrow");
        next.setOnAction(e -> move(1));
        range.getStyleClass().add("range-label");

        HBox segmented = new HBox();
        segmented.getStyleClass().add("segmented");
        for (Mode m : Mode.values()) {
            ToggleButton button = new ToggleButton(m.label);
            button.setUserData(m);
            button.setToggleGroup(modes);
            button.setSelected(m == mode);
            button.setOnAction(e -> setMode(m));
            if (m.ordinal() == 0) {
                button.getStyleClass().add("first");
            } else if (m.ordinal() == Mode.values().length - 1) {
                button.getStyleClass().add("last");
            }
            segmented.getChildren().add(button);
        }

        Button newEvent = new Button("+ Nuevo evento");
        newEvent.getStyleClass().add("accent");
        newEvent.setOnAction(e -> createEvent(defaultEventStart(), false));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, today, prev, next, range, spacer, segmented, newEvent);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("calendar-toolbar");
        setTop(toolbar);

        Label backlogTitle = new Label("Tareas sin programar");
        backlogTitle.getStyleClass().add("section-title");
        Label hint = new Label("Arrastra una tarea a la agenda para reservarle tiempo.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        Label empty = new Label("Todo está programado.");
        empty.getStyleClass().add("hint");
        backlog.setPlaceholder(empty);
        backlog.getStyleClass().add("task-list");
        backlog.setCellFactory(v -> new TaskCell(planner, onOpenTask, t -> true));
        VBox.setVgrow(backlog, Priority.ALWAYS);
        VBox backlogPanel = new VBox(8, backlogTitle, hint, backlog);
        backlogPanel.getStyleClass().add("backlog-panel");
        setRight(backlogPanel);

        planner.addListener(this::refresh);
        refresh();
    }

    private void setMode(Mode m) {
        modes.getToggles().forEach(t -> t.setSelected(t.getUserData() == m));
        mode = m;
        refresh();
    }

    private void move(int direction) {
        anchor = switch (mode) {
            case MONTH -> anchor.plusMonths(direction);
            case WEEK -> anchor.plusWeeks(direction);
            case DAY -> anchor.plusDays(direction);
        };
        refresh();
    }

    private void refresh() {
        backlog.getItems().setAll(planner.unscheduledTasks());
        backlog.refresh();

        double scrollValue = grid == null ? -1 : grid.scrollValue();
        switch (mode) {
            case MONTH -> {
                grid = null;
                range.setText(Fmt.capitalize(Fmt.MONTH_YEAR.format(anchor)));
                setCenter(new MonthView(planner, YearMonth.from(anchor), this));
            }
            case WEEK -> {
                LocalDate monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate sunday = monday.plusDays(6);
                range.setText(monday.getMonth() == sunday.getMonth()
                        ? monday.getDayOfMonth() + " – " + Fmt.DAY_MONTH_YEAR.format(sunday)
                        : Fmt.DAY_MONTH.format(monday) + " – " + Fmt.DAY_MONTH_YEAR.format(sunday));
                grid = new TimeGridView(planner, Stream.iterate(monday, d -> d.plusDays(1)).limit(7).toList(),
                        this, scrollValue);
                setCenter(grid);
            }
            case DAY -> {
                range.setText(Fmt.capitalize(Fmt.LONG_DATE.format(anchor)));
                grid = new TimeGridView(planner, List.of(anchor), this, scrollValue);
                setCenter(grid);
            }
        }
    }

    private LocalDateTime defaultEventStart() {
        int hour = Math.min(planner.now().getHour() + 1, 23);
        return anchor.atTime(hour, 0);
    }

    // ---- CalendarActions -----------------------------------------------------------------

    @Override
    public void openTask(Task task) {
        onOpenTask.accept(task);
    }

    @Override
    public void editEvent(CalendarEvent event) {
        new EventDialog(event, false, getScene().getWindow()).showAndWait().ifPresent(action -> {
            switch (action) {
                case SAVE -> planner.changed();
                case DELETE -> planner.deleteEvent(event);
            }
        });
    }

    @Override
    public void createEvent(LocalDateTime start, boolean allDay) {
        CalendarEvent event = new CalendarEvent();
        event.setStart(start);
        event.setEnd(allDay ? start.plusDays(1) : start.plusHours(1));
        event.setAllDay(allDay);
        new EventDialog(event, true, getScene().getWindow()).showAndWait()
                .filter(action -> action == EventDialog.Action.SAVE)
                .ifPresent(action -> planner.addEvent(event));
    }

    @Override
    public void openDay(LocalDate day) {
        anchor = day;
        setMode(Mode.DAY);
    }

    @Override
    public void dropAt(Dragboard db, LocalDateTime start) {
        DragPayload.task(planner, db).ifPresent(task -> planner.scheduleTask(task, start));
        DragPayload.event(planner, db).ifPresent(event -> {
            Duration length = event.isAllDay()
                    ? Duration.ofHours(1)
                    : Duration.between(event.getStart(), event.getEnd());
            event.setAllDay(false);
            event.setStart(start);
            event.setEnd(start.plus(length));
            planner.changed();
        });
    }

    @Override
    public void dropOnDay(Dragboard db, LocalDate day, boolean allDayRow) {
        DragPayload.task(planner, db).ifPresent(task -> {
            if (task.isScheduled() && !allDayRow) {
                planner.scheduleTask(task, day.atTime(task.getScheduledStart().toLocalTime()));
            } else {
                task.setScheduledStart(null);
                task.setDueDate(day);
                planner.changed();
            }
        });
        DragPayload.event(planner, db).ifPresent(event -> {
            if (allDayRow && !event.isAllDay()) {
                event.setAllDay(true);
                event.setStart(day.atStartOfDay());
                event.setEnd(day.plusDays(1).atStartOfDay());
            } else {
                long shift = ChronoUnit.DAYS.between(event.getStart().toLocalDate(), day);
                event.setStart(event.getStart().plusDays(shift));
                event.setEnd(event.getEnd().plusDays(shift));
            }
            planner.changed();
        });
    }
}
