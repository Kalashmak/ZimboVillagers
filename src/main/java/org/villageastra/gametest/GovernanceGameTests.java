package org.villageastra.gametest;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.server.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class GovernanceGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void serverRejectsVisitorStaleAndRemoteOrders(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var data=SettlementData.get(l.getServer());HallUpgradeGoal.request(l,data.entry(s.id()));var project=HallUpgradeGoal.inspect(l,s.id()).getUUID("id");
  var mayor=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"OfficeMayor"));mayor.setPos(origin.getX(),origin.getY()+1,origin.getZ());var visitor=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"OfficeVisitor"));visitor.setPos(mayor.position());s.appointPlayerMayor(mayor.getUUID());long epoch=s.governance().epoch();
  h.assertTrue(!ManagementOrders.pause(visitor,s.id(),project,epoch,0,true),"Visitor cannot mutate via direct request");h.assertTrue(ManagementOrders.pause(mayor,s.id(),project,epoch,0,true),"Mayor can pause existing project");h.assertTrue(!ManagementOrders.pause(mayor,s.id(),project,epoch,0,false),"Replay revision rejected");
  var saved=data.save(new CompoundTag());var restored=SettlementData.load(saved).entry(s.id()).settlement();h.assertTrue(restored.governance().paused(project)&&restored.governance().playerMayor().equals(mayor.getUUID()),"Office and order preserved together");
  s.appointPlayerMayor(visitor.getUUID());h.assertTrue(!ManagementOrders.pause(mayor,s.id(),project,epoch,1,false),"Loss of office rejects an old open-window request immediately");
  visitor.setPos(origin.getX()+100,origin.getY(),origin.getZ());h.assertTrue(!ManagementOrders.pause(visitor,s.id(),project,s.governance().epoch(),1,false),"Remote mayor rejected");h.assertTrue(s.governance().paused(project),"Rejections do not unpause");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void schemaFiveMigratesWithoutResettingKnowledgeOrInventory(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var data=SettlementData.get(l.getServer());s.restoreCivilization(org.villageastra.domain.Civilization.restore(2,List.of("cartography","ironworking"),"medicine",400));var record=data.save(new CompoundTag());record.putInt("schema",5);for(var raw:record.getList("settlements",Tag.TAG_COMPOUND))((CompoundTag)raw).remove("governance");
  var migrated=SettlementData.load(record).entry(s.id()).settlement();h.assertTrue(migrated.residents().size()==6&&new ArrayList<>(migrated.buildings()).equals(new ArrayList<>(s.buildings())),"Residents retained");
  h.assertTrue(migrated.governance().playerMayor()==null&&migrated.governance().epoch()==0&&migrated.governance().pausedProjects().isEmpty(),"Old save defaults to NPC office with no invented orders");
  h.assertTrue(migrated.residents().stream().filter(r->r.profession()==org.villageastra.domain.Profession.MAYOR).count()==1,"Original NPC mayor retained");h.assertTrue(migrated.civilization().level()==s.civilization().level()&&migrated.buildings().size()==s.buildings().size(),"Buildings and hall level preserved");h.assertTrue(migrated.civilization().completed().equals(s.civilization().completed())&&migrated.civilization().active().equals("medicine")&&migrated.civilization().progress()==400,"Paid knowledge and partial progress preserved without payment");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void builderDeathDoesNotDiscardMayorPause(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);HallUpgradeGoal.request(l,SettlementData.get(l.getServer()).entry(s.id()));var state=HallUpgradeGoal.inspect(l,s.id());var project=HallConstructionPlan.projectId(state);var oldTransaction=state.getUUID("id");
  var mayor=UUID.randomUUID();s.appointPlayerMayor(mayor);s.governance().setPaused(mayor,s.governance().epoch(),0,project,true);
  var builder=s.residents().stream().filter(r->r.profession()==org.villageastra.domain.Profession.BUILDER).findFirst().orElseThrow();state.putUUID("worker",builder.id());state.remove("project");
  org.villageastra.persistence.NbtRecord.write(l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+s.id()+".bin"),state);
  var worker=(ResidentEntity)l.getEntity(builder.id());worker.hurt(l.damageSources().genericKill(),1000);CargoCustody.tick(l.getServer());var reset=HallUpgradeGoal.inspect(l,s.id());
  h.assertTrue(!reset.getUUID("id").equals(oldTransaction)&&HallConstructionPlan.projectId(reset).equals(project)&&s.governance().paused(HallConstructionPlan.projectId(reset)),"Funding owner can change without losing the durable project order, including a legacy queue");h.succeed();
 }

}
