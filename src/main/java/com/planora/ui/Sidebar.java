package com.planora.ui;

import com.planora.model.Task;
import com.planora.model.TaskList;
import com.planora.service.Planner;
import com.planora.service.SmartList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class Sidebar extends VBox {
    private static final PseudoClass GROUP_START = PseudoClass.getPseudoClass("group-start");

    private final Planner planner;
    private final ListView<Nav> navList = new ListView<>();
    private boolean refreshing;

    Sidebar(Planner planner, Consumer<Nav> onNavigate) {
        this.planner = planner;
        getStyleClass().add("sidebar");

        Label brand = new Label("Planora");
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

        getChildren().addAll(brand, navList, newList);
        planner.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        Nav selected = navList.getSelectionModel().getSelectedItem();
        List<Nav> items = new ArrayList<>();
        items.add(Nav.calendar());
        for (SmartList smart : SmartList.values()) {
            items.add(Nav.smart(smart));
        }
        for (TaskList list : planner.lists()) {
            items.add(Nav.list(list));
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
            navList.getSelectionModel().select(Nav.smart(SmartList.MY_DAY));
        }
    }

    private long pendingCount(Nav nav) {
        List<Task> tasks = switch (nav.kind()) {
            case CALENDAR -> List.of();
            case SMART -> planner.tasksFor(nav.smart());
            case LIST -> planner.tasksInList(nav.list());
        };
        return tasks.stream().filter(t -> !t.isCompleted()).count();
    }

    private void rename(TaskList list) {
        TextInputDialog dialog = new TextInputDialog(list.getName());
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Renombrar lista");
        dialog.setHeaderText(null);
        dialog.setContentText("Nombre:");
        dialog.showAndWait()
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .ifPresent(name -> planner.renameList(list, name));
    }

    private void delete(TaskList list) {
        if (planner.lists().size() <= 1) {
            Alert info = new Alert(Alert.AlertType.INFORMATION, "Debe existir al menos una lista.");
            info.initOwner(getScene().getWindow());
            info.setHeaderText(null);
            info.showAndWait();
            return;
        }
        long count = planner.tasksInList(list).size();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Se eliminará «" + list.getName() + "» y sus " + count + " tareas.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(getScene().getWindow());
        confirm.setHeaderText("¿Eliminar lista?");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> planner.deleteList(list));
    }

    private class NavCell extends ListCell<Nav> {
        private final Label icon = new Label();
        private final Label name = new Label();
        private final Label count = new Label();
        private final HBox root = new HBox(10, icon, name, count);

        NavCell() {
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
            icon.setText(nav.icon());
            icon.setStyle(nav.kind() == Nav.Kind.LIST ? "-fx-text-fill: " + nav.list().getColor() + ";" : "");
            name.setText(nav.label());
            long pending = pendingCount(nav);
            count.setText(pending > 0 ? String.valueOf(pending) : "");

            int index = getIndex();
            List<Nav> items = getListView().getItems();
            boolean groupStart = index > 0 && items.get(index - 1).kind() != nav.kind();
            pseudoClassStateChanged(GROUP_START, groupStart);

            if (nav.kind() == Nav.Kind.LIST) {
                MenuItem renameItem = new MenuItem("Renombrar");
                renameItem.setOnAction(e -> rename(nav.list()));
                MenuItem deleteItem = new MenuItem("Eliminar lista");
                deleteItem.setOnAction(e -> delete(nav.list()));
                setContextMenu(new ContextMenu(renameItem, deleteItem));
            } else {
                setContextMenu(null);
            }
            setGraphic(root);
        }
    }
}
