package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;

/** A new generation needs its teacher before the extra construction crew grows. */
public final class EducationStaffing {
 private EducationStaffing(){}
 public static boolean tick(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();if(s.governance().playerMayor()!=null||s.residents().stream().noneMatch(r->r.alive()&&r.life()==Resident.Life.CHILD&&!r.educated()))return false;
  var school=s.buildings().stream().filter(b->b.type().equals("school")&&Population.slots(s,b)>0
   &&s.residents().stream().noneMatch(r->r.alive()&&r.profession()==Profession.TEACHER&&b.equals(s.workplace(r.id())))).findFirst().orElse(null);
  if(school==null)return false;
  var builders=s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.BUILDER).sorted(Comparator.comparing(Resident::id)).toList();
  if(builders.size()<2)return false;
  var project=HallUpgradeGoal.exists(l,s.id())?HallUpgradeGoal.headerView(l,s.id()):new net.minecraft.nbt.CompoundTag();
  UUID lead=!project.getBoolean("complete")&&project.hasUUID("worker")?project.getUUID("worker"):builders.get(0).id();
  for(var r:builders){
   if(r.id().equals(lead)||!Population.mayWork(r)||Trails.onTrail(l,e,r.id())
    ||!(l.getEntity(r.id()) instanceof ResidentEntity body)||body.escortPlayer()!=null||body.blockWork()!=null
    ||CargoCustody.pending(l.getServer(),r.id())||NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,r.id()))||PorterWork.active(PorterWork.inspect(l,r.id())))continue;
   // Let a personally carried furnace batch finish before changing professions.
   if(s.buildings().stream().map(b->Workshops.inspect(l,b.id())).anyMatch(t->t.getBoolean("physicalSmelt")&&!t.getString("stage").equals("idle")&&t.hasUUID("worker")&&t.getUUID("worker").equals(r.id())))continue;
   s.unassign(r.id());CargoCustody.beginReturn(body);s.assign(r.id(),Profession.TEACHER,school.id());return true;
  }
  return false;
 }
}
