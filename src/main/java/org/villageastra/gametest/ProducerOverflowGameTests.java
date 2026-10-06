package org.villageastra.gametest;
import java.util.*;
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
public final class ProducerOverflowGameTests {
 @GameTest(template="empty",batch="surplus_food_space",timeoutTicks=100)
 public static void genericBulkLeavesRealFoodSlotsWhileHarvestStillArrives(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");try{
   var source=LogisticsRoutes.chest(t.l,t.e,t.shop);var dest=LogisticsRoutes.chest(t.l,t.e,t.hall());source.clearContent();dest.clearContent();
   for(int i=0;i<source.getContainerSize();i++)source.setItem(i,new ItemStack(Items.COBBLESTONE,64));
   for(int i=0;i<dest.getContainerSize()-11;i++)dest.setItem(i,new ItemStack(Items.DIRT,64));
   h.assertTrue(LogisticsRoutes.choose(t.l,t.e,t.hall())==null,"Unrequested mining surplus leaves eleven real slots for food and maintenance");
   source.setItem(0,new ItemStack(Items.WHEAT,16));var route=LogisticsRoutes.choose(t.l,t.e,t.hall());h.assertTrue(route!=null&&route.item().is(Items.WHEAT),"Real grain remains deliverable despite the bulk limit");
   h.assertTrue(dest.countItem(Items.DIRT)==(dest.getContainerSize()-11)*64,"Planning deletes nothing and invents no storage");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="producer_overflow",timeoutTicks=200)
 public static void fullMineDeliversRealHarvestAfterPorterExportsPastOneStack(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");ResidentEntity npc=null;
  try{
   var source=LogisticsRoutes.chest(t.l,t.e,t.shop);var dest=LogisticsRoutes.chest(t.l,t.e,t.hall());source.clearContent();dest.clearContent();
   for(int slot=0;slot<source.getContainerSize();slot++)source.setItem(slot,new ItemStack(Items.COBBLESTONE,64));dest.setItem(0,new ItemStack(Items.COBBLESTONE,64));
   var route=LogisticsRoutes.choose(t.l,t.e,t.hall());h.assertTrue(route!=null&&route.source().equals(t.shop)&&route.destination().equals(t.hall())&&route.item().is(Items.COBBLESTONE),"A full mine exports surplus despite the hall's existing stack");
   int initial=source.countItem(Items.COBBLESTONE);var transfer=UUID.randomUUID();var taken=WorldJournal.takeAmount(t.l,Settlement.childId(transfer,"take"),Workshops.station(t.e,t.shop),0,source.getItem(0).copy(),route.item().getCount());
   h.assertTrue(WorldJournal.deposit(t.l,Settlement.childId(transfer,"deposit"),Workshops.station(t.e,t.hall()),taken),"The selected real parcel physically reaches shared stock");
   var target=t.e.center().offset(25,2,25);var before=Blocks.STONE.defaultBlockState();t.l.setBlock(target,before,2);var operation=UUID.randomUUID();WorldJournal.harvest(t.l,operation,target,before,new ItemStack(Items.STONE_PICKAXE));var loot=WorldJournal.recoverExisting(t.l,operation).getList("loot",Tag.TAG_COMPOUND).copy();
   h.assertTrue(loot.size()==1&&ItemStack.of(loot.getCompound(0)).is(Items.COBBLESTONE)&&ItemStack.of(loot.getCompound(0)).getCount()==1&&t.l.getBlockState(target).isAir(),"One actual mined stone funds the waiting cargo");
   npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),r);npc.setNoAi(true);
   var state=new CompoundTag();state.putInt("schema",1);state.putInt("width",3);state.putInt("height",5);state.putInt("descent",7);state.putInt("step",1);state.putUUID("worker",r.id());state.putUUID("operation",operation);state.putString("stage","deliver");state.put("cargo",loot);state.putBoolean("advanced",true);state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));MineWork.write(t.l,t.shop,state);
   var output=Workshops.station(t.e,t.shop);npc.moveTo(output.getX()+1.5,output.getY(),output.getZ()+.5);var goal=new ResourceWorkGoal(npc,true,()->6000);h.assertTrue(goal.canUse(),"The actual saved miner delivery resumes");goal.start();goal.tick();
   h.assertTrue(source.countItem(Items.COBBLESTONE)==initial-taken.getCount()+1&&!MineWork.read(t.l,t.shop).contains("cargo"),"Freed space allows real miner cargo delivery and subsequent work");
   MineWork.write(t.l,t.shop,state);goal=new ResourceWorkGoal(npc,true,()->6000);h.assertTrue(goal.canUse(),"Old delivery checkpoint resumes");goal.start();goal.tick();
   h.assertTrue(source.countItem(Items.COBBLESTONE)==initial-taken.getCount()+1&&dest.countItem(Items.COBBLESTONE)==64+taken.getCount(),"Export plus replay conserves every item without duplicating the harvest");
  }finally{if(npc!=null)npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="producer_overflow_full_stock",timeoutTicks=200)
 public static void fullSharedStockLeavesMineSurplusInItsRealChest(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");
  try{
   var source=LogisticsRoutes.chest(t.l,t.e,t.shop);var dest=LogisticsRoutes.chest(t.l,t.e,t.hall());source.clearContent();dest.clearContent();
   for(int slot=0;slot<source.getContainerSize();slot++)source.setItem(slot,new ItemStack(Items.COBBLESTONE,64));for(int slot=0;slot<dest.getContainerSize();slot++)dest.setItem(slot,new ItemStack(Items.DIAMOND,64));
   h.assertTrue(LogisticsRoutes.choose(t.l,t.e,t.hall())==null,"Overflow never promises a destination without physical room");
   h.assertTrue(source.countItem(Items.COBBLESTONE)==source.getContainerSize()*64&&dest.countItem(Items.DIAMOND)==dest.getContainerSize()*64,"Planning discards no surplus and creates no space");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
