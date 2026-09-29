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
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-094: the mayor orders a castle wall through the map's own order message; the builders put it up block by block from the hall's
 *  stone, an archer tower follows as a building of its own, and a trained archer climbs to its platform. */
final class WallProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,walled,manned,onTower,towerStocked;
 private static volatile int shape,radius;private static volatile long epoch,center;private static volatile UUID village,archer,tower;
 static boolean enabled(){return Boolean.getBoolean("villageastra.wallSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-wall-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_WALL screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entry(village);}
 /** The hall is kept stocked with what the builders still need, as the village's porters would; returns how many items were put in. */
 private static int topUp(OwnedChestEntity hall,Map<Item,Integer> needs){int added=0;
  for(var need:needs.entrySet()){int have=LogisticsRoutes.count(hall,x->x.is(need.getKey()));int left=need.getValue()-have;
   for(int slot=0;slot<hall.getContainerSize()&&left>0;slot++)if(hall.getItem(slot).isEmpty()){int n=Math.min(need.getKey().getMaxStackSize(),left);hall.setItem(slot,new ItemStack(need.getKey(),n));left-=n;added+=n;}}
  return added;}
 /** The village's own repairs would take the crew off the wall: the fixture puts back what the village had lost and calls off the repair. */
 private static void callOff(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,String when){
  var state=HallUpgradeGoal.inspect(l,village);int mended=0;
  for(var b:List.copyOf(e.settlement().buildings())){var missing=BuildingRepairs.damage(l,e,b);if(missing.isEmpty())continue;
   var design=BuildingPlacement.layout(BuildingRepairs.design(e,b),e.center().offset(b.x(),b.y(),b.z()),b.rotation());
   for(var pos:missing){var block=design.get(pos);if(block!=null)l.setBlock(pos,block,3);}mended+=missing.size();}
  HallUpgradeGoal.drop(l,village);
  LogUtils.getLogger().info("ASTRA_WALL the mayor called off {} {} ({} cells of the village made whole)",state.getString("design"),when,mended);}
 private static Map<Item,Integer> items(CompoundTag cost){var out=new LinkedHashMap<Item,Integer>();for(var key:cost.getAllKeys())out.put(BuiltInRegistries.ITEM.get(new ResourceLocation(key)),cost.getInt(key));return out;}
 private static String crew(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e){var out=new StringBuilder();
  for(var x:e.settlement().residents())if(x.alive()&&x.profession()==Profession.BUILDER)out.append(l.getEntity(x.id()) instanceof ResidentEntity r?r.workStatus()+" at "+r.blockPosition().toShortString()+" goals="+r.runningGoals()+"; ":"away; ");
  return out.toString();}
 /** Fixture: the player is mayor, both researches are done, the crew is free, and the first ring from 18 blocks out that stands clear is chosen. */
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=SettlementData.get(s).entries().iterator().next();var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  village=settlement.id();settlement.appointPlayerMayor(p.getUUID());epoch=settlement.governance().epoch();center=e.center().asLong();
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(1000);
  var research=BookResearch.inspect(l,e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  for(var id:List.of("defense.1","defense.2"))done.add(StringTag.valueOf(id));research.put("legacyDone",done);BookResearch.store(l,e,research);
  if(HallUpgradeGoal.pending(l,village))callOff(l,e,"before the wall");
  var road=Roads.project(l,village);if(road!=null&&!road.getBoolean("complete")){road.putBoolean("complete",true);Roads.save(l,village,road);LogUtils.getLogger().info("ASTRA_WALL fixture closed the road work the village had open");}
  var tried=new StringBuilder();Walls.Plan chosen=null;
  search:for(var sh:List.of(Walls.Shape.ROUND,Walls.Shape.SQUARE))for(int r=18;r<=40;r+=2){var plan=Walls.plan(l,e,sh,r);tried.append(sh.name().charAt(0)).append(r).append('=').append(plan.reason().isEmpty()?plan.blocks().size():plan.reason()).append(' ');
   if(plan.reason().isEmpty()&&plan.blocks().size()<=1200&&!plan.towers().isEmpty()){chosen=plan;break search;}}
  LogUtils.getLogger().info("ASTRA_WALL rings tried: {}",tried);
  if(chosen==null)throw new IllegalStateException("No ring stands clear around this village: "+tried);
  shape=chosen.shape()==Walls.Shape.ROUND?1:0;radius=chosen.radius();
  var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));if(hall==null)throw new IllegalStateException("Hall chest missing");
  int put=topUp(hall,items(Walls.project(l,chosen).getCompound("cost")));
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_WALL fixture ring {} radius {}: {} blocks, {} gates, {} towers; {} items put in the hall",chosen.shape(),radius,chosen.blocks().size(),chosen.gates().size(),chosen.towers().size(),put);
  ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 /** Samples the work on the server: the wall, then the tower, then its archer. */
 private static void sample(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);if(e==null){failure="The village disappeared";return;}
  var record=Walls.record(s,village);
  if(record==null){progress="no wall recorded yet";return;}
  int built=0;var cells=record.getLongArray("cells");for(long c:cells)if(!l.getBlockState(BlockPos.of(c)).isAir())built++;
  var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));
  var project=Roads.project(l,village);
  boolean wallProject=project!=null&&project.getUUID("id").equals(record.getUUID("project"));
  if(!walled){
   // The village's own repairs would hold the crew off the road work; the mayor calls them off while the wall goes up.
   if(HallUpgradeGoal.pending(l,village))callOff(l,e,"while the wall goes up");
   if(wallProject&&!project.getBoolean("complete")&&hall!=null)topUp(hall,Roads.needs(project));
   progress="wall built="+built+"/"+cells.length+" project="+(wallProject?project.getInt("index")+"/"+project.getList("ops",Tag.TAG_COMPOUND).size()+" complete="+project.getBoolean("complete"):"other")+" crew=["+crew(l,e)+"]";
   if(wallProject&&project.getBoolean("complete")){walled=true;LogUtils.getLogger().info("ASTRA_WALL the wall stands: {}",progress);}
   return;}
  var standing=e.settlement().buildings().stream().filter(b->b.type().equals(Walls.TOWER)).findFirst().orElse(null);
  if(standing==null){
   var state=HallUpgradeGoal.inspect(l,village);boolean ours=HallUpgradeGoal.pending(l,village)&&state.getString("design").equals(Walls.TOWER);
   if(HallUpgradeGoal.pending(l,village)&&!ours)callOff(l,e,"that stood before the tower");
   if(ours&&!towerStocked&&hall!=null){towerStocked=true;LogUtils.getLogger().info("ASTRA_WALL tower queued at {}: {} items put in the hall",BlockPos.of(state.getLong("origin")).toShortString(),topUp(hall,items(state.getCompound("cost"))));}
   progress="wall built="+built+"/"+cells.length+" tower="+(ours?state.getInt("index")+"/"+state.getList("ops",Tag.TAG_COMPOUND).size():"waiting")+" crew=["+crew(l,e)+"]";
   return;}
  tower=standing.id();
  if(!manned){
   var settlement=e.settlement();
   var recruit=settlement.residents().stream().filter(r->r.alive()&&r.life()==Resident.Life.ADULT&&r.profession()!=Profession.BUILDER&&r.profession()!=Profession.MAYOR&&r.profession()!=Profession.ARCHER_GUARD).findFirst().orElse(null);
   if(recruit==null){failure="Nobody to send up the tower";return;}
   recruit.trainMilitary();settlement.assign(recruit.id(),Profession.ARCHER_GUARD,standing.id());archer=recruit.id();
   var chest=LogisticsRoutes.chest(l,e,standing);if(chest==null){failure="The tower has no chest";return;}
   topUp(chest,Map.of(Items.BOW,1,Items.ARROW,32));SettlementData.get(s).setDirty();manned=true;
   LogUtils.getLogger().info("ASTRA_WALL a trained archer is sent to the tower at {}",BuildingPlacement.origin(e,standing).toShortString());return;}
  var platform=BuildingPlacement.at(e,standing,3,8,3);var npc=l.getEntity(archer) instanceof ResidentEntity r?r:null;
  progress="wall built="+built+"/"+cells.length+" tower="+BuildingPlacement.origin(e,standing).toShortString()+" archer="+(npc==null?"away":npc.workStatus()+" at "+npc.blockPosition().toShortString())+" platform="+platform.toShortString();
  if(npc!=null&&npc.workStatus().equals("guard_on_tower")&&npc.blockPosition().distSqr(platform)<=4){onTower=true;
   // The player looks at the tower from outside the ring.
   var c=e.center();var p=s.getPlayerList().getPlayers().get(0);var out=platform.offset(Integer.signum(platform.getX()-c.getX())*10,-2,Integer.signum(platform.getZ()-c.getZ())*10);
   double dx=platform.getX()-out.getX(),dz=platform.getZ()-out.getZ(),dy=platform.getY()-out.getY()-1.6;
   p.teleportTo(l,out.getX()+.5,out.getY(),out.getZ()+.5,(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));}
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>90000)throw new IllegalStateException("Wall timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>40){
   // Exactly what the map's «Order» button of the wall tool sends.
   ConstructionNetwork.sendMapOrder(new ConstructionNetwork.MapOrder(village,epoch,MapOrders.WALL,1,center,center,shape,radius,""));
   LogUtils.getLogger().info("ASTRA_WALL the mayor ordered a {} wall of radius {} from the map",shape==1?"round":"square",radius);phase=2;ticks=0;}
  else if(phase==2&&ticks%100==0){sample(mc);LogUtils.getLogger().info("ASTRA_WALL progress {}",progress);
   if(ticks>1200&&progress.startsWith("no wall"))throw new IllegalStateException("The wall order was never taken");
   if(walled&&phase==2){phase=3;ticks=0;}}
  else if(phase==3&&ticks==60)capture(mc,"wall");
  else if(phase==3&&ticks>60&&ticks%100==0){sample(mc);LogUtils.getLogger().info("ASTRA_WALL progress {}",progress);if(onTower){phase=4;ticks=0;}
   if(ticks>40000)throw new IllegalStateException("The tower was never manned: "+progress);}
  else if(phase==4&&ticks>60){
   capture(mc,"tower");
   LogUtils.getLogger().info("ASTRA_WALL VERIFIED the builders put up the {} wall from the hall's stone, a tower followed and its archer stands on it: {}; reload=false",shape==1?"round":"square",progress);
   mc.setScreen(null);mc.stop();phase=5;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_WALL FAILED",ex);mc.stop();}}
}
