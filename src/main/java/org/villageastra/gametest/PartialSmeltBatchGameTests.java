package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

/** Prepared three-of-four smelting stock; planning must use only whole affordable units. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PartialSmeltBatchGameTests {
 @GameTest(template="empty",batch="partial_smelt_batch",timeoutTicks=200)
 public static void threeSandstoneStartThreePaidUnitsInsteadOfWaitingForFour(GameTestHelper h){
  var stock=new SimpleContainer(9);stock.setItem(0,new ItemStack(Items.SANDSTONE,3));stock.setItem(1,new ItemStack(Items.COAL));
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.SMOOTH_SANDSTONE),16,UUID.randomUUID()));
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants);
  h.assertTrue(job!=null&&job.recipe().equals("minecraft:smooth_sandstone")&&job.units()==3,"Existing three sandstone must start a three-unit smelt, not wait for a fourth");
  h.assertTrue(job.inputs().size()==1&&job.inputs().get(0).count()==3&&job.outputs().size()==1&&job.outputs().get(0).is(Items.SMOOTH_SANDSTONE)&&job.outputs().get(0).getCount()==3,"Exact paid input and output counts");
  h.assertTrue(job.fuelTicks()==600&&job.labor()==10800,"Three ordinary recipe fuel and labor units are retained");
  h.assertTrue(stock.countItem(Items.SANDSTONE)==3&&stock.countItem(Items.COAL)==1,"Planning itself neither takes nor gives materials");
  h.succeed();
 }
 @GameTest(template="empty",batch="partial_smelt_batch",timeoutTicks=200)
 public static void partialSmeltStillRefusesMissingFuel(GameTestHelper h){
  var stock=new SimpleContainer(9);stock.setItem(0,new ItemStack(Items.SANDSTONE,3));
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.SMOOTH_SANDSTONE),16,UUID.randomUUID()));
  h.assertTrue(Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants)==null,"Even one smelting unit still needs real fuel");
  h.assertTrue(stock.countItem(Items.SANDSTONE)==3,"Unfunded stock unchanged");h.succeed();
 }
 @GameTest(template="empty",batch="partial_smelt_batch",timeoutTicks=1600)
 public static void threeSandstoneArePaidCookedInRealFurnaceAndDepositedOnce(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new net.minecraft.core.BlockPos(2,3,2));
  var s=new org.villageastra.domain.Settlement(UUID.randomUUID());var hall=new org.villageastra.domain.Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var e=new org.villageastra.server.SettlementData.Entry(s,l.dimension().location().toString(),center);org.villageastra.server.SettlementData.get(l.getServer()).add(e);
  var chestAt=Workshops.station(e,hall);l.setBlock(chestAt,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.setItem(0,new ItemStack(Items.SANDSTONE,3));stock.setItem(1,new ItemStack(Items.COAL));
  var furnaceAt=center.offset(4,1,4);l.setBlock(furnaceAt,net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState(),2);var chunk=new net.minecraft.world.level.ChunkPos(furnaceAt);boolean already=l.getForcedChunks().contains(chunk.toLong());if(!already)l.setChunkForced(chunk.x,chunk.z,true);
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.SMOOTH_SANDSTONE),16,hall.id()));boolean[] finished={false};long began=l.getGameTime();UUID[] job={null};
  h.onEachTick(()->{if(finished[0]||l.getGameTime()%20!=0)return;Workshops.advance(l,e,hall,l.getGameTime(),wants);var t=Workshops.inspect(l,hall.id());if(t.hasUUID("id"))job[0]=t.getUUID("id");});
  h.startSequence().thenWaitUntil(()->h.assertTrue(stock.countItem(Items.SMOOTH_SANDSTONE)==3,"Three units must actually cook and reach the chest"))
   .thenExecute(()->{
    finished[0]=true;
    try{
     h.assertTrue(l.getGameTime()-began>=600&&stock.countItem(Items.SANDSTONE)==0&&stock.countItem(Items.COAL)==0,"Actual vanilla cooking ticks, three raw inputs and one real coal paid");
     var t=Workshops.inspect(l,hall.id());h.assertTrue(t.getString("stage").equals("idle")&&t.getList("inputs",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getInt("count")==3,"Exactly the smaller paid job completed");
     var furnace=(net.minecraft.world.level.block.entity.FurnaceBlockEntity)l.getBlockEntity(furnaceAt);h.assertTrue(furnace.getItem(0).isEmpty()&&furnace.getItem(2).isEmpty(),"No hidden duplicate raw or cooked output");
     for(var suffix:List.of("smelt/raw/0","smelt/put_raw","smelt/fuel/0","smelt/put_fuel/0","smelt/output","smelt/deliver"))h.assertTrue(org.villageastra.persistence.WorldJournal.recoverExisting(l,org.villageastra.domain.Settlement.childId(job[0],suffix))!=null,"Committed slot receipt: "+suffix);
     com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_PARTIAL_SMELT VERIFIED job={} sandstone=3 smooth=3 coal=1 actualTicks={}",job[0],l.getGameTime()-began);
    }finally{org.villageastra.server.SettlementData.get(l.getServer()).remove(s.id());if(!already)l.setChunkForced(chunk.x,chunk.z,false);}
   }).thenSucceed();
 }
}
