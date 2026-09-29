package org.villageastra.domain;

import java.util.*;

/** Immutable estimate and execution input. Unknown substrate cannot be estimated as free air. */
public record ConstructionPlan(UUID id, int blueprintVersion, long surveyVersion, List<Operation> operations) {
    public record Position(int x, int y, int z) {}
    public enum Kind { CLEAR, DEMOLISH, FILL, FOUNDATION, BUILD, ROAD, LIGHT, FENCE, TEMPORARY }
    public record Operation(UUID id, Position position, String expected, String replacement,
                            Kind kind, Map<String, Integer> materials, long laborTicks) {
        public Operation {
            Objects.requireNonNull(id); Objects.requireNonNull(position);
            Objects.requireNonNull(expected); Objects.requireNonNull(replacement); Objects.requireNonNull(kind);
            if (expected.equals("unknown")) throw new IllegalArgumentException("Survey required");
            if (expected.equals(replacement)) throw new IllegalArgumentException("No-op");
            if (laborTicks <= 0 || materials.values().stream().anyMatch(n -> n <= 0))
                throw new IllegalArgumentException("Invalid cost");
            materials = Map.copyOf(materials);
        }
    }
    public ConstructionPlan {
        Objects.requireNonNull(id);
        if (blueprintVersion < 1 || surveyVersion < 0) throw new IllegalArgumentException("Invalid version");
        operations = List.copyOf(operations);
        Set<UUID> ids = new HashSet<>();
        Map<Position, String> previous = new HashMap<>();
        for (Operation op : operations) {
            if (!ids.add(op.id())) throw new IllegalArgumentException("Duplicate operation");
            String state = previous.put(op.position(), op.replacement());
            if (state != null && !state.equals(op.expected())) throw new IllegalArgumentException("Broken state chain");
        }
    }
    public Map<String, Long> materials() {
        Map<String, Long> total = new TreeMap<>();
        operations.forEach(op -> op.materials().forEach((item, count) -> total.merge(item, (long) count, Math::addExact)));
        return Collections.unmodifiableMap(total);
    }
    public long uniquePositions() { return operations.stream().map(Operation::position).distinct().count(); }
    public long laborTicks() { return operations.stream().mapToLong(Operation::laborTicks).reduce(0, Math::addExact); }
}
