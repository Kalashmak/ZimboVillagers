package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** A funded final-operation fixture reproduces the roof failure's actual position;
 * physical funding of the whole warehouse is checked by TerminalBuilderGameTests. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WarehouseRoofGameTests {
 @GameTest(template="empty",batch="warehouse_roof",timeoutTicks=3600)
 public static void lastRoofSlabUsesNearbyLegalWorkPosition(GameTestHelper h){build(h,false);}
 @GameTest(template="empty",batch="warehouse_route",timeoutTicks=3600)
 public static void nearbyWorkStepDoesNotWaitForCachedRoute(GameTestHelper h){build(h,true);}
 private static void build(GameTestHelper h,boolean cachedRoute){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));
  var forced=PhysicalFixtureChunks.force(l,center,-4,65,-4,35);
  for(int x=-4;x<=65;x++)for(int z=-4;z<=35;z++){
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<=29;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
   l.setBlock(center.offset(x,30,z),Blocks.GLASS.defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var b=new Settlement.Building(UUID.randomUUID(),"warehouse",32,0,0,0,5);s.addBuilding(b);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var learned=BookResearch.inspect(l,e);var done=new ListTag();ResearchCatalog.NODES.keySet().forEach(id->done.add(StringTag.valueOf(id)));learned.put("legacyDone",done);BookResearch.store(l,e,learned);ResearchKnobs.forget(s.id());
  for(var cell:BuildingPlacement.layout(e,b,BuildingTiers.layoutId("warehouse",6)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  var target=BuildingPlacement.at(e,b,20,19,2);var after=l.getBlockState(target);h.assertTrue(after.is(Blocks.DEEPSLATE_TILE_SLAB),"Reproduce the terminal roof slab");l.setBlock(target,Blocks.AIR.defaultBlockState(),2);
  var op=new CompoundTag();op.putLong("pos",target.asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(after));op.putString("item","minecraft:deepslate_tile_slab");
  var ops=new ListTag();ops.add(op);var project=new CompoundTag();var id=UUID.randomUUID();project.putInt("schema",2);project.putString("kind","building");project.putUUID("id",id);project.putUUID("project",id);project.putUUID("building",b.id());project.putString("design","warehouse@6");project.putLong("origin",BuildingPlacement.origin(e,b).asLong());project.putLong("hatch",center.asLong());project.putBoolean("noHatch",true);project.put("ops",ops);project.putBoolean("funded",true);
  var cargo=new ListTag();cargo.add(new ItemStack(Blocks.DEEPSLATE_TILE_SLAB).save(new CompoundTag()));project.put("cargo",cargo);HallUpgradeGoal.enqueue(l,e,project);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),r);npc.moveTo(center.getX()+(cachedRoute?46.5:46.1),center.getY()+1,center.getZ()+3.5,0,0);
  var nearby=HallUpgradeGoal.stand(l,npc,target,new int[]{center.getY()+1},BuildingOrders.workReachSq(3));
  h.assertTrue(nearby!=null&&nearby.distToCenterSqr(npc.position())<4,"A legal nearby work position avoids a detour below the high roof: "+nearby);
  if(cachedRoute){
   // A formerly usable next node becomes blocked after construction changes the world.
   // The requested work stand still has a clear short approach in the opposite direction.
   var blocked=npc.blockPosition().south();l.setBlock(blocked,Blocks.STONE.defaultBlockState(),2);
   var nodes=new ArrayList<net.minecraft.world.level.pathfinder.Node>();nodes.add(new net.minecraft.world.level.pathfinder.Node(npc.blockPosition().getX(),npc.blockPosition().getY(),npc.blockPosition().getZ()));
   nodes.add(new net.minecraft.world.level.pathfinder.Node(blocked.getX(),blocked.getY(),blocked.getZ()));nodes.add(new net.minecraft.world.level.pathfinder.Node(nearby.getX(),nearby.getY(),nearby.getZ()));
   var path=new net.minecraft.world.level.pathfinder.Path(nodes,nearby,true);path.setNextNodeIndex(1);npc.getNavigation().moveTo(path,.8);
   h.assertTrue(!npc.getNavigation().isDone(),"The previous route is active");
   h.assertTrue(!HallUpgradeGoal.stepToWorkStand(l,npc,blocked),"A blocked step cannot bypass collision");
   h.assertTrue(!npc.getNavigation().isDone(),"A refused step preserves navigation");
   h.assertTrue(HallUpgradeGoal.stepToWorkStand(l,npc,nearby),"Clear nearby work step does not wait for the obsolete route");
   h.assertTrue(npc.getNavigation().isDone(),"Obsolete route is cancelled before movement");
  }
  if(!cachedRoute){
   h.assertTrue(HallUpgradeGoal.stepToWorkStand(l,npc,nearby),"Corner approach has a safe centering step");
   h.assertTrue(Math.abs(npc.getMoveControl().getWantedZ()-npc.blockPosition().getZ()-.5)<.001,"The first step centres beside the wall instead of clipping its corner");
  }
  var goal=new HallUpgradeGoal(npc,true);npc.onlyGoals(g->g instanceof FloatGoal||g instanceof ResidentDoorGoal||g instanceof DoorwayGoal||g instanceof SafeDescentGoal||g instanceof PitEscapeGoal,5,goal);l.addFreshEntity(npc);boolean[] ended={false};
  Runnable cleanup=()->{ended[0]=true;PhysicalFixtureChunks.release(l,forced);npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());ResearchKnobs.forget(s.id());};
  h.onEachTick(()->{if(ended[0]||!l.getBlockState(target).is(Blocks.DEEPSLATE_TILE_SLAB))return;var current=HallUpgradeGoal.inspect(l,s.id());h.assertTrue(current.getList("cargo",Tag.TAG_COMPOUND).stream().allMatch(t->ItemStack.of((CompoundTag)t).isEmpty()),"Funded slab consumed exactly once");cleanup.run();h.succeed();});
  h.runAtTickTime(3500,()->{if(ended[0])return;var why=npc.workStatus()+" at="+npc.position().subtract(center.getX(),center.getY(),center.getZ())+" op="+goal.opDiag+" stand="+goal.standDiag+" walk="+goal.walkDiag;cleanup.run();throw new GameTestAssertException("Final roof remains missing: "+why);});
 }
}
