package org.villageastra.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
import org.villageastra.persistence.NbtRecord;
/** Real GUI policy changes and finite physical crop work; fixture growth is frozen. */
final class CropsProbe {
 private static int phase,ticks;private static volatile boolean firstDone,done;private static volatile String failure;private static boolean stopped;
 static boolean enabled(){return Boolean.getBoolean("villageastra.cropsSmoke");}
 static void setup(net.minecraft.server.MinecraftServer server){var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();for(var r:e.settlement().residents())((ResidentEntity)l.getEntity(r.id())).setNoAi(true);e.settlement().appointPlayerMayor(server.getPlayerList().getPlayers().get(0).getUUID());SettlementData.get(server).setDirty();
  // AD-093: carrots and cane open by their quests; the probe's village has done them, since it tests the crop policy itself.
  for(var crop:java.util.List.of("minecraft:carrot","minecraft:sugar_cane"))org.villageastra.world.CropUnlocks.unlock(l,e,crop,"probe");
  for(var p:FarmWorkArea.cells(e)){l.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState(),3);l.setBlock(p,Blocks.WHEAT.defaultBlockState(),3);}l.setBlock(e.center().offset(2,1,22),Blocks.AIR.defaultBlockState(),3);var root=e.center().offset(4,1,24);l.setBlock(root.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(root.below().west(),Blocks.WATER.defaultBlockState(),3);l.setBlock(e.center().offset(3,0,25),Blocks.WATER.defaultBlockState(),3);for(int y=0;y<3;y++)l.setBlock(root.above(y),Blocks.SUGAR_CANE.defaultBlockState(),3);var stock=(OwnedChestEntity)l.getBlockEntity(e.center().offset(1,1,4));stock.setItem(20,new ItemStack(Items.CARROT,3));stock.setItem(21,new ItemStack(Items.SUGAR_CANE,3));l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(0,server);}
 private static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
 private static void call(Minecraft mc,java.util.function.Consumer<net.minecraft.server.MinecraftServer> action){mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer());}catch(Exception ex){failure=ex.toString();}});}
 /** The office opens on its overview now: the probe goes straight to the buildings tab, as its tab button would. */
 private static void open(Minecraft mc){OfficeProbes.open(mc,ConstructionScreen.BUILDINGS);mc.screen.tick();}
 /** The buildings tab lists every card: choose each farm in turn, as a click on its row does, and press the crop button found by its label. */
 private static void clickCrop(Minecraft mc){
  var label=BuildingCard.text("next_crop");
  for(var raw:ConstructionOverlay.snapshot().getList("cards",Tag.TAG_COMPOUND)){var card=(CompoundTag)raw;if(!BuildingCard.farm(card)||!card.hasUUID("id"))continue;
   BuildingsPanel.select(card.getUUID("id"));mc.screen.tick();
   var b=OfficeProbes.button(mc.screen,label);if(b!=null){OfficeProbes.clickOrFail(mc.screen,b,"The crop button");return;}}
  throw new IllegalStateException("No farm card with a crop button");
 }
 private static ResidentEntity farmer(net.minecraft.server.MinecraftServer s,SettlementData.Entry e){return (ResidentEntity)s.overworld().getEntity(e.settlement().residents().stream().filter(r->r.profession()==Profession.FARMER).findFirst().orElseThrow().id());}
 private static String crop(CompoundTag t){return t.getList("farms",Tag.TAG_COMPOUND).getCompound(0).getString("crop");}
 private static void inspect(Minecraft mc,boolean finalCheck){call(mc,s->{var e=SettlementData.get(s).entries().iterator().next();var l=s.overworld();var o=e.center();var r=farmer(s,e);var work=s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+e.settlement().workplace(r.getUUID()).id()+".bin");if(!java.nio.file.Files.exists(work))return;var state=NbtRecord.read(work);var stock=(OwnedChestEntity)l.getBlockEntity(o.offset(1,1,4));var output=(OwnedChestEntity)l.getBlockEntity(o.offset(1,1,16));if(!state.getString("stage").equals("choose")||!state.getList("cargo",Tag.TAG_COMPOUND).isEmpty()||!l.getBlockState(o.offset(2,1,22)).is(Blocks.CARROTS)||!l.getBlockState(o.offset(4,1,24)).is(Blocks.SUGAR_CANE)||!l.getBlockState(o.offset(4,2,24)).isAir())return;if(finalCheck&&!l.getBlockState(o.offset(2,1,25)).is(Blocks.SUGAR_CANE))return;require(stock.countItem(Items.CARROT)==2&&stock.countItem(Items.SUGAR_CANE)==3&&output.countItem(Items.SUGAR_CANE)==(finalCheck?1:2),"Exact carrot and cane conservation");if(finalCheck){require(l.getBlockState(o.offset(2,0,25)).is(Blocks.DIRT),"Cane bed was not prepared");require(ItemStack.of(state.getCompound("tool")).getDamageValue()==1,"Expected one soil preparation and no wear from the two cane harvests (AD-104 P2)");}r.setNoAi(true);if(finalCheck)done=true;else firstDone=true;});}
 static void tick(Minecraft mc){if(stopped)return;try{if(failure!=null)throw new IllegalStateException(failure);if(++ticks>7200)throw new IllegalStateException("Crop probe timeout phase="+phase);var t=ConstructionOverlay.snapshot();boolean reload=Boolean.getBoolean("villageastra.reloadSmoke");if(ticks%200==0)com.mojang.logging.LogUtils.getLogger().info("ASTRA_CROPS phase={} ticks={} crop={}",phase,ticks,crop(t));
  if(phase==0&&t.getBoolean("canManage")&&!t.getList("farms",Tag.TAG_COMPOUND).isEmpty()){open(mc);if(reload){require(crop(t).equals("sugar_cane"),"Lost saved building policy");phase=5;}else{clickCrop(mc);phase=1;}ticks=0;}
  else if(phase==1&&crop(t).equals("carrot")){call(mc,s->{var e=SettlementData.get(s).entries().iterator().next();farmer(s,e).setNoAi(false);});phase=2;ticks=0;}
  else if(phase==2){if(ticks%20==0)inspect(mc,false);if(firstDone){phase=3;ticks=0;}}
  else if(phase==3&&ticks%20==0){if(crop(t).equals("sugar_cane")){call(mc,s->{var e=SettlementData.get(s).entries().iterator().next();s.overworld().setBlock(e.center().offset(2,1,25),Blocks.AIR.defaultBlockState(),3);farmer(s,e).setNoAi(false);});phase=5;ticks=0;}else{open(mc);clickCrop(mc);}}
  else if(phase==5){if(ticks%20==0)inspect(mc,true);if(done){open(mc);phase=6;ticks=0;}}
  else if(phase==6&&ticks>40){var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-crops.png");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}com.mojang.logging.LogUtils.getLogger().info("ASTRA_CROPS screenshot {}",path);stopped=true;call(mc,s->{s.saveEverything(false,true,true);com.mojang.logging.LogUtils.getLogger().info("ASTRA_CROPS VERIFIED GUI policy, real carrot planted, two cane harvested with root preserved, one delivered cane replanted at prepared bank, exact inputs/outputs and one hoe durability (the bank's tilling; reaping wears none); reload={}",reload);mc.execute(mc::stop);});}
 }catch(Exception ex){stopped=true;com.mojang.logging.LogUtils.getLogger().error("ASTRA_CROPS FAILED",ex);mc.stop();}}
}
