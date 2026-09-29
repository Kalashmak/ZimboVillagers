package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
/** AD-128: completed gift tokens, the per-kind memory behind the repeat factor, each player's gift total per village, and the canonical armour
 *  residents wear (their bodies only mirror it) with pieces still waiting for room in the hall. Saved with players and chests at autosave,
 *  so a crash rolls the gift back as a whole. */
public final class GiftLedger extends SavedData {
 public static final int KEEP=4096;
 public record Memory(long count,long since){}
 public record Worn(UUID village,Map<EquipmentSlot,ItemStack> slots){}
 public record Pending(UUID village,ItemStack stack){}
 private final LinkedHashMap<UUID,CompoundTag> deals=new LinkedHashMap<>();
 private final Map<String,Memory> memory=new HashMap<>();
 private final Map<String,Long> totals=new HashMap<>();
 private final Map<UUID,Worn> worn=new LinkedHashMap<>();
 private final List<Pending> pending=new ArrayList<>();
 public static GiftLedger get(MinecraftServer server){
  return server.overworld().getDataStorage().computeIfAbsent(GiftLedger::load,()->{
   var file=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/villageastra_gifts.dat");
   if(java.nio.file.Files.exists(file)||java.nio.file.Files.exists(file.resolveSibling("villageastra_gifts.dat_old")))throw new IllegalStateException("Refusing to replace corrupt gift ledger");
   return new GiftLedger();},"villageastra_gifts");
 }
 private static String key(UUID village,UUID player){return village+"/"+player;}
 private static String key(UUID village,UUID player,String item){return village+"/"+player+"/"+item;}
 public CompoundTag deal(UUID token){var d=deals.get(token);return d==null?null:d.copy();}
 public int deals(){return deals.size();}
 public void record(UUID token,CompoundTag deal){
  if(deals.putIfAbsent(token,deal.copy())!=null)throw new IllegalStateException("Duplicate gift token");while(deals.size()>KEEP)deals.remove(deals.keySet().iterator().next());
  long rep=deal.getLong("reputation");if(rep>0)totals.merge(key(deal.getUUID("village"),deal.getUUID("player")),rep,Math::addExact);setDirty();
 }
 public long total(UUID village,UUID player){return totals.getOrDefault(key(village,player),0L);}
 /** Units of this kind still remembered at active time now (read-only). */
 public long remembered(UUID village,UUID player,String item,long now){var m=memory.get(key(village,player,item));if(m==null)return 0;return org.villageastra.domain.GiftValue.recover(Gifts.rules(),m.count,m.since,now,org.villageastra.domain.ElectionRoll.PERIOD)[0];}
 public void remember(UUID village,UUID player,String item,long now,int units){
  if(units<=0)return;var k=key(village,player,item);var m=memory.get(k);long count=0,since=now;
  if(m!=null){var r=org.villageastra.domain.GiftValue.recover(Gifts.rules(),m.count,m.since,now,org.villageastra.domain.ElectionRoll.PERIOD);if(r[0]>0){count=r[0];since=r[1];}}
  memory.put(k,new Memory(Math.addExact(count,units),since));setDirty();
 }
 /** CF-11: kinds fully forgotten leave the ledger. */
 public void prune(long now){if(memory.entrySet().removeIf(e->org.villageastra.domain.GiftValue.recover(Gifts.rules(),e.getValue().count,e.getValue().since,now,org.villageastra.domain.ElectionRoll.PERIOD)[0]<=0))setDirty();}
 public Worn worn(UUID resident){return worn.get(resident);}
 public Collection<UUID> wearers(){return List.copyOf(worn.keySet());}
 public ItemStack wear(UUID resident,UUID village,EquipmentSlot slot,ItemStack stack){
  var w=worn.computeIfAbsent(resident,id->new Worn(village,new EnumMap<>(EquipmentSlot.class)));var old=w.slots.put(slot,stack.copy());setDirty();return old==null?ItemStack.EMPTY:old;
 }
 /** Everything the resident wore leaves them; the caller puts it into the hall or keeps it pending. */
 public Worn strip(UUID resident){var w=worn.remove(resident);if(w!=null)setDirty();return w;}
 public List<Pending> pending(){return List.copyOf(pending);}
 public void addPending(UUID village,ItemStack stack){if(!stack.isEmpty()){pending.add(new Pending(village,stack.copy()));setDirty();}}
 public void removePending(Pending p){if(pending.remove(p))setDirty();}
 private static ItemStack stack(CompoundTag t){var s=ItemStack.of(t);if(s.isEmpty())throw new IllegalArgumentException("Invalid gift item");return s;}
 public static GiftLedger load(CompoundTag tag){
  if(!tag.contains("schema",Tag.TAG_INT)||tag.getInt("schema")!=1)throw new IllegalArgumentException("Invalid gift ledger");var data=new GiftLedger();
  for(var raw:tag.getList("deals",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(!t.hasUUID("token")||t.getLong("reputation")<0||data.deals.putIfAbsent(t.getUUID("token"),t)!=null)throw new IllegalArgumentException("Invalid gift deal");}
  for(var raw:tag.getList("memory",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;long c=t.getLong("count");if(c<=0||t.getLong("since")<0||data.memory.putIfAbsent(t.getString("key"),new Memory(c,t.getLong("since")))!=null)throw new IllegalArgumentException("Invalid gift memory");}
  for(var raw:tag.getList("totals",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.getLong("reputation")<0||data.totals.putIfAbsent(t.getString("key"),t.getLong("reputation"))!=null)throw new IllegalArgumentException("Invalid gift total");}
  for(var raw:tag.getList("worn",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var slots=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);var s=t.getCompound("slots");
   for(var name:s.getAllKeys()){var slot=EquipmentSlot.byName(name);if(slot.getType()!=EquipmentSlot.Type.ARMOR)throw new IllegalArgumentException("Invalid worn slot");slots.put(slot,stack(s.getCompound(name)));}
   if(slots.isEmpty()||data.worn.putIfAbsent(t.getUUID("resident"),new Worn(t.getUUID("village"),slots))!=null)throw new IllegalArgumentException("Invalid worn armour");}
  for(var raw:tag.getList("pending",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;data.pending.add(new Pending(t.getUUID("village"),stack(t.getCompound("item"))));}
  return data;
 }
 @Override public CompoundTag save(CompoundTag tag){
  tag.putInt("schema",1);var d=new ListTag();deals.values().forEach(x->d.add(x.copy()));tag.put("deals",d);
  var m=new ListTag();memory.forEach((k,v)->{if(v.count<=0)return;var t=new CompoundTag();t.putString("key",k);t.putLong("count",v.count);t.putLong("since",v.since);m.add(t);});tag.put("memory",m);
  var s=new ListTag();totals.forEach((k,v)->{var t=new CompoundTag();t.putString("key",k);t.putLong("reputation",v);s.add(t);});tag.put("totals",s);
  var w=new ListTag();worn.forEach((id,v)->{var t=new CompoundTag();t.putUUID("resident",id);t.putUUID("village",v.village);var slots=new CompoundTag();v.slots.forEach((slot,stack)->slots.put(slot.getName(),stack.save(new CompoundTag())));t.put("slots",slots);w.add(t);});tag.put("worn",w);
  var p=new ListTag();pending.forEach(x->{var t=new CompoundTag();t.putUUID("village",x.village);t.put("item",x.stack.save(new CompoundTag()));p.add(t);});tag.put("pending",p);
  return tag;
 }
}
