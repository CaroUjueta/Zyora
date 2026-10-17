package com.zyora;

import javafx.application.Application;

/**
 * Entry point for the fat jar. A main class that extends {@link Application} cannot be launched
 * from the classpath, so this plain class starts it instead.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(ZyoraApp.class, args);
    }
}
