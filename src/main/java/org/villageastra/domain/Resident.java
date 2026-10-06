package org.villageastra.domain;

import java.util.Objects;
import java.util.UUID;

/** Entity UUID equals resident ID; unloading an entity never changes its life state. */
public final class Resident {
    public enum Life { CHILD, ADULT, DEAD }
    private final UUID id;
    private Life life;
    private final ResidentProfile profile;
    private boolean educated;
    /** AD-095: military training, earned at the drill ground of the barracks like schooling at the school. */
    private boolean military, recruit;
    private long drillTicks;
    private Profession profession;
    private UUID home;
    private long homelessSince;
    private long born = -1, lastMeal = -1, schoolTicks;
    private int missedMeals;

    public Resident(UUID id, Life life, boolean educated, Profession profession, UUID home, long homelessSince) {
        this(id,life,educated,profession,home,homelessSince,ResidentProfile.generate(id));
    }
    public Resident(UUID id, Life life, boolean educated, Profession profession, UUID home, long homelessSince, ResidentProfile profile) {
        this.profile=Objects.requireNonNull(profile);
        this.id = Objects.requireNonNull(id);
        this.life = Objects.requireNonNull(life);
        if (homelessSince < -1 || (home != null && homelessSince != -1))
            throw new IllegalArgumentException("Inconsistent housing episode");
        this.educated = educated;
        this.home = home;
        this.homelessSince = homelessSince;
        if (profession != null && (life != Life.ADULT || (profession.educationRequired() && !educated)))
            throw new IllegalArgumentException("Unqualified profession");
        this.profession = profession;
        // A resident made as a soldier (a saved village from before training existed, or a founding watch) is a trained one.
        if (profession != null && profession.military()) this.military = true;
    }
    public ResidentProfile profile() { return profile; }
    public UUID id() { return id; }
    public Life life() { return life; }
    public boolean educated() { return educated; }
    public boolean military() { return military; }
    public boolean recruit() { return recruit; }
    public long drillTicks() { return drillTicks; }
    /** The labor office sends an adult without a trade to the drill ground; a trained one is no recruit any more. */
    public void enlist() { if (life == Life.ADULT && !military && profession == null) recruit = true; }
    public void discharge() { recruit = false; }
    /** Time at the drill ground, counted only for a recruit that is still untrained. */
    public void drill(long ticks) { if (recruit && !military && life == Life.ADULT) drillTicks += ticks; }
    /** Training complete: from now on the resident may serve in the watch, at the archery or in the barracks. */
    public boolean trainMilitary() { if (military || life != Life.ADULT) return false; military = true; recruit = false; return true; }
    /** Saved training state (AD-095); absent values keep the defaults of an older save. */
    public void restoreTraining(boolean military, boolean recruit, long drillTicks) {
        if (drillTicks < 0) throw new IllegalArgumentException("Invalid drill time");
        this.military = this.military || military; this.recruit = recruit && !this.military; this.drillTicks = drillTicks;
    }
    public Profession profession() { return profession; }
    public UUID home() { return home; }
    public long homelessSince() { return homelessSince; }
    public boolean alive() { return life != Life.DEAD; }
    public boolean educate() {
        if (life != Life.CHILD || educated) return false;
        educated = true;
        return true;
    }
    public void growUp() { if (life == Life.CHILD) life = Life.ADULT; }
    public void assign(Profession role) {
        if (life != Life.ADULT || (role != null && role.educationRequired() && !educated) || (role != null && role.military() && !military))
            throw new IllegalStateException("Unqualified profession");
        profession = role;
        if (role != null) recruit = false;
    }
    void house(UUID home) {
        if (!alive()) throw new IllegalStateException("Dead resident");
        this.home = Objects.requireNonNull(home);
        homelessSince = -1;
    }
    void loseHome(long activeTicks) {
        if (activeTicks < 0) throw new IllegalArgumentException("Negative active time");
        if (home != null) { home = null; homelessSince = activeTicks; }
    }
    public boolean mustSeekNewSettlement(long ticks) {
        return alive() && home == null && homelessSince >= 0 && ticks - homelessSince >= 10800L * 20;
    }
    public long born() { return born; }
    public long lastMeal() { return lastMeal; }
    public int missedMeals() { return missedMeals; }
    public long schoolTicks() { return schoolTicks; }
    /** Saved needs/childhood state (AD-031); absent values keep the defaults of an older save. */
    public void restoreNeeds(long born, long lastMeal, int missedMeals, long schoolTicks) {
        if (born < -1 || lastMeal < -1 || missedMeals < 0 || schoolTicks < 0) throw new IllegalArgumentException("Invalid resident needs");
        this.born = born; this.lastMeal = lastMeal; this.missedMeals = missedMeals; this.schoolTicks = schoolTicks;
    }
    public void born(long activeTicks) { if (life != Life.CHILD) throw new IllegalStateException("Only a child is born"); born = activeTicks; lastMeal = activeTicks; }
    public void ate(long activeTicks) { lastMeal = activeTicks; missedMeals = 0; }
    public void missedMeal(long activeTicks) { lastMeal = activeTicks; missedMeals++; }
    /** AD-111 interim freeze: a far village nobody cares about neither eats nor starves; the due passes and missed meals stay as they are. */
    public void skipMeal(long activeTicks) { lastMeal = activeTicks; }
    public void attendSchool(long ticks) { if (life == Life.CHILD && !educated) schoolTicks += ticks; }
    /** AD-151 (owner: "III opens the military class — a child grows up a soldier"): a child taught in the school's military class; it grows up
     *  trained for arms (no drill at the barracks) when it grows up educated. */
    private boolean cadet;
    /** AD-152: ill — does not work until the village's medicine cures it (the hospital, a medic at home, medicine, or the clinic of VI). */
    private boolean sick;
    private long recoveryRest;
    public long recoveryRest() { return recoveryRest; }
    public void restoreRecoveryRest(long ticks) { if(ticks<0)throw new IllegalArgumentException("Invalid recovery rest"); recoveryRest=sick?ticks:0; }
    public void restForRecovery(long ticks) { if(sick&&ticks>0)recoveryRest+=ticks; }
    public boolean sick() { return sick; }
    public boolean fallIll() { if (!alive() || sick) return false; sick = true; recoveryRest=0; return true; }
    public boolean cure() { if (!sick) return false; sick = false; recoveryRest=0; return true; }
    public void restoreSick(boolean sick) { this.sick = sick; }
    public boolean cadet() { return cadet; }
    public void enlistCadet() { if (life == Life.CHILD && !educated) cadet = true; }
    public void restoreCadet(boolean cadet) { this.cadet = cadet && life == Life.CHILD; }
    public void die() { life = Life.DEAD; profession = null; home = null; homelessSince = -1; recruit = false; }
}
