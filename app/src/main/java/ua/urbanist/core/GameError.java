package ua.urbanist.core;

public final class GameError extends RuntimeException {
    public final int code;
    public GameError(String msg) { this(msg, 400); }
    public GameError(String msg, int code) { super(msg); this.code = code; }
}
