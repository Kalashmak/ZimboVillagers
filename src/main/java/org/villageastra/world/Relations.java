package org.villageastra.world;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
/** AD-100: how two villages stand with each other. Every pair of villages has one score from -100 (enemies) to +100 (allies), the same
 *  seen from either side, and a short memory of why it moved. Deeds move it — a caravan that arrived, a siege laid, a village conquered,
 *  a quest done for the other side — and time softens it: every day each score drifts one point back toward neutral, so relations
 *  improve and worsen with what really happens between the villages, not by themselves. */
public final class Relations extends SavedData {
 public static final int MIN=-100,MAX=100,MEMORY=8;
 /** Standings the rest of the mod reads: at or below HOSTILE no caravan goes between the two, at or above FRIENDLY they trade gladly. */
 public static final int HOSTILE=-40,COLD=-10,WARM=10,FRIENDLY=40;
 private record Pair(UUID a,UUID b){static Pair of(UUID x,UUID y){return x.compareTo(y)<=0?new Pair(x,y):new Pair(y,x);}}
 private final Map<Pair,Integer> scores=new LinkedHashMap<>();
 private final Map<Pair,ListTag> reasons=new LinkedHashMap<>();
 private long lastDay=-1;
 public static Relations get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(Relations::load,Relations::new,"villageastra_relations");}
 /** The score between two villages; 0 when they have never had anything to do with each other. */
 public static int score(MinecraftServer s,UUID a,UUID b){if(a.equals(b))return MAX;return get(s).scores.getOrDefault(Pair.of(a,b),0);}
 /** Moves the relation of two villages by delta (clamped to -100..100) and remembers why. Returns the new score. */
 public static int change(MinecraftServer s,UUID a,UUID b,int delta,String reason){
  if(a.equals(b)||delta==0)return score(s,a,b);
  var r=get(s);var key=Pair.of(a,b);int now=Math.max(MIN,Math.min(MAX,r.scores.getOrDefault(key,0)+delta));
  r.scores.put(key,now);
  var memory=r.reasons.computeIfAbsent(key,k->new ListTag());var entry=new CompoundTag();entry.putString("reason",reason);entry.putInt("delta",delta);
  entry.putLong("tick",org.villageastra.server.SettlementData.get(s).clock().ticks());memory.add(0,entry);while(memory.size()>MEMORY)memory.remove(memory.size()-1);
  r.setDirty();return now;
 }
 /** Why the relation of two villages is what it is, most recent first. */
 public static ListTag reasons(MinecraftServer s,UUID a,UUID b){var memory=get(s).reasons.get(Pair.of(a,b));return memory==null?new ListTag():memory.copy();}
 /** The word the office shows for a score. */
 public static String standing(int score){return score<=HOSTILE?"hostile":score<=COLD?"cold":score<WARM?"neutral":score<FRIENDLY?"warm":"friendly";}
 /** Once per village day every score drifts one point back toward neutral; nothing else changes relations by itself. */
 public static void tick(MinecraftServer s,long activeTicks){
  var r=get(s);long day=activeTicks/24000;if(r.lastDay<0){r.lastDay=day;r.setDirty();return;}if(day<=r.lastDay)return;
  long days=day-r.lastDay;r.lastDay=day;
  for(var entry:r.scores.entrySet()){int v=entry.getValue();int step=(int)Math.min(Math.abs(v),days);entry.setValue(v>0?v-step:v+step);}
  r.setDirty();
 }
 public static Relations load(CompoundTag tag){
  var r=new Relations();r.lastDay=tag.getLong("lastDay");
  for(var raw:tag.getList("pairs",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var key=Pair.of(t.getUUID("a"),t.getUUID("b"));
   int v=t.getInt("score");if(v<MIN||v>MAX)throw new IllegalArgumentException("Invalid relation score");
   if(r.scores.putIfAbsent(key,v)!=null)throw new IllegalArgumentException("Duplicate relation");r.reasons.put(key,t.getList("reasons",Tag.TAG_COMPOUND));}
  return r;
 }
 @Override public CompoundTag save(CompoundTag tag){
  tag.putInt("schema",1);tag.putLong("lastDay",lastDay);var list=new ListTag();
  scores.forEach((key,v)->{var t=new CompoundTag();t.putUUID("a",key.a);t.putUUID("b",key.b);t.putInt("score",v);t.put("reasons",reasons.getOrDefault(key,new ListTag()));list.add(t);});
  tag.put("pairs",list);return tag;
 }
}
