package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ChestExitGameTests {
 @GameTest(template="empty",batch="chest_exit",timeoutTicks=4000)
 public static void builderStepsOffBedroomChestBeforeRoutingToSite(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(6,0,6));var foot=new BlockPos(origin.getX()+24576,90,origin.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(foot.getX()-5)>>4;x<=(foot.getX()+6)>>4;x++)for(int z=(foot.getZ()-5)>>4;z<=(foot.getZ()+3)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-5;x<=6;x++)for(int z=-5;z<=3;z++)for(int y=-1;y<=4;y++){
   boolean room=x>=-1&&x<=5&&z>=-2&&z<=2,wall=room&&(x==-1||x==5||z==-2||z==2);
   l.setBlock(foot.offset(x,y,z),y<0||room&&y==3||wall&&y>=0&&y<3?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
  l.setBlock(foot,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  for(int x:new int[]{1,3})for(int z=-1;z<=0;z++)l.setBlock(foot.offset(x,0,z),Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,z==0?BedPart.HEAD:BedPart.FOOT),2);
  l.setBlock(foot.offset(2,2,0),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true),2);
  var door=foot.offset(2,0,-2);
  for(int y=0;y<=1;y++)l.setBlock(door.above(y),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,y==0?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY()+.875,foot.getZ()+.5);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->!(g instanceof BedExitGoal)&&!(g instanceof ResidentDoorGoal));npc.targetSelector.removeAllGoals(g->true);
  var destination=foot.offset(-4,0,-4);
  h.assertTrue(foot.north().equals(BedExitGoal.landing(npc)),"Chest occupant must step toward the bedroom door before distant routing");
  npc.goalSelector.addGoal(5,new Goal(){ {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%20==0)npc.getNavigation().moveTo(npc.routeTo(destination,0,NaturalSupplyGoal.ROUTE_RANGE),.8);}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(foot),"Entity chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Resident added"));
  h.onEachTick(()->{h.assertTrue(npc.tickCount<700,"Chest exit stalled: "+npc.position()+" goals="+npc.runningGoals());if(npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(destination))<.5&&npc.onGround()){
   h.assertTrue(BedExitGoal.landing(npc)==null,"Floor movement does not restart recovery");h.assertTrue(l.getBlockState(foot).is(VillageAstra.OWNED_CHEST.get())&&l.getBlockState(door.above(2)).is(Blocks.STONE),"Chest and lintel preserved");
   npc.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  }});
 }
}
