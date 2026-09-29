package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealableBlock;
import org.villageastra.persistence.WorldJournal;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.ResearchCatalog;
import org.villageastra.server.SettlementData;
/** AD-123: research effects that are plain numbers, read from balance/research_knobs.json by the simulation and the research cards alike.
 *  Research lookups read the village's research record from disk, so the completed set is cached per village and game tick. */
public final class ResearchKnobs {
 private ResearchKnobs(){}
 private static final JsonObject ROOT=read();
 private static final int[] FOUNDATION=ints("foundation"),QUARRY=ints("quarry_depth");
 /** AD-153: the construction ladder's crew, bag, reach and wolf, indexed like the foundation by the highest construction rung done (0..6). */
 private static final int[] BUILDERS=ints("builders"),BAG=ints("builder_bag"),BUILDER_REACH=ints("builder_reach"),BUILDER_WOLF=ints("builder_wolf");
 private static final String FOUNDATION_BRANCH=ROOT.get("foundation_research").getAsString(),QUARRY_BRANCH=ROOT.get("quarry_research").getAsString();
 /** AD-136 (D9, CF14): the drive shaft from the mill wheel reaches this far in every village (no mechanics branch any more). */
 public static final int DRIVE_FIXED=ROOT.get("drive_fixed").getAsInt();
 /** Bone meal feeding (Agriculture I): the farmer feeds a crop he has just planted from the farm chest; while false no card shows it. */
 public static final boolean BONE_MEAL_ACTIVE=ROOT.get("bone_meal_active").getAsBoolean();
 private static final Map<String,Integer> BONE_MEAL=boneMealTable();
 private record Done(long time,Set<String> nodes){}
 private static final Map<UUID,Done> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
 private static JsonObject read(){try(var s=ResearchKnobs.class.getResourceAsStream("/data/villageastra/balance/research_knobs.json")){if(s==null)throw new IllegalStateException("Missing research knobs");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static int[] ints(String key){var a=ROOT.getAsJsonArray(key);var out=new int[a.size()];for(int i=0;i<out.length;i++){out[i]=a.get(i).getAsInt();if(i>0&&out[i]<out[i-1])throw new IllegalStateException("Research knob "+key+" must not get worse");}return out;}
 private static Map<String,Integer> boneMealTable(){var out=new LinkedHashMap<String,Integer>();for(var en:ROOT.getAsJsonObject("bone_meal_per_module").entrySet()){int n=en.getValue().getAsInt();if(n<1||n>4)throw new IllegalStateException("Invalid bone meal knob "+en.getKey());out.put(en.getKey(),n);}return Map.copyOf(out);}
 /** Completed research of a village, read at most once per game tick. */
 public static Set<String> done(ServerLevel l,SettlementData.Entry e){
  long now=l.getGameTime();var id=e.settlement().id();var c=CACHE.get(id);
  if(c!=null&&c.time==now)return c.nodes;var nodes=Set.copyOf(ResearchGate.done(l,e));CACHE.put(id,new Done(now,nodes));return nodes;
 }
 /** Drops the cached research of a village (tests that learn research and read a knob in the same tick). */
 public static void forget(UUID village){CACHE.remove(village);}
 public static void clear(){CACHE.clear();}
 /** The highest rung 1..max of a branch the village has done, 0 when none. */
 private static int rung(Set<String> done,String branch,int max){int best=0;for(int t=1;t<=max;t++)if(done.contains(branch+"."+t))best=t;return best;}
 private static int value(int[] table,Set<String> done,String branch){return table[rung(done,branch,table.length-1)];}
 /** The deepest fill (blocks) a new building site may take under its footprint: Construction I–IV. */
 public static int foundation(ServerLevel l,SettlementData.Entry e){return value(FOUNDATION,done(l,e),FOUNDATION_BRANCH);}
 /** The longest drive shaft from the mill wheel: fixed (AD-136). */
 public static int driveReach(ServerLevel l,SettlementData.Entry e){return DRIVE_FIXED;}
 /** How far below its top a newly planned quarry working goes: Mining I. */
 public static int quarryDepth(ServerLevel l,SettlementData.Entry e){return value(QUARRY,done(l,e),QUARRY_BRANCH);}
 /** AD-153: how many builders the hall posts (Construction I..VI: 2/2/4/6/8/10, one without research). */
 public static int builders(ServerLevel l,SettlementData.Entry e){return value(BUILDERS,done(l,e),FOUNDATION_BRANCH);}
 /** AD-153: how many times the plain load a builder carries (Construction II: the builder's bag, x3). */
 public static int bag(ServerLevel l,SettlementData.Entry e){return value(BAG,done(l,e),FOUNDATION_BRANCH);}
 /** AD-153: the builder's reach as a multiple of the plain 6 blocks (Construction IV: x2, VI: x3). */
 public static int reach(ServerLevel l,SettlementData.Entry e){return value(BUILDER_REACH,done(l,e),FOUNDATION_BRANCH);}
 /** AD-153: whether a kennel wolf fetches the road and wall materials for the builder (Construction V). */
 public static boolean builderWolf(ServerLevel l,SettlementData.Entry e){return value(BUILDER_WOLF,done(l,e),FOUNDATION_BRANCH)>0;}
 private static int at(int[] table,int rung){return table[Math.max(0,Math.min(table.length-1,rung))];}
 public static int buildersAt(int rung){return at(BUILDERS,rung);}
 public static int bagAt(int rung){return at(BAG,rung);}
 public static int reachAt(int rung){return at(BUILDER_REACH,rung);}
 public static boolean builderWolfAt(int rung){return at(BUILDER_WOLF,rung)>0;}
 /** AD-046: the plain reach of a builder, blocks from the eye to the cell centre (BuildingOrders.REACH_SQ). */
 public static final int BASE_REACH=6;
 public static int foundationAt(int rung){return FOUNDATION[Math.max(0,Math.min(FOUNDATION.length-1,rung))];}

 public static int quarryAt(int rung){return QUARRY[Math.max(0,Math.min(QUARRY.length-1,rung))];}
 /** Bone meal a freshly planted crop gets: the largest of the done research that grants it, 0 without it (or while the hook is off). */
 public static int boneMeal(ServerLevel l,SettlementData.Entry e){if(!BONE_MEAL_ACTIVE)return 0;var done=done(l,e);int n=0;for(var en:BONE_MEAL.entrySet())if(done.contains(en.getKey()))n=Math.max(n,en.getValue());return n;}
 /** AD-123 (Agriculture I): the farmer feeds the crop he has just planted with bone meal taken from the farm chest — each take under its own journal id
  *  derived from the planting operation, so a replay never pays twice; without the research, the bone meal or a crop that can grow, nothing happens. */
 public static int feedCrop(ServerLevel l,SettlementData.Entry e,BlockPos chest,BlockPos crop,UUID operation){
  int n=boneMeal(l,e),fed=0;
  for(int i=0;i<n;i++){
   UUID take=org.villageastra.domain.Settlement.childId(operation,"bone_meal/"+i);if(WorldJournal.exists(l,take))continue;
   var state=l.getBlockState(crop);if(!(state.getBlock() instanceof BonemealableBlock grow)||!grow.isValidBonemealTarget(l,crop,state,false))break;
   if(!(l.getBlockEntity(chest) instanceof Container c))break;
   var meal=ItemStack.EMPTY;for(int slot=0;slot<c.getContainerSize();slot++)if(c.getItem(slot).is(Items.BONE_MEAL)){meal=WorldJournal.take(l,take,chest,slot,c.getItem(slot).copy());break;}
   if(meal.isEmpty())break;
   if(grow.isBonemealSuccess(l,l.random,crop,state))grow.performBonemeal(l,l.random,crop,state);
   l.levelEvent(1505,crop,0);fed++;
  }
  return fed;
 }
 private static Component text(String key,Object... args){return Component.translatable("research.villageastra.fact.knob."+key,args);}
 /** Card lines of the knobs one research node moves, each followed by how it applies (AD-123 R3). */
 public static void facts(String id,List<Component> out){
  var n=ResearchCatalog.get(id);int t=n.tier();
  if(n.branch().equals(FOUNDATION_BRANCH)&&t<FOUNDATION.length){out.add(ResearchEffects.tagged(text("foundation",foundationAt(t-1),foundationAt(t)),"new"));}
  // AD-153: the construction ladder — crew, bag, reach and the wolf, each line only where this rung moves it.
  if(n.branch().equals(FOUNDATION_BRANCH)){
   if(buildersAt(t)!=buildersAt(t-1))out.add(ResearchEffects.tagged(text("builders",buildersAt(t-1),buildersAt(t)),"existing"));
   if(bagAt(t)!=bagAt(t-1))out.add(ResearchEffects.tagged(text("bag",bagAt(t),Roads.CARRY*bagAt(t-1),Roads.CARRY*bagAt(t)),"existing"));
   if(reachAt(t)!=reachAt(t-1))out.add(ResearchEffects.tagged(text("reach",BASE_REACH*reachAt(t-1),BASE_REACH*reachAt(t)),"existing"));
   if(builderWolfAt(t)&&!builderWolfAt(t-1))out.add(ResearchEffects.tagged(text("builder_wolf"),"existing"));}

  if(n.branch().equals(QUARRY_BRANCH)&&t<QUARRY.length){out.add(ResearchEffects.tagged(text("quarry",quarryAt(t-1),quarryAt(t)),"new"));}
  if(BONE_MEAL_ACTIVE&&BONE_MEAL.containsKey(id)){out.add(ResearchEffects.tagged(text("bone_meal",BONE_MEAL.get(id)),"existing"));}
 }
}
