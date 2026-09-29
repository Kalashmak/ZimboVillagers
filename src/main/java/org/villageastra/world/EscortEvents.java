package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
/** QUEST-003 (AD-106): a player going through a portal leaves a note on the companions following them — which portal, into which world, until
 *  when — and those walk into that very portal after them. A companion never goes through a portal on its own: without such a note its
 *  crossing is refused, and the refusal does not leave it on a long cooldown. A command teleport, a respawn or an End portal is no way to
 *  follow: the companion waits, saying its player is in another world. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class EscortEvents {
 private EscortEvents(){}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void travel(EntityTravelToDimensionEvent event){
  var entity=event.getEntity();if(!(entity.level() instanceof ServerLevel l))return;
  if(entity instanceof ResidentEntity npc){
   if(npc.escortPlayer()!=null&&!npc.crossingTo(event.getDimension(),l.getGameTime())){event.setCanceled(true);npc.setPortalCooldown(0);}
   return;}
  if(!(entity instanceof ServerPlayer p))return;
  // Only a nether portal: an End portal lies over its own void, and a companion there waits for its player with an honest reason instead.
  var found=BlockPos.betweenClosedStream(p.getBoundingBox().deflate(1.0E-7)).filter(b->l.getBlockState(b).is(Blocks.NETHER_PORTAL)).map(BlockPos::immutable).findFirst().orElse(null);
  if(found==null)return;
  // The bottom block of that portal column: the one a companion can stand in.
  var cell=found;while(l.getBlockState(cell.below()).is(Blocks.NETHER_PORTAL))cell=cell.below();
  final var bottom=cell;
  for(var npc:l.getEntitiesOfClass(ResidentEntity.class,p.getBoundingBox().inflate(48),x->x.isAlive()&&p.getUUID().equals(x.escortPlayer())&&!x.ordered()&&!"arrived".equals(x.escortState()))){
   npc.recordPortal(bottom,l.dimension(),event.getDimension(),l.getGameTime()+EscortGoal.CROSS_TIME);npc.setEscort("dimension","");EscortGoal.hold(npc);}
 }
}
