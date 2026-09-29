package org.villageastra.gametest;
import java.util.EnumSet;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class GoalReplacementGameTests {
 private static final class Move extends Goal {
  boolean started,stopped;
  Move(){setFlags(EnumSet.of(Flag.MOVE));}
  public boolean canUse(){return true;}
  public boolean canContinueToUse(){return true;}
  public void start(){started=true;}
  public void stop(){stopped=true;}
 }
 @GameTest(template="empty",batch="goal_replacement",timeoutTicks=100) public static void replacedRunningGoalReleasesMovement(GameTestHelper h){
  var r=VillageAstra.RESIDENT.get().create(h.getLevel());var p=h.absolutePos(new net.minecraft.core.BlockPos(2,3,2));r.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);r.setNoGravity(true);
  var first=new Move();var next=new Move();r.onlyGoals(g->false,5,first);h.getLevel().addFreshEntity(r);
  h.startSequence().thenWaitUntil(()->h.assertTrue(first.started,"First movement goal started"))
   .thenExecute(()->r.onlyGoals(g->false,5,next))
   .thenWaitUntil(()->h.assertTrue(first.stopped&&next.started,"Removed running goal releases MOVE for its replacement"))
   .thenExecute(r::discard).thenSucceed();
 }
}
