package org.villageastra.gametest;
import java.util.*;
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
public final class FarmSeedReturnGameTests {
 @GameTest(template="empty",batch="farm_seed_return",timeoutTicks=200)
 public static void trampledLastPlotReturnsPaidSeedAndCheckpointCannotDuplicateIt(GameTestHelper h){check(h,false);}
 @GameTest(template="empty",batch="farm_seed_return",timeoutTicks=200)
 public static void fullFarmChestRetainsBorrowedSeedUntilSpaceReturns(GameTestHelper h){check(h,true);}
 private static void check(GameTestHelper h,boolean full){
  var t=ResearchV2Town.town(h,"farm");var npc=VillageAstra.RESIDENT.get().create(t.l);
  var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.FARMER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);t.l.addFreshEntity(npc);
  h.runAfterDelay(12,()->{try{
   var plots=FarmWorkArea.cells(t.l,t.e,npc.getUUID());h.assertTrue(!plots.isEmpty(),"Actual registered field exists");
   for(var p:plots){t.l.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),2);t.l.setBlock(p,Blocks.WHEAT.defaultBlockState(),2);}
   var target=plots.get(0);t.l.setBlock(target,Blocks.AIR.defaultBlockState(),2);t.l.setBlock(target.below(),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   var pos=LogisticsRoutes.position(t.e,t.shop);var chest=LogisticsRoutes.chest(t.l,t.e,t.shop);chest.clearContent();chest.setItem(0,new ItemStack(Items.WHEAT_SEEDS));var id=UUID.randomUUID();
   h.assertTrue(WorldJournal.takeAmount(t.l,id,pos,0,chest.getItem(0).copy(),1).getCount()==1,"Seed really leaves the farm chest");
   if(full)for(int slot=0;slot<chest.getContainerSize();slot++)chest.setItem(slot,new ItemStack(Items.DIRT,64));
   var state=new CompoundTag();state.putInt("schema",1);state.putInt("width",1);state.putInt("height",3);state.putUUID("worker",npc.getUUID());state.putUUID("operation",id);state.putString("stage","plant");state.putString("crop","wheat");state.putLong("target",target.asLong());state.putLong("plantSource",pos.asLong());state.put("tool",new ItemStack(Items.STONE_HOE).save(new CompoundTag()));MineWork.write(t.l,t.shop,state);
   npc.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);npc.setOnGround(true);
   var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Pending sowing reloads");goal.tick();
   if(full){
    var held=MineWork.read(t.l,t.shop);h.assertTrue(held.getString("stage").equals("plant")&&held.getUUID("operation").equals(id),"Full storage retains original sowing operation");
    h.assertTrue(ForestFixture.count(JobCargo.snapshot(npc,true).items(),Items.WHEAT_SEEDS)==1&&!WorldJournal.exists(t.l,Settlement.childId(id,"plant_return")),"Unreturned seed remains in custody without a failed return intent");
    chest.setItem(0,ItemStack.EMPTY);goal.tick();
   }
   var after=MineWork.read(t.l,t.shop);h.assertTrue(after.getString("stage").equals("choose")&&chest.countItem(Items.WHEAT_SEEDS)==1,"Invalid last plot returns its paid seed and releases farmer");
   h.assertTrue(WorldJournal.inspectCommitted(t.l,Settlement.childId(id,"plant_return"))!=null,"Return has its own committed receipt");
   h.assertTrue(t.l.getBlockState(target).isAir()&&t.l.getBlockState(target.below()).is(Blocks.GRASS_BLOCK),"Return does not create soil or crop");
   MineWork.write(t.l,t.shop,state);var snap=JobCargo.snapshot(npc,true);h.assertTrue(ForestFixture.count(snap.items(),Items.WHEAT_SEEDS)==0,"Checkpoint lag cannot reclaim the already returned seed");
   goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Stale sowing checkpoint reloads");goal.tick();h.assertTrue(chest.countItem(Items.WHEAT_SEEDS)==1&&MineWork.read(t.l,t.shop).getString("stage").equals("choose"),"Receipt replay returns exactly once and resumes choosing");
   h.assertTrue(ItemStack.of(MineWork.read(t.l,t.shop).getCompound("tool")).getDamageValue()==0,"Refund does not pretend to till or wear the hoe");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();});
 }
}
