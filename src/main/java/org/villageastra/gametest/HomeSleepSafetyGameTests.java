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
/** Actual naturally built home's terrain, copied read-only; no live-world changes. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HomeSleepSafetyGameTests {
 @GameTest(template="empty",batch="home_sleep_safety",timeoutTicks=4800)
 public static void aMinerWalksHomeWithoutClimbingItsRoofOrTakingFallDamage(GameTestHelper h){room(h,false,false);}
 @GameTest(template="empty",batch="home_sleep_safety",timeoutTicks=4800)
 public static void aMinerAlreadyOnTheObservedPorchRoofGetsSafelyToBed(GameTestHelper h){room(h,true,false);}
 @GameTest(template="empty",batch="home_sleep_safety",timeoutTicks=4800)
 public static void aRestingSickResidentDoesNotClimbOutOfItsOwnPorch(GameTestHelper h){room(h,false,true);}
 private static void room(GameTestHelper h,boolean roof,boolean resting){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(resting?176128:roof?172032:167936),90,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();var owner=UUID.randomUUID();var ticket=net.minecraft.server.level.TicketType.<UUID>create("zimbovillagers_home_sleep_fixture",Comparator.naturalOrder());
  for(int x=(base.getX()-15)>>4;x<=(base.getX()+14)>>4;x++)for(int z=(base.getZ()-11)>>4;z<=(base.getZ()+13)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(ticket,cp,3,owner);held.add(cp);l.getChunk(x,z);}
  for(int x=((base.getX()-15)>>4)-2;x<=((base.getX()+14)>>4)+2;x++)for(int z=((base.getZ()-11)>>4)-2;z<=((base.getZ()+13)>>4)+2;z++)l.getChunk(x,z);
  for(var p:BlockPos.betweenClosed(base.offset(-15,-5,-11),base.offset(14,15,13)))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
  try(var in=HomeSleepSafetyGameTests.class.getResourceAsStream("/data/villageastra/fixtures/new_home_sleep_20261005.json")){
   var cells=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();for(var raw:cells){var c=raw.getAsJsonArray();var state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),c.get(3).getAsString(),false).blockState();l.setBlock(base.offset(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),state,2);}
  }catch(Exception ex){throw new RuntimeException(ex);}
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var home=UUID.randomUUID();s.addBuilding(new Settlement.Building(home,"home",-3,-1,-1));s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,Profession.MINER,null,-1);s.admit(r,home);if(resting)r.restoreSick(true);npc.bind(s.id(),r);var support=base.offset(-1,4,0);double roofY=base.getY()+4+l.getBlockState(support).getCollisionShape(l,support).max(net.minecraft.core.Direction.Axis.Y);npc.moveTo(base.getX()+(roof?-.5:.5),roof?roofY:base.getY(),base.getZ()+.5);npc.setOnGround(true);h.assertTrue(l.noCollision(npc,npc.getBoundingBox()),"Observed starting body is clear of furniture");h.assertTrue(!l.getBlockState(npc.blockPosition().below()).getCollisionShape(l,npc.blockPosition().below()).isEmpty(),"Observed starting position has a real support");
  npc.onlyGoals(g->g instanceof PitEscapeGoal||g instanceof SafeDescentGoal||g instanceof BedExitGoal||g instanceof ResidentDoorGoal||g instanceof DoorwayGoal,2,new SleepGoal(npc,true,()->resting?0L:14000L));if(resting)npc.goalSelector.addGoal(5,new PatientGoal(npc));npc.targetSelector.removeAllGoals(g->true);
  h.assertTrue(SleepGoal.bed(l,e,r)!=null,"Observed home has an actual assigned bed");
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Home chunks physically tick")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Miner registered"));
  h.onEachTick(()->{l.resetEmptyTime();h.assertTrue(npc.isAlive()&&npc.getHealth()==npc.getMaxHealth(),"Home return causes fall damage: body="+npc.position()+" health="+npc.getHealth()+" goals="+npc.runningGoals());h.assertTrue(npc.tickCount<4000,"Home return stalled: body="+npc.position()+" goals="+npc.runningGoals()+" escape="+PitEscapeGoal.escape(npc));
   if(resting)h.assertTrue(npc.getY()<base.getY()+1.5,"A resting patient must not climb the porch roof: body="+npc.position()+" goals="+npc.runningGoals());
   // The fixture uses a private night clock; ResidentEntity may stand up in the shared daytime world.
   // Successful real bed activation is enough to verify arrival, not a whole night of rest.
   if(npc.isSleeping()||!resting&&"sleeping".equals(npc.workStatus())||resting&&npc.tickCount>=1200){npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:held)l.getChunkSource().removeRegionTicket(ticket,cp,3,owner);h.succeed();}
  });
 }
}
