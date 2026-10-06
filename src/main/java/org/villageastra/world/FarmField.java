package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-104: the one definition of a farm's field, for the farmer, the machine, protection, sieges, world generation and the farm card.
 *  The field is made of 9x9 modules ("fields"), each with one water source at its centre, so every one of its 80 plots is hydrated. A module
 *  [mx,mz,floor] lies at farm-local x origin.x+9mx..+8, z origin.z+9mz..+8, y floor_pitch*floor, behind the farmhouse; plots are the cells one
 *  block above its ground. Positions go through BuildingPlacement, so a turned farm turns its field with it.
 *  AD-130 (owner 2026-09-22): fields per level 1/4/6/12/18/18 — a 2x3 on the ground, then two more floors of 2x3 in the barn (FarmBarn).
 *  Villages laid before layout 6 (Settlement.lotLayout &lt; OrganicLots.BARN_LOTS) keep the AD-104 table (legacy_levels) and work their old land. */
public final class FarmField {
 private FarmField(){}
 private static final JsonObject ROOT=root();
 public static final int MODULE=ROOT.get("module").getAsInt(),ORIGIN_X=ROOT.getAsJsonArray("origin").get(0).getAsInt(),ORIGIN_Z=ROOT.getAsJsonArray("origin").get(1).getAsInt(),
  WATER_X=ROOT.getAsJsonArray("water").get(0).getAsInt(),WATER_Z=ROOT.getAsJsonArray("water").get(1).getAsInt(),RATING=ROOT.get("rating_divisor").getAsInt(),
  MODULE_SOIL=ROOT.get("module_soil").getAsInt(),TNT_PER_MODULE=ROOT.get("tnt_per_module").getAsInt(),
  /** AD-112: the legacy field level world generation and lots keep clear around a farm of a village laid before layout 6. */
  RESERVED=ROOT.get("reserved_level").getAsInt(),
  /** AD-130: blocks from one floor's ground to the next; the first level whose fields stand in the barn; dirt one upper field costs. */
  FLOOR_PITCH=ROOT.get("floor_pitch").getAsInt(),BARN_FROM=ROOT.get("barn_from").getAsInt(),UPPER_SOIL=ROOT.get("upper_soil_per_module").getAsInt(),
  /** AD-130: the most fields a farm has (18) — the per-field crop choice has one entry for each. */
  FIELDS=18;
 /** AD-112: the one item a new module costs, the slab over its water; its ground and water are the builders' own work. */
 public static final String COVER_ITEM=ROOT.get("cover_item").getAsString(),
  /** AD-130: what an upper field's ground is laid of (1 per plot): the barn deck carries no natural earth. */
  UPPER_SOIL_ITEM=ROOT.get("upper_soil_item").getAsString();
 private static final Map<Integer,List<int[]>> ADDED=added("levels",true),LEGACY=added("legacy_levels",false);
 private static final List<int[]> AIMS=aims(),LANTERNS=cells("lantern_cells");
 private static final Map<Integer,Integer> TARGETS=targets();
 private static JsonObject root(){try(var s=FarmField.class.getResourceAsStream("/data/villageastra/balance/farm_fields.json")){if(s==null)throw new IllegalStateException("Missing farm field balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static Map<Integer,List<int[]>> added(String key,boolean floors){
  var out=new TreeMap<Integer,List<int[]>>();var seen=new HashSet<String>();var levels=ROOT.getAsJsonObject(key);int[] perFloor=new int[3];
  for(int level=1;level<=6;level++){var list=levels.getAsJsonArray(String.valueOf(level));if(list==null)throw new IllegalStateException("Farm field level "+level+" missing in "+key);
   var mods=new ArrayList<int[]>();for(var raw:list){var a=raw.getAsJsonArray();var m=new int[]{a.get(0).getAsInt(),a.get(1).getAsInt(),a.size()>2?a.get(2).getAsInt():0};
    if(m[0]<0||m[1]<0||m[2]<0||m[2]>2||!floors&&m[2]!=0||!seen.add(m[0]+","+m[1]+","+m[2]))throw new IllegalStateException("Farm field modules must be new and non-negative: "+Arrays.toString(m));
    // AD-130: an upper field stands over a field of the floor below (the deck is carried by the barn over the ground 2x3).
    if(m[2]>0&&!seen.contains(m[0]+","+m[1]+","+(m[2]-1)))throw new IllegalStateException("Upper field without a field below: "+Arrays.toString(m));
    perFloor[m[2]]++;mods.add(m);}
   out.put(level,List.copyOf(mods));}
  if(out.get(1).size()!=1||out.get(1).get(0)[0]!=0||out.get(1).get(0)[1]!=0||out.get(1).get(0)[2]!=0)throw new IllegalStateException("Farm field level I is module (0,0)");
  if(floors&&(perFloor[0]!=6||perFloor[1]!=6||perFloor[2]!=6))throw new IllegalStateException("AD-130: every floor holds 6 fields");
  if(floors&&seen.size()!=FIELDS)throw new IllegalStateException("AD-130: a farm has "+FIELDS+" fields");
  if(WATER_X<1||WATER_X>=MODULE-1||WATER_Z<1||WATER_Z>=MODULE-1)throw new IllegalStateException("Farm field water must lie inside its module");
  if(RESERVED<1||RESERVED>6)throw new IllegalStateException("Farm field reserved level must be I..VI");
  if(UPPER_SOIL!=MODULE*MODULE-1)throw new IllegalStateException("An upper field takes one soil a plot");
  return out;
 }
 private static List<int[]> cells(String key){var out=new ArrayList<int[]>();for(var raw:ROOT.getAsJsonArray(key)){var a=raw.getAsJsonArray();var c=new int[]{a.get(0).getAsInt(),a.get(1).getAsInt()};
  if(c[0]<0||c[0]>=MODULE||c[1]<0||c[1]>=MODULE||c[0]==WATER_X&&c[1]==WATER_Z)throw new IllegalStateException("A lantern hangs over a plot of its field");out.add(c);}return List.copyOf(out);}
 private static List<int[]> aims(){var out=new ArrayList<int[]>();for(var raw:ROOT.getAsJsonArray("aims")){var a=raw.getAsJsonArray();var d=new int[]{a.get(0).getAsInt(),a.get(1).getAsInt()};
  if(d[0]==0&&d[1]==0||Math.abs(d[0])>4||Math.abs(d[1])>4)throw new IllegalStateException("A siege aim must be a plot of the module, not its water");out.add(d);}return List.copyOf(out);}
 private static Map<Integer,Integer> targets(){var out=new TreeMap<Integer,Integer>();var t=ROOT.getAsJsonObject("targets");for(int level=1;level<=6;level++)out.put(level,t.get(String.valueOf(level)).getAsInt());return out;}
 /** AD-130: farm-local bounds {minX,minZ,maxX,maxZ} of the barn's final footprint on its east side (OrganicLots keeps the same). */
 public static int[] reserve(){var a=ROOT.getAsJsonArray("reserve");return new int[]{a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt()};}
 /** AD-130: whether a village keeps the AD-104 field table — one laid before layout 6 (its lots have no room for the barn). */
 public static boolean legacy(Settlement s){return s.lotLayout()<org.villageastra.domain.OrganicLots.BARN_LOTS;}
 private static final int[] FARMERS=ROOT.getAsJsonArray("farmers").asList().stream().mapToInt(JsonElement::getAsInt).toArray();
 /** AD-130: the farmers a farm kept at this level posts: 1/1/1/2/3 and none at VI, where the machine works every field. */
 public static int farmers(int level){return FARMERS[Math.max(1,Math.min(6,level))-1];}
 /** AD-130: wheat a day these modules grow at a randomTickSpeed — every floor on its own (a deck parts them), by vanilla's crop rule. */
 public static double wheatPerDay(List<int[]> modules,int randomTickSpeed){
  double out=0;for(int f:floors(modules)){var plots=new ArrayList<int[]>();for(var m:modules)if(floor(m)==f)for(var p:localCells(List.of(m)))plots.add(new int[]{p.getX(),p.getZ()});
   out+=org.villageastra.domain.FarmYield.wheatPerDay(plots,randomTickSpeed);}
  return out;
 }
 /** AD-130: residents a farm of this level is rated to feed on wheat at the default randomTickSpeed (3): the core's "feeds" effect. */
 public static int feeds(int level){return org.villageastra.domain.FarmYield.ratedResidents(wheatPerDay(modules(level),3),RATING);}
 /** Residents the owner wants a farm of this level to feed (a minimum: AD-130 fields feed at least this). */
 public static int target(int level){return TARGETS.get(Math.max(1,Math.min(6,level)));}
 private static Map<Integer,List<int[]>> table(boolean legacy){return legacy?LEGACY:ADDED;}
 /** Every module a farm of this level has, in the order they were added (AD-130 table). */
 public static List<int[]> modules(int level){return modules(level,false,false);}
 /** AD-112: the same on a side — a west module (mx,mz) is stored as (-mx,mz), so corner() mirrors it past the farmhouse's west wall. */
 public static List<int[]> modules(int level,boolean west){return modules(level,west,false);}
 public static List<int[]> modules(int level,boolean west,boolean legacy){var out=new ArrayList<int[]>();for(int l=1;l<=Math.max(1,Math.min(6,level));l++)out.addAll(side(table(legacy).get(l),west));return out;}
 private static List<int[]> side(List<int[]> mods,boolean west){var out=new ArrayList<int[]>();for(var m:mods)out.add(west?new int[]{-m[0],m[1],m[2]}:m);return out;}
 /** The modules one level adds, on a side (none below II or above VI). */
 public static List<int[]> added(int level,boolean west){return added(level,west,false);}
 public static List<int[]> added(int level,boolean west,boolean legacy){if(level<1||level>6)return List.of();return side(table(legacy).get(level),west);}
 /** The floor a module stands on (0 the ground). */
 public static int floor(int[] m){return m.length>2?m[2]:0;}
 /** AD-130: the number (1..18) of a field — floor-major, in the order the table adds them: 1=(0,0) 2=(1,0) 3=(0,1) 4=(1,1) 5=(0,2) 6=(1,2),
  *  7..12 the same on floor 1, 13..18 on floor 2. A legacy module takes its place in the legacy table's order. 0: none. */
 public static int number(int[] m,boolean legacy){var all=modules(6,false,legacy);for(int i=0;i<all.size();i++){var a=all.get(i);if(a[0]==Math.abs(m[0])&&a[1]==m[1]&&floor(a)==floor(m))return i+1;}return 0;}
 /** AD-130: the module of a field number on a side (null outside the table). */
 public static int[] field(int number,boolean west,boolean legacy){var all=modules(6,west,legacy);return number<1||number>all.size()?null:all.get(number-1);}
 /** The level that lays a field number (1..6), or 0. */
 public static int levelOf(int number,boolean legacy){int n=0;for(int l=1;l<=6;l++){n+=table(legacy).get(l).size();if(number>=1&&number<=n)return l;}return 0;}
 /** The land of this farm: every module its builders have laid (Settlement.fieldLevel, on its side) — protection, sieges' land, clearing
  *  and the survey's under-building check. */
 public static List<int[]> modules(SettlementData.Entry e,Settlement.Building b){var s=e.settlement();return modules(s.fieldLevel(b.id()),s.westField(b.id()),legacy(s));}
 /** AD-112: the modules this farm works — its working level (capped by its core and, AD-130, by the lanterns and machinery of its barn)
  *  within the land that exists. */
 public static List<int[]> worked(ServerLevel l,SettlementData.Entry e,Settlement.Building b){var s=e.settlement();return modules(Math.min(BuildingTiers.level(l,e,b),s.fieldLevel(b.id())),s.westField(b.id()),legacy(s));}
 /** AD-130: the floors among these modules, lowest first. */
 public static List<Integer> floors(List<int[]> modules){var out=new TreeSet<Integer>();for(var m:modules)out.add(floor(m));return List.copyOf(out);}
 /** The plots this farm works, in the world. */
 public static List<BlockPos> workedCells(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return world(e,b,localCells(worked(l,e,b)));}
 /** The plots every farm of the settlement works. */
 public static List<BlockPos> workedCells(ServerLevel l,SettlementData.Entry e){var out=new ArrayList<BlockPos>();for(var b:e.settlement().buildings())if(b.type().equals("farm"))out.addAll(workedCells(l,e,b));return List.copyOf(out);}
 /** AD-130: the share of the worked modules a farmer of rank {@code rank} among {@code farmers} works: with no more farmers than worked floors, the
  *  floors whose index modulo farmers is his rank; with more, the farmers of one floor split its modules by module index. Two farmers never
  *  share a module; a rank below 0 (not one of the farm's farmers) works them all. */
 public static List<int[]> share(List<int[]> worked,int rank,int farmers){
  if(farmers<=1||rank<0)return worked;var floors=floors(worked);var out=new ArrayList<int[]>();int n=floors.size();if(n==0)return out;
  if(farmers<=n){for(int i=0;i<n;i++)if(i%farmers==rank%farmers)for(var m:worked)if(floor(m)==floors.get(i))out.add(m);return out;}
  int f=floors.get(rank%n);int onFloor=0;for(int j=0;j<farmers;j++)if(j%n==rank%n)onFloor++;int q=rank/n,index=0;
  for(var m:worked)if(floor(m)==f){if(index%onFloor==q)out.add(m);index++;}
  return out;
 }
 /** Working farmers in posting order. A resting colleague leaves his fields to the healthy crew. */
 public static List<UUID> farmers(Settlement s,Settlement.Building b){return s.employees(b.id()).stream().filter(id->{var r=s.resident(id);return r!=null&&r.alive()&&r.life()==org.villageastra.domain.Resident.Life.ADULT&&r.profession()==org.villageastra.domain.Profession.FARMER&&Population.mayWork(r);}).toList();}
 /** AD-130: the plots one farmer works — his own farm's worked modules, his share of them by rank; all of them when he is not posted here. */
 public static List<BlockPos> workedCells(ServerLevel l,SettlementData.Entry e,Settlement.Building b,UUID farmer){
  var crew=farmers(e.settlement(),b);return world(e,b,localCells(share(worked(l,e,b),crew.indexOf(farmer),crew.size())));}
 private static List<BlockPos> world(SettlementData.Entry e,Settlement.Building b,List<BlockPos> local){var out=new ArrayList<BlockPos>(local.size());for(var p:local)out.add(BuildingPlacement.at(e,b,p.getX(),p.getY(),p.getZ()));return out;}
 /** What the new ground modules of one level cost: a cover slab each; equals the field cost of a survey that lays that level alone. The upper
  *  floors' soil, water and slabs are laid and paid with the barn (FarmBarn.addedCost). */
 public static Map<String,Integer> addedCost(int level){
  var out=new TreeMap<String,Integer>();if(level<2)return out;
  for(var m:added(level,false))if(floor(m)==0)out.merge(COVER_ITEM,1,Integer::sum);
  return out;
 }
 /** Farm-local corner of a module (its ground, y floor_pitch*floor). */
 public static BlockPos corner(int[] m){return new BlockPos(ORIGIN_X+MODULE*m[0],FLOOR_PITCH*floor(m),ORIGIN_Z+MODULE*m[1]);}
 /** Farm-local water source of a module (its ground level). */
 public static BlockPos localWater(int[] m){return corner(m).offset(WATER_X,0,WATER_Z);}
 /** AD-130: farm-local cells of the lanterns hung over a module's plots (from the deck above; y = its ground + 3). */
 public static List<BlockPos> localLanterns(int[] m){var c=corner(m);var out=new ArrayList<BlockPos>();for(var a:LANTERNS)out.add(c.offset(a[0],3,a[1]));return out;}
 /** Farm-local plots (one above the ground) of these modules: every cell of each module but its water. */
 public static List<BlockPos> localCells(List<int[]> modules){
  var out=new ArrayList<BlockPos>();
  for(var m:modules){var c=corner(m);var w=localWater(m);for(int x=0;x<MODULE;x++)for(int z=0;z<MODULE;z++){var g=c.offset(x,0,z);if(!g.equals(w))out.add(g.above());}}
  return out;
 }
 /** The plots of this farm in the world, one block above their ground. */
 public static List<BlockPos> cells(SettlementData.Entry e,Settlement.Building b){return world(e,b,localCells(modules(e,b)));}
 /** The plots of every farm of the settlement. */
 public static List<BlockPos> cells(SettlementData.Entry e){var out=new ArrayList<BlockPos>();for(var b:e.settlement().buildings())if(b.type().equals("farm"))out.addAll(cells(e,b));return List.copyOf(out);}
 /** The water sources of this farm in the world (their ground level). */
 public static List<BlockPos> water(SettlementData.Entry e,Settlement.Building b){var out=new ArrayList<BlockPos>();for(var m:modules(e,b)){var w=localWater(m);out.add(BuildingPlacement.at(e,b,w.getX(),w.getY(),w.getZ()));}return out;}
 /** Whether a farm-local cell lies over one of these modules in plan (at any height). */
 public static boolean inModules(List<int[]> modules,BlockPos local){return module(modules,local.getX(),local.getZ(),Integer.MIN_VALUE)!=null;}
 /** The module whose columns hold (x,z) with its ground at y (MIN_VALUE: any floor), or null. */
 private static int[] module(List<int[]> modules,int x,int z,int y){
  for(var m:modules){var c=corner(m);if(x>=c.getX()&&x<c.getX()+MODULE&&z>=c.getZ()&&z<c.getZ()+MODULE&&(y==Integer.MIN_VALUE||y==c.getY()))return m;}
  return null;
 }
 /** AD-130: the module of this farm a world position is a plot of (one above a field's ground, not over its water), or null. */
 public static int[] moduleAt(SettlementData.Entry e,Settlement.Building b,BlockPos world){
  var local=BuildingPlacement.local(e,b,world);var m=module(modules(e,b),local.getX(),local.getZ(),local.getY()-1);
  return m==null||localWater(m).above().equals(local)?null:m;
 }
 /** Whether a world position is a plot of this farm: one block above a field's ground, in a module, not over its water. */
 public static boolean contains(SettlementData.Entry e,Settlement.Building b,BlockPos world){return moduleAt(e,b,world)!=null;}
 /** Whether a world position is the field's ground (farmland or water) of this farm — the settlement's land, protected. */
 public static boolean ground(SettlementData.Entry e,Settlement.Building b,BlockPos world){var local=BuildingPlacement.local(e,b,world);return module(modules(e,b),local.getX(),local.getZ(),local.getY())!=null;}
 /** Farm-local bounds {minX,minZ,maxX,maxZ} of these modules. */
 public static int[] box(List<int[]> modules){
  int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
  for(var m:modules){var c=corner(m);minX=Math.min(minX,c.getX());minZ=Math.min(minZ,c.getZ());maxX=Math.max(maxX,c.getX()+MODULE-1);maxZ=Math.max(maxZ,c.getZ()+MODULE-1);}
  return new int[]{minX,minZ,maxX,maxZ};
 }
 private static List<BlockPos> aims(SettlementData.Entry e,Settlement.Building b,List<int[]> modules){
  var out=new ArrayList<BlockPos>();for(var m:modules){var w=localWater(m);for(var a:AIMS)out.add(BuildingPlacement.at(e,b,w.getX()+a[0],w.getY()+1,w.getZ()+a[1]));}return out;
 }
 /** Where a siege places its charges on this farm: plots two blocks diagonal from each module's water, never on or over the water. */
 public static List<BlockPos> aims(SettlementData.Entry e,Settlement.Building b){return aims(e,b,modules(e,b));}
 /** AD-112: the siege's view of a working farm — aims, soil and charges over the modules it works (3 TNT a field: 54 at V/VI, AD-130),
  *  on each field's own floor. */
 public static List<BlockPos> aims(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return aims(e,b,worked(l,e,b));}
 public static int workingSoil(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return MODULE_SOIL*worked(l,e,b).size();}
 public static int charges(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return TNT_PER_MODULE*worked(l,e,b).size();}
 /** The soil a siege counts for this farm to still be working: MODULE_SOIL per module. */
 public static int workingSoil(SettlementData.Entry e,Settlement.Building b){return MODULE_SOIL*modules(e,b).size();}
 /** Charges a siege needs for this farm: TNT_PER_MODULE per module. */
 public static int charges(SettlementData.Entry e,Settlement.Building b){return TNT_PER_MODULE*modules(e,b).size();}
 /** What stands over a module's water: a slab, as players cover a farm's water — ice forms only on open water, so the field stays wet in
  *  a snowy biome (the world generator freezes open water after the village is laid, and snowfall freezes it later), and nobody steps in. */
 public static final BlockState COVER=Blocks.OAK_SLAB.defaultBlockState();
 /** Layers of air laid over the field (y 2..HEADROOM): no bush, drift, bank or low branch stands on the plots or shades them. */
 public static final int HEADROOM=3;
 /** The field as the world generator lays it for a farm standing unturned at base: moist farmland, one covered water source per module and
  *  wheat of mixed unripe ages (seeded, so the same village always grows the same field), like a vanilla village farm, with open air above. */
 public static Map<BlockPos,BlockState> layout(BlockPos base,List<int[]> modules,long seed){
  var out=new LinkedHashMap<BlockPos,BlockState>();var random=new Random(seed);
  for(var m:modules){var c=corner(m);var w=localWater(m);
   for(int x=0;x<MODULE;x++)for(int z=0;z<MODULE;z++){var g=c.offset(x,0,z);
    if(g.equals(w)){out.put(base.offset(g),Blocks.WATER.defaultBlockState());out.put(base.offset(g).above(),COVER);}
    else{out.put(base.offset(g),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,FarmBlock.MAX_MOISTURE));
     out.put(base.offset(g).above(),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,random.nextInt(7)));}
    for(int y=2;y<=HEADROOM;y++)out.put(base.offset(g).above(y),Blocks.AIR.defaultBlockState());}}
  return out;
 }
 /** Farm-local columns (their ground y) of every module of these modules, water included. */
 public static List<BlockPos> localColumns(List<int[]> modules){var out=new ArrayList<BlockPos>();for(var m:modules){var c=corner(m);for(int x=0;x<MODULE;x++)for(int z=0;z<MODULE;z++)out.add(c.offset(x,0,z));}return out;}
 /** AD-112 (AD-104 P4): the field work of an upgrade — ops in BuildingOrders' op NBT (each flagged field), the items they cost, the cells
  *  in the way, the reason ("" when it can be laid, "unloaded", "field") and the side the new modules take. */
 public record FieldPlan(ListTag ops,Map<String,Integer> cost,Set<BlockPos> conflicts,String reason,boolean west){public boolean ok(){return reason.isEmpty()&&conflicts.isEmpty();}}
 /** Whether some module of these levels lies east or west of the farmhouse's own column. */
 private static boolean sided(int level,boolean legacy){for(var m:modules(level,false,legacy))if(m[0]!=0)return true;return false;}
 /** The field work to bring this farm's land up to level: the side is chosen at the first level whose new modules leave the farmhouse's
  *  column (east unless east is in the way and west is not) and kept from then on. AD-130: only the ground floor's fields are laid here;
  *  the upper floors are laid with the barn that carries them (FarmBarn.plan). */
 public static FieldPlan plan(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level){
  var s=e.settlement();boolean west=s.westField(b.id()),legacy=legacy(s);
  if(west||sided(s.fieldLevel(b.id()),legacy)||!sided(level,legacy))return survey(l,e,b,level,west);
  var east=survey(l,e,b,level,false);if(east.ok()||!east.reason().equals("field"))return east;
  var other=survey(l,e,b,level,true);return other.ok()?other:east;
 }
 /** Soil the farmer prepares as it is. */
 private static boolean soil(BlockState s){return s.is(Blocks.DIRT)||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.FARMLAND);}
 /** What the builders clear from the plots' air or level into the ground without a word: plants, snow, earth, stone, sand and gravel. */
 static boolean natural(BlockState s){
  return s.isAir()||s.canBeReplaced()||s.is(BlockTags.LEAVES)||s.is(BlockTags.FLOWERS)||s.is(BlockTags.SAPLINGS)||s.is(BlockTags.CROPS)||s.is(Blocks.SNOW)||s.is(Blocks.SNOW_BLOCK)
   ||s.is(BlockTags.DIRT)||s.is(Blocks.FARMLAND)||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL);
 }
 /** The survey of every ground module of levels fieldLevel+1..level on a side. y 1..HEADROOM: natural blocks become air, top-down. y 0: soil stays,
  *  anything else natural becomes dirt levelled from the cleared earth (no item). The water cell gets a dirt support if it would run
  *  down, water (dug to the water table, no item) and the slab cover (COVER_ITEM). Order: clears, supports, ground, water, cover.
  *  In the way: an unloaded chunk, another building's ground or lot (this farm's own field box excepted: from V it already holds VI's
  *  modules), a road, a block entity, a fluid outside the water cell, anything not natural at y 0..HEADROOM, or an opaque solid other
  *  than leaves up to 8 blocks above the headroom. */
 public static FieldPlan survey(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level,boolean west){
  return survey(l,e,b,level,west,e.settlement().fieldLevel(b.id()));
 }
 /** The first field of a paid new farmhouse is surveyed before registration. */
 public static FieldPlan initial(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return survey(l,e,b,1,false,0);}
 private static FieldPlan survey(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level,boolean west,int from){
  var conflicts=new LinkedHashSet<BlockPos>();var none=new FieldPlan(new ListTag(),Map.of(),conflicts,"",west);
  if(level<=from)return none;boolean legacy=legacy(e.settlement());
  var box=box(modules(e,b));int wide=BuildingPlacement.size(b.type(),0)[0];
  record Op(BlockPos pos,BlockState before,BlockState after,String item){}
  var clears=new ArrayList<Op>();var supports=new ArrayList<Op>();var ground=new ArrayList<Op>();var water=new ArrayList<Op>();var cover=new ArrayList<Op>();
  var air=Blocks.AIR.defaultBlockState();var dirt=Blocks.DIRT.defaultBlockState();
  for(int lv=from+1;lv<=level;lv++)for(var m:added(lv,west,legacy)){if(floor(m)>0)continue;var wet=localWater(m);
   for(var column:localColumns(List.of(m))){
    var g=BuildingPlacement.at(e,b,column.getX(),0,column.getZ());boolean spring=column.equals(wet);
    for(int y=-1;y<=HEADROOM+8;y++)if(!l.hasChunkAt(g.above(y))||l.isOutsideBuildHeight(g.above(y)))return new FieldPlan(new ListTag(),Map.of(),conflicts,"unloaded",west);
    boolean own=column.getX()>=Math.min(0,box[0])&&column.getX()<=Math.max(wide-1,box[2])&&column.getZ()>=0&&column.getZ()<=box[3];
    if(!own&&org.villageastra.server.MayorSurvey.underBuilding(l,g)||Roads.cell(l,g)!=null||Roads.cell(l,g.above())!=null){conflicts.add(g);continue;}
    boolean blocked=false;
    for(int y=0;y<=HEADROOM+8&&!blocked;y++){var p=g.above(y);var now=l.getBlockState(p);
     if(y<=HEADROOM)blocked=l.getBlockEntity(p)!=null||!now.getFluidState().isEmpty()&&!(spring&&y==0)||!natural(now)&&!(spring&&y==0&&now.getFluidState().isSource()&&now.is(Blocks.WATER))&&!(spring&&y==1&&now.equals(COVER));
     else blocked=!now.isAir()&&!now.is(BlockTags.LEAVES)&&now.canOcclude();
     if(blocked)conflicts.add(p);}
    if(blocked)continue;
    for(int y=HEADROOM;y>=1;y--){var p=g.above(y);var now=l.getBlockState(p);if(!now.isAir()&&!(spring&&y==1&&now.equals(COVER)))clears.add(new Op(p,now,air,""));}
    var now=l.getBlockState(g);
    if(spring){var below=l.getBlockState(g.below());
     if(below.getFluidState().isEmpty()&&(below.isAir()||below.canBeReplaced()||!below.isFaceSturdy(l,g.below(),Direction.UP))&&l.getBlockEntity(g.below())==null)supports.add(new Op(g.below(),below,dirt,""));
     if(!(now.is(Blocks.WATER)&&now.getFluidState().isSource()))water.add(new Op(g,now,Blocks.WATER.defaultBlockState(),""));
     if(!l.getBlockState(g.above()).equals(COVER))cover.add(new Op(g.above(),air,COVER,COVER_ITEM));}
    else if(!soil(now))ground.add(new Op(g,now,dirt,""));
   }}
  if(!conflicts.isEmpty())return new FieldPlan(new ListTag(),Map.of(),conflicts,"field",west);
  clears.sort(Comparator.<Op>comparingInt(o->-o.pos().getY()).thenComparingInt(o->o.pos().getZ()).thenComparingInt(o->o.pos().getX()));
  var ops=new ListTag();var cost=new LinkedHashMap<String,Integer>();
  for(var list:List.of(clears,supports,ground,water,cover))for(var op:list){
   var t=new CompoundTag();t.putLong("pos",op.pos().asLong());t.put("before",NbtUtils.writeBlockState(op.before()));t.put("after",NbtUtils.writeBlockState(op.after()));t.putBoolean("field",true);
   if(!op.item().isEmpty()){t.putString("item",op.item());cost.merge(op.item(),1,Integer::sum);}ops.add(t);}
  return new FieldPlan(ops,Map.copyOf(cost),conflicts,"",west);
 }
}
