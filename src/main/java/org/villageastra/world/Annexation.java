package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
/** AD-044: peaceful annexation earned by real deliveries and paid from existing coins, and the single transfer of ownership shared by both paths. */
public final class Annexation {
 public static final String OFFERED="offered",ACCEPTED="accepted",PAID="paid",TRANSFERRED="transferred",CANCELLED="cancelled";
 private static final JsonObject ROOT=root();
 public static final int SUPPLY_WINDOW=ROOT.get("supply_window").getAsInt(),SUPPLY_VALUE=ROOT.get("supply_value").getAsInt(),
  PRICE_RESIDENT=ROOT.get("price_per_resident").getAsInt(),PRICE_BUILDING=ROOT.get("price_per_building").getAsInt(),
  GOODS_SHARE=ROOT.get("goods_share_percent").getAsInt(),CONFIRM_TICKS=ROOT.get("confirm_ticks").getAsInt();
 private Annexation(){}
 private static JsonObject root(){try(var s=Annexation.class.getResourceAsStream("/data/villageastra/balance/annexation.json")){if(s==null)throw new IllegalStateException("Missing annexation balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static Path path(MinecraftServer s,UUID target){return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-annex/"+target+".bin");}
 public static CompoundTag record(MinecraftServer s,UUID target){var p=path(s,target);return Files.exists(p)?NbtRecord.read(p):null;}
 private static void save(MinecraftServer s,UUID target,CompoundTag t){NbtRecord.write(path(s,target),t);}
 public static UUID owner(MinecraftServer s,UUID village){var t=record(s,village);return t!=null&&t.getString("state").equals(TRANSFERRED)&&t.hasUUID("owner")?t.getUUID("owner"):null;}
 /** Value of goods this settlement really delivered to that one inside the window; only accepted caravan deliveries count. */
 public static long supplied(MinecraftServer s,UUID from,UUID to,long now){
  long value=0;
  for(var contract:Caravans.contracts(s)){
   if(!contract.getUUID("source").equals(from)||!contract.getUUID("destination").equals(to))continue;
   if(contract.getInt("delivered")<=0)continue;
   long tick=contract.getCompound("stamps").getLong(Caravans.DELIVERED);if(tick==0)tick=contract.getCompound("stamps").getLong(Caravans.PARTIAL);
   if(tick==0||now-tick>SUPPLY_WINDOW)continue;
   value+=contract.getLong("paid");}
  return value;
 }
 /** Price of a settlement: its living people and standing buildings, never a round number pulled from the air. */
 public static long price(SettlementData.Entry target){
  long residents=target.settlement().residents().stream().filter(Resident::alive).count();
  return residents*PRICE_RESIDENT+(long)target.settlement().buildings().size()*PRICE_BUILDING;
 }
 /** An offer needs real sustained supplies, a peaceful relation and coins reserved from the buyer's existing treasury. */
 public static String offer(MinecraftServer s,SettlementData.Entry buyer,SettlementData.Entry target,long now){
  if(buyer.settlement().id().equals(target.settlement().id()))return "same";
  if(owner(s,target.settlement().id())!=null)return "already_owned";
  var existing=record(s,target.settlement().id());
  if(existing!=null&&(existing.getString("state").equals(OFFERED)||existing.getString("state").equals(ACCEPTED)||existing.getString("state").equals(PAID)))return "pending";
  if(Sieges.siegeOf(s,target.settlement().id())!=null)return "war";
  long supplied=supplied(s,buyer.settlement().id(),target.settlement().id(),now);
  if(supplied<SUPPLY_VALUE)return "supplies";
  long price=price(target);var ledger=TradeLedger.get(s);
  if(ledger.treasury(buyer.settlement().id())<price)return "coins";
  ledger.addTreasury(buyer.settlement().id(),-price);
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("target",target.settlement().id());t.putUUID("buyer",buyer.settlement().id());t.putLong("price",price);t.putLong("supplied",supplied);
  t.putLong("offered",now);t.putLong("deadline",now+CONFIRM_TICKS);t.putLong("goods",price*GOODS_SHARE/100);t.putString("state",OFFERED);save(s,target.settlement().id(),t);return "";
 }
 /** The target's mayor answers: a player confirms in person, an NPC weighs the published terms. Being offline is never consent. */
 public static String answer(MinecraftServer s,SettlementData.Entry target,ServerPlayer actor,boolean accept,long now){
  var t=record(s,target.settlement().id());if(t==null||!t.getString("state").equals(OFFERED))return "none";
  var mayor=target.settlement().governance().playerMayor();
  if(mayor!=null){if(actor==null||!actor.getUUID().equals(mayor))return "not_mayor";}
  else if(actor!=null)return "not_mayor";
  if(!accept){refund(s,t);t.putString("state",CANCELLED);t.putLong("cancelled",now);save(s,target.settlement().id(),t);return "cancelled";}
  t.putString("state",ACCEPTED);t.putLong("accepted",now);save(s,target.settlement().id(),t);return "";
 }
 /** An NPC mayor accepts only when the price covers its settlement and the supplies really came. */
 public static boolean npcAccepts(MinecraftServer s,SettlementData.Entry target,long now){
  var t=record(s,target.settlement().id());if(t==null||!t.getString("state").equals(OFFERED)||target.settlement().governance().playerMayor()!=null)return false;
  return t.getLong("price")>=price(target)&&t.getLong("supplied")>=SUPPLY_VALUE&&now<=t.getLong("deadline");
 }
 private static void refund(MinecraftServer s,CompoundTag t){
  if(t.getBoolean("refunded")||t.getString("state").equals(TRANSFERRED))return;
  TradeLedger.get(s).addTreasury(t.getUUID("buyer"),t.getLong("price"));t.putBoolean("refunded",true);
 }
 /** Expiry returns the reserved coins exactly once. */
 public static void tick(MinecraftServer s,SettlementData.Entry target,long now){
  var t=record(s,target.settlement().id());if(t==null)return;
  if(t.getString("state").equals(OFFERED)&&now>t.getLong("deadline")){refund(s,t);t.putString("state",CANCELLED);t.putLong("cancelled",now);save(s,target.settlement().id(),t);return;}
  if(t.getString("state").equals(OFFERED)&&npcAccepts(s,target,now)){t.putString("state",ACCEPTED);t.putLong("accepted",now);save(s,target.settlement().id(),t);}
 }
 /** The military path ends in the same single transfer: no price, and a pending purchase is refunded once. */
 public static String conquer(MinecraftServer s,SettlementData.Entry target,UUID victor,long now){
  var t=record(s,target.settlement().id());
  if(t!=null&&t.getString("state").equals(TRANSFERRED))return "done";
  if(t!=null)refund(s,t);
  var fresh=new CompoundTag();fresh.putInt("schema",1);fresh.putUUID("target",target.settlement().id());fresh.putUUID("buyer",victor);fresh.putLong("price",0);fresh.putString("path","siege");
  fresh.putLong("offered",now);fresh.putLong("accepted",now);fresh.putString("state",ACCEPTED);fresh.putBoolean("refunded",true);save(s,target.settlement().id(),fresh);
  return transfer(s,target,now);
 }
 /** MULTI-006: one transfer of people, buildings, stock and management; the old rating is archived, never mixed in. */
 public static String transfer(MinecraftServer s,SettlementData.Entry target,long now){
  var t=record(s,target.settlement().id());if(t==null)return "none";
  if(t.getString("state").equals(TRANSFERRED))return "done";
  if(!t.getString("state").equals(ACCEPTED)&&!t.getString("state").equals(PAID))return "not_accepted";
  var data=SettlementData.get(s);var buyer=data.entry(t.getUUID("buyer"));if(buyer==null)return "no_buyer";
  // The reserved coins are spent now: they never land in a treasury the buyer could spend again.
  t.putString("state",PAID);t.putLong("paid",now);save(s,target.settlement().id(),t);
  var settlement=target.settlement();
  settlement.residents().stream().filter(r->r.alive()&&r.profession()==Profession.MAYOR).toList().forEach(r->r.assign(null));
  var property=PropertyLedger.get(s);t.put("archivedRoll",property.reset(settlement.id()));
  var newMayor=buyer.settlement().governance().playerMayor();
  if(newMayor!=null)settlement.appointPlayerMayor(newMayor);else settlement.appointNpcMayor();
  data.setDirty();
  t.putUUID("owner",buyer.settlement().id());t.putString("state",TRANSFERRED);t.putLong("transferred",now);save(s,target.settlement().id(),t);
  return "";
 }
}
