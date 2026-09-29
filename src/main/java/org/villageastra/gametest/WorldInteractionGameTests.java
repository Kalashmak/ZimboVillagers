package org.villageastra.gametest;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.server.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorldInteractionGameTests {
 @GameTest(template="empty") public static void playerCannotBreakBuildingInEitherMode(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));StarterVillage.create(l,origin);
  var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"ProtectionTest"));
  var interior=origin.offset(3,1,3);l.setBlock(interior,Blocks.STONE.defaultBlockState(),3);
  for(var mode:List.of(GameType.CREATIVE,GameType.SURVIVAL)){
   p.setGameMode(mode);p.setPos(origin.getX()+3,origin.getY()+1,origin.getZ()+2);
   h.assertTrue(!p.gameMode.destroyBlock(origin)&&l.getBlockState(origin).is(Blocks.COBBLESTONE),"Actual player break is rejected: "+mode);
   for(var ground:List.of(origin.offset(-3,-3,-3),origin.offset(9,-3,3),origin.offset(3,-3,9))){l.setBlock(ground,Blocks.DIRT.defaultBlockState(),3);h.assertTrue(!p.gameMode.destroyBlock(ground)&&l.getBlockState(ground).is(Blocks.DIRT),"Ground at the outer +3 boundary stays intact: "+mode);}
   h.assertTrue(!p.gameMode.destroyBlock(interior)&&l.getBlockState(interior).is(Blocks.STONE),"Interior blocks also protected: "+mode);
  }
  var outside=origin.offset(-6,0,-6);l.setBlock(outside,Blocks.STONE.defaultBlockState(),3);p.setGameMode(GameType.CREATIVE);
  h.assertTrue(p.gameMode.destroyBlock(outside),"Unowned world remains editable");h.succeed();
 }
 @GameTest(template="empty") public static void stationsRequireActualRegisteredBuilding(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));StarterVillage.create(l,origin);
  h.assertTrue(BuildingInteractions.station(l,origin.offset(2,1,4))==0,"Hall lectern opens management");
  h.assertTrue(BuildingInteractions.station(l,origin.offset(4,1,4))==4,"Hall desk surveys in world");
  h.assertTrue(BuildingInteractions.station(l,origin.offset(1,1,14))==2,"Farm composter opens crop management");
  var desk=origin.offset(4,1,4);l.setBlock(desk,Blocks.AIR.defaultBlockState(),3);BuildingInteractions.ensureHallDesk(l);
  h.assertTrue(l.getBlockState(desk).is(Blocks.CRAFTING_TABLE),"Legacy hall gets a desk in the vacant slot");
  l.setBlock(desk,Blocks.GOLD_BLOCK.defaultBlockState(),3);BuildingInteractions.ensureHallDesk(l);
  h.assertTrue(l.getBlockState(desk).is(Blocks.GOLD_BLOCK),"Migration never replaces an occupied slot");
  var outside=origin.offset(-6,1,-6);l.setBlock(outside,Blocks.LECTERN.defaultBlockState(),3);
  h.assertTrue(BuildingInteractions.station(l,outside)==-1,"Player lecterns keep vanilla interaction");h.succeed();
 }
 @GameTest(template="empty") public static void organicRoadsConnectAllDoorsteps(GameTestHelper h){
  for(int seed=0;seed<30;seed++){
   var s=org.villageastra.domain.Settlement.natural(new UUID(seed,seed*17L),new int[7],2);
   var roads=NaturalVillage.terrainRoads(BlockPos.ZERO,s,p->0);var pending=new java.util.ArrayDeque<BlockPos>();var visited=new HashSet<BlockPos>();
   pending.add(roads.keySet().iterator().next());
   while(!pending.isEmpty()){var p=pending.remove();if(!visited.add(p))continue;for(var d:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.WEST)){var n=p.relative(d);if(roads.containsKey(n)&&!visited.contains(n))pending.add(n);}}
   for(var b:s.buildings())h.assertTrue(visited.contains(new BlockPos(b.x()+BuildingBlueprints.doorX(b.type()),0,b.z()-1)),"Every entrance reaches central network");
   h.assertTrue(visited.size()==roads.size(),"No isolated road cells");
   var wide=new HashSet<BlockPos>();for(var c:roads.keySet()){boolean square=true;for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(!roads.containsKey(c.offset(x,0,z)))square=false;if(square)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)wide.add(c.offset(x,0,z));}
   h.assertTrue(wide.equals(roads.keySet()),"Every road cell belongs to a complete 3 x 3 footprint; no single-block tails");
  }h.succeed();
 }
 @GameTest(template="empty") public static void roadsDetourAroundSteepRidge(GameTestHelper h){
  var s=new org.villageastra.domain.Settlement(UUID.randomUUID());
  s.addBuilding(new org.villageastra.domain.Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0));
  s.addBuilding(new org.villageastra.domain.Settlement.Building(UUID.randomUUID(),"home",28,0,0));
  var roads=NaturalVillage.terrainRoads(BlockPos.ZERO,s,p->p.getX()>=10&&p.getX()<=14&&p.getZ()>=-4&&p.getZ()<=10?8:0);
  h.assertTrue(roads.keySet().stream().anyMatch(p->p.getX()>=10&&p.getX()<=14&&p.getZ()<-4),"Road detours around the ridge instead of taking a straight cut");
  var columns=new HashMap<BlockPos,Integer>();roads.keySet().forEach(p->columns.put(new BlockPos(p.getX(),0,p.getZ()),p.getY()));
  for(var e:columns.entrySet())for(var d:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.WEST)){
   var other=columns.get(e.getKey().relative(d));h.assertTrue(other==null||Math.abs(other-e.getValue())<=1,"Final adjacent road columns remain walkable");
  }h.succeed();
 }
 @GameTest(template="empty") public static void roadCompletionClearsLateFoliageButPreservesPlacedBlocks(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(2,3,2));
  l.setBlock(p,Blocks.DIRT.defaultBlockState(),3);l.setBlock(p.above(),Blocks.SPRUCE_LEAVES.defaultBlockState(),3);
  h.assertTrue(InitialRoads.repairCell(l,p)&&l.getBlockState(p).is(Blocks.DIRT_PATH)&&l.getBlockState(p.above()).isAir(),"Late neighboring tree decoration leaves a walkable road");
  l.setBlock(p.above(),Blocks.GOLD_BLOCK.defaultBlockState(),3);
  h.assertTrue(!InitialRoads.repairCell(l,p)&&l.getBlockState(p.above()).is(Blocks.GOLD_BLOCK),"Completion never clears player construction");h.succeed();
 }
}
