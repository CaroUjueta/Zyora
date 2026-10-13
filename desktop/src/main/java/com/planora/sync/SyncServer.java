package com.planora.sync;

import com.planora.service.Planner;
import com.planora.service.PlannerData;
import com.planora.service.PlannerJson;
import com.planora.service.SyncMerger;
import com.planora.service.SyncProtocol;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lets the phone sync over Wi‑Fi while Zyora is open (see {@link SyncProtocol}). Only requests
 * that carry the pairing code are accepted; the code is kept in {@code sync.properties} next to the
 * data file so the phone does not need it again.
 */
public class SyncServer {
    /** After this many wrong codes in a row, requests are refused for a while. */
    private static final int MAX_FAILURES = 5;
    private static final long LOCKOUT_MILLIS = 60_000;
    private static final int MAX_BODY_BYTES = 50 * 1024 * 1024;

    /** A finished sync, for the window that shows the status. */
    public record Result(Instant at, String device) {
    }

    private final Planner planner;
    private final Path settingsFile;
    private final String code;
    private final List<Consumer<Result>> listeners = new ArrayList<>();
    private HttpServer server;
    private String error;
    private volatile Result last;
    private int failures;
    private long lockedUntil;

    public SyncServer(Planner planner, Path dataFile) {
        this.planner = planner;
        this.settingsFile = dataFile.toAbsolutePath().resolveSibling("sync.properties");
        this.code = loadOrCreateCode();
    }

    /** Starts listening. On failure the app keeps working and {@link #error()} says why. */
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(SyncProtocol.PORT), 0);
            server.createContext(SyncProtocol.PATH, this::handle);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "planora-sync");
                t.setDaemon(true);
                return t;
            }));
            server.start();
        } catch (IOException e) {
            server = null;
            error = "No se pudo abrir el puerto " + SyncProtocol.PORT
                    + ". ¿Hay otra ventana de Zyora abierta?";
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public String error() {
        return error;
    }

    public String code() {
        return code;
    }

    public Result lastSync() {
        return last;
    }

    /** Called on the JavaFX thread after each sync. */
    public void addListener(Consumer<Result> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Result> listener) {
        listeners.remove(listener);
    }

    /** The addresses the phone can use to reach this computer on the local network. */
    public static List<String> localAddresses() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        out.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            // No addresses to show; the window says so.
        }
        return out;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod())) {
                reply(exchange, 405, "Usa POST");
                return;
            }
            if (System.currentTimeMillis() < lockedUntil) {
                reply(exchange, 429, "Demasiados intentos con un código incorrecto. Espera un minuto.");
                return;
            }
            String given = exchange.getRequestHeaders().getFirst(SyncProtocol.CODE_HEADER);
            if (given == null || !MessageDigest.isEqual(given.strip().getBytes(StandardCharsets.UTF_8),
                    code.getBytes(StandardCharsets.UTF_8))) {
                if (++failures >= MAX_FAILURES) {
                    failures = 0;
                    lockedUntil = System.currentTimeMillis() + LOCKOUT_MILLIS;
                }
                reply(exchange, 401, "El código no coincide con el que muestra el PC.");
                return;
            }
            failures = 0;

            PlannerData fromPhone;
            try (InputStream body = exchange.getRequestBody()) {
                byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
                if (bytes.length > MAX_BODY_BYTES) {
                    reply(exchange, 413, "Los datos son demasiado grandes.");
                    return;
                }
                fromPhone = PlannerJson.read(new ByteArrayInputStream(bytes));
            } catch (IOException e) {
                reply(exchange, 400, "Los datos del teléfono no se pudieron leer.");
                return;
            }

            String device = exchange.getRequestHeaders().getFirst(SyncProtocol.DEVICE_HEADER);
            byte[] answer = onFxThread(() -> {
                planner.replaceWith(SyncMerger.merge(planner.snapshot(), fromPhone, Instant.now()));
                Result result = new Result(Instant.now(), device == null || device.isBlank() ? "Teléfono" : device);
                last = result;
                listeners.forEach(l -> l.accept(result));
                return PlannerJson.write(planner.snapshot());
            });
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        } catch (RuntimeException e) {
            reply(exchange, 500, "Error en el PC: " + e.getMessage());
        }
    }

    /** The planner is only touched on the JavaFX thread, like the rest of the UI does. */
    private static <T> T onFxThread(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(work.get());
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.join();
        } catch (CompletionException e) {
            throw e.getCause() instanceof RuntimeException r ? r : e;
        }
    }

    private static void reply(HttpExchange exchange, int status, String message) {
        try {
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException ignored) {
            // The phone went away; nothing to tell it.
        }
    }

    private String loadOrCreateCode() {
        Properties props = new Properties();
        String fresh = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        try {
            if (Files.exists(settingsFile)) {
                try (InputStream in = Files.newInputStream(settingsFile)) {
                    props.load(in);
                }
            }
            String saved = props.getProperty("code");
            if (saved != null && saved.matches("\\d{6}")) {
                return saved;
            }
            props.setProperty("code", fresh);
            Files.createDirectories(settingsFile.getParent());
            try (OutputStream out = Files.newOutputStream(settingsFile)) {
                props.store(out, "Zyora: código para sincronizar con el teléfono");
            }
            return fresh;
        } catch (IOException e) {
            return fresh; // works for this session; the phone will need the new code next time
        }
    }
}
