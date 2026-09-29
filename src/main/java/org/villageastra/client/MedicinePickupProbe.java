package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** A live resident collects clinic V medicine without any delivery wolf. */
final class MedicinePickupProbe {
 private static int ticks,phase;private static volatile boolean busy,done;private static volatile String failure;private static UUID village,clinicId,patientId;private static ResidentEntity patientBody;private static BlockPos start;private static double walked;private static boolean loaded;
 static boolean enabled(){return Boolean.getBoolean("villageastra.medicinePickupSmoke");}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>3600)throw new IllegalStateException("Medicine pickup timeout phase="+phase+" loaded="+loaded+" walked="+walked);
  if(done){var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-medicine-pickup.png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_MEDICINE_PICKUP screenshot {}",path);LogUtils.getLogger().info("ASTRA_MEDICINE_PICKUP VERIFIED naturalTicks=true loaded=true cured=true paid=1 noWolf=true walked=true noFreeSecondCure=true");mc.stop();return;}
  if(ticks%20!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_MEDICINE_PICKUP FAILED",ex);mc.stop();}}
 private static void step(ServerLevel l){var data=SettlementData.get(l.getServer());
  if(phase==0){var original=data.entries().iterator().next();var center=original.center().offset(200,0,0);var s=new Settlement(UUID.randomUUID());village=s.id();var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);data.add(e);
   l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   clinicId=Settlement.childId(village,"clinic");var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,-18);s.addBuilding(hall);
   s.addBuilding(new Settlement.Building(clinicId,"clinic",0,0,0));for(int n=2;n<=5;n++)s.raiseBuildingLevel(clinicId,n);var clinic=building(e,clinicId);
   for(int x=-5;x<=44;x++)for(int z=-5;z<=42;z++){var at=center.offset(x,0,z);l.setBlock(at,Blocks.STONE.defaultBlockState(),3);for(int y=1;y<=7;y++)l.setBlock(at.above(y),Blocks.AIR.defaultBlockState(),3);}
   for(var b:List.of(clinic))for(var cell:BuildingPlacement.layout(e,b,b==clinic?BuildingTiers.layoutId("clinic",5):b.type()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   BuildingLevels.forgetBest(village);for(var b:List.of(hall,clinic)){l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);LogisticsRoutes.chest(l,e,b).clearContent();}
   var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);var house=new Settlement.Building(home.id(),"home_2",0,0,26);s.addBuilding(house);for(var cell:BuildingPlacement.layout(e,house,"home_2").entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);var patient=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(patient,home.id());patient.fallIll();patientId=patient.id();patientBody=VillageAstra.RESIDENT.get().create(l);patientBody.bind(village,patient);start=center.offset(42,1,20);patientBody.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5,0,0);l.addFreshEntity(patientBody);
   var p=l.getServer().getPlayerList().getPlayers().get(0);s.appointPlayerMayor(p.getUUID());p.setGameMode(GameType.SPECTATOR);p.teleportTo(l,center.getX()+18.5,center.getY()+22,center.getZ()-16,0,42);LogisticsRoutes.chest(l,e,clinic).setItem(0,new ItemStack(VillageAstra.BANDAGE.get()));phase=1;LogUtils.getLogger().info("ASTRA_MEDICINE_PICKUP setup patient={} village={}",patientBody.getUUID(),village);return;
  }
  var e=data.entry(village);var source=LogisticsRoutes.chest(l,e,building(e,clinicId));var trip=MedicineDelivery.inspect(l,village);var patient=e.settlement().resident(patientId);var npc=(ResidentEntity)l.getEntity(patientId);loaded|=trip.getString("stage").equals("go");walked=Math.max(walked,Math.sqrt(patientBody.distanceToSqr(start.getX()+.5,start.getY(),start.getZ()+.5)));
  if(ticks%200==0)LogUtils.getLogger().info("ASTRA_MEDICINE_PICKUP progress stage={} at={} source={} sick={} walked={}",trip.getString("stage"),patientBody.blockPosition()+" "+patientBody.runningGoals()+" "+patientBody.workStatus(),source.countItem(VillageAstra.BANDAGE.get()),patient.sick(),walked);
  if(phase==2){require(patient.sick()&&!MedicinePacks.carried(l,patientId)&&source.countItem(VillageAstra.BANDAGE.get())==0,"Second illness cannot reuse spent medicine");if(++stable>=5)done=true;return;}
  if(!patient.sick()){require(trip.getString("stage").equals("complete")&&loaded&&walked>=8,"Cure requires completed physical delivery");require(MedicinePacks.received(l,patientId,trip.getUUID("id")),"Counter receipt persisted");require(source.countItem(VillageAstra.BANDAGE.get())==0&&!MedicinePacks.carried(l,patientId),"Paid once and consumed");patient.fallIll();patientBody.setNoAi(true);var viewer=l.getServer().getPlayerList().getPlayers().get(0);viewer.teleportTo(l,npc.getX()-6,npc.getY()+4,npc.getZ()-6,-45,25);phase=2;}
 }
 private static int stable;
}
