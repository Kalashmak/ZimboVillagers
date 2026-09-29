package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PatrolDutyGameTests {
 private record Yard(ServerLevel l,SettlementData.Entry e,Wolf wolf) implements AutoCloseable {
  public void close(){VillageWolves.releaseDog(l,wolf.getUUID());VillageWolves.culled(wolf);wolf.discard();BuildingLevels.forgetBest(e.settlement().id());Atlas.forgetMargins();SettlementData.get(l.getServer()).remove(e.settlement().id());}
 }
 private static Yard yard(GameTestHelper h){var l=h.getLevel();var s=new Settlement(UUID.randomUUID());var c=h.absolutePos(new BlockPos(8,3,8));
  var office=new Settlement.Building(UUID.randomUUID(),"cartographer",0,0,0);s.addBuilding(office);for(int n=2;n<=5;n++)s.raiseBuildingLevel(office.id(),n);
  var farm=new Settlement.Building(UUID.randomUUID(),"livestock",30,0,0);s.addBuilding(farm);var kennel=new Settlement.Building(UUID.randomUUID(),VillageWolves.TYPE,50,0,0);s.addBuilding(kennel);s.linkAnnex(kennel.id(),farm.id());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),c);SettlementData.get(l.getServer()).add(e);
  for(var cell:BuildingPlacement.layout(e,office,BuildingTiers.layoutId("cartographer",5)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  l.setBlock(LogisticsRoutes.position(e,office),VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);BuildingLevels.forgetBest(s.id());
  // Keep the fixture entity inside GameTest's ticking area; the old +24.5 X position crossed its edge in a full batch.
  var spot=h.absolutePos(new BlockPos(3,5,3));var w=EntityType.WOLF.create(l);w.moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,0,0);l.addFreshEntity(w);w.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l));
  h.assertTrue(l.getEntity(w.getUUID())==w,"Fixture wolf is loaded before testing patrol assignment");
  h.assertTrue(VillageWolves.enlist(l,e,w,kennel),"Enlisted fixture wolf");return new Yard(l,e,w);
 }
 @GameTest(template="empty",batch="patrol_duty") public static void cartographerDoesNotTrainDeliveryDog(GameTestHelper h){try(var y=yard(h)){
  var target=y.e.center().east(20);VillageWolves.sendDog(y.l,y.e,y.wolf.getUUID(),target);
  h.assertTrue(CartographyLadder.train(y.l,y.e)==null,"Busy delivery wolf must not become a patrol");
  h.assertTrue(target.equals(VillageWolves.sentTo(y.wolf)),"Delivery destination retained");h.succeed();}}
 @GameTest(template="empty",batch="patrol_duty") public static void trainedPatrolIsNotAFreeDeliveryDog(GameTestHelper h){try(var y=yard(h)){
  h.assertTrue(CartographyLadder.train(y.l,y.e)==y.wolf,"Fed idle wolf trained");
  h.assertTrue(VillageWolves.freeWolf(y.l,y.e)==null&&!VillageWolves.DOGS.free(y.l,y.e).contains(y.wolf.getUUID()),"Patrol reserved from both logistics and culling");h.succeed();}}
 @GameTest(template="empty",batch="patrol_duty") public static void hungryOrSittingWolfIsNotTrained(GameTestHelper h){try(var y=yard(h)){
  y.wolf.getPersistentData().remove(VillageWolves.FED);h.assertTrue(CartographyLadder.train(y.l,y.e)==null,"Hungry wolf waits for meal");
  y.wolf.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(y.l));y.wolf.setOrderedToSit(true);h.assertTrue(CartographyLadder.train(y.l,y.e)==null,"Sitting order respected");h.succeed();}}
 @GameTest(template="empty",batch="patrol_duty") public static void unloadedPatrolKeepsItsJob(GameTestHelper h){try(var y=yard(h)){
  h.assertTrue(CartographyLadder.train(y.l,y.e)==y.wolf,"First trained: loaded="+(y.l.getEntity(y.wolf.getUUID())==y.wolf)+" patrols="+CartographyLadder.patrols(y.l,y.e)+" free="+(VillageWolves.freeWolf(y.l,y.e)==y.wolf)+" at="+y.wolf.blockPosition());var second=EntityType.WOLF.create(y.l);second.moveTo(y.wolf.position());y.l.addFreshEntity(second);
  try{second.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(y.l));h.assertTrue(VillageWolves.enlist(y.l,y.e,second,VillageWolves.kennel(y.e)),"Second wolf enlisted");
   y.wolf.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
   h.assertTrue(CartographyLadder.train(y.l,y.e)==null&&!second.getPersistentData().hasUUID(CartographyLadder.PATROL),"Unloaded patrol does not create a second specialist");
   h.assertTrue(VillageWolves.DOGS.free(y.l,y.e).contains(second.getUUID()),"Other wolf remains available");h.succeed();
  }finally{second.discard();}}}
 @GameTest(template="empty",batch="patrol_duty") public static void oldPatrolMarkDoesNotOverrideExistingDelivery(GameTestHelper h){try(var y=yard(h)){
  y.wolf.getPersistentData().putUUID(CartographyLadder.PATROL,y.e.settlement().id());VillageWolves.sendDog(y.l,y.e,y.wolf.getUUID(),y.e.center().east(20));
  h.assertTrue(CartographyLadder.patrol(y.l,y.e,y.wolf)==null,"Legacy double-assigned dog finishes its delivery first");
  h.assertTrue(!new WolfPatrolGoal(y.wolf).canUse(),"Patrol goal also yields to delivery");h.succeed();}}
}
