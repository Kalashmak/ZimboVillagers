package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BuilderDescentGameTests {
 @GameTest(template="empty",batch="builder_descent",timeoutTicks=3200)
 public static void stalledBuilderDescendsThroughIntermediateFloorInsteadOfClimbingBack(GameTestHelper h){run(h,true);}
 @GameTest(template="empty",batch="resident_tiered_descent",timeoutTicks=3200)
 public static void ordinaryResidentDescendsWithoutPitEscapeUndoingTheLowerLanding(GameTestHelper h){run(h,false);}
 private static void run(GameTestHelper h,boolean builder){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+61440+(builder?0:128),90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-8)>>4;x<=(base.getX()+8)>>4;x++)for(int z=(base.getZ()-8)>>4;z<=(base.getZ()+8)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=0;y<=10;y++){
   boolean wall=y>=1&&y<=7&&(Math.abs(x)==3&&Math.abs(z)<=3||Math.abs(z)==3&&Math.abs(x)<=3);
   boolean floor=y==4&&Math.abs(x)<3&&Math.abs(z)<3&&!(x==-2&&z==2);
   boolean door=x==0&&z==-3&&y>=1&&y<=2;
   l.setBlock(base.offset(x,y,z),y==0||wall&&!door||floor?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+8,base.getZ()+3.5);npc.setOnGround(true);var target=base.offset(0,1,-5);
  var settlement=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);settlement.addBuilding(hall);var home=UUID.randomUUID();settlement.addHome(new Settlement.Home(home,1,2,true));
  var resident=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(resident,home);settlement.assign(resident.id(),builder?Profession.BUILDER:Profession.PORTER,hall.id());npc.bind(settlement.id(),settlement.resident(resident.id()));
  SettlementData.get(l.getServer()).add(new SettlementData.Entry(settlement,l.dimension().location().toString(),base));
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());project.putString("kind","building");project.putLong("origin",base.asLong());project.putBoolean("funded",true);HallUpgradeGoal.store(l,settlement.id(),project);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(0,new PitEscapeGoal(npc));npc.goalSelector.addGoal(1,new SafeDescentGoal(npc));
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(builder&&npc.getY()>base.getY()+1.7){npc.getNavigation().stop();npc.workStatus("needs_access");}else if(npc.tickCount%20==0)npc.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,.8);}
   public void stop(){npc.getNavigation().stop();}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Construction chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Worker loaded"));
  // Vanilla navigation may finish at the adjacent door node. Reaching the lower
  // job means standing outside that doorway, within its normal two-block reach.
  h.onEachTick(()->{if(!npc.onGround()||npc.getY()>base.getY()+1.1||npc.getZ()>=base.getZ()-3||npc.position().distanceToSqr(Vec3.atBottomCenterOf(target))>=4)return;
   h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Both controlled descents preserve health");h.assertTrue(l.getBlockState(base.offset(0,4,0)).is(Blocks.STONE)&&l.getBlockState(base.offset(-2,4,2)).isAir(),"Recovery does not dig or add blocks");
   npc.discard();HallUpgradeGoal.drop(l,settlement.id());SettlementData.get(l.getServer()).remove(settlement.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(3000,()->h.assertTrue(false,"Worker remained on its wall or intermediate floor: "+npc.position()+" goals="+npc.runningGoals()));
 }
}
