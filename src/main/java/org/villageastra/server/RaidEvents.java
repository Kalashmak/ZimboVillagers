package org.villageastra.server;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** Unloading is not death; retired identities cannot attack again when their old chunks load. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class RaidEvents {
 private RaidEvents(){}
 @SubscribeEvent public static void join(EntityJoinLevelEvent event){if(event.getLevel() instanceof ServerLevel l&&!RaidRoster.mayJoin(l,event.getEntity()))event.setCanceled(true);}
 @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){var mob=event.getEntity();
  if(event.getLevel() instanceof ServerLevel l&&mob.getRemovalReason()!=null&&mob.getRemovalReason().shouldDestroy())Raids.removed(l,mob);
 }
}
