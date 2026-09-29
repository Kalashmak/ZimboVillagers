package org.villageastra.world;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.server.*;
/** Credit only real survival menu deposits that fill a bounded, useful reserve. */
final class DonationAccounting {
 private static final Set<Item> SUPPLIES=Set.of(Items.OAK_LOG,Items.COBBLESTONE,Items.WHEAT,Items.WHEAT_SEEDS,Items.IRON_INGOT);
 static void record(ServerPlayer player,OwnedChestEntity chest,List<ItemStack> before,List<ItemStack> fresh){
  if(player.isCreative()||player.isSpectator()||!player.isAlive())return;
  var id=chest.getPersistentData().getUUID("AstraSettlement");var e=SettlementData.get(player.server).entry(id);if(!ManagementOrders.allowedContext(player,e))return;
  // Re-check that this is a registered building's own container, not a copied owner tag.
  if(e.settlement().buildings().stream().noneMatch(b->LogisticsRoutes.position(e,b).equals(chest.getBlockPos())))return;
  int count=0;for(var item:SUPPLIES){int old=before.stream().filter(s->s.is(item)&&!s.hasTag()).mapToInt(ItemStack::getCount).sum(),now=0;for(int i=0;i<chest.getContainerSize();i++){var s=chest.getItem(i);if(s.is(item)&&!s.hasTag())now+=s.getCount();}int first=fresh.stream().filter(s->s.is(item)&&!s.hasTag()).mapToInt(ItemStack::getCount).sum();count+=Math.min(first,Math.max(0,Math.min(64,now)-Math.min(64,old)));}
  if(count>0){var ledger=PropertyLedger.get(player.server);ledger.roll(id).decay(SettlementData.get(player.server).clock().ticks());ledger.gift(id,player.getUUID(),count);player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.villageastra.donation",count),false);}
 }
}
