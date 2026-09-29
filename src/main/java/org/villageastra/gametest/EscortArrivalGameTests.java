package org.villageastra.gametest;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.EscortGoal;
/** Arrival hysteresis must not accept a single transient crossing followed by continued movement away. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class EscortArrivalGameTests {
 @GameTest(template="empty",batch="escort_arrival") public static void escortArrivalReplansAfterInitialDrift(GameTestHelper h){
  var l=h.getLevel();for(int x=1;x<=14;x++)for(int z=1;z<=14;z++){h.setBlock(new BlockPos(x,3,z),Blocks.STONE);for(int y=4;y<=7;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);}
  var owner=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"ArrivalOwner"));owner.setGameMode(GameType.SURVIVAL);var at=h.absolutePos(new BlockPos(10,4,8));owner.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);EscortGoal.TEST_OWNERS.put(owner.getUUID(),owner);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.setNoAi(true);npc.escort(owner.getUUID());npc.moveTo(owner.getX()-2.9,owner.getY(),owner.getZ(),0,0);npc.setOnGround(true);l.addFreshEntity(npc);
  try{
   var goal=new EscortGoal(npc);goal.start();goal.tick();
   // Controlled fixture movement stands for the remaining physics step after the first arrival check.
   npc.setPos(owner.getX()-3.15,owner.getY(),owner.getZ());npc.setOnGround(true);goal.tick();
   h.assertTrue(npc.getNavigation().isInProgress(),"A first near sample followed by drift must resume the approach, not settle outside three blocks");
  }finally{EscortGoal.TEST_OWNERS.remove(owner.getUUID());npc.discard();}h.succeed();
 }
}
