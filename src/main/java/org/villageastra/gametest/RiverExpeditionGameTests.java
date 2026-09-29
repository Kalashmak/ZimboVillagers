package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RiverExpeditionGameTests {
 @GameTest(template="empty",batch="river_expedition",timeoutTicks=7000)
 public static void distantWorkerFindsAndWalksAReturnableRouteAcrossRiverTerrain(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+53248,90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-4)>>4;x<=(base.getX()+184)>>4;x++)for(int z=(base.getZ()-124)>>4;z<=(base.getZ()+124)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-4;x<=184;x++)for(int z=-124;z<=124;z++){
   l.setBlock(base.offset(x,0,z),Blocks.DIRT.defaultBlockState(),2);
   if(x>=90&&x<=109&&Math.abs(z)<=120){l.setBlock(base.offset(x,-4,z),Blocks.STONE.defaultBlockState(),2);for(int y=-3;y<=0;y++)l.setBlock(base.offset(x,y,z),Blocks.WATER.defaultBlockState(),2);}
  }
  for(int x=89;x<=110;x++)for(int z=-121;z<=121;z++)if(x==89||x==110||Math.abs(z)==121)for(int y=-3;y<0;y++)l.setBlock(base.offset(x,y,z),Blocks.DIRT.defaultBlockState(),2);
  var start=base.offset(2,1,0);var finish=base.offset(180,1,0);var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->!(g instanceof FloatGoal));npc.targetSelector.removeAllGoals(g->true);int[] leg={0};var destinations=new BlockPos[]{finish,start};
  var original=npc.getNavigation().getPath();var planned=npc.routeTo(finish,0,NaturalSupplyGoal.ROUTE_RANGE);
  h.assertTrue(HarvestAccess.reversible(planned),"Distant river route must reach dry ground with reversible steps, got "+(planned==null?null:planned.getEndNode()));
  h.assertTrue(npc.getNavigation().getPath()==original,"Planning does not replace the active navigation");
  npc.goalSelector.addGoal(5,new Goal(){ {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%40==0){var route=npc.routeTo(destinations[leg[0]],0,NaturalSupplyGoal.ROUTE_RANGE);npc.getNavigation().moveTo(route,.8);}}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(start),"Entity chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Resident added"));
  h.onEachTick(()->{h.assertTrue(npc.isAlive()&&npc.getAirSupply()>0,"Physical crossing must not drown the worker");if(npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(destinations[leg[0]]))>.5||!npc.onGround())return;
   if(leg[0]==0){leg[0]=1;return;}
   h.assertTrue(l.getBlockState(base.offset(100,0,0)).is(Blocks.WATER)&&l.getBlockState(base.offset(100,-4,0)).is(Blocks.STONE),"River and bed remain intact");npc.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(6800,()->h.assertTrue(false,"River crossing stalled on leg "+leg[0]+" at "+npc.position()+" ticks="+npc.tickCount+" goals="+npc.runningGoals()));
 }
}
