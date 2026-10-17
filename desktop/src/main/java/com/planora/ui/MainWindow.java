package com.planora.ui;

import com.planora.model.Task;
import com.planora.service.Planner;
import com.planora.sync.SyncServer;
import javafx.scene.layout.BorderPane;

import java.time.LocalDate;

/** Sidebar on the left, the home page, a task list or the calendar in the middle, task details on the right. */
public class MainWindow extends BorderPane {
    private final HomePane homePane;
    private final TaskListPane taskPane;
    private final CalendarPane calendarPane;
    private final TaskDetailPane detailPane;
    private final Sidebar sidebar;

    public MainWindow(Planner planner, Profile profile, SyncServer sync, DesktopWidget widget) {
        getStyleClass().add("main-window");
        detailPane = new TaskDetailPane(planner, () -> setRight(null));
        homePane = new HomePane(planner, profile, this::openTask, this::openDay);
        taskPane = new TaskListPane(planner, this::openTask);
        calendarPane = new CalendarPane(planner, this::openTask);
        sidebar = new Sidebar(planner, sync, widget, this::navigate);
        setLeft(sidebar);
    }

    /** Call once the window is showing: asks for the user's name the first time Zyora opens. */
    public void welcome() {
        homePane.askNameIfMissing();
    }

    private void navigate(Nav nav) {
        switch (nav.kind()) {
            case HOME -> setCenter(homePane);
            case CALENDAR -> setCenter(calendarPane);
            default -> {
                taskPane.show(nav);
                setCenter(taskPane);
            }
        }
    }

    private void openTask(Task task) {
        detailPane.show(task);
        setRight(detailPane);
    }

    /** Shows that day on the calendar. */
    public void openDay(LocalDate day) {
        calendarPane.openDay(day);
        sidebar.select(Nav.calendar());
    }
}
