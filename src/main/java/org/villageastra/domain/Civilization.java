package org.villageastra.domain;

import java.util.*;

/** Completed town hall level gates research availability; progress never manufactures physical goods. */
public final class Civilization {
    public record Research(String id,int level,long ticks,Set<String> prerequisites) {}
    public static final List<Research> CATALOG=List.of(
        new Research("cartography",1,12000,Set.of()),new Research("ironworking",1,16000,Set.of()),
        new Research("housing_2",1,12000,Set.of()),new Research("milling",1,12000,Set.of()),
        new Research("medicine",2,20000,Set.of("ironworking")),new Research("caravans",2,24000,Set.of("cartography")),
        new Research("engineering",2,24000,Set.of("ironworking")),new Research("fortification",2,24000,Set.of("engineering")),
        new Research("deep_mining",3,30000,Set.of("engineering")),new Research("siege",3,36000,Set.of("fortification")));
    private int hallLevel=1;private final Set<String> completed=new LinkedHashSet<>();private String active="";private long progress;
    public int level(){return hallLevel;}public Set<String> completed(){return Collections.unmodifiableSet(completed);}
    public String active(){return active;}public long progress(){return progress;}
    public List<Research> available(){return CATALOG.stream().filter(r->r.level<=hallLevel&&!completed.contains(r.id)&&completed.containsAll(r.prerequisites)).toList();}
    /** Called only after a material-funded hall project has passed its physical completion check. */
    public void completedHallUpgrade(int newLevel){if(newLevel!=hallLevel+1||newLevel>6)throw new IllegalArgumentException("Hall levels must be completed in order");hallLevel=newLevel;}
    public void begin(String id){if(!active.isEmpty())throw new IllegalStateException("Research already active");if(available().stream().noneMatch(r->r.id.equals(id)))throw new IllegalArgumentException("Research locked");active=id;progress=0;}
    public boolean work(long ticks,boolean educatedScientistPresent,boolean supplyPaid){
        if(ticks<0)throw new IllegalArgumentException("Negative labor");if(active.isEmpty()||!educatedScientistPresent||!supplyPaid)return false;
        Research r=CATALOG.stream().filter(x->x.id.equals(active)).findFirst().orElseThrow();
        progress=Math.min(r.ticks,Math.addExact(progress,ticks));
        if(progress==r.ticks){completed.add(active);active="";progress=0;return true;}return false;
    }
    public static Civilization restore(int level,Collection<String> completed,String active,long progress){
        if(level<1||level>6||progress<0)throw new IllegalArgumentException("Invalid civilization save");
        Civilization c=new Civilization();c.hallLevel=level;
        for(String id:completed){Research r=CATALOG.stream().filter(x->x.id.equals(id)).findFirst().orElseThrow();if(r.level>level)throw new IllegalArgumentException("Completed research above civilization");c.completed.add(id);}
        for(Research r:CATALOG)if(c.completed.contains(r.id)&&!c.completed.containsAll(r.prerequisites))throw new IllegalArgumentException("Missing research prerequisite");
        if(!active.isEmpty()){c.begin(active);Research r=CATALOG.stream().filter(x->x.id.equals(active)).findFirst().orElseThrow();if(progress>=r.ticks)throw new IllegalArgumentException("Invalid unfinished labor");c.progress=progress;}
        else if(progress!=0)throw new IllegalArgumentException("Labor without research");return c;
    }
}
