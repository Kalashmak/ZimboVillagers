package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LocalBuildingTimberGameTests {
 private static CompoundTag quote(ResearchV2Town.Town t){
  var origin=t.e.center().offset(35,0,0);
  for(int x=-4;x<=17;x++)for(int z=-4;z<=17;z++){t.l.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);t.l.setBlock(origin.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<24;y++)t.l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var survey=BuildingOrders.survey(t.l,t.e,"school",1,origin,null,-1,false,null,"");if(!survey.ok())throw new IllegalStateException(survey.reason());return survey.state();
 }
 @GameTest(template="empty",batch="local_building_timber",timeoutTicks=300)
 public static void untouchedSchoolKeepsPaidStoneAndReceiptIdsWhenUsingObservedOak(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var state=quote(t);var id=state.getUUID("id");var stock=LogisticsRoutes.chest(t.l,t.e,t.hall());stock.clearContent();stock.setItem(0,new ItemStack(Items.COBBLESTONE,64));stock.setItem(1,new ItemStack(Items.OAK_LOG,32));
   var taken=WorldJournal.takeAmount(t.l,Settlement.childId(id,"fund/0"),Workshops.station(t.e,t.hall()),0,stock.getItem(0).copy(),64);state.getList("cargo",Tag.TAG_COMPOUND).add(taken.save(new CompoundTag()));state.putInt("withdrawals",1);HallUpgradeGoal.enqueue(t.l,t.e,state);
   h.assertTrue(LocalBuildingTimber.requote(t.l,t.e),"An untouched NPC school selects the real oak supply");var revised=HallUpgradeGoal.inspect(t.l,t.s.id());
   h.assertTrue(revised.getString("wood").equals("oak")&&revised.getUUID("id").equals(id)&&revised.getUUID("project").equals(state.getUUID("project"))&&revised.getInt("withdrawals")==1&&revised.getList("cargo",Tag.TAG_COMPOUND).equals(state.getList("cargo",Tag.TAG_COMPOUND)),"Project identity, real paid cargo and next withdrawal survive");
   h.assertTrue(revised.getCompound("cost").getAllKeys().stream().noneMatch(k->k.contains("dark_oak")||k.contains("spruce"))&&ConstructionFunding.missing(revised).get("minecraft:cobblestone")==revised.getCompound("cost").getInt("minecraft:cobblestone")-64,"The bill asks only for local wood and counts the same paid stone");
   var oldOps=state.getList("ops",Tag.TAG_COMPOUND);var newOps=revised.getList("ops",Tag.TAG_COMPOUND);h.assertTrue(oldOps.size()==newOps.size(),"Operations retain their order and count");
   for(int i=0;i<oldOps.size();i++){var a=oldOps.getCompound(i);var b=newOps.getCompound(i);h.assertTrue(a.getLong("pos")==b.getLong("pos")&&a.getCompound("before").equals(b.getCompound("before")),"Real prior world and operation coordinates remain unchanged");var step=HallConstructionPlan.step(b);t.l.setBlock(step.pos(),step.after(),3);}
   h.assertTrue(!LocalBuildingTimber.requote(t.l,t.e)&&stock.countItem(Items.COBBLESTONE)==0&&stock.countItem(Items.OAK_LOG)==32,"Repeated update neither changes the frozen species nor spends or grants inventory");
   // Directed geometry placement above is not a natural or paid completion proof.
   h.assertTrue(BuildingOrders.complete(t.l,t.e,revised),"The quoted oak geometry completes through the normal registration check");var school=t.s.buildings().stream().filter(b->b.type().equals("school")).findFirst().orElseThrow();h.assertTrue(school.wood().equals("oak")&&BuildingRepairs.damage(t.l,t.e,school).isEmpty(),"Registration and later repair use the identical frozen local timber");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="local_building_timber_paid",timeoutTicks=300)
 public static void alreadyPaidDifferentWoodRemainsRealCargoAndStartedWorkCannotBeRecolored(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var state=quote(t);var stock=LogisticsRoutes.chest(t.l,t.e,t.hall());stock.clearContent();stock.setItem(0,new ItemStack(Items.OAK_LOG,32));stock.setItem(1,new ItemStack(Items.DARK_OAK_LOG));
   var taken=WorldJournal.takeAmount(t.l,Settlement.childId(state.getUUID("id"),"fund/0"),Workshops.station(t.e,t.hall()),1,stock.getItem(1).copy(),1);state.getList("cargo",Tag.TAG_COMPOUND).add(taken.save(new CompoundTag()));state.putInt("withdrawals",1);HallUpgradeGoal.enqueue(t.l,t.e,state);
   h.assertTrue(LocalBuildingTimber.requote(t.l,t.e)&&HallUpgradeGoal.inspect(t.l,t.s.id()).getList("cargo",Tag.TAG_COMPOUND).equals(state.getList("cargo",Tag.TAG_COMPOUND)),"Paid dark oak stays actual dark oak in surplus cargo; no implicit conversion or lost item");
   state.putInt("index",1);HallUpgradeGoal.store(t.l,t.s.id(),state);h.assertTrue(!LocalBuildingTimber.requote(t.l,t.e),"Started operations keep their original design");
   h.assertTrue(stock.countItem(Items.OAK_LOG)==32&&stock.countItem(Items.DARK_OAK_LOG)==0,"Neither re-quoting nor refusing started work changes real stock");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="local_building_timber_surplus",timeoutTicks=300)
 public static void surplusOldWindowReturnsThroughBuilderAndReplaysWithoutDuplicates(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);ResidentEntity npc=null;
  try{
   var original=quote(t);var id=original.getUUID("id");var stock=LogisticsRoutes.chest(t.l,t.e,t.hall());var pos=Workshops.station(t.e,t.hall());stock.clearContent();
   var window=BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("villageastra:dark_oak_framed_window"));stock.setItem(0,new ItemStack(window));stock.setItem(1,new ItemStack(Items.OAK_LOG,32));
   var paid=WorldJournal.takeAmount(t.l,Settlement.childId(id,"fund/0"),pos,0,stock.getItem(0).copy(),1);original.getList("cargo",Tag.TAG_COMPOUND).add(paid.save(new CompoundTag()));original.putInt("withdrawals",1);HallUpgradeGoal.enqueue(t.l,t.e,original);
   h.assertTrue(LocalBuildingTimber.requote(t.l,t.e),"The one real paid old window cannot freeze all local timber supply");var state=HallUpgradeGoal.inspect(t.l,t.s.id());h.assertTrue(!state.getCompound("cost").contains("villageastra:dark_oak_framed_window")&&ItemStack.of(state.getList("cargo",Tag.TAG_COMPOUND).getCompound(0)).is(window),"The new bill and original physical window remain distinct");
   // Prepare a completed geometry fixture, then exercise only the actual cargo
   // return phase. This is not a claim of paid or autonomous whole construction.
   var ops=state.getList("ops",Tag.TAG_COMPOUND);for(var raw:ops){var op=(CompoundTag)raw;var step=HallConstructionPlan.step(op);t.l.setBlock(step.pos(),step.after(),3);op.putBoolean("done",true);}state.putInt("index",ops.size());state.putBoolean("funded",true);HallUpgradeGoal.store(t.l,t.s.id(),state);var checkpoint=state.copy();
   npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.BUILDER,t.hall().id());npc.bind(t.s.id(),r);npc.setNoAi(true);npc.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);
   var goal=new HallUpgradeGoal(npc,true);h.assertTrue(goal.canUse(),"The real builder resumes the return phase");goal.start();goal.tick();h.assertTrue(stock.countItem(window)==1,"The original surplus window is physically returned to the hall");
   HallUpgradeGoal.store(t.l,t.s.id(),checkpoint);goal=new HallUpgradeGoal(npc,true);h.assertTrue(goal.canUse(),"Old return checkpoint resumes");goal.start();goal.tick();h.assertTrue(stock.countItem(window)==1&&stock.countItem(Items.OAK_LOG)==32,"Replayed builder receipt neither duplicates the old window nor touches local logs");
  }finally{if(npc!=null)npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
}
