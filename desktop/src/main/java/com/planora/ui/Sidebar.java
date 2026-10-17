package com.planora.ui;

import com.planora.model.Task;
import com.planora.model.TaskList;
import com.planora.service.Planner;
import com.planora.service.SmartList;
import com.planora.sync.SyncServer;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

class Sidebar extends VBox {
    private static final PseudoClass GROUP_START = PseudoClass.getPseudoClass("group-start");

    private final Planner planner;
    private final ListView<Nav> navList = new ListView<>();
    /** Ids of the lists whose sublists are hidden. Only remembered while the app is open. */
    private final Set<String> collapsed = new HashSet<>();
    private boolean refreshing;

    Sidebar(Planner planner, SyncServer sync, DesktopWidget widget, Consumer<Nav> onNavigate) {
        this.planner = planner;
        getStyleClass().add("sidebar");

        Label brand = new Label("✿ Zyora");
        brand.getStyleClass().add("brand");

        navList.getStyleClass().add("nav-list");
        navList.setCellFactory(v -> new NavCell());
        navList.getSelectionModel().selectedItemProperty().addListener((obs, old, nav) -> {
            if (nav != null && !refreshing) {
                onNavigate.accept(nav);
            }
        });
        VBox.setVgrow(navList, Priority.ALWAYS);

        TextField newList = new TextField();
        newList.setPromptText("+ Nueva lista");
        newList.getStyleClass().add("new-list-field");
        newList.setOnAction(e -> {
            String name = newList.getText().strip();
            if (!name.isEmpty()) {
                newList.clear();
                TaskList list = planner.addList(name);
                navList.getSelectionModel().select(Nav.list(list));
            }
        });

        Button syncButton = new Button("Sincronizar con el teléfono", Icons.view("sync", 20));
        syncButton.setGraphicTextGap(10);
        syncButton.getStyleClass().add("sync-button");
        syncButton.setMaxWidth(Double.MAX_VALUE);
        syncButton.setOnAction(e -> SyncDialog.show(getScene().getWindow(), sync));
        Label syncStatus = new Label();
        syncStatus.getStyleClass().add("sync-hint");
        syncStatus.setWrapText(true);
        syncStatus.setManaged(false);
        syncStatus.setVisible(false);
        sync.addListener(result -> {
            syncStatus.setText(SyncDialog.statusText(result));
            syncStatus.setManaged(true);
            syncStatus.setVisible(true);
        });

        Button widgetButton = new Button("", Icons.view("widget", 20));
        widgetButton.textProperty().bind(widget.showingProperty()
                .map(open -> open ? "Ocultar el widget" : "Widget de escritorio"));
        widgetButton.setGraphicTextGap(10);
        widgetButton.getStyleClass().add("sync-button");
        widgetButton.setMaxWidth(Double.MAX_VALUE);
        widgetButton.setOnAction(e -> widget.toggle());

        getChildren().addAll(brand, navList, newList, new VBox(6, widgetButton, syncButton, syncStatus));
        planner.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        Nav selected = navList.getSelectionModel().getSelectedItem();
        List<Nav> items = new ArrayList<>();
        items.add(Nav.home());
        items.add(Nav.calendar());
        for (SmartList smart : SmartList.values()) {
            items.add(Nav.smart(smart));
        }
        for (TaskList list : planner.listTree()) {
            if (!insideCollapsed(list)) {
                items.add(Nav.list(list));
            }
        }

        refreshing = true;
        navList.getItems().setAll(items);
        navList.refresh();
        boolean keep = selected != null && items.contains(selected);
        if (keep) {
            navList.getSelectionModel().select(selected);
        }
        refreshing = false;
        if (!keep) {
            navList.getSelectionModel().select(Nav.home());
        }
    }

    void select(Nav nav) {
        navList.getSelectionModel().select(nav);
    }

    private long pendingCount(Nav nav) {
        List<Task> tasks = switch (nav.kind()) {
            case HOME, CALENDAR -> List.of();
            case SMART -> planner.tasksFor(nav.smart());
            case LIST -> planner.tasksInList(nav.list());
        };
        return tasks.stream().filter(t -> !t.isCompleted()).count();
    }

    /** How many lists this one is nested in; 0 for top-level lists. */
    private int depth(TaskList list) {
        return planner.pathOf(list).size() - 1;
    }

    private boolean insideCollapsed(TaskList list) {
        List<TaskList> ancestors = planner.pathOf(list).subList(0, depth(list));
        return ancestors.stream().anyMatch(l -> collapsed.contains(l.getId()));
    }

    private void toggle(TaskList list) {
        if (!collapsed.remove(list.getId())) {
            collapsed.add(list.getId());
        }
        // If the open list is about to be hidden, move the selection up to the collapsed list.
        Nav selected = navList.getSelectionModel().getSelectedItem();
        boolean hidesSelection = selected != null && selected.kind() == Nav.Kind.LIST
                && selected.list() != list && planner.subtree(list).contains(selected.list());
        refresh();
        if (hidesSelection) {
            navList.getSelectionModel().select(Nav.list(list));
        }
    }

