package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkerSupplyGameTests {
 @GameTest(template="empty") public static void minerCarriesOwnOutputAndRequestsCorrectToolTier(GameTestHelper h){
  var l=h.getLevel();var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var mine=new Settlement.Building(UUID.randomUUID(),"mine",16,0,0);s.addBuilding(hall);s.addBuilding(mine);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  for(var b:List.of(hall,mine))l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var source=LogisticsRoutes.chest(l,e,mine);var dest=LogisticsRoutes.chest(l,e,hall);source.setItem(0,new ItemStack(Items.COBBLESTONE,2));dest.setItem(0,new ItemStack(Items.STONE_PICKAXE));dest.setItem(1,new ItemStack(Items.BREAD,64));
  var order=new net.minecraft.nbt.CompoundTag();order.putUUID("id",UUID.randomUUID());var cost=new net.minecraft.nbt.CompoundTag();cost.putInt("minecraft:cobblestone",2);order.put("cost",cost);HallUpgradeGoal.enqueue(l,e,order);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.setNoAi(true);l.addFreshEntity(npc);var route=LogisticsRoutes.workerRoute(l,e,mine);h.assertTrue(route!=null&&route.item().is(Items.COBBLESTONE)&&route.destination().equals(hall),"Miner exports needed output without a porter profession");
  var at=LogisticsRoutes.position(e,mine);npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);PorterWork.step(npc,route,true);PorterWork.step(npc,route,true);
  var trip=PorterWork.inspect(l,npc.getUUID());h.assertTrue(source.isEmpty()&&dest.countItem(Items.COBBLESTONE)==0&&PorterWork.cargo(l,trip).getCount()==2,"Two real blocks are in transit, not remotely delivered");
  var dropped=JobCargo.snapshot(npc,true);h.assertTrue(dropped.items().size()==1&&ItemStack.of(dropped.items().getCompound(0)).getCount()==2,"Death custody includes the self-supply parcel");
  at=LogisticsRoutes.position(e,hall);npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);PorterWork.step(npc,null,true);PorterWork.step(npc,null,true);h.assertTrue(dest.countItem(Items.COBBLESTONE)==2&&!PorterWork.active(PorterWork.inspect(l,npc.getUUID())),"Arrival delivers the blocks exactly once");var state=MineWork.read(l,mine);state.put("requiredToolState",net.minecraft.nbt.NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.GOLD_ORE.defaultBlockState()));state.putString("stage","tool");MineWork.write(l,mine,state);h.assertTrue(WorkerSupplies.wants(l,e).stream().anyMatch(w->w.matches(new ItemStack(Items.IRON_PICKAXE))&&w.destination().equals(hall.id())),"Gold ore requests an iron pickaxe at the shared hall, despite the inadequate stone tool there");h.succeed();
 }
}
