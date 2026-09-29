package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-063: a generated village has straight roads that climb gently and meet every door at floor level, flat strips around its lots,
 *  and no tree left on its territory. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class VillageGenerationGameTests {
 /** The top block of a gentle slope with a one-block ripple, like ordinary grassland — what the generator reports as first occupied. */
 private static int terrain(BlockPos p){return 70+Math.floorDiv(p.getX(),12)+Math.floorMod(p.getX()*31+p.getZ()*17,5)/4;}
 private record Village(BlockPos origin,Settlement settlement){}
 private static Village village(UUID id){
  var lots=OrganicLots.buildings(id,4);var hall=lots.get(0);
  int hallFloor=terrain(new BlockPos(hall.x()+BuildingBlueprints.doorX(hall.type()),0,hall.z()));
  var origin=new BlockPos(0,hallFloor,0);var elevations=new int[7];
  for(int i=0;i<7;i++){var b=lots.get(i);elevations[i]=terrain(new BlockPos(b.x()+BuildingBlueprints.doorX(b.type()),0,b.z()))-hallFloor;}
  return new Village(origin,Settlement.natural(id,elevations,4));
 }
 private static int turns(List<BlockPos> path){
  int turns=0;for(int i=2;i<path.size();i++){var a=path.get(i-2);var b=path.get(i-1);var c=path.get(i);
   boolean straight=(a.getX()==b.getX()&&b.getX()==c.getX())||(a.getZ()==b.getZ()&&b.getZ()==c.getZ());if(!straight)turns++;}
  return turns;
 }
 @GameTest(template="empty",timeoutTicks=100) public static void roadsAreStraightGentleAndMeetEveryDoorAtTheFloor(GameTestHelper h){
  for(var seed:List.of("astra-a","astra-b","astra-c")){
   var v=village(UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   var paths=NaturalVillage.network(v.origin(),v.settlement(),VillageGenerationGameTests::terrain);
   h.assertTrue(paths.size()==6,seed+": every building is joined to the roads: "+paths.size());
   for(var path:paths)h.assertTrue(turns(path)<=4,seed+": a road turns no more than it must: "+turns(path)+" turns over "+path.size()+" blocks");
   var roads=NaturalVillage.straightRoads(v.origin(),v.settlement(),VillageGenerationGameTests::terrain);
   Map<Long,Integer> height=new HashMap<>();for(var cell:roads.keySet())height.put(BlockPos.asLong(cell.getX(),0,cell.getZ()),cell.getY());
   for(var cell:roads.keySet())for(var d:List.of(new int[]{1,0},new int[]{0,1})){
    Integer next=height.get(BlockPos.asLong(cell.getX()+d[0],0,cell.getZ()+d[1]));
    h.assertTrue(next==null||Math.abs(next-cell.getY())<=1,seed+": neighbouring road blocks differ by one block at most at "+cell.toShortString()+" vs "+next);}
   for(var cell:roads.keySet())h.assertTrue(Math.abs(cell.getY()-terrain(cell))<=3,seed+": the road lies on the ground, not on a dam or in a trench: "+cell.toShortString()+" ground "+terrain(cell));
   for(var b:v.settlement().buildings()){var door=new BlockPos(b.x()+BuildingBlueprints.doorX(b.type()),0,b.z()-1);
    Integer y=height.get(BlockPos.asLong(door.getX(),0,door.getZ()));
    h.assertTrue(y!=null&&y==v.origin().getY()+b.y(),seed+": the road meets the "+b.type()+" door at its floor: "+y+" vs "+(v.origin().getY()+b.y()));}
  }
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theStripAroundEveryLotIsLevelWithItsFloor(GameTestHelper h){
  var v=village(UUID.nameUUIDFromBytes("astra-lots".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  var lots=NaturalVillage.lots(v.origin(),v.settlement());
  for(var b:v.settlement().buildings()){
   var design=BuildingBlueprints.design(b.type());
   var door=new BlockPos(b.x()+BuildingBlueprints.doorX(b.type()),0,b.z()-1);
   h.assertTrue(lots.get(door)!=null&&lots.get(door)==v.origin().getY()+b.y(),"The ground at the "+b.type()+" door is at its floor");
   // AD-104: a lot is the building's own ground — a farm's is its farmhouse and the whole box of its field, from one column west of it.
   var own=NaturalVillage.lot(v.settlement(),b);int west=own.stream().mapToInt(BlockPos::getX).min().orElseThrow(),east=own.stream().mapToInt(BlockPos::getX).max().orElseThrow();
   h.assertTrue(west<=b.x()&&east>=b.x()+design.width()-1,"The "+b.type()+"'s lot holds the building");
   for(var c:own)h.assertTrue(!lots.containsKey(c),"A lot's own ground is the building's, not the strip's");
   for(int x=west-NaturalVillage.STRIP;x<=east+NaturalVillage.STRIP;x++)h.assertTrue(lots.containsKey(new BlockPos(x,0,b.z()-NaturalVillage.STRIP)),"The whole strip in front is levelled");
  }
  h.succeed();
 }
 private static void openSky(net.minecraft.server.level.ServerLevel l,BlockPos from){
  int top=l.getHeight(Heightmap.Types.WORLD_SURFACE,from.getX(),from.getZ());
  for(int y=from.getY();y<top;y++)l.setBlock(new BlockPos(from.getX(),y,from.getZ()),Blocks.AIR.defaultBlockState(),2);
 }
 private static void tree(net.minecraft.server.level.ServerLevel l,BlockPos base){
  for(int y=0;y<5;y++)l.setBlock(base.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
  for(int dx=-2;dx<=2;dx++)for(int dy=3;dy<=6;dy++)for(int dz=-2;dz<=2;dz++){var p=base.offset(dx,dy,dz);if(l.getBlockState(p).isAir())l.setBlock(p,Blocks.OAK_LEAVES.defaultBlockState(),2);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void treesOnTheVillageTerritoryGoWhole(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  var home=new Settlement.Building(Settlement.childId(s.id(),"building/home-trees"),"home",0,0,0);s.addBuilding(home);
  for(int x=-6;x<40;x++)for(int z=-6;z<14;z++){l.setBlock(center.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=0;y<8;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  // One tree in the strip beside the house, one far out in the field.
  var inside=center.offset(8,0,3);var outside=center.offset(30,0,3);
  // The house has a timber post of real logs right beside the tree: it must stay.
  var post=center.offset(6,0,3);
  for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){openSky(l,inside.offset(dx,0,dz));openSky(l,outside.offset(dx,0,dz));}
  l.setBlock(post,Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),2);l.setBlock(post.above(),Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),2);
  tree(l,inside);tree(l,outside);
  try{
   int removed=VillageClearing.clear(l,e,new long[0]);
   int left=0;for(int dx=-1;dx<=3;dx++)for(int dy=0;dy<=7;dy++)for(int dz=-3;dz<=3;dz++){var st=l.getBlockState(inside.offset(dx,dy,dz));if(st.is(BlockTags.LOGS)||st.is(BlockTags.LEAVES))left++;}
   h.assertTrue(left==0,"Nothing of the tree on the territory is left: "+left+" blocks, "+removed+" removed");
   h.assertTrue(l.getBlockState(outside).is(Blocks.OAK_LOG)&&l.getBlockState(outside.above(5)).is(Blocks.OAK_LEAVES),"The tree out in the field stands");
   h.assertTrue(l.getBlockState(post).is(Blocks.STRIPPED_SPRUCE_LOG)&&l.getBlockState(post.above()).is(Blocks.STRIPPED_SPRUCE_LOG),"The house's own timber is never taken for a tree");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
