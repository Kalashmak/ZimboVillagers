package org.villageastra.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SettlementTest {
    @Test void exactStartAndStableIdentities() {
        UUID id = UUID.randomUUID();
        Settlement s = Settlement.initial(id);
        assertEquals(6, s.residents().size());
        assertEquals(3, s.homes().size());
        s.homes().forEach(h -> assertEquals(2, s.occupancy(h.id())));
        assertTrue(s.residents().stream().noneMatch(Resident::educated));
        assertEquals(s.residents().stream().map(Resident::id).toList(),
                Settlement.initial(id).residents().stream().map(Resident::id).toList());
    }
    @Test void fullHousingRejectsAdmissionAndDuplicateIdentity() {
        Settlement s = Settlement.initial(UUID.randomUUID());
        UUID home = s.homes().iterator().next().id();
        assertThrows(IllegalStateException.class, () -> s.admit(
                new Resident(UUID.randomUUID(), Resident.Life.CHILD, false, null, null, -1), home));
        assertThrows(IllegalArgumentException.class, () -> s.restoreResident(s.residents().iterator().next()));
        assertEquals(6, s.residents().size());
    }
    @Test void adultsCannotBeEducatedButChildrenKeepEducation() {
        Resident r = new Resident(UUID.randomUUID(), Resident.Life.CHILD, false, null, null, -1);
        assertTrue(r.educate());
        assertThrows(IllegalStateException.class, () -> r.assign(Profession.SCIENTIST));
        r.growUp();
        r.assign(Profession.SCIENTIST);
        assertTrue(r.educated());
        Resident adult = Settlement.initial(UUID.randomUUID()).residents().iterator().next();
        assertFalse(adult.educate());
        assertThrows(IllegalStateException.class, () -> adult.assign(Profession.DOCTOR));
    }
    @Test void housingLossTimerIsNotResetByRepeatedDamage() {
        Settlement s = Settlement.initial(UUID.randomUUID());
        Resident r = s.residents().iterator().next();
        UUID home = r.home();
        s.damageHome(home, 100);
        s.damageHome(home, 1000);
        assertEquals(100, r.homelessSince());
        assertFalse(r.mustSeekNewSettlement(100 + 10800L * 20 - 1));
        assertTrue(r.mustSeekNewSettlement(100 + 10800L * 20));
        UUID replacement = UUID.randomUUID();
        s.addHome(new Settlement.Home(replacement, 2, 4, true));
        s.rehouse(r.id(), replacement);
        assertFalse(r.mustSeekNewSettlement(Long.MAX_VALUE));
    }
    @Test void emptyServerAndPauseDoNotAdvanceTime() {
        SimulationClock clock = new SimulationClock(42);
        for (int i = 0; i < 1000; i++) { clock.advance(false, false); clock.advance(true, true); }
        assertEquals(42, clock.ticks());
        assertTrue(clock.advance(true, false));
        assertEquals(43, clock.ticks());
        assertEquals(43, new SimulationClock(clock.ticks()).ticks());
    }
    @Test void rolesMatchLocksAndNoExtraMayor() {
        assertEquals(22, Profession.values().length);
        assertEquals(6, java.util.Arrays.stream(Profession.values()).filter(Profession::educationRequired).count());
        Settlement s = Settlement.initial(UUID.randomUUID());
        Resident farmer = s.residents().stream().filter(r -> r.profession() == Profession.FARMER).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> s.assign(farmer.id(), Profession.MAYOR, "town_hall", 0));
        assertThrows(IllegalStateException.class, () -> s.assign(farmer.id(), Profession.SOLDIER, "barracks", 8));
        assertThrows(IllegalStateException.class, () -> s.assign(farmer.id(), Profession.BUILDER, "workshop", 0));
        s.assign(farmer.id(), Profession.TEACHER, "school", 0);
        assertEquals(Profession.TEACHER, farmer.profession());
    }
}
