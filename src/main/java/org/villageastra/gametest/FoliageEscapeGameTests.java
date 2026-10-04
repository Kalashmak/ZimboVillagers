package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.FoliageEscapeGoal;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FoliageEscapeGameTests {
 @GameTest(template="empty",batch="foliage_boundaries",timeoutTicks=100)
 public static void leafEscapeLeavesSupportsPlacedFoliageAndBuildingsIntact(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(8,0,8));var foot=new BlockPos(at.getX(),160,at.getZ());var leaf=Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,1);
  for(var p:BlockPos.betweenClosed(foot.offset(-1,-1,-1),foot.offset(1,2,1)))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);
  l.setBlock(foot.below(),leaf,2);l.setBlock(foot.east().above(),leaf,2);h.assertTrue(FoliageEscapeGoal.obstruction(npc)==null,"Never clear the supporting crown or adjacent leaves");
  var head=foot.above();l.setBlock(head,leaf.setValue(LeavesBlock.PERSISTENT,true),2);h.assertTrue(FoliageEscapeGoal.obstruction(npc)==null,"Player-placed persistent leaves remain");
  l.setBlock(head,leaf.setValue(LeavesBlock.WATERLOGGED,true),2);h.assertTrue(FoliageEscapeGoal.obstruction(npc)==null,"Do not release water from waterlogged leaves");
  l.setBlock(head,Blocks.OAK_LOG.defaultBlockState(),2);h.assertTrue(FoliageEscapeGoal.obstruction(npc)==null,"The escape never removes logs or walls");
  l.setBlock(head,leaf,2);h.assertTrue(head.equals(FoliageEscapeGoal.obstruction(npc)),"A natural leaf inside the body is the obstruction");
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0));SettlementData.get(l.getServer()).add(new SettlementData.Entry(s,l.dimension().location().toString(),foot));
  try{h.assertTrue(FoliageEscapeGoal.obstruction(npc)==null,"Even natural leaves inside protected building bounds remain");}finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 @GameTest(template="empty",batch="foliage_escape",timeoutTicks=1200)
 public static void residentWalksOutAfterNaturalLeavesGrowInsideHisBody(GameTestHelper h){run(h,false);}
 @GameTest(template="empty",batch="foliage_path",timeoutTicks=1200)
 public static void residentEscapesGrownFoliageWhileStandingOnAPath(GameTestHelper h){run(h,true);}
 private static void run(GameTestHelper h,boolean path){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(8,0,8));var foot=new BlockPos(origin.getX(),160,origin.getZ());
  for(var p:BlockPos.betweenClosed(foot.offset(-6,-1,-6),foot.offset(6,6,6)))l.setBlock(p,p.getY()==159?(path?Blocks.DIRT_PATH:Blocks.STONE).defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var e=new SettlementData.Entry(s,l.dimension().location().toString(),foot);SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(foot.getX()+.401,foot.getY(),foot.getZ()+.42);npc.setOnGround(true);
  var destination=foot.east(5);var grown=foot.above();
  // Let the real body settle before a low natural crown grows through its head.
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}
   public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount>40&&npc.tickCount%20==0)npc.getNavigation().moveTo(destination.getX()+.5,destination.getY(),destination.getZ()+.5,.8);}
  });l.addFreshEntity(npc);
  h.runAtTickTime(30,()->{for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)l.setBlock(grown.offset(x,0,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,1),2);});
  h.runAtTickTime(1100,()->h.assertTrue(false,"Natural foliage still traps body: "+npc.position()+" status="+npc.workStatus()));
  // Vanilla navigation finishes a node about one block before its centre. Require real travel
  // past the whole crown and arrival within that ordinary tolerance, not exact centring.
  h.startSequence().thenIdle(40).thenWaitUntil(()->h.assertTrue(npc.getX()>=foot.getX()+4&&npc.position().distanceToSqr(Vec3.atBottomCenterOf(destination))<2.25,"Resident must leave the crown and resume walking with real physics"))
   .thenExecute(()->{h.assertTrue(l.getBlockState(grown).isAir(),"Only the obstructing natural leaf is cleared");h.assertTrue(l.getBlockState(foot.below()).is(path?Blocks.DIRT_PATH:Blocks.STONE),"Supporting terrain remains");npc.discard();SettlementData.get(l.getServer()).remove(s.id());}).thenSucceed();
 }
}
