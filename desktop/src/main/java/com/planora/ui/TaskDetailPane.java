package com.planora.ui;

import com.planora.model.Step;
import com.planora.model.Task;
import com.planora.model.TaskList;
import com.planora.service.Planner;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Edits one task. Changes are applied as soon as they are made, like in Microsoft To Do. */
class TaskDetailPane extends VBox {
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final List<Integer> DURATIONS = List.of(15, 30, 45, 60, 90, 120);

    private final Planner planner;
    private final Runnable onClose;
    private Task task;
    private boolean loading;

    private final Label where = new Label();
    private final CheckBox completed = new CheckBox();
    private final TextArea title = new TextArea();
    private final ToggleButton important = new ToggleButton();
    private final VBox steps = new VBox(2);
    private final TextField newStep = new TextField();
    private final Button myDay = new Button();
    private final DatePicker dueDate = new DatePicker();
    private final DatePicker scheduleDate = new DatePicker();
    private final ComboBox<LocalTime> scheduleTime = new ComboBox<>();
    private final FlowPane durations = new FlowPane(6, 6);
    private final ToggleGroup durationGroup = new ToggleGroup();
    private final Label scheduleInfo = new Label();
    private final Button unschedule = new Button("Quitar");
    private final ComboBox<TaskList> list = new ComboBox<>();
    private final TextArea notes = new TextArea();
    private final Label created = new Label();

