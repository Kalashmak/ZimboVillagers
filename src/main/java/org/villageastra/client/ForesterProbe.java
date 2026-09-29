package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-131: the forester's hut I..VI in a real client, the forester walking and felling in real time. A wood of wild oaks grows behind the hut
 *  (24..48 blocks from its door), with one tree a player planted and one touching planks nearest the door. The hut is raised a level at a time
 *  by its own upgrade projects, laid at once; after each the probe waits for what the level does and logs it:
 *  <ol><li>FORESTER_WILD (I): the first tree he fells is a wild one 15..25 blocks off, the player's and the planked one still stand;</li>
 *  <li>FORESTER_REPLANT (II): a sapling stands on a foot he felled;</li>
 *  <li>FORESTER_VARIETY (III): his plantings hold three kinds, the card lists what the hut asks for;</li>
 *  <li>SAWMILL_PLANKS (IV): the saw turns one log into six planks of its kind, seen in the chest; then five minutes of felling at IV;</li>
 *  <li>FORESTER_SPEED (V): five minutes of felling at V against IV;</li>
 *  <li>GROVE_RATE (VI): five minutes in which the courtyard grove and the forester both work, the grove's logs against his.</li></ol> */
final class ForesterProbe {
 static boolean enabled(){return Boolean.getBoolean("villageastra.foresterProbe");}
 private static final int WINDOW=6000;
 private static int phase,ticks,level=1,start=-1;private static volatile boolean groveReady;private static volatile String failure;private static volatile boolean ready,applied,met;
 private static volatile UUID hutId,village,foresterId;private static volatile BlockPos door,playerTree,plankTree,felled;
 private static volatile int logsAt,groveAt,logsIV,logsV,logsVI,groveVI,cuts,badCuts,birchLogs=-1,birchPlanks=-1,kinds,asks;
 private static final List<String> shots=new ArrayList<>();
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-forester-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}shots.add(suffix);LogUtils.getLogger().info("ASTRA_FORESTER screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building hut(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->hutId==null?b.type().equals(ForesterHut.TYPE):b.id().equals(hutId)).findFirst().orElseThrow();}
 private static void server(Minecraft mc,Runnable r){mc.getSingleplayerServer().execute(()->{try{r.run();}catch(Exception ex){failure=ex.toString();LogUtils.getLogger().error("ASTRA_FORESTER server step",ex);}});}
 private static CompoundTag work(net.minecraft.server.MinecraftServer s){var p=MineWork.path(s.overworld(),hutId);return java.nio.file.Files.exists(p)?NbtRecord.read(p):new CompoundTag();}
 private static BlockPos ground(net.minecraft.server.level.ServerLevel l,int x,int z){return new BlockPos(x,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);}
 /** The player is mayor, every forestry level's research is done, the day stands still; the wood grows behind the hut. */
 private static void fixture(Minecraft mc){server(mc,()->{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  st.appointPlayerMayor(p.getUUID());village=st.id();var hut=hut(e);hutId=hut.id();door=ForesterHut.door(e,hut);
  foresterId=st.residents().stream().filter(r->r.profession()==Profession.FORESTER).findFirst().orElseThrow().id();
  if(st.lotLayout()<OrganicLots.BARN_LOTS)throw new IllegalStateException("The probe village has the old lots: layout "+st.lotLayout());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,s);l.setWeatherParameters(6000,0,false,false);l.setDayTime(6000);
  var research=BookResearch.inspect(l,e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  for(int lv=2;lv<=6;lv++)for(var id:BuildingTiers.research(ForesterHut.TYPE,lv))done.add(StringTag.valueOf(id));
  research.put("legacyDone",done);BookResearch.store(l,e,research);
  if(HallUpgradeGoal.pending(l,st.id()))HallUpgradeGoal.drop(l,st.id());
  // The wood: wild oaks every 3 blocks, 24..48 behind the door, 27 either side; the nearest two are a player's and one against a plank.
  int trees=0;
  for(int dz=24;dz<=48;dz+=3)for(int dx=-27;dx<=27;dx+=3){var foot=ground(l,door.getX()+dx,door.getZ()+dz);
   if(!l.getBlockState(foot).isAir()||!l.getBlockState(foot.below()).is(net.minecraft.tags.BlockTags.DIRT))continue;
   var logs=ForestWork.wildOak(l,foot,5);trees++;
   if(dx==0&&dz==24){playerTree=foot;ForestPlantings.get(s).recordPlayer(l,foot);}
   if(dx==3&&dz==24){plankTree=foot;l.setBlock(logs.get(1).west(),Blocks.OAK_PLANKS.defaultBlockState(),3);}}
  ForestWork.forget(hutId);SettlementData.get(s).setDirty();p.setGameMode(GameType.SPECTATOR);
  LogUtils.getLogger().info("ASTRA_FORESTER fixture hut {} door {} wood {} trees, player tree {}, planked tree {}",hutId,door,trees,playerTree,plankTree);ready=true;});}
 /** Raises the hut one level with its own project, laid at once; the forester is set down in front of the door, out of the way. */
 private static void apply(Minecraft mc){server(mc,()->{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var b=hut(e);
  var survey=BuildingTiers.survey(l,e,b);
  if(!survey.reason().isEmpty()||!survey.conflicts().isEmpty()){var why=new StringBuilder();for(var c:survey.conflicts())why.append(' ').append(BuildingPlacement.local(e,b,c).toShortString()).append('=').append(l.getBlockState(c)).append(l.getBlockEntity(c)!=null?"(entity)":"");
   throw new IllegalStateException("Hut level "+level+" survey refused: "+survey.reason()+" at"+why);}
  var ops=survey.state().getList("ops",Tag.TAG_COMPOUND);
  for(int i=0;i<ops.size();i++){var op=HallConstructionPlan.step(ops.getCompound(i));l.setBlock(op.pos(),op.after(),2);}
  if(!BuildingOrders.complete(l,e,survey.state()))throw new IllegalStateException("Hut level "+level+" does not match its project");
  BuildingLevels.forgetBest(e.settlement().id());int working=BuildingLevels.level(l,e,hut(e));
  if(working!=level)throw new IllegalStateException("The hut works at "+working+", not "+level);
  var npc=l.getEntity(foresterId);if(npc!=null)npc.teleportTo(door.getX()+.5,door.getY(),door.getZ()-2.5);
  var chest=(Container)l.getBlockEntity(LogisticsRoutes.position(e,b));
  if(level==2)put(chest,new ItemStack(Items.OAK_SAPLING,16));
  if(level==3){CropUnlocks.unlock(l,e,"minecraft:birch_sapling","quest");CropUnlocks.unlock(l,e,"minecraft:spruce_sapling","quest");put(chest,new ItemStack(Items.BIRCH_SAPLING,4));put(chest,new ItemStack(Items.SPRUCE_SAPLING,4));}
  if(level==4){put(chest,new ItemStack(Items.BIRCH_LOG,24));var hall=(Container)l.getBlockEntity(LogisticsRoutes.position(e,Workshops.hall(e)));for(int i=0;i<hall.getContainerSize();i++)if(hall.getItem(i).is(net.minecraft.tags.ItemTags.PLANKS))hall.setItem(i,ItemStack.EMPTY);}
  if(level==6)put(chest,new ItemStack(Items.OAK_SAPLING,64));
  LogUtils.getLogger().info("ASTRA_FORESTER level {} laid: {} operations, cost {}",level,ops.size(),survey.state().getCompound("cost"));
  SettlementData.get(s).setDirty();applied=true;});}
 private static void put(Container c,ItemStack s){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty()){c.setItem(i,s);return;}throw new IllegalStateException("The hut chest is full");}
 private static void look(Minecraft mc,BlockPos eye,BlockPos at){server(mc,()->{var s=mc.getSingleplayerServer();var l=s.overworld();double dx=at.getX()-eye.getX(),dy=at.getY()-eye.getY()-1.62,dz=at.getZ()-eye.getZ();
  var p=s.getPlayerList().getPlayers().get(0);p.teleportTo(l,eye.getX()+.5,eye.getY(),eye.getZ()+.5,(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));});}
 /** One look at the world a second on the server: whether the level's check is met, and its numbers. */
 private static void check(Minecraft mc){server(mc,()->{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var b=hut(e);var w=work(s);
  switch(level){
   case 1->{if(w.contains("lastFoot")&&felled==null)felled=BlockPos.of(w.getLong("lastFoot"));
    if(felled!=null){double d=Math.sqrt(felled.distSqr(new BlockPos(door.getX(),felled.getY(),door.getZ())));
     if(d<15||d>25)throw new IllegalStateException("The first tree felled stood "+d+" from the door");
     if(!l.getBlockState(playerTree).is(Blocks.OAK_LOG)||!l.getBlockState(plankTree).is(Blocks.OAK_LOG))throw new IllegalStateException("The player's or the planked tree was felled");
     LogUtils.getLogger().info("ASTRA_FORESTER_WILD VERIFIED felled={} distance={} player=standing planks=standing",felled,String.format(Locale.ROOT,"%.1f",d));met=true;}}
   case 2->{var p=ForestPlantings.get(s);for(var dz=20;dz<=50&&!met;dz++)for(int dx=-30;dx<=30&&!met;dx++){var c=ground(l,door.getX()+dx,door.getZ()+dz);
     if(l.getBlockState(c).is(net.minecraft.tags.BlockTags.SAPLINGS)){var r=p.forester(l,c);if(r!=null&&r.building().equals(hutId)){felled=c;met=true;
      LogUtils.getLogger().info("ASTRA_FORESTER_REPLANT VERIFIED sapling={} at {} on a felled foot",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(c).getBlock()),c);}}}}
   case 3->{var mix=ForestPlantings.get(s).planted(l,hutId,door,ForestBalance.radius(3));kinds=mix.size();var card=BuildingCards.card(l,e,b).getCompound("forest");asks=card.getList("asks",Tag.TAG_COMPOUND).size();
    if(kinds>=3&&asks>=1){met=true;LogUtils.getLogger().info("ASTRA_FORESTER_VARIETY VERIFIED kinds={} mix={} asks={}",kinds,mix,asks);}}
   default->{}
  }
  if(level>=4){var chest=(Container)l.getBlockEntity(LogisticsRoutes.position(e,b));int logs=chest.countItem(Items.BIRCH_LOG),planks=chest.countItem(Items.BIRCH_PLANKS);
   if(birchLogs>=0&&logs==birchLogs-1&&planks==birchPlanks+ForestBalance.PLANKS_PER_LOG)cuts++;else if(birchLogs>=0&&logs==birchLogs-1&&planks!=birchPlanks+ForestBalance.PLANKS_PER_LOG&&planks>birchPlanks)badCuts++;
   birchLogs=logs;birchPlanks=planks;}});}
 /** Starts and ends the five-minute windows: the forester's logs felled (his record's count) and the grove's (its record's). */
 private static void mark(Minecraft mc,boolean end){server(mc,()->{var s=mc.getSingleplayerServer();int logs=work(s).getInt("logsFelled"),grove=ForestryMachines.inspect(s.overworld(),hutId).getInt("groveLogs");
  if(!end){logsAt=logs;groveAt=grove;LogUtils.getLogger().info("ASTRA_FORESTER level {} window starts: forester {} logs, grove {} logs",level,logs,grove);return;}
  int felledNow=logs-logsAt,groveNow=grove-groveAt;LogUtils.getLogger().info("ASTRA_FORESTER level {} window ends: forester {} logs, grove {} logs in {} ticks",level,felledNow,groveNow,WINDOW);
  if(level==4)logsIV=felledNow;if(level==5)logsV=felledNow;if(level==6){logsVI=felledNow;groveVI=groveNow;}met=true;});}
 /** The card scrolled down to its forester rows (the asks line under the mix). */
 private static void scrollCard(Minecraft mc){if(!(mc.screen instanceof ConstructionScreen screen))return;var panel=(BuildingsPanel)screen.panel(ConstructionScreen.BUILDINGS);var area=panel.cardArea();if(area!=null)panel.scroll(area.x()+area.w()/2.0,area.y()+area.h()/2.0,-1);}
 private static void card(Minecraft mc){BuildingsPanel.select(hutId);OfficeProbes.open(mc,ConstructionScreen.BUILDINGS);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>40000)throw new IllegalStateException("Forester probe timeout phase="+phase+" level="+level);
  switch(phase){
   case 0->{if(ticks>60){fixture(mc);phase=1;ticks=0;}}
   case 1->{if(ready&&ticks==40){look(mc,door.offset(0,22,8),door.offset(0,0,30));}
    if(ready&&ticks>60&&ticks%20==0)check(mc);
    if(met){met=false;phase=2;ticks=0;}}
   case 2->{if(ticks==1)look(mc,felled.offset(-3,4,-4),felled);if(ticks==60){capture(mc,"level"+level);phase=level==3?3:4;ticks=0;}}
   // III: the hut's card in the office with what it asks for.
   case 3->{if(ticks==1){server(mc,()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).setGameMode(GameType.CREATIVE));}
    if(ticks==20)card(mc);if(ticks>40&&ticks<110&&ticks%10==0)scrollCard(mc);if(ticks==120)capture(mc,"card");if(ticks==140){mc.setScreen(null);server(mc,()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).setGameMode(GameType.SPECTATOR));phase=4;ticks=0;}}
   case 4->{if(ticks>20){if(level<6){level++;applied=false;apply(mc);phase=5;ticks=0;}else{phase=9;ticks=0;}}}
   case 5->{if(!applied)return;
    if(level<=3){if(ticks==20)look(mc,door.offset(0,22,8),door.offset(0,0,30));if(ticks>40&&ticks%20==0)check(mc);if(met){met=false;phase=2;ticks=0;}return;}
    // IV..VI: five minutes of work, the saw watched every tick at IV. At VI the window opens once the grove has felled its first tree (its
    // steady pace: the first growth of 800 ticks is a start-up, not a rate), and forester and grove are counted over the same ticks.
    if(ticks==20)look(mc,door.offset(level==6?7:0,24,level==6?14:8),level==6?door.offset(3,0,14):door.offset(0,0,30));
    if(start<0&&ticks>=20&&(level<6||ticks%20==0)){if(level<6){mark(mc,false);start=ticks;}else server(mc,()->{if(ForestryMachines.inspect(mc.getSingleplayerServer().overworld(),hutId).getInt("groveTrees")>=1)groveReady=true;});}
    if(start<0&&groveReady){mark(mc,false);start=ticks;}
    if(start<0)return;
    if(level==4)check(mc);
    if(ticks==start+WINDOW/2){capture(mc,"level"+level);if(level==6){look(mc,door.offset(3,2,10),door.offset(3,1,18));}}
    if(level==6&&ticks==start+20+WINDOW/2)capture(mc,"courtyard");
    if(ticks==start+WINDOW)mark(mc,true);
    if(ticks>start+WINDOW&&met){met=false;start=-1;groveReady=false;
     if(level==4){if(cuts<3||badCuts>0)throw new IllegalStateException("The saw's cuts: "+cuts+" of one log into six planks, "+badCuts+" otherwise");
      LogUtils.getLogger().info("ASTRA_SAWMILL_PLANKS VERIFIED cuts={} planks_per_log={} bad={}",cuts,ForestBalance.PLANKS_PER_LOG,badCuts);}
     if(level==5){if(logsV<=logsIV)throw new IllegalStateException("Level V felled "+logsV+" logs in five minutes, IV "+logsIV);
      LogUtils.getLogger().info("ASTRA_FORESTER_SPEED VERIFIED iv={} v={} ratio={}",logsIV,logsV,String.format(Locale.ROOT,"%.2f",logsV/(double)Math.max(1,logsIV)));}
     if(level==6){double ratio=groveVI/(double)Math.max(1,logsVI);
      if(ratio<1.5)throw new IllegalStateException("The grove gave "+groveVI+" logs in five minutes, the forester "+logsVI+": "+ratio);
      LogUtils.getLogger().info("ASTRA_GROVE_RATE VERIFIED grove={} forester={} ratio={} window_ticks={}",groveVI,logsVI,String.format(Locale.ROOT,"%.2f",ratio),WINDOW);}
     phase=4;ticks=0;}}
   case 9->{if(ticks==20){LogUtils.getLogger().info("ASTRA_FORESTER PROBE VERIFIED levels=6 frames={} iv={} v={} vi={} grove={}",shots.size(),logsIV,logsV,logsVI,groveVI);mc.stop();phase=10;}}
   default->{}
  }
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_FORESTER FAILED",ex);phase=10;mc.stop();}}
}
