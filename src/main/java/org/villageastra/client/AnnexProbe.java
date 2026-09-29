package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-135 in the real client: a turned forester's hut of level III beside the village, carpentry researched, the hall stocked with what the
 *  annex takes. The mayor opens the hut's card in the office, the card shows the carpentry annex as ready, the mayor presses "Build annex"
 *  and the village builder really builds the lean-to at its site — turned with the hut. Then the hut is raised to IV, V and VI: the annex
 *  stays whole and is photographed beside every level, since it keeps no level of its own. */
final class AnnexProbe {
 private static int phase,ticks,shot;private static volatile String failure,progress="";private static volatile boolean ready,built,intact=true;
 private static int waited;private static volatile UUID hutId,annexId;private static final int ROTATION=1;
 private static final List<Integer> SHOWN=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.annexProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-annex-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_ANNEX screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building hut(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.id().equals(hutId)).findFirst().orElseThrow();}
 /** The annex this run builds (-PannexType, default the carpentry beside the forester's hut); its parent and level come from annexes.json. */
 private static final Annexes.Kind KIND=Annexes.kind(System.getProperty("villageastra.annexType","carpentry_annex"));
 /** Fixture: the player is mayor; a hut of level III turned once stands on a cleared pad north of the village; carpentry is researched; the
  *  hall holds exactly what the annex's survey asks for. */
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  settlement.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(1000);l.setWeatherParameters(6000,0,false,false);
  var hut=new Settlement.Building(Settlement.childId(settlement.id(),"building/annex-"+KIND.parent()),KIND.parent(),6,0,-72,ROTATION,1);settlement.addBuilding(hut);hutId=hut.id();
  var origin=BuildingPlacement.origin(e,hut);var size=BuildingPlacement.size(hut.type(),hut.rotation());
  // The pad: the hut's lot, its annex's side (north for this turn) and a margin, cleared to grass on dirt.
  // Margin 14: the tower mill beside a turned restaurant reaches 9 blocks off the lot, and its survey keeps a buffer round it.
  for(int x=-14;x<size[0]+14;x++)for(int z=-14;z<size[1]+14;z++){var g=origin.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g.below(2),Blocks.DIRT.defaultBlockState(),3);
   l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=1;y<18;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),3);}
  for(int n=2;n<=KIND.parentLevel();n++)settlement.raiseBuildingLevel(hut.id(),n);hut=hut(e);
  for(var cell:BuildingPlacement.layout(e,hut,BuildingTiers.layoutId(hut.type(),KIND.parentLevel())).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  var research=BookResearch.inspect(l,e);var done=research.getList("legacyDone",Tag.TAG_STRING);done.add(StringTag.valueOf(KIND.research()));research.put("legacyDone",done);BookResearch.store(l,e,research);ResearchKnobs.forget(settlement.id());
  if(HallUpgradeGoal.pending(l,settlement.id())){HallUpgradeGoal.drop(l,settlement.id());LogUtils.getLogger().info("ASTRA_ANNEX fixture cleared the project the village had queued");}
  int mended=0;
  for(var b:List.copyOf(settlement.buildings())){if(b.id().equals(hutId))continue;var missing=BuildingRepairs.damage(l,e,b);if(missing.isEmpty())continue;
   var design=BuildingPlacement.layout(BuildingRepairs.design(e,b),e.center().offset(b.x(),b.y(),b.z()),b.rotation());
   for(var pos:missing){var block=design.get(pos);if(block!=null)l.setBlock(pos,block,3);}mended+=missing.size();}
  if(mended>0)LogUtils.getLogger().info("ASTRA_ANNEX fixture made {} cells of the village whole again",mended);
  var survey=Annexes.survey(l,e,hut,KIND);if(!survey.ok())throw new IllegalStateException("The annex's site is not free on the pad: "+survey.reason()+" "+survey.conflicts().size()+" "+survey.conflicts().stream().limit(4).map(c->c.toShortString()+"="+l.getBlockState(c).getBlock()).toList()+" origin "+Annexes.origin(e,hut,KIND).toShortString());
  var cost=survey.state().getCompound("cost");var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));if(hall==null)throw new IllegalStateException("Hall chest missing");
  int slot=0;
  // AD-137: the hall holds exactly the estimate — once ordered, the rest of the village leaves it to the builder (no margin any more).
  for(var key:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));
   int left=cost.getInt(key);
   while(left>0){while(slot<hall.getContainerSize()&&!hall.getItem(slot).isEmpty())slot++;if(slot>=hall.getContainerSize())throw new IllegalStateException("Hall chest is full");
    int n=Math.min(item.getMaxStackSize(),left);hall.setItem(slot,new ItemStack(item,n));left-=n;}}
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_ANNEX fixture hut III turned {} at {}, annex site at {}, {} item kinds in the hall",ROTATION,origin.toShortString(),Annexes.origin(e,hut,KIND).toShortString(),cost.getAllKeys().size());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 /** The project's current operation as the crew sees it: where, what it lays, and the stand it chose. */
 private static String op(CompoundTag project){var ops=project.getList("ops",Tag.TAG_COMPOUND);int i=project.getInt("index");if(i>=ops.size())return "-";var o=ops.getCompound(i);
  var st=HallConstructionPlan.step(o);return st.pos().toShortString()+" "+BuiltInRegistries.BLOCK.getKey(st.after().getBlock())+(o.contains("stand")?" stand="+BlockPos.of(o.getLong("stand")).toShortString():"")+(o.contains("why")?" why="+o.getString("why"):"");}
 /** The camera out beyond the annex, looking back at it and the hut's side it leans on. */
 private static void look(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);var hut=hut(e);
  var a=Annexes.origin(e,hut,KIND);var as=BuildingPlacement.size(KIND.type(),hut.rotation());var ho=BuildingPlacement.origin(e,hut);var hs=BuildingPlacement.size(hut.type(),hut.rotation());
  double ax=a.getX()+as[0]/2.0,az=a.getZ()+as[1]/2.0,hx=ho.getX()+hs[0]/2.0,hz=ho.getZ()+hs[1]/2.0;double dx=ax-hx,dz=az-hz,n=Math.sqrt(dx*dx+dz*dz);dx/=n;dz/=n;
  // Out from the annex away from the hut, and to the street side of it (the hut's front, z 0 of its unturned lot), so the annex's street
  // face — the mill's sails — the hut's side and its front are all in the frame.
  int w=BuildingBlueprints.design(hut.type()).width();var f0=BuildingPlacement.at(e,hut,w/2,0,0);var f1=BuildingPlacement.at(e,hut,w/2,0,-1);
  double sx=f1.getX()-f0.getX(),sz=f1.getZ()-f0.getZ();
  double ex=ax+dx*10+sx*14,ez=az+dz*10+sz*14,ey=a.getY()+9;double tx=ax-dx*3-ex,tz=az-dz*3-ez;
  float yaw=(float)Math.toDegrees(Math.atan2(-tx,tz)),pitch=(float)Math.toDegrees(Math.atan2(7,Math.sqrt(tx*tx+tz*tz)));
  // Noon and a clear sky for every frame: the warped server runs the day on while the crew builds.
  s.overworld().setDayTime(6000);s.overworld().setWeatherParameters(12000,0,false,false);
  var p=s.getPlayerList().getPlayers().get(0);p.teleportTo(s.overworld(),ex,ey,ez,yaw,pitch);});}
 /** Raises the hut one level with that level's plan laid, and checks the annex still stands whole beside it. */
 private static void raise(Minecraft mc,int level){var s=mc.getSingleplayerServer();s.execute(()->{try{var l=s.overworld();var e=entry(s);
  e.settlement().raiseBuildingLevel(hutId,level);var hut=hut(e);
  for(var cell:BuildingPlacement.layout(e,hut,BuildingTiers.layoutId(hut.type(),level)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  var annex=e.settlement().buildings().stream().filter(b->b.id().equals(annexId)).findFirst().orElseThrow();
  var missing=BuildingRepairs.damage(l,e,annex);if(!missing.isEmpty()){intact=false;failure="The annex lost "+missing.size()+" cells beside hut level "+level;}
  SettlementData.get(s).setDirty();}catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>40000)throw new IllegalStateException("Annex timeout phase="+phase+" "+progress);
  var s=mc.getSingleplayerServer();
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>40){BuildingsPanel.select(hutId);OfficeProbes.open(mc,ConstructionScreen.BUILDINGS);phase=2;ticks=0;}
  else if(phase==2&&ticks>60&&mc.screen instanceof ConstructionScreen screen){
   var panel=(BuildingsPanel)screen.panel(ConstructionScreen.BUILDINGS);
   CompoundTag card=null;for(var raw:ConstructionOverlay.snapshot().getList("cards",Tag.TAG_COMPOUND))if(((CompoundTag)raw).hasUUID("id")&&((CompoundTag)raw).getUUID("id").equals(hutId))card=(CompoundTag)raw;
   if(card==null){if(ticks>600)throw new IllegalStateException("The hut has no card in the office");return;}
   var annexes=card.getList("annexes",Tag.TAG_COMPOUND);
   if(annexes.isEmpty())throw new IllegalStateException("The hut's card names no annex: "+card.getAllKeys());
   String state=annexes.getCompound(0).getString("state");progress="card annex state="+state;
   if(state.equals("busy")){if(ticks>6000)throw new IllegalStateException("The crew never became free");return;}
   if(!state.equals("ready"))throw new IllegalStateException("The carpentry should be ready to order: "+annexes.getCompound(0));
   var button=panel.annexButton();
   // The panel lays the card out on its next frame after the snapshot names the annex ready (probe mill-probe12 read it a frame early).
   if(!button.visible||!button.active){if(++waited<200){BuildingsPanel.select(hutId);return;}throw new IllegalStateException("The annex button is not offered: visible="+button.visible+" active="+button.active);}
   var problems=panel.layoutProblems();if(!problems.isEmpty())throw new IllegalStateException("Card layout: "+problems);
   capture(mc,"card");
   OfficeProbes.clickOrFail(screen,button,"The annex button");phase=3;ticks=0;}
  else if(phase==3&&ticks%100==0){
   s.execute(()->{var l=s.overworld();var e=entry(s);var hut=hut(e);
    var annex=e.settlement().annexes(hut.id()).stream().findFirst().orElse(null);
    var project=HallUpgradeGoal.pending(l,e.settlement().id())?HallUpgradeGoal.inspect(l,e.settlement().id()):new CompoundTag();
    if(annex!=null){annexId=annex.id();built=true;}
    var crew=new StringBuilder();
    for(var x:e.settlement().residents())if(x.profession()==Profession.BUILDER&&x.alive())crew.append(l.getEntity(x.id()) instanceof ResidentEntity r?r.workStatus()+"@"+r.blockPosition().toShortString()+" "+r.runningGoals()+"; ":"away; ");
    var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));var short_=new StringBuilder();var cost=project.getCompound("cost");
    for(var key:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));int held=0;
     for(var raw:project.getList("cargo",Tag.TAG_COMPOUND)){var st=ItemStack.of((CompoundTag)raw);if(st.is(item))held+=st.getCount();}
     int have=hall==null?0:LogisticsRoutes.count(hall,x->x.is(item));if(held+have<cost.getInt(key))short_.append(key.replace("minecraft:","")).append(' ').append(held).append('+').append(have).append('/').append(cost.getInt(key)).append(' ');}
    progress="short=["+short_+"] queued="+Annexes.queued(l,e,hut,KIND)+" index="+project.getInt("index")+"/"+project.getList("ops",Tag.TAG_COMPOUND).size()+" funded="+project.getBoolean("funded")+" cargo="+project.getList("cargo",Tag.TAG_COMPOUND).size()+" built="+(annex!=null)+" crew="+crew+" op="+op(project);});
   if(ticks%1000==0)LogUtils.getLogger().info("ASTRA_ANNEX progress {}",progress);
   if(ticks==300&&!progress.contains("queued=true")&&!built)throw new IllegalStateException("The order did not queue the annex: "+progress);
   if(built){if(mc.screen!=null)mc.setScreen(null);mc.options.hideGui=true;look(mc);phase=4;ticks=0;}}
  else if(phase==4&&ticks>80){int level=KIND.parentLevel()+shot;capture(mc,"hut-"+level);SHOWN.add(level);shot++;
   if(level<6){raise(mc,level+1);phase=5;ticks=0;}else{phase=6;ticks=0;}}
  else if(phase==5&&ticks>40){look(mc);phase=4;ticks=0;}
  else if(phase==6&&ticks>10){mc.options.hideGui=false;
   s.execute(()->{var e=entry(s);var hut=hut(e);var annex=e.settlement().buildings().stream().filter(b->b.id().equals(annexId)).findFirst().orElseThrow();
    progress="linked="+hutId.equals(e.settlement().annexParent(annexId))+" turned="+(annex.rotation()==hut.rotation())+" level="+annex.level();});
   phase=7;ticks=0;}
  else if(phase==7&&ticks>10){
   if(!progress.equals("linked=true turned=true level=1"))throw new IllegalStateException("The annex is not the hut's: "+progress);
   LogUtils.getLogger().info("ASTRA_ANNEX VERIFIED ordered from the office, built by the crew, linked=true turned=true intact={} beside hut levels {}; reload=false",intact,SHOWN);
   mc.stop();phase=8;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_ANNEX FAILED",ex);mc.options.hideGui=false;mc.stop();}}
}
