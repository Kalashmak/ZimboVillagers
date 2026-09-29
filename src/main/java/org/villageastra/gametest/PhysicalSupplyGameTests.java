package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PhysicalSupplyGameTests {
 private record Bench(ServerLevel level,SettlementData.Entry entry,Settlement.Building hall,OwnedChestEntity chest,BlockPos furnace){
  CompoundTag state(){return Workshops.inspect(level,hall.id());}
  String step(long now){return Workshops.advance(level,entry,hall,now,List.of(new Workshops.Want(Ingredient.of(Items.GLASS),2,hall.id())));}
 }
 private static Bench bench(GameTestHelper h,boolean furnace){
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());var b=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(b);var e=new SettlementData.Entry(s,l.dimension().location().toString(),at);SettlementData.get(l.getServer()).add(e);
  var stock=Workshops.station(e,b);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var c=(OwnedChestEntity)l.getBlockEntity(stock);c.setItem(0,new ItemStack(Items.SAND));c.setItem(1,new ItemStack(Items.SAND));c.setItem(2,new ItemStack(Items.COAL));var f=at.offset(4,1,4);if(furnace)l.setBlock(f,Blocks.FURNACE.defaultBlockState(),2);return new Bench(l,e,b,c,f);
 }
 private static int count(Bench b,Item item){return LogisticsRoutes.count(b.chest,s->s.is(item));}
 @GameTest(template="empty") public static void glassNeedsRealFurnaceAndVanillaCooking(GameTestHelper h){
  var b=bench(h,true);long now=1000;for(int i=0;i<12;i++){b.step(now);now+=20;}
  var f=(FurnaceBlockEntity)b.level.getBlockEntity(b.furnace);h.assertTrue(f.getItem(0).is(Items.SAND)&&f.getItem(0).getCount()==2&&f.getItem(1).is(Items.COAL),"Both fragmented raw stacks and real fuel reached furnace slots");
  h.assertTrue(count(b,Items.GLASS)==0&&f.getItem(2).isEmpty(),"Workshop ticks cannot manufacture smelted output");
  var saved=f.saveWithoutMetadata();f.load(saved);for(int i=0;i<405;i++)AbstractFurnaceBlockEntity.serverTick(b.level,b.furnace,b.level.getBlockState(b.furnace),f);
  h.assertTrue(f.getItem(2).is(Items.GLASS)&&f.getItem(2).getCount()==2,"Vanilla furnace produces exactly two glass");
  for(int i=0;i<4&&!b.state().getString("stage").equals("idle");i++){b.step(now);now+=20;}
  h.assertTrue(count(b,Items.GLASS)==2&&count(b,Items.SAND)==0&&count(b,Items.COAL)==0&&f.getItem(2).isEmpty(),"Output returned exactly once, raw and fuel were paid");
  h.assertTrue(NaturalFurnace.availableBurn(b.level,b.entry)>0,"Unused paid fuel remains in real furnace");h.succeed();
 }
 @GameTest(template="empty") public static void missingFurnaceDoesNotConsumeOrCreateGoods(GameTestHelper h){
  var b=bench(h,false);b.step(1000);h.assertTrue(b.step(1020).equals("workshop_missing_furnace"),"Missing physical equipment is reported");h.assertTrue(count(b,Items.SAND)==2&&count(b,Items.COAL)==1&&count(b,Items.GLASS)==0,"Nothing silently smelts or disappears");h.succeed();
 }
 @GameTest(template="empty") public static void waitingFurnaceCanUsePreviouslyPaidLegacyFuel(GameTestHelper h){
  var b=bench(h,true);b.step(1000);var t=b.state();b.chest.clearContent();var f=(FurnaceBlockEntity)b.level.getBlockEntity(b.furnace);f.setItem(0,new ItemStack(Items.SAND,2));t.putString("stage","smelt_wait");t.putLong("furnace",b.furnace.asLong());t.putInt("fuelBank",400);NbtRecord.write(Workshops.path(b.level,b.hall.id()),t);
  b.step(1020);h.assertTrue(b.state().getInt("fuelBank")==0&&f.saveWithoutMetadata().getShort("BurnTime")==401,"The waiting stage transfers its saved paid credit, once");
  for(int tick=0;tick<400;tick++)AbstractFurnaceBlockEntity.serverTick(b.level,b.furnace,b.level.getBlockState(b.furnace),f);
  b.step(1040);b.step(1060);h.assertTrue(count(b,Items.GLASS)==2&&f.getItem(1).isEmpty(),"Saved credit performs actual cooking without a new fuel item");h.succeed();
 }
 @GameTest(template="empty") public static void recoveredOutputIsDeliveredEvenIfFurnaceWasRemoved(GameTestHelper h){
  var b=bench(h,true);b.step(1000);var state=b.state();state.putString("stage","smelt_wait");state.putLong("furnace",b.furnace.asLong());
  var f=(FurnaceBlockEntity)b.level.getBlockEntity(b.furnace);f.setItem(2,new ItemStack(Items.GLASS,2));
  WorldJournal.takeAmount(b.level,Settlement.childId(state.getUUID("id"),"smelt/output"),b.furnace,2,f.getItem(2).copy(),2);
  NbtRecord.write(Workshops.path(b.level,b.hall.id()),state);b.level.setBlock(b.furnace,Blocks.AIR.defaultBlockState(),2);
  b.step(1020);b.step(1040);h.assertTrue(count(b,Items.GLASS)==2&&b.state().getString("stage").equals("idle"),"Committed output remains recoverable without its old furnace");h.succeed();
 }
 @GameTest(template="empty") public static void legacyMineGetsPaidFurnaceWhileConstructionWaits(GameTestHelper h){
  var b=bench(h,false);var mine=new Settlement.Building(UUID.randomUUID(),"mine",12,0,0);b.entry.settlement().addBuilding(mine);var place=BuildingPlacement.at(b.entry,mine,0,1,2);b.level.setBlock(place.below(),Blocks.COBBLESTONE.defaultBlockState(),2);b.level.setBlock(place,Blocks.COBBLESTONE.defaultBlockState(),2);b.chest.setItem(3,new ItemStack(Items.COBBLESTONE,8));
  long now=1000;for(int i=0;i<20;i++){b.step(now);now+=20;}h.assertTrue(b.level.getBlockState(place).is(Blocks.COBBLESTONE)&&count(b,Items.COBBLESTONE)==0&&ItemStack.of(b.state().getCompound("carried")).getCount()==8,"Materials are withdrawn, but crafting takes real work turns");
  for(int i=0;i<200&&b.level.getBlockState(place).is(Blocks.COBBLESTONE);i++){b.step(now);now+=20;}
  h.assertTrue(b.level.getBlockEntity(place) instanceof FurnaceBlockEntity&&count(b,Items.COBBLESTONE)==0&&count(b,Items.GLASS)==0,"Eight cobblestone became a placed furnace, without free glass or waiting for the main building queue");h.succeed();
 }
 @GameTest(template="empty") public static void furnaceClaimAndCargoRecoveryRespectCommittedSlots(GameTestHelper h){
  var b=bench(h,true);b.step(1000);var worker=UUID.randomUUID();h.assertTrue(NaturalFurnace.claim(b.level,b.hall,b.state(),worker),"First worker claims job");h.assertTrue(!NaturalFurnace.claim(b.level,b.hall,b.state(),UUID.randomUUID()),"Second worker cannot carry first worker's job");
  b.step(1020);b.step(1040);var carried=b.state();h.assertTrue(carried.getString("stage").equals("smelt_put_raw"),"Two partial withdrawals form carried input");
  var held=new ListTag();var reset=NaturalFurnace.custody(b.level,carried,held);h.assertTrue(held.size()==1&&ItemStack.of(held.getCompound(0)).getCount()==2&&!reset.hasUUID("worker"),"Death releases exactly the carried raw goods");
  WorldJournal.putSlot(b.level,Settlement.childId(carried.getUUID("id"),"smelt/put_raw"),b.furnace,0,ItemStack.of(carried.getCompound("carried")));
  held=new ListTag();reset=NaturalFurnace.custody(b.level,carried,held);h.assertTrue(held.isEmpty()&&reset.getString("stage").equals("smelt_wait"),"A crash after committed insertion never drops the same sand again");h.succeed();
 }
 @GameTest(template="empty") public static void naturalHarvestReceiptSurvivesReloadWithoutDuplicateCargo(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(2,4,2));l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(p,Blocks.SAND.defaultBlockState(),2);h.assertTrue(NaturalSupplyGoal.safe(l,p),"Exposed natural sand may be gathered");
  var id=UUID.randomUUID();WorldJournal.harvest(l,id,p,Blocks.SAND.defaultBlockState(),ItemStack.EMPTY);var state=new CompoundTag();state.putUUID("id",id);state.putString("stage","dig");var cargo=NaturalSupplyGoal.cargo(l,state);h.assertTrue(l.getBlockState(p).isAir()&&cargo.size()==1&&ItemStack.of(cargo.getCompound(0)).is(Items.SAND),"Recovery reads real removed-block loot");
  var stock=p.offset(3,0,0);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);WorldJournal.deposit(l,Settlement.childId(id,"deliver/0"),stock,ItemStack.of(cargo.getCompound(0)));h.assertTrue(NaturalSupplyGoal.cargo(l,state).isEmpty(),"Already delivered loot cannot be dropped or delivered twice");
  l.setBlock(p,Blocks.SAND.defaultBlockState(),2);l.setBlock(p.east(),Blocks.LAVA.defaultBlockState(),2);h.assertTrue(!NaturalSupplyGoal.safe(l,p),"Gatherer refuses adjacent lava");h.succeed();
 }
 @GameTest(template="empty") public static void glassDemandFindsNaturalSandInsteadOfInventingItFromStone(GameTestHelper h){
  h.assertTrue(BuildingOrders.payable(Blocks.GRASS_BLOCK.defaultBlockState()).is(Blocks.DIRT),"New yard soil is dirt that can green naturally, not a silk-touch grass requirement");var l=h.getLevel();var c=new net.minecraft.world.SimpleContainer(108);c.setItem(0,new ItemStack(Items.COBBLESTONE,64));c.setItem(1,new ItemStack(Items.COAL,16));var wants=List.of(new Workshops.Want(Ingredient.of(Items.GLASS),2,UUID.randomUUID()));
  var needs=Workshops.needs(l,Workshops.spec("town_hall"),c,wants);h.assertTrue(needs.stream().anyMatch(in->in.matches(new ItemStack(Items.SAND))),"Glass shortage resolves to natural sand");h.assertTrue(Workshops.plan(l,Workshops.spec("town_hall"),c,wants)==null,"Stone and coal cannot conjure sand or glass");var bare=new net.minecraft.world.SimpleContainer(108);var torch=Workshops.needs(l,Workshops.spec("town_hall"),bare,List.of(new Workshops.Want(Ingredient.of(Items.TORCH),4,UUID.randomUUID())));h.assertTrue(torch.stream().noneMatch(in->in.matches(new ItemStack(Items.COAL_ORE))),"Ordinary coal is a mining output, not a demand for silk-touch coal ore");h.succeed();
 }
}
