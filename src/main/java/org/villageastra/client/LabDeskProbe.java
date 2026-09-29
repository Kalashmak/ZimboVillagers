package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Three then six live scientists walk from outside a rotated lab to their own desks. */
final class LabDeskProbe {
 private static final List<UUID> WORKERS=new ArrayList<>();private static UUID village,labId,homeId;
 private static int phase,ticks,stable;private static volatile boolean busy,done;private static volatile String failure,capture;private static long began;
 private LabDeskProbe(){}
 static boolean enabled(){return Boolean.getBoolean("villageastra.labDeskSmoke");}
 private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
 private static Settlement.Building lab(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.id().equals(labId)).findFirst().orElseThrow();}
 private static void raise(ServerLevel l,SettlementData.Entry e,int level){for(int n=lab(e).level()+1;n<=level;n++)e.settlement().raiseBuildingLevel(labId,n);var b=lab(e);for(var cell:BuildingPlacement.layout(e,b,BuildingTiers.layoutId("laboratory",level)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);BuildingLevels.forgetBest(village);}
 private static void admit(ServerLevel l,SettlementData.Entry e,int count){while(WORKERS.size()<count){int i=WORKERS.size();var r=new Resident(Settlement.childId(village,"lab-desk/worker/"+i),Resident.Life.CHILD,false,null,null,-1);e.settlement().admit(r,homeId);r.educate();r.growUp();e.settlement().assign(r.id(),Profession.SCIENTIST,labId);
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(village,r);var at=BuildingPlacement.at(e,lab(e),3+i,1,-4);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(npc);WORKERS.add(r.id());}
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>9000)throw new IllegalStateException("Lab desks timeout phase="+phase);
  if(capture!=null){var suffix=capture;var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-lab-desks-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_LAB_DESKS screenshot {}",path);capture=null;}
  if(done){LogUtils.getLogger().info("ASTRA_LAB_DESKS VERIFIED level3=3 level6=6 walked=true distinct=true rotation=1 works=6 clockAdvanced=true");mc.stop();return;}
  if(ticks%20!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_LAB_DESKS FAILED",ex);try{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-lab-desks-failed.png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_LAB_DESKS failure screenshot {}",path);}catch(Exception ignored){}mc.stop();}}
 private static void step(ServerLevel l){var data=SettlementData.get(l.getServer());
  if(phase==0){var e=data.entries().iterator().next();village=e.settlement().id();labId=Settlement.childId(village,"lab-desk/lab");homeId=Settlement.childId(village,"lab-desk/home");
   for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc)npc.setNoAi(true);
   l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());
   e.settlement().addHome(new Settlement.Home(homeId,6,10,true));var home=new Settlement.Building(homeId,"home_2",62,0,20);e.settlement().addBuilding(home);for(int n=2;n<=6;n++)e.settlement().raiseBuildingLevel(homeId,n);
   for(var cell:BuildingPlacement.layout(e,home,"home_2@6").entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   e.settlement().addBuilding(new Settlement.Building(labId,"laboratory",40,0,20,1));raise(l,e,3);admit(l,e,3);ScienceWorks.advance(l,e,data.clock().ticks());data.setDirty();phase=1;began=l.getGameTime();
   var at=BuildingPlacement.at(e,lab(e),5,1,6);var p=l.getServer().getPlayerList().getPlayers().get(0);p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);p.teleportTo(l,at.getX()+.5,at.getY(),at.getZ()+.5,-90,15);
  }
  var e=data.entry(village);if(phase==3){if(capture!=null)return;raise(l,e,6);admit(l,e,6);ScienceWorks.advance(l,e,data.clock().ticks());data.setDirty();phase=2;began=l.getGameTime();stable=0;}int count=phase==1?3:6;var stands=new HashSet<BlockPos>();var positions=new ArrayList<net.minecraft.world.phys.Vec3>();int arrived=0;
  for(var id:WORKERS){var npc=(ResidentEntity)l.getEntity(id);require(npc!=null,"Scientist disappeared");var desk=LabDesks.of(l,e,lab(e),id);require(stands.add(desk.stand()),"Two workers share a desk");positions.add(npc.position());
   if(npc.blockPosition().equals(desk.stand())&&npc.distanceToSqr(desk.stand().getX()+.5,desk.stand().getY(),desk.stand().getZ()+.5)<.75*.75&&npc.workStatus().equals("science_writing"))arrived++;
  }
  boolean apart=true;for(int i=0;i<positions.size();i++)for(int j=i+1;j<positions.size();j++)if(positions.get(i).distanceToSqr(positions.get(j))<.6*.6)apart=false;
  if(arrived==count&&apart)stable++;else stable=0;
  if(l.getGameTime()-began>2400)throw new IllegalStateException("Scientists did not settle at own desks: "+WORKERS.stream().map(id->{var n=(ResidentEntity)l.getEntity(id);return id+" "+n.blockPosition()+" target="+LabDesks.of(l,e,lab(e),id).stand()+" status="+n.workStatus()+" goals="+n.runningGoals();}).toList());
  LogUtils.getLogger().info("ASTRA_LAB_DESKS phase={} arrived={}/{} apart={} stable={}",phase,arrived,count,apart,stable);
  if(stable<5)return;
  if(phase==1){capture="three";phase=3;stable=0;}
  else{var chest=LogisticsRoutes.chest(l,e,lab(e));require(chest!=null&&chest.countItem(VillageAstra.RESEARCH_VOLUME.get())==0,"Unexpected early works");long future=data.clock().ticks()+ScienceBalance.WORK_TICKS;int written=ScienceWorks.advance(l,e,future);require(written==6&&ScienceWorks.advance(l,e,future)==0&&chest.countItem(VillageAstra.RESEARCH_VOLUME.get())==6,"Six occupied places must write six works once");capture="six";done=true;}
 }
}
