package org.villageastra.domain;
import java.util.*;
/** Observation baseline: resuming may retain research, but may not hide an extra or substituted building. */
public final class FirstHouseBaseline {
 private FirstHouseBaseline(){}
 public static Set<UUID> buildings(Settlement s,boolean resume,boolean researched){
  if(s.civilization().level()!=1||s.residents().size()!=6||s.buildings().size()!=7||s.governance().playerMayor()!=null||!resume&&researched)throw new IllegalStateException("Not an eligible starter observation");
  var ids=new HashSet<UUID>();for(var b:Settlement.initialBuildings(s.id())){
   if(s.buildings().stream().noneMatch(actual->actual.id().equals(b.id())&&actual.type().equals(b.type())))throw new IllegalStateException("Starter building identity changed");
   ids.add(b.id());
  }return Set.copyOf(ids);
 }
}
