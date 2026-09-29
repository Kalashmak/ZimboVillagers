package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-152 in the real client: a clinic of level I north of the village with its medic and three bandages; two residents fall ill twenty
 *  blocks away. Both walk to the hospital; its one bed cures one of them for one bandage while the other waits. Frame: the sick at the clinic. */
final class MedicineProbe {
 private static int phase,ticks;private static volatile String failure,progress="",facts="";private static volatile boolean ready,done;
 private static volatile UUID clinicId;private static final List<UUID> SICK=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.medicineProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-medicine-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_MEDICINE screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 private static void fixture(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);st.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(3000);l.setWeatherParameters(24000,0,false,false);
  var clinic=new Settlement.Building(Settlement.childId(st.id(),"building/probe-clinic"),"clinic",0,0,-60);st.addBuilding(clinic);clinicId=clinic.id();
  var o=BuildingPlacement.origin(e,clinic);
  for(int x=-4;x<16;x++)for(int z=-26;z<14;z++){var g=o.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=1;y<16;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),3);}
  for(var cell:BuildingPlacement.layout(e,clinic,"clinic").entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  var chest=LogisticsRoutes.chest(l,e,clinic);if(chest==null)throw new IllegalStateException("No clinic chest");chest.setItem(0,new ItemStack(VillageAstra.BANDAGE.get(),3));
  var home=new Settlement.Home(Settlement.childId(st.id(),"home/probe-clinic"),1,6,true);st.addHome(home);
  var medic=new Resident(Settlement.childId(st.id(),"probe-medic"),Resident.Life.ADULT,true,null,null,-1);st.admit(medic,home.id());st.assign(medic.id(),Profession.DOCTOR,clinic.id());
  var station=LogisticsRoutes.position(e,clinic);
  var m=VillageAstra.RESIDENT.get().create(l);m.bind(st.id(),st.resident(medic.id()));m.moveTo(station.getX()+.5,station.getY(),station.getZ()+1.5,0,0);l.addFreshEntity(m);
  for(int i=0;i<2;i++){var r=new Resident(Settlement.childId(st.id(),"probe-sick-"+i),Resident.Life.ADULT,false,null,null,-1);st.admit(r,home.id());r.fallIll();SICK.add(r.id());
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(st.id(),r);var at=o.offset(3+3*i,1,-20);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(npc);}
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_MEDICINE fixture clinic I with its medic, two sick residents 20 blocks away");ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{var l=s.overworld();var e=entry(s);var clinic=building(e,clinicId);var at=LogisticsRoutes.position(e,clinic);
  int arrived=0,cured=0;for(var id:SICK){var r=e.settlement().resident(id);if(!r.sick())cured++;if(l.getEntity(id) instanceof ResidentEntity npc&&npc.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)<=Medicine.WARD*Medicine.WARD)arrived++;}
  int bandages=LogisticsRoutes.count(LogisticsRoutes.chest(l,e,clinic),x->x.is(VillageAstra.BANDAGE.get()));
  var medic=e.settlement().resident(Settlement.childId(e.settlement().id(),"probe-medic"));String doc="none";
  if(medic!=null&&l.getEntity(medic.id()) instanceof ResidentEntity d)doc=String.format(Locale.ROOT,"%.1f work=%s mayWork=%b sick=%b prof=%s status=%s",Math.sqrt(d.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)),e.settlement().workplace(medic.id())==null?"-":e.settlement().workplace(medic.id()).type(),Population.mayWork(medic),medic.sick(),medic.profession(),d.workStatus());
  progress="arrived="+arrived+" cured="+cured+" bandages="+bandages+" beds="+Medicine.beds(BuildingLevels.level(l,e,clinic))+" medic=["+doc+"]";
  if(!done&&arrived==2&&cured==1&&bandages==2){done=true;facts=progress;}
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Medicine timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%20==0){sample(mc);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_MEDICINE progress {}",progress);
   if(done){var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);var o=BuildingPlacement.origin(e,building(e,clinicId));s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),o.getX()+5.5,o.getY()+9,o.getZ()-12,0,30);});phase=2;ticks=0;}}
  else if(phase==2&&ticks>40){mc.options.hideGui=true;capture(mc,"hospital");mc.options.hideGui=false;
   LogUtils.getLogger().info("ASTRA_MEDICINE VERIFIED two sick residents walked to the hospital of level I; its one bed cured one for one bandage, the other waits: {}; reload=false",facts);
   phase=3;mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_MEDICINE FAILED",ex);phase=99;mc.stop();}}
}
