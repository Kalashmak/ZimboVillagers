package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-095: a recruit really walks to the drill ground of the barracks by day, its drill is counted only there, and once trained the labor
 *  office gives it a military post. Fixture: a barracks beside the hall, one adult newcomer enlisted with its drill nearly done. */
final class DrillProbe {
 static final long LEFT=600;
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,trained,posted,atGround;
 private static volatile UUID recruit;private static volatile long startDrill;
 static boolean enabled(){return Boolean.getBoolean("villageastra.drillSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-drill-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_DRILL screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(1000);
  // The barracks stands on levelled ground beside the hall.
  var barracks=new Settlement.Building(Settlement.childId(settlement.id(),"building/barracks-drill"),"barracks",-22,0,-6);settlement.addBuilding(barracks);
  var origin=BuildingPlacement.origin(e,barracks);
  for(int x=-3;x<16;x++)for(int z=-3;z<16;z++){l.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
   for(int y=0;y<14;y++)l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
  for(var cell:BuildingPlacement.layout(e,barracks,barracks.type()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  // A newcomer without a trade is enlisted; most of its drill is behind it, the rest it has to do on the ground.
  var home=new Settlement.Home(Settlement.childId(settlement.id(),"home/drill-probe"),1,1,true);settlement.addHome(home);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(r,home.id());r.enlist();
  if(!r.recruit())throw new IllegalStateException("The newcomer could not be enlisted");
  r.drill(Population.DRILL_REQUIRED-LEFT);startDrill=r.drillTicks();recruit=r.id();
  var at=e.center().offset(2,1,2);var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(settlement.id(),r);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(npc);
  SettlementData.get(s).setDirty();
  var ground=Population.drillGround(e);
  p.teleportTo(l,ground.getX()+6.5,ground.getY()+2,ground.getZ()+6.5,135,20);
  LogUtils.getLogger().info("ASTRA_DRILL fixture barracks at {}, drill ground {}, recruit {} with {}/{} ticks",origin.toShortString(),ground.toShortString(),r.id(),startDrill,Population.DRILL_REQUIRED);ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var r=e.settlement().resident(recruit);if(r==null||!r.alive()){failure="The recruit is gone";return;}
  var ground=Population.drillGround(e);var npc=l.getEntity(recruit) instanceof ResidentEntity x?x:null;
  double d=npc==null?-1:Math.sqrt(npc.distanceToSqr(ground.getX()+.5,ground.getY(),ground.getZ()+.5));
  if(npc!=null&&d<=Population.DRILL_RADIUS)atGround=true;
  // Drill is counted only on the ground: while the recruit is still walking there, it must not grow.
  if(!atGround&&!r.military()&&r.drillTicks()>startDrill+40){failure="Drill counted away from the ground: "+r.drillTicks()+" at distance "+d;return;}
  if(r.military())trained=true;
  if(trained&&r.profession()!=null&&r.profession().military())posted=true;
  progress="drill="+r.drillTicks()+"/"+Population.DRILL_REQUIRED+" military="+r.military()+" recruit="+r.recruit()+" profession="+r.profession()
    +" status="+(npc==null?"away":npc.workStatus())+" distance="+String.format(Locale.ROOT,"%.1f",d)+" atGround="+atGround;
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Drill timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%100==0){sample(mc);LogUtils.getLogger().info("ASTRA_DRILL progress {}",progress);
   if(atGround&&phase==1){capture(mc,"ground");phase=2;ticks=0;}}
  else if(phase==2&&ticks%100==0){sample(mc);LogUtils.getLogger().info("ASTRA_DRILL progress {}",progress);
   if(posted){phase=3;ticks=0;}
   if(ticks>6000)throw new IllegalStateException("The recruit was never trained and posted: "+progress);}
  else if(phase==3&&ticks>20){
   LogUtils.getLogger().info("ASTRA_DRILL VERIFIED the recruit walked to the drill ground, drilled there and was given a post: {}; reload=false",progress);
   mc.setScreen(null);mc.stop();phase=4;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_DRILL FAILED",ex);mc.stop();}}
}
