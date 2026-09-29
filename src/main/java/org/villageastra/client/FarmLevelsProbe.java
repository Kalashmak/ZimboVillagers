package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-130: the farm's six levels in a real client. The starter village's farm (grown west) is raised I..VI by its own upgrade projects,
 *  each surveyed by the builders' plan and set block by block at once (no walking); after every level the probe counts the plots, the
 *  lowest block light of the barn's plots, the farmers the labour pass posts against the farm's slots, and photographs the farm from the
 *  street; inside the barn at IV and V one frame a floor (at night: lantern light). At V it opens the farm's card in the office, sets all
 *  fields to potatoes with the "sow all" button and field 7 to carrots with a right click on the grid (the crop before potato) (both GUI scales photographed), and reads
 *  the policy file back after a save. */
final class FarmLevelsProbe {
 private static final int[][] PASSES={{1280,720,2,640,360},{960,720,3,320,240}};
 private static int phase,ticks,level=1,floor,pass,step;private static volatile String failure;private static volatile boolean ready,applied;
 private static volatile UUID farmId,village;private static final List<String> shots=new ArrayList<>(),plots=new ArrayList<>(),crews=new ArrayList<>();
 private static volatile int minLight=99;private static volatile String policy="";
 static boolean enabled(){return Boolean.getBoolean("villageastra.farmLevelsSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-farm-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}shots.add(suffix);LogUtils.getLogger().info("ASTRA_FARM_LEVELS screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building farm(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->farmId==null?b.type().equals("farm"):b.id().equals(farmId)).findFirst().orElseThrow();}
 private static void server(Minecraft mc,Runnable r){mc.getSingleplayerServer().execute(()->{try{r.run();}catch(Exception ex){failure=ex.toString();LogUtils.getLogger().error("ASTRA_FARM_LEVELS server step",ex);}});}
 /** The player is mayor; every farm level's research, the crops' quests and the machines' mechanics are done; three more adults live in the
  *  village (the labour pass posts the farm's second and third farmers from them); no project of the village's own stands in the way. */
 private static void fixture(Minecraft mc){server(mc,()->{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  st.appointPlayerMayor(p.getUUID());village=st.id();farmId=farm(e).id();
  if(FarmField.legacy(st))throw new IllegalStateException("The probe village keeps the AD-104 table: layout "+st.lotLayout());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,s);l.setWeatherParameters(6000,0,false,false);l.setDayTime(6000);
  var research=BookResearch.inspect(l,e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  for(int lv=2;lv<=6;lv++)for(var id:BuildingTiers.research("farm",lv))done.add(StringTag.valueOf(id));
  // AD-136: no mechanics branch; the farm's machine is its own level VI.
  research.put("legacyDone",done);BookResearch.store(l,e,research);
  CropUnlocks.unlock(l,e,"minecraft:carrot","quest");CropUnlocks.unlock(l,e,"minecraft:potato","quest");
  if(HallUpgradeGoal.pending(l,st.id()))HallUpgradeGoal.drop(l,st.id());
  var home=Settlement.childId(st.id(),"probe-farmhands");st.addHome(new Settlement.Home(home,1,4,true));
  for(int i=0;i<3;i++)st.admit(new Resident(Settlement.childId(st.id(),"probe-farmhand/"+i),Resident.Life.ADULT,true,null,null,-1),home);
  SettlementData.get(s).setDirty();p.setGameMode(GameType.SPECTATOR);
  LogUtils.getLogger().info("ASTRA_FARM_LEVELS fixture farm {} west={} layout={}",farmId,st.westField(farmId),st.lotLayout());ready=true;});}
 /** Raises the farm one level with its own project, laid at once; then counts what the level gives. */
 private static void apply(Minecraft mc){server(mc,()->{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var b=farm(e);
  if(level>1){var survey=BuildingTiers.survey(l,e,b);
   if(!survey.reason().isEmpty()||!survey.conflicts().isEmpty())throw new IllegalStateException("Farm level "+level+" survey refused: "+survey.reason()+" "+FarmBarn.describe(l,e,b,survey.conflicts()));
   var ops=survey.state().getList("ops",Tag.TAG_COMPOUND);
   for(int i=0;i<ops.size();i++){var op=HallConstructionPlan.step(ops.getCompound(i));l.setBlock(op.pos(),op.after(),2);}
   if(!BuildingOrders.complete(l,e,survey.state()))throw new IllegalStateException("Farm level "+level+" does not match its project");
   LogUtils.getLogger().info("ASTRA_FARM_LEVELS level {} laid: {} operations, cost {}",level,ops.size(),survey.state().getCompound("cost"));}
  b=farm(e);Population.assign(e);
  int n=FarmField.cells(e,b).size(),crew=e.settlement().employees(b.id()).size(),slots=Population.slots(e.settlement(),b);
  plots.add(String.valueOf(n));crews.add(crew+"/"+slots);
  if(crew!=slots)throw new IllegalStateException("Level "+level+": the labour pass posted "+crew+" farmers for "+slots+" places");
  SettlementData.get(s).setDirty();applied=true;});}
 /** The lowest block light over the plots of the barn's floors (sky does not reach them). */
 private static void light(Minecraft mc){server(mc,()->{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var b=farm(e);int min=99;BlockPos worst=null;
  for(var m:FarmField.modules(e,b)){for(var c:FarmField.localCells(List.of(m))){var at=BuildingPlacement.at(e,b,c.getX(),c.getY(),c.getZ());int v=l.getBrightness(LightLayer.BLOCK,at);if(v<min){min=v;worst=at;}}}
  minLight=Math.min(minLight,min);LogUtils.getLogger().info("ASTRA_FARM_LEVELS level {} lowest plot block light {} at {} over {} plots",level,min,worst,FarmField.cells(e,b).size());
  if(min<9)throw new IllegalStateException("A plot of the barn has block light "+min+" at "+worst);});}
 /** The player's eye at a farm-local (east) point, looking at another, on the farm's side and turn. */
 private static void look(Minecraft mc,BlockPos eye,BlockPos at,boolean night){server(mc,()->{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var b=farm(e);
  var from=FarmBarn.at(e,b,eye);var to=FarmBarn.at(e,b,at);double dx=to.getX()-from.getX(),dy=to.getY()-from.getY()-1.62,dz=to.getZ()-from.getZ();
  l.setDayTime(night?18000:6000);var p=s.getPlayerList().getPlayers().get(0);
  p.teleportTo(l,from.getX()+.5,from.getY(),from.getZ()+.5,(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));});}
 private static boolean scale(Minecraft mc,int[] p,boolean set){var w=mc.getWindow();
  if(set){w.setWindowed(p[0],p[1]);mc.options.guiScale().set(p[2]);mc.resizeDisplay();return false;}
  return (int)w.getGuiScale()==p[2]&&w.getGuiScaledWidth()==p[3]&&w.getGuiScaledHeight()==p[4];}
 private static CompoundTag card(){for(var raw:ConstructionOverlay.snapshot().getList("cards",Tag.TAG_COMPOUND)){var c=(CompoundTag)raw;if(c.hasUUID("id")&&c.getUUID("id").equals(farmId))return c;}return null;}
 private static void click(Minecraft mc,OfficeUi.Rect r,String what){click(mc,r,what,0);}
 private static void click(Minecraft mc,OfficeUi.Rect r,String what,int button){if(!(mc.screen instanceof ConstructionScreen screen))throw new IllegalStateException("The office is not open");
  if(r==null)throw new IllegalStateException(what+" is not on the card");var panel=(BuildingsPanel)screen.panel(ConstructionScreen.BUILDINGS);
  if(!panel.cardArea().containsRect(r))throw new IllegalStateException(what+" lies outside the card "+r);
  double x=r.x()+r.w()/2.0,y=r.y()+r.h()/2.0;if(!screen.mouseClicked(x,y,button))throw new IllegalStateException(what+" did not take the click");screen.mouseReleased(x,y,button);
  LogUtils.getLogger().info("ASTRA_FARM_LEVELS clicked {}",what);}
 /** The card scrolled down until the grid's last row (and "sow all") is inside it. */
 private static void reveal(Minecraft mc){if(!(mc.screen instanceof ConstructionScreen screen))return;var panel=(BuildingsPanel)screen.panel(ConstructionScreen.BUILDINGS);var area=panel.cardArea();if(area==null)return;
  var all=FarmFieldGrid.cell(0,"potato");if(all!=null&&!area.containsRect(all))panel.scroll(area.x()+area.w()/2.0,area.y()+area.h()/2.0,-1);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>60000)throw new IllegalStateException("Farm levels probe timeout phase="+phase+" level="+level);
  var eyeOut=new BlockPos(32,20,-6);var centre=new BlockPos(7,6,18);
  switch(phase){
   case 0->{if(ticks>60){fixture(mc);phase=1;ticks=0;}}
   case 1->{if(ready&&ticks>20){applied=false;apply(mc);phase=2;ticks=0;}}
   case 2->{if(applied&&ticks>40){look(mc,eyeOut,centre,false);phase=3;ticks=0;}}
   case 3->{if(ticks==60){if(level>=4)light(mc);}
    if(ticks==80){capture(mc,"level"+level);floor=0;phase=level>=4&&level<=5?4:7;ticks=0;}}
   // Inside the barn, one frame a floor, at night.
   case 4->{if(ticks==1){int y=FarmField.FLOOR_PITCH*floor+1;look(mc,new BlockPos(15,y,34),new BlockPos(0,y+1,10),true);}
    if(ticks==60){capture(mc,"inside"+level+"-floor"+floor);if(++floor<=level-3){ticks=0;}else{phase=level==5?5:7;ticks=0;}}}
   // The farm's card in the office at V: sow all to potatoes, field 7 to carrots, at both GUI scales.
   case 5->{if(ticks==1)server(mc,()->{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);l.setDayTime(6000);
      p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);});
    if(ticks==40){scale(mc,PASSES[pass],true);}
    if(ticks==60){if(!scale(mc,PASSES[pass],false))throw new IllegalStateException("GUI did not reach "+PASSES[pass][3]+"x"+PASSES[pass][4]);OfficeUi.frames=0;BuildingsPanel.select(farmId);OfficeProbes.open(mc,ConstructionScreen.BUILDINGS);step=0;
     LogUtils.getLogger().info("ASTRA_FARM_LEVELS office opened for pass {} at {}x{}",pass,PASSES[pass][3],PASSES[pass][4]);}
    if(ticks>100&&ticks<400&&ticks%5==0)reveal(mc);
    if(ticks==420&&pass==0){var c=card();if(c==null||!FarmFieldGrid.shown(c))throw new IllegalStateException("The farm card has no field grid: "+c);click(mc,FarmFieldGrid.cell(0,"potato"),"sow all: potato");}
    if(ticks==480&&pass==0){var c=card();var f7=c.getList("fields",Tag.TAG_COMPOUND).getCompound(6).getString("crop");
     if(!f7.equals("potato"))throw new IllegalStateException("Sow all did not reach field 7: "+f7);click(mc,FarmFieldGrid.cell(7,""),"field 7 (right: the crop before potato)",1);}
    // The layout is read on a frame drawn from the snapshot in hand: a snapshot arriving between a frame and the check changes the tabs'
    // badges under it, so the check is taken again on the next frames (at most 6 times) before it counts.
    if(ticks>=560&&ticks<=620&&ticks%10==0){var c=card();var f7=c.getList("fields",Tag.TAG_COMPOUND).getCompound(6);
     if(!f7.getString("crop").equals("carrot")||!f7.getBoolean("own"))throw new IllegalStateException("Field 7 should grow carrots of its own: "+f7);
     if(!(mc.screen instanceof ConstructionScreen screen))throw new IllegalStateException("The office closed at pass "+pass+", tick "+ticks+": the screen in front is "+(mc.screen==null?"none":mc.screen.getClass().getName()));var problems=OfficeProbes.layoutProblems(screen);
     if(!problems.isEmpty()&&ticks<620){LogUtils.getLogger().info("ASTRA_FARM_LEVELS grid layout retry at {}: {}",ticks,problems);return;}
     LogUtils.getLogger().info("ASTRA_FARM_LEVELS grid s{} {}x{} problems={}",PASSES[pass][2],PASSES[pass][3],PASSES[pass][4],problems);
     if(!problems.isEmpty())throw new IllegalStateException("Office layout at "+PASSES[pass][3]+"x"+PASSES[pass][4]+": "+problems);
     capture(mc,"grid-s"+PASSES[pass][2]);
     if(++pass<PASSES.length){ticks=39;}else{mc.setScreen(null);server(mc,()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).setGameMode(GameType.SPECTATOR));phase=7;ticks=0;}}}
   case 7->{if(ticks>20){if(level<6){level++;phase=1;ticks=0;}else{phase=8;ticks=0;}}}
   // The policy file after the clicks, as the server saves it.
   case 8->{if(ticks==1)server(mc,()->{var s=mc.getSingleplayerServer();var data=FarmPolicies.get(s);
      var crop=data.crop(village,farmId);var f7=data.crop(village,farmId,7);var f1=data.crop(village,farmId,1);
      s.overworld().getDataStorage().save();
      var file=s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/villageastra_farms.dat");
      try{var tag=NbtIo.readCompressed(file.toFile()).getCompound("data");var loaded=FarmPolicies.load(tag);
       boolean ok=tag.getInt("schema")==2&&loaded.crop(village,farmId)==FarmCrops.POTATO&&loaded.crop(village,farmId,7)==FarmCrops.CARROT&&loaded.own(village,farmId,7)&&!loaded.own(village,farmId,1);
       policy="crop="+crop.id()+" f7="+f7.id()+" f1="+f1.id()+" file="+ok;}catch(java.io.IOException ex){throw new IllegalStateException(ex);}});
    if(ticks==40){if(!policy.contains("file=true")||!policy.startsWith("crop=potato f7=carrot f1=potato"))throw new IllegalStateException("The policy after the clicks: "+policy);
     LogUtils.getLogger().info("ASTRA_FARM_LEVELS FARM LEVELS PROBE VERIFIED plots={} light={} farmers={} {} frames={}",String.join(",",plots),minLight,String.join(",",crews),policy,shots.size());mc.stop();phase=9;}}
   default->{}
  }
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_FARM_LEVELS FAILED",ex);phase=9;mc.stop();}}
}
