package org.villageastra.world;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.persistence.WorldJournal;
import java.util.*;

/** The physical scientist at the laboratory desk. AD-136: every assigned scientist works (no longer only the first by id): his place writes
 *  scientific works by the village clock ({@link ScienceWorks}) and at the desk he spends them on the village's target, one a step. */
public final class ResearchGoal extends Goal {
 private final ResidentEntity worker;private BlockPos approach;
 public ResearchGoal(ResidentEntity worker){this.worker=worker;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public boolean canUse(){
  if(!(worker.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0||worker.settlementId()==null||worker.escortPlayer()!=null)return false;
  if(!CargoCustody.mayStartWork(worker))return false;
  var e=SettlementData.get(l.getServer()).entry(worker.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return false;
  var r=e.settlement().resident(worker.getUUID());return r!=null&&r.alive()&&r.educated()&&r.profession()==Profession.SCIENTIST&&e.settlement().workplace(r.id())!=null;
 }
 @Override public void stop(){approach=null;worker.displayWorkItem(ItemStack.EMPTY);}
 @Override public void tick(){
  // Vanilla navigation finishes within a fraction of the final cell; finish the last step physically.
  // Adjacent desks otherwise leave their workers overlapping at the shared cell edge.
  if(approach!=null&&worker.blockPosition().equals(approach)){
   double remaining=worker.distanceToSqr(approach.getX()+.5,approach.getY(),approach.getZ()+.5);
   if(remaining>.04){worker.getNavigation().stop();worker.getMoveControl().setWantedPosition(approach.getX()+.5,approach.getY(),approach.getZ()+.5,.8);return;}approach=null;
  }
  if(worker.tickCount%20!=0)return;
  var level=(ServerLevel)worker.level();var data=SettlementData.get(level.getServer());var entry=data.entry(worker.settlementId());var s=entry.settlement();
  var building=s.workplace(worker.getUUID());
  // AD-154: every scientist goes to the desk of his own place (LabDesks), stands on its free cell and faces it.
  var own=LabDesks.of(level,entry,building,worker.getUUID());var desk=own.stand();var nav=worker.getNavigation();
  double far=worker.distanceToSqr(desk.getX()+.5,desk.getY(),desk.getZ()+.5);
  if(far>.04){approach=desk;if(worker.blockPosition().equals(desk))return;var path=nav.createPath(desk,0);if(path!=null&&path.canReach()){nav.moveTo(path,.8);return;}
   if(far>6.25){nav.moveTo(desk.getX()+.5,desk.getY(),desk.getZ()+.5,.8);return;}}
  nav.stop();worker.getLookControl().setLookAt(own.block().getX()+.5,own.block().getY()+.9,own.block().getZ()+.5);var civ=s.civilization();
  if(org.villageastra.server.BookResearch.consume(level,entry,building)){worker.displayWorkItem(new ItemStack(org.villageastra.VillageAstra.RESEARCH_VOLUME.get()));worker.workStatus("researching");worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);return;}
  // AD-136: his place writes works by the clock; the resident card says whether it writes, waits on a full chest or has no place.
  var place=ScienceWorks.status(level,entry,worker.getUUID());
  if(org.villageastra.server.BookResearch.selected(level,entry)||!place.isEmpty()){worker.displayWorkItem(new ItemStack(Items.WRITABLE_BOOK));worker.workStatus(place.isEmpty()?"science_no_place":place);return;}
  if(civ.active().isEmpty()) {var next=civ.available().stream().findFirst();if(next.isEmpty()){worker.workStatus("research_tier_complete");return;}civ.begin(next.get().id());data.setDirty();}
  if(!org.villageastra.server.BookResearch.legacyAllowed(level,entry,civ.active())){worker.workStatus("missing_research_supply");return;}
  var id=Settlement.childId(s.id(),"research/paper/"+civ.active());ItemStack paper=WorldJournal.recoverTake(level,id);BlockPos chest=BuildingPlacement.at(entry,building,1,1,4);
  if(paper.isEmpty()&&!WorldJournal.exists(level,id)&&level.getBlockEntity(chest) instanceof Container c)
   for(int slot=0;slot<c.getContainerSize();slot++)if(c.getItem(slot).is(Items.PAPER)){paper=WorldJournal.take(level,id,chest,slot,c.getItem(slot).copy());break;}
  if(paper.isEmpty()){worker.displayWorkItem(ItemStack.EMPTY);worker.workStatus("missing_research_supply");return;}
  worker.displayWorkItem(paper);worker.workStatus("researching");
  civ.work(20,true,true);data.setDirty();worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
 }
}
