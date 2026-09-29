package org.villageastra.server;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import org.villageastra.domain.ElectionRoll;
/** One active-time snapshot; disconnected candidates remain eligible. No wall clock or UUID ranking. */
public final class Elections {
 private Elections(){}
 public static void tick(MinecraftServer server){if(server.getPlayerCount()==0)return;var data=SettlementData.get(server);for(var e:data.entries())advance(data,PropertyLedger.get(server),e,data.clock().ticks());}
 public static void advance(SettlementData data,PropertyLedger ledger,SettlementData.Entry e,long now){
  var s=e.settlement();var g=s.governance();var roll=ledger.roll(s.id());if(roll.decay(now))ledger.setDirty();
  if(g.startSchedule(now))data.setDirty();
  if(now>=g.nextElection()){var winner=roll.winner().orElse(null);if(winner==null)s.appointNpcMayor();else s.appointPlayerMayor(winner.player());g.recordElection(now,winner);data.setDirty();}
  else if(g.playerMayor()==null&&s.appointNpcMayor())data.setDirty();
 }
 public static boolean order(ServerPlayer actor,UUID village,long epoch,long sequence,int action){
  var data=SettlementData.get(actor.server);var e=data.entry(village);if(!ManagementOrders.allowedContext(actor,e))return false;
  var g=e.settlement().governance();if(epoch!=g.epoch())return false;var ledger=PropertyLedger.get(actor.server);var roll=ledger.roll(village);var a=roll.account(actor.getUUID());
  if(sequence!=roll.sequence())return false;
  if(action==0){if(a.candidate()||a.score()<ElectionRoll.MINIMUM)return false;roll.candidate(actor.getUUID(),true);}
  else if(action==1){if(!a.candidate())return false;roll.candidate(actor.getUUID(),false);}
  else if(action==2){if(!g.canManage(actor.getUUID(),epoch))return false;roll.candidate(actor.getUUID(),false);e.settlement().appointNpcMayor();data.setDirty();}
  else return false;ledger.setDirty();return true;
 }
 public static void addView(ServerPlayer actor,CompoundTag tag){
  var data=SettlementData.get(actor.server);var e=tag.hasUUID("village")?data.entry(tag.getUUID("village")):data.entries().stream().filter(v->v.dimension().equals(actor.serverLevel().dimension().location().toString())&&v.center().distSqr(actor.blockPosition())<=4096).min(Comparator.comparingDouble(v->v.center().distSqr(actor.blockPosition()))).orElse(null);if(e==null)return;
  var s=e.settlement();var g=s.governance();var a=PropertyLedger.get(actor.server).roll(s.id()).account(actor.getUUID());var v=new CompoundTag();v.putUUID("village",s.id());v.putLong("epoch",g.epoch());v.putLong("score",a.score());v.putLong("gifts",a.gifts());v.putLong("theft",a.theft());v.putLong("decay",a.decay());v.putLong("minimum",ElectionRoll.MINIMUM);v.putBoolean("candidate",a.candidate());v.putBoolean("safe",ManagementOrders.allowedContext(actor,e));v.putBoolean("mayor",actor.getUUID().equals(g.playerMayor()));v.putLong("remaining",Math.max(0,g.nextElection()-data.clock().ticks()));v.putLong("lastElection",g.lastElection());v.putLong("lastScore",g.lastScore());
  var roll=PropertyLedger.get(actor.server).roll(s.id());v.putLong("sequence",roll.sequence());
  // The office draws the time left against the whole term, and counts who stands.
  v.putLong("period",ElectionRoll.PERIOD);v.putInt("candidates",(int)roll.accounts().stream().filter(ElectionRoll.Account::candidate).count());
  tag.putUUID("village",s.id());tag.putLong("epoch",g.epoch());tag.putLong("revision",g.revision());tag.putBoolean("canManage",v.getBoolean("mayor")&&v.getBoolean("safe"));tag.putString("lockReason",!v.getBoolean("mayor")?"only_mayor":!v.getBoolean("safe")?"unsafe":"manage");
  String holder;if(g.playerMayor()!=null){var cache=actor.server.getProfileCache();holder=cache==null?g.playerMayor().toString():cache.get(g.playerMayor()).map(p->p.getName()).orElse(g.playerMayor().toString());}else holder=s.residents().stream().filter(r->r.profession()==org.villageastra.domain.Profession.MAYOR).map(r->r.profile().name()).findFirst().orElse("—");v.putString("holder",holder);tag.put("election",v);
 }
}
