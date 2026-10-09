package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LegacyRepairTimberGameTests {
 private static CompoundTag brokenRoof(ResearchV2Town.Town t){
  ResearchV2Town.lay(t.l,t.e,t.hall(),"town_hall");
  var roof=BuildingPlacement.at(t.e,t.hall(),1,6,2);
  if(!t.l.getBlockState(roof).is(Blocks.DARK_OAK_STAIRS))throw new IllegalStateException("Observed starter roof geometry changed");
  t.l.setBlock(roof,Blocks.AIR.defaultBlockState(),2);
  t.l.setBlock(roof.above(),Blocks.FIRE.defaultBlockState(),2);
  var planned=BuildingRepairs.plan(t.l,t.e,t.hall());
  if(planned.state()==null)throw new IllegalStateException(planned.reason());
  return planned.state();
 }
 @GameTest(template="empty",batch="legacy_repair_timber",timeoutTicks=300)
 public static void burntStarterRoofUsesObservedBirchWithoutARepeatedRepair(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var state=brokenRoof(t);var stock=LogisticsRoutes.chest(t.l,t.e,t.hall());stock.clearContent();stock.setItem(0,new ItemStack(Items.BIRCH_LOG,16));
   h.assertTrue(state.getCompound("cost").getInt("minecraft:dark_oak_stairs")==1,"The observed roof asks for exactly one rare-wood stair");
   HallUpgradeGoal.enqueue(t.l,t.e,state);h.assertTrue(LocalBuildingTimber.requote(t.l,t.e),"An unstarted legacy repair selects observed birch supply");
   var revised=HallUpgradeGoal.inspect(t.l,t.s.id());
   h.assertTrue(revised.getCompound("cost").getInt("minecraft:birch_stairs")==1&&!revised.getCompound("cost").contains("minecraft:dark_oak_stairs"),"One real stair remains payable; no construction discount");
   h.assertTrue(revised.getUUID("id").equals(state.getUUID("id"))&&revised.getUUID("building").equals(t.hall().id()),"Repair and building identities remain unchanged");
   var old=state.getList("ops",Tag.TAG_COMPOUND);var next=revised.getList("ops",Tag.TAG_COMPOUND);h.assertTrue(old.size()==next.size(),"No operation is removed or created");
   for(int i=0;i<old.size();i++){var a=old.getCompound(i);var b=next.getCompound(i);h.assertTrue(a.getLong("pos")==b.getLong("pos")&&a.getCompound("before").equals(b.getCompound("before")),"Coordinates and actual prior geometry remain exact");var step=HallConstructionPlan.step(b);t.l.setBlock(step.pos(),step.after(),2);}
   // Directed placement verifies survey/repair recognition, not paid construction.
   h.assertTrue(BuildingOrders.complete(t.l,t.e,revised)&&BuildingRepairs.damage(t.l,t.e,t.hall()).isEmpty(),"Local repaired component completes and does not generate another repair");
   h.assertTrue(stock.countItem(Items.BIRCH_LOG)==16&&!LocalBuildingTimber.requote(t.l,t.e),"Quote does not convert inventory and its species stays frozen");
   h.assertTrue(!BuildingRepairs.present(Blocks.STONE_STAIRS.defaultBlockState(),Blocks.DARK_OAK_STAIRS.defaultBlockState())&&!BuildingRepairs.present(Blocks.BIRCH_PLANKS.defaultBlockState(),Blocks.DARK_OAK_STAIRS.defaultBlockState()),"Stone stairs and wood of the wrong shape cannot replace the component");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="legacy_repair_timber_custody",timeoutTicks=300)
 public static void paidSpeciesKeepsCustodyAndConfirmedWorkCannotBeRequoted(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var state=brokenRoof(t);var stock=LogisticsRoutes.chest(t.l,t.e,t.hall());stock.clearContent();stock.setItem(0,new ItemStack(Items.BIRCH_LOG,16));stock.setItem(1,new ItemStack(Items.DARK_OAK_STAIRS));
   var paid=WorldJournal.takeAmount(t.l,Settlement.childId(state.getUUID("id"),"fund/0"),Workshops.station(t.e,t.hall()),1,stock.getItem(1).copy(),1);state.getList("cargo",Tag.TAG_COMPOUND).add(paid.save(new CompoundTag()));state.putInt("withdrawals",1);
   HallUpgradeGoal.enqueue(t.l,t.e,state);h.assertTrue(LocalBuildingTimber.requote(t.l,t.e),"Actual paid old species remains surplus while quote uses available wood");var revised=HallUpgradeGoal.inspect(t.l,t.s.id());
   h.assertTrue(revised.getList("cargo",Tag.TAG_COMPOUND).equals(state.getList("cargo",Tag.TAG_COMPOUND))&&revised.getInt("withdrawals")==1,"Paid physical stair and receipt sequence survive unchanged");
   state.putBoolean("funded",true);HallUpgradeGoal.store(t.l,t.s.id(),state);h.assertTrue(!LocalBuildingTimber.requote(t.l,t.e),"Funded repair cannot change");
   state.remove("funded");state.putInt("index",1);HallUpgradeGoal.store(t.l,t.s.id(),state);h.assertTrue(!LocalBuildingTimber.requote(t.l,t.e),"Started repair cannot change");
   state.putInt("index",0);var op=HallConstructionPlan.step(state.getList("ops",Tag.TAG_COMPOUND).getCompound(0));h.assertTrue(WorldJournal.place(t.l,Settlement.childId(state.getUUID("id"),"block/0"),op.pos(),op.before(),op.after()),"Real first operation obtains a receipt");HallUpgradeGoal.store(t.l,t.s.id(),state);
   h.assertTrue(!LocalBuildingTimber.requote(t.l,t.e),"Even a stale index cannot recolor confirmed work");
   h.assertTrue(stock.countItem(Items.BIRCH_LOG)==16&&stock.countItem(Items.DARK_OAK_STAIRS)==0,"Quote never manufactures or silently converts a stack");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="legacy_repair_timber_supply",timeoutTicks=300)
 public static void hallMakesRequiredStairFromRealBirchLogsAtStarterLevel(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var state=brokenRoof(t);var stock=LogisticsRoutes.chest(t.l,t.e,t.hall());stock.clearContent();stock.setItem(0,new ItemStack(Items.BIRCH_LOG,16));
   HallUpgradeGoal.enqueue(t.l,t.e,state);h.assertTrue(LocalBuildingTimber.requote(t.l,t.e),"Legacy repair requests the observed timber");
   var wants=java.util.List.of(new Workshops.Want(net.minecraft.world.item.crafting.Ingredient.of(Items.BIRCH_STAIRS),1,t.hall().id()));
   var first=Workshops.plan(t.l,t.e,t.hall(),stock,wants);
   h.assertTrue(first!=null&&first.outputs().stream().anyMatch(s->s.is(Items.BIRCH_PLANKS)),"Starter hall resolves missing stairs through their plank input");
   // This is a prepared-input production test. Ordinary paid workshop records,
   // labor, withdrawals and delivery execute; it is not natural-growth proof.
   for(long now=20;now<=240000&&stock.countItem(Items.BIRCH_STAIRS)==0;now+=20)Workshops.advance(t.l,t.e,t.hall(),now,wants);
   h.assertTrue(stock.countItem(Items.BIRCH_STAIRS)==4&&stock.countItem(Items.BIRCH_LOG)==14&&stock.countItem(Items.BIRCH_PLANKS)==2,"Two actual logs become eight planks; six actual planks become four stairs");
   h.assertTrue(stock.countItem(Items.DARK_OAK_STAIRS)==0&&stock.countItem(Items.DARK_OAK_LOG)==0,"No rare wood is introduced or converted");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="legacy_repair_timber_scope",timeoutTicks=300)
 public static void mismatchedBuildingsUpgradesAndRelocationsKeepTheirExactQuote(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var original=brokenRoof(t);var stock=LogisticsRoutes.chest(t.l,t.e,t.hall());stock.clearContent();stock.setItem(0,new ItemStack(Items.BIRCH_LOG,16));
   var wrong=new java.util.ArrayList<CompoundTag>();
   var state=original.copy();state.putLong("origin",BlockPos.of(state.getLong("origin")).east().asLong());wrong.add(state);
   state=original.copy();state.putInt("rotation",1);wrong.add(state);
   state=original.copy();state.putUUID("building",java.util.UUID.randomUUID());wrong.add(state);
   state=original.copy();state.putInt("upgradeLevel",2);wrong.add(state);
   state=original.copy();state.putBoolean("relocate",true);wrong.add(state);
   state=original.copy();state.putString("wood","dark_oak");wrong.add(state);
   state=original.copy();state.remove("repair");wrong.add(state);
   for(var candidate:wrong){HallUpgradeGoal.store(t.l,t.s.id(),candidate);h.assertTrue(!LocalBuildingTimber.requote(t.l,t.e)&&HallUpgradeGoal.inspect(t.l,t.s.id()).equals(candidate),"Only this untouched legacy repair may change timber: "+candidate);}
   h.assertTrue(stock.countItem(Items.BIRCH_LOG)==16,"Rejected quotes never touch actual stock");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
