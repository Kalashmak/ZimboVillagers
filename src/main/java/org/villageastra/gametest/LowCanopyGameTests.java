package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LowCanopyGameTests {
 @GameTest(template="empty",batch="low_canopy",timeoutTicks=1600)
 public static void foresterDescendsFromLowOakCrownAndFellsFromGround(GameTestHelper h){run(h,true);}
 @GameTest(template="empty",batch="low_canopy_ground",timeoutTicks=1600)
 public static void foresterApproachesLowOakWithoutClimbingItsTrunk(GameTestHelper h){run(h,false);}
 @GameTest(template="empty",batch="low_canopy_diagonal",timeoutTicks=1600)
 public static void foresterFinishesTheLastDiagonalStrideToTheTree(GameTestHelper h){run(h,false,true);}
 private static void run(GameTestHelper h,boolean crown){run(h,crown,false);}
 private static void run(GameTestHelper h,boolean crown,boolean diagonal){
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(0,0,0));var center=new BlockPos(at.getX(),160,at.getZ());var foot=center.offset(24,0,24);
  for(var p:BlockPos.betweenClosed(foot.offset(-8,-1,-8),foot.offset(8,8,8)))l.setBlock(p,p.getY()==foot.getY()-1?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var logs=new ArrayList<BlockPos>();var leaves=new ArrayList<BlockPos>();
  for(int y=0;y<4;y++){var p=foot.above(y);logs.add(p);l.setBlock(p,Blocks.OAK_LOG.defaultBlockState(),2);}
  for(int y=1;y<=4;y++){int radius=y<=2?2:1;for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
   if(x==0&&z==0&&y<4||y>=3&&Math.abs(x)+Math.abs(z)>1)continue;var p=foot.offset(x,y,z);leaves.add(p);l.setBlock(p,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,1),2);
  }}
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);var hut=new Settlement.Building(UUID.randomUUID(),"forester",0,0,0);s.addBuilding(hut);s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",-20,0,0));var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.FORESTER,hut.id());npc.bind(s.id(),s.resident(r.id()));
  npc.moveTo(foot.getX()+(diagonal?4.987:crown?.56:-5.5),foot.getY()+(crown?5:0),foot.getZ()+(diagonal?2.862:crown?1.48:.5));npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);var duty=new ResourceWorkGoal(npc,true,()->6000L);
  // The observed diagonal stall must also finish at the ordinary once-per-second work cadence.
  npc.goalSelector.addGoal(1,diagonal?new net.minecraft.world.entity.ai.goal.Goal(){
   {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
   public boolean canUse(){return duty.canUse();}public boolean canContinueToUse(){return duty.canContinueToUse();}
   public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%20==0)duty.tick();}public void stop(){duty.stop();}
  }:duty);
  var t=new CompoundTag();t.putInt("schema",2);t.putUUID("worker",npc.getUUID());t.putUUID("operation",UUID.randomUUID());t.putString("stage","dig");t.putLong("target",foot.asLong());t.putLongArray("base",new long[]{foot.asLong()});t.putLongArray("tree",logs.stream().mapToLong(BlockPos::asLong).toArray());var before=new ListTag();logs.forEach(p->before.add(NbtUtils.writeBlockState(l.getBlockState(p))));t.put("treeBefore",before);t.putLongArray("treeLeaves",leaves.stream().mapToLong(BlockPos::asLong).toArray());t.put("tool",new ItemStack(Items.STONE_AXE).save(new CompoundTag()));MineWork.write(l,hut,t);l.addFreshEntity(npc);
  h.runAtTickTime(1500,()->h.assertTrue(false,"Low canopy stalled: "+npc.position()+" "+npc.workStatus()+" target="+npc.getNavigation().getTargetPos()));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.getBlockState(foot).isAir(),"Actual forester must find ground access and fell the tree"))
   .thenExecute(()->{var work=MineWork.read(l,hut);h.assertTrue(ForestFixture.count(work.getList("cargo",Tag.TAG_COMPOUND),Items.OAK_LOG)==4,"All four actual logs enter cargo");h.assertTrue(ItemStack.of(work.getCompound("tool")).getDamageValue()==4,"Actual four-log axe wear");h.assertTrue(npc.getY()<=foot.getY()+1,"Felling happens from ground beside the trunk");npc.discard();SettlementData.get(l.getServer()).remove(s.id());}).thenSucceed();
 }
}
