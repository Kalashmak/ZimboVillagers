package org.villageastra.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionTest {
    private ConstructionPlan.Operation operation(String before, String after, Map<String,Integer> cost) {
        return new ConstructionPlan.Operation(UUID.randomUUID(), new ConstructionPlan.Position(3,70,4), before, after,
                ConstructionPlan.Kind.BUILD, cost, 20);
    }
    @Test void replacementHasTwoOperationsOnePositionAndOneNewMaterial() {
        var remove = operation("stone", "air", Map.of());
        var place = operation("air", "planks", Map.of("planks", 1));
        var plan = new ConstructionPlan(UUID.randomUUID(), 1, 42, List.of(remove, place));
        assertEquals(2, plan.operations().size());
        assertEquals(1, plan.uniquePositions());
        assertEquals(Map.of("planks", 1L), plan.materials());
        assertEquals(40, plan.laborTicks());
        assertThrows(UnsupportedOperationException.class, () -> plan.operations().clear());
    }
    @Test void unknownAndConflictingOperationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> operation("unknown", "planks", Map.of()));
        var a = operation("air", "stone", Map.of("stone", 1));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionPlan(UUID.randomUUID(), 1, 0, List.of(a,a)));
        assertThrows(IllegalArgumentException.class, () -> new ConstructionPlan(UUID.randomUUID(), 1, 0,
                List.of(a, operation("air", "planks", Map.of("planks", 1)))));
    }
    @Test void handoverInvalidatesOldWorkerAndCompletionIsOnceOnly() {
        var lease = new WorkLease(0, false);
        var worker = UUID.randomUUID();
        var detailed = lease.acquire(worker, WorkLease.Mode.DETAILED);
        assertThrows(IllegalStateException.class, () -> lease.acquire(UUID.randomUUID(), WorkLease.Mode.BACKGROUND));
        lease.release(detailed);
        var background = lease.acquire(worker, WorkLease.Mode.BACKGROUND);
        assertFalse(lease.complete(detailed));
        assertTrue(lease.complete(background));
        assertFalse(lease.complete(background));
        assertThrows(IllegalStateException.class, () -> lease.acquire(worker, WorkLease.Mode.DETAILED));
        var restored = new WorkLease(lease.generation(), lease.completed());
        assertThrows(IllegalStateException.class, () -> restored.acquire(worker, WorkLease.Mode.BACKGROUND));
    }
    @Test void territoryUsesBuildingBoundaryAndRoadsCannotExtendIt() {
        var anchor = new Territory.Footprint(0,0,100,10,true,true);
        assertEquals(Territory.Result.OK, Territory.validate(new Territory.Footprint(104,0,110,6,false,true), List.of(anchor), List.of(anchor)));
        assertEquals(Territory.Result.TOO_CLOSE, Territory.validate(new Territory.Footprint(103,0,109,6,false,true), List.of(anchor), List.of(anchor)));
        assertEquals(Territory.Result.OK, Territory.validate(new Territory.Footprint(251,0,257,6,false,true), List.of(anchor), List.of(anchor)));
        assertEquals(Territory.Result.OUT_OF_REACH, Territory.validate(new Territory.Footprint(252,0,258,6,false,true), List.of(anchor), List.of(anchor)));
        var road = new Territory.Footprint(260,0,600,1,true,false);
        assertEquals(Territory.Result.OUT_OF_REACH, Territory.validate(new Territory.Footprint(604,0,610,6,false,true), List.of(anchor, road), List.of(anchor,road)));
    }
}
