package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SurfaceQuarryGameTests {
 @GameTest(template="empty",batch="surface_quarry_access",timeoutTicks=200)
 public static void harvestRequiresStableReturnableStand(GameTestHelper h){
  var l=h.getLevel();var target=h.absolutePos(new BlockPos(5,3,5));
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
   l.setBlock(target.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(target.offset(x,1,z),Blocks.AIR.defaultBlockState(),2);l.setBlock(target.offset(x,2,z),Blocks.AIR.defaultBlockState(),2);
  }
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(target.getX()+2.5,target.getY()+1,target.getZ()+.5);npc.setOnGround(true);l.addFreshEntity(npc);
  try{
   var stand=HarvestAccess.find(npc,target);
   h.assertTrue(stand!=null&&!stand.below().equals(target),"Navigator finds a dry side stand whose floor is not mined");
   l.setBlock(target,Blocks.AIR.defaultBlockState(),3);
   h.assertTrue(HarvestAccess.standing(l,stand,target),"Extracting the block preserves the working platform");
   var drop=new net.minecraft.world.level.pathfinder.Path(List.of(new net.minecraft.world.level.pathfinder.Node(0,4,0),new net.minecraft.world.level.pathfinder.Node(1,1,0)),BlockPos.ZERO,true);
   h.assertTrue(!HarvestAccess.reversible(drop),"A reachable three-block drop is not a return route");
   var stair=new net.minecraft.world.level.pathfinder.Path(List.of(new net.minecraft.world.level.pathfinder.Node(0,2,0),new net.minecraft.world.level.pathfinder.Node(1,1,0),new net.minecraft.world.level.pathfinder.Node(2,0,0)),BlockPos.ZERO,true);
   h.assertTrue(HarvestAccess.reversible(stair),"Ordinary one-block steps remain usable");
  }finally{npc.discard();}h.succeed();
 }
 @GameTest(template="empty",batch="surface_quarry_crops",timeoutTicks=200)
 public static void gatherersKeepCaneAndCactusGrowingBases(GameTestHelper h){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(2,3,2));
  for(var plant:List.of(Blocks.SUGAR_CANE,Blocks.CACTUS)){
   l.setBlock(base.below(),Blocks.SAND.defaultBlockState(),2);l.setBlock(base.below().east(),Blocks.WATER.defaultBlockState(),2);
   for(int y=0;y<3;y++)l.setBlock(base.above(y),plant.defaultBlockState(),2);
   h.assertTrue(!NaturalSupplyGoal.safe(l,base)&&!NaturalSupplyGoal.safe(l,base.above())&&NaturalSupplyGoal.safe(l,base.above(2)),"Only the growing column's top is harvested");
   var loot=WorldJournal.harvest(l,UUID.randomUUID(),base.above(2),plant.defaultBlockState(),ItemStack.EMPTY);
   h.assertTrue(loot!=null&&loot.size()==1&&l.getBlockState(base).is(plant)&&l.getBlockState(base.above()).is(plant),"Real top loot leaves its source alive");
   for(int y=0;y<3;y++)l.setBlock(base.above(y),Blocks.AIR.defaultBlockState(),2);
  }h.succeed();
 }
 @GameTest(template="empty",batch="surface_quarry",timeoutTicks=800)
 public static void exhaustedMineRequestsPickAndFindsNeededSurfaceAndesite(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);var l=t.l;var e=t.e;var s=t.s;var hall=Workshops.hall(e);var chest=LogisticsRoutes.chest(l,e,hall);chest.clearContent();
  var mine=new Settlement.Building(UUID.randomUUID(),"mine",20,0,0);s.addBuilding(mine);var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,Settlement.childId(s.id(),"home"));s.assign(r.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(r.id()));npc.setNoAi(true);
   var work=MineWork.read(l,mine);work.putString("status","mine_floor");work.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));
   int floor=MineWork.floorStep(l,e,mine,work);work.putInt("floorStep",floor);work.putInt("step",floor+1);work.putInt("side",MineDrive.DONE);work.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,floor).toArray());s.noteMine(mine.id(),floor,3,5,work.getInt("descent"));MineWork.write(l,mine,work);
   h.assertTrue(MineWork.next(l,e,mine,work.copy()).floor(),"The fixture has actually exhausted its saved drive, not only set a status string");
   var column=e.center().offset(80,0,0);l.getChunkAt(column);var target=new BlockPos(column.getX(),Math.max(BuildingPlacement.origin(e,mine).getY(),l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ())),column.getZ());for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=3;y++)l.setBlock(target.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);l.setBlock(target.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(target,Blocks.ANDESITE.defaultBlockState(),2);
   npc.moveTo(target.getX()+1.5,target.getY()+1,target.getZ()+.5);npc.setOnGround(true);
   h.assertTrue(SurfaceQuarry.mayStart(l,e,npc),"An exhausted mine permits a surface trip after its miner is above ground");
   h.assertTrue(!NaturalSupplyGoal.miningPriority(l,e,npc),"Every available floor was surveyed; no outstanding drive overrides surface supply");
   h.assertTrue(WorkerSupplies.wants(l,e,mine.id()).stream().anyMatch(w->w.matches(new ItemStack(Items.STONE_PICKAXE))),"A borrowed quarry tool is requested separately from the pick still held by the mine");chest.setItem(0,new ItemStack(Items.STONE_PICKAXE));
   var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:polished_andesite",3);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);
   var goal=new NaturalSupplyGoal(npc,true);h.startSequence().thenWaitUntil(()->{npc.tickCount+=100;h.assertTrue(goal.canUse(),"Surveying needed surface rock within the load budget: pos="+npc.position()+" primary="+NaturalSupplyGoal.primaryResourcePending(l,e,npc)+" priority="+NaturalSupplyGoal.miningPriority(l,e,npc)+" surface="+SurfaceQuarry.mayStart(l,e,npc)+" demand="+NaturalSupplyGoal.demand(l,e)+" mine="+MineWork.read(l,mine)+" survey="+NaturalSupplyGoal.inspect(l,npc.getUUID()));}).thenExecute(()->{
   var trip=NaturalSupplyGoal.inspect(l,npc.getUUID());var chosen=BlockPos.of(trip.getLong("target"));
   h.assertTrue(trip.getBoolean("quarry")&&l.getBlockState(chosen).is(Blocks.ANDESITE)&&SurfaceQuarry.safe(l,chosen)&&HarvestAccess.find(npc,chosen)!=null,"Demand-driven survey chooses reachable, safe real andesite, including natural terrain closer than the fixture");
   h.assertTrue(net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),trip.getCompound("before")).equals(l.getBlockState(chosen)),"Persisted quarry receipt records the selected real block before extraction");
   npc.moveTo(target.getX()+1.5,BuildingPlacement.origin(e,mine).getY()-3,target.getZ()+.5);h.assertTrue(!SurfaceQuarry.mayStart(l,e,npc),"An underground miner first returns to the surface");
   npc.discard();ResearchV2Town.done(t);}).thenSucceed();
 }
 @GameTest(template="empty",batch="surface_quarry",timeoutTicks=200)
 public static void drySurfaceRockUsesRealPickAndReturnsWornTool(GameTestHelper h){quarry(h,false);}
 @GameTest(template="empty",batch="mine_stairs",timeoutTicks=200)
 public static void quarryRepairsOwnStairsBeforeReturningBorrowedTool(GameTestHelper h){quarry(h,true);}
 private static void quarry(GameTestHelper h,boolean stairs){
  var t=ResearchV2Town.town(h,null);var l=t.l;var e=t.e;var s=t.s;var hall=Workshops.hall(e);var chest=LogisticsRoutes.chest(l,e,hall);chest.clearContent();
  var mine=new Settlement.Building(UUID.randomUUID(),"mine",20,0,0);s.addBuilding(mine);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,Settlement.childId(s.id(),"home"));s.assign(r.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(r.id()));npc.setNoAi(true);l.addFreshEntity(npc);
  var target=e.center().offset(80,0,0);var stock=LogisticsRoutes.position(e,hall);
  try{
   if(stairs){
    var order=MineWork.read(l,mine);order.putString("stage","stair");order.putString("status","missing_stair_stone");order.putString("stairItem","minecraft:cobblestone");order.putInt("descent",0);order.putInt("stairStep",0);
    for(var cell:MineDrive.stairs(0,MineWork.shape(order)))l.setBlock(MineWork.at(e,mine,cell),Blocks.AIR.defaultBlockState(),2);MineWork.write(l,mine,order);
    l.setBlock(LogisticsRoutes.position(e,mine),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
   }
   l.setBlock(target.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(target,Blocks.STONE.defaultBlockState(),2);l.setBlock(target.above(),Blocks.AIR.defaultBlockState(),2);l.setBlock(target.above(2),Blocks.AIR.defaultBlockState(),2);
   for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)l.setBlock(target.relative(d),Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(SurfaceQuarry.safe(l,target),"Dry exposed rock outside village lots is safe");
   l.setBlock(target.east(),Blocks.WATER.defaultBlockState(),2);h.assertTrue(!SurfaceQuarry.safe(l,target),"Water beside the face still forbids digging");l.setBlock(target.east(),Blocks.AIR.defaultBlockState(),2);
   chest.setItem(0,new ItemStack(Items.WOODEN_PICKAXE));h.assertTrue(SurfaceQuarry.tool(l,e,Blocks.GOLD_ORE.defaultBlockState())<0,"A wooden tool cannot extract gold");
   var state=new CompoundTag();state.putUUID("id",UUID.randomUUID());state.putLong("target",target.asLong());state.put("before",NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));state.putBoolean("quarry",true);state.putLong("stand",target.east().above().asLong());l.setBlock(target.east(),Blocks.STONE.defaultBlockState(),2);l.setBlock(target.east().above(),Blocks.AIR.defaultBlockState(),2);l.setBlock(target.east().above(2),Blocks.AIR.defaultBlockState(),2);state.putString("stage","tool");NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),state);
   var goal=new NaturalSupplyGoal(npc,true);npc.moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);npc.tickCount=100;h.assertTrue(goal.canUse(),"Saved quarry trip resumes");goal.start();goal.tick();
   h.assertTrue(chest.countItem(Items.WOODEN_PICKAXE)==0&&NaturalSupplyGoal.cargo(l,NaturalSupplyGoal.inspect(l,npc.getUUID())).size()==1,"Pick is in worker custody, not duplicated in the chest");
   npc.moveTo(target.getX()+.5,target.getY()+1,target.getZ()+.5);npc.tickCount+=20;goal.tick();
   h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getInt("labor")==0,"Standing on the block to be removed is not arrival at the side platform");
   npc.moveTo(target.getX()+1.5,target.getY()+1,target.getZ()+.5);
   for(int i=0;i<10;i++){npc.tickCount+=20;goal.tick();}
   var after=NaturalSupplyGoal.inspect(l,npc.getUUID());var cargo=NaturalSupplyGoal.cargo(l,after);
   h.assertTrue(l.getBlockState(target).isAir()&&cargo.stream().map(x->ItemStack.of((CompoundTag)x)).anyMatch(x->x.is(Items.COBBLESTONE)),"The actual rock disappeared and yielded stone in transit");
   h.assertTrue(cargo.stream().map(x->ItemStack.of((CompoundTag)x)).anyMatch(x->x.is(Items.WOODEN_PICKAXE)&&x.getDamageValue()==1),"One real tool durability point was spent");
   // Simulate interruption after harvest committed but before carry state saved.
   state.putString("stage","dig");NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),state);var recovered=NaturalSupplyGoal.cargo(l,state);
   h.assertTrue(recovered.equals(cargo),"Journal recovery reconstructs the same loot and worn tool");NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),after);
   if(stairs){var mineStock=LogisticsRoutes.position(e,mine);npc.moveTo(mineStock.getX()+1.5,mineStock.getY(),mineStock.getZ()+.5);npc.tickCount+=20;goal.tick();
    h.assertTrue(LogisticsRoutes.chest(l,e,mine).countItem(Items.COBBLESTONE)==1&&chest.countItem(Items.COBBLESTONE)==0,"Fresh rock reaches the mine without consuming the hall reserve");
    goal=new NaturalSupplyGoal(npc,true);npc.tickCount+=100;h.assertTrue(goal.canUse(),"Interrupted return of the borrowed tool resumes");goal.start();
   }
   npc.moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);for(int i=0;i<3;i++){npc.tickCount+=20;goal.tick();}
   h.assertTrue(chest.countItem(Items.COBBLESTONE)==(stairs?0:1)&&chest.countItem(Items.WOODEN_PICKAXE)==1&&!NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,npc.getUUID())),"Physical return deposits exactly one stone and one pick");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
}
