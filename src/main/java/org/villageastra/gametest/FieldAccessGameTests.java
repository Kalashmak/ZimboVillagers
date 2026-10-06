package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Prepared physical regressions for the two observed unfinished farm operations. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FieldAccessGameTests {
 @GameTest(template="empty",batch="field_access",timeoutTicks=2400)
 public static void builderBackfillsOwnFlowingIrrigation(GameTestHelper h){run(h,false);}
 @GameTest(template="empty",batch="field_access",timeoutTicks=2400)
 public static void builderReusesReturnedScaffoldsForCanopy(GameTestHelper h){run(h,true);}
 @GameTest(template="empty",batch="field_access",timeoutTicks=100)
 public static void waterReconciliationRequiresOwnCommittedSource(GameTestHelper h){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(4,8,4));var id=UUID.randomUUID();var project=project(base,id);var source=op(base,Blocks.AIR.defaultBlockState(),Blocks.WATER.defaultBlockState());source.putBoolean("field",true);source.putBoolean("done",true);
  var fill=op(base.east(),Blocks.AIR.defaultBlockState(),Blocks.DIRT.defaultBlockState());fill.putBoolean("field",true);var ops=new ListTag();ops.add(source);ops.add(fill);project.put("ops",ops);
  l.setBlock(base,Blocks.WATER.defaultBlockState(),2);l.setBlock(base.east(),Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1),2);
  h.assertTrue(!FieldWaterDrift.reconcile(l,project,fill),"Uncommitted natural source is refused");
  l.setBlock(base,Blocks.AIR.defaultBlockState(),2);h.assertTrue(WorldJournal.place(l,Settlement.childId(id,"block/0"),base,Blocks.AIR.defaultBlockState(),Blocks.WATER.defaultBlockState()),"Committed project source");
  h.assertTrue(FieldWaterDrift.reconcile(l,project,fill),"Own connected flow accepted");
  l.setBlock(base.east(),Blocks.WATER.defaultBlockState(),2);h.assertTrue(!FieldWaterDrift.reconcile(l,project,fill),"Source water itself refused");
  l.setBlock(base.east(),Blocks.LAVA.defaultBlockState(),2);h.assertTrue(!FieldWaterDrift.reconcile(l,project,fill),"Lava refused");
  l.setBlock(base.east(),Blocks.AIR.defaultBlockState(),2);h.assertTrue(FieldWaterDrift.reconcile(l,project,fill)&&HallConstructionPlan.step(fill).before().isAir(),"Drained own flow returns to surveyed air");
  project.putString("design","home");h.assertTrue(!FieldWaterDrift.reconcile(l,project,fill),"Ordinary building refused");h.succeed();
 }
 private static void run(GameTestHelper h,boolean canopy){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+64000,120,at.getZ());var forced=PhysicalFixtureChunks.force(l,base,-6,20,-6,20);
  for(int x=-6;x<=20;x++)for(int z=-6;z<=20;z++)for(int y=-1;y<=12;y++)l.setBlock(base.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",-100,0,-100);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var target=base.offset(8,canopy?8:0,8);var id=UUID.randomUUID();var state=project(base,id);var ops=new ListTag();var cargo=new ListTag();
  if(canopy){
   // Three by three tall wall prevents every ground stand within unchanged six-block reach.
   for(int x=7;x<=9;x++)for(int z=7;z<=9;z++)for(int y=1;y<=7;y++)l.setBlock(base.offset(x,y,z),Blocks.COBBLESTONE.defaultBlockState(),2);
   l.setBlock(target,Blocks.BIRCH_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true),2);
   ops.add(op(target,l.getBlockState(target),Blocks.AIR.defaultBlockState()));cargo.add(new ItemStack(VillageAstra.TIMBER_SCAFFOLD.get(),8).save(new CompoundTag()));
  }else{
   var water=base.offset(10,0,8);l.setBlock(water,Blocks.AIR.defaultBlockState(),2);l.setBlock(target,Blocks.AIR.defaultBlockState(),2);l.setBlock(target.east(),Blocks.AIR.defaultBlockState(),2);
   var source=op(water,Blocks.AIR.defaultBlockState(),Blocks.WATER.defaultBlockState());source.putBoolean("field",true);source.putBoolean("done",true);ops.add(source);
   h.assertTrue(WorldJournal.place(l,Settlement.childId(id,"block/0"),water,Blocks.AIR.defaultBlockState(),Blocks.WATER.defaultBlockState()),"Paid project's irrigation source placed");
   var fill=op(target,Blocks.AIR.defaultBlockState(),Blocks.DIRT.defaultBlockState());fill.putBoolean("field",true);ops.add(fill);
   state.putInt("progress",1);state.putInt("index",1);
  }
  state.put("ops",ops);state.put("cargo",cargo);HallUpgradeGoal.store(l,s.id(),state);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),r);npc.moveTo(base.getX()+2.5,base.getY()+1,base.getZ()+2.5,0,0);npc.onlyGoals(g->false,5,new HallUpgradeGoal(npc,true));
  boolean[] started={false},ended={false},flowed={false},clearedInReach={false};Runnable cleanup=()->{ended[0]=true;npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,forced);};
  h.onEachTick(()->{
   if(ended[0])return;if(!started[0]){if(!canopy&&!l.getBlockState(target).is(Blocks.WATER))return;flowed[0]=!canopy;started[0]=l.addFreshEntity(npc);return;}
   if(canopy?!l.getBlockState(target).isAir():!l.getBlockState(target).is(Blocks.DIRT))return;
   if(canopy&&!clearedInReach[0]){h.assertTrue(npc.getEyePosition().distanceToSqr(target.getCenter())<=BuildingOrders.REACH_SQ,"Canopy cleared within unchanged physical reach");clearedInReach[0]=true;}
   var actual=HallUpgradeGoal.inspect(l,s.id());var current=actual.getList("ops",Tag.TAG_COMPOUND);
   if(canopy&&current.stream().anyMatch(raw->!((CompoundTag)raw).getBoolean("done")))return;
   if(!canopy)h.assertTrue(npc.getEyePosition().distanceToSqr(target.getCenter())<=BuildingOrders.REACH_SQ,"Physical builder retains reach limit");
   h.assertTrue(WorldJournal.inspectCommitted(l,Settlement.childId(id,"block/"+(canopy?0:1)))!=null,"Actual work has committed receipt");
   if(canopy){int count=0;for(var raw:actual.getList("cargo",Tag.TAG_COMPOUND)){var item=ItemStack.of((CompoundTag)raw);if(item.is(VillageAstra.TIMBER_SCAFFOLD.get().asItem()))count+=item.getCount();}
    h.assertTrue(count==8,"All eight finite returned scaffolds conserved");h.assertTrue(current.size()>1,"Temporary access was actually appended");
    for(var raw:current){var step=HallConstructionPlan.step((CompoundTag)raw);if(step.before().is(VillageAstra.TIMBER_SCAFFOLD.get()))h.assertTrue(!l.getBlockState(step.pos()).is(VillageAstra.TIMBER_SCAFFOLD.get()),"Temporary column physically removed");}
   }else h.assertTrue(flowed[0],"Vanilla source actually flowed before physical backfill");
   cleanup.run();h.succeed();
  });
  h.runAtTickTime(2300,()->{if(ended[0])return;var detail=npc.position()+" "+npc.workStatus()+" "+HallUpgradeGoal.inspect(l,s.id());cleanup.run();throw new GameTestAssertException("Physical farm access stalled: "+detail);});
 }
 private static CompoundTag project(BlockPos base,UUID id){var t=new CompoundTag();t.putInt("schema",2);t.putUUID("id",id);t.putUUID("project",id);t.putString("kind","building");t.putString("design","farm");t.putLong("origin",base.asLong());t.putLong("hatch",base.asLong());t.putBoolean("noHatch",true);t.putBoolean("funded",true);t.put("cost",new CompoundTag());return t;}
 private static CompoundTag op(BlockPos p,net.minecraft.world.level.block.state.BlockState before,net.minecraft.world.level.block.state.BlockState after){var t=new CompoundTag();t.putLong("pos",p.asLong());t.put("before",NbtUtils.writeBlockState(before));t.put("after",NbtUtils.writeBlockState(after));return t;}
}
