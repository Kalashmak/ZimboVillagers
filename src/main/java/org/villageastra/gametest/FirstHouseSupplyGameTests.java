package org.villageastra.gametest;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FirstHouseSupplyGameTests {
 private record Town(SettlementData.Entry e,Settlement.Building hall,OwnedChestEntity chest) {}
 private static Town town(GameTestHelper h) {
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var hut=new Settlement.Building(UUID.randomUUID(),"forester",20,0,0);s.addBuilding(hut);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));
  var resident=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(resident,home);s.assign(resident.id(),Profession.FORESTER,hut.id());
  var e=new SettlementData.Entry(s,h.getLevel().dimension().location().toString(),h.absolutePos(new BlockPos(3,3,3)));
  SettlementData.get(h.getLevel().getServer()).add(e);
  h.getLevel().setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  var chest=LogisticsRoutes.chest(h.getLevel(),e,hall);chest.clearContent();return new Town(e,hall,chest);
 }
 private static CompoundTag project() {
  var t=new CompoundTag();t.putUUID("id",UUID.randomUUID());t.put("cargo",new ListTag());
  var cost=new CompoundTag();cost.putInt("minecraft:bookshelf",2);cost.putInt("minecraft:cobblestone",49);t.put("cost",cost);return t;
 }
 @GameTest(template="empty",batch="first_house_supply",timeoutTicks=200)
 public static void availableStoneIsCollectedBeforeMissingBookshelvesAndRecoveryDoesNotDuplicate(GameTestHelper h) {
  var t=town(h);var l=h.getLevel();var state=project();var s=t.e.settlement();
  var npc=VillageAstra.RESIDENT.get().create(l);
  try {
   var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));
   var resident=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(resident,home);s.assign(resident.id(),Profession.BUILDER,t.hall.id());
   npc.bind(s.id(),s.resident(resident.id()));npc.setNoAi(true);l.addFreshEntity(npc);
   var stock=LogisticsRoutes.position(t.e,t.hall);npc.moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);
   t.chest.setItem(0,new ItemStack(Items.COBBLESTONE,60));HallUpgradeGoal.store(l,s.id(),state);
   var missing=ConstructionFunding.missing(state);int slot=ConstructionFunding.slot(t.chest,missing);
   h.assertTrue(slot==0&&!HallUpgradeGoal.waiting(l,t.e),"Missing bookshelves do not hide available construction stone");
   var goal=new HallUpgradeGoal(npc,true);h.assertTrue(goal.canUse(),"Physical funding goal starts with a later material available");npc.tickCount=20;goal.tick();
   state=HallUpgradeGoal.inspect(l,s.id());
   h.assertTrue(state.getInt("withdrawals")==1&&t.chest.countItem(Items.COBBLESTONE)==11,"Builder withdraws only 49 stone, through the normal goal");
   h.assertTrue(!ConstructionFunding.missing(state).containsKey("minecraft:cobblestone")&&ConstructionFunding.slot(t.chest,ConstructionFunding.missing(state))==-1,"Surplus stone is not collected twice after recovery");
   h.assertTrue(HallReserve.reserved(l,t.e,Items.COBBLESTONE)==0&&HallReserve.reserved(l,t.e,Items.BOOKSHELF)==2,"Reserve follows actual cargo, without forgiving unpaid shelves");
   h.assertTrue(!goal.canContinueToUse(),"An unpaid project yields when no needed material is available");
   // Crash window: real shelves removed and journal committed, but project cargo not yet saved.
   t.chest.setItem(1,new ItemStack(Items.BOOKSHELF,2));var id=Settlement.childId(state.getUUID("id"),"fund/1");
   h.assertTrue(WorldJournal.takeAmount(l,id,stock,1,t.chest.getItem(1).copy(),2).getCount()==2,"Two fixture shelves enter journal custody");
   var restarted=new HallUpgradeGoal(npc,true);h.assertTrue(restarted.canUse(),"Committed funding resumes even with the source slot empty");npc.tickCount=40;restarted.tick();
   state=HallUpgradeGoal.inspect(l,s.id());h.assertTrue(state.getInt("withdrawals")==2&&ConstructionFunding.missing(state).isEmpty(),"Recovered shelves are booked exactly once");
   npc.tickCount=60;restarted.tick();state=HallUpgradeGoal.inspect(l,s.id());
   h.assertTrue(state.getBoolean("funded")&&state.getInt("withdrawals")==2&&t.chest.countItem(Items.COBBLESTONE)==11,"Complete payment neither repeats withdrawals nor consumes surplus stone");
  } finally {npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
 @GameTest(template="empty",batch="first_house_supply",timeoutTicks=200)
 public static void foresterUsesThePaidStoneMaintenanceBufferWithoutForgivingConstruction(GameTestHelper h){replacement(h,true);}
 @GameTest(template="empty",batch="first_house_supply",timeoutTicks=200)
 public static void foresterMakesPaidWoodenReplacementWhenStoneIsAbsent(GameTestHelper h){replacement(h,false);}
 private static void replacement(GameTestHelper h,boolean stone){
  var t=town(h);var l=h.getLevel();var s=t.e.settlement();
  try{
   HallUpgradeGoal.store(l,s.id(),project());if(stone)t.chest.setItem(0,new ItemStack(Items.COBBLESTONE,3));
   t.chest.setItem(1,new ItemStack(Items.BIRCH_PLANKS,3));t.chest.setItem(2,new ItemStack(Items.STICK,2));
   var wants=WorkerSupplies.wants(l,t.e);var result=stone?Items.STONE_AXE:Items.WOODEN_AXE;
   h.assertTrue(wants.stream().anyMatch(w->w.matches(new ItemStack(Items.WOODEN_AXE))),"Missing forestry tool retains its wooden fallback");
   for(int i=0;i<1000&&t.chest.countItem(result)==0;i++)Workshops.advance(l,t.e,t.hall,l.getGameTime()+20L*i,wants);
   h.assertTrue(t.chest.countItem(result)==1,"Actual workshop funding, labor and output produce the chosen replacement: "+Workshops.inspect(l,t.hall.id()));
   h.assertTrue(t.chest.countItem(Items.COBBLESTONE)==0&&t.chest.countItem(Items.BIRCH_PLANKS)==(stone?3:0)&&t.chest.countItem(Items.STICK)==0,"Exactly three matching tool materials and two sticks are consumed");
   var order=HallUpgradeGoal.inspect(l,s.id());h.assertTrue(order.getCompound("cost").getInt("minecraft:cobblestone")==49&&!order.getBoolean("funded"),"Maintenance never waives the construction bill");
  }finally{HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
 @GameTest(template="empty",batch="first_house_supply",timeoutTicks=200)
 public static void minerCanBootstrapStoneButCannotRequestWoodForGold(GameTestHelper h) {
  var t=town(h);var l=h.getLevel();var s=t.e.settlement();
  try {
   var mine=new Settlement.Building(UUID.randomUUID(),"mine",40,0,0);s.addBuilding(mine);
   var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());
   var wants=WorkerSupplies.wants(l,t.e,mine.id());
   h.assertTrue(wants.stream().anyMatch(w->w.matches(new ItemStack(Items.WOODEN_PICKAXE))),"A miner can restart ordinary stone mining with a paid wooden pick");
   var state=MineWork.read(l,mine);state.put("requiredToolState",NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.GOLD_ORE.defaultBlockState()));MineWork.write(l,mine,state);
   wants=WorkerSupplies.wants(l,t.e,mine.id());
   h.assertTrue(wants.stream().anyMatch(w->w.matches(new ItemStack(Items.IRON_PICKAXE)))&&wants.stream().noneMatch(w->w.matches(new ItemStack(Items.WOODEN_PICKAXE))),"Ore hardness is preserved: gold needs iron, not the wooden fallback");
  } finally {HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
}
