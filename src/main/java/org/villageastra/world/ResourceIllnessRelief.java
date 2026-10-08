package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;

/** A spare construction worker covers an ill mining crew when real ore orders remain. */
public final class ResourceIllnessRelief {
 private ResourceIllnessRelief(){}
 public static boolean tick(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();if(s.governance().playerMayor()!=null)return false;
  for(var mine:s.buildings()){
   if(!mine.type().equals("mine")||Population.slots(s,mine)==0)continue;
   var crew=s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.MINER&&mine.equals(s.workplace(r.id()))).toList();
   if(crew.isEmpty()||crew.stream().anyMatch(r->!r.sick())
     ||crew.stream().anyMatch(r->!(l.getEntity(r.id()) instanceof ResidentEntity n)||n.escortPlayer()!=null)
     ||MineProspecting.needed(l,e,mine).isEmpty())continue;
   var builders=s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.BUILDER).sorted(Comparator.comparing(Resident::id)).toList();
   var project=HallUpgradeGoal.exists(l,s.id())?HallUpgradeGoal.headerView(l,s.id()):new net.minecraft.nbt.CompoundTag();
   UUID lead=!project.getBoolean("complete")&&project.hasUUID("worker")?project.getUUID("worker"):builders.isEmpty()?null:builders.get(0).id();
   for(var r:s.residents()){
    if(!r.alive()||r.life()!=Resident.Life.ADULT||!Population.mayWork(r)
      ||!(r.profession()==null||r.profession()==Profession.BUILDER&&builders.size()>1&&!r.id().equals(lead))
      ||Trails.onTrail(l,e,r.id())||!(l.getEntity(r.id()) instanceof ResidentEntity body)
      ||body.escortPlayer()!=null||body.blockWork()!=null||CargoCustody.pending(l.getServer(),r.id())
      ||NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,r.id()))||PorterWork.active(PorterWork.inspect(l,r.id())))continue;
    // A personally carried furnace batch completes before its carrier changes jobs.
    if(s.buildings().stream().map(b->Workshops.inspect(l,b.id())).anyMatch(t->t.getBoolean("physicalSmelt")&&!t.getString("stage").equals("idle")&&t.hasUUID("worker")&&t.getUUID("worker").equals(r.id())))continue;
    // The old mine record remains owned until its patient physically returns the
    // withdrawn tool, timber, lights and minerals through the existing custody journal.
    for(var old:crew){s.unassign(old.id());CargoCustody.beginReturn((ResidentEntity)l.getEntity(old.id()));}
    s.unassign(r.id());CargoCustody.beginReturn(body);s.assign(r.id(),Profession.MINER,mine.id());
    if(Boolean.getBoolean("villageastra.autonomyGrowthSmoke"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH miningRelief helper={} patients={} mine={}",r.id(),crew.stream().map(Resident::id).toList(),mine.id());
    return true;
   }
  }
  return false;
 }
}
