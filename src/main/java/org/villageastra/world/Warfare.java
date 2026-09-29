package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.domain.*;
import org.villageastra.server.*;
/** AD-049: the mayor's war desk in the settlement office — the same campaigns, sieges and annexations the operator commands drive, but reachable in play. */
public final class Warfare {
 public static final int NEIGHBOURS=4,RANGE=512;
 private Warfare(){}
 private static SettlementData.Entry entry(MinecraftServer s,UUID village){return SettlementData.get(s).entry(village);}
 /** Settlements of the same dimension within reach of a campaign, nearest first. */
 public static List<SettlementData.Entry> neighbours(MinecraftServer s,SettlementData.Entry home){
  return SettlementData.get(s).entries().stream()
   .filter(v->!v.settlement().id().equals(home.settlement().id())&&v.dimension().equals(home.dimension())&&v.center().distSqr(home.center())<=(long)RANGE*RANGE)
   .sorted(Comparator.comparingDouble(v->v.center().distSqr(home.center()))).limit(NEIGHBOURS).toList();
 }
 /** The war section of the office snapshot: own force, the campaign under way and every standing offer. */
 public static void addView(ServerPlayer p,CompoundTag tag){
  if(!tag.hasUUID("village"))return;var s=p.server;var home=entry(s,tag.getUUID("village"));if(home==null)return;
  var war=new CompoundTag();var g=home.settlement().governance();
  war.putBoolean("mayor",g.canManage(p.getUUID(),g.epoch())&&ManagementOrders.allowedContext(p,home));
  var barracks=home.settlement().buildings().stream().filter(b->b.type().equals("barracks")).findFirst().orElse(null);
  war.putBoolean("barracks",barracks!=null);war.putInt("soldiers",Sieges.soldiers(home).size());
  // AD-159 VI: with Military VI a neighbour's campaign button sends the army to take the town on its own (order 5).
  war.putBoolean("capture",ResearchKnobs.done(p.serverLevel(),home).contains(Army.CAPTURE));
  war.putLong("treasury",TradeLedger.get(s).treasury(home.settlement().id()));
  var army=Sieges.armies(s).stream().filter(a->a.getUUID("attacker").equals(home.settlement().id())).findFirst().orElse(null);
  if(army!=null){
   war.putString("state",army.getString("state"));war.putUUID("campaign",army.getUUID("target"));
   var target=entry(s,army.getUUID("target"));
   if(target!=null)war.putLong("campaignCenter",target.center().asLong());
   war.putInt("fences",Sieges.supply(army,"fences"));war.putInt("campfires",Sieges.supply(army,"campfires"));
   war.putInt("charges",Sieges.supply(army,"tnt"));war.putInt("rations",Sieges.supply(army,"bread"));
   war.putString("ready",Sieges.ready(p.serverLevel(),army));
  }else war.putString("state","none");
  var list=new ListTag();
  for(var v:neighbours(s,home)){
   var row=new CompoundTag();row.putUUID("village",v.settlement().id());row.putLong("center",v.center().asLong());
   row.putString("name",v.settlement().name());row.putInt("distance",(int)Math.sqrt(v.center().distSqr(home.center())));
   row.putInt("residents",(int)v.settlement().residents().stream().filter(Resident::alive).count());
   row.putInt("buildings",v.settlement().buildings().size());
   row.putLong("price",Annexation.price(v));
   row.putLong("supplied",Annexation.supplied(s,home.settlement().id(),v.settlement().id(),SettlementData.get(s).clock().ticks()));
   row.putBoolean("besieged",Sieges.besieged(s,v.settlement().id()));
   // AD-100: how the two villages stand, and the latest reason it moved.
   int relation=Relations.score(s,home.settlement().id(),v.settlement().id());row.putInt("relation",relation);row.putString("standing",Relations.standing(relation));
   var why=Relations.reasons(s,home.settlement().id(),v.settlement().id());if(!why.isEmpty())row.putString("why",why.getCompound(0).getString("reason"));
   var offer=Annexation.record(s,v.settlement().id());
   if(offer!=null){row.putString("offer",offer.getString("state"));row.putBoolean("mine",offer.hasUUID("buyer")&&offer.getUUID("buyer").equals(home.settlement().id()));}
   list.add(row);
  }
  war.put("neighbours",list);
  var incoming=Annexation.record(s,home.settlement().id());
  if(incoming!=null&&incoming.getString("state").equals("offered")){
   war.putBoolean("offered",true);war.putLong("offerPrice",incoming.getLong("price"));
   if(incoming.hasUUID("buyer")){var buyer=entry(s,incoming.getUUID("buyer"));if(buyer!=null)war.putLong("buyerCenter",buyer.center().asLong());}
  }
  tag.put("war",war);
 }
 /** One war order from the office: 0 muster a campaign, 1 begin the siege, 2 offer an annexation, 3 accept, 4 decline, 5 (AD-159,
  *  Military VI) send the army to take the town on its own: mustered as 0, it lays the siege itself when ready, or turns back when the
  *  defenders are stronger. */
 public static String order(ServerPlayer p,UUID village,long epoch,int action,UUID target){
  var s=p.server;var home=entry(s,village);if(home==null)return "village";
  var g=home.settlement().governance();
  if(!g.canManage(p.getUUID(),epoch)||!ManagementOrders.allowedContext(p,home))return "mayor";
  long now=SettlementData.get(s).clock().ticks();
  switch(action){
   case 0->{
    var other=entry(s,target);if(other==null||other.settlement().id().equals(village))return "target";
    var army=Sieges.muster(p.serverLevel(),home,other,now);if(army==null)return "no_army";
    // AD-100: an army mustered against a village is seen there.
    Relations.change(p.server,home.settlement().id(),other.settlement().id(),-10,"campaign");return "";
   }
   case 5->{
    if(!ResearchKnobs.done(p.serverLevel(),home).contains(Army.CAPTURE))return "research";
    var other=entry(s,target);if(other==null||other.settlement().id().equals(village))return "target";
    var army=Sieges.muster(p.serverLevel(),home,other,now);if(army==null)return "no_army";
    army.putBoolean("auto",true);Sieges.save(s,army);
    Relations.change(p.server,home.settlement().id(),other.settlement().id(),-10,"campaign");return "";
   }
   case 1->{
    var army=Sieges.armies(s).stream().filter(a->a.getUUID("attacker").equals(village)).findFirst().orElse(null);
    if(army==null)return "no_army";
    String reason=Sieges.ready(p.serverLevel(),army);if(!reason.isEmpty())return reason;
    return Sieges.begin(p.serverLevel(),army,now)?"":"not_ready";
   }
   case 2->{
    var other=entry(s,target);if(other==null||other.settlement().id().equals(village))return "target";
    return Annexation.offer(s,home,other,now);
   }
   case 3,4->{
    var record=Annexation.record(s,village);if(record==null)return "none";
    String reason=Annexation.answer(s,home,p,action==3,now);
    if(!reason.isEmpty())return reason;
    if(action==3){String transfer=Annexation.transfer(s,home,now);if(!transfer.isEmpty())return transfer;}
    return "";
   }
   default->{return "action";}
  }
 }
}
