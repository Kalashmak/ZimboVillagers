package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-126: the construction schematic in the real client — an estimate with its brackets, a half-built house with a foreign block in the
 *  way, the cut-away from inside, the focus layer mode through the real key handler, one more step (a fade, at most two layers baked
 *  again, then no baking while nothing changes) and hiding it with B. */
final class SchematicProbe {
 private static int phase,ticks,bakes0,index0,step,fadesSeen,hudDraws;private static volatile boolean serverDone;private static volatile String failure;
 private static volatile int placeAt=-1;private static int rebake,idleBakes,cut,layers;private static long vertexCount;
 static boolean enabled(){return Boolean.getBoolean("villageastra.schematicSmoke");}
 private static SettlementData.Entry entry(MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 static void setup(MinecraftServer s){
  var l=s.overworld();var e=entry(s);for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc)npc.setNoAi(true);
  e.settlement().appointPlayerMayor(s.getPlayerList().getPlayers().get(0).getUUID());SettlementData.get(s).setDirty();
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,s);
  l.setDayTime(6000);l.setWeatherParameters(24000,0,false,false);
 }
 private static void server(Minecraft mc,java.util.function.Consumer<MinecraftServer> work){serverDone=false;var s=mc.getSingleplayerServer();s.execute(()->{try{work.accept(s);serverDone=true;}catch(Exception ex){failure=ex.toString();}});}
 /** A camera about {@code distance} blocks away on the diagonal, looking down at 30° on the site's centre. */
 private static void look(MinecraftServer s,Vec3 c,double distance,double side){
  double h=distance*Math.tan(Math.toRadians(30));double x=c.x-distance*side/Math.sqrt(2),z=c.z-distance/Math.sqrt(2),y=c.y+h;
  float yaw=(float)(Math.toDegrees(Math.atan2(c.z-z,c.x-x))-90);var p=s.getPlayerList().getPlayers().get(0);p.teleportTo(s.overworld(),x,y-1.62,z,yaw,30);
 }
 private static void capture(Minecraft mc,String suffix)throws Exception{
  var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-schematic-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_SCHEMATIC screenshot {}",path);
 }
 private static boolean settled(){return SchematicRenderer.pendingLayers==0&&SchematicRenderer.bakedLayers>0;}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>6000)throw new IllegalStateException("Schematic timeout phase="+phase);
  var t=ConstructionOverlay.snapshot();
  if(ticks%200==0)LogUtils.getLogger().info("ASTRA_SCHEMATIC progress phase={} index={} layers={} pending={} bakes={}",phase,t.getInt("index"),SchematicRenderer.bakedLayers,SchematicRenderer.pendingLayers,SchematicRenderer.bakes);
  switch(phase){
   // 1. The estimate: the mayor asks for the hall's draft at the hall.
   case 0->{if(t.getBoolean("canManage")){server(mc,s->{var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);p.teleportTo(s.overworld(),e.center().getX()+3.5,e.center().getY()+1,e.center().getZ()+3.5,0,20);
     var g=e.settlement().governance();if(!ConstructionDrafts.order(p,e.settlement().id(),g.epoch(),g.revision(),0,null))throw new IllegalStateException("Draft refused");});phase=1;ticks=0;}}
   case 1->{if(serverDone&&t.getBoolean("draft")&&settled()){var c=SchematicRenderer.center();server(mc,s->look(s,c,16,1));phase=2;ticks=0;}}
   case 2->{if(serverDone&&ticks>60){if(SchematicHud.draws==0||SchematicRenderer.layersDrawn==0)throw new IllegalStateException("Draft ghost not drawn");capture(mc,"draft");
     var id=t.getUUID("id");server(mc,s->{var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);p.teleportTo(s.overworld(),e.center().getX()+3.5,e.center().getY()+1,e.center().getZ()+3.5,0,20);var g=e.settlement().governance();
      if(!ConstructionDrafts.order(p,e.settlement().id(),g.epoch(),g.revision(),2,id))throw new IllegalStateException("Draft not cancelled");});phase=3;ticks=0;}}
   // 2. A house ordered at the building-order site, half built, with a gold block where the plan still has work to do.
   case 3->{if(serverDone&&!t.getBoolean("draft")){server(mc,s->{var l=s.overworld();var e=entry(s);var site=BuildingOrderProbe.SITE;
     var reason=BuildingOrders.approve(l,e,"home",0,site);if(!reason.isEmpty())throw new IllegalStateException("Home not ordered: "+reason);
     var state=HallUpgradeGoal.inspect(l,e.settlement().id());var ops=state.getList("ops",Tag.TAG_COMPOUND);HallUpgradeGoal.advanceForProbe(l,e.settlement().id(),ops.size()/2);
     state=HallUpgradeGoal.inspect(l,e.settlement().id());int index=state.getInt("index");
     // C11: the first remaining operation of its cell, not already gold — the highest such cell, so it is seen from outside.
     var first=new java.util.HashSet<Long>();BlockPos gold=null;
     for(int i=index;i<ops.size();i++){var op=ops.getCompound(i);if(op.getBoolean("done"))continue;var st=HallConstructionPlan.step(op);if(!first.add(st.pos().asLong()))continue;
      if(st.before().is(Blocks.GOLD_BLOCK)||!st.before().isAir()||st.after().isAir()||st.after().is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())||i<index+40)continue;
      if(gold==null||st.pos().getY()>gold.getY())gold=st.pos();}
     if(gold==null)throw new IllegalStateException("No free cell for the conflict");l.setBlock(gold,Blocks.GOLD_BLOCK.defaultBlockState(),3);
     LogUtils.getLogger().info("ASTRA_SCHEMATIC fixture: home at {} advanced to {}/{}, gold at {}",site,index,ops.size(),gold.subtract(site).toShortString());
     var p=s.getPlayerList().getPlayers().get(0);p.teleportTo(l,site.getX()+3.5,site.getY()+4,site.getZ()-6.5,0,30);});phase=4;ticks=0;}}
   case 4->{if(serverDone&&t.getString("design").equals("home")&&t.getInt("conflicts")>0&&!t.getBoolean("draft")&&t.getInt("index")>0&&settled()&&ticks>40){var c=SchematicRenderer.center();server(mc,s->look(s,c,15,1));phase=5;ticks=0;}}
   case 5->{if(serverDone&&ticks>60){if(SchematicRenderer.layersDrawn==0)throw new IllegalStateException("Half-built ghost not drawn");
     if(!SchematicRenderer.MODEL.hasTarget())throw new IllegalStateException("No builder target");if(t.getList("upcoming",Tag.TAG_COMPOUND).isEmpty())throw new IllegalStateException("No upcoming items");
     capture(mc,"half");layers=SchematicRenderer.bakedLayers;vertexCount=SchematicRenderer.vertices;
     // 3. The cut-away: a spectator standing on the lowest remaining layer inside the site.
     var b=SchematicRenderer.MODEL.bounds();var c=SchematicRenderer.center();
     server(mc,s->{var p=s.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.SPECTATOR);p.teleportTo(s.overworld(),c.x,b[1]+1,c.z,45,-10);});phase=6;ticks=0;}}
   case 6->{if(serverDone&&ticks>60){cut=SchematicRenderer.layersCut;if(cut<=0)throw new IllegalStateException("Nothing cut away inside the site");capture(mc,"cutaway");
     var c=SchematicRenderer.center();server(mc,s->{var p=s.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();look(s,c,15,1);});phase=7;ticks=0;}}
   // 4. The layer mode through the real key handler.
   case 7->{if(serverDone&&ticks>20){if(SchematicRenderer.mode!=SchematicModel.Mode.ALL)throw new IllegalStateException("Mode not ALL at start");KeyMapping.click(SchematicKeys.LAYERS.getKey());phase=8;ticks=0;}}
   case 8->{if(ticks>40){if(SchematicRenderer.mode!=SchematicModel.Mode.FOCUS)throw new IllegalStateException("N did not switch to FOCUS: "+SchematicRenderer.mode);capture(mc,"focus");
     // 5. One more placed block: first up to the next placing step, then that step alone.
     index0=t.getInt("index");server(mc,s->{var l=s.overworld();var e=entry(s);var state=HallUpgradeGoal.inspect(l,e.settlement().id());var ops=state.getList("ops",Tag.TAG_COMPOUND);int origin=BlockPos.of(state.getLong("origin")).getY();
      int i=state.getInt("index");while(i<ops.size()&&(ops.getCompound(i).getBoolean("done")||!ConstructionViews.kind(ops.getCompound(i),origin).equals("place")))i++;
      if(i>=ops.size())throw new IllegalStateException("No placing step left");if(i>state.getInt("index"))HallUpgradeGoal.advanceForProbe(l,e.settlement().id(),i);placeAt=i;});phase=9;ticks=0;}}
   case 9->{if(serverDone&&placeAt>=0&&t.getInt("index")==placeAt&&settled()&&ticks>30){bakes0=SchematicRenderer.bakes;index0=placeAt;
     server(mc,s->{var e=entry(s);if(HallUpgradeGoal.advanceForProbe(s.overworld(),e.settlement().id(),index0+1)!=1)throw new IllegalStateException("The step was not applied");});phase=10;ticks=0;}}
   case 10->{fadesSeen=Math.max(fadesSeen,SchematicRenderer.fadesActive);
     if(serverDone&&t.getInt("index")>index0&&settled()&&ticks>30){rebake=SchematicRenderer.bakes-bakes0;
      if(fadesSeen<1)throw new IllegalStateException("No fade of the placed block");if(rebake>2)throw new IllegalStateException("One step baked "+rebake+" layers again");
      bakes0=SchematicRenderer.bakes;phase=11;ticks=0;}}
   case 11->{if(ticks>120){idleBakes=SchematicRenderer.bakes-bakes0;if(idleBakes!=0)throw new IllegalStateException("Repeated snapshots baked "+idleBakes+" layers");
     // 6. B hides the schematic and its card.
     KeyMapping.click(SchematicKeys.TOGGLE.getKey());phase=12;ticks=0;}}
   case 12->{if(ticks==10)hudDraws=SchematicHud.draws;if(ticks>20){if(ConstructionOverlay.visible||SchematicRenderer.layersDrawn!=0||SchematicHud.draws!=hudDraws)throw new IllegalStateException("B did not hide the schematic");
     KeyMapping.click(SchematicKeys.TOGGLE.getKey());phase=13;ticks=0;}}
   case 13->{if(ticks>20){if(!ConstructionOverlay.visible||SchematicRenderer.layersDrawn==0)throw new IllegalStateException("B did not show it again");
     double ms=SchematicRenderer.drawNanosAvg/1e6;if(ms>4)throw new IllegalStateException("Draw CPU time "+ms+" ms");if(SchematicRenderer.fallback)throw new IllegalStateException("Fallback to frames");
     LogUtils.getLogger().info("ASTRA_SCHEMATIC VERIFIED layers={} vertices={} rebakeOnStep={} idleBakes={} fades={} cut={} drawMsAvg={} fallback={}",layers,vertexCount,rebake,idleBakes,fadesSeen,cut,String.format(java.util.Locale.ROOT,"%.3f",ms),SchematicRenderer.fallback);
     phase=14;mc.stop();}}
   default->{}
  }
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_SCHEMATIC FAILED",ex);phase=99;mc.stop();}}
}
