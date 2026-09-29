package org.villageastra.server;
import java.nio.file.*;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import org.villageastra.persistence.NbtRecord;
/** AD-124: a few daily samples of a village (residents, beds, food, coins, research volumes paid) so the office can show trends.
 *  Statistics only: nothing in the world changes, so no WorldJournal. A sample is taken when the office snapshot is built on a new day of
 *  the simulation clock (active ticks: only while a player is near), at most 8 are kept; a skipped day shows as a longer span, never as zero.
 *  data/astra-office/<village>-history.dat, schema 1; a broken or foreign file is renamed to .bad and a new one begins. */
public final class OfficeHistory {
 public static final int SCHEMA=1,KEEP=8;
 public static final long DAY=24000;
 /** One day of a village. food and need are rations (need = a day of meals); paidTotal counts every volume paid into research. */
 public record Sample(long day,int residents,int beds,long food,long need,long coins,long paidTotal,int completed){
  CompoundTag tag(){var t=new CompoundTag();t.putLong("day",day);t.putInt("residents",residents);t.putInt("beds",beds);t.putLong("food",food);t.putLong("need",need);t.putLong("coins",coins);t.putLong("paidTotal",paidTotal);t.putInt("completed",completed);return t;}
  static Sample of(CompoundTag t){return new Sample(t.getLong("day"),t.getInt("residents"),t.getInt("beds"),t.getLong("food"),t.getLong("need"),t.getLong("coins"),t.getLong("paidTotal"),t.getInt("completed"));}
  /** Days of food times ten (one decimal as an integer), -1 without a daily need. */
  long foodTenths(){return need<=0?-1:Math.round(food*10.0/need);}
 }
 private static final Map<UUID,CompoundTag> MEMORY=new HashMap<>();
 private OfficeHistory(){}
 public static void clear(){MEMORY.clear();}
 /** Forget the kept copy of one village (a probe or test that wrote the file itself). */
 public static void forget(UUID village){MEMORY.remove(village);}
 public static Path path(MinecraftServer server,UUID village){return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-office/"+village+"-history.dat");}
 public static CompoundTag empty(UUID village){var t=new CompoundTag();t.putInt("schema",SCHEMA);t.putUUID("village",village);t.put("samples",new ListTag());return t;}
 /** Appends today's sample when the last one is from an earlier day, keeping the newest KEEP. Returns whether it was added. */
 public static boolean record(CompoundTag history,long day,Sample s){
  var list=history.getList("samples",Tag.TAG_COMPOUND);
  if(!list.isEmpty()&&list.getCompound(list.size()-1).getLong("day")>=day)return false;
  list.add(new Sample(day,s.residents(),s.beds(),s.food(),s.need(),s.coins(),s.paidTotal(),s.completed()).tag());while(list.size()>KEEP)list.remove(0);
  history.put("samples",list);return true;
 }
 /** The change since the oldest sample at least a day old: span (days, 0 without one), residents, food (days x10), coins and paid volumes. */
 public static CompoundTag trend(CompoundTag history,long day,Sample now){
  var out=new CompoundTag();Sample base=null;
  for(var raw:history.getList("samples",Tag.TAG_COMPOUND)){var s=Sample.of((CompoundTag)raw);if(s.day()<=day-1){base=s;break;}}
  if(base==null){out.putInt("span",0);return out;}
  out.putInt("span",(int)Math.min(Integer.MAX_VALUE,day-base.day()));out.putLong("since",base.day());
  out.putInt("residents",now.residents()-base.residents());out.putLong("coins",now.coins()-base.coins());out.putLong("paid",now.paidTotal()-base.paidTotal());
  long a=now.foodTenths(),b=base.foodTenths();out.putBoolean("hasFood",a>=0&&b>=0);out.putLong("food",a>=0&&b>=0?a-b:0);
  return out;
 }
 /** The village's history: the kept copy, else the file, else a new one; a broken or foreign file is set aside as .bad, never fatal. */
 public static CompoundTag load(MinecraftServer server,UUID village){
  var kept=MEMORY.get(village);if(kept!=null)return kept;var p=path(server,village);CompoundTag t=null;
  if(Files.exists(p)){
   try{t=NbtRecord.read(p);if(t.getInt("schema")!=SCHEMA||!t.hasUUID("village")||!t.getUUID("village").equals(village)||!t.contains("samples",Tag.TAG_LIST))throw new IllegalStateException("schema "+t.getInt("schema"));}
   catch(RuntimeException ex){LogUtils.getLogger().warn("Office history {} is unreadable ({}); it is kept as .bad and a new one begins",p.getFileName(),ex.getMessage());t=null;
    try{Files.move(p,p.resolveSibling(p.getFileName()+".bad"),StandardCopyOption.REPLACE_EXISTING);}catch(java.io.IOException io){LogUtils.getLogger().warn("Cannot set the office history aside: {}",io.toString());}}}
  if(t==null)t=empty(village);MEMORY.put(village,t);return t;
 }
 public static void save(MinecraftServer server,UUID village,CompoundTag history){try{NbtRecord.write(path(server,village),history);MEMORY.put(village,history);}catch(RuntimeException ex){LogUtils.getLogger().warn("Cannot write the office history: {}",ex.toString());}}
 /** Records today's sample (written only when it was added) and returns the trend against the earlier days. */
 public static CompoundTag update(MinecraftServer server,UUID village,long day,Sample now){
  var h=load(server,village);if(record(h,day,now)||!Files.exists(path(server,village)))save(server,village,h);return trend(h,day,now);
 }
}
