package org.villageastra.domain;
import java.util.List;
/** QUEST-003 (AD-106): what a companion is doing and, while it waits, why. Kept free of the game so the translations can be checked on their own. */
public final class EscortStates {
 /** "unseen" is only the board's word for somebody the server has not had loaded since the quest was taken; no companion is ever in it. */
 public static final List<String> STATES=List.of("none","following","waiting","boat","dimension","wounded","arrived","unseen");
 public static final List<String> REASONS=List.of("offline","too_far","other_dimension","no_path","needs_boat","boat_stuck","ordered");
 private EscortStates(){}
 /** A saved state read back: an unknown one is a wait while somebody is being followed, and nothing otherwise. */
 public static String state(String saved,boolean escorted){return STATES.contains(saved)?saved:escorted?"waiting":"none";}
 public static String reason(String saved){return REASONS.contains(saved)?saved:"";}
}
