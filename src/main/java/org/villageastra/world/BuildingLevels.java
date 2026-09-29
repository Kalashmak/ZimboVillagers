package org.villageastra.world;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-052/AD-073: what a level does for a working building. The level is read from the equipment standing in place (BuildingTiers);
 *  a station works faster with every level above the first. */
public final class BuildingLevels {
 public static final int MAX=BuildingTiers.MAX;
 private static final int LABOR_BONUS=LevelArchitecture.data().get("labor_bonus").getAsInt();
 private BuildingLevels(){}
 public static boolean upgradable(String type){return BuildingTiers.upgradable(type);}
 public static int level(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return BuildingTiers.level(l,e,b);}
 /** Work of one turn of a station: level II works LABOR_BONUS times as fast as level I, each further level adds one more turn. */
 public static int labor(ServerLevel l,SettlementData.Entry e,Settlement.Building b){int level=level(l,e,b);return level<=1?20:20*(LABOR_BONUS+level-2);}
 /** AD-112: how long best() trusts a level it has read, in game ticks. */
 public static final int BEST_TICKS=100;
 private record Best(long at,int level){}
 private static final Map<String,Best> BEST=new java.util.concurrent.ConcurrentHashMap<>();
 /** AD-112: the best working level among the settlement's buildings of this type (0 when it has none) — a settlement-wide knob such as the
  *  barracks' drill or the laboratory's labour. Read from the world at most once per BEST_TICKS for each settlement and type. */
 public static int best(ServerLevel l,SettlementData.Entry e,String type){
  var key=e.settlement().id()+"/"+type;long now=l.getGameTime();var cached=BEST.get(key);
  if(cached!=null&&now>=cached.at()&&now-cached.at()<BEST_TICKS)return cached.level();
  int best=0;for(var b:e.settlement().buildings())if(org.villageastra.domain.CoreCatalog.canonical(b.type()).equals(type))best=Math.max(best,level(l,e,b));
  BEST.put(key,new Best(now,best));return best;
 }
 /** Forget the levels best() has read for a settlement (a test or a change that must be seen at once). */
 public static void forgetBest(UUID settlement){BEST.keySet().removeIf(k->k.startsWith(settlement+"/"));}
 /** The upgrade project of the next level, or null when it cannot be planned now. */
 public static CompoundTag plan(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(!BuildingTiers.refusal(l,e,b).isEmpty())return null;
  var survey=BuildingTiers.survey(l,e,b);return survey.ok()?survey.state():null;
 }
 public static String order(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return BuildingTiers.order(l,e,b);}
 public static int missingItems(ServerLevel l,SettlementData.Entry e,CompoundTag project){return BuildingTiers.missingItems(l,e,project);}
 /** Equipment of one level of a design, in local cells. */
 public static List<LevelArchitecture.Placed> equipment(String type,int level){
  return LevelArchitecture.equipment(type).stream().filter(p->p.level()==level).toList();
 }
}
