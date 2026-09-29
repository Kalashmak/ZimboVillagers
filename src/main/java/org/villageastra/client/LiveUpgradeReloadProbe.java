package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import org.villageastra.VillageAstra;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** A second JVM resumes the paid project and treats a second illness from the one remaining real bandage. */
final class LiveUpgradeReloadProbe {
 private static int ticks,phase;private static volatile boolean busy,done;private static volatile String failure;
 private static CompoundTag saved;private static Container stock;private static BlockPos start;private static double walked;
 static boolean enabled(){return Boolean.getBoolean("villageastra.liveUpgradeReloadSmoke");}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Live upgrade reload timeout");
  if(done){LogUtils.getLogger().info("ASTRA_LIVE_UPGRADE_RELOAD VERIFIED restart=true activeProject=true cured=true paidBandage=1 continued=true walked=true");mc.stop();return;}
  if(ticks%10!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_LIVE_UPGRADE_RELOAD FAILED",ex);mc.stop();}}
 private static void step(ServerLevel l){
  if(saved==null)saved=NbtRecord.read(l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-live-upgrade-probe.bin"));
  var data=SettlementData.get(l.getServer());var e=data.entry(saved.getUUID("village"));require(e!=null,"Saved village");
  var b=e.settlement().buildings().stream().filter(x->x.id().equals(saved.getUUID("clinic"))).findFirst().orElseThrow();
  // The common reload fixture starts at the original village; this clinic is in its own settlement 220 blocks away.
  if(phase==0){var player=l.getServer().getPlayerList().getPlayers().get(0);player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);player.teleportTo(l,e.center().getX()+32,e.center().getY()+15,e.center().getZ()-15,25,25);}
  if(!(l.getEntity(saved.getUUID("patient")) instanceof ResidentEntity patient))return;
  var state=HallUpgradeGoal.inspect(l,e.settlement().id());require(state.hasUUID("id")&&state.getUUID("id").equals(saved.getUUID("project")),"Same paid project resumes");
  require(!state.getBoolean("complete")&&BuildingTiers.level(l,e,b)==2,"Old working level remains while project is active");
  var actual=LogisticsRoutes.chest(l,e,b);require(actual!=null,"Clinic inventory loaded");
  if(phase==0){stock=actual;require(stock.countItem(VillageAstra.BANDAGE.get())==1&&!e.settlement().resident(patient.getUUID()).sick(),"First cure and remaining dose persisted without replay");
   e.settlement().resident(patient.getUUID()).fallIll();start=BuildingPlacement.origin(e,b).offset(4,1,-14);patient.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5,0,0);data.setDirty();phase=1;return;}
  require(actual==stock,"Stock survives resumed construction");walked=Math.max(walked,Math.sqrt(patient.distanceToSqr(start.getCenter())));
  if(e.settlement().resident(patient.getUUID()).sick())return;
  int operations=0;for(var raw:state.getList("ops",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getBoolean("done"))operations++;
  require(stock.countItem(VillageAstra.BANDAGE.get())==0&&walked>6,"Second physical visit costs last bandage");
  require(state.getBoolean("funded")&&operations>saved.getInt("operations"),"Builder continues existing funded work after restart");done=true;
 }
}
