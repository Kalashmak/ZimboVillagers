package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
/** AD-036: completed trade tokens, owed coins, buy-back counters and village treasuries. Saved with players and chests at autosave, so a crash rolls them back together. */
public final class TradeLedger extends SavedData {
 public static final int KEEP=4096;
 private final LinkedHashMap<UUID,CompoundTag> deals=new LinkedHashMap<>();
 private final Map<String,Integer> bought=new HashMap<>();
 private final Map<UUID,Long> owed=new HashMap<>(),treasury=new HashMap<>();
 public static TradeLedger get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(TradeLedger::load,TradeLedger::new,"villageastra_trade");}
 public CompoundTag deal(UUID token){var d=deals.get(token);return d==null?null:d.copy();}
 public void record(UUID token,CompoundTag deal){if(deals.putIfAbsent(token,deal.copy())!=null)throw new IllegalStateException("Duplicate trade token");while(deals.size()>KEEP)deals.remove(deals.keySet().iterator().next());setDirty();}
 private static String key(UUID village,UUID player,String item){return village+"/"+player+"/"+item;}
 public int bought(UUID village,UUID player,String item){return bought.getOrDefault(key(village,player,item),0);}
 public void addBought(UUID village,UUID player,String item,int count){if(count<=0)throw new IllegalArgumentException("Nonpositive purchase");bought.merge(key(village,player,item),count,Math::addExact);setDirty();}
 /** Returned items first cancel earlier purchases; only the rest can earn reputation. */
 public int returned(UUID village,UUID player,String item,int count){int b=bought(village,player,item),r=Math.min(b,count);if(r>0){if(b==r)bought.remove(key(village,player,item));else bought.put(key(village,player,item),b-r);setDirty();}return r;}
 public long owed(UUID player){return owed.getOrDefault(player,0L);}
 public void owe(UUID player,long delta){long next=Math.addExact(owed(player),delta);if(next<0)throw new IllegalStateException("Negative owed coins");if(next==0)owed.remove(player);else owed.put(player,next);setDirty();}
 public long treasury(UUID village){return treasury.getOrDefault(village,0L);}
 public void addTreasury(UUID village,long coins){treasury.merge(village,coins,Math::addExact);setDirty();}
 public static TradeLedger load(CompoundTag tag){
  if(tag.getInt("schema")!=1)throw new IllegalArgumentException("Invalid trade ledger");var data=new TradeLedger();
  for(var raw:tag.getList("deals",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(data.deals.putIfAbsent(t.getUUID("token"),t)!=null)throw new IllegalArgumentException("Duplicate trade token");}
  for(var raw:tag.getList("bought",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.getInt("count")<=0||data.bought.putIfAbsent(t.getString("key"),t.getInt("count"))!=null)throw new IllegalArgumentException("Invalid purchase counter");}
  for(var raw:tag.getList("owed",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.getLong("coins")<=0||data.owed.putIfAbsent(t.getUUID("player"),t.getLong("coins"))!=null)throw new IllegalArgumentException("Invalid owed coins");}
  for(var raw:tag.getList("treasury",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.getLong("coins")<0||data.treasury.putIfAbsent(t.getUUID("village"),t.getLong("coins"))!=null)throw new IllegalArgumentException("Invalid treasury");}
  return data;
 }
 @Override public CompoundTag save(CompoundTag tag){
  tag.putInt("schema",1);var d=new ListTag();deals.values().forEach(x->d.add(x.copy()));tag.put("deals",d);
  var b=new ListTag();bought.forEach((k,v)->{var t=new CompoundTag();t.putString("key",k);t.putInt("count",v);b.add(t);});tag.put("bought",b);
  var o=new ListTag();owed.forEach((k,v)->{var t=new CompoundTag();t.putUUID("player",k);t.putLong("coins",v);o.add(t);});tag.put("owed",o);
  var r=new ListTag();treasury.forEach((k,v)->{var t=new CompoundTag();t.putUUID("village",k);t.putLong("coins",v);r.add(t);});tag.put("treasury",r);return tag;
 }
}
