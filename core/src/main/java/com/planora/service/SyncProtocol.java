package com.planora.service;

/**
 * How the phone and the computer talk over Wi‑Fi. While Planora is open on the computer it listens
 * on {@link #PORT}. The phone sends {@code POST /sync} with the pairing code in {@link #CODE_HEADER}
 * and all its data as the body; the computer merges both sides, keeps the result and answers with
 * it, so after one request both devices hold the same data.
 */
public final class SyncProtocol {
    public static final int PORT = 47821;
    public static final String PATH = "/sync";
    public static final String CODE_HEADER = "X-Planora-Code";
    /** Optional: a name for the phone, shown on the computer. */
    public static final String DEVICE_HEADER = "X-Planora-Device";

    private SyncProtocol() {
    }
}
