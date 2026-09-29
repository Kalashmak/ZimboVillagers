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
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-138 in the real client: two yards north of the village — one of level I to look at, one of level III at work. Its keeper, with wheat,
 *  carrots and shears in the yard chest, fills the four feeders over the fences, breeds the pens, and drives two wild pigs into the
 *  empty fourth pen through its gate, closing it behind him. Frames: the yard of level I, the yard of level III at work, a full feeder,
 *  the yard's card in the office. */
final class LivestockProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,done,driving,driveShot;private static volatile net.minecraft.world.phys.Vec3 driveAt;private static volatile String facts="";
 private static volatile UUID yardId,lookId;
 static boolean enabled(){return Boolean.getBoolean("villageastra.livestockProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-livestock-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_LIVESTOCK screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 private static void pad(net.minecraft.server.level.ServerLevel l,BlockPos origin){for(int x=-6;x<21;x++)for(int z=-8;z<29;z++){var g=origin.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=1;y<16;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),3);}}
 private static void lay(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level){for(var cell:BuildingPlacement.layout(e,b,BuildingTiers.layoutId(b.type(),level)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);}
 private static <T extends Animal> T animal(net.minecraft.server.level.ServerLevel l,EntityType<T> type,BlockPos at){var a=type.create(l);a.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,l.random.nextFloat()*360,0);l.addFreshEntity(a);return a;}
 private static void fixture(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);st.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(6000);l.setWeatherParameters(24000,0,false,false);
  // AD-136: no building above its hall; the hall of this village stands at III.
  for(int n=st.civilization().level()+1;n<=3;n++)st.civilization().completedHallUpgrade(n);
  var look=new Settlement.Building(Settlement.childId(st.id(),"building/probe-yard-1"),"livestock",-24,0,-80);st.addBuilding(look);lookId=look.id();
  var yard=new Settlement.Building(Settlement.childId(st.id(),"building/probe-yard-3"),"livestock",4,0,-80);st.addBuilding(yard);yardId=yard.id();
  pad(l,BuildingPlacement.origin(e,look));pad(l,BuildingPlacement.origin(e,yard));lay(l,e,look,1);
  st.raiseBuildingLevel(yard.id(),2);st.raiseBuildingLevel(yard.id(),3);yard=building(e,yard.id());lay(l,e,yard,3);
  int level=BuildingLevels.level(l,e,yard);if(level!=3)throw new IllegalStateException("The yard built to III works at "+level);
  var chest=LogisticsRoutes.chest(l,e,yard);if(chest==null)throw new IllegalStateException("No yard chest");
  chest.setItem(0,new ItemStack(Items.WHEAT,64));chest.setItem(1,new ItemStack(Items.CARROT,32));chest.setItem(3,new ItemStack(Items.SHEARS));
  // Stock: pens 1..3 their kind, three adults each; pen 4 (pigs) empty, two wild pigs on the grass beside the yard.
  for(var pen:LivestockPens.all()){if(pen.index()==4)continue;var kind=pen.species();
   for(int i=0;i<3;i++){var at=LivestockPens.at(e,yard,new BlockPos(pen.x()+2+i,1,pen.z()+3));
    Animal a=switch(kind){case "cow"->animal(l,EntityType.COW,at);case "chicken"->animal(l,EntityType.CHICKEN,at);default->animal(l,EntityType.SHEEP,at);};LivestockPens.tag(a,st.id(),yard,pen);}}
  var beside=BuildingPlacement.origin(e,yard);animal(l,EntityType.PIG,beside.offset(18,1,20));animal(l,EntityType.PIG,beside.offset(18,1,22));
  // The keeper: a new adult of the village, housed and posted at the yard.
  var room=new Settlement.Home(Settlement.childId(st.id(),"home/probe-keeper"),1,2,true);st.addHome(room);
  var keeper=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);st.admit(keeper,room.id());st.assign(keeper.id(),Profession.LIVESTOCK_FARMER,yard.id());
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(st.id(),st.resident(keeper.id()));var door=BuildingPlacement.at(e,yard,5,1,-1);npc.moveTo(door.getX()+.5,door.getY(),door.getZ()+.5,0,0);l.addFreshEntity(npc);
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_LIVESTOCK fixture yards I and III north of the village, keeper {} at the yard III",keeper.id());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{var l=s.overworld();var e=entry(s);var yard=building(e,yardId);
  var feeds=new StringBuilder();var pens=new StringBuilder();int filled=0,babies=0,pigs=0;boolean gatesShut=true;
  for(var pen:LivestockPens.built(3)){var f=l.getBlockState(LivestockPens.at(e,yard,pen.feeder()));int feed=f.getBlock() instanceof FeederBlock?f.getValue(FeederBlock.FEED):-1;if(feed>0)filled++;feeds.append(feed).append(' ');
   var herd=LivestockPens.herd(l,e,yard,pen);babies+=(int)herd.stream().filter(Animal::isBaby).count();
   pens.append(pen.index()).append(':').append(herd.size()).append('/').append(herd.stream().filter(a->!a.isBaby()).count()).append('/').append(herd.stream().filter(Animal::isInLove).count()).append(' ');if(pen.index()==4)pigs=(int)herd.stream().filter(a->a instanceof Pig&&LivestockPens.inside(e,yard,pen,a.blockPosition())).count();
   gatesShut&=!Gates.isOpen(l.getBlockState(LivestockPens.at(e,yard,pen.gate())));}
  String status="";for(var r:e.settlement().residents())if(r.profession()==Profession.LIVESTOCK_FARMER&&e.settlement().workplace(r.id())!=null&&e.settlement().workplace(r.id()).id().equals(yardId)&&l.getEntity(r.id()) instanceof ResidentEntity npc){if(npc.workStatus().equals("livestock_leading")&&!driveShot){driving=true;driveAt=npc.position();}var nav=npc.getNavigation();var path=nav.getPath();status=npc.workStatus()+"@"+String.format(java.util.Locale.ROOT,"%.1f %.1f %.1f",npc.getX(),npc.getY(),npc.getZ())+" target="+(nav.getTargetPos()==null?"-":nav.getTargetPos().toShortString())+" done="+nav.isDone()+" partial="+(path!=null&&!path.canReach())+" end="+(path==null||path.getEndNode()==null?"-":path.getEndNode().asBlockPos().toShortString())+" goals="+npc.runningGoals()+" clock="+SettlementData.get(s).clock().ticks()+" v="+npc.getDeltaMovement()+" ground="+npc.onGround()+" wall="+npc.isInWall()+" speed="+npc.getSpeed()+" leashed="+npc.isLeashed()+" passenger="+npc.isPassenger()+" feetBlock="+l.getBlockState(npc.blockPosition()).getBlock()+" noAi="+npc.isNoAi();}
  // No water outside the sunk troughs (probe livestock-4 found the pens flooded from troughs set above ground).
  int wet=0;var o=BuildingPlacement.origin(e,yard);for(int x=0;x<15;x++)for(int z=0;z<23;z++)for(int y=1;y<=2;y++)if(!l.getFluidState(o.offset(x,y,z)).isEmpty())wet++;
  progress="feeders=["+feeds.toString().trim()+"] filled="+filled+" babies="+babies+" pigsInPen4="+pigs+" gatesShut="+gatesShut+" wet="+wet+" pens(head/adult/love)=["+pens.toString().trim()+"] pigsAround="+l.getEntitiesOfClass(Pig.class,LivestockPens.yardBox(e,yard).inflate(48)).size()+" keeper="+status;
  if(wet>0)failure="Water ran out of the troughs: "+progress;
  // The facts are kept as they stood when every condition held (a later sample may find the keeper at a gate again).
  if(!done&&filled==4&&babies>0&&pigs>=2&&gatesShut){facts=progress;done=true;}
 }catch(Exception ex){failure=ex.toString();}});}
 private static void look(Minecraft mc,UUID id,double dx,double dy,double dz,float yaw,float pitch){var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);var o=BuildingPlacement.origin(e,building(e,id));
  s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),o.getX()+dx,o.getY()+dy,o.getZ()+dz,yaw,pitch);});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>30000)throw new IllegalStateException("Livestock timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  // One frame of a drive: the keeper behind an animal on its way to a gate (owner 2026-09-23: no lead, the keeper runs behind).
  else if(phase==1&&ready&&driving&&!driveShot&&driveAt!=null){driveShot=true;var at=driveAt;var s=mc.getSingleplayerServer();s.execute(()->s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),at.x-6,at.y+7,at.z-6,-45,40));phase=10;ticks=0;}
  else if(phase==10&&ticks>12){mc.options.hideGui=true;capture(mc,"drive");mc.options.hideGui=false;var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);});phase=1;ticks=100;}
  else if(phase==1&&ready&&ticks%(driveShot?20:5)==0){sample(mc);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_LIVESTOCK progress {}",progress);
   // From behind the pens (the street side shows the byre, which hides them), looking north over the yard.
   if(done){phase=2;ticks=0;mc.options.hideGui=true;look(mc,lookId,8.5,15,36,180,40);}
   else if(ticks>6000)throw new IllegalStateException("The yard did not come to work: "+progress);}
  else if(phase==2&&ticks>80){capture(mc,"yard-1");look(mc,yardId,8.5,16,37,180,42);phase=3;ticks=0;}
  else if(phase==3&&ticks>80){capture(mc,"yard-3");var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);var yard=building(e,yardId);var f=LivestockPens.at(e,yard,LivestockPens.pen(1).feeder());
    s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),f.getX()+.5,f.getY()+1.6,f.getZ()-1.6,0,55);});phase=4;ticks=0;}
  else if(phase==4&&ticks>60){capture(mc,"feeder");mc.options.hideGui=false;var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);});
   BuildingsPanel.select(yardId);phase=5;ticks=0;}
  else if(phase==5&&ticks>40){OfficeProbes.open(mc,ConstructionScreen.BUILDINGS);phase=6;ticks=0;}
  else if(phase==6&&ticks>80&&mc.screen instanceof ConstructionScreen){
   CompoundTag card=null;for(var raw:ConstructionOverlay.snapshot().getList("cards",net.minecraft.nbt.Tag.TAG_COMPOUND))if(((CompoundTag)raw).hasUUID("id")&&((CompoundTag)raw).getUUID("id").equals(yardId))card=(CompoundTag)raw;
   if(card==null||card.getList("pens",net.minecraft.nbt.Tag.TAG_COMPOUND).size()!=4)throw new IllegalStateException("The yard's card should show four pens: "+(card==null?"no card":card.getList("pens",10)));
   capture(mc,"card");mc.setScreen(null);
   LogUtils.getLogger().info("ASTRA_LIVESTOCK VERIFIED four feeders filled over the fences, the pens bred, two wild pigs driven into pen 4 through its gate, gates shut, card with four pens: {}; reload=false",facts);
   mc.stop();phase=7;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_LIVESTOCK FAILED",ex);mc.options.hideGui=false;mc.stop();}}
}
