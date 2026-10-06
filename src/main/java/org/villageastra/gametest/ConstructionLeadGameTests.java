package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ConstructionLeadGameTests {
 @GameTest(template="empty",batch="construction_lead",timeoutTicks=200)
 public static void sickFirstBuilderDoesNotBlockNewProjectFunding(GameTestHelper h){lead(h,false);}
 @GameTest(template="empty",batch="construction_lead_handover",timeoutTicks=200)
 public static void sickLeaderHandsOverExactPaidCargoAndOldGoalStops(GameTestHelper h){lead(h,true);}
 private static void lead(GameTestHelper h,boolean claimed){
  var t=ResearchV2Town.town(h,null);var id=t.s.id();
  try{
   var home=Settlement.childId(id,"home");var old=new Resident(new UUID(Long.MIN_VALUE,1),Resident.Life.ADULT,true,null,null,-1);var next=new Resident(new UUID(1,1),Resident.Life.ADULT,true,null,null,-1);
   t.s.admit(old,home);t.s.admit(next,home);t.s.assign(old.id(),Profession.BUILDER,t.hall().id());t.s.assign(next.id(),Profession.BUILDER,t.hall().id());
   var oldBody=VillageAstra.RESIDENT.get().create(t.l);oldBody.setUUID(old.id());oldBody.bind(id,old);var body=VillageAstra.RESIDENT.get().create(t.l);body.setUUID(next.id());body.bind(id,next);
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.setItem(0,new ItemStack(Items.OAK_PLANKS,4));var pos=LogisticsRoutes.position(t.e,t.hall());body.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);oldBody.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);
   var state=new CompoundTag();var project=UUID.randomUUID();state.putUUID("id",project);state.putUUID("project",project);state.putString("kind","building");state.putString("design","farm");state.putLong("origin",t.e.center().offset(35,0,0).asLong());state.put("ops",new ListTag());
   var cost=new CompoundTag();cost.putInt("minecraft:oak_planks",4);state.put("cost",cost);var held=WorldJournal.takeAmount(t.l,Settlement.childId(project,"fund/0"),pos,0,chest.getItem(0).copy(),1);var cargo=new ListTag();cargo.add(held.save(new CompoundTag()));state.put("cargo",cargo);state.putInt("withdrawals",1);
   var waiting=new CompoundTag();waiting.putUUID("id",UUID.randomUUID());waiting.putString("design","school");state.put("waitingProject",waiting);HallUpgradeGoal.store(t.l,id,state);
   var previous=new HallUpgradeGoal(oldBody,true);if(claimed)h.assertTrue(previous.canUse(),"Healthy former leader starts before falling ill");old.fallIll();if(claimed)h.assertTrue(!previous.canContinueToUse(),"Illness pauses the old builder before any handover");
   var goal=new HallUpgradeGoal(body,true);h.assertTrue(goal.canUse(),"The healthy builder can fund despite an earlier sick UUID");
   var after=HallUpgradeGoal.inspect(t.l,id);h.assertTrue(after.getUUID("worker").equals(next.id())&&after.getUUID("id").equals(project)&&after.getInt("withdrawals")==1&&after.getList("cargo",Tag.TAG_COMPOUND).equals(cargo)&&after.getCompound("waitingProject").equals(waiting),"Only the worker changes: paid material, waiting project and receipt identity survive");
   if(claimed){h.assertTrue(!previous.canContinueToUse(),"The former leader stops after ownership changes");previous.tick();h.assertTrue(chest.countItem(Items.OAK_PLANKS)==3,"A stale leader cannot withdraw again");}
   goal.tick();after=HallUpgradeGoal.inspect(t.l,id);int paid=0;for(var raw:after.getList("cargo",Tag.TAG_COMPOUND))paid+=ItemStack.of((CompoundTag)raw).getCount();
   h.assertTrue(paid==4&&chest.countItem(Items.OAK_PLANKS)==0&&WorldJournal.recoverAmount(t.l,Settlement.childId(project,"fund/1")).getCount()==3,"Successor withdraws the three missing real planks under the original next receipt");
  }finally{HallUpgradeGoal.drop(t.l,id);ResearchV2Town.done(t);}h.succeed();
 }
}
