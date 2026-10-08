package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** A dispersed settlement still sends its teacher home by ordinary navigation. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DistantSleepGameTests {
 @GameTest(template="empty",batch="distant_sleep",timeoutTicks=5000)
 public static void teacherWalksHomeBeyondTheOldNinetySixBlockLimit(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+122880,90,at.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-3)>>4;x<=(base.getX()+195)>>4;x++)for(int z=(base.getZ()-6)>>4;z<=(base.getZ()+14)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-3;x<=195;x++)for(int z=-6;z<=14;z++)for(int y=0;y<=16;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  // A short wall requires real pathfinding; crossing straight through it is not a return.
  for(int z=-2;z<=2;z++)for(int y=1;y<=3;y++)l.setBlock(base.offset(70,y,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var home=new Settlement.Building(UUID.randomUUID(),"home",180,0,0);var school=new Settlement.Building(UUID.randomUUID(),"school",0,0,0);s.addBuilding(home);s.addBuilding(school);s.addHome(new Settlement.Home(home.id(),1,2,true));
  for(var cell:BuildingBlueprints.layout("home",base.offset(180,0,0)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());s.assign(r.id(),Profession.TEACHER,school.id());npc.bind(s.id(),r);npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);
  long[] time={18000};var goal=new SleepGoal(npc,true,()->time[0]);npc.onlyGoals(g->g instanceof ResidentDoorGoal||g instanceof BedExitGoal,2,goal);npc.targetSelector.removeAllGoals(g->true);
  var bed=SleepGoal.bed(l,e,r);h.assertTrue(bed!=null&&npc.blockPosition().distSqr(bed)>96*96,"Own bed is beyond the old cutoff");
  time[0]=1000;h.assertTrue(!goal.canUse(),"Daytime duty is unchanged");time[0]=18000;
  npc.moveTo(base.getX()-200.5,base.getY()+1,base.getZ()+.5);h.assertTrue(!goal.canUse(),"Return remains bounded to 320 blocks");npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5);
  h.assertTrue(goal.canUse(),"Teacher must start a night return from the distant school");
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Physical start chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Teacher added"));
  boolean[] detour={false};
  h.onEachTick(()->{l.resetEmptyTime();if(Math.abs(npc.getX()-(base.getX()+70.5))<2&&Math.abs(npc.getZ()-(base.getZ()+.5))>2.5)detour[0]=true;
   h.assertTrue(npc.isAlive()&&npc.getHealth()==npc.getMaxHealth(),"Return is safe");h.assertTrue(npc.tickCount<4400,"Distant sleep stalled: "+npc.position()+" "+npc.workStatus());
   if(!npc.isSleeping()&&!"sleeping".equals(npc.workStatus()))return;
   h.assertTrue(detour[0],"Teacher physically walked around the wall");h.assertTrue(npc.distanceToSqr(bed.getCenter())<9,"Teacher reached their own bed");h.assertTrue(l.getBlockState(base.offset(70,1,0)).is(Blocks.STONE_BRICKS),"Return preserved terrain");
   System.out.println("DISTANT_SLEEP bodyTicks="+npc.tickCount);goal.stop();npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
}
