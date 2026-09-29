package org.villageastra.server;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class HorseEvents {
 private HorseEvents(){}
 @SubscribeEvent public static void join(EntityJoinLevelEvent event){if(event.getLevel() instanceof ServerLevel&&event.getEntity() instanceof Horse h){if(CaravanHorses.stale(h)){event.setCanceled(true);return;}VillageHorses.attach(h);}}
 @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getLevel() instanceof ServerLevel&&event.getEntity() instanceof Horse h&&h.getRemovalReason()!=null&&!h.getRemovalReason().shouldDestroy())CaravanHorses.unload(h);}
 @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity() instanceof Horse h&&h.level() instanceof ServerLevel l)CaravanHorses.died(h,SettlementData.get(l.getServer()).clock().ticks());}
 @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event){
  if(!(event.getLevel() instanceof ServerLevel l)||!(event.getTarget() instanceof Horse h))return;
  boolean busy=CaravanHorses.reserved(l.getServer(),h.getUUID());
  if(!busy&&(!event.getEntity().isShiftKeyDown()||!event.getItemStack().is(Items.LEAD)))return;
  String result=busy?"busy":VillageHorses.interact(l,event.getEntity(),h);
  event.getEntity().displayClientMessage(Component.translatable(switch(result){case "enlisted"->"message.villageastra.trade_horse.enlisted";case "released"->"message.villageastra.trade_horse.released";case "busy"->"message.villageastra.trade_horse.busy";case "owner"->"message.villageastra.trade_horse.owner";default->"message.villageastra.trade_horse.yard";}),true);
  event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
 }
}
