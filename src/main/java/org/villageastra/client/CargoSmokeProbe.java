package org.villageastra.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
import java.nio.file.Path;
import java.util.*;

/** Separate finite fixture: real return navigation, replacement ownership, death, looting and restart. */
final class CargoSmokeProbe {
 private static int ticks;private static boolean stopped;
 static boolean enabled(){return Boolean.getBoolean("villageastra.cargoSmoke");}
 private static Path root(MinecraftServer s){return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);}
 private static Path marker(MinecraftServer s){return root(s).resolve("data/cargo-smoke-probe.bin");}
 private static Path work(MinecraftServer s,UUID id){return root(s).resolve("data/astra-work/"+id+".bin");}
 private static int slot(Container c,Item item){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))return i;throw new IllegalStateException("Missing fixture item "+item);}
 static void setup(MinecraftServer server){
  var level=server.overworld();var entry=SettlementData.get(server).entries().iterator().next();var village=entry.settlement();var origin=entry.center();
  for(var r:village.residents())((ResidentEntity)level.getEntity(r.id())).setNoAi(true);
  var old=village.residents().stream().filter(r->r.profession()==Profession.MINER).findFirst().orElseThrow();
  var replacement=village.residents().stream().filter(r->r.profession()==Profession.FARMER).findFirst().orElseThrow();var mine=village.workplace(old.id());
  var stock=(Container)level.getBlockEntity(origin.offset(1,1,4));var tool=WorldJournal.take(level,UUID.randomUUID(),origin.offset(1,1,4),slot(stock,Items.STONE_PICKAXE),stock.getItem(slot(stock,Items.STONE_PICKAXE)).copy());
  var id=UUID.randomUUID();var target=origin.offset(34,3,19);level.setBlock(target,Blocks.STONE.defaultBlockState(),3);var loot=WorldJournal.harvest(level,id,target,Blocks.STONE.defaultBlockState(),tool);tool.setDamageValue(1);
  var job=new CompoundTag();job.putInt("schema",1);job.putInt("width",3);job.putInt("height",4);job.putUUID("operation",id);job.putUUID("worker",old.id());job.put("tool",tool.save(new CompoundTag()));job.putString("stage","deliver");var cargo=new ListTag();for(var stack:loot)cargo.add(stack.save(new CompoundTag()));job.put("cargo",cargo);NbtRecord.write(work(server,mine.id()),job);
  village.assign(old.id(),Profession.PORTER,Settlement.childId(village.id(),"building/town_hall"));village.assign(replacement.id(),Profession.MINER,mine.id());SettlementData.get(server).setDirty();
  var actor=(ResidentEntity)level.getEntity(old.id());actor.moveTo(origin.getX()+26.5,origin.getY()+1,origin.getZ()+17.5);actor.setNoAi(false);((ResidentEntity)level.getEntity(replacement.id())).setNoAi(false);
  var t=new CompoundTag();t.putUUID("old",old.id());t.putUUID("replacement",replacement.id());t.putUUID("mine",mine.id());t.putString("phase","return");NbtRecord.write(marker(server),t);
  LogUtils.getLogger().info("ASTRA_CARGO fixture: one paid pickaxe, one harvested stone, old worker at mine, replacement assigned");
 }
 static void tick(Minecraft mc){
  if(stopped||++ticks%20!=0)return;
  mc.getSingleplayerServer().execute(()->{try{step(mc);if(ticks>4800)throw new IllegalStateException("Cargo scenario timeout");}catch(Exception ex){stopped=true;LogUtils.getLogger().error("ASTRA_CARGO FAILED",ex);mc.execute(mc::stop);}});
 }
 private static void step(Minecraft mc){
  var server=mc.getSingleplayerServer();var level=server.overworld();var entry=SettlementData.get(server).entries().iterator().next();var origin=entry.center();var t=NbtRecord.read(marker(server));
  var stock=(Container)level.getBlockEntity(origin.offset(1,1,4));var old=(ResidentEntity)level.getEntity(t.getUUID("old"));var replacement=(ResidentEntity)level.getEntity(t.getUUID("replacement"));
  if(t.getString("phase").equals("return")){
   var custody=CargoCustody.inspect(server,t.getUUID("old"));var job=NbtRecord.read(work(server,t.getUUID("mine")));
   if(!custody.getBoolean("complete")){
    if(job.hasUUID("worker")&&!job.getUUID("worker").equals(t.getUUID("old")))throw new IllegalStateException("Replacement inherited undelivered cargo");
    if(ticks%200==0)LogUtils.getLogger().info("ASTRA_CARGO return waiting old={} stockTool={} stockStone={}",old.position(),stock.countItem(Items.STONE_PICKAXE),stock.countItem(Items.COBBLESTONE));return;
   }
   if(!job.hasUUID("worker")||!job.getUUID("worker").equals(t.getUUID("replacement"))||ItemStack.of(job.getCompound("tool")).isEmpty())return;
   if(stock.countItem(Items.COBBLESTONE)!=65||stock.countItem(Items.STONE_PICKAXE)!=0||ItemStack.of(job.getCompound("tool")).getDamageValue()!=1)throw new IllegalStateException("Return or replacement duplicated/lost cargo");
   LogUtils.getLogger().info("ASTRA_CARGO RETURN VERIFIED old worker physically returned one stone and tool; replacement took same damaged tool after release");
   old.setNoAi(true);replacement.setNoAi(true);var tool=ItemStack.of(job.getCompound("tool"));var id=UUID.randomUUID();var target=origin.offset(35,3,19);level.setBlock(target,Blocks.STONE.defaultBlockState(),3);WorldJournal.harvest(level,id,target,Blocks.STONE.defaultBlockState(),tool);
   job.putUUID("operation",id);job.putString("stage","dig");job.remove("cargo");job.putInt("step",0);job.putInt("cell",1);NbtRecord.write(work(server,t.getUUID("mine")),job);
   replacement.moveTo(origin.getX()+26.5,origin.getY()+1,origin.getZ()+16.5);t.putBoolean("returnVerified",true);t.putString("phase","death");NbtRecord.write(marker(server),t);
   NbtRecord.write(root(server).resolve("data/journal-crash-probe.bin"),new CompoundTag());server.saveEverything(false,true,true);
   var boundary=System.getProperty("villageastra.cargoCrash");if(boundary!=null)System.setProperty("villageastra.journalCrash",boundary);
   replacement.hurt(level.damageSources().genericKill(),1000);return;
  }
  if(!t.getBoolean("returnVerified"))throw new IllegalStateException("Missing completed return phase");
  var custody=CargoCustody.inspect(server,t.getUUID("replacement"));if(!custody.getBoolean("complete"))return;
  if(!custody.getBoolean("dead")||entry.settlement().resident(t.getUUID("replacement")).alive()||(replacement!=null&&replacement.isAlive()))throw new IllegalStateException("Dead worker resurrected");
  var drops=custody.getList("drops",Tag.TAG_COMPOUND);if(drops.size()!=1)throw new IllegalStateException("Wrong physical cargo count");
  var pos=BlockPos.of(drops.getCompound(0).getLong("pos"));var pile=(Container)level.getBlockEntity(pos);var player=server.getPlayerList().getPlayers().get(0);
  if(pile==null||pile.countItem(Items.STONE_PICKAXE)!=1||pile.getItem(slot(pile,Items.STONE_PICKAXE)).getDamageValue()!=2||stock.countItem(Items.STONE_PICKAXE)!=0||stock.countItem(Items.COBBLESTONE)!=65)throw new IllegalStateException("Death cargo was duplicated, refunded or lost");
  if(!t.getBoolean("looted")){
   if(pile.countItem(Items.COBBLESTONE)!=1)throw new IllegalStateException("Missing finite ore in death cargo");
   player.getInventory().add(pile.removeItem(slot(pile,Items.COBBLESTONE),1));pile.setChanged();t.putBoolean("looted",true);NbtRecord.write(marker(server),t);
  }
  CargoCustody.recover(server);
  if(pile.countItem(Items.COBBLESTONE)!=0||player.getInventory().countItem(Items.COBBLESTONE)!=1)throw new IllegalStateException("Looted resource resurrected on recovery");
  long physical=entry.settlement().residents().stream().filter(r->level.getEntity(r.id()) instanceof ResidentEntity entity&&entity.isAlive()).count();if(physical!=5)throw new IllegalStateException("Wrong live resident count");
  server.saveEverything(false,true,true);LogUtils.getLogger().info("ASTRA_CARGO VERIFIED physical return and replacement, five living NPCs, one damaged tool in death cargo, looted stone retained exactly once; reload={}",Boolean.getBoolean("villageastra.reloadSmoke"));
  stopped=true;mc.execute(mc::stop);
 }
}
