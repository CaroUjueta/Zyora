package com.planora.ui;

import com.planora.model.Task;
import com.planora.model.TaskList;
import com.planora.service.AgendaItem;
import com.planora.service.Planner;
import com.planora.service.SmartList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** The tasks of a list or smart list. "Mi día" also shows today's agenda next to the tasks. */
class TaskListPane extends BorderPane {
    private final Planner planner;
    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final TextField addField = new TextField();
    private final ListView<Task> pending = new ListView<>();
    private final ListView<Task> done = new ListView<>();
    private final TitledPane donePane = new TitledPane();
    private final Label emptyLabel = new Label();
    private final VBox agendaItems = new VBox(8);
    private final VBox agendaPanel;
    private Nav nav;

    TaskListPane(Planner planner, Consumer<Task> onOpen) {
        this.planner = planner;
        getStyleClass().add("task-pane");

        title.getStyleClass().add("pane-title");
        subtitle.getStyleClass().add("pane-subtitle");
        VBox header = new VBox(2, title, subtitle);
        header.getStyleClass().add("pane-header");

        addField.setPromptText("+ Agregar una tarea");
        addField.getStyleClass().add("add-task-field");
        addField.setOnAction(e -> addTask());

        configure(pending, onOpen);
        configure(done, onOpen);
        emptyLabel.getStyleClass().add("hint");
        pending.setPlaceholder(emptyLabel);
        done.setPrefHeight(200);
        donePane.setContent(done);
        donePane.setExpanded(false);
        donePane.setAnimated(false);
        donePane.getStyleClass().add("done-pane");
        VBox.setVgrow(pending, Priority.ALWAYS);

        VBox center = new VBox(12, addField, pending, donePane);
        center.setPadding(new Insets(0, 28, 20, 28));

        Label agendaTitle = new Label("Agenda de hoy");
        agendaTitle.getStyleClass().add("section-title");
        agendaPanel = new VBox(12, agendaTitle, agendaItems);
        agendaPanel.getStyleClass().add("agenda-panel");

        setTop(header);
        setCenter(center);
        planner.addListener(this::refresh);
    }

    void show(Nav nav) {
        this.nav = nav;
        addField.clear();
        refresh();
    }

    private void configure(ListView<Task> listView, Consumer<Task> onOpen) {
        listView.getStyleClass().add("task-list");
        listView.setCellFactory(v -> new TaskCell(planner, onOpen,
                () -> nav != null && nav.kind() != Nav.Kind.LIST));
    }

    private void refresh() {
        if (nav == null || nav.kind() == Nav.Kind.CALENDAR) {
            return;
        }
        List<Task> tasks = nav.kind() == Nav.Kind.SMART
                ? planner.tasksFor(nav.smart())
                : planner.tasksInList(nav.list());
        List<Task> open = tasks.stream().filter(t -> !t.isCompleted()).toList();
        List<Task> completed = tasks.stream()
                .filter(Task::isCompleted)
                .sorted(Comparator.comparing(Task::getCompletedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        pending.getItems().setAll(open);
        pending.refresh();
        done.getItems().setAll(completed);
        done.refresh();
        donePane.setText("Completadas (" + completed.size() + ")");
        donePane.setVisible(!completed.isEmpty());
        donePane.setManaged(!completed.isEmpty());

        title.setText(nav.label());
        boolean myDay = nav.kind() == Nav.Kind.SMART && nav.smart() == SmartList.MY_DAY;
        subtitle.setText(myDay
                ? Fmt.capitalize(Fmt.LONG_DATE.format(planner.today()))
                : open.size() == 1 ? "1 tarea pendiente" : open.size() + " tareas pendientes");
        emptyLabel.setText(myDay
                ? "Concéntrate en tu día: agrega tareas o arrástralas al calendario."
                : "No hay tareas pendientes aquí.");

        if (myDay) {
            fillAgenda();
            setRight(agendaPanel);
        } else {
            setRight(null);
        }
    }

    private void fillAgenda() {
        agendaItems.getChildren().clear();
        List<AgendaItem> items = planner.agendaFor(planner.today());
        if (items.isEmpty()) {
            Label none = new Label("Nada en el calendario hoy.");
            none.getStyleClass().add("hint");
            agendaItems.getChildren().add(none);
            return;
        }
        for (AgendaItem item : items) {
            Region bar = new Region();
            bar.getStyleClass().add("agenda-bar");
            bar.setStyle("-fx-background-color: " + item.color() + ";");
            Label time = new Label(item.allDay()
                    ? (item.isTask() ? "Vence hoy" : "Todo el día")
                    : Fmt.TIME.format(item.start()) + " – " + Fmt.TIME.format(item.end()));
            time.getStyleClass().add("agenda-time");
            Label name = new Label((item.isTask() ? (item.completed() ? "✓ " : "☐ ") : "") + item.title());
            name.getStyleClass().add("agenda-title");
            name.setWrapText(true);
            HBox row = new HBox(10, bar, new VBox(2, time, name));
            row.setAlignment(Pos.TOP_LEFT);
            agendaItems.getChildren().add(row);
        }
    }

    private void addTask() {
        String text = addField.getText().strip();
        if (text.isEmpty() || nav == null) {
            return;
        }
        addField.clear();
        TaskList list = nav.kind() == Nav.Kind.LIST ? nav.list() : planner.defaultList();
        planner.addTask(text, list, task -> {
            if (nav.kind() != Nav.Kind.SMART) {
                return;
            }
            switch (nav.smart()) {
                case MY_DAY -> task.setMyDay(planner.today());
                case IMPORTANT -> task.setImportant(true);
                case PLANNED -> task.setDueDate(planner.today());
                case ALL -> { }
            }
        });
    }
}
