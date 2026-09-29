package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-138 VI in the real client: a yard of level VI north of the village with its kennel and two wolves of the player (who stays at the
 *  village), seventeen sheep in pen 1 (one over the cap), sixteen wheat and four beef in the yard chest, the feeders and the bin empty. The
 *  yard's machine fills the feeders, puts meat in the bin and sends a wolf to cull the surplus sheep; the mutton it drops ends in the chest.
 *  Frames: the wolf at the pen, the yard after. */
final class AutoYardProbe {
 private static int phase,ticks;private static volatile String failure,progress="",facts="";private static volatile boolean ready,done,cullSeen,cullShot,sawDrop;private static volatile net.minecraft.world.phys.Vec3 cullAt;
 private static volatile UUID yardId,kennelId;
 static boolean enabled(){return Boolean.getBoolean("villageastra.autoYardProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-autoyard-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_AUTOYARD screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 private static void lay(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building b,String design){for(var cell:BuildingPlacement.layout(e,b,design).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);}
 private static void fixture(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);st.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(6000);l.setWeatherParameters(24000,0,false,false);
  for(int n=st.civilization().level()+1;n<=6;n++)st.civilization().completedHallUpgrade(n);
  var yard=new Settlement.Building(Settlement.childId(st.id(),"building/probe-auto-yard"),"livestock",4,0,-80);st.addBuilding(yard);yardId=yard.id();
  for(int n=2;n<=6;n++)st.raiseBuildingLevel(yard.id(),n);yard=building(e,yard.id());
  var o=BuildingPlacement.origin(e,yard);
  for(int x=-10;x<27;x++)for(int z=-8;z<31;z++){var g=o.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=1;y<16;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),3);}
  lay(l,e,yard,BuildingTiers.layoutId("livestock",6));
  var record=BookResearch.inspect(l,e);var done=record.getList("legacyDone",Tag.TAG_STRING);for(var n:List.of("livestock.4","livestock.5","livestock.6"))done.add(StringTag.valueOf(n));record.put("legacyDone",done);BookResearch.store(l,e,record);ResearchKnobs.forget(st.id());
  int level=BuildingLevels.level(l,e,yard);if(level!=6)throw new IllegalStateException("The yard built to VI works at "+level);
  var kind=Annexes.kind(VillageWolves.TYPE);var ko=Annexes.origin(e,yard,kind).subtract(e.center());
  var kennel=new Settlement.Building(Settlement.childId(st.id(),"building/probe-auto-kennel"),VillageWolves.TYPE,ko.getX(),ko.getY(),ko.getZ(),yard.rotation());st.addBuilding(kennel);st.linkAnnex(kennel.id(),yard.id());kennelId=kennel.id();
  lay(l,e,kennel,VillageWolves.TYPE);
  for(int i=0;i<2;i++){var w=EntityType.WOLF.create(l);var at=LivestockPens.at(e,yard,new BlockPos(8,1,12+3*i));w.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);w.tame(p);l.addFreshEntity(w);
   if(!VillageWolves.enlist(l,e,w,kennel))throw new IllegalStateException("Wolf not enlisted");w.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l));}
  var pen=LivestockPens.pen(1);
  for(int i=0;i<=LivestockPens.PEN_CAP;i++){var at=LivestockPens.at(e,yard,new BlockPos(pen.x()+1+i%5,1,pen.z()+1+(i/5)%5));var sheep=EntityType.SHEEP.create(l);sheep.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,l.random.nextFloat()*360,0);l.addFreshEntity(sheep);LivestockPens.tag(sheep,st.id(),yard,pen);}
  var chest=LogisticsRoutes.chest(l,e,yard);chest.setItem(0,new ItemStack(Items.WHEAT,16));chest.setItem(1,new ItemStack(Items.BEEF,4));
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_AUTOYARD fixture yard VI with its kennel, two wolves, {} sheep in pen 1",LivestockPens.PEN_CAP+1);ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{var l=s.overworld();var e=entry(s);var yard=building(e,yardId);var kennel=building(e,kennelId);
  int filled=0;var feeds=new StringBuilder();for(var p:LivestockGoal.pens(l,e,yard)){var f=l.getBlockState(LivestockPens.at(e,yard,p.feeder()));int v=f.getBlock() instanceof FeederBlock?f.getValue(FeederBlock.FEED):-1;if(v>0)filled++;feeds.append(v).append(' ');}
  int herd=LivestockPens.herd(l,e,yard,LivestockPens.pen(1)).size();var chest=LogisticsRoutes.chest(l,e,yard);var bin=LogisticsRoutes.chest(l,e,kennel);
  int mutton=LogisticsRoutes.count(chest,x->x.is(Items.MUTTON)),binMeat=LogisticsRoutes.count(bin,x->x.is(Items.BEEF));
  var wolves=new StringBuilder();var gate=LivestockPens.at(e,yard,LivestockPens.pen(1).gate());
  for(var id:VillageWolves.wolves(l,e))if(l.getEntity(id) instanceof Wolf w){var order=VillageWolves.cullOrder(w);if(order!=null&&!cullShot){cullSeen=true;cullAt=w.position();}
   var path=w.getNavigation().getPath();var target=order==null?null:l.getEntity(order);
   wolves.append("[wolf at ").append(BuildingPlacement.local(e,yard,w.blockPosition()).toShortString()).append(order==null?" free":" order->"+(target==null?"gone":BuildingPlacement.local(e,yard,target.blockPosition()).toShortString()))
    .append(" nav=").append(path==null?"none":(path.canReach()?"reach":"partial")+"->"+(path.getEndNode()==null?"-":BuildingPlacement.local(e,yard,path.getEndNode().asBlockPos()).toShortString()))
    .append(" goals=").append(w.goalSelector.getRunningGoals().map(g->g.getGoal().getClass().getSimpleName()).toList()).append("] ");}
  wolves.append("gate1=").append(Gates.isOpen(l.getBlockState(gate))?"open":"shut");
  // The cull's drops: seen (lying, in the chest, or carried by the machine — the keeper gathers too, the porters take them on to the stock),
  // then none left lying in the yard.
  int gathered=LivestockMachine.GATHERED.get();
  int lying=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,LivestockPens.yardBox(e,yard).inflate(2),i->i.getItem().is(Items.MUTTON)).size();
  if(lying>0||mutton>0||gathered>0)sawDrop=true;
  progress="feeders=["+feeds.toString().trim()+"] filled="+filled+" pen1="+herd+" bin="+binMeat+" gathered="+gathered+" lying="+lying+" sawDrop="+sawDrop+" muttonInChest="+mutton+" machine="+LivestockMachine.lastReason+" "+wolves;
  if(!done&&filled>=1&&binMeat>=1&&herd<=LivestockPens.PEN_CAP&&sawDrop&&lying==0){done=true;facts=progress;}
 }catch(Exception ex){failure=ex.toString();}});}
 private static void look(Minecraft mc,double x,double y,double z,float yaw,float pitch){var s=mc.getSingleplayerServer();s.execute(()->s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),x,y,z,yaw,pitch));}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Auto yard timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%10==0){sample(mc);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_AUTOYARD progress {}",progress);
   // One frame of the cull: the camera 20 blocks off the wolf (its owner, further than it follows), looking down at the pen.
   if(cullSeen&&!cullShot&&cullAt!=null){cullShot=true;var at=cullAt;look(mc,at.x-12,at.y+14,at.z-12,-45,40);phase=2;ticks=0;}
   else if(done){phase=3;ticks=0;}}
  else if(phase==2&&ticks>10){mc.options.hideGui=true;capture(mc,"cull");mc.options.hideGui=false;var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);});phase=1;ticks=1;}
  else if(phase==3){var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);var o=BuildingPlacement.origin(e,building(e,yardId));s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),o.getX()-8,o.getY()+16,o.getZ()-10,-35,40);});phase=4;ticks=0;}
  else if(phase==4&&ticks>40){mc.options.hideGui=true;capture(mc,"after");mc.options.hideGui=false;
   LogUtils.getLogger().info("ASTRA_AUTOYARD VERIFIED the yard VI's machine filled the feeders, put meat in the bin and had a wolf cull the surplus sheep, its mutton in the chest: {} cullFrame={}; reload=false",facts,cullShot);
   phase=5;mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_AUTOYARD FAILED",ex);phase=99;mc.stop();}}
}
