package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** A real doctor cures a walking patient while a real builder is renovating clinic II to III. The saved world intentionally retains the active project. */
final class LiveUpgradeProbe {
 private static int ticks,phase;private static volatile boolean busy,done;private static volatile String failure;
 private static UUID village,clinicId,patientId;private static Container clinicStock;private static ResidentEntity builder,patient;
 private static BlockPos center,start;private static double walked;private static boolean constructionSeen;
 static boolean enabled(){return Boolean.getBoolean("villageastra.liveUpgradeSmoke");}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private static Settlement.Building clinic(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.id().equals(clinicId)).findFirst().orElseThrow();}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Live upgrade timeout");
  if(done){require(!ConstructionOverlay.projectName(ConstructionOverlay.snapshot()).getString().contains("building.villageastra."),"HUD translates tiered building name");var sample=new CompoundTag();sample.putString("design","clinic@3");require(ConstructionOverlay.projectName(sample).getString().contains("III"),"HUD names target tier");var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-live-upgrade.png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_LIVE_UPGRADE screenshot {}",path);LogUtils.getLogger().info("ASTRA_LIVE_UPGRADE VERIFIED naturalTicks=true activeProject=true cured=true paidBandage=1 stockIdentity=true oldLevel=2 walked=true");mc.stop();return;}
  if(ticks%10!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_LIVE_UPGRADE FAILED",ex);mc.stop();}}
 private static void step(ServerLevel l){var data=SettlementData.get(l.getServer());
  if(phase==0){center=data.entries().iterator().next().center().offset(220,0,0);var s=new Settlement(UUID.randomUUID());village=s.id();var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);data.add(e);
   l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   for(int x=-20;x<=45;x++)for(int z=-16;z<=35;z++){var at=center.offset(x,0,z);l.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(at,Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=25;y++)l.setBlock(at.above(y),Blocks.AIR.defaultBlockState(),2);}
   var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0,0,3);s.addBuilding(hall);clinicId=UUID.randomUUID();var b=new Settlement.Building(clinicId,"clinic",22,0,4,0,2);s.addBuilding(b);
   for(var building:List.of(hall,b))BuildingPlacement.layout(e,building,building==b?"clinic@2":"town_hall_3").forEach((p,st)->l.setBlock(p,st,3));
   var home=new Settlement.Home(UUID.randomUUID(),1,6,true);s.addHome(home);
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());s.assign(r.id(),Profession.BUILDER,hall.id());builder=VillageAstra.RESIDENT.get().create(l);builder.bind(village,r);builder.moveTo(center.getX()+9.5,center.getY()+1,center.getZ()-2.5,0,0);builder.onlyGoals(g->g instanceof FloatGoal||g instanceof ReturnCargoGoal||g instanceof ResidentDoorGoal||g instanceof DoorwayGoal,5,new HallUpgradeGoal(builder));l.addFreshEntity(builder);
   var doc=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(doc,home.id());s.assign(doc.id(),Profession.DOCTOR,clinicId);var medic=VillageAstra.RESIDENT.get().create(l);medic.bind(village,doc);var station=LogisticsRoutes.position(e,b);medic.moveTo(station.getX()+.5,station.getY(),station.getZ()+1.5,0,0);l.addFreshEntity(medic);
   var sick=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(sick,home.id());sick.fallIll();patientId=sick.id();patient=VillageAstra.RESIDENT.get().create(l);patient.bind(village,sick);start=BuildingPlacement.origin(e,b).offset(4,1,-14);patient.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5,0,0);l.addFreshEntity(patient);
   clinicStock=LogisticsRoutes.chest(l,e,b);require(clinicStock!=null,"Clinic stock");clinicStock.clearContent();clinicStock.setItem(0,new ItemStack(VillageAstra.BANDAGE.get(),2));
   var research=BookResearch.inspect(l,e);var known=new ListTag();for(int level=2;level<=3;level++)for(var node:BuildingTiers.research("clinic",level))known.add(StringTag.valueOf(node));research.put("legacyDone",known);BookResearch.store(l,e,research);
   var player=l.getServer().getPlayerList().getPlayers().get(0);s.appointPlayerMayor(player.getUUID());player.setGameMode(GameType.SPECTATOR);player.teleportTo(l,center.getX()+32,center.getY()+15,center.getZ()-15,25,25);
   var why=BuildingTiers.order(l,e,b);require(why.isEmpty(),"Ordinary clinic upgrade order: "+why);var state=HallUpgradeGoal.inspect(l,village);var stock=LogisticsRoutes.chest(l,e,hall);require(stock!=null,"Hall stock");stock.clearContent();int slot=0;
   for(var key:new TreeSet<>(state.getCompound("cost").getAllKeys())){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));int left=state.getCompound("cost").getInt(key);while(left>0){int n=Math.min(left,item.getMaxStackSize());require(slot<stock.getContainerSize()-1,"Funding capacity");stock.setItem(slot++,new ItemStack(item,n));left-=n;}}stock.setItem(stock.getContainerSize()-1,new ItemStack(Items.BREAD,64));
   data.setDirty();phase=1;return;
  }
  var e=data.entry(village);var b=clinic(e);var state=HallUpgradeGoal.inspect(l,village);
  require(clinicStock==LogisticsRoutes.chest(l,e,b),"Clinic retains its physical inventory");require(BuildingTiers.level(l,e,b)==2,"Old working level remains active during construction");
  walked=Math.max(walked,Math.sqrt(patient.distanceToSqr(start.getCenter())));
  int operations=0;for(var raw:state.getList("ops",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getBoolean("done"))operations++;
  constructionSeen|=state.getBoolean("funded")&&operations>0;
  if(ticks%200==0)LogUtils.getLogger().info("ASTRA_LIVE_UPGRADE progress operations={} sick={} bandages={} builder={} walked={}",operations,e.settlement().resident(patientId).sick(),clinicStock.countItem(VillageAstra.BANDAGE.get()),builder.workStatus(),walked);
  require(!state.getBoolean("complete"),"Patient must be cured before renovation finishes");
  if(!e.settlement().resident(patientId).sick()){require(constructionSeen&&state.getInt("withdrawals")>0&&walked>6,"Physical healing overlaps funded building work");require(clinicStock.countItem(VillageAstra.BANDAGE.get())==1,"Exactly one bandage consumed");
   var saved=new CompoundTag();saved.putUUID("village",village);saved.putUUID("clinic",clinicId);saved.putUUID("patient",patientId);saved.putUUID("project",state.getUUID("id"));saved.putInt("operations",operations);
   org.villageastra.persistence.NbtRecord.write(l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-live-upgrade-probe.bin"),saved);
   org.villageastra.server.ProbeWarp.end("Clinic keeps healing during renovation");done=true;}
 }
}
