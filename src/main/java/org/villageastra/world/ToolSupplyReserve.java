package org.villageastra.world;

import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;

/** A tiny maintenance buffer prevents unpaid construction from monopolising tool inputs. */
public final class ToolSupplyReserve {
 private ToolSupplyReserve() {}
 private static long WORK_READS;
 /** Actual work-record read attempts, for development diagnostics. */
 public static synchronized long workReads(){return WORK_READS;}
 private record WorkStamp(int tick,long revision,java.nio.file.attribute.FileTime modified,java.nio.file.attribute.FileTime created,long size,Object key){}
 private record WorkRead(WorkStamp stamp,CompoundTag tag){}
 private static final Map<net.minecraft.server.MinecraftServer,Map<Path,WorkRead>> WORK_CACHE=new WeakHashMap<>();
 /** Share copied work records within one real server tick; stock and residents remain live queries. */
 public static synchronized CompoundTag inspectWork(ServerLevel l,UUID building){
  var p=MineWork.path(l,building).toAbsolutePath().normalize();
  if(!Files.exists(p)){var cache=WORK_CACHE.get(l.getServer());if(cache!=null)cache.remove(p);return new CompoundTag();}
  try{
   var a=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class);
   var stamp=new WorkStamp(l.getServer().getTickCount(),org.villageastra.persistence.AtomicRecord.revision(p),a.lastModifiedTime(),a.creationTime(),a.size(),a.fileKey());
   var cache=WORK_CACHE.computeIfAbsent(l.getServer(),server->new LinkedHashMap<Path,WorkRead>(16,.75F,true){
    @Override protected boolean removeEldestEntry(Map.Entry<Path,WorkRead> entry){return size()>256;}
   });
   var old=cache.get(p);if(old!=null&&old.stamp().equals(stamp))return old.tag().copy();
   WORK_READS++;var tag=NbtRecord.read(p);cache.put(p,new WorkRead(stamp,tag));return tag.copy();
  }catch(java.io.IOException ex){throw new IllegalStateException("Cannot read "+p,ex);}
 }
 public static boolean needed(ServerLevel l,SettlementData.Entry e){
  var hall=Workshops.hall(e);var stock=hall==null?null:LogisticsRoutes.chest(l,e,hall);
  for(var r:e.settlement().residents()){
   if(!r.alive()||r.life()!=Resident.Life.ADULT||r.profession()==null)continue;
   var tag=switch(r.profession()){case FORESTER->ItemTags.AXES;case MINER->ItemTags.PICKAXES;case FARMER->ItemTags.HOES;default->null;};
   if(tag==null)continue;var b=e.settlement().workplace(r.id());if(b==null)continue;
   var work=inspectWork(l,b.id());
   var required=work.contains("requiredToolState")?net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),work.getCompound("requiredToolState")):null;
   java.util.function.Predicate<ItemStack> fits=s->usable(s,tag)&&(required==null||s.isCorrectToolForDrops(required));
   var held=ItemStack.of(work.getCompound("tool"));if(fits.test(held))continue;
   if(has(stock,fits)||has(LogisticsRoutes.chest(l,e,b),fits))continue;
   return true;
  }return false;
 }
 private static boolean usable(ItemStack s,net.minecraft.tags.TagKey<Item> tag){return !s.isEmpty()&&s.is(tag)&&(!s.isDamageableItem()||s.getDamageValue()<s.getMaxDamage());}
 private static boolean has(Container c,java.util.function.Predicate<ItemStack> fits){if(c==null)return false;for(int i=0;i<c.getContainerSize();i++)if(fits.test(c.getItem(i)))return true;return false;}
 public static boolean tool(ItemStack s){return s.is(ItemTags.AXES)||s.is(ItemTags.PICKAXES)||s.is(ItemTags.HOES);}
 public static int quantity(Item item){var s=new ItemStack(item);return s.is(ItemTags.LOGS)?1:s.is(ItemTags.PLANKS)||s.is(ItemTags.STONE_TOOL_MATERIALS)||item==Items.IRON_INGOT||item==Items.RAW_IRON||item==Items.DIAMOND?3:item==Items.STICK?2:0;}
 public static int quantity(ServerLevel l,SettlementData.Entry e,Item item){return quantity(item)>0&&needed(l,e)?quantity(item):0;}
 /** Science cannot spend the sole tool a posted raw producer still needs to collect. */
 public static int researchQuantity(ServerLevel l,SettlementData.Entry e,Item item){
  int maintenance=quantity(l,e,item);var candidate=new ItemStack(item);if(!tool(candidate))return maintenance;
  for(var r:e.settlement().residents()){
   if(!r.alive()||r.life()!=Resident.Life.ADULT||r.profession()==null)continue;
   var tag=switch(r.profession()){case FORESTER->ItemTags.AXES;case MINER->ItemTags.PICKAXES;case FARMER->ItemTags.HOES;default->null;};
   if(tag==null||!candidate.is(tag))continue;var b=e.settlement().workplace(r.id());if(b==null)continue;
   var work=inspectWork(l,b.id());
   var required=work.contains("requiredToolState")?net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),work.getCompound("requiredToolState")):null;
   java.util.function.Predicate<ItemStack> fits=s->usable(s,tag)&&(required==null||s.isCorrectToolForDrops(required));
   if(!fits.test(candidate)||fits.test(ItemStack.of(work.getCompound("tool")))||has(LogisticsRoutes.chest(l,e,b),fits))continue;
   return 1;
  }return maintenance;
 }
 public static boolean payment(ServerLevel l,SettlementData.Entry e,java.util.UUID take){
  var hall=Workshops.hall(e);if(hall==null)return false;var job=Workshops.inspect(l,hall.id());
  if(!job.getBoolean("toolRepair")||!job.hasUUID("id"))return false;
  String suffix=switch(job.getString("stage")){case "fund"->"input/"+job.getInt("withdrawals");case "smelt_raw"->"smelt/raw/"+job.getInt("withdrawals");case "smelt_fuel"->"smelt/fuel/"+job.getInt("fuels");default->null;};
  return suffix!=null&&take.equals(Settlement.childId(job.getUUID("id"),suffix));
 }
}
