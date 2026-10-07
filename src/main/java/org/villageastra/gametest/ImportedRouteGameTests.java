package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ImportedRouteGameTests {
 @GameTest(template="empty",batch="imported_route",timeoutTicks=3000)
 public static void doorUpdateDoesNotRestoreThePreviousBedroomDestination(GameTestHelper h){walk(h,false);}
 @GameTest(template="empty",batch="imported_route_long",timeoutTicks=6000)
 public static void importedExpeditionRetainsItsRangeAcrossDoorUpdates(GameTestHelper h){walk(h,true);}
 private static void walk(GameTestHelper h,boolean expedition){
  int length=expedition?180:38;
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(expedition?118784:110592),100,at.getZ());
  var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-3)>>4;x<=(base.getX()+length+4)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+3)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(int x=-3;x<=length+4;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),(y==0||x==10&&z!=0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  var door=base.offset(10,1,0);l.setBlock(door,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST),2);l.setBlock(door.above(),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->!(g instanceof ResidentDoorGoal));npc.targetSelector.removeAllGoals(g->true);
  var end=base.offset(length,1,0);boolean[] updated={false};int[] started={-1};
  npc.goalSelector.addGoal(5,new net.minecraft.world.entity.ai.goal.Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}
   public boolean canUse(){return updated[0];}
   public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0&&npc.getNavigation().isDone())npc.getNavigation().moveTo(npc.routeTo(end,0,NaturalSupplyGoal.ROUTE_RANGE),.8);}
  });
  Runnable clean=()->{npc.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);};
  h.startSequence().thenWaitUntil(()->h.assertTrue(TouchLoad.ticking(l,npc.blockPosition()),"Native chunk ready")).thenExecute(()->{
   l.addFreshEntity(npc);var nav=npc.getNavigation();var bedroom=base.offset(2,1,0);h.assertTrue(nav.moveTo(nav.createPath(bedroom,0),.8),"Previous bedroom path exists");
   var work=npc.routeTo(end,0,NaturalSupplyGoal.ROUTE_RANGE);h.assertTrue(work!=null&&work.canReach(),"Independent work planner reaches the destination");h.assertTrue(nav.moveTo(work,.8),"Work path adopted");started[0]=npc.tickCount;
   var previous=nav.createPath(bedroom,0);h.assertTrue(previous!=null&&previous.getTarget().equals(bedroom)&&nav.getPath()==work,"Native request must not reuse the imported work path as its stale bedroom cache, or change the active path while merely planning");
  });
  h.onEachTick(()->{
   if(started[0]<0)return;
   if(!updated[0]&&npc.tickCount-started[0]>=25){
    // Inspect this recomputation before the normal work goal may retry an expired path.
    npc.getNavigation().recomputePath();updated[0]=true;
    h.assertTrue(npc.getNavigation().getPath()!=null&&npc.getNavigation().getPath().canReach()&&npc.getNavigation().getPath().getTarget().equals(end),"Terrain update must retain the imported work destination and full planning range, got "+npc.getNavigation().getTargetPos());
   }
   h.assertTrue(npc.tickCount-started[0]<(expedition?2500:700),"Worker failed to reach work after leaving the bedroom: "+npc.position()+" base="+base+" end="+end+" ground="+npc.onGround()+" door="+l.getBlockState(door)+" goals="+npc.runningGoals()+" path="+npc.getNavigation().getPath()+" target="+npc.getNavigation().getTargetPos()+" next="+(npc.getNavigation().getPath()==null||npc.getNavigation().getPath().isDone()?null:npc.getNavigation().getPath().getNextNodePos()));
   if(updated[0]&&npc.distanceToSqr(end.getX()+.5,end.getY(),end.getZ()+.5)<1){h.assertTrue(npc.onGround()&&l.getBlockState(door).is(Blocks.OAK_DOOR),"Worker walks through the intact door");clean.run();h.succeed();}
  });
 }
}
