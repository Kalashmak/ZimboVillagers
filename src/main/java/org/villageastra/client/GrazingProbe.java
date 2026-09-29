package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-138 V in the real client: a yard of level V north of the village, livestock.5 known, its keeper posted, its kennel with two wolves
 *  of the player (who stays at the village), sheep in pen 1 and cows in pen 2 — and no feed anywhere. By day the pens stand open and the
 *  herd grazes on the grass around the yard; at night every beast is home, the gates are shut and the wolves lie in the kennel.
 *  Frames: the herd out on the grass, the yard at night with the herd home. */
final class GrazingProbe {
 private static int phase,ticks;private static volatile String failure,progress="",outFacts="",homeFacts="";private static volatile boolean ready,outOk,homeOk;
 private static volatile UUID yardId,kennelId;private static final List<UUID> WOLVES=new ArrayList<>(),HERD=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.grazingProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-grazing-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_GRAZING screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 private static void lay(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building b,String design){for(var cell:BuildingPlacement.layout(e,b,design).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);}
 private static void fixture(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);st.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(3000);l.setWeatherParameters(24000,0,false,false);
  for(int n=st.civilization().level()+1;n<=5;n++)st.civilization().completedHallUpgrade(n);
  var yard=new Settlement.Building(Settlement.childId(st.id(),"building/probe-graze-yard"),"livestock",4,0,-80);st.addBuilding(yard);yardId=yard.id();
  for(int n=2;n<=5;n++)st.raiseBuildingLevel(yard.id(),n);yard=building(e,yard.id());
  var o=BuildingPlacement.origin(e,yard);
  for(int x=-24;x<41;x++)for(int z=-12;z<45;z++){var g=o.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=1;y<16;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),3);}
  lay(l,e,yard,BuildingTiers.layoutId("livestock",5));
  var record=BookResearch.inspect(l,e);var done=record.getList("legacyDone",Tag.TAG_STRING);done.add(StringTag.valueOf(LivestockGrazing.RESEARCH));done.add(StringTag.valueOf("livestock.4"));record.put("legacyDone",done);BookResearch.store(l,e,record);ResearchKnobs.forget(st.id());
  int level=BuildingLevels.level(l,e,yard);if(level!=5)throw new IllegalStateException("The yard built to V works at "+level);
  var kind=Annexes.kind(VillageWolves.TYPE);var ko=Annexes.origin(e,yard,kind).subtract(e.center());
  var kennel=new Settlement.Building(Settlement.childId(st.id(),"building/probe-graze-kennel"),VillageWolves.TYPE,ko.getX(),ko.getY(),ko.getZ(),yard.rotation());st.addBuilding(kennel);st.linkAnnex(kennel.id(),yard.id());kennelId=kennel.id();
  lay(l,e,kennel,VillageWolves.TYPE);var bin=LogisticsRoutes.chest(l,e,kennel);bin.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF,4));
  for(int i=0;i<2;i++){var w=EntityType.WOLF.create(l);var at=LivestockPens.at(e,yard,new BlockPos(8,1,10+3*i));w.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);w.tame(p);l.addFreshEntity(w);
   if(!VillageWolves.enlist(l,e,w,kennel))throw new IllegalStateException("Wolf not enlisted");WOLVES.add(w.getUUID());}
  // Pen 1 sheep, pen 2 cows, three each; no feed in the chest, the feeders empty.
  for(var pen:List.of(LivestockPens.pen(1),LivestockPens.pen(2)))for(int i=0;i<3;i++){var at=LivestockPens.at(e,yard,new BlockPos(pen.x()+2+i,1,pen.z()+3));
   Animal a=pen.index()==1?EntityType.SHEEP.create(l):EntityType.COW.create(l);a.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,l.random.nextFloat()*360,0);l.addFreshEntity(a);LivestockPens.tag(a,st.id(),yard,pen);HERD.add(a.getUUID());}
  var room=new Settlement.Home(Settlement.childId(st.id(),"home/probe-graze-keeper"),1,2,true);st.addHome(room);
  var keeper=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);st.admit(keeper,room.id());st.assign(keeper.id(),Profession.LIVESTOCK_FARMER,yard.id());
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(st.id(),st.resident(keeper.id()));var door=BuildingPlacement.at(e,yard,5,1,-1);npc.moveTo(door.getX()+.5,door.getY(),door.getZ()+.5,0,0);l.addFreshEntity(npc);
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_GRAZING fixture yard V with its kennel, two wolves, three sheep and three cows, no feed");ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(Minecraft mc,boolean night){var s=mc.getSingleplayerServer();s.execute(()->{try{var l=s.overworld();var e=entry(s);var yard=building(e,yardId);var kennel=building(e,kennelId);
  int out=0,home=0,grass=0;var sb=new StringBuilder();
  for(var id:HERD){if(!(l.getEntity(id) instanceof Animal a)){sb.append("missing ");continue;}var pen=LivestockPens.pen(a.getPersistentData().getInt(LivestockPens.PEN_TAG));
   boolean in=LivestockPens.fenced(e,yard,pen,a.blockPosition());if(in)home++;else{out++;if(l.getBlockState(a.blockPosition().below()).is(Blocks.GRASS_BLOCK))grass++;
    var path=a.getNavigation().getPath();sb.append("[p").append(pen.index()).append(" at ").append(BuildingPlacement.local(e,yard,a.blockPosition()).toShortString()).append(" on ").append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(a.blockPosition().below()).getBlock()).getPath())
     .append(" nav=").append(path==null?"none":(path.canReach()?"reach":"partial")+"->"+(path.getEndNode()==null?"-":BuildingPlacement.local(e,yard,path.getEndNode().asBlockPos()).toShortString())).append(" goals=").append(a.goalSelector.getRunningGoals().map(g->g.getGoal().getClass().getSimpleName()).toList()).append("] ");}}
  var gates=new StringBuilder();boolean shut=true,open=true;for(var pen:List.of(LivestockPens.pen(1),LivestockPens.pen(2))){boolean o=Gates.isOpen(l.getBlockState(LivestockPens.at(e,yard,pen.gate())));shut&=!o;open&=o;gates.append(pen.index()).append(o?"open ":"shut ");}
  int bed=0;var list=VillageWolves.wolves(l,e);for(var id:WOLVES)if(l.getEntity(id) instanceof Wolf w){var b=WolfKennelGoal.BEDS[WolfKennelGoal.bed(list,id)];var at=BuildingPlacement.at(e,kennel,b[0],b[1],b[2]);if(w.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)<2.6)bed++;}
  String keeper="";for(var r:e.settlement().residents())if(r.profession()==Profession.LIVESTOCK_FARMER&&e.settlement().workplace(r.id())!=null&&e.settlement().workplace(r.id()).id().equals(yardId)&&l.getEntity(r.id()) instanceof ResidentEntity npc)keeper+=npc.workStatus()+" ";
  progress=(night?"night":"day")+" out="+out+" onGrass="+grass+" home="+home+"/"+HERD.size()+" gates="+gates+"wolvesInBed="+bed+" keeper="+keeper+sb;
  if(!night&&out>=3&&grass>=3&&open){outOk=true;outFacts=progress;}
  if(night&&home==HERD.size()&&shut&&bed==WOLVES.size()){homeOk=true;homeFacts=progress;}
 }catch(Exception ex){failure=ex.toString();}});}
 private static void look(Minecraft mc,int x,int y,int z,int tx,int ty,int tz){var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);var b=building(e,yardId);
  var at=BuildingPlacement.at(e,b,x,y,z);var to=BuildingPlacement.at(e,b,tx,ty,tz);double dx=to.getX()-at.getX(),dy=to.getY()-at.getY(),dz=to.getZ()-at.getZ();
  float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)Math.toDegrees(Math.atan2(-dy,Math.sqrt(dx*dx+dz*dz)));
  s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),at.getX()+.5,at.getY(),at.getZ()+.5,yaw,pitch);});}
 private static void toVillage(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>16000)throw new IllegalStateException("Grazing timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  // Day: the keeper opens the hungry pens, the herd goes out onto the grass.
  else if(phase==1&&ready&&ticks%20==0&&ticks>200){sample(mc,false);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_GRAZING progress {}",progress);if(outOk){phase=2;ticks=0;look(mc,-20,16,-10,8,1,12);}}
  else if(phase==2&&ticks>40){mc.options.hideGui=true;capture(mc,"out");mc.options.hideGui=false;toVillage(mc);
   var s=mc.getSingleplayerServer();s.execute(()->s.overworld().setDayTime(13500));phase=3;ticks=0;}
  // Dusk and night: home through the gates, the gates shut, the wolves to bed.
  else if(phase==3&&ticks%20==0&&ticks>100){sample(mc,true);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_GRAZING progress {}",progress);if(homeOk){phase=4;ticks=0;look(mc,-20,16,-10,8,1,12);}}
  else if(phase==4&&ticks>40){mc.options.hideGui=true;capture(mc,"home");mc.options.hideGui=false;
   LogUtils.getLogger().info("ASTRA_GRAZING VERIFIED a hungry yard V grazed its herd on the village grass by day and had it home, gates shut and the wolves in the kennel at night: day {} | night {}; reload=false",outFacts,homeFacts);
   phase=5;mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_GRAZING FAILED",ex);phase=99;mc.stop();}}
}
