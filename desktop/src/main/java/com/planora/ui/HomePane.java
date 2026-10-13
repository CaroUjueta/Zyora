package com.planora.ui;

import com.planora.model.Task;
import com.planora.service.Planner;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** The start page: a greeting, a small month calendar and the tasks coming up this week. */
class HomePane extends VBox {
    /** How far ahead the task card looks. */
    private static final int DAYS_AHEAD = 7;

    private final Planner planner;
    private final Profile profile;
    private final Consumer<Task> onOpenTask;
    private final Consumer<LocalDate> onOpenDay;
    private final Label monthLabel = new Label();
    private final GridPane monthGrid = new GridPane();
    private final VBox taskRows = new VBox(4);
    private YearMonth month;

    HomePane(Planner planner, Profile profile, Consumer<Task> onOpenTask, Consumer<LocalDate> onOpenDay) {
        this.planner = planner;
        this.profile = profile;
        this.onOpenTask = onOpenTask;
        this.onOpenDay = onOpenDay;
        this.month = YearMonth.from(planner.today());
        getStyleClass().add("home-pane");

        Label hello = new Label();
        hello.textProperty().bind(profile.nameProperty()
                .map(name -> name.isEmpty() ? "Hola ✿" : "Hola, " + name + " ✿"));
        hello.getStyleClass().add("home-hello");
        hello.setTooltip(new Tooltip("Clic para cambiar tu nombre"));
        hello.setOnMouseClicked(e -> askName("Tu nombre"));
        Label subtitle = new Label("Aquí tienes tu plan para hoy:");
        subtitle.getStyleClass().add("home-subtitle");

        Node calendarCard = calendarCard();
        Node tasksCard = tasksCard();
        HBox.setHgrow(tasksCard, Priority.ALWAYS);
        HBox cards = new HBox(20, calendarCard, tasksCard);
        cards.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(cards, Priority.ALWAYS);

        getChildren().addAll(new VBox(4, hello, subtitle), cards);
        planner.addListener(this::refresh);
        refresh();
    }

    /** Asks for the name on the first run; cancelling leaves the greeting without a name until next time. */
    void askNameIfMissing() {
        if (!profile.hasName()) {
            askName("Te damos la bienvenida a Zyora. ¿Cómo te llamas?");
        }
    }

    private void askName(String header) {
        Dialogs.prompt(getScene().getWindow(), header, profile.nameProperty().get(), "Tu nombre", "Guardar")
                .ifPresent(profile::setName);
    }

    private void refresh() {
        fillMonth();
        fillTasks();
    }

    // ---- small calendar ------------------------------------------------------------------

    private Node calendarCard() {
        Button prev = new Button("‹");
        prev.getStyleClass().add("nav-arrow");
        prev.setOnAction(e -> {
            month = month.minusMonths(1);
            fillMonth();
        });
        Button next = new Button("›");
        next.getStyleClass().add("nav-arrow");
        next.setOnAction(e -> {
            month = month.plusMonths(1);
            fillMonth();
        });
        monthLabel.getStyleClass().add("home-month");
        // Clicking the month name goes back to the current month.
        monthLabel.setOnMouseClicked(e -> {
            month = YearMonth.from(planner.today());
            fillMonth();
        });
        HBox header = new HBox(10, prev, monthLabel, next);
        header.setAlignment(Pos.CENTER);

        for (int c = 0; c < 7; c++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 7);
            monthGrid.getColumnConstraints().add(col);
        }
        monthGrid.getStyleClass().add("mini-month");