    TaskDetailPane(Planner planner, Runnable onClose) {
        this.planner = planner;
        this.onClose = onClose;
        getStyleClass().add("detail-pane");

        // Where the task lives, and the close button
        where.getStyleClass().add("detail-where");
        where.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(where, Priority.ALWAYS);
        HBox topBar = new HBox(8, where, iconButton("✕", onClose));
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Title, completion, importance and steps
        title.getStyleClass().add("detail-title");
        title.setWrapText(true);
        title.setPrefRowCount(1);
        title.textProperty().addListener((obs, old, text) -> fitTitle());
        title.widthProperty().addListener((obs, old, width) -> fitTitle());
        title.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                commitTitle();
            }
        });
        title.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                commitTitle();
            }
        });
        completed.setOnAction(e -> edit(t -> planner.setCompleted(t, completed.isSelected())));
        important.getStyleClass().add("star-toggle");
        important.setOnAction(e -> update(t -> t.setImportant(important.isSelected())));
        HBox.setHgrow(title, Priority.ALWAYS);
        HBox head = new HBox(8, completed, title, important);
        head.setAlignment(Pos.TOP_LEFT);
        completed.setPadding(new Insets(8, 0, 0, 0));

        newStep.setPromptText("+ Agregar paso");
        newStep.getStyleClass().add("inline-field");
        newStep.setOnAction(e -> {
            String text = newStep.getText().strip();
            if (!text.isEmpty()) {
                newStep.clear();
                update(t -> t.getSteps().add(new Step(text)));
            }
        });
        VBox titleCard = card(head, steps, newStep);

        // My Day and due date
        myDay.getStyleClass().add("row-button");
        myDay.setMaxWidth(Double.MAX_VALUE);
        myDay.setAlignment(Pos.CENTER_LEFT);
        myDay.setOnAction(e -> update(t -> t.setMyDay(
                planner.today().equals(t.getMyDay()) ? null : planner.today())));
        dueDate.setPromptText("Sin fecha");
        dueDate.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dueDate, Priority.ALWAYS);
        dueDate.valueProperty().addListener((obs, old, date) -> update(t -> t.setDueDate(date)));
        Button clearDue = iconButton("✕", () -> dueDate.setValue(null));
        HBox dueRow = new HBox(6, dueDate, clearDue);
        dueRow.setAlignment(Pos.CENTER_LEFT);
        VBox planCard = card(myDay, section("⚑", "Fecha de vencimiento"), dueRow);

        // Time block on the calendar
        scheduleTime.getItems().setAll(Fmt.timeOptions());
        scheduleTime.setConverter(Fmt.timeConverter());
        scheduleTime.setPromptText("Hora");
        scheduleTime.setVisibleRowCount(10);
        scheduleDate.setPromptText("Día");
        scheduleDate.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(scheduleDate, Priority.ALWAYS);
        durationGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null && !loading) {
                old.setSelected(true); // a duration is always chosen
            } else if (now != null) {
                update(t -> t.setDurationMinutes((Integer) now.getUserData()));
            }
        });
        Button schedule = new Button("Programar");
        schedule.getStyleClass().add("accent");
        schedule.setOnAction(e -> scheduleTask());
        unschedule.setOnAction(e -> edit(planner::unscheduleTask));
        scheduleInfo.getStyleClass().add("hint");
        scheduleInfo.setWrapText(true);
        VBox scheduleCard = card(section("▦", "Bloque en el calendario"),
                new HBox(6, scheduleDate, scheduleTime), fieldLabel("Duración"), durations,
                new HBox(6, schedule, unschedule), scheduleInfo);

        // List and notes
        list.setMaxWidth(Double.MAX_VALUE);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(TaskList item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "●  " + item.getName());
                setStyle(empty || item == null ? "" : "-fx-padding: 6 10 6 " + (10 + 16 * (planner.pathOf(item).size() - 1))
                        + "; -fx-text-fill: " + Palette.ink(item.getColor()) + ";");
            }
        });
        list.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(TaskList item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Fmt.path(planner.pathOf(item)));
            }
        });
        list.valueProperty().addListener((obs, old, l) -> {
            if (l != null) {
                update(t -> t.setListId(l.getId()));
            }
        });
        notes.setPromptText("Agregar una nota");
        notes.setWrapText(true);
        notes.setPrefRowCount(5);
        notes.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && task != null && !notes.getText().equals(task.getNotes())) {
                update(t -> t.setNotes(notes.getText()));
            }
        });
        VBox moreCard = card(section("≡", "Lista"), list, section("✎", "Notas"), notes);

        VBox content = new VBox(12, titleCard, planCard, scheduleCard, moreCard);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("detail-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Footer
        Button delete = new Button("Eliminar tarea");
        delete.getStyleClass().add("danger");
        delete.setOnAction(e -> confirmDelete());
        created.getStyleClass().add("hint");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, created, spacer, delete);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("detail-footer");

        getChildren().addAll(topBar, scroll, footer);
        planner.addListener(this::reload);
    }

    void show(Task task) {
        this.task = task;
        reload();
    }

    private void reload() {
        if (task == null) {
            return;
        }
        if (planner.findTask(task.getId()).isEmpty()) {
            task = null;
            onClose.run();
            return;
        }
        loading = true;
        try {
            LocalDate today = planner.today();
            where.setText(Fmt.path(planner.pathOf(planner.listOf(task))));
            completed.setSelected(task.isCompleted());
            title.setText(task.getTitle());
            important.setSelected(task.isImportant());
            important.setText(task.isImportant() ? "★" : "☆");
            rebuildSteps();

            boolean inMyDay = today.equals(task.getMyDay());
            myDay.setText(inMyDay ? "☀  En Mi día" : "☀  Agregar a Mi día");
            myDay.pseudoClassStateChanged(ACTIVE, inMyDay);
            dueDate.setValue(task.getDueDate());

            if (task.isScheduled()) {
                scheduleDate.setValue(task.getScheduledStart().toLocalDate());
                scheduleTime.setValue(task.getScheduledStart().toLocalTime());
                scheduleInfo.setText("Programada: " + Fmt.SHORT_DATE.format(task.getScheduledStart()) + ", "
                        + Fmt.TIME.format(task.getScheduledStart()) + " – " + Fmt.TIME.format(task.getScheduledEnd()));
            } else {
                scheduleDate.setValue(today);
                scheduleTime.setValue(nextQuarterHour(planner.now().toLocalTime()));
                scheduleInfo.setText("Elige día y hora, o arrastra la tarea al calendario.");
            }
            rebuildDurations();
            unschedule.setDisable(!task.isScheduled());

            list.getItems().setAll(planner.listTree());
            list.setValue(planner.listOf(task));
            notes.setText(task.getNotes() == null ? "" : task.getNotes());
            created.setText("Creada el " + Fmt.SHORT_DATE.format(task.getCreatedAt()));
        } finally {
            loading = false;
        }
    }

    private void rebuildSteps() {
        steps.getChildren().clear();
        for (Step step : task.getSteps()) {
            CheckBox box = new CheckBox(step.getTitle());
            box.setSelected(step.isDone());
            box.setWrapText(true);
            box.setOnAction(e -> update(t -> step.setDone(box.isSelected())));
            box.setMaxWidth(Double.MAX_VALUE);
            if (step.isDone()) {
                box.getStyleClass().add("done");
            }
            HBox.setHgrow(box, Priority.ALWAYS);
            Button remove = iconButton("✕", () -> update(t -> t.getSteps().remove(step)));
            HBox row = new HBox(6, box, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("step-row");
            steps.getChildren().add(row);
        }
    }

    /** Duration chips; a custom duration (from older versions) gets its own chip. */
    private void rebuildDurations() {
        int current = task.getDurationMinutes();
        List<Integer> options = new ArrayList<>(DURATIONS);
        if (!options.contains(current)) {
            options.add(current);
            options.sort(null);
        }
        durationGroup.getToggles().clear();
        durations.getChildren().clear();
        for (int minutes : options) {
            ToggleButton chip = new ToggleButton(durationText(minutes));
            chip.getStyleClass().add("chip");
            chip.setUserData(minutes);
            chip.setToggleGroup(durationGroup);
            chip.setSelected(minutes == current);
            durations.getChildren().add(chip);
        }
    }

    private static String durationText(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        return minutes / 60 + " h" + (minutes % 60 == 0 ? "" : " " + minutes % 60);
    }

    /** Grows the title box so long titles wrap onto as many lines as they need. */
    private void fitTitle() {
        Text probe = new Text(title.getText().isEmpty() ? " " : title.getText());
        probe.setFont(title.getFont());
        probe.setWrappingWidth(Math.max(40, title.getWidth() - 24));
        title.setPrefHeight(probe.getLayoutBounds().getHeight() + 20);
    }

    private void commitTitle() {
        if (loading || task == null) {
            return;
        }
        String text = title.getText().replace('\n', ' ').strip();
        if (text.isEmpty()) {
            title.setText(task.getTitle());
        } else if (!text.equals(task.getTitle())) {
            update(t -> t.setTitle(text));
        }
    }

    private void scheduleTask() {
        LocalDate day = scheduleDate.getValue();
        LocalTime time = scheduleTime.getValue();
        if (day == null || time == null) {
            return;
        }
        edit(t -> planner.scheduleTask(t, day.atTime(time)));
    }

    private void confirmDelete() {
        if (task != null && Dialogs.confirm(getScene().getWindow(), "¿Eliminar tarea?",
                "Se eliminará «" + task.getTitle() + "». Esta acción no se puede deshacer.", "Eliminar")) {
            planner.deleteTask(task);
        }
    }

    /** Runs an action that saves on its own (through a Planner method). */
    private void edit(Consumer<Task> action) {
        if (!loading && task != null) {
            action.accept(task);
        }
    }

    /** Applies an in-place change to the task and saves it. */
    private void update(Consumer<Task> change) {
        if (!loading && task != null) {
            change.accept(task);
            planner.changed();
        }
    }

    private static LocalTime nextQuarterHour(LocalTime time) {
        int minutes = (time.getHour() * 60 + time.getMinute()) / 15 * 15 + 15;
        return minutes >= 24 * 60 ? LocalTime.of(23, 45) : LocalTime.of(minutes / 60, minutes % 60);
    }

    private static VBox card(Node... children) {
        VBox card = new VBox(8, children);
        card.getStyleClass().add("detail-card");
        return card;
    }

    /** A small heading with an icon, for each block of the panel. */
    private static HBox section(String icon, String text) {
        Label i = new Label(icon);
        i.getStyleClass().add("section-icon");
        HBox row = new HBox(6, i, fieldLabel(text));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static Button iconButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("icon-button");
        button.setOnAction(e -> action.run());
        return button;
    }
}
