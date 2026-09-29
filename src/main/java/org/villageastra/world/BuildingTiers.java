package org.villageastra.world;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.Settlement;
import org.villageastra.server.*;
/** AD-073 (A07-AUTO-001, OWNER_REQUEST §6): six levels of every settlement building.
 *  <ul><li>The level a building is <b>kept at</b> is stored with it: repairs and protection keep that structure.</li>
 *  <li>The level it <b>works at</b> is read from the world: the highest level whose equipment all stands in place (AD-052).</li>
 *  <li>A level is reached by a construction project on the same lot: the builder rebuilds the difference from the design of the next level with
 *      real materials from the hall; the research the catalogue names for that level must be finished first.</li>
 *  <li>The town hall reaches II and III by its own hall projects (AD-018) and IV…VI through the same projects as every other building.</li></ul> */
public final class BuildingTiers {
 public static final int MAX=LevelArchitecture.MAX;
 private BuildingTiers(){}
 /** AD-136: a building type's levels II–VI that need its parent building at that level (mill, stoneworks, carpentry, expedition). */
 public record Parent(String type,int level){}
 private static final Map<String,Integer> MAX_LEVEL=new HashMap<>();
 private static final Map<String,Map<Integer,Parent>> PARENTS=new HashMap<>();
 private static final Map<String,Map<Integer,List<String>>> REQUIRED=readRequirements();
 private static Map<String,Map<Integer,List<String>>> readRequirements(){
  try(var s=BuildingTiers.class.getResourceAsStream("/data/villageastra/catalog/building_progression.json")){
   if(s==null)throw new IllegalStateException("Missing building progression catalogue");
   var root=JsonParser.parseReader(new java.io.InputStreamReader(s,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();var out=new HashMap<String,Map<Integer,List<String>>>();
   for(var raw:root.getAsJsonArray("catalog")){var b=raw.getAsJsonObject();var levels=new HashMap<Integer,List<String>>();
    String id=b.get("building_id").getAsString();var parents=new HashMap<Integer,Parent>();
    for(var l:b.getAsJsonArray("levels")){var o=l.getAsJsonObject();var req=new ArrayList<String>();for(var r:o.getAsJsonArray("requires_research_all"))req.add(r.getAsString());levels.put(o.get("level").getAsInt(),List.copyOf(req));
     if(o.has("requires_building")){var rb=o.getAsJsonObject("requires_building");parents.put(o.get("level").getAsInt(),new Parent(rb.get("type").getAsString(),rb.get("level").getAsInt()));}}
    // AD-136: a level the catalogue does not list is never open (CF5): the engineering office stops at V.
    int max=b.has("max_level")?b.get("max_level").getAsInt():LevelArchitecture.MAX;for(int lv=1;lv<=max;lv++)if(!levels.containsKey(lv))throw new IllegalStateException("Missing level "+lv+" of "+id);
    MAX_LEVEL.put(id,max);PARENTS.put(id,Map.copyOf(parents));
    out.put(id,Map.copyOf(levels));}
   return Map.copyOf(out);
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 public static boolean upgradable(String type){return LevelArchitecture.hasLevels(type)&&catalog(type)!=null;}
 static String catalog(String type){var c=LevelArchitecture.data().getAsJsonObject("catalog");return c.has(type)?c.get(type).getAsString():null;}
 /** Research the catalogue names for a level of this building type. */
 public static List<String> research(String type,int level){var c=catalog(type);if(c==null)return List.of();return REQUIRED.getOrDefault(c,Map.of()).getOrDefault(level,List.of());}
 /** AD-136: the highest level a building type may be ordered to (the engineering office V, every other VI). */
 public static int max(String type){var c=catalog(type);return c==null?MAX:MAX_LEVEL.getOrDefault(c,MAX);}
 /** AD-136: the parent building a level needs (its working level at least that level), or null. */
 public static Parent parent(String type,int level){var c=catalog(type);return c==null?null:PARENTS.getOrDefault(c,Map.of()).get(level);}
 /** Whether the level's parent condition holds: the parent's best working level in the village. */
 public static boolean parentReady(ServerLevel l,SettlementData.Entry e,String type,int level){var p=parent(type,level);return p==null||BuildingLevels.best(l,e,p.type())>=p.level();}
 /** AD-136 (owner answer 2): the town hall's level caps every other building's level. */
 public static int hallLevel(SettlementData.Entry e){var hall=Workshops.hall(e);return hall==null?e.settlement().civilization().level():built(e,hall);}
 /** Research needed for the next town hall level: allowed one hall tier early, or the hall could never be raised to open its own tier. */
 public static List<String> hallResearch(int level){return research("town_hall",level);}
 /** Design id of a level: level I is the design itself; the hall's II and III are its own blueprints and IV…VI are built on III. */
 /** AD-121: the design id of a level of this village's building - a castle village's hall is the castle ("castle@N", every level its own
  *  CastlePlan), every other the design ladder below. */
 public static String layoutId(org.villageastra.domain.Settlement s,String type,int level){
  if(type.equals("town_hall")&&HallSite.castle(s))return BuildingBlueprints.CASTLE+"@"+Math.max(1,Math.min(6,level));
  return layoutId(type,level);
 }
 public static String layoutId(String type,int level){
  if(type.equals("town_hall"))return level<=1?"town_hall":level<=3?"town_hall_"+level:"town_hall_3@"+level;
  return level<=1?type:type+"@"+level;
 }
 /** The level a building is kept at. The hall's first three levels are the civilization's. */
 public static int built(SettlementData.Entry e,Settlement.Building b){
  return b.type().equals("town_hall")?Math.max(b.level(),e.settlement().civilization().level()):b.level();
 }
 /** Equipment cells of the design one level stands on (for the hall IV…VI, those of hall III). */
 private static List<LevelArchitecture.Placed> equipment(String type){return LevelArchitecture.equipment(type.equals("town_hall")?"town_hall_3":type);}
 /** The level a building works at: its kept level, lowered to the last level whose equipment all stands. */
 public static int level(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  int kept=built(e,b);if(!upgradable(b.type())||kept<=1)return kept;
  // AD-121: a castle hall works at the highest level whose kit (CastlePlan.kitAt: stations, the seal) stands, every lower one too.
  if(b.type().equals("town_hall")&&HallSite.castle(e.settlement())){int reached=1;var shell=CastleArchitecture.shell(kept,BuildingPlacement.origin(e,b));
   for(int level=2;level<=kept;level++){for(var c:CastlePlan.kitAt(level).keySet()){var pos=BuildingPlacement.origin(e,b).offset(c.x(),c.y(),c.z());
     if(!l.hasChunkAt(pos)||l.getBlockState(pos).getBlock()!=shell.get(pos).getBlock())return reached;}reached=level;}
   return reached;}
  int floor=b.type().equals("town_hall")?Math.min(kept,3):1;
  int reached=floor;boolean legacy=legacyShape(l,e,b);
  var actualEquipment=legacy?Legacy117Architecture.equipment(b.type().equals("town_hall")?"town_hall_3":b.type()).stream().map(p->new LevelArchitecture.Placed(p.local(),p.state(),p.level())).toList():equipment(b.type());
  for(int level=Math.max(2,floor+1);level<=kept;level++){
   // The hall's fourth level brings the equipment of levels II…IV at once: its II and III are hall blueprints without kits.
   final int at=level;boolean hallFirst=b.type().equals("town_hall")&&level==4;
   for(var placed:actualEquipment){if(hallFirst?placed.level()>at:placed.level()!=at)continue;
    var pos=BuildingPlacement.at(e,b,placed.local().getX(),placed.local().getY(),placed.local().getZ());
    if(!l.hasChunkAt(pos)||l.getBlockState(pos).getBlock()!=BuildingPlacement.state(placed.state(),b.rotation()).getBlock())return reached;}
   reached=level;
  }
  // AD-112: a building works no higher than its core's grade (none: level I); a building type without a core is not capped.
  if(org.villageastra.domain.CoreCatalog.coreId(b.type())!=null){var cell=legacy?Legacy117Architecture.core(b.type()):LevelArchitecture.core(b.type());var at=BuildingPlacement.at(e,b,cell.getX(),cell.getY(),cell.getZ());
   var now=l.hasChunkAt(at)?l.getBlockState(at):null;reached=Math.min(reached,now!=null&&Cores.isCoreOf(now,b.type())?Cores.grade(now):1);}
  // AD-130: a farm of a layout-6 village works a barn level only while that level's lanterns (IV, V) or machinery (VI) stand.
  if(b.type().equals("farm")&&reached>=FarmField.BARN_FROM&&!FarmField.legacy(e.settlement())){int laid=FarmBarn.laid(e.settlement(),b),cap=FarmField.BARN_FROM-1;
   for(int lv=FarmField.BARN_FROM;lv<=reached&&lv<=laid&&FarmBarn.stands(l,e,b,lv);lv++)cap=lv;reached=Math.min(reached,cap);}
  return reached;
 }
 /** AD-117 migration's reading of an old building — except a forester's hut (AD-131): its 15x21 plans have no frozen counterpart, so every
  *  world reads it as the new hut (old worlds are not converted, owner 2026-09-19). AD-138: the livestock yard likewise (11x13 -> 17x25).
  *  AD-147 (CF-A): the warehouse likewise (13x11 -> 23x17), one plan for every world. */
 public static boolean legacyShape(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return !b.type().equals(ForesterHut.TYPE)&&!b.type().equals("livestock")&&!b.type().equals(WarehouseStore.TYPE)&&ArchitectureMigration.legacy(l,e,b);}
 /** Value of one item for comparing what levels cost. */
 public static int value(String item){var v=LevelArchitecture.data().getAsJsonObject("value");return v.has(item)?v.get(item).getAsInt():LevelArchitecture.data().get("default_value").getAsInt();}
 /** Items an upgrade from level-1 to level of a design needs, from the designs alone: every cell whose block changes to something that is not air. */
 public static Map<String,Integer> cost(String type,int level){
  return cost(type,level,"");
 }
 public static Map<String,Integer> cost(String type,int level,String wood){
  var before=BuildingWood.apply(BuildingBlueprints.layout(layoutId(type,level-1),BlockPos.ZERO),wood);var after=BuildingWood.apply(BuildingBlueprints.layout(layoutId(type,level),BlockPos.ZERO),wood);
  var out=new TreeMap<String,Integer>();
  for(var cell:after.entrySet()){var now=cell.getValue();if(now.isAir())continue;var old=before.get(cell.getKey());
   if(old!=null&&BuildingRepairs.present(old,now))continue;
   // AD-112: the same rule as the builders' survey — a core cell costs the core and a ring for each grade it rises.
   var items=BuildingOrders.materials(old==null?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():old,BuildingOrders.payable(now));if(items==null)continue;
   for(var item:items)if(!item.isEmpty())out.merge(item,1,Integer::sum);}
  // AD-112 (AD-104 P4): a farm level also lays the plots of its new field modules, one slab over each new spring. AD-130: the barn is field
  // work sized by the fields, not the design's level (IV lays nearly all of it): it is paid with the project and shown on the card (view),
  // outside this design catalogue that AD-073 holds to "every level dearer".
  if(type.equals("farm"))FarmField.addedCost(level).forEach((k,v)->out.merge(k,v,Integer::sum));
  return out;
 }
 public static int count(Map<String,Integer> cost){return cost.values().stream().mapToInt(Integer::intValue).sum();}
 public static long worth(Map<String,Integer> cost){long n=0;for(var e:cost.entrySet())n+=(long)value(e.getKey())*e.getValue();return n;}
 /** Why the next level cannot be ordered now, or empty. */
 public static String refusal(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(ArchitectureMigration.waiting(l,e,b))return "busy";
  if(!upgradable(b.type()))return "building";
  int kept=built(e,b);if(kept>=max(b.type()))return "done";
  if(b.type().equals("town_hall")&&kept<3)return "hall_office";
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  if(HallUpgradeGoal.pending(l,e.settlement().id())&&!HallUpgradeGoal.yields(l,e.settlement().id()))return "busy";
  if(level(l,e,b)<kept)return "equipment";
  if(!missingResearch(l,e,b.type(),kept+1).isEmpty())return "research";
  // AD-136: no building above the hall's level (the hall itself follows its own branch), and a side building no higher than its parent.
  if(!b.type().equals("town_hall")&&kept+1>hallLevel(e))return "hall";
  if(!parentReady(l,e,b.type(),kept+1))return "parent";
  // AD-112 (AD-104 P4): the new plots of a farm level must have room.
  if(b.type().equals("farm")&&FarmField.plan(l,e,b,kept+1).reason().equals("field"))return "field";
  // AD-130: and the barn of the level must have room (reason "barn").
  if(b.type().equals("farm")&&FarmBarn.plan(l,e,b,kept+1).reason().equals("barn"))return "barn";
  return "";
 }
 /** AD-112 (owner, 2026-09-19): the highest grade of core this building type may hold: every level from II up to it has its research
  *  done (1 when the core's own research, level II, is missing). */
 public static int researchedGrade(ServerLevel l,SettlementData.Entry e,String type){
  var done=BookResearch.completed(e,BookResearch.inspect(l,e));int grade=1;
  // CF5: only up to the type's own last level, and a level with a parent needs that parent standing at it.
  for(int level=2;level<=max(type)&&done.containsAll(research(type,level))&&parentReady(l,e,type,level);level++)grade=level;
  return grade;
 }
 public static List<String> missingResearch(ServerLevel l,SettlementData.Entry e,String type,int level){
  var done=BookResearch.completed(e,BookResearch.inspect(l,e));var out=new ArrayList<String>();
  for(var id:research(type,level))if(!done.contains(id))out.add(id);
  return out;
 }
 /** The upgrade project: the builder's own survey of the next level's design at the building's place, in the building's turn. */
 public static BuildingOrders.Survey survey(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  int next=built(e,b)+1;
  var survey=BuildingOrders.survey(l,e,layoutId(e.settlement(),b.type(),next),b.rotation(),BuildingPlacement.origin(e,b),b);
  if(!survey.state().isEmpty()){survey.state().putInt("upgradeLevel",next);survey.state().putBoolean("upgrade",true);survey.state().remove("repair");}
  // AD-112 (AD-104 P4): a farm level lays the plots of every level its field lags behind, on the side chosen once; paid with the building.
  if(b.type().equals("farm")&&!survey.state().isEmpty()){var fp=FarmField.plan(l,e,b,next);
   if(!fp.reason().isEmpty())return new BuildingOrders.Survey(survey.state(),fp.conflicts().isEmpty()?survey.conflicts():fp.conflicts(),survey.reason().isEmpty()||fp.reason().equals("unloaded")?fp.reason():survey.reason());
   // AD-130: the barn of the level (walls, decks, the upper fields' soil, lanterns, tower, roof, VI machinery) after the ground fields.
   var bp=FarmBarn.plan(l,e,b,next);
   if(!bp.reason().isEmpty())return new BuildingOrders.Survey(survey.state(),bp.conflicts().isEmpty()?survey.conflicts():bp.conflicts(),survey.reason().isEmpty()||bp.reason().equals("unloaded")?bp.reason():survey.reason());
   var ops=survey.state().getList("ops",Tag.TAG_COMPOUND);var cost=survey.state().getCompound("cost");
   for(var op:fp.ops())ops.add(op);fp.cost().forEach((k,v)->cost.putInt(k,cost.getInt(k)+v));
   for(var op:bp.ops())ops.add(op);bp.cost().forEach((k,v)->cost.putInt(k,cost.getInt(k)+v));
   survey.state().putBoolean("fieldWest",fp.west());survey.state().putInt("fieldLevel",next);}
  return survey;
 }
 /** Orders the next level: refused with a reason, or queued for the builders who then fund it from the hall. */
 public static String order(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var refusal=refusal(l,e,b);if(!refusal.isEmpty())return refusal;
  var survey=survey(l,e,b);
  if(!survey.reason().isEmpty())return survey.reason();
  if(survey.state().getList("ops",Tag.TAG_COMPOUND).isEmpty())return "nothing";
  HallUpgradeGoal.yield(l,e.settlement().id());
  HallUpgradeGoal.enqueue(l,e,survey.state());SettlementData.get(l.getServer()).setDirty();return "";
 }
 /** Items the hall is still short of for a project. */
 public static int missingItems(ServerLevel l,SettlementData.Entry e,CompoundTag project){
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);int missing=0;
  var cost=project.getCompound("cost");
  for(var key:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));int have=chest==null?0:LogisticsRoutes.count(chest,s->s.is(item));missing+=Math.max(0,cost.getInt(key)-have);}
  return missing;
 }
 /** Registers a finished upgrade: the building is kept at its new level; the hall's civilization level follows its hall. */
 public static void completed(SettlementData.Entry e,Settlement.Building b,int level){
  // Defence tiers may skip a material-neutral rung (II -> IV). Commit those metadata steps only after the entire paid plan stands.
  if(b.type().equals(Walls.TOWER)){for(int n=b.level()+1;n<=level;n++)e.settlement().raiseBuildingLevel(b.id(),n);return;}
  if(e.settlement().raiseBuildingLevel(b.id(),level)&&b.type().equals("town_hall")&&e.settlement().civilization().level()==level-1)e.settlement().civilization().completedHallUpgrade(level);
 }
 /** Most items listed one by one on a card's cost. */
 public static final int COST_ROWS=12;
 /** The card of a building's levels for the map and the office. */
 public static CompoundTag view(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var t=new CompoundTag();int kept=built(e,b);t.putInt("level",level(l,e,b));t.putInt("kept",kept);t.putInt("max",max(b.type()));
  t.putString("refusal",refusal(l,e,b));t.putInt("hall",hallLevel(e));var parent=parent(b.type(),kept+1);if(parent!=null){t.putString("parent",parent.type());t.putInt("parentLevel",parent.level());}
  if(kept<max(b.type())){
   var cost=cost(b.type(),kept+1,b.wood());
   // AD-130: a layout-6 farm's card counts its barn too (walls, decks, dirt, lanterns, roof, tower, VI machinery), as its project charges.
   if(b.type().equals("farm")&&!FarmField.legacy(e.settlement())){var all=new TreeMap<String,Integer>(cost);FarmBarn.addedCost(kept+1).forEach((k,v)->all.merge(k,v,Integer::sum));cost=all;}
   t.putInt("next",kept+1);t.putInt("items",count(cost));t.putLong("worth",worth(cost));
   t.put("core",core(l,e,b,kept+1,cost));
   var research=new ListTag();for(var id:missingResearch(l,e,b.type(),kept+1))research.add(StringTag.valueOf(id));t.put("research",research);
   var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);int lack=0;
   // The office shows each item as have/need, the ones still missing first (at most COST_ROWS; the totals above stay for older readers).
   var rows=new ArrayList<CompoundTag>();
   for(var entry:cost.entrySet()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(entry.getKey()));int have=chest==null?0:LogisticsRoutes.count(chest,s->s.is(item))-HallReserve.keptFrom(l,e,b,item);lack+=Math.max(0,entry.getValue()-have);
    var row=new CompoundTag();row.putString("item",entry.getKey());row.putInt("need",entry.getValue());row.putInt("have",have);rows.add(row);}
   rows.sort(Comparator.<CompoundTag>comparingInt(r->r.getInt("have")-r.getInt("need")).thenComparing(r->r.getString("item")));
   var list=new ListTag();for(var row:rows)if(list.size()<COST_ROWS)list.add(row);
   t.putInt("lack",lack);t.put("cost",list);
  }
  t.putBoolean("driven",Workshops.spec(b.type())!=null&&Drive.driven(l,e,b));
  return t;
 }
 /** AD-112: the core row of a building's card: the core or ring the next level takes (item, have, need; nothing for a building without a
  *  core), the grade standing now (0 when missing) and needed, the research that level still waits for, and every effect as now → next
  *  (active false: the effect does not work yet and carries no numbers). The farm adds its field modules built now, worked now and built next. */
 /** AD-112: what an active effect of this building gives at a level as the world turns it: the table's number, except the farm's field
  *  (the modules worked now: the core's level within the land laid) and the mine's floor (the Y its drive really stops at). */
 public static int effectAt(ServerLevel l,SettlementData.Entry e,Settlement.Building b,String type,String id,int level){
  if(type.equals("mine")&&id.equals("floor"))return MineWork.floorY(l,e,b,level);
  return org.villageastra.domain.CoreEffects.value(type,id,level);
 }
 public static int effectNow(ServerLevel l,SettlementData.Entry e,Settlement.Building b,String type,String id,int working){
  if(type.equals("farm")&&id.equals("field"))return FarmField.worked(l,e,b).size();
  return effectAt(l,e,b,type,id,working);
 }
 /** AD-121: the local cell of this building's core: a castle hall's seal (CastlePlan 14,1,23 - the village centre), a legacy shape's,
  *  else its design's. */
 public static BlockPos coreCell(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(b.type().equals("town_hall")&&HallSite.castle(e.settlement()))return new BlockPos(-HallSite.CASTLE_X,1,-HallSite.CASTLE_Z);
  return legacyShape(l,e,b)?Legacy117Architecture.core(b.type()):LevelArchitecture.core(b.type());}
 static CompoundTag core(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int next,Map<String,Integer> cost){
  var t=new CompoundTag();var type=org.villageastra.domain.CoreCatalog.coreType(b.type());if(type==null)return t;
  var cell=coreCell(l,e,b);var at=BuildingPlacement.at(e,b,cell.getX(),cell.getY(),cell.getZ());var now=l.hasChunkAt(at)?l.getBlockState(at):null;
  int grade=now!=null&&Cores.isCoreOf(now,b.type())?Cores.grade(now):0;t.putInt("grade",grade);t.putInt("gradeNeeded",next);
  String item=cost.keySet().stream().filter(org.villageastra.domain.CoreCatalog::isCore).findFirst().orElse(cost.keySet().stream().filter(org.villageastra.domain.CoreCatalog::isRing).max(Comparator.naturalOrder()).orElse(""));
  t.putString("item",item);t.putInt("need",item.isEmpty()?0:cost.get(item));
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);
  if(!item.isEmpty()){var it=BuiltInRegistries.ITEM.get(new ResourceLocation(item));t.putInt("have",chest==null?0:LogisticsRoutes.count(chest,s->s.is(it)));}
  var research=new ListTag();for(var id:missingResearch(l,e,b.type(),next))research.add(StringTag.valueOf(id));t.put("research",research);
  int working=level(l,e,b);var effects=new ListTag();
  for(var effect:org.villageastra.domain.CoreEffects.effects(type)){var row=new CompoundTag();row.putString("id",effect.id());row.putBoolean("active",effect.active());
   if(effect.active()){row.putInt("now",effectNow(l,e,b,type,effect.id(),working));row.putInt("next",effectAt(l,e,b,type,effect.id(),next));}
   effects.add(row);}
  t.put("effects",effects);
  if(type.equals("farm")){var s=e.settlement();boolean legacy=FarmField.legacy(s);t.putInt("nowBuilt",FarmField.modules(s.fieldLevel(b.id()),s.westField(b.id()),legacy).size());t.putInt("nowWorked",FarmField.worked(l,e,b).size());t.putInt("nextBuilt",FarmField.modules(Math.max(next,s.fieldLevel(b.id())),s.westField(b.id()),legacy).size());}
  return t;
 }
}
