package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-057: a clearing ordered on the map — builders take soil and wood, miners take stone and ore, nothing is dug under the settlement's floor. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MapOrderGameTests {
 private record Land(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building hall,Settlement.Building mine,BlockPos a,BlockPos b,BlockPos ore,BlockPos log,BlockPos box){}
 private static void openSky(net.minecraft.server.level.ServerLevel l,BlockPos from){
  int top=l.getHeight(Heightmap.Types.WORLD_SURFACE,from.getX(),from.getZ());
  for(int y=from.getY();y<top;y++)l.setBlock(new BlockPos(from.getX(),y,from.getZ()),Blocks.AIR.defaultBlockState(),2);
 }
 /** A settlement on stone with a four by four plot: two layers of stone (one ore), dirt, grass, one log and one chest on top, open to the sky. */
 private static Land land(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var mine=new Settlement.Building(Settlement.childId(s.id(),"building/mine"),"mine",0,0,8);s.addBuilding(mine);
  for(int x=-2;x<20;x++)for(int z=-2;z<16;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<6;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(var b:List.of(hall,mine)){var at=LogisticsRoutes.position(e,b);l.setBlock(at.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(at,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);}
  var a=center.offset(12,0,0);var b=center.offset(15,0,3);
  for(int x=12;x<=15;x++)for(int z=0;z<=3;z++){
   l.setBlock(center.offset(x,-2,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.DIRT.defaultBlockState(),2);l.setBlock(center.offset(x,1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   openSky(l,center.offset(x,2,z));}
  var ore=center.offset(13,-1,1);l.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),2);
  var log=center.offset(14,2,2);l.setBlock(log,Blocks.OAK_LOG.defaultBlockState(),2);
  var box=center.offset(12,2,3);l.setBlock(box,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  return new Land(l,e,hall,mine,a,b,ore,log,box);
 }
 private static void done(GameTestHelper h,Land t){
  try{java.nio.file.Files.deleteIfExists(t.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-dig/"+t.e.settlement().id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  SettlementData.get(t.l.getServer()).remove(t.e.settlement().id());h.succeed();
 }
 /** OWNER_REQUEST 9.15: a demolition is never a way to take a house down — the settlement's own buildings, an occupied home
  *  and a neighbouring village's walls all stay standing, whatever rectangle the mayor draws over them. */
 @GameTest(template="empty",timeoutTicks=200) public static void aDemolitionLeavesHomesAndANeighboursWallsStanding(GameTestHelper h){
  var t=land(h);int ground=t.e.center().getY();var s=t.e.settlement();
  // An occupied home of this village stands on the plot.
  var home=new Settlement.Building(Settlement.childId(s.id(),"building/home"),"home",15,0,3);s.addBuilding(home);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home/0"),1,4,true));
  var resident=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(resident,s.homes().iterator().next().id());
  for(var cell:BuildingPlacement.layout(t.e,home,"home").entrySet())t.l.setBlock(cell.getKey(),cell.getValue(),3);
  // And the corner of a neighbouring village's storehouse reaches into the same rectangle.
  var neighbour=new Settlement(UUID.randomUUID());
  var theirs=new Settlement.Building(Settlement.childId(neighbour.id(),"building/warehouse"),"warehouse",0,0,0);neighbour.addBuilding(theirs);
  var theirCenter=t.e.center().offset(14,0,0);
  var them=new SettlementData.Entry(neighbour,t.l.dimension().location().toString(),theirCenter);SettlementData.get(t.l.getServer()).add(them);
  for(var cell:BuildingPlacement.layout(them,theirs,"warehouse").entrySet())t.l.setBlock(cell.getKey(),cell.getValue(),3);
  try{
   var c=MapOrders.clearing(t.l,t.e,t.a,t.b,ground-3);
   h.assertTrue(c.reason().isEmpty(),"The rectangle can be surveyed at all: "+c.reason());
   var ours=BuildingPlacement.size("home",0);var theirSize=BuildingPlacement.size("warehouse",0);
   var mine=t.e.center().offset(15,0,3);
   var work=new ArrayList<BlockPos>(c.builders());work.addAll(c.miners());
   for(var pos:work){
    boolean inHome=pos.getX()>=mine.getX()&&pos.getX()<mine.getX()+ours[0]&&pos.getZ()>=mine.getZ()&&pos.getZ()<mine.getZ()+ours[1]&&pos.getY()>=mine.getY()-1;
    boolean inTheirs=pos.getX()>=theirCenter.getX()&&pos.getX()<theirCenter.getX()+theirSize[0]&&pos.getZ()>=theirCenter.getZ()&&pos.getZ()<theirCenter.getZ()+theirSize[1];
    h.assertTrue(!inHome,"The occupied home is never given to the crew: "+pos);
    h.assertTrue(!inTheirs,"The neighbour's walls are never given to the crew: "+pos);
   }
   h.assertTrue(c.kept()>0,"And what is left alone is counted: kept="+c.kept());
   var wall=BuildingPlacement.at(t.e,home,0,1,0);var theirWall=BuildingPlacement.at(them,theirs,0,1,0);
   var before=t.l.getBlockState(wall);var theirBefore=t.l.getBlockState(theirWall);
   var order=MapOrders.demolish(t.l,t.e,t.a,t.b,ground-3);
   h.assertTrue(order.equals("nothing"),"A plot that is all houses and their strips gives the crew nothing to take: "+order);
   h.assertTrue(t.l.getBlockState(wall).equals(before)&&t.l.getBlockState(theirWall).equals(theirBefore),"No wall of either village is touched");
   h.assertTrue(Roads.project(t.l,s.id())==null,"And no clearing is queued at all");
  }finally{SettlementData.get(t.l.getServer()).remove(neighbour.id());}
  done(h,t);
 }
 @GameTest(template="empty",timeoutTicks=200) public static void clearingGivesStoneAndOreOnlyToMiners(GameTestHelper h){
  var t=land(h);int ground=t.e.center().getY();
  var c=MapOrders.clearing(t.l,t.e,t.a,t.b,ground-3);
  h.assertTrue(c.reason().isEmpty(),"The plot can be cleared: "+c.reason());
  h.assertTrue(c.builders().stream().noneMatch(p->MapOrders.minersOnly(t.l.getBlockState(p))),"Builders are never given stone or ore");
  h.assertTrue(c.miners().stream().allMatch(p->MapOrders.minersOnly(t.l.getBlockState(p))),"Miners get only stone and ore");
  h.assertTrue(c.miners().contains(t.ore)&&!c.builders().contains(t.ore),"The ore is the miners' work");
  h.assertTrue(c.builders().contains(t.log),"The log is the builders' work");
  h.assertTrue(c.builders().size()==33&&c.miners().size()==32,"Dirt, grass and the log for builders, both stone layers for miners: "+c.builders().size()+"/"+c.miners().size());
  h.assertTrue(c.kept()==1&&!c.builders().contains(t.box),"The chest on the plot is left alone: kept="+c.kept());
  for(int i=1;i<c.builders().size();i++)h.assertTrue(c.builders().get(i-1).getY()>=c.builders().get(i).getY(),"Builders work from the top down");
  // The order: one clearing project for the builders, one excavation for the miners.
  h.assertTrue(MapOrders.demolish(t.l,t.e,t.a,t.b,ground-3).isEmpty(),"The clearing is ordered");
  var project=Roads.project(t.l,t.e.settlement().id());
  h.assertTrue(project!=null&&project.getString("kind").equals("clearing")&&project.getList("ops",Tag.TAG_COMPOUND).size()==33,"The builders got exactly their blocks");
  h.assertTrue(Excavation.pending(t.l,t.e.settlement().id())&&Excavation.remaining(t.l,t.e.settlement().id())==32,"The miners got exactly the stone and ore");
  h.assertTrue(MapOrders.demolish(t.l,t.e,t.a,t.b,ground-3).equals("busy_builders"),"A second clearing waits for the crews");
  // The builders do their part: the soil and the log go, every stone and the ore stay for the miners.
  for(int guard=0;guard<200;guard++){var r=Roads.apply(t.l,t.e,project);if(r.equals("complete"))break;if(r.equals("unload"))Roads.load(t.l,t.e,t.hall,project);project=Roads.project(t.l,t.e.settlement().id());}
  h.assertTrue(Roads.project(t.l,t.e.settlement().id()).getBoolean("complete"),"The builders finished their part");
  h.assertTrue(t.l.getBlockState(t.log).isAir()&&t.l.getBlockState(t.a).isAir(),"Soil and wood really left the world");
  h.assertTrue(t.l.getBlockState(t.ore).is(Blocks.IRON_ORE)&&t.l.getBlockState(t.a.below()).is(Blocks.STONE),"No builder touched the stone or the ore");
  var hallChest=LogisticsRoutes.chest(t.l,t.e,t.hall);
  h.assertTrue(hallChest.countItem(Items.DIRT)>0&&hallChest.countItem(Items.OAK_LOG)==1,"What the builders took is in the hall");
  // The miners take the rest, top first, with a real pick.
  var mineChest=LogisticsRoutes.chest(t.l,t.e,t.mine);int dug=0;
  for(int guard=0;guard<80;guard++){var next=Excavation.next(t.l,t.e);if(next==null)break;
   h.assertTrue(MapOrders.minersOnly(t.l.getBlockState(next)),"A miner only takes stone and ore: "+t.l.getBlockState(next));
   h.assertTrue(Excavation.dig(t.l,t.e,t.mine,next).isEmpty(),"The block is taken and stocked");dug++;}
  h.assertTrue(dug==32&&!Excavation.pending(t.l,t.e.settlement().id()),"The miners worked out every ordered block: "+dug);
  h.assertTrue(mineChest.countItem(Items.COBBLESTONE)>0&&mineChest.countItem(Items.RAW_IRON)>0,"Stone yields cobblestone and the ore its raw drop");
  done(h,t);
 }
 @GameTest(template="empty",timeoutTicks=200) public static void nothingIsDugUnderTheSettlementFloor(GameTestHelper h){
  var t=land(h);int ground=t.e.center().getY();
  h.assertTrue(MapOrders.lowest(t.e)==ground&&MapOrders.floor(t.e)==ground-MapOrders.DIG_BELOW,"The floor is ten blocks under the lowest foundation");
  var deep=MapOrders.clearing(t.l,t.e,t.a,t.b,ground-40);
  h.assertTrue(deep.reason().isEmpty()&&deep.deep()>0,"Asking deeper than the floor is cut at the floor: deep="+deep.deep());
  h.assertTrue(deep.level()==MapOrders.floor(t.e)-1,"The kept level is set just under the floor: "+deep.level());
  h.assertTrue(deep.builders().stream().allMatch(p->p.getY()>=MapOrders.floor(t.e))&&deep.miners().stream().allMatch(p->p.getY()>=MapOrders.floor(t.e)),"No block under the floor is ever ordered");
  // A lower building moves the floor down with it; a quarry claimed later obeys the same floor.
  t.e.settlement().addBuilding(new Settlement.Building(Settlement.childId(t.e.settlement().id(),"building/cellar"),"warehouse",0,-4,-1));
  h.assertTrue(MapOrders.floor(t.e)==ground-4-MapOrders.DIG_BELOW,"The lowest building sets the floor");
  h.assertTrue(MapOrders.clearing(t.l,t.e,t.a,t.a.offset(-33,0,0),ground).reason().equals("size"),"A plot wider than the limit is refused");
  done(h,t);
 }
 @GameTest(template="empty",timeoutTicks=200) public static void quarryObeysTheSettlementFloor(GameTestHelper h){
  var l=h.getLevel();var probe=h.absolutePos(new BlockPos(8,3,8));
  // A settlement standing on the natural surface above the test: its quarry cannot go twenty-four deep, only ten under the settlement.
  int surface=l.getHeight(Heightmap.Types.WORLD_SURFACE,probe.getX(),probe.getZ())-1;
  var center=new BlockPos(probe.getX(),surface,probe.getZ());var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var chunk=new ChunkPos(new ChunkPos(center).x+2,new ChunkPos(center).z);
  String claimed=Quarry.claim(l,e,chunk);
  if(claimed.equals("unloaded")||claimed.equals("road")){Quarry.clear(l,s.id());SettlementData.get(l.getServer()).remove(s.id());h.succeed();return;}
  h.assertTrue(claimed.isEmpty(),"The chunk is claimed: "+claimed);
  var record=Quarry.record(l,s.id());int top=record.getInt("layer");
  h.assertTrue(record.getInt("floor")==Math.max(l.getMinBuildHeight()+1,Math.max(top-Quarry.DEPTH,MapOrders.floor(e))),"The quarry floor is the deeper-bound of its own depth and the settlement floor: "+record.getInt("floor"));
  h.assertTrue(record.getInt("floor")>=MapOrders.floor(e),"The quarry never goes under the settlement floor");
  Quarry.clear(l,s.id());SettlementData.get(l.getServer()).remove(s.id());h.succeed();
 }
}
