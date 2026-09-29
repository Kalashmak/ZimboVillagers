package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Road upkeep (AD-038, AD-042, AD-077, AD-094): a maintenance round never holds the road slot when the hall lacks a piece, a broken corridor gate
 *  comes back as a gate, a wall cell something never leaves is passed over, and a level-five hall's machines start a new round after any finished project. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RoadUpkeepGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building hall,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  for(int x=-4;x<24;x++)for(int z=-4;z<18;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Town(l,s,e,hall,center);
 }
 private static void done(Town t,net.minecraft.world.entity.Entity... bodies){
  for(var b:bodies)if(b!=null)b.discard();SettlementData.get(t.l.getServer()).remove(t.s.id());
  try{java.nio.file.Files.deleteIfExists(Roads.projectPath(t.l,t.s.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 /** A builder of the hall, standing still where it is put. */
 private static ResidentEntity builder(Town t,BlockPos at){
  var home=new Settlement.Home(UUID.randomUUID(),1,1,true);t.s.addHome(home);var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,home.id());t.s.assign(r.id(),Profession.BUILDER,t.hall.id());
  var npc=VillageAstra.RESIDENT.get().create(t.l);npc.bind(t.s.id(),r);npc.setNoAi(true);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);t.l.addFreshEntity(npc);return npc;
 }
 private static CompoundTag op(String kind,BlockPos pos,Block after,Item item){
  var op=new CompoundTag();op.putString("kind",kind);op.putLong("pos",pos.asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
  op.put("after",NbtUtils.writeBlockState(after.defaultBlockState()));op.putString("item",BuiltInRegistries.ITEM.getKey(item).toString());return op;
 }
 /** A one-op project with its item already carried. */
 private static CompoundTag project(String kind,CompoundTag op,Item item){
  var ops=new ListTag();ops.add(op);var cargo=new ListTag();cargo.add(new ItemStack(item).save(new CompoundTag()));
  var p=new CompoundTag();p.putUUID("id",UUID.randomUUID());p.putString("kind",kind);p.put("ops",ops);p.putInt("index",0);p.put("cargo",cargo);p.put("returns",new ListTag());return p;
 }
 /** A corridor piece the settlement really put up: applied through Roads.apply, so it is remembered as built. */
 private static void put(GameTestHelper h,Town t,String kind,BlockPos pos,Block block,Item item){
  var p=project("build",op(kind,pos,block,item),item);
  h.assertTrue(Roads.apply(t.l,t.e,p).equals("done")&&Roads.apply(t.l,t.e,p).equals("complete")&&t.l.getBlockState(pos).is(block),"The "+kind+" is put up at "+pos.toShortString());
 }
 private static net.minecraft.world.level.block.state.BlockState after(Town t,CompoundTag op){return NbtUtils.readBlockState(t.l.holderLookup(net.minecraft.core.registries.Registries.BLOCK),op.getCompound("after"));}
 /** #12: a corridor gate remembered as a gate is put back as a gate, not as a plain fence that closes the passage. */
 @GameTest(template="empty",timeoutTicks=100) public static void aBrokenCorridorGateComesBackAsAGate(GameTestHelper h){
  var t=town(h);var fence=t.center.offset(4,1,14);var gate=t.center.offset(6,1,14);
  try{
   put(h,t,"fence",fence,Blocks.OAK_FENCE,Items.OAK_FENCE);put(h,t,"gate",gate,Blocks.OAK_FENCE_GATE,Items.OAK_FENCE_GATE);
   var fences=Roads.fences(t.l.getServer(),t.s.id());var gates=Roads.gates(t.l.getServer(),t.s.id());
   h.assertTrue(fences.length==2&&gates.length==1&&gates[0]==gate.asLong(),"Both are corridor fences and the gate is remembered as a gate: "+fences.length+"/"+gates.length);
   t.l.setBlock(fence,Blocks.AIR.defaultBlockState(),3);t.l.setBlock(gate,Blocks.AIR.defaultBlockState(),3);
   var round=Roads.maintenance(t.l,t.e);
   h.assertTrue(round!=null&&round.getBoolean("maintenance")&&round.getString("kind").equals("build"),"The broken pieces are a maintenance round");
   CompoundTag atGate=null,atFence=null;
   for(var raw:round.getList("ops",Tag.TAG_COMPOUND)){var o=(CompoundTag)raw;if(o.getLong("pos")==gate.asLong())atGate=o;if(o.getLong("pos")==fence.asLong())atFence=o;}
   h.assertTrue(atGate!=null&&atGate.getString("kind").equals("gate")&&atGate.getString("item").equals("minecraft:oak_fence_gate")&&after(t,atGate).is(Blocks.OAK_FENCE_GATE),"The gate is planned as a gate: "+atGate);
   h.assertTrue(atFence!=null&&atFence.getString("kind").equals("fence")&&after(t,atFence).is(Blocks.OAK_FENCE),"The fence stays a fence: "+atFence);
   h.assertTrue(round.getCompound("cost").getInt("minecraft:oak_fence_gate")==1&&round.getCompound("cost").getInt("minecraft:oak_fence")==1,"The round costs one gate and one fence: "+round.getCompound("cost"));
   // Carried out of the hall's own stock, the gate really stands again.
   h.assertTrue(Roads.order(t.l,t.e,round),"The round is ordered");
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall);chest.setItem(0,new ItemStack(Items.OAK_FENCE));chest.setItem(1,new ItemStack(Items.OAK_FENCE_GATE));
   h.assertTrue(Roads.load(t.l,t.e,t.hall,round)==2,"Both pieces are carried");
   for(int i=0;i<4&&!round.getBoolean("complete");i++)Roads.apply(t.l,t.e,round);
   h.assertTrue(round.getBoolean("complete")&&t.l.getBlockState(gate).is(Blocks.OAK_FENCE_GATE)&&t.l.getBlockState(fence).is(Blocks.OAK_FENCE),"The gate stands again as a gate: "+t.l.getBlockState(gate));
  }finally{done(t);}
  h.succeed();
 }
 /** #3: a maintenance round with a broken fence is a "build", yet a hall without fences never holds the builder: an empty round is dropped, a fence it
  *  cannot carry is left for the next round while the repair it can carry goes on; a player's road still waits for its material. */
 @GameTest(template="empty",timeoutTicks=100) public static void aMaintenanceRoundTheHallCannotSupplyNeverHoldsTheBuilder(GameTestHelper h){
  var t=town(h);var fence=t.center.offset(4,1,14);var worn=t.center.offset(3,0,12);ResidentEntity npc=null;
  try{
   put(h,t,"fence",fence,Blocks.OAK_FENCE,Items.OAK_FENCE);t.l.setBlock(fence,Blocks.AIR.defaultBlockState(),3);
   npc=builder(t,LogisticsRoutes.position(t.e,t.hall).south());var goal=new RoadWorkGoal(npc,true);
   var first=Roads.maintenance(t.l,t.e);
   h.assertTrue(first!=null&&first.getBoolean("maintenance")&&first.getString("kind").equals("build")&&Roads.order(t.l,t.e,first),"The broken fence is ordered as a maintenance round");
   goal.tick();
   h.assertTrue(!Roads.active(t.l,t.s.id())&&"road_missing_materials".equals(npc.workStatus()),"Without a fence in the hall the round is dropped, not held: "+npc.workStatus());
   t.l.setBlock(worn,Blocks.DIRT_PATH.defaultBlockState(),3);Roads.register(t.l,t.s.id(),worn);Roads.cell(t.l,worn).wear=Roads.REPAIR_WEAR+5;
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall);chest.setItem(0,new ItemStack(Items.DIRT,1));
   var second=Roads.maintenance(t.l,t.e);
   h.assertTrue(second!=null&&second.getList("ops",Tag.TAG_COMPOUND).size()==2&&second.getBoolean("maintenance")&&Roads.order(t.l,t.e,second),"The fence and the worn cell are one round");
   goal.tick();goal.tick();
   var now=Roads.project(t.l,t.s.id());
   h.assertTrue(Roads.active(t.l,t.s.id())&&now.getInt("index")==1&&chest.countItem(Items.DIRT)==0,"The fence the hall lacks is left and its dirt is carried: index="+now.getInt("index")+" dirt="+chest.countItem(Items.DIRT));
   h.assertTrue(Roads.apply(t.l,t.e,now).equals("done")&&Roads.cell(t.l,worn).wear==0&&Roads.apply(t.l,t.e,now).equals("complete"),"The repair goes on and the round completes");
   // A player's road is no upkeep: without its cobblestone it waits at the hall, whole.
   var strip=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();strip.put(t.center.offset(8,0,13),Blocks.DIRT_PATH.defaultBlockState());
   var road=Roads.plan(t.l,t.e,List.copyOf(strip.keySet()),strip,2,false);h.assertTrue(Roads.order(t.l,t.e,road),"The player's road is ordered");
   goal.tick();goal.tick();
   h.assertTrue(Roads.active(t.l,t.s.id())&&Roads.project(t.l,t.s.id()).getInt("index")==0,"The player's road waits for its material");
  }finally{done(t,npc);}
  h.succeed();
 }
 /** #13: a wall cell something never leaves is passed over after a while, as a changed cell is; a free moment starts the wait over. */
 @GameTest(template="empty",timeoutTicks=100) public static void aWallCellSomethingNeverLeavesIsPassedOver(GameTestHelper h){
  var t=town(h);var cell=t.center.offset(6,1,9);ResidentEntity npc=null;net.minecraft.world.entity.animal.Pig pig=null;
  try{
   Roads.save(t.l,t.s.id(),project("wall",op("wall",cell,Blocks.COBBLESTONE,Items.COBBLESTONE),Items.COBBLESTONE));
   pig=EntityType.PIG.create(t.l);pig.setNoAi(true);pig.moveTo(cell.getX()+.5,cell.getY(),cell.getZ()+.5,0,0);t.l.addFreshEntity(pig);
   npc=builder(t,cell.west(2));var goal=new RoadWorkGoal(npc,true);
   for(int i=0;i<RoadWorkGoal.WALL_WAIT/2;i++)goal.tick();
   h.assertTrue("wall_waiting".equals(npc.workStatus())&&Roads.project(t.l,t.s.id()).getInt("index")==0,"The builder waits for the cell: "+npc.workStatus());
   pig.moveTo(cell.getX()+.5,cell.getY(),cell.getZ()+4.5,0,0);goal.tick();pig.moveTo(cell.getX()+.5,cell.getY(),cell.getZ()+.5,0,0);
   for(int i=0;i<RoadWorkGoal.WALL_WAIT-1;i++)goal.tick();
   h.assertTrue("wall_waiting".equals(npc.workStatus())&&Roads.project(t.l,t.s.id()).getInt("index")==0,"A free moment starts the wait over: "+npc.workStatus());
   goal.tick();var now=Roads.project(t.l,t.s.id());
   h.assertTrue(now.getInt("index")==1&&now.getInt("conflicts")==1&&"road_changed".equals(npc.workStatus()),"After the wait the cell is passed over as a conflict: index="+now.getInt("index")+" "+npc.workStatus());
   h.assertTrue(t.l.getBlockState(cell).isAir(),"Nothing is laid on whoever stands there");
  }finally{done(t,npc,pig);}
  h.succeed();
 }
 /** #14: the machines of a level-five hall keep the roads after any project has finished, and a round the hall cannot supply is dropped, not held. */
 @GameTest(template="empty",timeoutTicks=100) public static void theMachinesStartANewRoundAfterAFinishedProject(GameTestHelper h){
  var t=town(h);var worn=t.center.offset(3,0,12);
  try{
   for(int i=2;i<=5;i++)t.s.raiseBuildingLevel(t.hall.id(),i);var hall=Workshops.hall(t.e);
   // The hall's equipment of levels II…V stands (AD-076), set without shape updates so nothing standing on air drops.
   for(var placed:LevelArchitecture.equipment("town_hall_3"))if(placed.level()<=5)
    t.l.setBlock(BuildingPlacement.at(t.e,hall,placed.local().getX(),placed.local().getY(),placed.local().getZ()),BuildingPlacement.state(placed.state(),hall.rotation()),18);
   h.assertTrue(BuildingLevels.level(t.l,t.e,hall)==5,"The hall works at level five: "+BuildingLevels.level(t.l,t.e,hall));
   var research=BookResearch.inspect(t.l,t.e);var learned=research.getList("legacyDone",Tag.TAG_STRING);learned.add(StringTag.valueOf(Machines.ROAD_REPAIR));research.put("legacyDone",learned);BookResearch.store(t.l,t.e,research);
   t.l.setBlock(worn,Blocks.DIRT_PATH.defaultBlockState(),3);Roads.register(t.l,t.s.id(),worn);Roads.cell(t.l,worn).wear=Roads.REPAIR_WEAR+5;
   var finished=Roads.clearing(t.l,t.e,List.of());Roads.save(t.l,t.s.id(),finished);
   h.assertTrue(Roads.project(t.l,t.s.id())!=null&&!Roads.active(t.l,t.s.id()),"A finished project's file is left");
   Machines.tick(t.l,t.e,40,List.of());var dropped=Roads.project(t.l,t.s.id());
   h.assertTrue(dropped.getString("kind").equals("repair")&&!dropped.getUUID("id").equals(finished.getUUID("id"))&&!Roads.active(t.l,t.s.id()),"A new round is planned and, with no dirt in the hall, dropped at once: "+dropped.getString("kind"));
   var chest=LogisticsRoutes.chest(t.l,t.e,hall);chest.setItem(0,new ItemStack(Items.DIRT,1));
   Machines.tick(t.l,t.e,80,List.of());
   h.assertTrue(Roads.active(t.l,t.s.id())&&chest.countItem(Items.DIRT)==0,"The next round carries the hall's dirt");
   h.assertTrue(Machines.tick(t.l,t.e,120,List.of())>0&&Roads.cell(t.l,worn).wear==0,"The machines repair the worn cell: wear="+Roads.cell(t.l,worn).wear);
   Machines.tick(t.l,t.e,160,List.of());
   h.assertTrue(!Roads.active(t.l,t.s.id()),"The round completes and the slot is free for the player's orders");
  }finally{done(t);}
  h.succeed();
 }
}
