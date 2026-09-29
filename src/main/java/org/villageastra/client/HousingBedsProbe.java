package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** OWNER-HOUSING-BEDS in the real client: a standard house of level III and a big house of level VI, built to their level designs, hold the
 *  owner's ladder (4 and 10 people). At dusk every one of those people walks in and lies down in a bed of their own — the six upstairs of the
 *  big house by its stair. Frames: the ground floor of the house III and the upper storey of the big house with everyone asleep. */
final class HousingBedsProbe {
 private record House(Settlement.Building b,String type,int level){}
 private static int phase,ticks,shot;private static volatile String failure,progress="";private static volatile boolean ready,allAsleep;
 private static final List<House> HOUSES=new ArrayList<>();private static final Map<UUID,UUID> PEOPLE=new HashMap<>();
 private static volatile String facts="";
 static boolean enabled(){return Boolean.getBoolean("villageastra.housingBedsProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-housing-beds-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_HOUSING_BEDS screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static void fixture(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{
  var l=server.overworld();var e=entry(server);var s=e.settlement();var p=server.getPlayerList().getPlayers().get(0);
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);l.setWeatherParameters(6000,0,false,false);
  // A clear lawn beside the village, the two houses on it side by side, their doors to the north.
  var lawn=e.center().offset(-60,0,40);
  for(int x=-8;x<40;x++)for(int z=-12;z<20;z++){var ground=lawn.offset(x,0,z);l.setBlock(ground.below(),Blocks.DIRT.defaultBlockState(),2);l.setBlock(ground,Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<24;y++)l.setBlock(ground.above(y),Blocks.AIR.defaultBlockState(),2);}
  HOUSES.clear();PEOPLE.clear();
  HOUSES.add(new House(new Settlement.Building(Settlement.childId(s.id(),"building/beds-home"),"home",-60,0,40,0,3),"home",3));
  HOUSES.add(new House(new Settlement.Building(Settlement.childId(s.id(),"building/beds-home-2"),"home_2",-44,0,40,0,6),"home_2",6));
  for(var h:HOUSES){s.addBuilding(h.b());
   for(var cell:BuildingPlacement.layout(e,h.b(),BuildingTiers.layoutId(h.type(),h.level())).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   s.addHome(new Settlement.Home(h.b().id(),1,HousingLadder.capacity(h.type(),1),true));
   int level=BuildingTiers.level(l,e,h.b());if(level!=h.level())throw new IllegalStateException(h.type()+" built to level "+h.level()+" reads as level "+level);
   // The finished project: the house takes the beds really standing in it.
   int cap=HousingLadder.upgraded(l,e,h.b(),h.level()),want=HousingLadder.capacity(h.type(),h.level());
   if(cap!=want)throw new IllegalStateException(h.type()+" "+h.level()+" holds "+cap+", the ladder says "+want);
   var door=BuildingPlacement.origin(e,h.b());
   for(int i=0;i<want;i++){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,i%2==0,null,null,-1);s.admit(r,h.b().id());
    var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.moveTo(door.getX()+1.5+i%5,door.getY()+1,door.getZ()-5.5-i/5,0,0);l.addFreshEntity(npc);PEOPLE.put(r.id(),h.b().id());}}
  SettlementData.get(server).setDirty();
  p.setGameMode(GameType.SPECTATOR);var eye=lawn.offset(12,8,-14);p.teleportTo(l,eye.getX()+.5,eye.getY(),eye.getZ()+.5,0,25);
  l.setDayTime(13000);
  LogUtils.getLogger().info("ASTRA_HOUSING_BEDS fixture home@3 and home_2@6 on a lawn at {}, {} people at dusk",lawn.toShortString(),PEOPLE.size());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(net.minecraft.server.MinecraftServer server){server.execute(()->{var l=server.overworld();var e=entry(server);
  var beds=new HashMap<UUID,Set<BlockPos>>();int asleep=0,upstairs=0,loaded=0;var awake=new ArrayList<String>();
  for(var person:PEOPLE.entrySet()){if(!(l.getEntity(person.getKey()) instanceof ResidentEntity npc))continue;loaded++;
   var at=npc.getSleepingPos().orElse(null);
   if(npc.isSleeping()&&at!=null){asleep++;beds.computeIfAbsent(person.getValue(),k->new HashSet<>()).add(at);
    var house=HOUSES.stream().filter(h->h.b().id().equals(person.getValue())).findFirst().orElseThrow();
    if(house.type().equals("home_2")&&at.getY()>=BuildingPlacement.origin(e,house.b()).getY()+5)upstairs++;}
   else if(awake.size()<4)awake.add(npc.workStatus()+"@"+npc.blockPosition().toShortString());}
  int distinct=beds.values().stream().mapToInt(Set::size).sum();
  allAsleep=loaded==PEOPLE.size()&&asleep==PEOPLE.size()&&distinct==PEOPLE.size();
  var parts=new ArrayList<String>();for(var h:HOUSES)parts.add(h.type()+"@"+h.level()+"="+beds.getOrDefault(h.b().id(),Set.of()).size()+"/"+HousingLadder.capacity(h.type(),h.level()));
  facts=String.join(" ",parts)+" upstairs="+upstairs;
  progress="loaded="+loaded+" asleep="+asleep+"/"+PEOPLE.size()+" beds="+distinct+" "+facts+" awake="+awake;});}
 private static void look(Minecraft mc,int house,double x,double y,double z,float yaw,float pitch){var server=mc.getSingleplayerServer();
  server.execute(()->{var e=entry(server);var o=BuildingPlacement.origin(e,HOUSES.get(house).b());var p=server.getPlayerList().getPlayers().get(0);
   p.teleportTo(server.overworld(),o.getX()+x,o.getY()+y,o.getZ()+z,yaw,pitch);});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>7000)throw new IllegalStateException("Housing beds timeout phase="+phase+" "+progress);
  var server=mc.getSingleplayerServer();
  if(phase==0&&ticks>80){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%10==0){sample(server);
   if(ticks%200==0)LogUtils.getLogger().info("ASTRA_HOUSING_BEDS progress {}",progress);
   if(allAsleep){phase=2;ticks=0;mc.options.hideGui=true;look(mc,0,3.5,2.0,3.5,0,50);}
   else if(ticks>4000)throw new IllegalStateException("Not everyone got a bed of their own: "+progress);}
  // Inside, the camera in the air over the floor: the house III from its doorway toward its back beds, the big house's upper storey from its middle.
  else if(phase==2&&ticks>60){capture(mc,"home-3");look(mc,1,5.5,6.0,5.4,180,40);phase=3;ticks=0;}
  else if(phase==3&&ticks>60){capture(mc,"home_2-6-upstairs");look(mc,1,5.5,6.0,3.0,0,40);phase=4;ticks=0;}
  else if(phase==4&&ticks>60){capture(mc,"home_2-6-back");sample(server);phase=5;ticks=0;}
  else if(phase==5&&ticks>10){mc.options.hideGui=false;
   if(!allAsleep)throw new IllegalStateException("Somebody got up during the frames: "+progress);
   LogUtils.getLogger().info("ASTRA_HOUSING_BEDS VERIFIED every one of {} people asleep in a bed of their own: {}; reload=false",PEOPLE.size(),facts);
   mc.stop();phase=6;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_HOUSING_BEDS FAILED",ex);mc.options.hideGui=false;mc.stop();}}
}
