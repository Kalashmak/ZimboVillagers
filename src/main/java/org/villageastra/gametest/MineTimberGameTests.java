package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineTimberGameTests {
 private record Town(ServerLevel l,SettlementData.Entry e,Settlement.Building mine,ResidentEntity npc,OwnedChestEntity stock,BlockPos chest,CompoundTag state){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var village=StarterVillage.create(l,origin);var e=SettlementData.get(l.getServer()).entry(village.id());
  var r=village.residents().stream().filter(x->x.profession()==Profession.MINER).findFirst().orElseThrow();var npc=(ResidentEntity)l.getEntity(r.id());if(npc==null){npc=VillageAstra.RESIDENT.get().create(l);npc.bind(village.id(),r);}npc.setNoAi(true);var mine=village.workplace(r.id());
  var hall=Workshops.hall(e);var chest=LogisticsRoutes.position(e,hall);var stock=LogisticsRoutes.chest(l,e,hall);stock.clearContent();
  var state=new CompoundTag();state.putInt("schema",1);state.putInt("width",3);state.putInt("height",4);state.putInt("step",4);state.putInt("cell",0);state.putUUID("worker",r.id());state.putUUID("operation",UUID.randomUUID());state.putString("stage","support_fetch");state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));MineWork.write(l,mine,state);
  for(var cell:MineWork.beam(state).cells())l.setBlock(MineWork.at(e,mine,cell),Blocks.AIR.defaultBlockState(),2);
  return new Town(l,e,mine,npc,stock,chest,state);
 }
 private static ResourceWorkGoal resume(GameTestHelper h,Town t){var goal=new ResourceWorkGoal(t.npc,true,()->6000);h.assertTrue(goal.canUse(),"Saved support order resumes");goal.start();return goal;}
 private static int count(ListTag items,Item item){int n=0;for(var raw:items){var stack=ItemStack.of((CompoundTag)raw);if(stack.is(item))n+=stack.getCount();}return n;}
 private static void requestClay(GameTestHelper h,Town t){
  var demand=new CompoundTag();demand.putString("stage","idle");var needs=new ListTag();var need=new CompoundTag();need.putString("ingredient","{\"item\":\"minecraft:clay_ball\"}");need.putInt("count",1);needs.add(need);demand.put("needs",needs);
  org.villageastra.persistence.NbtRecord.write(Workshops.path(t.l,Workshops.hall(t.e).id()),demand);
  h.assertTrue(NaturalSupplyGoal.demand(t.l,t.e).contains(Items.CLAY_BALL),"A separate supply request exists");
 }
 @GameTest(template="empty",batch="mine_timber",timeoutTicks=200)
 public static void mixedLocalTimberPaysActualBeamAndReplaysWithoutChangingSpecies(GameTestHelper h){
  var t=town(h);t.stock.setItem(0,new ItemStack(Items.BIRCH_LOG,2));t.stock.setItem(1,new ItemStack(Items.SPRUCE_LOG,2));
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:birch_log",1);project.put("cost",cost);HallUpgradeGoal.store(t.l,t.e.settlement().id(),project);
  t.npc.moveTo(t.chest.getX()+1.5,t.chest.getY(),t.chest.getZ()+.5);var goal=resume(h,t);goal.tick();
  h.assertTrue(t.stock.countItem(Items.BIRCH_LOG)==1,"The construction-reserved birch log stays in the hall");
  // Committed take, old work checkpoint: only the same paid birch is recovered.
  MineWork.write(t.l,t.mine,t.state);goal=resume(h,t);goal.tick();
  h.assertTrue(count(MineTimber.stored(MineWork.read(t.l,t.mine)),Items.BIRCH_LOG)==1&&t.stock.countItem(Items.SPRUCE_LOG)==2,"Pending withdrawal recovers without taking another species");
  for(int i=0;i<3&&MineWork.read(t.l,t.mine).getString("stage").equals("support_fetch");i++)goal.tick();
  var paid=MineWork.read(t.l,t.mine);h.assertTrue(paid.getString("stage").equals("support_place")&&count(MineTimber.stored(paid),Items.SPRUCE_LOG)==2,"Mixed real logs fully fund the beam");
  requestClay(h,t);
  h.assertTrue(!new NaturalSupplyGoal(t.npc,true).canUse(),"A new raw-material trip cannot abandon paid beam placement");
  var cells=MineWork.beam(paid).cells();var stand=MineWork.at(t.e,t.mine,MineWork.beamStand(paid));t.npc.moveTo(stand.getX()+.5,stand.getY()+1,stand.getZ()+.5);goal.tick();
  h.assertTrue(t.l.getBlockState(MineWork.at(t.e,t.mine,cells.get(0))).is(Blocks.BIRCH_LOG),"The installed block is the actual paid birch");
  // A death snapshot while placement is ahead of the saved checkpoint must keep only spruce.
  MineWork.write(t.l,t.mine,paid);var custody=JobCargo.snapshot(t.npc,true);
  h.assertTrue(count(custody.items(),Items.SPRUCE_LOG)==2&&count(custody.items(),Items.BIRCH_LOG)==0&&count(custody.items(),Items.OAK_LOG)==0,"Custody excludes the installed log and preserves the remaining species");
  goal=resume(h,t);for(int i=0;i<4&&MineWork.read(t.l,t.mine).getString("stage").equals("support_place");i++)goal.tick();
  h.assertTrue(t.l.getBlockState(MineWork.at(t.e,t.mine,cells.get(1))).is(Blocks.SPRUCE_LOG)&&t.l.getBlockState(MineWork.at(t.e,t.mine,cells.get(2))).is(Blocks.SPRUCE_LOG),"Replayed placement finishes with two paid spruce blocks");
  h.assertTrue(t.stock.countItem(Items.BIRCH_LOG)==1&&t.stock.countItem(Items.SPRUCE_LOG)==0&&!MineWork.read(t.l,t.mine).contains("supportTimber"),"No duplicate withdrawal, conversion or forgotten beam cargo");
  h.assertTrue(!NaturalSupplyGoal.primaryResourcePending(t.l,t.e,t.npc),"Finished beam releases the miner for subsequent supply decisions");h.succeed();
 }
 @GameTest(template="empty",batch="mine_timber_demand",timeoutTicks=200)
 public static void supportDemandAcceptsLocalSpeciesInsteadOfRequiringOak(GameTestHelper h){
  var t=town(h);var wants=WorkerSupplies.wants(t.l,t.e,t.mine.id());
  h.assertTrue(wants.stream().anyMatch(w->w.count()==3&&w.matches(new ItemStack(Items.BIRCH_LOG))&&w.matches(new ItemStack(Items.SPRUCE_LOG))),"A three-log beam requests available local species");h.succeed();
 }
 @GameTest(template="empty",batch="mine_paid_delivery",timeoutTicks=200)
 public static void harvestedCargoReachesMineStockBeforeAnotherSupplyTrip(GameTestHelper h){
  var t=town(h);var target=BuildingPlacement.origin(t.e,t.mine).offset(20,2,20);var before=Blocks.STONE.defaultBlockState();t.l.setBlock(target,before,2);
  var id=t.state.getUUID("operation");org.villageastra.persistence.WorldJournal.harvest(t.l,id,target,before,new ItemStack(Items.STONE_PICKAXE));
  var receipt=org.villageastra.persistence.WorldJournal.recoverExisting(t.l,id);var loot=receipt.getList("loot",Tag.TAG_COMPOUND).copy();
  h.assertTrue(t.l.getBlockState(target).isAir()&&count(loot,Items.COBBLESTONE)==1,"One actual stone block funds the carried cobblestone");
  t.state.putString("stage","deliver");t.state.put("cargo",loot);MineWork.write(t.l,t.mine,t.state);requestClay(h,t);
  h.assertTrue(!new NaturalSupplyGoal(t.npc,true).canUse(),"A new supply request waits for carried loot delivery");
  var output=BuildingPlacement.at(t.e,t.mine,1,1,4);var chest=(net.minecraft.world.Container)t.l.getBlockEntity(output);int initial=chest.countItem(Items.COBBLESTONE);
  t.npc.moveTo(output.getX()+1.5,output.getY(),output.getZ()+.5);var goal=resume(h,t);goal.tick();
  h.assertTrue(chest.countItem(Items.COBBLESTONE)==initial+1&&!MineWork.read(t.l,t.mine).contains("cargo"),"The worker deposits its real harvested cargo");
  MineWork.write(t.l,t.mine,t.state);goal=resume(h,t);goal.tick();
  h.assertTrue(chest.countItem(Items.COBBLESTONE)==initial+1,"Replaying the old delivery checkpoint cannot duplicate loot");h.succeed();
 }
}
