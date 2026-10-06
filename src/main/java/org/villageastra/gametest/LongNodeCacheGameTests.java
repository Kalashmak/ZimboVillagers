package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LongNodeCacheGameTests {
 @GameTest(template="empty",batch="long_node_cache",timeoutTicks=100)
 public static void anUnwalkableHorizontalJumpIsNotAnExtractionRoute(GameTestHelper h){
  var a=new Node(-2385,64,-2371);var b=new Node(-2385,64,-2243);var path=new Path(List.of(a,b),b.asBlockPos(),true);
  h.assertTrue(!HarvestAccess.reversible(path),"An unchanged y does not make a 128-block horizontal jump physically walkable");h.succeed();
 }
 @GameTest(template="empty",batch="long_node_cache_walk",timeoutTicks=4800)
 public static void negativeCoordinatesKeepDistinctNodesAndWalkAroundAWallBothWays(GameTestHelper h){
  var l=h.getLevel();var base=new BlockPos(-262144-2385,90,-262144-2371);var end=base.south(128);var held=new ArrayList<net.minecraft.world.level.ChunkPos>();var owner=UUID.randomUUID();var ticket=net.minecraft.server.level.TicketType.<UUID>create("zimbovillagers_long_node_fixture",Comparator.naturalOrder());
  for(int x=(base.getX()-5)>>4;x<=(base.getX()+5)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+131)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(ticket,cp,3,owner);held.add(cp);l.getChunk(x,z);}
  for(int x=((base.getX()-5)>>4)-2;x<=((base.getX()+5)>>4)+2;x++)for(int z=((base.getZ()-3)>>4)-2;z<=((base.getZ()+131)>>4)+2;z++)l.getChunk(x,z);
  for(var p:BlockPos.betweenClosed(base.offset(-5,-1,-3),base.offset(5,4,131)))l.setBlock(p,p.getY()==base.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  for(int x=-5;x<=2;x++)for(int y=0;y<3;y++)l.setBlock(base.offset(x,y,64),Blocks.STONE.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY(),base.getZ()+.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  h.assertTrue(Node.createHash(base.getX(),base.getY(),base.getZ())==Node.createHash(end.getX(),end.getY(),end.getZ()),"Actual negative-coordinate points reproduce vanilla's int-key alias");
  var planned=npc.routeTo(end,0,NaturalSupplyGoal.ROUTE_RANGE);h.assertTrue(planned!=null&&planned.canReach()&&planned.getNodeCount()>=129,"Expanded route must retain the real 128-block walk, rather than an aliased single node: "+(planned==null?null:planned.getNodeCount()));h.assertTrue(planned.getNode(0).asBlockPos().distSqr(base)<=2&&HarvestAccess.reversible(planned),"Start is near the real body and every edge is walkable");
  int[] leg={0};npc.goalSelector.addGoal(5,new Goal(){ {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%20==0)npc.getNavigation().moveTo(npc.routeTo(leg[0]==0?end:base,0,NaturalSupplyGoal.ROUTE_RANGE),.8);}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base)&&l.isPositionEntityTicking(end),"Both ends tick physically")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Walking actor registered"));
  h.onEachTick(()->{l.resetEmptyTime();h.assertTrue(npc.tickCount<4000,"Real long trip stalled: "+npc.position()+" leg="+leg[0]);h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"No fall, teleport or damage in either direction");var target=leg[0]==0?end:base;
   if(npc.onGround()&&npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(target))<.5){if(leg[0]==0){leg[0]=1;npc.getNavigation().stop();}else{npc.discard();for(var cp:held)l.getChunkSource().removeRegionTicket(ticket,cp,3,owner);h.succeed();}}
  });
 }
}
