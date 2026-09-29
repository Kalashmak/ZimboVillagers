package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import org.villageastra.VillageAstra;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** A second JVM checks a spent delivery receipt and the subsequent untreated illness in the saved smoke world. */
final class MedicineReloadProbe {
 private static int ticks,samples;private static volatile boolean busy,done;private static volatile String failure;
 static boolean enabled(){return Boolean.getBoolean("villageastra.medicineReloadSmoke");}
 static void tick(Minecraft mc){try{if(failure!=null)throw new IllegalStateException(failure);if(++ticks>1200)throw new IllegalStateException("Medicine reload timeout");if(done){LogUtils.getLogger().info("ASTRA_MEDICINE_RELOAD VERIFIED sick=true stock=0 dose=0 receipt=true samples=10");mc.stop();return;}
  if(ticks%20!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{var l=mc.getSingleplayerServer().overworld();var data=SettlementData.get(l.getServer());var e=data.entries().stream().filter(x->!MedicineDelivery.inspect(l,x.settlement().id()).isEmpty()).findFirst().orElseThrow();var trip=MedicineDelivery.inspect(l,e.settlement().id());var id=trip.getUUID("patient");var clinic=e.settlement().buildings().stream().filter(b->b.id().equals(trip.getUUID("clinic"))).findFirst().orElseThrow();var p=l.getServer().getPlayerList().getPlayers().get(0);p.setGameMode(GameType.SPECTATOR);p.teleportTo(l,e.center().getX()+20,e.center().getY()+15,e.center().getZ()+10,0,40);
   if(l.getEntity(id)==null)return;var chest=LogisticsRoutes.chest(l,e,clinic);if(!trip.getString("stage").equals("complete")||!e.settlement().resident(id).sick()||MedicinePacks.carried(l,id)||!MedicinePacks.received(l,id,trip.getUUID("id"))||chest==null||chest.countItem(VillageAstra.BANDAGE.get())!=0)throw new IllegalStateException("Medicine duplicated or replayed after restart");if(++samples>=10)done=true;
  }catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_MEDICINE_RELOAD FAILED",ex);mc.stop();}}
}
