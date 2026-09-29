package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.villageastra.domain.Settlement;
import org.villageastra.world.*;
/** AD-131: what the mayor chose for a forester's hut — the kind of tree preferred (first on a tie of the mix at III, the grove's kind at VI) and
 *  whether the sawmill runs (IV+, on by default). SavedData villageastra_forest, schema 1; the old nursery sizes (villageastra_nurseries) are
 *  not read. */
public final class ForestPolicies extends SavedData {
 public static final String NAME="villageastra_forest";
 private record Key(UUID village,UUID building){}
 private final Map<Key,String> preferred=new LinkedHashMap<>();
 private final Set<Key> sawOff=new LinkedHashSet<>();
 public static ForestPolicies get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(ForestPolicies::load,ForestPolicies::new,NAME);}
 /** The preferred kind of sapling of a hut (the oak until the mayor picks another opened kind). */
 public String preferred(UUID village,UUID building){return preferred.getOrDefault(new Key(village,building),"minecraft:oak_sapling");}
 public String preferred(SettlementData.Entry e,Settlement.Building b){return preferred(e.settlement().id(),b.id());}
 public boolean sawOn(UUID village,UUID building){return !sawOff.contains(new Key(village,building));}
 public boolean sawOn(SettlementData.Entry e,Settlement.Building b){return sawOn(e.settlement().id(),b.id());}
 /** For tests and probes: sets the saw directly (the mayor's way is order). */
 public void saw(UUID village,UUID building,boolean on){if(on?sawOff.remove(new Key(village,building)):sawOff.add(new Key(village,building)))setDirty();}
 private static Settlement.Building hut(SettlementData.Entry e,UUID building){return e.settlement().buildings().stream().filter(x->x.id().equals(building)&&x.type().equals(ForesterHut.TYPE)).findFirst().orElse(null);}
 private static String allowed(ServerPlayer p,SettlementData.Entry e,long epoch,long revision){
  if(e==null)return "village";
  if(!ManagementOrders.allowedContext(p,e)||!e.settlement().governance().canManage(p.getUUID(),epoch)||e.settlement().governance().revision()!=revision)return "mayor";
  return "";
 }
 /** The mayor picks the next opened kind of sapling a hut prefers (mangrove never: a forester does not plant it). */
 public static String orderSpecies(ServerPlayer p,UUID village,UUID building,long epoch,long revision){
  var data=SettlementData.get(p.server);var e=data.entry(village);var refused=allowed(p,e,epoch,revision);if(!refused.isEmpty())return refused;
  if(hut(e,building)==null)return "building";
  var policies=get(p.server);var now=policies.preferred(village,building);var all=ForestWork.PLANTED;
  String next=now;
  for(int i=1;i<=all.size();i++){var c=all.get((Math.max(0,all.indexOf(now))+i)%all.size());if(CropUnlocks.unlocked(p.serverLevel(),e,c)){next=c;break;}}
  if(next.equals(now))return "locked";
  if(!e.settlement().governance().recordOrder(p.getUUID(),epoch,revision))return "mayor";
  policies.preferred.put(new Key(village,building),next);policies.setDirty();data.setDirty();return "";
 }
 /** The mayor starts or stops a hut's sawmill. */
 public static String orderSaw(ServerPlayer p,UUID village,UUID building,long epoch,long revision){
  var data=SettlementData.get(p.server);var e=data.entry(village);var refused=allowed(p,e,epoch,revision);if(!refused.isEmpty())return refused;
  var b=hut(e,building);if(b==null)return "building";
  if(BuildingLevels.level(p.serverLevel(),e,b)<ForestBalance.SAW_FROM)return "saw";
  if(!e.settlement().governance().recordOrder(p.getUUID(),epoch,revision))return "mayor";
  var policies=get(p.server);var key=new Key(village,building);if(!policies.sawOff.remove(key))policies.sawOff.add(key);policies.setDirty();data.setDirty();return "";
 }
 public static ForestPolicies load(CompoundTag tag){
  if(tag.getInt("schema")!=1)throw new IllegalArgumentException("Invalid forest policies");
  var d=new ForestPolicies();
  for(var raw:tag.getList("huts",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var k=new Key(t.getUUID("village"),t.getUUID("building"));
   if(t.contains("preferred",Tag.TAG_STRING))d.preferred.put(k,t.getString("preferred"));if(!t.getBoolean("sawOn"))d.sawOff.add(k);}
  return d;
 }
 @Override public CompoundTag save(CompoundTag tag){
  tag.putInt("schema",1);var keys=new LinkedHashSet<Key>(preferred.keySet());keys.addAll(sawOff);var rows=new ListTag();
  for(var k:keys){var t=new CompoundTag();t.putUUID("village",k.village());t.putUUID("building",k.building());if(preferred.containsKey(k))t.putString("preferred",preferred.get(k));t.putBoolean("sawOn",!sawOff.contains(k));rows.add(t);}
  tag.put("huts",rows);return tag;
 }
}
