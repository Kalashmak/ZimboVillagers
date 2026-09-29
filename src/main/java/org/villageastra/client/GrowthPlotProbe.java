package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import org.villageastra.domain.Profession;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** NPC selects and walks to a spacious site. No player construction order or worker teleport. */
final class GrowthPlotProbe {
 private static int ticks,finished;private static volatile String failure;private static volatile boolean done;private static MayorPlanner.Proposal chosen;private static boolean visited;
 static boolean enabled(){return Boolean.getBoolean("villageastra.growthPlotsSmoke");}
 static void setup(net.minecraft.server.MinecraftServer server){
  var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();
  for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc&&r.profession()!=Profession.MAYOR)npc.setNoAi(true);
  server.getPlayerList().getPlayers().get(0).setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
  MayorPlanner.clear();for(int i=0;i<100&&chosen==null;i++)chosen=MayorPlanner.plan(l,e);
  if(chosen==null)throw new IllegalStateException("No spaced NPC proposal on the starter terrain");
  if(!GrowthPlots.available(e,chosen.design(),chosen.site(),0))throw new IllegalStateException("Proposal consumes reserved expansion space");
  var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(l,chosen.site().getX()+18,chosen.site().getY()+25,chosen.site().getZ()+18,135,48);
  MinecraftForge.EVENT_BUS.addListener(GrowthPlotProbe::observe);
  LogUtils.getLogger().info("ASTRA_GROWTH fixture proposal={} at={} expansion=6 passage=6; only NPC mayor active",chosen.design(),chosen.site().toShortString());
 }
 private static void observe(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END||done||failure!=null)return;try{
  var server=event.getServer();var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();
  var mayor=e.settlement().residents().stream().filter(r->r.profession()==Profession.MAYOR).findFirst().orElseThrow();var npc=(ResidentEntity)l.getEntity(mayor.id());
  visited|=npc.distanceToSqr(chosen.site().getX()+.5,chosen.site().getY()+1,chosen.site().getZ()+.5)<=BuildingOrders.SITE_DISTANCE*BuildingOrders.SITE_DISTANCE;
  if(HallUpgradeGoal.pending(l,e.settlement().id())){var state=HallUpgradeGoal.inspect(l,e.settlement().id());
   if(!state.getString("design").equals(chosen.design())||state.getLong("origin")!=chosen.site().asLong())throw new IllegalStateException("Different project approved");
   if(!GrowthPlots.available(e,chosen.design(),chosen.site(),0))throw new IllegalStateException("Reserve invalidated before approval");
   if(!visited)throw new IllegalStateException("Mayor never approached the site before approval");done=true;
  }
 }catch(Exception ex){failure=ex.toString();}}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>18000)throw new IllegalStateException("NPC approval timeout");
  var server=mc.getSingleplayerServer();
  if(done&&++finished==40){var file=java.nio.file.Path.of("../docs/runs/"+server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-growth.png");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(file);}LogUtils.getLogger().info("ASTRA_GROWTH screenshot {}",file);LogUtils.getLogger().info("ASTRA_GROWTH VERIFIED expansion=6 passage=6 physicalApproval=true");mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_GROWTH FAILED",ex);mc.stop();}}
}
