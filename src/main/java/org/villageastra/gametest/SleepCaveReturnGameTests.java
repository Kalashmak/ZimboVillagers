package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SleepCaveReturnGameTests {
 @GameTest(template="empty",batch="sleep_cave_return",timeoutTicks=3000)
 public static void nightReturnLeavesDeepDryCaveAndReachesTheResidentsOwnBed(GameTestHelper h){check(h,false);}
 @GameTest(template="empty",batch="sleep_cave_return",timeoutTicks=3000)
 public static void distantNightReturnKeepsDeepRecoveryInsteadOfDroppingThePartialRoute(GameTestHelper h){check(h,true);}
 private static void check(GameTestHelper h,boolean distant){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+458752+(distant?65536:0),120,at.getZ());int edge=distant?65:8;var held=PhysicalFixtureChunks.force(l,base,-4,distant?96:40,-8,8);
  for(int x=-4;x<=(distant?96:40);x++)for(int z=-8;z<=8;z++)for(int y=-10;y<=4;y++){
   boolean hollow=x>=edge&&x<=edge+16&&Math.abs(z)<=3;l.setBlock(base.offset(x,y,z),(y<=(hollow?-9:0)?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var home=new Settlement.Building(UUID.randomUUID(),"home",0,0,0);s.addBuilding(home);s.addHome(new Settlement.Home(home.id(),1,2,true));SettlementData.get(l.getServer()).add(e);
  var bed=base.offset(2,1,3);l.setBlock(bed,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD),2);l.setBlock(bed.south(),Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.FOOT),2);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home.id());npc.bind(s.id(),r);npc.moveTo(base.getX()+edge+8.5,base.getY()-8,base.getZ()+.5);
  h.assertTrue(bed.equals(SleepGoal.bed(l,e,r)),"The real bed belongs to this resident");npc.onlyGoals(g->g instanceof PitEscapeGoal||g instanceof SafeDescentGoal||g instanceof ResidentDoorGoal||g instanceof BedExitGoal,2,new SleepGoal(npc,true,()->14000));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Cave entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  boolean[] done={false};Runnable clean=()->{done[0]=true;npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{if(done[0])return;h.assertTrue(npc.isAlive()&&npc.getHealth()==npc.getMaxHealth(),"Night return preserves health");
   // The goal has its own night clock; the shared daytime world can immediately
   // wake the body. Observe the real bed activation, as the home-safety tests do.
   if(!npc.isSleeping()&&!"sleeping".equals(npc.workStatus()))return;
   h.assertTrue(npc.distanceToSqr(bed.getCenter())<=4&&bed.equals(SleepGoal.bed(l,e,r))&&l.getBlockState(base.offset(edge-1,-5,0)).is(Blocks.STONE),"Own bed reached physically; cave wall remains intact");clean.run();h.succeed();});
  h.runAtTickTime(2800,()->{if(done[0])return;var why=npc.position()+" goals="+npc.runningGoals()+" tick="+npc.tickCount+" entityTicking="+l.isPositionEntityTicking(npc.blockPosition())+" added="+npc.isAddedToWorld()+" status="+npc.workStatus()+" path="+npc.getNavigation().getPath();clean.run();throw new GameTestAssertException("Night cave return stalled: "+why);});
 }
}