        VBox card = new VBox(12, header, monthGrid);
        card.getStyleClass().addAll("home-card", "home-calendar");
        card.setMaxHeight(Region.USE_PREF_SIZE);
        return card;
    }

    private void fillMonth() {
        monthLabel.setText(Fmt.capitalize(month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Fmt.ES))
                + " " + month.getYear());
        monthGrid.getChildren().clear();
        for (int c = 0; c < 7; c++) {
            String name = DayOfWeek.MONDAY.plus(c).getDisplayName(TextStyle.SHORT_STANDALONE, Fmt.ES);
            Label label = new Label(Fmt.capitalize(name.replace(".", "")).substring(0, 2));
            label.getStyleClass().add("mini-weekday");
            label.setMaxWidth(Double.MAX_VALUE);
            monthGrid.add(label, c, 0);
        }
        LocalDate today = planner.today();
        LocalDate first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = 0; i < 42; i++) {
            monthGrid.add(dayCell(first.plusDays(i), today), i % 7, 1 + i / 7);
        }
    }

    private Node dayCell(LocalDate day, LocalDate today) {
        Label number = new Label(String.valueOf(day.getDayOfMonth()));
        number.getStyleClass().add("mini-day");
        if (!YearMonth.from(day).equals(month)) {
            number.getStyleClass().add("other-month");
        }
        if (day.equals(today)) {
            number.getStyleClass().add("today");
        }
        // A dot under the days that have something on the calendar.
        Region dot = new Region();
        dot.getStyleClass().add("mini-dot");
        dot.setVisible(!day.equals(today) && !planner.agendaFor(day).isEmpty());

        VBox cell = new VBox(1, number, dot);
        cell.setAlignment(Pos.TOP_CENTER);
        cell.getStyleClass().add("mini-cell");
        cell.setOnMouseClicked(e -> onOpenDay.accept(day));
        return cell;
    }

    // ---- tasks ---------------------------------------------------------------------------

    private Node tasksCard() {
        Label icon = new Label("✓");
        icon.getStyleClass().add("home-card-icon");
        Label title = new Label("Mis tareas");
        title.getStyleClass().add("home-card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button add = new Button("+  Nueva tarea");
        add.getStyleClass().add("accent");
        add.setOnAction(e -> Dialogs.prompt(getScene().getWindow(), "Nueva tarea para hoy", "",
                        "Título de la tarea", "Crear")
                .ifPresent(text -> planner.addTask(text, planner.defaultList(),
                        t -> t.setMyDay(planner.today()))));
        HBox header = new HBox(10, icon, title, spacer, add);
        header.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(taskRows);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("home-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox card = new VBox(14, header, scroll);
        card.getStyleClass().addAll("home-card", "home-tasks");
        return card;
    }

    /**
     * The day a task belongs to on the home page: its time block, its due date, or today if it was
     * added to Mi día. Null for tasks without any date.
     */
    private LocalDate dayOf(Task task, LocalDate today) {
        if (task.isScheduled()) {
            return task.getScheduledStart().toLocalDate();
        }
        if (task.getDueDate() != null) {
            return task.getDueDate();
        }
        return task.isInMyDay(today) ? today : null;
    }

    /** Pending tasks up to a week ahead (overdue ones too), plus the ones finished today. */
    private void fillTasks() {
        LocalDate today = planner.today();
        LocalDate limit = today.plusDays(DAYS_AHEAD);
        List<Task> tasks = planner.tasks().stream()
                .filter(t -> {
                    LocalDate day = dayOf(t, today);
                    if (day == null || day.isAfter(limit)) {
                        return false;
                    }
                    return !t.isCompleted()
                            || (t.getCompletedAt() != null && t.getCompletedAt().toLocalDate().equals(today));
                })
                .sorted(Comparator.comparing(Task::isCompleted)
                        .thenComparing(t -> dayOf(t, today)))
                .toList();

        taskRows.getChildren().clear();
        if (tasks.isEmpty()) {
            Label none = new Label("Nada pendiente para esta semana.");
            none.getStyleClass().add("hint");
            taskRows.getChildren().add(none);
            return;
        }
        tasks.forEach(t -> taskRows.getChildren().add(taskRow(t, today)));
    }

    private Node taskRow(Task task, LocalDate today) {
        CheckBox check = new CheckBox();
        check.setSelected(task.isCompleted());
        check.setOnAction(e -> planner.setCompleted(task, check.isSelected()));

        Label title = new Label(task.getTitle());
        title.setWrapText(true);
        title.getStyleClass().add("task-title");
        if (task.isCompleted()) {
            title.getStyleClass().add("done");
        }
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        HBox row = new HBox(12, check, title);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("home-task-row");
        if (!task.isCompleted()) {
            row.getChildren().add(badge(dayOf(task, today), today));
        }
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && !check.isHover()) {
                onOpenTask.accept(task);
            }
        });
        return row;
    }

    private Node badge(LocalDate day, LocalDate today) {
        Label badge = new Label();
        badge.getStyleClass().add("home-badge");
        if (day.isBefore(today)) {
            badge.setText("Venció " + Fmt.DAY_MONTH.format(day));
            badge.getStyleClass().add("overdue");
        } else if (day.equals(today)) {
            badge.setText("Hoy");
            badge.getStyleClass().add("today");
        } else if (day.equals(today.plusDays(1))) {
            badge.setText("Mañana");
            badge.getStyleClass().add("tomorrow");
        } else {
            badge.setText(Fmt.capitalize(Fmt.SHORT_DATE.format(day)));
        }
        badge.setMinWidth(Region.USE_PREF_SIZE);
        return new StackPane(badge);
    }
}
