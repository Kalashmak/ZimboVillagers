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
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HomeRestRouteGameTests {
 @GameTest(template="empty",batch="home_rest_route",timeoutTicks=2400)
 public static void childPlansSafeStepsAndPhysicallyReachesItsOwnChair(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+720896,120,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-4,34,-6,24);
  for(int x=-4;x<=34;x++)for(int z=-6;z<=24;z++)for(int y=-1;y<=9;y++){
   int top=x<=5&&z<=18?5:x==6&&z<16?2:x>=6&&x<=10&&z>=16&&z<=18?10-x:-1;
   l.setBlock(base.offset(x,y,z),(y<=top?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));s.addBuilding(new Settlement.Building(home,"home",18,-1,0));SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.CHILD,false,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.setHealth(20);npc.moveTo(base.getX()+2.5,base.getY()+6,base.getZ()+.5,0,0);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(5,new HomeRestGoal(npc,()->11000L).eager(600));var destination=base.offset(21,0,4);l.setBlock(destination,VillageAstra.CHAIRS.get("birch").get().defaultBlockState(),2);
  npc.setOnGround(false);h.assertTrue(!new HomeRestGoal(npc,()->11000L).eager(600).canUse(),"A falling child must not start an idle chair journey");npc.setOnGround(true);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Patient chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{SeatEntity.stand(npc);npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  boolean[] tookSteps={false};
  h.onEachTick(()->{
   var path=npc.getNavigation().getPath();
   if(npc.tickCount>5&&!npc.onGround()&&!npc.isPassenger()&&npc.getX()>=base.getX()+6&&npc.getX()<=base.getX()+11){tookSteps[0]=true;h.assertTrue(path!=null&&!npc.getNavigation().isDone(),"Safe chair path remains active during the actual step down");}
   if(path!=null)for(int i=1;i<path.getNodeCount();i++)if(Math.abs(path.getNode(i).y-path.getNode(i-1).y)>1){String why="Child chair route planned a non-reversible cliff descent: "+path.getNode(i-1)+" -> "+path.getNode(i);clean.run();h.assertTrue(false,why);return;}
   if(npc.getHealth()!=20){String why="Child lost health on chair route: pos="+npc.position()+" HP="+npc.getHealth();clean.run();h.assertTrue(false,why);return;}
   if(!SeatEntity.seated(npc))return;
   h.assertTrue(tookSteps[0]&&npc.isAlive()&&r.life()==Resident.Life.CHILD,"Actual child walks to and sits on its own chair without aging or healing");clean.run();h.succeed();
  });
  h.runAtTickTime(2350,()->{String why="Child never reached its chair: "+npc.position();clean.run();h.assertTrue(false,why);});
 }
}
