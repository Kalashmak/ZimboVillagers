package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-056: the settlement map is a model of every remembered block — residents are faces on the map itself, a click on a face follows them,
 *  a click on a block opens its whole column, one chunk can be looked at on its own, and the canopy can be dropped. */
final class MapProbe {
 static final int NEEDED=9;
 private static int phase,ticks,guard;private static boolean villageShot;private static volatile String entrances="";private static volatile String failure,progress="";private static volatile int surveyed;private static volatile boolean ready;
 private static volatile BlockPos walker;private static volatile java.util.UUID walkerId;private static float[] focusBefore;
 private static int icons,depth,blocksAll,blocksChunk;
 static boolean enabled(){return Boolean.getBoolean("villageastra.mapSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-map-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_MAP screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static void click(AtlasScreen screen,double x,double y){screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);}
 private static void press(AtlasScreen screen,String name){var at=screen.buttonCenter(name);if(at==null)throw new IllegalStateException("No button "+name);click(screen,at[0],at[1]);}
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  settlement.appointPlayerMayor(p.getUUID());
  // The office stands on the real ground of a natural world, never inside a hill.
  var c=e.center();int ground=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,c.getX()-43,c.getZ())-1-c.getY();
  var office=new Settlement.Building(Settlement.childId(settlement.id(),"building/cartographer-map"),"cartographer",-44,ground,-4);settlement.addBuilding(office);
  var chestPos=LogisticsRoutes.position(e,office);l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  var chest=LogisticsRoutes.chest(l,e,office);if(chest==null)throw new IllegalStateException("Office chest missing");chest.setItem(0,new ItemStack(Items.PAPER,32));
  var home=new Settlement.Home(Settlement.childId(settlement.id(),"home/cartographer-map"),1,1,true);settlement.addHome(home);
  var r=new Resident(java.util.UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(r,home.id());
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(settlement.id(),settlement.resident(r.id()));npc.moveTo(chestPos.getX()+1.5,chestPos.getY(),chestPos.getZ()+1.5,0,0);l.addFreshEntity(npc);
  settlement.assign(r.id(),Profession.CARTOGRAPHER,office.id());SettlementData.get(s).setDirty();
  walkerId=r.id();
  LogUtils.getLogger().info("ASTRA_MAP fixture mayor and a cartographer at {}",chestPos.toShortString());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Map timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%100==0){
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var e=entry(s);surveyed=Atlas.surveyed(Atlas.inspect(s.overworld(),e.settlement().id())).size();});
   progress="surveyed="+surveyed;
   if(surveyed>=NEEDED){
    mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var c=entry(mc.getSingleplayerServer()).center();p.teleportTo(p.serverLevel(),c.getX()+.5,c.getY()+2,c.getZ()+.5,0,0);});
    mc.setScreen(new AtlasScreen());phase=2;ticks=0;}}
  else if(phase==2&&mc.screen instanceof AtlasScreen screen&&ticks>60){
   if(AtlasScreen.opened()<NEEDED){if(ticks>600)throw new IllegalStateException("Client received "+AtlasScreen.opened()+" chunks");return;}
   if(AtlasScreen.people().isEmpty()){if(ticks>900)throw new IllegalStateException("No resident reached the map");return;}
   // AD-063: the generated village seen from above — houses on level ground, straight roads, no trees on its territory.
   if(!villageShot){
    var hall=BlockPos.of(AtlasScreen.live().getLong("center"));var height=AtlasScreen.TERRAIN.heightAt(hall.getX(),hall.getZ());
    // Every generated building can be walked into: solid ground before the door at floor level, the door and the air above the step free.
    mc.getSingleplayerServer().execute(()->{var srv=mc.getSingleplayerServer();var l=srv.overworld();var e=entry(srv);int open=0,total=0;var shut=new StringBuilder();
     for(var b:e.settlement().buildings()){if(b.id().toString().equals(Settlement.childId(e.settlement().id(),"building/cartographer-map").toString()))continue;
      var design=BuildingBlueprints.design(b.type());var base=e.center().offset(b.x(),b.y(),b.z());var step=base.offset(BuildingBlueprints.doorX(b.type()),0,-1);total++;
      // A dirt path is a hair lower than a full block but a perfectly good step: any ground with a collision shape carries the resident.
      boolean ok=!l.getBlockState(step).getCollisionShape(l,step).isEmpty()&&l.getBlockState(step.above()).getCollisionShape(l,step.above()).isEmpty()
       &&l.getBlockState(step.above(2)).getCollisionShape(l,step.above(2)).isEmpty()
       &&(l.getBlockState(base.offset(BuildingBlueprints.doorX(b.type()),1,0)).getBlock() instanceof net.minecraft.world.level.block.DoorBlock||l.getBlockState(base.offset(BuildingBlueprints.doorX(b.type()),1,0)).getCollisionShape(l,base.offset(BuildingBlueprints.doorX(b.type()),1,0)).isEmpty());
      if(ok)open++;else shut.append(b.type()).append('@').append(step.toShortString()).append('[').append(l.getBlockState(step).getBlock()).append('/').append(l.getBlockState(step.above()).getBlock()).append('/').append(l.getBlockState(step.above(2)).getBlock()).append('/').append(l.getBlockState(base.offset(BuildingBlueprints.doorX(b.type()),1,0)).getBlock()).append("] ");}
     var roadsFile=srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-initial-roads/"+e.settlement().id()+".bin");
     long[] roadCells=java.nio.file.Files.exists(roadsFile)?org.villageastra.persistence.NbtRecord.read(roadsFile).getLongArray("cells"):new long[0];
     var office=Settlement.childId(e.settlement().id(),"building/cartographer-map");
     int trees=VillageClearing.remaining(l,e,roadCells,b->!b.id().equals(office));
     // Roads climb gently: neighbouring road blocks in the real world differ by one block at most.
     var roadHeight=new java.util.HashMap<Long,Integer>();for(long raw:roadCells){var c=BlockPos.of(raw);roadHeight.put(BlockPos.asLong(c.getX(),0,c.getZ()),c.getY());}
     int steep=0;for(var cell:roadHeight.entrySet()){var c=BlockPos.of(cell.getKey());for(var n:java.util.List.of(c.east(),c.south())){Integer y=roadHeight.get(n.asLong());if(y!=null&&Math.abs(y-cell.getValue())>1)steep++;}}
     entrances=open+"/"+total+(shut.length()==0?"":" shut "+shut)+"; trees on territory="+trees+" road cells="+roadCells.length+" steep road steps="+steep;LogUtils.getLogger().info("ASTRA_VILLAGE entrances open={}",entrances);});
    villageShot=true;screen.overview(new BlockPos(hall.getX()+3,height==null?hall.getY():height,hall.getZ()+3),3.2F);phase=20;ticks=0;return;}
   // The cartographer is the one who keeps walking: the probe waits until they are on the opened map rather than following somebody at rest.
   boolean known=false;for(int i=0;i<AtlasScreen.people().size();i++)if(AtlasScreen.people().getCompound(i).getUUID("id").equals(walkerId))known=true;
   if(!known){if(ticks<2400)return;walkerId=AtlasScreen.people().getCompound(0).getUUID("id");}
   // The resident button brings the camera onto a resident, one after another, until it is the cartographer.
   for(int i=0;i<=AtlasScreen.people().size()&&!walkerId.equals(AtlasScreen.following());i++)press(screen,"resident");
   if(!walkerId.equals(AtlasScreen.following()))throw new IllegalStateException("The resident button never reached the cartographer");
   phase=3;ticks=0;}
  else if(phase==20&&ticks>20){capture(mc,"village");phase=2;ticks=61;}
  else if(phase==3&&mc.screen instanceof AtlasScreen screen&&ticks>40){
   // The resident is a face drawn on the map itself, where they stand; clicking the face stops and starts following.
   var icon=screen.icons().get(walkerId);
   if(icon==null){if(ticks>400)throw new IllegalStateException("The followed resident has no face on the map: "+screen.icons().size()+" faces");return;}
   icons=screen.icons().size();
   click(screen,icon[0],icon[1]);
   if(AtlasScreen.following()!=null)throw new IllegalStateException("A click on the followed face did not stop following");
   icon=screen.icons().get(walkerId);click(screen,icon[0],icon[1]);
   if(!walkerId.equals(AtlasScreen.following()))throw new IllegalStateException("A click on the face did not follow the resident");
   for(var raw:AtlasScreen.people()){var person=(net.minecraft.nbt.CompoundTag)raw;if(person.getUUID("id").equals(walkerId))walker=BlockPos.of(person.getLong("pos"));}
   progress+=" faces="+icons;phase=4;ticks=0;}
  else if(phase==4&&ticks>20){capture(mc,"people");phase=5;ticks=0;}
  else if(phase==5&&mc.screen instanceof AtlasScreen screen&&ticks>5){
   // A click on a block of the map opens its whole column, from the roof to the remembered depth.
   blocksAll=AtlasScreen.drawnBlocks();
   boolean picked=false;
   for(int radius=12;radius<=120&&!picked;radius+=6)for(int dx=-radius;dx<=radius&&!picked;dx+=6)for(int dz=-radius;dz<=radius&&!picked;dz+=6){
    double mx=screen.width/2D+dx,my=screen.height/2D+dz;
    if(Math.max(Math.abs(dx),Math.abs(dz))!=radius||!screen.pickable(mx,my))continue;
    click(screen,mx,my);picked=AtlasScreen.selected()!=null;}
   if(!picked)throw new IllegalStateException("No block of the map could be picked");
   if(!walkerId.equals(AtlasScreen.following()))throw new IllegalStateException("Picking a block dropped the followed resident");
   // The picked column is remembered and drawn whole, from its roof down past the ground.
   depth=AtlasScreen.columnDepth();
   if(depth<Atlas.BELOW_GROUND)throw new IllegalStateException("The map does not hold the whole column: depth="+depth);
   if(blocksAll<=AtlasScreen.opened()*256)throw new IllegalStateException("The map draws no more than the tops of the columns: "+blocksAll+" blocks for "+AtlasScreen.opened()+" chunks");
   progress+=" column="+depth+" blocks="+blocksAll;phase=6;ticks=0;}
  else if(phase==6&&ticks>10){capture(mc,"column");
   if(mc.screen instanceof AtlasScreen screen)press(screen,"chunk");
   phase=7;ticks=0;}
  else if(phase==7&&mc.screen instanceof AtlasScreen screen&&ticks>10){
   // One chunk on its own: all four sides of it are open, so every stored layer of it is drawn.
   if(!AtlasScreen.isolated())throw new IllegalStateException("The chunk button did not isolate the chunk");
   if(ticks<30)return;
   blocksChunk=AtlasScreen.drawnBlocks();
   if(blocksChunk<=256+60*8)throw new IllegalStateException("The isolated chunk does not show its sides: "+blocksChunk+" blocks");
   capture(mc,"chunk");
   press(screen,"chunk");
   if(AtlasScreen.isolated())throw new IllegalStateException("The chunk button did not return to the whole map");
   progress+=" chunk="+blocksChunk;
   phase=71;ticks=0;}
  else if(phase==71&&mc.screen instanceof AtlasScreen screen&&ticks%10==0){
   // Framing the chunk let the resident go; the resident button brings the camera back onto them once they are on the opened map again.
   for(int i=0;i<=AtlasScreen.people().size()&&!walkerId.equals(AtlasScreen.following());i++)press(screen,"resident");
   if(!walkerId.equals(AtlasScreen.following())){if(ticks>1200)throw new IllegalStateException("The resident button did not return to the cartographer");return;}
   focusBefore=screen.focus();phase=8;ticks=0;}
  else if(phase==8&&mc.screen instanceof AtlasScreen screen&&ticks>40){
   var list=AtlasScreen.people();net.minecraft.nbt.CompoundTag person=null;
   for(int i=0;i<list.size();i++)if(list.getCompound(i).getUUID("id").equals(walkerId))person=list.getCompound(i);
   if(person==null){if(ticks>1600)throw new IllegalStateException("The followed resident left the map");return;}
   var now=BlockPos.of(person.getLong("pos"));
   if(now.equals(walker)){if(ticks>1600)throw new IllegalStateException("The resident never moved on the map: "+person.getString("name")+"/"+person.getString("profession")+" status="+person.getString("status")+" at "+now.toShortString());return;}
   if(!walkerId.equals(AtlasScreen.following()))throw new IllegalStateException("The resident is no longer followed");
   if(++guard<20)return;
   var focusNow=screen.focus();
   if(focusBefore[0]==focusNow[0]&&focusBefore[2]==focusNow[2])throw new IllegalStateException("The view did not follow the resident");
   if(!screen.icons().containsKey(walkerId))throw new IllegalStateException("The walking resident lost their face on the map");
   float zoomed=screen.zoomLevel();screen.mouseScrolled(60,screen.height/2D,1);
   if(screen.zoomLevel()<=zoomed)throw new IllegalStateException("The wheel did not zoom in: "+zoomed+"->"+screen.zoomLevel());
   progress+=" walked "+walker.toShortString()+" -> "+now.toShortString()+" zoom "+zoomed+" -> "+screen.zoomLevel();
   walker=now;phase=9;ticks=0;}
  else if(phase==9&&ticks>10){capture(mc,"follow");phase=10;ticks=0;}
  else if(phase==10&&mc.screen instanceof AtlasScreen screen){
   // AD-051: the map remembers whole columns — dropping the canopy has to reveal the ground under the trees.
   long withCanopy=AtlasScreen.heightSum();
   press(screen,"crowns");
   if(AtlasScreen.canopy())throw new IllegalStateException("The canopy button did not drop the crowns");
   long withoutCanopy=AtlasScreen.heightSum();
   if(withoutCanopy>=withCanopy)throw new IllegalStateException("Dropping the canopy showed nothing under it: "+withCanopy+" -> "+withoutCanopy);
   progress+=" canopy="+withCanopy+"->"+withoutCanopy;phase=11;ticks=0;}
  else if(phase==11&&ticks>10){
   capture(mc,"ground");
   LogUtils.getLogger().info("ASTRA_MAP VERIFIED faces on the map, whole columns and single chunk: {}; entrances={}; following=true; reload=false",progress,entrances);
   mc.setScreen(null);mc.stop();phase=12;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_MAP FAILED",ex);mc.stop();}}
}
