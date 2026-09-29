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
/** AD-151 in the real client: three schools north of the village, laid at levels I, IV and VI side by side; the VI one with its teacher and
 *  five children of the village walking to the lesson. The class takes all five (eight seats at VI) and its military class the two eldest.
 *  Frames: the three schools, the children at the lesson. */
final class SchoolProbe {
 private static int phase,ticks;private static volatile String failure,progress="",facts="";private static volatile boolean ready,done;
 private static final List<UUID> SCHOOLS=new ArrayList<>(),KIDS=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.schoolProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-school-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_SCHOOL screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 private static void fixture(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);st.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(3000);l.setWeatherParameters(24000,0,false,false);
  for(int n=st.civilization().level()+1;n<=6;n++)st.civilization().completedHallUpgrade(n);
  int[] levels={1,4,6};
  for(int i=0;i<3;i++){var b=new Settlement.Building(Settlement.childId(st.id(),"building/probe-school-"+levels[i]),"school",-30+16*i,0,-70);st.addBuilding(b);
   for(int n=2;n<=levels[i];n++)st.raiseBuildingLevel(b.id(),n);b=building(e,b.id());SCHOOLS.add(b.id());
   var o=BuildingPlacement.origin(e,b);for(int x=-3;x<15;x++)for(int z=-3;z<16;z++){var g=o.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=1;y<18;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),3);}
   for(var cell:BuildingPlacement.layout(e,b,BuildingTiers.layoutId("school",levels[i])).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);}
  var six=building(e,SCHOOLS.get(2));int level=BuildingLevels.level(l,e,six);if(level!=6)throw new IllegalStateException("The school laid at VI works at "+level);
  var home=new Settlement.Home(Settlement.childId(st.id(),"home/probe-school"),1,20,true);st.addHome(home);
  var teacher=new Resident(Settlement.childId(st.id(),"probe-teacher"),Resident.Life.ADULT,true,null,null,-1);st.admit(teacher,home.id());st.assign(teacher.id(),Profession.TEACHER,six.id());
  var station=LogisticsRoutes.position(e,six);
  var tnpc=VillageAstra.RESIDENT.get().create(l);tnpc.bind(st.id(),st.resident(teacher.id()));tnpc.moveTo(station.getX()+.5,station.getY(),station.getZ()+1.5,0,0);l.addFreshEntity(tnpc);
  long now=SettlementData.get(s).clock().ticks();
  for(int i=0;i<5;i++){var r=new Resident(Settlement.childId(st.id(),"probe-kid-"+i),Resident.Life.CHILD,false,null,null,-1);r.born(Math.max(0,now-100*(5-i)));st.admit(r,home.id());KIDS.add(r.id());
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(st.id(),r);var at=BuildingPlacement.origin(e,six).offset(5+i%3,1,-6-i/3);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(npc);}
  SettlementData.get(s).setDirty();
  var o=BuildingPlacement.origin(e,building(e,SCHOOLS.get(1)));p.teleportTo(l,o.getX()+5.5,o.getY()+14,o.getZ()-22,0,30);
  LogUtils.getLogger().info("ASTRA_SCHOOL fixture schools I, IV, VI; teacher and five children at the VI school");ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{var l=s.overworld();var e=entry(s);
  var station=Population.schoolStation(l,e);var taught=station==null?Set.<UUID>of():Population.classroom(l,e,station);
  long cadets=KIDS.stream().filter(id->e.settlement().resident(id).cadet()).count();long mine=KIDS.stream().filter(taught::contains).count();
  long ticks=KIDS.stream().mapToLong(id->e.settlement().resident(id).schoolTicks()).sum();
  progress="station="+(station!=null)+" taught="+mine+" cadets="+cadets+" schoolTicks="+ticks+" seats="+CoreEffects.value("school","pupils",BuildingLevels.best(l,e,"school"));
  if(!done&&mine==5&&cadets==2&&ticks>0){done=true;facts=progress;}
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>8000)throw new IllegalStateException("School timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>60){mc.options.hideGui=true;capture(mc,"levels");mc.options.hideGui=false;phase=2;ticks=0;}
  else if(phase==2&&ticks%20==0){sample(mc);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_SCHOOL progress {}",progress);
   if(done){var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);var st=LogisticsRoutes.position(e,building(e,SCHOOLS.get(2)));var six=building(e,SCHOOLS.get(2));var eye=BuildingPlacement.at(e,six,5,1,4);double dx=st.getX()-eye.getX(),dz=st.getZ()-eye.getZ();
     // Inside the classroom, four blocks from the teacher's place, looking at it: the lesson is indoors.
     s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),eye.getX()+.5,eye.getY(),eye.getZ()+.5,(float)Math.toDegrees(Math.atan2(-dx,dz)),20);});phase=3;ticks=0;}}
  else if(phase==3&&ticks>30){mc.options.hideGui=true;capture(mc,"lesson");mc.options.hideGui=false;
   LogUtils.getLogger().info("ASTRA_SCHOOL VERIFIED schools I, IV, VI laid; the VI school's class took the five children and its military class the two eldest: {}; reload=false",facts);
   phase=4;mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_SCHOOL FAILED",ex);phase=99;mc.stop();}}
}
