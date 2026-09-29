package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.world.HallUpgradeGoal;
/** Unapproved surveys are ephemeral and private; accepted work uses the existing durable queue. */
public final class ConstructionDrafts {
 private record Draft(UUID village,long epoch,long revision,long expires,CompoundTag state){}
 private static final Map<UUID,Draft> DRAFTS=new HashMap<>();
 public static void clear(){DRAFTS.clear();}
 public static void prune(net.minecraft.server.MinecraftServer server){DRAFTS.keySet().retainAll(server.getPlayerList().getPlayers().stream().map(p->p.getUUID()).toList());}
 private static boolean valid(ServerPlayer p,Draft d){var data=SettlementData.get(p.server);var e=data.entry(d.village);return ManagementOrders.allowedContext(p,e)&&data.clock().ticks()<d.expires&&e.settlement().governance().canManage(p.getUUID(),d.epoch)&&e.settlement().governance().revision()==d.revision&&!HallUpgradeGoal.pending(p.serverLevel(),d.village);}
 public static boolean order(ServerPlayer p,UUID village,long epoch,long revision,int action,UUID proposal){
  var data=SettlementData.get(p.server);var e=data.entry(village);if(!ManagementOrders.allowedContext(p,e)||!e.settlement().governance().canManage(p.getUUID(),epoch)||e.settlement().governance().revision()!=revision)return false;
  if(action==0){
   try{var state=HallUpgradeGoal.preview(p.serverLevel(),e);DRAFTS.put(p.getUUID(),new Draft(village,epoch,revision,Math.addExact(data.clock().ticks(),1200),state));return true;}
   catch(IllegalStateException ex){DRAFTS.remove(p.getUUID());p.displayClientMessage(net.minecraft.network.chat.Component.translatable("construction.villageastra.survey_failed"),false);return false;}
  }
  var d=DRAFTS.get(p.getUUID());if(d==null||!d.village.equals(village)||!d.state.getUUID("id").equals(proposal)||!valid(p,d))return false;
  if(action==2){DRAFTS.remove(p.getUUID());return true;}
  if(action!=1||p.blockPosition().distSqr(e.center())>16*16)return false;
  var view=ConstructionViews.project(p.serverLevel(),e,p.blockPosition(),d.state);if(view.getInt("hidden")>0||view.getInt("conflicts")>0)return false;
  try{if(!HallUpgradeGoal.approve(p.serverLevel(),e,d.state))return false;DRAFTS.remove(p.getUUID());data.setDirty();return true;}catch(IllegalStateException ex){return false;}
 }
 public static CompoundTag view(ServerPlayer p,CompoundTag ordinary){
  var d=DRAFTS.get(p.getUUID());if(d==null)return ordinary;if(!valid(p,d)){DRAFTS.remove(p.getUUID());return ordinary;}
  var e=SettlementData.get(p.server).entry(d.village);var result=ConstructionViews.project(p.serverLevel(),e,p.blockPosition(),d.state);result.putBoolean("draft",true);result.putBoolean("confirmable",p.blockPosition().distSqr(e.center())<=16*16&&result.getInt("hidden")==0&&result.getInt("conflicts")==0);result.putString("status","awaiting_confirmation");return result;
 }
}
