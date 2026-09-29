package org.villageastra.server;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.domain.*;
import org.villageastra.world.*;
/** The office's Overview tab: the few numbers that tell a mayor at a glance whether the village needs him now
 *  (food against a day of meals, missed meals, beds, builders, raid, siege, coins). Read-only; nothing here changes the world. */
public final class OfficeOverview {
 /** Pantries are walked chest by chest, and every player's snapshot pass would do it again: one reading per village lasts this long. */
 public static final int CACHE_TICKS=40;
 private record Cached(int at,CompoundTag tag){}
 private static final Map<UUID,Cached> CACHE=new HashMap<>();
 private OfficeOverview(){}
 public static void clear(){CACHE.clear();OfficeHistory.clear();}
 /** Adds overview{} to an office snapshot that names its village. */
 public static void addView(ServerPlayer p,CompoundTag tag){
  if(!tag.hasUUID("village"))return;var e=SettlementData.get(p.server).entry(tag.getUUID("village"));if(e==null)return;
  tag.put("overview",cached(p.server,e).copy());
 }
 /** AD-124: the reading of the last 40 ticks (made now when older), shared with the research view's lab stock and rate. Do not modify. */
 public static CompoundTag cached(net.minecraft.server.MinecraftServer server,SettlementData.Entry e){
  int now=server.getTickCount();var id=e.settlement().id();var cached=CACHE.get(id);
  if(cached==null||now-cached.at()>=CACHE_TICKS||now<cached.at()){cached=new Cached(now,read(server,e));CACHE.put(id,cached);}
  return cached.tag();
 }
 /** The village's own level (its dimension), else the viewer's: raids and pantries live there. */
 private static ServerLevel level(net.minecraft.server.MinecraftServer server,SettlementData.Entry e){
  var l=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(e.dimension())));return l==null?server.overworld():l;}
 public static CompoundTag read(net.minecraft.server.MinecraftServer server,SettlementData.Entry e){
  var l=level(server,e);var s=e.settlement();var o=new CompoundTag();long now=SettlementData.get(server).clock().ticks();
  int alive=0,adults=0,missed=0,hungry=0,builders=0;
  for(var r:s.residents()){if(!r.alive())continue;alive++;if(r.life()==Resident.Life.ADULT)adults++;if(r.missedMeals()>0)missed++;if(r.missedMeals()>=Population.HUNGRY)hungry++;if(r.profession()==Profession.BUILDER)builders++;}
  int homes=0;long beds=0,free=0;for(var h:s.homes())if(h.usable()){homes++;beds+=h.capacity();free+=Math.max(0,h.capacity()-s.occupancy(h.id()));}
  o.putInt("residents",alive);o.putInt("adults",adults);o.putInt("homes",homes);o.putInt("beds",(int)beds);o.putInt("bedsFree",(int)free);
  // Rations in the pantries against the rations a day of meals takes (HandBread.reserveRations with one day).
  o.putInt("food",Population.storedNutrition(l,e));o.putInt("need",(int)(alive*(long)Population.MEAL_NUTRITION*24000L/Population.MEAL_INTERVAL));
  o.putInt("missed",missed);o.putInt("hungry",hungry);
  boolean project=HallUpgradeGoal.pending(l,s.id());o.putBoolean("project",project);o.putInt("builders",builders);o.putInt("buildersIdle",project?0:builders);
  var raid=Raids.record(l,s.id());boolean active=raid.contains("active");o.putBoolean("raid",active);
  if(!active&&raid.contains("nextAt"))o.putLong("raidNext",Math.max(0,raid.getLong("nextAt")-now));
  o.putBoolean("besieged",Sieges.besieged(server,s.id()));var siege=Sieges.siegeOf(server,s.id());if(siege!=null)o.putString("siege",siege.getString("state"));
  o.putLong("coins",TradeLedger.get(server).treasury(s.id()));o.putInt("hallLevel",s.civilization().level());
  // AD-124: the science tile's ETA — volumes waiting in the laboratories (-1 while a lab chest is not loaded: unknown, not 'none') and living scientists.
  int labs=0,volumes=0,scientists=0;boolean unknown=false;
  for(var b:s.buildings())if(b.type().equals("laboratory")){labs++;var c=LogisticsRoutes.chest(l,e,b);if(c==null)unknown=true;else volumes+=LogisticsRoutes.count(c,BookResearch::work);}
  for(var r:s.residents())if(r.alive()&&r.profession()==Profession.SCIENTIST)scientists++;
  o.putInt("labs",labs);o.putInt("labVolumes",unknown?-1:volumes);o.putInt("scientists",scientists);
  // AD-124: one sample a day of the simulation clock, and the change since the oldest earlier one.
  // A research record that cannot be read now is no sample (a false 0 would show as a jump of volumes and a false pace later).
  long paid=0;int completed=0;boolean readable=true;
  try{if(java.nio.file.Files.exists(BookResearch.path(l,s.id()))){var t=BookResearch.inspect(l,e);for(var k:t.getCompound("paid").getAllKeys())paid+=t.getCompound("paid").getInt(k);completed=BookResearch.completed(e,t).size();}}catch(RuntimeException ex){readable=false;}
  long day=now/OfficeHistory.DAY;var sample=new OfficeHistory.Sample(day,alive,(int)beds,o.getInt("food"),o.getInt("need"),o.getLong("coins"),paid,completed);
  var trend=readable?OfficeHistory.update(server,s.id(),day,sample):OfficeHistory.trend(OfficeHistory.load(server,s.id()),day,sample);if(!readable)trend.remove("paid");
  o.put("trend",trend);o.putLong("day",day);
  return o;
 }
}
