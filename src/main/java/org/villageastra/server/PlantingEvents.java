package org.villageastra.server;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
/** AD-131: a sapling a real player sets is theirs — noted in ForestPlantings, so no forester ever fells the tree it grows into. Runs last, and
 *  only on a placement nothing refused (OwnershipEvents may cancel one inside a lot). */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class PlantingEvents {
 private PlantingEvents(){}
 /** AD-131 (check fix 3): a sapling of a courtyard grove that grows by itself (a random tick, a player's bone meal) grows into the grove's
  *  compact tree of its kind, never a fancy oak or a bee nest. */
 @SubscribeEvent public static void grow(net.minecraftforge.event.level.SaplingGrowTreeEvent event){
  if(!(event.getLevel() instanceof ServerLevel level)||!org.villageastra.world.ForestryMachines.groveCell(level,event.getPos()))return;
  var sapling=level.getBlockState(event.getPos());if(sapling.is(BlockTags.SAPLINGS))event.setFeature(org.villageastra.world.ForestryMachines.holder(level,sapling.getBlock()));
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void place(BlockEvent.EntityPlaceEvent event){
  if(!(event.getEntity() instanceof ServerPlayer player)||player instanceof FakePlayer||!(event.getLevel() instanceof ServerLevel level))return;
  var placed=event.getPlacedBlock();if(!placed.is(BlockTags.SAPLINGS)&&!placed.is(Blocks.MANGROVE_PROPAGULE))return;
  ForestPlantings.get(level.getServer()).recordPlayer(level,event.getPos());
 }
}
