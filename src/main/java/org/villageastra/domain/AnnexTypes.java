package org.villageastra.domain;
import java.util.*;
/** AD-135: an annex is a building of its own beside its parent building, and it works as the building it stands for — the carpenter's
 *  lean-to beside the forester's hut is the village's carpentry. Every system that matches a profession to its workplace, or a building to
 *  its workshop, reads the type through {@link #workplace}. Where the annex stands and what opens it lives in world/Annexes. */
public final class AnnexTypes {
    private AnnexTypes(){}
    private static final Map<String,String> WORKPLACE=Map.of("carpentry_annex","carpentry","masonry_annex","masonry","mill_annex","mill");
    /** AD-138: annexes that are equipment, not a workshop — nobody works there (the wolf kennel beside the livestock yard). */
    private static final Set<String> EQUIPMENT=Set.of("kennel_annex","dog_academy_annex");
    private static final Set<String> TYPES;
    static{var all=new LinkedHashSet<>(WORKPLACE.keySet());all.addAll(EQUIPMENT);TYPES=Collections.unmodifiableSet(all);}
    /** The workplace type a building works as: an annex's own trade, every other type (an equipment annex too) itself. */
    public static String workplace(String type){return WORKPLACE.getOrDefault(type,type);}
    public static boolean annex(String type){return TYPES.contains(type);}
    /** An annex that is a workshop of a trade (the carpenter's, the stonecutter's, the miller's). */
    public static boolean workshop(String type){return WORKPLACE.containsKey(type);}
    public static Set<String> types(){return TYPES;}
}
