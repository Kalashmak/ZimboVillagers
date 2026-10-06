package org.villageastra.gametest;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** Abundant grain must leave storage room for construction, without blocking explicit needs. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FoodStockHeadroomGameTests {
 @GameTest(template="empty",batch="food_stock_headroom",timeoutTicks=200)
 public static void surplusGrainLeavesRoomButPaidConstructionAndScarceFoodStillTravel(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),at);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var farm=new Settlement.Building(UUID.randomUUID(),"farm",20,0,0);s.addBuilding(hall);s.addBuilding(farm);SettlementData.get(l.getServer()).add(e);
  for(var b:s.buildings())l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var stock=LogisticsRoutes.chest(l,e,hall);stock.expandHall();var source=LogisticsRoutes.chest(l,e,farm);
  for(int i=0;i<97;i++)stock.setItem(i,new ItemStack(Items.DIRT,64));stock.setItem(0,new ItemStack(Items.WHEAT,64));stock.setItem(1,new ItemStack(Items.WHEAT,64));stock.setItem(2,new ItemStack(Items.BREAD,64));
  for(int i=0;i<17;i++)source.setItem(i,new ItemStack(Items.WHEAT,64));
  h.assertTrue(LogisticsRoutes.choose(l,e,hall,at)==null,"Abundant grain must not use the final eleven free slots");
  var project=new CompoundTag();var id=UUID.randomUUID();project.putUUID("id",id);project.putUUID("project",id);project.putString("design","home");project.putLong("origin",at.offset(60,0,0).asLong());var cost=new CompoundTag();cost.putInt("minecraft:wheat",200);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var route=LogisticsRoutes.choose(l,e,hall,at);h.assertTrue(route!=null&&route.item().is(Items.WHEAT),"Explicit construction demand still carries grain through reserved headroom");HallUpgradeGoal.drop(l,s.id());
  stock.setItem(0,new ItemStack(Items.DIRT,64));stock.setItem(1,new ItemStack(Items.DIRT,64));
  route=LogisticsRoutes.choose(l,e,hall,at);h.assertTrue(route!=null&&route.item().is(Items.WHEAT),"Scarce staple can still refill a stack when storage is tight");
  SettlementData.get(l.getServer()).remove(s.id());h.succeed();
 }
 @GameTest(template="empty",batch="food_stock_headroom_guards",timeoutTicks=200)
 public static void protectedGrainAndFullFarmsNeverBecomeEmergencyExports(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),at);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var farm=new Settlement.Building(UUID.randomUUID(),"farm",20,0,0);s.addBuilding(hall);s.addBuilding(farm);SettlementData.get(l.getServer()).add(e);
  for(var b:s.buildings())l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.expandHall();var dest=LogisticsRoutes.chest(l,e,farm);
  for(int slot=0;slot<108;slot++)stock.setItem(slot,new ItemStack(slot<16?Items.WHEAT:Items.DIRT,64));dest.setItem(0,new ItemStack(Items.WHEAT_SEEDS,8));
  var project=new CompoundTag();var id=UUID.randomUUID();project.putUUID("id",id);project.putUUID("project",id);project.putString("design","home");project.putLong("origin",at.offset(60,0,0).asLong());var cost=new CompoundTag();cost.putInt("minecraft:wheat",1024);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  h.assertTrue(LogisticsRoutes.choose(l,e,hall,at)==null,"Approved grain is never exported even when the hall is full");HallUpgradeGoal.drop(l,s.id());
  for(int slot=0;slot<dest.getContainerSize();slot++)dest.setItem(slot,new ItemStack(Items.DIRT,64));
  h.assertTrue(LogisticsRoutes.choose(l,e,hall,at)==null&&stock.countItem(Items.WHEAT)==1024,"Full farms refuse overflow without changing stock");
  dest.setItem(0,ItemStack.EMPTY);var route=LogisticsRoutes.choose(l,e,hall,at);h.assertTrue(route!=null&&route.source().id().equals(hall.id())&&route.destination().id().equals(farm.id())&&route.item().is(Items.WHEAT),"Free loaded farm receives only actual surplus grain");
  for(int slot=0;slot<16;slot++)stock.setItem(slot,new ItemStack(Items.DIRT,64));for(int slot=0;slot<4;slot++)stock.setItem(slot,new ItemStack(Items.WHEAT,64));
  h.assertTrue(LogisticsRoutes.choose(l,e,hall,at)==null&&stock.countItem(Items.WHEAT)==256,"Four staple stacks remain for village meals");SettlementData.get(l.getServer()).remove(s.id());h.succeed();
 }

}
