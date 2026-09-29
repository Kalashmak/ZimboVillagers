package org.villageastra.domain;

import java.util.Locale;

/** Stable save identifiers. Behaviour is supplied by the server work scheduler. */
public enum Profession {
    MAYOR("town_hall", false), GUARD("guard_house", false), ARCHER_GUARD("archery", false),
    FORESTER("forester", false), MINER("mine", false), FARMER("farm", false),
    TEACHER("school", false), CARTOGRAPHER("cartographer", true), BLACKSMITH("smithy", true),
    SCIENTIST("laboratory", true), BUILDER("town_hall", false), EXPEDITIONER("expedition", true),
    PORTER("warehouse", false), SOLDIER("barracks", false), LIVESTOCK_FARMER("livestock", false),
    MILLER("mill", false), BAKER("restaurant", false), MASON("masonry", false),
    CARPENTER("carpentry", false), CARAVANEER("caravan", false), DOCTOR("clinic", true),
    ENGINEER("engineering", true);

    private final String workplace;
    private final boolean educationRequired;
    Profession(String workplace, boolean educationRequired) {
        this.workplace = workplace;
        this.educationRequired = educationRequired;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String workplace() { return workplace; }
    public boolean educationRequired() { return educationRequired; }
    /** AD-095: the watch, the archers and the soldiers — only a resident with military training may hold these. */
    public boolean military() { return this == GUARD || this == ARCHER_GUARD || this == SOLDIER; }
    public static Profession fromId(String id) { return valueOf(id.toUpperCase(Locale.ROOT)); }
}
