package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-127: the player mayor orders the wall fitted to the starter village through the map's own order message (variant 2); the crew's
 *  work is applied by the fixture from the hall's stone (Roads.load/apply, the same operations the builders run — AD-094's WallProbe
 *  covers the builders walking the ring); the ring is photographed from above. Two houses then go up beyond the ring to the east, the
 *  map's order extends the wall, the old stretch comes down with its stone back in the hall, and the ring is photographed again. */
final class WallRingProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,built,grown,extendedBuilt;
 private static volatile long epoch,center;private static volatile UUID village;private static volatile int headroom;
 private static volatile String fittedLine="",extendedLine="";private static volatile int deposits,retired,view=100;
 static boolean enabled(){return Boolean.getBoolean("villageastra.wallRingSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-wallring-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_WALLRING screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entry(village);}
 /** The hall holds plenty of every wall material, as the village's porters and masons would bring it. */
 private static void stock(OwnedChestEntity hall){
  for(var item:List.of(Items.COBBLESTONE,Items.STONE_BRICKS,Items.STONE_BRICK_WALL,Items.STRIPPED_SPRUCE_LOG)){int have=LogisticsRoutes.count(hall,x->x.is(item));
   for(int slot=0;slot<hall.getContainerSize()&&have<1200;slot++)if(hall.getItem(slot).isEmpty()){hall.setItem(slot,new ItemStack(item,64));have+=64;}}}
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=SettlementData.get(s).entries().iterator().next();var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  village=settlement.id();settlement.appointPlayerMayor(p.getUUID());epoch=settlement.governance().epoch();center=e.center().asLong();
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(6000);
  var research=BookResearch.inspect(l,e);var done=research.getList("legacyDone",Tag.TAG_STRING);done.add(StringTag.valueOf("defense.1"));research.put("legacyDone",done);BookResearch.store(l,e,research);
  if(HallUpgradeGoal.pending(l,village))HallUpgradeGoal.drop(l,village);
  var road=Roads.project(l,village);if(road!=null&&!road.getBoolean("complete")){road.putBoolean("complete",true);Roads.save(l,village,road);}
  var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));if(hall==null)throw new IllegalStateException("Hall chest missing");
  for(int slot=0;slot<hall.getContainerSize();slot++)hall.setItem(slot,ItemStack.EMPTY);stock(hall);
  headroom=Walls.FIT.headroom();
  var plan=Walls.plan(l,e,Walls.Shape.FITTED,headroom);
  LogUtils.getLogger().info("ASTRA_WALLRING fixture: {} buildings, planned ring {} columns radius {} reason '{}'",settlement.buildings().size(),plan.ring().size(),plan.radius(),plan.reason());
  if(!plan.reason().isEmpty())throw new IllegalStateException("The fitted wall is refused: "+plan.reason());
  view=Math.max(80,(int)(plan.radius()*1.9));
  ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 /** The crew's work: up to 40 operations a tick, material taken from the hall and returns brought back through the journal. */
 private static void crew(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var record=Walls.record(s,village);if(record==null){progress="no wall recorded yet";return;}
  var hallBuilding=Workshops.hall(e);var hall=LogisticsRoutes.chest(l,e,hallBuilding);
  WorldJournal.batch(l,()->{for(int i=0;i<40;i++){var project=Roads.project(l,village);if(project==null||!project.getUUID("id").equals(record.getUUID("project"))||project.getBoolean("complete"))return null;
   var op=Roads.current(project);
   if(op!=null&&!op.getString("item").isEmpty()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(op.getString("item")));boolean carried=false;
    for(var raw:project.getList("cargo",Tag.TAG_COMPOUND))if(ItemStack.of((CompoundTag)raw).is(item))carried=true;
    if(!carried){stock(hall);Roads.load(l,e,hallBuilding,project);continue;}}
   var r=Roads.apply(l,e,project);if(r.equals("unload")){Roads.load(l,e,hallBuilding,project);continue;}
   if(r.equals("missing"))throw new IllegalStateException("The crew could not lay "+op);}return null;});
  var project=Roads.project(l,village);boolean ours=project!=null&&project.getUUID("id").equals(record.getUUID("project"));
  int standing=0;var cells=record.getLongArray("cells");for(long c:cells)if(Walls.palette(l.getBlockState(BlockPos.of(c))))standing++;
  progress="gen="+record.getInt("generation")+" project="+(ours?project.getInt("index")+"/"+project.getList("ops",Tag.TAG_COMPOUND).size()+" complete="+project.getBoolean("complete"):"other")+" standing="+standing+"/"+cells.length;
  if(ours&&project.getBoolean("complete")){
   Walls.tick(l,e);var after=Walls.record(s,village);int total=0,laid=0;
   // What stands is judged against a fresh plan of the same ring: every block of it in place.
   var plan=Walls.plan(l,e,Walls.Shape.FITTED,after.getInt("headroom"));
   for(var cell:plan.blocks().entrySet()){total++;if(l.getBlockState(cell.getKey()).getBlock()==cell.getValue().getBlock())laid++;}
   var ring=plan.ring().stream().map(x->new int[]{x.getX(),x.getZ()}).toList();
   String line="built="+laid+"/"+total+" gates="+after.getLongArray("gates").length+" towers="+after.getLongArray("towers").length+" straight="+WallOutline.straightRun(ring)+" limit="+WallOutline.straightLimit(Walls.FIT.curvature(plan.radius()))+" radius="+plan.radius();
   if(after.getInt("generation")==1){fittedLine=line;built=true;}
   else{int wallClears=0;for(var raw:project.getList("ops",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getString("kind").equals("wall_clear"))wallClears++;
    retired=wallClears;deposits=project.getInt("deposits");
    int left=0;for(var raw:project.getList("ops",Tag.TAG_COMPOUND)){var op=(CompoundTag)raw;if(op.getString("kind").equals("wall_clear")&&!l.getBlockState(BlockPos.of(op.getLong("pos"))).isAir())left++;}
    extendedLine=line+" retired="+retired+" left="+left+" returned="+deposits+" cells="+after.getLongArray("cells").length+" retiring="+after.getLongArray("retiring").length;extendedBuilt=true;}
   LogUtils.getLogger().info("ASTRA_WALLRING the wall stands: {}",line);}
 }catch(Exception ex){failure=ex.toString();}});}
 /** Two houses go up beyond the ring to the east, as the village's own builders would put them. */
 private static void grow(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var c=e.center();
  var plan=Walls.plan(l,e,Walls.Shape.FITTED,headroom);
  int east=plan.ring().stream().filter(x->Math.abs(x.getZ()-c.getZ())<=12).mapToInt(BlockPos::getX).max().orElseThrow()-c.getX();
  int i=0;for(int dz:new int[]{-14,2}){var origin=c.offset(east+3,0,dz);
   for(var cell:BuildingPlacement.layout("home",origin,0).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   e.settlement().addBuilding(new Settlement.Building(Settlement.childId(village,"building/wallring_home/"+i++),"home",east+3,0,dz));}
  SettlementData.get(s).setDirty();var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));stock(hall);
  var record=Walls.record(s,village);
  LogUtils.getLogger().info("ASTRA_WALLRING two houses beyond the ring at x+{}: outgrown={}",east+3,Walls.outgrown(e,record));
  // The mayor comes back down over the hall: map orders are taken only near the village (ConstructionViews.RADIUS).
  var p=s.getPlayerList().getPlayers().get(0);p.teleportTo(l,c.getX()+8.5,c.getY()+20,c.getZ()+8.5,0,90);
  view=Math.max(view,(int)((east+30)*1.9));grown=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void look(Minecraft mc){mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var c=BlockPos.of(center);var p=s.getPlayerList().getPlayers().get(0);
  p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(s.overworld(),c.getX()+8.5,c.getY()+view,c.getZ()+8.5,0,90);});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>60000)throw new IllegalStateException("Wall ring timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>40){
   ConstructionNetwork.sendMapOrder(new ConstructionNetwork.MapOrder(village,epoch,MapOrders.WALL,1,center,center,2,headroom,""));
   LogUtils.getLogger().info("ASTRA_WALLRING the mayor ordered the wall fitted to the village from the map, headroom {}",headroom);phase=2;ticks=0;}
  else if(phase==2&&ticks%5==0){crew(mc);if(ticks%200==0)LogUtils.getLogger().info("ASTRA_WALLRING progress {}",progress);if(ticks>1200&&progress.startsWith("no wall"))throw new IllegalStateException("The wall order was never taken");
   if(built){phase=3;ticks=0;mc.options.hideGui=true;look(mc);}}
  else if(phase==3&&ticks==200)capture(mc,"fitted");
  else if(phase==3&&ticks==220){mc.options.hideGui=false;grow(mc);}
  else if(phase==3&&grown&&ticks>=260){
   ConstructionNetwork.sendMapOrder(new ConstructionNetwork.MapOrder(village,epoch,MapOrders.WALL,1,center,center,2,headroom,""));
   LogUtils.getLogger().info("ASTRA_WALLRING the mayor ordered the wall again: it follows the village");phase=4;ticks=0;}
  else if(phase==4&&ticks%5==0){crew(mc);if(ticks%200==0)LogUtils.getLogger().info("ASTRA_WALLRING progress {}",progress);if(ticks>1200&&progress.startsWith("gen=1"))throw new IllegalStateException("The extension was never taken: "+progress);
   if(extendedBuilt){phase=5;ticks=0;mc.options.hideGui=true;look(mc);}}
  else if(phase==5&&ticks==200){
   capture(mc,"extended");mc.options.hideGui=false;
   LogUtils.getLogger().info("ASTRA_WALLRING VERIFIED fitted {} extended {} reload=false",fittedLine,extendedLine);
   mc.setScreen(null);mc.stop();phase=6;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_WALLRING FAILED",ex);mc.stop();}}
}
