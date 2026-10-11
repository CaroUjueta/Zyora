package com.planora.ui;

import com.planora.model.Task;
import com.planora.service.Planner;
import com.planora.sync.SyncServer;
import javafx.scene.layout.BorderPane;

/** Sidebar on the left, the task list or calendar in the middle, task details on the right. */
public class MainWindow extends BorderPane {
    private final TaskListPane taskPane;
    private final CalendarPane calendarPane;
    private final TaskDetailPane detailPane;

    public MainWindow(Planner planner, SyncServer sync) {
        getStyleClass().add("main-window");
        detailPane = new TaskDetailPane(planner, () -> setRight(null));
        taskPane = new TaskListPane(planner, this::openTask);
        calendarPane = new CalendarPane(planner, this::openTask);
        setLeft(new Sidebar(planner, sync, this::navigate));
    }

    private void navigate(Nav nav) {
        if (nav.kind() == Nav.Kind.CALENDAR) {
            setCenter(calendarPane);
        } else {
            taskPane.show(nav);
            setCenter(taskPane);
        }
    }

    private void openTask(Task task) {
        detailPane.show(task);
        setRight(detailPane);
    }
}
