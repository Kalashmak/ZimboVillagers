package org.villageastra.domain;

import java.util.Objects;
import java.util.UUID;

/** Generation token invalidates a stale detailed/background worker on handover. */
public final class WorkLease {
    public enum Mode { DETAILED, BACKGROUND }
    public record Token(UUID worker, Mode mode, long generation) {}
    private Token current;
    private long generation;
    private boolean completed;
    public WorkLease(long generation, boolean completed) {
        if (generation < 0) throw new IllegalArgumentException("Negative generation");
        this.generation = generation; this.completed = completed;
    }
    public Token acquire(UUID worker, Mode mode) {
        if (completed || current != null) throw new IllegalStateException("Work already owned or completed");
        current = new Token(Objects.requireNonNull(worker), Objects.requireNonNull(mode), ++generation);
        return current;
    }
    public boolean owns(Token token) { return token != null && token.equals(current) && !completed; }
    public void release(Token token) {
        if (!owns(token)) throw new IllegalStateException("Stale work token");
        current = null;
    }
    public boolean complete(Token token) {
        if (!owns(token)) return false;
        completed = true; current = null; return true;
    }
    public long generation() { return generation; }
    public boolean completed() { return completed; }
}
