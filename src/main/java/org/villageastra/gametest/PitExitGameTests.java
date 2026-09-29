package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PitExitGameTests {
 @GameTest(template="empty",batch="pit_exit",timeoutTicks=100)
 public static void fallingFromAboveIsNotARecoverableJump(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(6,5,6));
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-1;y<=7;y++)
   l.setBlock(foot.offset(x,y,z),y<0||y<4&&(x!=0||z!=0)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY()+2.5,foot.getZ()+.5);npc.setOnGround(false);
  h.assertTrue(PitEscapeGoal.escape(npc)==null,"A fall above normal jump height is not a pit escape");
  npc.moveTo(foot.getX()+.5,foot.getY()+.5,foot.getZ()+.5);
  h.assertTrue(PitEscapeGoal.escape(npc)!=null,"An ordinary jump still identifies its stable dry floor");npc.discard();h.succeed();
 }
 @GameTest(template="empty",batch="pit_exit_jump",timeoutTicks=2000)
 public static void futileJumpingStillTriggersRealGoalSelectorRecovery(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(6,5,6));
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=6;y++)
   l.setBlock(foot.offset(x,y,z),y<0||y<3&&(x!=0||z!=0)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  for(int y=1;y<3;y++)l.setBlock(foot.east().above(y),Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);l.addFreshEntity(npc);
  var rim=PitEscapeGoal.escape(npc);h.assertTrue(rim!=null&&rim.getY()==foot.getY()+3,"Choose the upper rim instead of the intermediate shelf");
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  npc.goalSelector.addGoal(1,new PitEscapeGoal(npc));
  npc.goalSelector.addGoal(5,new net.minecraft.world.entity.ai.goal.Goal(){
   {setFlags(java.util.EnumSet.of(Flag.MOVE,Flag.JUMP));}
   public boolean canUse(){return npc.getY()<foot.getY()+2;}
   public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){npc.getJumpControl().jump();}
  });
  h.onEachTick(()->{if(npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(rim))<.04&&npc.onGround()){npc.discard();h.succeed();}});
  h.runAtTickTime(1800,()->h.assertTrue(false,"Repeated futile jumps must not reset stuck detection forever: "+npc.position()+" rim="+rim+" ground="+npc.onGround()+" removed="+npc.getRemovalReason()+" health="+npc.getHealth()+" goals="+npc.runningGoals()));
 }
 @GameTest(template="empty",batch="pit_exit",timeoutTicks=500)
 public static void localReachableLedgeDoesNotPreventStuckRecovery(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(6,5,6));
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=6;y++)
   l.setBlock(foot.offset(x,y,z),y<0||y<3&&(x!=0||z!=0)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  // Like the observed quarry hollow: one low ledge exists, but the distant delivery route stalls.
  for(int y=1;y<3;y++)l.setBlock(foot.east().above(y),Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);npc.setOnGround(true);l.addFreshEntity(npc);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var local=npc.routeTo(foot.east().above(),0);
  h.assertTrue(local!=null&&local.canReach(),"Fixture has an ordinarily reachable local ledge");
  var rim=PitEscapeGoal.escape(npc);h.assertTrue(rim!=null&&rim.getY()==foot.getY()+3,"A local route to the low shelf must not prevent recovery onto the upper rim");
  var forced=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(foot.getX()-8)>>4;x<=(foot.getX()+8)>>4;x++)for(int z=(foot.getZ()-8)>>4;z<=(foot.getZ()+8)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  var goal=new PitEscapeGoal(npc);boolean[] started={false};
  boolean[] stopped={false};
  h.onEachTick(()->{
   if(!started[0]){
    if(!l.isPositionEntityTicking(foot)||npc.tickCount==0||!npc.onGround())return;
    for(int i=0;i<240;i++)if(goal.canUse()){goal.start();started[0]=true;break;}
    h.assertTrue(started[0],"Stuck recovery starts after its ordinary stillness threshold");
   }
   if(npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(rim))<.04&&npc.onGround()){
    goal.stop();npc.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();return;
   }
   if(!stopped[0]){if(goal.canContinueToUse())goal.tick();else{goal.stop();stopped[0]=true;}}
  });
  h.runAtTickTime(400,()->h.assertTrue(npc.isRemoved(),"Resident did not physically reach its ledge: "+npc.position()+" rim="+rim+" started="+started[0]+" stopped="+stopped[0]+" ticks="+npc.tickCount+" entityTicking="+l.isPositionEntityTicking(npc.blockPosition())));
 }
}
