package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-104 P2, "a level-I farm feeds its village in play": the starter village lives two days on its own field. The hall holds 10 bread, less
 *  than a day of meals for its six; the field grows at randomTickSpeed 3 and every resident keeps its own AI — the farmer reaps, sows again and
 *  brings the wheat home in batches, the porter hauls it, an idle adult bakes it by hand at the hall. Over 48,000 active ticks nobody may miss a
 *  meal. The player stays by the village, so its chunks tick and its pantry is loaded. */
final class StarterVillageProbe {
 /** Active ticks the village lives on its own; client ticks before the probe gives up; bread the hall starts with. */
 private static final long RUN=48000,TIMEOUT=90000;private static final int BREAD=10;
 /** What the spec asks of two days: the plots reaped, the bread baked by hand, the farmer's work ticks for each plot at most. */
 private static final int REAPED=45,BAKED=10,TICKS_PER_PLOT=400;
 /** The farmer's statuses of real work: walking to a plot or a chest, reaping, tilling. Waiting for crops and sleep are not work. */
 private static final Set<String> WORK=Set.of("walking","working","tilling");
 private static int ticks,shot,phase;private static volatile boolean verified;private static volatile String failure,progress="",verdict="";
 private static volatile long start=-1,busy,sampled=-1;
 static boolean enabled(){return Boolean.getBoolean("villageastra.starterFoodSmoke");}
 /** Server thread, before the harness sets noon: 10 bread in the hall instead of 48, the vanilla random tick speed, nobody frozen. */
 static void setup(MinecraftServer server){
  var l=server.overworld();var data=SettlementData.get(server);var e=data.entries().iterator().next();
  var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));if(hall==null)throw new IllegalStateException("The hall chest is missing");
  for(int slot=0;slot<hall.getContainerSize();slot++)if(hall.getItem(slot).is(Items.BREAD))hall.setItem(slot,ItemStack.EMPTY);
  int free=-1;for(int slot=hall.getContainerSize()-1;slot>=0;slot--)if(hall.getItem(slot).isEmpty())free=slot;
  if(free<0)throw new IllegalStateException("No room for the fixture's bread in the hall");
  hall.setItem(free,new ItemStack(Items.BREAD,BREAD));hall.setChanged();
  l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(3,server);start=data.clock().ticks();
  LogUtils.getLogger().info("ASTRA_STARTER_FOOD fixture: {} bread in the hall ({} rations; a day of meals for the village is {}), randomTickSpeed 3, every resident on its own AI",
   BREAD,Population.storedNutrition(l,e),HandBread.reserveRations(e));
 }
 private static int count(Container c,Item item){if(c==null)return -1;int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;}
 private static String role(Settlement s,UUID id){var r=s.resident(id);return r==null?"stranger":r.profession()==null?"idle":r.profession().name();}
 private static void capture(Minecraft mc)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-starter-food.png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_STARTER_FOOD screenshot {}",path);}
 /** One look at the village on the server thread: meals, the farm's reapings, the bread baked by hand, the stocks and the farmer's work. */
 private static void sample(Minecraft mc){var server=mc.getSingleplayerServer();if(server==null)return;server.execute(()->{try{
  // The verdict covers the 48,000 ticks; a look still queued behind it judges nothing more.
  if(verified)return;
  var l=server.overworld();var data=SettlementData.get(server);var e=data.entries().iterator().next();var s=e.settlement();long active=data.clock().ticks()-start;
  var farmer=s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.FARMER).findFirst().orElse(null);
  var npc=farmer!=null&&l.getEntity(farmer.id()) instanceof ResidentEntity x?x:null;
  // The farmer's own work goal runs and he walks, reaps or tills: the ticks since the last look count as work.
  if(sampled>=0&&npc!=null&&npc.runningGoals().contains("ResourceWorkGoal")&&WORK.contains(npc.workStatus()))busy+=active-sampled;
  sampled=active;
  int missed=0;var hungry=new StringBuilder();
  for(var r:s.residents())if(r.alive()&&r.missedMeals()>0){missed+=r.missedMeals();hungry.append(role(s,r.id())).append('=').append(r.missedMeals()).append(' ');}
  long alive=s.residents().stream().filter(Resident::alive).count();
  var farm=s.buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();
  var record=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+farm.id()+".bin");int reaped=Files.exists(record)?NbtRecord.read(record).getInt("reaped"):0;
  var job=HandBread.inspect(l,s.id());int baked=job.getInt("baked");String baker=job.hasUUID("lastBaker")?role(s,job.getUUID("lastBaker")):"none";
  var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));var field=LogisticsRoutes.chest(l,e,farm);
  int bread=count(hall,Items.BREAD),hallWheat=count(hall,Items.WHEAT),farmWheat=count(field,Items.WHEAT);long perPlot=reaped==0?0:busy/reaped;
  var bakers=new StringBuilder();for(var p:List.of(Profession.MAYOR,Profession.BUILDER))for(var r:s.residents())if(r.alive()&&r.profession()==p&&l.getEntity(r.id()) instanceof ResidentEntity who)
   bakers.append(p.name()).append('=').append(who.workStatus()).append('/').append(who.runningGoals()).append('@').append(who.blockPosition().toShortString()).append(' ');
  // Why no job starts: the wheat a job could take, and what a pending hall project asks in wheat.
  var project=org.villageastra.world.HallUpgradeGoal.pending(l,s.id())?org.villageastra.world.HallUpgradeGoal.inspect(l,s.id()):new net.minecraft.nbt.CompoundTag();
  String wants=project.isEmpty()?"none":project.getString("design")+(project.getBoolean("funded")?" funded":" unfunded")+" wheat="+project.getCompound("cost").getInt("minecraft:wheat")+" hay="+project.getCompound("cost").getInt("minecraft:hay_block");
  progress=String.format(Locale.ROOT,"active=%d/%d reaped=%d baked=%d bread=%d hallWheat=%d farmWheat=%d spare=%d project=[%s] missed=%d alive=%d baker=%s job=%s ticksPerPlot=%d farmer=%s %s",
   active,RUN,reaped,baked,bread,hallWheat,farmWheat,HandBread.wheatAvailable(l,e),wants,missed,alive,baker,job.getString("stage"),perPlot,
   npc==null?"away":npc.workStatus()+" at "+npc.blockPosition().toShortString()+" goals="+npc.runningGoals(),bakers.toString().trim());
  if(missed>0){failure="A meal was missed: "+hungry.toString().trim()+"; "+progress;return;}
  if(alive<6){failure="A resident died: "+progress;return;}
  if(active<RUN||verified)return;
  if(reaped<REAPED)failure="The farmer reaped "+reaped+" plots in two days, fewer than "+REAPED+": "+progress;
  else if(baked<BAKED)failure="Only "+baked+" bread were baked by hand, fewer than "+BAKED+": "+progress;
  else if(baker.equals("FARMER")||baker.equals("PORTER"))failure="The bread was baked by the "+baker+", who has work of his own: "+progress;
  else if(perPlot>TICKS_PER_PLOT)failure="The farmer worked "+perPlot+" ticks a plot, more than "+TICKS_PER_PLOT+": "+progress;
  else{verdict=String.format(Locale.ROOT,"reaped=%d baked=%d missed=0 ticksPerPlot=%d baker=%s bread=%d hallWheat=%d farmWheat=%d active=%d reload=false",reaped,baked,perPlot,baker,bread,hallWheat,farmWheat,active);verified=true;}
 }catch(Exception ex){failure=ex.toString();}});}
 /** The player looks down on the farm and its field from the north, above the farmhouse. */
 private static void frame(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{
  var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();
  var cells=FarmField.cells(e,e.settlement().buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow());
  double x=cells.stream().mapToInt(BlockPos::getX).average().orElse(0),z=cells.stream().mapToInt(BlockPos::getZ).average().orElse(0);int y=cells.get(0).getY();
  server.getPlayerList().getPlayers().get(0).teleportTo(l,x+.5,y+15,z-14.5,0,45);
 }catch(Exception ex){failure="Frame: "+ex;}});}
 static void tick(Minecraft mc){if(phase>=2)return;try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>TIMEOUT)throw new IllegalStateException("Starter food timeout: "+progress);
  if(phase==0&&ticks%20==0){sample(mc);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_STARTER_FOOD progress {}",progress);
   if(verified){LogUtils.getLogger().info("ASTRA_STARTER_FOOD progress {}",progress);frame(mc);phase=1;}}
  else if(phase==1&&++shot>60){
   capture(mc);LogUtils.getLogger().info("ASTRA_STARTER_FOOD VERIFIED {}",verdict);
   mc.setScreen(null);mc.stop();phase=2;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_STARTER_FOOD FAILED",ex);phase=9;mc.stop();}}
}
