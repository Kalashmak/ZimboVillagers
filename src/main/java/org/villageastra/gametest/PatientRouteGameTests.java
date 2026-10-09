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
public final class PatientRouteGameTests {
 @GameTest(template="empty",batch="patient_safe_return",timeoutTicks=2400)
 public static void woundedPatientPlansReversibleStepsAndWalksHomeWithoutDamage(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+622592,120,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-4,34,-6,40);
  for(int x=-4;x<=34;x++)for(int z=-6;z<=40;z++)for(int y=-1;y<=9;y++){
   int top=x<=5&&z<=34?5:x==6&&z<32?2:x>=6&&x<=10&&z>=32&&z<=34?10-x:-1;
   l.setBlock(base.offset(x,y,z),(y<=top?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));s.addBuilding(new Settlement.Building(home,"home",22,-1,0));SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);r.fallIll();npc.bind(s.id(),r);npc.setHealth(2);npc.moveTo(base.getX()+2.5,base.getY()+6,base.getZ()+.5,0,0);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(5,new PatientGoal(npc));var destination=HomeNeighborhood.anchor(npc);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Patient chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{
   var path=npc.getNavigation().getPath();
   if(path!=null)for(int i=1;i<path.getNodeCount();i++)if(Math.abs(path.getNode(i).y-path.getNode(i-1).y)>1){String why="Patient planned a non-reversible cliff descent: "+path.getNode(i-1)+" -> "+path.getNode(i);clean.run();h.assertTrue(false,why);return;}
   if(npc.getHealth()!=2){String why="Wounded patient lost health on route home: pos="+npc.position()+" HP="+npc.getHealth();clean.run();h.assertTrue(false,why);return;}
   if(npc.distanceToSqr(destination.getX()+.5,destination.getY(),destination.getZ()+.5)>16||!npc.getNavigation().isDone())return;
   h.assertTrue(npc.isAlive()&&r.sick(),"Actual safe journey does not grant healing");clean.run();h.succeed();
  });
  h.runAtTickTime(2350,()->{String why="Patient never reached home: "+npc.position();clean.run();h.assertTrue(false,why);});
 }
}
