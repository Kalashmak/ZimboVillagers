package org.villageastra.domain;

/** Server ticks, not day time or wall time. Lag slows simulation consistently. */
public final class SimulationClock {
    public static final long TICKS_PER_SECOND = 20;
    private long ticks;
    public SimulationClock(long ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Negative active time");
        this.ticks = ticks;
    }
    public boolean advance(boolean hasPlayers, boolean paused) {
        if (!hasPlayers || paused) return false;
        ticks = Math.addExact(ticks, 1);
        return true;
    }
    public long ticks() { return ticks; }
    public long seconds() { return ticks / TICKS_PER_SECOND; }
}
