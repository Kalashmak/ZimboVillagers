package org.villageastra.gametest;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.server.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ConstructionDraftGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void estimateIsPureAndApprovalUsesTheSamePaidPlan(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"DraftMayor"));p.setPos(origin.getX()+2,origin.getY()+1,origin.getZ()+2);var e=SettlementData.get(l.getServer()).entry(s.id());var chest=(OwnedChestEntity)l.getBlockEntity(origin.offset(1,1,4));var inventory=chest.saveWithFullMetadata();
  h.assertTrue(!ConstructionDrafts.order(p,s.id(),0,0,0,new UUID(0,0)),"Visitor cannot survey a management project");s.appointPlayerMayor(p.getUUID());long epoch=s.governance().epoch();
  var before=BuildingBlueprints.layout("town_hall",origin).keySet().stream().collect(java.util.stream.Collectors.toMap(pos->pos,l::getBlockState));h.assertTrue(ConstructionDrafts.order(p,s.id(),epoch,0,0,new UUID(0,0)),"Mayor can request an estimate");
  var view=ConstructionDrafts.view(p,new CompoundTag());var quoted=HallUpgradeGoal.preview(l,e);h.assertTrue(view.getBoolean("draft")&&view.getBoolean("confirmable")&&!HallUpgradeGoal.exists(l,s.id())&&chest.saveWithFullMetadata().equals(inventory)&&before.entrySet().stream().allMatch(c->l.getBlockState(c.getKey()).equals(c.getValue())),"Survey creates no queue, changes no block and debits nothing");
  var token=view.getUUID("id");h.assertTrue(ConstructionDrafts.order(p,s.id(),epoch,0,1,token),"Explicit approval queues work");var accepted=HallUpgradeGoal.inspect(l,s.id());h.assertTrue(accepted.getUUID("id").equals(token)&&accepted.get("ops").equals(quoted.get("ops"))&&accepted.get("cost").equals(quoted.get("cost"))&&!accepted.getBoolean("funded"),"Exact quoted operations enter existing paid queue without free funding");h.assertTrue(!ConstructionDrafts.order(p,s.id(),epoch,0,1,token),"Single-use approval cannot replay");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void changedTerrainRangeAndRevokedOfficeRejectApproval(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"DraftSafety"));p.setPos(origin.getX()+2,origin.getY()+1,origin.getZ()+2);s.appointPlayerMayor(p.getUUID());long epoch=s.governance().epoch();ConstructionDrafts.order(p,s.id(),epoch,0,0,new UUID(0,0));var token=ConstructionDrafts.view(p,new CompoundTag()).getUUID("id");
  p.setPos(origin.getX()+30,origin.getY()+1,origin.getZ());h.assertTrue(!ConstructionDrafts.order(p,s.id(),epoch,0,1,token),"Approval requires physical proximity to hall");p.setPos(origin.getX()+2,origin.getY()+1,origin.getZ()+2);
  var plan=HallUpgradeGoal.preview(l,SettlementData.get(l.getServer()).entry(s.id()));var cell=HallConstructionPlan.step(plan.getList("ops",10).getCompound(0));l.setBlock(cell.pos(),Blocks.DIAMOND_BLOCK.defaultBlockState(),3);h.assertTrue(!ConstructionDrafts.order(p,s.id(),epoch,0,1,token)&&!HallUpgradeGoal.exists(l,s.id()),"Changed terrain cannot silently change the quoted plan");l.setBlock(cell.pos(),cell.before(),3);
  s.appointPlayerMayor(UUID.randomUUID());h.assertTrue(!ConstructionDrafts.order(p,s.id(),epoch,0,1,token)&&!ConstructionDrafts.view(p,new CompoundTag()).getBoolean("draft"),"Loss of authority invalidates draft and confirmation");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void cancelAndExpiryNeverCreateWork(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"DraftCancel"));p.setPos(origin.getX(),origin.getY()+1,origin.getZ());s.appointPlayerMayor(p.getUUID());long epoch=s.governance().epoch();var data=SettlementData.get(l.getServer());ConstructionDrafts.order(p,s.id(),epoch,0,0,new UUID(0,0));var token=ConstructionDrafts.view(p,new CompoundTag()).getUUID("id");h.assertTrue(ConstructionDrafts.order(p,s.id(),epoch,0,2,token)&&!ConstructionDrafts.order(p,s.id(),epoch,0,1,token),"Cancelled draft cannot approve");ConstructionDrafts.order(p,s.id(),epoch,0,0,new UUID(0,0));token=ConstructionDrafts.view(p,new CompoundTag()).getUUID("id");for(int i=0;i<1200;i++)data.activeTick(true);h.assertTrue(!ConstructionDrafts.order(p,s.id(),epoch,0,1,token)&&!HallUpgradeGoal.exists(l,s.id()),"Expired survey never creates work");h.succeed();
 }
}
