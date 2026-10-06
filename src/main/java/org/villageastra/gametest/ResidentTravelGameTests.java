package org.villageastra.gametest;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResidentTravelGameTests {
 @GameTest(template="empty",batch="resident_travel",timeoutTicks=600)
 public static void flatTravelRunsFasterAndSlowsBeforeSteps(GameTestHelper h){travel(h,false);}
 @GameTest(template="empty",batch="resident_travel",timeoutTicks=600)
 public static void dirtPathTravelRunsFasterAndSlowsAtBrokenRoad(GameTestHelper h){travel(h,true);}
 private static void travel(GameTestHelper h,boolean road){
  var l=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(origin.getX()+(road?131072:98304),90,origin.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+84)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+8)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-2;x<=84;x++)for(int z=-2;z<=8;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),y==0?(road?Blocks.DIRT_PATH:Blocks.STONE).defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var fast=VillageAstra.RESIDENT.get().create(l);var walking=VillageAstra.RESIDENT.get().create(l);
  for(var npc:List.of(fast,walking)){npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);}
  double feet=road?90.9375:91;
  fast.moveTo(base.getX()+.5,feet,base.getZ()+1.5);walking.moveTo(base.getX()+.5,feet,base.getZ()+4.5);fast.setOnGround(true);walking.setOnGround(true);
  var target=base.offset(80,1,1);var route=fast.routeTo(target,0,96);
  h.assertTrue(ResidentTravel.speed(fast,route,.8)>.8,"A supported flat route permits running");
  var node=route.getNodePos(4);l.setBlock(node.above(),Blocks.STONE.defaultBlockState(),2);
  h.assertTrue(ResidentTravel.speed(fast,route,.8)==.8,"A changed obstruction immediately disables running even with an old path");l.setBlock(node.above(),Blocks.AIR.defaultBlockState(),2);
  var support=l.getBlockState(node.below());l.setBlock(node.below(),Blocks.AIR.defaultBlockState(),2);
  h.assertTrue(ResidentTravel.speed(fast,route,.8)==.8,"A hole in an existing road disables running immediately");l.setBlock(node.below(),support,2);
  l.setBlock(node,Blocks.WATER.defaultBlockState(),2);
  h.assertTrue(ResidentTravel.speed(fast,route,.8)==.8,"Water on an existing road disables running immediately");l.setBlock(node,Blocks.AIR.defaultBlockState(),2);
  int[] started={-1};
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(fast.blockPosition()),"Entity chunks ready")).thenExecute(()->{
   l.addFreshEntity(fast);l.addFreshEntity(walking);fast.getNavigation().moveTo(fast.routeTo(target,0,96),.8);
   walking.getNavigation().moveTo(walking.routeTo(base.offset(80,1,4),0,96),.8/1.35);started[0]=fast.tickCount;
  });
  h.onEachTick(()->{if(started[0]<0||fast.tickCount-started[0]<80)return;
   double run=fast.getX()-base.getX()-.5,walk=walking.getX()-base.getX()-.5;
   h.assertTrue(walk>5&&run>walk*1.2,"Real running distance exceeds walking by at least 20%: run="+run+" walk="+walk);
   h.assertTrue(fast.getY()>=feet-.01&&walking.getY()>=feet-.01,"Both remain on supported ground");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_TRAVEL VERIFIED road={} ticks=80 running={} walking={}",road,run,walk);
   fast.discard();walking.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
}
