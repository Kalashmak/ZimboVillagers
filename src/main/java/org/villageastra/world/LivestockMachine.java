package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
/** AD-138 VI (owner 2026-09-22: "feeders are filled automatically, surplus animals are slaughtered automatically by the wolves, and all
 *  drops are carried to the chest"): the yard's machine, one real operation a turn (Machines, every 30 ticks at VI), in this order —
 *  drops lying in the yard into its chest; a feeder below full filled from the chest's feed (two items a step, as the keeper does); the
 *  kennel's bin topped up from the chest to a piece of meat a wolf; a pen above PEN_CAP given to a free kennel wolf to cull (no wolf, no
 *  cull — the machine makes nothing up). Every item moves through the journal between real containers (the rule of Machines). */
public final class LivestockMachine {
 private LivestockMachine(){}
 /** Why the last turn did nothing (tests, probes). */
 public static volatile String lastReason="";
 /** Items the machines carried from the yards' ground into their chests since the start (probes: the cull's drops reached the chest). */
 public static final java.util.concurrent.atomic.AtomicInteger GATHERED=new java.util.concurrent.atomic.AtomicInteger();
 private static final Map<UUID,Integer> SEQ=new java.util.concurrent.ConcurrentHashMap<>();
 public static boolean cycle(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){
  // The journal ids of a turn: the village clock and a count of the yard's operations (two turns in one tick — a paused clock, a test —
  // never share an id).
  long clock=SettlementData.get(l.getServer()).clock().ticks();long now=clock*1000+Math.floorMod(SEQ.merge(yard.id(),1,Integer::sum),1000);
  int moved=LivestockGoal.gather(l,e,yard,now);if(moved>0){GATHERED.addAndGet(moved);lastReason="gathered";return true;}
  for(var p:LivestockGoal.pens(l,e,yard))if(LivestockGoal.fill(l,e,yard,p,now)){lastReason="filled "+p.index();return true;}
  if(bin(l,e,yard,now)){lastReason="bin";return true;}
  for(var p:LivestockGoal.pens(l,e,yard)){var herd=LivestockPens.herd(l,e,yard,p);
   if(herd.size()<=LivestockPens.PEN_CAP||LivestockGoal.cullsLeft(yard,p,clock)<=0)continue;
   if(herd.stream().anyMatch(a->VillageWolves.culling(a.getUUID())))continue;
   var surplus=herd.stream().filter(a->!a.isBaby()&&LivestockPens.inside(e,yard,p,a.blockPosition())).findFirst().orElse(null);if(surplus==null)continue;
   var wolf=VillageWolves.freeWolf(l,e);if(wolf==null){lastReason="no wolves";return false;}
   VillageWolves.cull(wolf,surplus);LivestockGoal.culled(yard,p,clock);lastReason="cull "+p.index();return true;}
  lastReason="idle";return false;
 }
 /** One piece of meat from the yard chest into the kennel's bin while it holds fewer than the kennel has wolves. */
 static boolean bin(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,long now){
  var kennel=VillageWolves.kennel(e);if(kennel==null||!yard.id().equals(e.settlement().annexParent(kennel.id())))return false;
  int wolves=VillageWolves.wolves(l,e).size();var bin=LogisticsRoutes.chest(l,e,kennel);var chest=LogisticsRoutes.chest(l,e,yard);
  if(wolves==0||bin==null||chest==null||LogisticsRoutes.count(bin,VillageWolves::meat)>=wolves)return false;
  var id=Settlement.childId(yard.id(),"machine/bin/"+now);var got=WorldJournal.recoverAmount(l,id);
  if(got.isEmpty()&&!WorldJournal.exists(l,id))for(int slot=0;slot<chest.getContainerSize();slot++)if(VillageWolves.meat(chest.getItem(slot))){got=WorldJournal.takeAmount(l,id,LogisticsRoutes.position(e,yard),slot,chest.getItem(slot).copy(),1);break;}
  if(got.isEmpty())return false;
  return WorldJournal.deposit(l,Settlement.childId(id,"in"),LogisticsRoutes.position(e,kennel),got);
 }
}
