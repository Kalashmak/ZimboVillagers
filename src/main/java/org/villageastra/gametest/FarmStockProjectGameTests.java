package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmStockProjectGameTests {
 private static CompoundTag legacy(ResearchV2Town.Town t){
  var id=UUID.randomUUID();var state=new CompoundTag();state.putUUID("id",id);state.putUUID("project",id);state.putString("kind","building");state.putString("design","farm");state.putString("wood","oak");var ops=new ListTag();int count=0;for(var cell:BuildingBlueprints.layout("farm",t.e.center().offset(35,0,35)).entrySet())if(cell.getValue().is(Blocks.BARREL)){var op=new CompoundTag();op.putLong("pos",cell.getKey().asLong());op.put("before",NbtUtils.writeBlockState(t.l.getBlockState(cell.getKey())));op.put("after",NbtUtils.writeBlockState(Blocks.HAY_BLOCK.defaultBlockState()));op.putString("item","minecraft:hay_block");ops.add(op);count++;}if(count!=10)throw new GameTestAssertException("Expected ten empty farm storage cells, found "+count);state.put("ops",ops);var cost=new CompoundTag();cost.putInt("minecraft:hay_block",count);state.put("cost",cost);state.put("cargo",new ListTag());return state;
 }
 @GameTest(template="empty",batch="farm_stock_quote",timeoutTicks=200)
 public static void unpaidFarmStorageKeepsPaidHayAndTheWaitingProject(GameTestHelper h){var t=ResearchV2Town.town(h,null);try{
  var original=legacy(t);var id=original.getUUID("id");var stock=LogisticsRoutes.chest(t.l,t.e,t.hall());stock.clearContent();stock.setItem(0,new ItemStack(Items.HAY_BLOCK,2));var paid=WorldJournal.takeAmount(t.l,Settlement.childId(id,"fund/0"),LogisticsRoutes.position(t.e,t.hall()),0,stock.getItem(0).copy(),2);original.getList("cargo",Tag.TAG_COMPOUND).add(paid.save(new CompoundTag()));original.putInt("withdrawals",1);var waiting=new CompoundTag();waiting.putUUID("project",UUID.randomUUID());waiting.putString("design","home");original.put("waitingProject",waiting);HallUpgradeGoal.store(t.l,t.s.id(),original);
  h.assertTrue(FarmStockProject.requote(t.l,t.e),"Untouched old farm uses real empty storage rather than ninety grains");var updated=HallUpgradeGoal.inspect(t.l,t.s.id());h.assertTrue(updated.getUUID("id").equals(id)&&updated.getUUID("project").equals(id)&&updated.getInt("withdrawals")==1&&updated.getList("cargo",Tag.TAG_COMPOUND).equals(original.getList("cargo",Tag.TAG_COMPOUND))&&updated.getCompound("waitingProject").equals(waiting),"Original receipt counter, actual paid hay, identities and waiting home survive");h.assertTrue(!updated.getCompound("cost").contains("minecraft:hay_block")&&ConstructionFunding.missing(updated).get("minecraft:barrel")==10,"All ten barrels require normal fresh funding");h.assertTrue(!FarmStockProject.requote(t.l,t.e)&&stock.isEmpty(),"Repeat neither changes the quote nor puts unpaid materials in stock");
  for(var raw:updated.getList("ops",Tag.TAG_COMPOUND)){var op=(CompoundTag)raw;h.assertTrue(op.getString("item").equals("minecraft:barrel")&&op.getCompound("after").getCompound("Properties").getString("facing").equals("up"),"Storage has its correct physical upright state");}
  // Geometry-complete fixture tests the existing return transaction, not whole paid construction.
  updated.putInt("index",updated.getList("ops",Tag.TAG_COMPOUND).size());updated.putBoolean("funded",true);HallUpgradeGoal.store(t.l,t.s.id(),updated);var checkpoint=updated.copy();var npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.BUILDER,t.hall().id());npc.bind(t.s.id(),r);npc.setNoAi(true);var pos=LogisticsRoutes.position(t.e,t.hall());npc.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);
  try{var goal=new HallUpgradeGoal(npc,true);h.assertTrue(goal.canUse(),"Builder can return surplus");goal.start();goal.tick();h.assertTrue(stock.countItem(Items.HAY_BLOCK)==2,"Both previously paid bales return to real stock");HallUpgradeGoal.store(t.l,t.s.id(),checkpoint);goal=new HallUpgradeGoal(npc,true);h.assertTrue(goal.canUse(),"Return checkpoint resumes");goal.start();goal.tick();h.assertTrue(stock.countItem(Items.HAY_BLOCK)==2,"Replaying return does not duplicate grain bales");}finally{npc.discard();}
 }finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="farm_stock_quote",timeoutTicks=200)
 public static void startedOrPlayerFarmKeepsItsCommittedBill(GameTestHelper h){var t=ResearchV2Town.town(h,null);try{
  var original=legacy(t);HallUpgradeGoal.store(t.l,t.s.id(),original);t.s.appointPlayerMayor(UUID.randomUUID());h.assertTrue(!FarmStockProject.requote(t.l,t.e)&&HallUpgradeGoal.inspect(t.l,t.s.id()).equals(original),"Player's order is not replaced");t.s.appointNpcMayor();original.putBoolean("funded",true);HallUpgradeGoal.store(t.l,t.s.id(),original);h.assertTrue(!FarmStockProject.requote(t.l,t.e),"Fully funded work stays committed");original.putBoolean("funded",false);original.putInt("index",1);HallUpgradeGoal.store(t.l,t.s.id(),original);h.assertTrue(!FarmStockProject.requote(t.l,t.e),"Started placement stays committed");original.putInt("index",0);original.getList("ops",Tag.TAG_COMPOUND).getCompound(0).putBoolean("done",true);HallUpgradeGoal.store(t.l,t.s.id(),original);h.assertTrue(!FarmStockProject.requote(t.l,t.e),"A done operation cannot be changed even if index lags");
 }finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();}
}