    private void addSublist(TaskList parent) {
        Dialogs.prompt(getScene().getWindow(), "Nueva sublista en «" + parent.getName() + "»", "",
                "Nombre de la sublista", "Crear").ifPresent(name -> {
                    collapsed.remove(parent.getId());
                    TaskList list = planner.addList(name, parent);
                    navList.getSelectionModel().select(Nav.list(list));
                });
    }

    private void rename(TaskList list) {
        Dialogs.prompt(getScene().getWindow(), "Renombrar lista", list.getName(), "Nombre", "Guardar")
                .ifPresent(name -> planner.renameList(list, name));
    }

    private void recolor(TaskList list) {
        Dialogs.pickColor(getScene().getWindow(), "Color de «" + list.getName() + "»", list.getColor())
                .ifPresent(color -> planner.setListColor(list, color));
    }

    private void delete(TaskList list) {
        List<TaskList> doomed = planner.subtree(list);
        if (doomed.size() >= planner.lists().size()) {
            Dialogs.info(getScene().getWindow(), "No se puede eliminar",
                    "Debe existir al menos una lista fuera de «" + list.getName() + "».");
            return;
        }
        int tasks = planner.tasksInList(list).size();
        int sublists = doomed.size() - 1;
        String text = "Se eliminarán " + (tasks == 1 ? "1 tarea" : tasks + " tareas")
                + (sublists == 0 ? "" : sublists == 1 ? " y 1 sublista" : " y " + sublists + " sublistas")
                + ". Esta acción no se puede deshacer.";
        if (Dialogs.confirm(getScene().getWindow(), "¿Eliminar «" + list.getName() + "»?", text, "Eliminar")) {
            planner.deleteList(list);
        }
    }

    private class NavCell extends ListCell<Nav> {
        private final Label arrow = new Label();
        private final Label icon = new Label();
        private final Label name = new Label();
        private final Label count = new Label();
        private final HBox root = new HBox(8, arrow, icon, name, count);
        private final ContextMenu menu = new ContextMenu(
                menuItem("Nueva sublista", Sidebar.this::addSublist),
                menuItem("Renombrar", Sidebar.this::rename),
                menuItem("Cambiar color", Sidebar.this::recolor),
                menuItem("Eliminar lista", Sidebar.this::delete));

        NavCell() {
            arrow.getStyleClass().add("nav-arrow-toggle");
            arrow.setOnMouseClicked(e -> {
                if (getItem() != null && getItem().kind() == Nav.Kind.LIST
                        && !planner.childrenOf(getItem().list()).isEmpty()) {
                    toggle(getItem().list());
                    e.consume();
                }
            });
            icon.getStyleClass().add("nav-icon");
            name.getStyleClass().add("nav-name");
            count.getStyleClass().add("nav-count");
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            root.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Nav nav, boolean empty) {
            super.updateItem(nav, empty);
            if (empty || nav == null) {
                setGraphic(null);
                setContextMenu(null);
                pseudoClassStateChanged(GROUP_START, false);
                return;
            }
            boolean isList = nav.kind() == Nav.Kind.LIST;
            boolean hasChildren = isList && !planner.childrenOf(nav.list()).isEmpty();
            arrow.setText(hasChildren ? (collapsed.contains(nav.list().getId()) ? "▸" : "▾") : "");
            arrow.setVisible(isList);
            arrow.setManaged(isList);
            root.setPadding(new Insets(0, 0, 0, isList ? depth(nav.list()) * 16 : 0));
            if (isList) {
                icon.setGraphic(null);
                icon.setText("●");
                icon.setStyle("-fx-text-fill: " + Palette.bar(nav.list().getColor()) + ";");
            } else {
                icon.setGraphic(Icons.view(nav.iconName(), 24));
                icon.setText("");
                icon.setStyle("");
            }
            name.setText(nav.label());
            long pending = pendingCount(nav);
            count.setText(String.valueOf(pending));
            count.setVisible(pending > 0);

            int index = getIndex();
            List<Nav> items = getListView().getItems();
            boolean groupStart = index > 0 && items.get(index - 1).section() != nav.section();
            pseudoClassStateChanged(GROUP_START, groupStart);

            setContextMenu(isList ? menu : null);
            setGraphic(root);
        }

        /**
         * JavaFX runs a menu item's action before it closes the menu, so a dialog opened right
         * away would leave the menu stuck on screen while the sidebar redraws underneath it.
         */
        private MenuItem menuItem(String text, Consumer<TaskList> action) {
            MenuItem item = new MenuItem(text);
            item.setOnAction(e -> {
                TaskList list = getItem().list();
                Platform.runLater(() -> action.accept(list));
            });
            return item;
        }
    }
}
