package com.zyora.ui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.HashMap;
import java.util.Map;

/** The line icons in resources/com/zyora/icons/nav, drawn at 128 px and shown scaled down. */
final class Icons {
    private static final Map<String, Image> CACHE = new HashMap<>();

    private Icons() {
    }

    static ImageView view(String name, double size) {
        Image image = CACHE.computeIfAbsent(name, n -> new Image(
                Icons.class.getResource("/com/zyora/icons/nav/" + n + ".png").toExternalForm()));
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setSmooth(true);
        return view;
    }
}
