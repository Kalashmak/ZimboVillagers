package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-112 (§14): the farm core the real way. The starter village's farm has its level-II research done and the player is mayor; the probe
 *  assembles core_farm from its own shaped recipe, puts it with the rest of the level's cost into the hall chest, reads the Levels tab at
 *  427x240 and 320x240 (core row have/need, effect line 'field 1 → 4', layout rules), orders level II from the tab's own button and waits
 *  for the builder to set the core. Then: core_farm[grade=2] at the design's core cell, the farm works at II on four modules and 320 plots (AD-130),
 *  the chest gave up the core, and the next ring (III) shows grey with the agriculture III title while that research is missing. */
final class CoreProbe {
 private static final int[][] PASSES={{854,480,2,427,240},{960,720,3,320,240}};
 private static final int BUILD_TIMEOUT=12*1200;
 private static int phase,ticks,pass,cardAt;private static volatile String failure,progress="";private static volatile boolean ready,built;
 private static volatile UUID farmId;private static volatile BlockPos corePos;private static volatile String verdict="";private static final List<String> shots=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.coreSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-core-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}shots.add(suffix);LogUtils.getLogger().info("ASTRA_CORE screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building farm(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->farmId==null?b.type().equals("farm"):b.id().equals(farmId)).findFirst().orElseThrow(()->new IllegalStateException("The starter village has no farm"));}
 /** The core from its own recipe: every ingredient's first item in the pattern's cell, matched and assembled by the recipe itself. */
 private static ItemStack craftCore(net.minecraft.server.MinecraftServer s){
  var want=BuiltInRegistries.ITEM.get(new ResourceLocation(CoreCatalog.coreId("farm")));var access=s.registryAccess();
  var recipe=s.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream().filter(r->r.getResultItem(access).is(want)).findFirst().orElseThrow(()->new IllegalStateException("No crafting recipe makes "+CoreCatalog.coreId("farm")));
  if(!(recipe instanceof ShapedRecipe shaped))throw new IllegalStateException("The farm core recipe is not shaped: "+recipe.getId());
  var grid=NonNullList.withSize(9,ItemStack.EMPTY);var ing=shaped.getIngredients();
  for(int row=0;row<shaped.getHeight();row++)for(int col=0;col<shaped.getWidth();col++){var i=ing.get(row*shaped.getWidth()+col);if(!i.isEmpty())grid.set(row*3+col,i.getItems()[0].copyWithCount(1));}
  var box=new TransientCraftingContainer(null,3,3,grid);
  if(!shaped.matches(box,s.overworld()))throw new IllegalStateException("The farm core recipe does not match its own ingredients");
  var out=shaped.assemble(box,access);if(!out.is(want)||out.getCount()!=1)throw new IllegalStateException("The farm core recipe gave "+out);
  LogUtils.getLogger().info("ASTRA_CORE crafted {} from recipe {}",BuiltInRegistries.ITEM.getKey(out.getItem()),recipe.getId());
  return out;
 }
 /** Fixture: the player is mayor, farm II's research is done (agriculture III is not), the crew is free and the hall holds farm II's cost with the crafted core. */
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  settlement.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(1000);
  var farm=farm(e);farmId=farm.id();
  var research=BookResearch.inspect(l,e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  for(var id:BuildingTiers.research("farm",2))done.add(StringTag.valueOf(id));
  research.put("legacyDone",done);BookResearch.store(l,e,research);
  if(!BuildingTiers.missingResearch(l,e,"farm",2).isEmpty())throw new IllegalStateException("Farm II research is still missing: "+BuildingTiers.missingResearch(l,e,"farm",2));
  if(BuildingTiers.missingResearch(l,e,"farm",3).isEmpty())throw new IllegalStateException("Farm III research should still be missing for the ring check");
  if(HallUpgradeGoal.pending(l,settlement.id())){HallUpgradeGoal.drop(l,settlement.id());LogUtils.getLogger().info("ASTRA_CORE fixture cleared the project the village had queued");}
  // AD-136: no building rises above the hall, so a level-two order needs the hall at II first; the mending below then
  // stands the hall's second-level design, as a finished hall project would have left it.
  if(settlement.civilization().level()<2){settlement.civilization().completedHallUpgrade(2);SettlementData.get(s).setDirty();LogUtils.getLogger().info("ASTRA_CORE fixture raised the hall to II");}
  int mended=0;
  for(var b:List.copyOf(settlement.buildings())){var missing=BuildingRepairs.damage(l,e,b);if(missing.isEmpty())continue;
   var design=BuildingPlacement.layout(BuildingRepairs.design(e,b),e.center().offset(b.x(),b.y(),b.z()),b.rotation());
   for(var pos:missing){var block=design.get(pos);if(block!=null)l.setBlock(pos,block,3);}mended+=missing.size();}
  if(mended>0)LogUtils.getLogger().info("ASTRA_CORE fixture made {} cells of the village whole again",mended);
  var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));if(hall==null)throw new IllegalStateException("Hall chest missing");
  var survey=BuildingTiers.survey(l,e,farm);if(!survey.reason().isEmpty())throw new IllegalStateException("Farm II survey refused: "+survey.reason());
  var estimate=survey.state().getCompound("cost");var core=CoreCatalog.coreId("farm");
  if(estimate.getInt(core)!=1)throw new IllegalStateException("Farm II should cost one farm core: "+estimate);
  var stacks=new ArrayList<ItemStack>();stacks.add(craftCore(s));
  for(var key:estimate.getAllKeys()){if(key.equals(core))continue;var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));int left=estimate.getInt(key);
   while(left>0){int n=Math.min(item.getMaxStackSize(),left);stacks.add(new ItemStack(item,n));left-=n;}}
  // AD-137: exactly the estimate, no share for the miner's lights: once ordered, the reserve keeps it for the builder.
  int mineLights=0;
  int slot=0;for(var st:stacks){while(slot<hall.getContainerSize()&&!hall.getItem(slot).isEmpty())slot++;if(slot>=hall.getContainerSize())throw new IllegalStateException("Hall chest is full");hall.setItem(slot,st);}
  var cell=LevelArchitecture.core("farm");corePos=BuildingPlacement.at(e,farm,cell.getX(),cell.getY(),cell.getZ());
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_CORE fixture farm {} kept {}, core cell {}, {} item kinds for level two in the hall, {} lanterns for the mine",farm.id(),farm.level(),corePos.toShortString(),estimate.getAllKeys().size(),mineLights);ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void callOff(){var t=ConstructionOverlay.snapshot();if(!t.hasUUID("village")||!t.hasUUID("id"))return;
  ConstructionNetwork.sendCancel(new ConstructionNetwork.CancelOrder(t.getUUID("village"),t.getUUID("id"),t.getLong("epoch"),t.getLong("revision")));LogUtils.getLogger().info("ASTRA_CORE the mayor called off the village's own project");}
 private static CompoundTag row(){for(var raw:ConstructionOverlay.snapshot().getCompound("upgrades").getList("buildings",Tag.TAG_COMPOUND)){var r=(CompoundTag)raw;if(r.hasUUID("id")&&r.getUUID("id").equals(farmId))return r;}return null;}
 /** Switch the window and GUI scale of a pass; true once the GUI has that size. */
 private static boolean scale(Minecraft mc,int[] p,boolean set){var w=mc.getWindow();
  if(set){w.setWindowed(p[0],p[1]);mc.options.guiScale().set(p[2]);mc.resizeDisplay();return false;}
  return (int)w.getGuiScale()==p[2]&&w.getGuiScaledWidth()==p[3]&&w.getGuiScaledHeight()==p[4];}
 /** The Levels tab of one pass: the farm's row is on screen, its core line says what the probe expects, and the tab keeps its layout rules. */
 private static void checkTab(Minecraft mc,String when)throws Exception{
  if(!(mc.screen instanceof ConstructionScreen screen))throw new IllegalStateException("The office is not open");var p=PASSES[pass];
  var r=row();if(r==null)throw new IllegalStateException("The farm is not on the Levels tab");var c=r.getCompound("core");
  var shown=UpgradePanel.CORE_SHOWN.get(farmId);if(shown==null)throw new IllegalStateException("The farm row drew no core line: "+c);
  String label=(String)shown[0],line=(String)shown[2];var tone=(OfficeUi.Tone)shown[1];
  if(when.equals("before")){
   if(!c.getString("item").equals(CoreCatalog.coreId("farm"))||c.getInt("need")!=1||c.getInt("have")!=1||!c.getList("research",Tag.TAG_STRING).isEmpty())throw new IllegalStateException("Farm II's core row should be core_farm 1/1 with its research done: "+c);
   if(tone!=OfficeUi.Tone.OK||!label.contains("1 / 1"))throw new IllegalStateException("The core cell should read 1 / 1 in green: "+label+" "+tone);
   var field=net.minecraft.network.chat.Component.translatable("core.villageastra.effect.field").getString();
   if(!line.contains(field+": 1 → 4"))throw new IllegalStateException("The effect line should read '"+field+": 1 → 4' (AD-130: a 2x2 at II): "+line);}
  else{
   if(!c.getString("item").equals(CoreCatalog.ringId(3))||c.getList("research",Tag.TAG_STRING).isEmpty())throw new IllegalStateException("Farm III should need ring III and still miss its research: "+c);
   // AD-123 R5 made ResearchEffects.title "branch numeral — essence"; the core label and tooltip name the research by branch and numeral.
   var node=org.villageastra.domain.ResearchCatalog.NODES.get(c.getList("research",Tag.TAG_STRING).getString(0));
   if(node==null)throw new IllegalStateException("Ring III waits for an unknown research: "+c);
   var title=net.minecraft.network.chat.Component.translatable("research.villageastra.branch."+node.branch()).getString()+" "+OfficeUi.roman(node.tier()).getString();
   if(tone!=OfficeUi.Tone.OFF||!label.contains(title))throw new IllegalStateException("Ring III without its research should be grey and name '"+title+"': "+label+" "+tone);
   var tips=CoreTooltips.lines(ConstructionOverlay.snapshot(),CoreCatalog.ringId(3));
   if(tips.isEmpty()||!tips.get(0).getString().contains(title))throw new IllegalStateException("The ring III tooltip does not name '"+title+"': "+tips);
   if(!verdict.contains(" ring3="))verdict+=" ring3="+title.replace(' ','_');}
  var problems=OfficeProbes.layoutProblems(screen);
  LogUtils.getLogger().info("ASTRA_CORE layout {} s{} {}x{} problems={} core='{}' effect='{}'",when,p[2],p[3],p[4],problems,label,line);
  if(!problems.isEmpty())throw new IllegalStateException("Levels tab layout at "+p[3]+"x"+p[4]+": "+problems);
  capture(mc,"levels-"+when+"-s"+p[2]);
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>40000)throw new IllegalStateException("Core probe timeout phase="+phase+" "+progress);
  switch(phase){
   case 0->{if(ticks>60){fixture(mc);phase=1;ticks=0;}}
   // Levels tab before the order, at both GUI sizes.
   case 1->{if(ready&&ticks>40){scale(mc,PASSES[pass],true);phase=2;ticks=0;}}
   case 2->{if(ticks>20){if(!scale(mc,PASSES[pass],false))throw new IllegalStateException("GUI did not reach "+PASSES[pass][3]+"x"+PASSES[pass][4]);OfficeUi.frames=0;OfficeProbes.open(mc,ConstructionScreen.LEVELS);phase=3;ticks=0;}}
   case 3->{if(ticks==30)UpgradePanel.reveal(farmId);
    if(ticks>80){var r=row();
     if(r!=null&&r.getString("refusal").equals("busy")){progress="the village had its own project";if(ticks%100==0)callOff();if(ticks>6000)throw new IllegalStateException("The crew never became free: "+r);return;}
     if(r==null||r.getInt("kept")!=1||r.getInt("lack")!=0||!r.getString("refusal").isEmpty())throw new IllegalStateException("The hall stock and the research should cover farm II: "+r);
     checkTab(mc,"before");
     if(++pass<PASSES.length){phase=1;ticks=0;ready=true;}else{pass=0;phase=4;ticks=0;}}}
   // Order farm II from the row's own button, as the mayor does.
   case 4->{if(ticks>20&&mc.screen instanceof ConstructionScreen screen){UpgradePanel.reveal(farmId);var b=UpgradePanel.buttonFor(farmId);
    if(b==null||!b.active)throw new IllegalStateException("The farm's upgrade button is not open: "+(b instanceof OfficeUi.OfficeButton ob&&ob.reason()!=null?ob.reason().getString():"missing")+" "+row());
    OfficeProbes.clickOrFail(screen,b,"The farm's upgrade button");LogUtils.getLogger().info("ASTRA_CORE the mayor ordered farm II");mc.setScreen(null);phase=5;ticks=0;}}
   case 5->{if(ticks%100==0)mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var farm=farm(e);
     var project=HallUpgradeGoal.inspect(l,e.settlement().id());var at=l.getBlockState(corePos);
     progress="kept="+farm.level()+" level="+BuildingTiers.level(l,e,farm)+" core="+at+" project="+project.getInt("index")+"/"+project.getList("ops",Tag.TAG_COMPOUND).size()+" upgradeLevel="+project.getInt("upgradeLevel")+" complete="+project.getBoolean("complete");
     if(farm.level()>=2&&BuildingTiers.level(l,e,farm)>=2&&(!HallUpgradeGoal.pending(l,e.settlement().id())||project.getBoolean("complete")))built=true;}catch(Exception ex){failure=ex.toString();}});
    if(ticks%600==0)LogUtils.getLogger().info("ASTRA_CORE progress {}",progress);
    if(built){phase=6;ticks=0;}else if(ticks>BUILD_TIMEOUT)throw new IllegalStateException("The builder did not raise the farm in 12 game minutes: "+progress);}
   // What the world, the card and the chest say now.
   case 6->{if(ticks==1)mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var farm=farm(e);var at=l.getBlockState(corePos);
     if(!Cores.isCoreOf(at,"farm")||Cores.grade(at)!=2)throw new IllegalStateException("core_farm[grade=2] should stand at "+corePos.toShortString()+": "+at);
     int level=BuildingTiers.level(l,e,farm),worked=FarmField.worked(l,e,farm).size();if(level!=2||worked!=4)throw new IllegalStateException("The farm should work at II on 4 modules (AD-130): level "+level+", modules "+worked);
     var card=BuildingCards.card(l,e,farm);if(card.getInt("fieldModules")!=4||card.getInt("fieldPlots")!=320||card.getCompound("core").getInt("grade")!=2)throw new IllegalStateException("The card should show core grade 2, 4 modules and 320 plots: "+card);
     var core=BuiltInRegistries.ITEM.get(new ResourceLocation(CoreCatalog.coreId("farm")));int left=LogisticsRoutes.count(LogisticsRoutes.chest(l,e,Workshops.hall(e)),x->x.is(core));
     if(left!=0)throw new IllegalStateException("The hall chest still holds "+left+" farm cores");
     verdict="grade=2 level=2 worked=4 plots=320 cardCore=2 chestCore=0";
     // Stand the player beside the core, looking at it.
     var p=s.getPlayerList().getPlayers().get(0);BlockPos eye=null;
     for(int r=2;r<=4&&eye==null;r++)for(var d:new int[][]{{r,0},{-r,0},{0,r},{0,-r},{r,r},{-r,-r}}){var q=corePos.offset(d[0],0,d[1]);if(l.getBlockState(q).isAir()&&l.getBlockState(q.above()).isAir()){eye=q;break;}}
     if(eye==null)eye=corePos.above(2);double dx=corePos.getX()-eye.getX(),dy=corePos.getY()+.5-(eye.getY()+1.62),dz=corePos.getZ()-eye.getZ();
     p.teleportTo(l,eye.getX()+.5,eye.getY(),eye.getZ()+.5,(float)(Math.toDegrees(Math.atan2(-dx,dz))),(float)(-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)))));
    }catch(Exception ex){failure=ex.toString();}});
    if(ticks>=60&&!verdict.isEmpty()&&!shots.contains("world")){capture(mc,"world");mc.setScreen(new ConstructionScreen(ConstructionScreen.BUILDINGS));BuildingsPanel.select(farmId);cardAt=ticks+80;}
    if(cardAt>0&&ticks>=cardAt){capture(mc,"card");pass=0;phase=7;ticks=0;}
    if(ticks>1200&&verdict.isEmpty())throw new IllegalStateException("The server never checked the built farm");}
   // The Levels tab after: ring III is grey and names agriculture III, at both GUI sizes.
   case 7->{scale(mc,PASSES[pass],true);phase=8;ticks=0;}
   case 8->{if(ticks>20){if(!scale(mc,PASSES[pass],false))throw new IllegalStateException("GUI did not reach "+PASSES[pass][3]+"x"+PASSES[pass][4]);OfficeUi.frames=0;OfficeProbes.open(mc,ConstructionScreen.LEVELS);phase=9;ticks=0;}}
   case 9->{if(ticks==30)UpgradePanel.reveal(farmId);
    if(ticks>80){checkTab(mc,"after");if(++pass<PASSES.length){phase=7;ticks=0;}
     else{LogUtils.getLogger().info("ASTRA_CORE VERIFIED {} layout=true frames={} reload=false",verdict,shots.size());mc.setScreen(null);mc.stop();phase=10;}}}
   default->{}
  }
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_CORE FAILED",ex);phase=10;mc.stop();}}
}
