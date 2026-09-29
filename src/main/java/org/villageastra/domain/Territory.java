package org.villageastra.domain;

import java.util.Collection;

/** Inclusive block footprints; civilian expansion uses a finished building boundary. */
public final class Territory {
    public record Footprint(int minX, int minZ, int maxX, int maxZ, boolean finished, boolean civilAnchor) {
        public Footprint {
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Inverted footprint");
        }
    }
    public enum Result { OK, TOO_CLOSE, OUT_OF_REACH, NO_ANCHOR }
    private Territory() {}
    private static long gap(int amin, int amax, int bmin, int bmax) {
        return Math.max(0, Math.max((long) bmin - amax - 1, (long) amin - bmax - 1));
    }
    public static Result validate(Footprint candidate, Collection<Footprint> own, Collection<Footprint> all) {
        for (Footprint other : all) {
            if (gap(candidate.minX, candidate.maxX, other.minX, other.maxX) < 3 &&
                    gap(candidate.minZ, candidate.maxZ, other.minZ, other.maxZ) < 3) return Result.TOO_CLOSE;
        }
        boolean hasAnchor = false;
        for (Footprint anchor : own) {
            if (!anchor.finished || !anchor.civilAnchor) continue;
            hasAnchor = true;
            // Geometric outer surfaces rather than center points or empty cells.
            double dx = Math.max(0, Math.max((double) candidate.minX - anchor.maxX - 1, (double) anchor.minX - candidate.maxX - 1));
            double dz = Math.max(0, Math.max((double) candidate.minZ - anchor.maxZ - 1, (double) anchor.minZ - candidate.maxZ - 1));
            if (dx * dx + dz * dz <= 150.0 * 150.0) return Result.OK;
        }
        return hasAnchor ? Result.OUT_OF_REACH : Result.NO_ANCHOR;
    }
}
