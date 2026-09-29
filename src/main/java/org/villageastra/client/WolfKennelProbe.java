package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-138 IV in the real client: a yard of level IV north of the village with its kennel beside the byre, two wolves tamed by the player and
 *  taken into the kennel, four pieces of beef in its bin, the player (their owner) back at the village, far away. By day both wolves keep to
 *  the yard's lane and walks, out of the pens; at night each lies on its own straw in the kennel and has eaten one piece from the bin.
 *  The camera stands more than OWNER_NEAR blocks off the wolves (it is their owner). Frames: the wolves in the yard by day, the wolves in the kennel at night. */
final class WolfKennelProbe {
 private static int phase,ticks;private static volatile String failure,progress="",dayFacts="",nightFacts="";private static volatile boolean ready,dayOk,nightOk;
 private static volatile UUID yardId,kennelId;private static final List<UUID> WOLVES=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.wolfKennelProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-wolves-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_WOLVES screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 private static void lay(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building b,String design){for(var cell:BuildingPlacement.layout(e,b,design).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);}
 private static void fixture(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);st.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(6000);l.setWeatherParameters(24000,0,false,false);
  for(int n=st.civilization().level()+1;n<=4;n++)st.civilization().completedHallUpgrade(n);
  var yard=new Settlement.Building(Settlement.childId(st.id(),"building/probe-wolf-yard"),"livestock",4,0,-80);st.addBuilding(yard);yardId=yard.id();
  for(int n=2;n<=4;n++)st.raiseBuildingLevel(yard.id(),n);yard=building(e,yard.id());
  var o=BuildingPlacement.origin(e,yard);
  for(int x=-10;x<27;x++)for(int z=-8;z<31;z++){var g=o.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=1;y<16;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),3);}
  lay(l,e,yard,BuildingTiers.layoutId("livestock",4));
  var kind=Annexes.kind(VillageWolves.TYPE);var ko=Annexes.origin(e,yard,kind).subtract(e.center());
  var kennel=new Settlement.Building(Settlement.childId(st.id(),"building/probe-kennel"),VillageWolves.TYPE,ko.getX(),ko.getY(),ko.getZ(),yard.rotation());st.addBuilding(kennel);st.linkAnnex(kennel.id(),yard.id());kennelId=kennel.id();
  lay(l,e,kennel,VillageWolves.TYPE);
  var bin=LogisticsRoutes.chest(l,e,kennel);if(bin==null)throw new IllegalStateException("No kennel bin");bin.setItem(0,new ItemStack(Items.BEEF,4));
  for(int i=0;i<2;i++){var w=EntityType.WOLF.create(l);var at=LivestockPens.at(e,yard,new BlockPos(8,1,10+3*i));w.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);w.tame(p);l.addFreshEntity(w);
   if(!VillageWolves.enlist(l,e,w,kennel))throw new IllegalStateException("Wolf not enlisted");WOLVES.add(w.getUUID());}
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_WOLVES fixture yard IV with its kennel at {}, two wolves of the player enlisted",BuildingPlacement.origin(e,kennel).toShortString());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 /** Where the wolves are: day — in the yard's lot and in no pen; night — on their beds, fed. */
 private static void sample(Minecraft mc,boolean night){var s=mc.getSingleplayerServer();s.execute(()->{try{var l=s.overworld();var e=entry(s);var yard=building(e,yardId);var kennel=building(e,kennelId);
  var list=VillageWolves.wolves(l,e);var sb=new StringBuilder();int good=0;
  for(var id:WOLVES){if(!(l.getEntity(id) instanceof Wolf w)){sb.append("missing ");continue;}
   var local=BuildingPlacement.local(e,yard,w.blockPosition());boolean inPen=LivestockPens.built(4).stream().anyMatch(pn->LivestockPens.fenced(e,yard,pn,w.blockPosition()));
   var b=WolfKennelGoal.BEDS[WolfKennelGoal.bed(list,id)];var bed=BuildingPlacement.at(e,kennel,b[0],b[1],b[2]);double toBed=Math.sqrt(w.distanceToSqr(bed.getX()+.5,bed.getY(),bed.getZ()+.5));
   boolean ok=night?toBed<1.6&&VillageWolves.fed(l,w)&&w.isInSittingPose():local.getX()>=-1&&local.getX()<=17&&local.getZ()>=0&&local.getZ()<25&&!inPen;
   var path=w.getNavigation().getPath();String nav=path==null?"none":(path.canReach()?"reach":"partial")+"->"+(path.getEndNode()==null?"-":path.getEndNode().asBlockPos().toShortString())+" bedAt="+bed.toShortString();
   if(ok)good++;sb.append(String.format(Locale.ROOT,"[local=%s pen=%b bed=%.1f fed=%b lying=%b nav=%s goals=%s] ",local.toShortString(),inPen,toBed,VillageWolves.fed(l,w),w.isInSittingPose(),nav,w.goalSelector.getRunningGoals().map(g->g.getGoal().getClass().getSimpleName()).toList()));}
  var bin=LogisticsRoutes.chest(l,e,kennel);int beef=bin==null?-1:LogisticsRoutes.count(bin,x->x.is(Items.BEEF));
  var gates=new StringBuilder();for(var pn:LivestockPens.built(4))gates.append(pn.index()).append(Gates.isOpen(l.getBlockState(LivestockPens.at(e,yard,pn.gate())))?"open ":"shut ");
  progress=(night?"night ":"day ")+"good="+good+"/"+WOLVES.size()+" beef="+beef+" gates="+gates+sb;
  if(good==WOLVES.size()){if(night&&beef==2){nightOk=true;nightFacts=progress;}else if(!night){dayOk=true;dayFacts=progress;}}
 }catch(Exception ex){failure=ex.toString();}});}
 /** The camera at a cell of the yard (or the kennel), turned to look at another of its cells, whatever the yard's turn. */
 private static void look(Minecraft mc,int x,int y,int z,int tx,int ty,int tz,boolean kennel){var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);var b=building(e,kennel?kennelId:yardId);
  var at=BuildingPlacement.at(e,b,x,y,z);var to=BuildingPlacement.at(e,b,tx,ty,tz);double dx=to.getX()-at.getX(),dy=to.getY()-at.getY(),dz=to.getZ()-at.getZ();
  float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)Math.toDegrees(Math.atan2(-dy,Math.sqrt(dx*dx+dz*dz)));
  s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),at.getX()+.5,at.getY(),at.getZ()+.5,yaw,pitch);});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Wolves timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  // Day: the wolves go about the yard for half a minute with their owner away, then are sampled until both are in the yard, out of the pens.
  else if(phase==1&&ready&&ticks<=600&&ticks%100==0){sample(mc,false);dayOk=false;LogUtils.getLogger().info("ASTRA_WOLVES early {}",progress);}
  else if(phase==1&&ready&&ticks>600&&ticks%20==0){sample(mc,false);if(ticks%200==0)LogUtils.getLogger().info("ASTRA_WOLVES progress {}",progress);if(dayOk){phase=2;ticks=0;look(mc,8,13,40,8,1,15,false);}}
  else if(phase==2&&ticks>40){mc.options.hideGui=true;capture(mc,"day");mc.options.hideGui=false;
   var s=mc.getSingleplayerServer();s.execute(()->{s.overworld().setDayTime(18000);var e=entry(s);s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);});phase=3;ticks=0;}
  // Night: to their beds in the kennel, each eats its piece.
  else if(phase==3&&ticks>100&&ticks%20==0){sample(mc,true);if(ticks%200==0)LogUtils.getLogger().info("ASTRA_WOLVES progress {}",progress);if(nightOk){phase=4;ticks=0;look(mc,2,5,-13,2,1,4,true);}}
  else if(phase==4&&ticks>40){mc.options.hideGui=true;capture(mc,"night");mc.options.hideGui=false;
   LogUtils.getLogger().info("ASTRA_WOLVES VERIFIED two wolves of the player at the kennel with their owner away: by day in the yard out of the pens, at night each on its own straw, fed from the bin: day {} | night {}; reload=false",dayFacts,nightFacts);
   phase=5;mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_WOLVES FAILED",ex);phase=99;mc.stop();}}
}
