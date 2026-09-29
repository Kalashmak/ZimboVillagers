package org.villageastra.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-136: the laboratory's scientist takes his place, and the place writes one scientific work per 36 000 ticks of the village clock. The
 *  probe does not wait half an hour: once the place is taken it advances the science by the village clock plus one work's time — the same
 *  call the server makes every 40 ticks, with a later clock — and requires exactly one work in the laboratory chest, none twice. */
final class ScienceProbe {
 private static int ticks;private static volatile boolean done;private static volatile String failure;private static boolean stopped;private static long deadline=-1;private static int before;
 static boolean enabled(){return Boolean.getBoolean("villageastra.scienceSmoke");}
 static void setup(net.minecraft.server.MinecraftServer server){var l=server.overworld();var data=SettlementData.get(server);var e=data.entries().iterator().next();var s=e.settlement();for(var r:s.residents())((ResidentEntity)l.getEntity(r.id())).setNoAi(true);var home=Settlement.childId(s.id(),"science/home");var lab=Settlement.childId(s.id(),"science/lab");BuildingBlueprints.preview(l,"home",e.center().offset(-12,0,0));BuildingBlueprints.preview(l,"laboratory",e.center().offset(-16,0,12));s.addHome(new Settlement.Home(home,1,2,true));s.addBuilding(new Settlement.Building(home,"home",-12,0,0));s.addBuilding(new Settlement.Building(lab,"laboratory",-16,0,12));var r=new Resident(Settlement.childId(s.id(),"science/worker"),Resident.Life.CHILD,false,null,null,-1);s.admit(r,home);r.educate();r.growUp();s.assign(r.id(),Profession.SCIENTIST,lab);var worker=VillageAstra.RESIDENT.get().create(l);worker.bind(s.id(),r);worker.moveTo(e.center().getX()-13.5,e.center().getY()+1,e.center().getZ()+16.5,0,0);l.addFreshEntity(worker);var c=(OwnedChestEntity)l.getBlockEntity(e.center().offset(-15,1,16));c.getPersistentData().putUUID("AstraSettlement",s.id());c.clearContent();c.setChanged();l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);data.setDirty();}
 private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 static void tick(Minecraft mc){if(stopped)return;try{if(failure!=null)throw new IllegalStateException(failure);if(++ticks>6000)throw new IllegalStateException("Science timeout");boolean reload=Boolean.getBoolean("villageastra.reloadSmoke");
  if(ticks%20==0&&!done)mc.getSingleplayerServer().execute(()->{try{var server=mc.getSingleplayerServer();var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();var b=ScienceWorks.lab(e);long clock=SettlementData.get(server).clock().ticks();var c=LogisticsRoutes.chest(l,e,b);
   if(deadline<0){var path=ScienceWorks.path(l,b.id());if(!java.nio.file.Files.exists(path))return;var record=org.villageastra.persistence.NbtRecord.read(path);var seats=record.getList("seats",10);if(seats.size()!=1||!seats.getCompound(0).hasUUID("worker"))return;var seat=seats.getCompound(0);require(seat.getLong("since")>=0&&!seat.getBoolean("full"),"The test place must be running");deadline=seat.getLong("since")+ScienceBalance.WORK_TICKS-seat.getLong("ticks");require(c!=null,"Science chest missing");before=c.countItem(VillageAstra.RESEARCH_VOLUME.get());com.mojang.logging.LogUtils.getLogger().info("ASTRA_SCIENCE observed={} actualSince={} deadline={} stock={}",clock,seat.getLong("since"),deadline,before);return;}
   require(c!=null&&c.countItem(VillageAstra.RESEARCH_VOLUME.get())==before,"No work before its time");
   int early=ScienceWorks.advance(l,e,deadline-1);require(early==0,"A work came before 36000 clock ticks");
   int written=ScienceWorks.advance(l,e,deadline);int again=ScienceWorks.advance(l,e,deadline);
   require(written==1&&again==0&&c.countItem(VillageAstra.RESEARCH_VOLUME.get())==before+1,"One work per 36000 clock ticks, never twice: written="+written+" again="+again);
   com.mojang.logging.LogUtils.getLogger().info("ASTRA_SCIENCE clock advanced explicitly to deadline {} with stock before {}: works={}",deadline,before,c.countItem(VillageAstra.RESEARCH_VOLUME.get()));done=true;}catch(Exception ex){failure=ex.toString();}});
 if(done){stopped=true;var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-science.png");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}com.mojang.logging.LogUtils.getLogger().info("ASTRA_SCIENCE screenshot {}",path);mc.getSingleplayerServer().execute(()->{mc.getSingleplayerServer().saveEverything(false,true,true);com.mojang.logging.LogUtils.getLogger().info("ASTRA_SCIENCE VERIFIED one seated scientist, one work per {} village-clock ticks, no duplicate; reload={}",ScienceBalance.WORK_TICKS,reload);mc.execute(mc::stop);});}
 }catch(Exception ex){stopped=true;com.mojang.logging.LogUtils.getLogger().error("ASTRA_SCIENCE FAILED",ex);mc.stop();}}
}
