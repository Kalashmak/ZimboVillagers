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
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmFieldTimberGameTests {
 @GameTest(template="empty",batch="farm_field_timber",timeoutTicks=200)
 public static void birchFarmFieldQuotesItsRealLocalWaterCover(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);try{
   var b=new Settlement.Building(UUID.randomUUID(),"farm",40,0,40,0,1,"birch");var at=BuildingPlacement.origin(t.e,b);
   for(int x=-3;x<=18;x++)for(int z=-3;z<=30;z++)for(int y=-1;y<=14;y++)t.l.setBlock(at.offset(x,y,z),y<=0?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
   var plan=FarmField.initial(t.l,t.e,b);h.assertTrue(plan.ok(),"Prepared clear soil accepts the real first field survey: "+plan.reason());
   h.assertTrue(plan.cost().getOrDefault("minecraft:birch_slab",0)==1&&!plan.cost().containsKey("minecraft:oak_slab"),"Birch farm pays one actual birch water cover, without an unrelated oak dependency: "+plan.cost());
   int covers=0;for(var raw:plan.ops()){var op=(CompoundTag)raw;if(!op.contains("item"))continue;h.assertTrue(op.getString("item").equals("minecraft:birch_slab")&&op.getCompound("after").getString("Name").equals("minecraft:birch_slab"),"Paid item and physical cover agree");covers++;}
   h.assertTrue(covers==1,"One water module has one paid cover");
   var full=BuildingOrders.survey(t.l,t.e,"farm",0,at,null,1,true,null,"birch");h.assertTrue(full.ok(),"The full real farmhouse survey includes its field: "+full.reason());
   h.assertTrue(full.state().getInt("fieldTimberRevision")==328&&!full.state().getCompound("cost").contains("minecraft:oak_slab"),"The complete quote propagates frozen timber into the field and marks the current version");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 private static CompoundTag oldCover(ResearchV2Town.Town t){
  var job=UUID.randomUUID();var state=new CompoundTag();state.putUUID("id",job);state.putUUID("project",job);state.putString("kind","building");state.putString("design","farm");state.putString("wood","birch");
  var op=new CompoundTag();op.putLong("pos",t.e.center().offset(40,1,40).asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(FarmField.COVER));op.putString("item",FarmField.COVER_ITEM);op.putBoolean("field",true);var ops=new ListTag();ops.add(op);state.put("ops",ops);var cost=new CompoundTag();cost.putInt(FarmField.COVER_ITEM,1);state.put("cost",cost);state.put("cargo",new ListTag());return state;
 }
 @GameTest(template="empty",batch="farm_field_timber",timeoutTicks=200)
 public static void oldUnstartedCoverKeepsPaidSpeciesAndReceiptIdentity(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);try{
   var original=oldCover(t);var job=original.getUUID("id");var stock=LogisticsRoutes.chest(t.l,t.e,t.hall());stock.clearContent();stock.setItem(0,new ItemStack(Items.OAK_SLAB));
   var paid=WorldJournal.takeAmount(t.l,Settlement.childId(job,"fund/0"),LogisticsRoutes.position(t.e,t.hall()),0,stock.getItem(0).copy(),1);original.getList("cargo",Tag.TAG_COMPOUND).add(paid.save(new CompoundTag()));original.putInt("withdrawals",1);
   var waiting=new CompoundTag();waiting.putUUID("id",UUID.randomUUID());original.put("waitingProject",waiting);HallUpgradeGoal.store(t.l,t.s.id(),original);
   h.assertTrue(FarmFieldTimber.requote(t.l,t.e),"An untouched field adopts the already frozen birch species");var updated=HallUpgradeGoal.inspect(t.l,t.s.id());
   h.assertTrue(updated.getUUID("id").equals(job)&&updated.getUUID("project").equals(job)&&updated.getInt("withdrawals")==1&&updated.getList("cargo",Tag.TAG_COMPOUND).equals(original.getList("cargo",Tag.TAG_COMPOUND))&&updated.getCompound("waitingProject").equals(waiting),"Actual oak cargo, withdrawal cursor, receipt identities and waiting project survive");
   h.assertTrue(updated.getList("ops",Tag.TAG_COMPOUND).getCompound(0).getCompound("before").equals(original.getList("ops",Tag.TAG_COMPOUND).getCompound(0).getCompound("before"))&&ConstructionFunding.missing(updated).getOrDefault("minecraft:birch_slab",0)==1&&!updated.getCompound("cost").contains("minecraft:oak_slab"),"Oak does not become birch for free; physical prior world stays unchanged");
   h.assertTrue(!FarmFieldTimber.requote(t.l,t.e)&&stock.isEmpty(),"Replay does not change inventory or mint slabs");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="farm_field_timber",timeoutTicks=200)
 public static void committedOrPlayerFieldCannotChangeItsCover(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);try{
   var original=oldCover(t);HallUpgradeGoal.store(t.l,t.s.id(),original);t.s.appointPlayerMayor(UUID.randomUUID());h.assertTrue(!FarmFieldTimber.requote(t.l,t.e),"Player bill remains chosen");t.s.appointNpcMayor();
   original.putBoolean("funded",true);HallUpgradeGoal.store(t.l,t.s.id(),original);h.assertTrue(!FarmFieldTimber.requote(t.l,t.e),"Funded project stays committed");
   original.putBoolean("funded",false);original.putInt("index",1);HallUpgradeGoal.store(t.l,t.s.id(),original);h.assertTrue(!FarmFieldTimber.requote(t.l,t.e),"Started operation stays committed");
   original.putInt("index",0);original.getList("ops",Tag.TAG_COMPOUND).getCompound(0).putBoolean("done",true);HallUpgradeGoal.store(t.l,t.s.id(),original);h.assertTrue(!FarmFieldTimber.requote(t.l,t.e),"Done operation refuses even with lagging index");
   original.getList("ops",Tag.TAG_COMPOUND).getCompound(0).remove("done");HallUpgradeGoal.store(t.l,t.s.id(),original);var op=original.getList("ops",Tag.TAG_COMPOUND).getCompound(0);var cell=BlockPos.of(op.getLong("pos"));t.l.setBlock(cell,Blocks.AIR.defaultBlockState(),2);h.assertTrue(WorldJournal.place(t.l,Settlement.childId(original.getUUID("id"),"block/0"),cell,Blocks.AIR.defaultBlockState(),FarmField.COVER),"A real committed block receipt exists");
   h.assertTrue(!FarmFieldTimber.requote(t.l,t.e)&&HallUpgradeGoal.inspect(t.l,t.s.id()).equals(original)&&t.l.getBlockState(cell).equals(FarmField.COVER),"Confirmed physical placement cannot be recolored even before done/index is saved");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
