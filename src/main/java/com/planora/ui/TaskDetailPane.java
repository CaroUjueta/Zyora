package com.planora.ui;

import com.planora.model.Step;
import com.planora.model.Task;
import com.planora.model.TaskList;
import com.planora.service.Planner;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Consumer;

/** Edits one task. Changes are applied as soon as they are made, like in Microsoft To Do. */
class TaskDetailPane extends VBox {
    private final Planner planner;
    private final Runnable onClose;
    private Task task;
    private boolean loading;

    private final CheckBox completed = new CheckBox();
    private final TextField title = new TextField();
    private final ToggleButton important = new ToggleButton();
    private final VBox steps = new VBox(4);
    private final TextField newStep = new TextField();
    private final Button myDay = new Button();
    private final DatePicker dueDate = new DatePicker();
    private final DatePicker scheduleDate = new DatePicker();
    private final ComboBox<LocalTime> scheduleTime = new ComboBox<>();
    private final Spinner<Integer> duration = new Spinner<>(15, 8 * 60, 30, 15);
    private final Label scheduleInfo = new Label();
    private final Button unschedule = new Button("Quitar del calendario");
    private final ComboBox<TaskList> list = new ComboBox<>();
    private final TextArea notes = new TextArea();
    private final Label created = new Label();

    TaskDetailPane(Planner planner, Runnable onClose) {
        this.planner = planner;
        this.onClose = onClose;
        getStyleClass().add("detail-pane");

        // Title, completion and importance
        title.getStyleClass().add("detail-title");
        title.setOnAction(e -> commitTitle());
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
        head.setAlignment(Pos.CENTER_LEFT);

        newStep.setPromptText("+ Agregar paso");
        newStep.setOnAction(e -> {
            String text = newStep.getText().strip();
            if (!text.isEmpty()) {
                newStep.clear();
                update(t -> t.getSteps().add(new Step(text)));
            }
        });
        VBox titleCard = card(head, steps, newStep);

        // My Day and due date
        myDay.setMaxWidth(Double.MAX_VALUE);
        myDay.setOnAction(e -> update(t -> t.setMyDay(
                planner.today().equals(t.getMyDay()) ? null : planner.today())));
        dueDate.setPromptText("Sin fecha");
        dueDate.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dueDate, Priority.ALWAYS);
        dueDate.valueProperty().addListener((obs, old, date) -> update(t -> t.setDueDate(date)));
        Button clearDue = iconButton("✕", () -> dueDate.setValue(null));
        VBox planCard = card(myDay, fieldLabel("Fecha de vencimiento"), new HBox(6, dueDate, clearDue));

        // Time block on the calendar
        scheduleTime.getItems().setAll(Fmt.timeOptions());
        scheduleTime.setConverter(Fmt.timeConverter());
        scheduleTime.setPromptText("Hora");
        scheduleTime.setVisibleRowCount(10);
        scheduleDate.setPromptText("Día");
        scheduleDate.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(scheduleDate, Priority.ALWAYS);
        duration.setEditable(true);
        duration.setPrefWidth(90);
        Button schedule = new Button("Programar");
        schedule.getStyleClass().add("accent");
        schedule.setOnAction(e -> scheduleTask());
        unschedule.setOnAction(e -> edit(planner::unscheduleTask));
        scheduleInfo.getStyleClass().add("hint");
        scheduleInfo.setWrapText(true);
        HBox durationRow = new HBox(8, fieldLabel("Duración (min)"), duration);
        durationRow.setAlignment(Pos.CENTER_LEFT);
        VBox scheduleCard = card(fieldLabel("Bloque en el calendario"),
                new HBox(6, scheduleDate, scheduleTime), durationRow,
                new HBox(6, schedule, unschedule), scheduleInfo);

        // List and notes
        list.setMaxWidth(Double.MAX_VALUE);
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
        VBox moreCard = card(fieldLabel("Lista"), list, fieldLabel("Notas"), notes);

        VBox content = new VBox(12, titleCard, planCard, scheduleCard, moreCard);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("detail-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Footer
        Button close = iconButton("⟩", onClose);
        Button delete = new Button("Eliminar");
        delete.getStyleClass().add("danger");
        delete.setOnAction(e -> confirmDelete());
        created.getStyleClass().add("hint");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, close, created, spacer, delete);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(scroll, footer);
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
            completed.setSelected(task.isCompleted());
            title.setText(task.getTitle());
            important.setSelected(task.isImportant());
            important.setText(task.isImportant() ? "★" : "☆");
            rebuildSteps();

            myDay.setText(today.equals(task.getMyDay()) ? "☀ Quitar de Mi día" : "☀ Agregar a Mi día");
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
            duration.getValueFactory().setValue(task.getDurationMinutes());
            unschedule.setDisable(!task.isScheduled());

            list.getItems().setAll(planner.lists());
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
            HBox.setHgrow(box, Priority.ALWAYS);
            Button remove = iconButton("✕", () -> update(t -> t.getSteps().remove(step)));
            HBox row = new HBox(6, box, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            steps.getChildren().add(row);
        }
    }

    private void commitTitle() {
        if (loading || task == null) {
            return;
        }
        String text = title.getText().strip();
        if (text.isEmpty()) {
            title.setText(task.getTitle());
        } else if (!text.equals(task.getTitle())) {
            update(t -> t.setTitle(text));
        }
    }

    private void scheduleTask() {
        LocalDate day = scheduleDate.getValue();
        LocalTime time = scheduleTime.getValue();
        Integer minutes = duration.getValue();
        if (day == null || time == null || minutes == null) {
            return;
        }
        edit(t -> {
            t.setDurationMinutes(minutes);
            planner.scheduleTask(t, day.atTime(time));
        });
    }

    private void confirmDelete() {
        if (task == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Se eliminará «" + task.getTitle() + "».", ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(getScene().getWindow());
        confirm.setHeaderText("¿Eliminar tarea?");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> planner.deleteTask(task));
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
