package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;

/** Before a hospital exists, one ill farmer must not stop the village's entire food supply. */
public final class FarmingRelief {
 private FarmingRelief(){}
 private static int rank(Resident r){return r.profession()==null?0:r.profession()==Profession.FORESTER?1:2;}
 public static boolean tick(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();if(s.governance().playerMayor()!=null)return false;boolean changed=false;
  for(var farm:s.buildings()){
   if(!farm.type().equals("farm")||Population.slots(s,farm)==0)continue;
   var crew=s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.FARMER&&farm.equals(s.workplace(r.id()))).toList();
   if(crew.stream().anyMatch(Population::mayWork))continue;
   // Persist each old worker's real cargo before admitting a new job. Unloaded or
   // escorted workers wait for a safe handover instead of abandoning their cargo.
   if(crew.stream().anyMatch(r->!(l.getEntity(r.id()) instanceof ResidentEntity n)||n.escortPlayer()!=null))continue;
   var replacement=s.residents().stream().filter(r->r.alive()&&r.life()==Resident.Life.ADULT&&!r.sick())
    .filter(r->r.profession()==null||r.profession()==Profession.FORESTER||r.profession()==Profession.MINER)
    .filter(r->l.getEntity(r.id()) instanceof ResidentEntity n&&n.escortPlayer()==null&&!CargoCustody.pending(l.getServer(),r.id()))
    .sorted(Comparator.comparingInt(FarmingRelief::rank).thenComparing(Resident::id)).findFirst().orElse(null);
   if(replacement==null)continue;
   for(var old:crew){s.unassign(old.id());CargoCustody.beginReturn((ResidentEntity)l.getEntity(old.id()));}
   s.unassign(replacement.id());CargoCustody.beginReturn((ResidentEntity)l.getEntity(replacement.id()));
   s.assign(replacement.id(),Profession.FARMER,farm.id());changed=true;
  }
  return changed;
 }
}
