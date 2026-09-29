package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
import java.nio.file.*;
import java.util.*;

/** Finite two-farm fixture; crop maturation itself remains vanilla and is not accelerated in production. AD-130: a farmer works his own farm's
 *  field — two ripe plants of his, and one ripe plant on the second registered farm, which is that farm's (it has no farmer) and stays. */
final class FarmSmokeProbe {
 private static int ticks;private static boolean stopped;
 static boolean enabled(){return Boolean.getBoolean("villageastra.farmSmoke");}
 private static Path root(MinecraftServer s){return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);}
 static void setup(MinecraftServer server){
  var level=server.overworld();var entry=SettlementData.get(server).entries().iterator().next();var village=entry.settlement();var origin=entry.center();
  for(var r:village.residents())((ResidentEntity)level.getEntity(r.id())).setNoAi(r.profession()!=Profession.FARMER);
  var farmer=village.residents().stream().filter(r->r.profession()==Profession.FARMER).findFirst().orElseThrow();
  village.addBuilding(new Settlement.Building(Settlement.childId(village.id(),"farm-probe-secondary"),"farm",42,0,12));BuildingBlueprints.preview(level,"farm",origin.offset(42,0,12));SettlementData.get(server).setDirty();
  for(var pos:FarmWorkArea.cells(entry)){level.setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(pos,Blocks.WHEAT.defaultBlockState(),3);}
  // AD-104: the second farm's field is watered as the generator waters one: a source at the centre of its module.
  for(var b:village.buildings())if(b.id().equals(Settlement.childId(village.id(),"farm-probe-secondary")))for(var w:org.villageastra.world.FarmField.water(entry,b))level.setBlock(w,Blocks.WATER.defaultBlockState(),3);
  level.setBlock(origin.offset(0,1,21),Blocks.AIR.defaultBlockState(),3);level.setBlock(origin.offset(0,0,21),Blocks.DIRT.defaultBlockState(),3);
  for(int x:new int[]{1,5,43})level.setBlock(origin.offset(x,1,22),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);
  level.setBlock(origin.offset(36,0,28),Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(origin.offset(36,1,28),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);
  level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
  var t=new CompoundTag();t.putUUID("farmer",farmer.id());t.putUUID("building",village.workplace(farmer.id()).id());t.put("harvests",new ListTag());NbtRecord.write(root(server).resolve("data/farm-smoke-probe.bin"),t);
  LogUtils.getLogger().info("ASTRA_FARM fixture: two registered farms, two mature plants of the farmer's own farm and one of the other farm, one damaged soil cell, one foreign mature plant; random ticks disabled only for fixture");
 }
 static void tick(Minecraft mc){if(stopped||++ticks%20!=0)return;mc.getSingleplayerServer().execute(()->{try{step(mc);if(ticks>7200)throw new IllegalStateException("Farm scenario timeout");}catch(Exception ex){stopped=true;LogUtils.getLogger().error("ASTRA_FARM FAILED",ex);mc.execute(mc::stop);}});}
 private static void step(Minecraft mc){
  var server=mc.getSingleplayerServer();var level=server.overworld();var entry=SettlementData.get(server).entries().iterator().next();var origin=entry.center();var file=root(server).resolve("data/farm-smoke-probe.bin");var t=NbtRecord.read(file);
  var work=root(server).resolve("data/astra-work/"+t.getUUID("building")+".bin");if(!Files.exists(work))return;var job=NbtRecord.read(work);
  if(job.hasUUID("lastHarvest")){
   var id=job.getUUID("lastHarvest");var harvests=t.getList("harvests",Tag.TAG_COMPOUND);
   if(harvests.stream().noneMatch(raw->((CompoundTag)raw).getUUID("id").equals(id))){
    var receipt=WorldJournal.recoverExisting(level,id);if(receipt==null)throw new IllegalStateException("Missing harvest receipt");int seeds=0;
    for(var raw:receipt.getList("loot",Tag.TAG_COMPOUND)){var item=ItemStack.of((CompoundTag)raw);if(item.is(Items.WHEAT_SEEDS))seeds+=item.getCount();}
    var h=new CompoundTag();h.putUUID("id",id);h.putInt("seeds",seeds);harvests.add(h);t.put("harvests",harvests);NbtRecord.write(file,t);
   }
  }
  var output=(Container)level.getBlockEntity(origin.offset(1,1,16));var stock=(Container)level.getBlockEntity(origin.offset(1,1,4));var tool=ItemStack.of(job.getCompound("tool"));
  if(ticks%200==0){var who=level.getEntity(t.getUUID("farmer"));LogUtils.getLogger().info("ASTRA_FARM progress wheat={} stockSeeds={} outputSeeds={} hoeDamage={} phase={} farmer={}",output.countItem(Items.WHEAT),stock.countItem(Items.WHEAT_SEEDS),output.countItem(Items.WHEAT_SEEDS),tool.getDamageValue(),job.getString("stage"),who==null?"unloaded":who.blockPosition().subtract(origin).toShortString()+(who instanceof ResidentEntity r?" "+r.workStatus():""));}
  if(output.countItem(Items.WHEAT)>2)throw new IllegalStateException("Farmer duplicated crops or harvested foreign field");
  if(output.countItem(Items.WHEAT)!=2||!job.getString("stage").equals("choose"))return;
  for(var pos:List.of(origin.offset(0,1,21),origin.offset(1,1,22),origin.offset(5,1,22))){var block=level.getBlockState(pos);if(!block.is(Blocks.WHEAT)||block.getValue(CropBlock.AGE)!=0)return;}
  if(!level.getBlockState(origin.offset(43,1,22)).equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7)))throw new IllegalStateException("The farmer reaped another farm's field (AD-130: his own farm only)");
  if(!level.getBlockState(origin.offset(0,0,21)).is(Blocks.FARMLAND))throw new IllegalStateException("Soil was not repaired");
  // AD-104 P2: reaping a crop wears no hoe; the one till of the damaged soil does.
  if(!tool.is(Items.STONE_HOE)||tool.getDamageValue()!=1||stock.countItem(Items.STONE_HOE)!=0)throw new IllegalStateException("Wrong real hoe or durability");
  var harvests=t.getList("harvests",Tag.TAG_COMPOUND);int seeds=harvests.stream().mapToInt(raw->((CompoundTag)raw).getInt("seeds")).sum();
  if(harvests.size()!=2||stock.countItem(Items.WHEAT_SEEDS)+output.countItem(Items.WHEAT_SEEDS)!=16+seeds-3)throw new IllegalStateException("Seed conservation failed");
  if(!level.getBlockState(origin.offset(36,1,28)).equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7)))throw new IllegalStateException("Unregistered field was touched");
  server.saveEverything(false,true,true);LogUtils.getLogger().info("ASTRA_FARM VERIFIED one physical farmer, two plants of his own field harvested and reseeded, the other farm's plant left, two wheat delivered, three seeds consumed, one hoe durability used, damaged soil repaired, foreign crop untouched; reload={}",Boolean.getBoolean("villageastra.reloadSmoke"));stopped=true;mc.execute(mc::stop);
 }
}
