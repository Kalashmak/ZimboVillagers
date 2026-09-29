package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-123: the owner's housing ladder (AD-115: I — two residents per house, II — four, III–VI as the real beds allow).
 *  The numbers live in balance/housing.json. A house holds as many people as its capacity, and the capacity rises only when a finished
 *  project really placed the beds of its level (level_beds on); it never drops. Housing II opens the big house for new projects,
 *  Housing III shortens the time between births. */
public final class HousingLadder {
 private HousingLadder(){}
 private static final JsonObject ROOT=read();
 private static final Map<String,int[]> BEDS=beds();
 private static final boolean LEVEL_BEDS=ROOT.get("level_beds").getAsBoolean();
 /** Test hook: forces level_beds on or off; null reads the balance file. */
 public static volatile Boolean levelBedsOverride;
 private static JsonObject read(){try(var s=HousingLadder.class.getResourceAsStream("/data/villageastra/balance/housing.json")){if(s==null)throw new IllegalStateException("Missing housing balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static Map<String,int[]> beds(){var out=new LinkedHashMap<String,int[]>();for(var e:ROOT.getAsJsonObject("beds").entrySet()){var a=e.getValue().getAsJsonArray();var v=new int[a.size()];
   if(v.length!=BuildingTiers.MAX)throw new IllegalStateException("Housing beds need "+BuildingTiers.MAX+" levels: "+e.getKey());
   for(int i=0;i<v.length;i++){v[i]=a.get(i).getAsInt();if(v[i]<1||i>0&&v[i]<v[i-1])throw new IllegalStateException("Housing beds must not drop: "+e.getKey());}out.put(e.getKey(),v);}
  return Collections.unmodifiableMap(out);}
 /** OWNER-HOUSING-BEDS: a bed a house level adds to its level-I design — the foot cell, its head one step toward facing, entered from behind the foot. */
 public record LevelBed(int level,BlockPos foot,net.minecraft.core.Direction facing){
  public BlockPos head(){return foot.relative(facing);}
  public BlockPos approach(){return foot.relative(facing.getOpposite());}
  public net.minecraft.world.level.block.state.BlockState state(BedPart part){return net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,facing).setValue(BedBlock.PART,part);}
 }
 private static final Map<String,List<LevelBed>> LEVEL_BED_CELLS=levelBedCells();
 private static Map<String,List<LevelBed>> levelBedCells(){var out=new HashMap<String,List<LevelBed>>();
  for(var e:ROOT.getAsJsonObject("level_bed_cells").entrySet()){var list=new ArrayList<LevelBed>();
   for(var raw:e.getValue().getAsJsonArray()){var o=raw.getAsJsonObject();var f=o.getAsJsonArray("foot");var facing=net.minecraft.core.Direction.byName(o.get("facing").getAsString());
    if(facing==null||facing.getAxis().isVertical())throw new IllegalStateException("Bad bed facing in "+e.getKey());
    list.add(new LevelBed(o.get("level").getAsInt(),new BlockPos(f.get(0).getAsInt(),f.get(1).getAsInt(),f.get(2).getAsInt()),facing));}
   out.put(e.getKey(),List.copyOf(list));}
  return Map.copyOf(out);}
 private static final Map<String,List<BlockPos>> WAY_UP=wayUp();
 private static Map<String,List<BlockPos>> wayUp(){var out=new HashMap<String,List<BlockPos>>();
  for(var e:ROOT.getAsJsonObject("way_up").entrySet()){var list=new ArrayList<BlockPos>();for(var raw:e.getValue().getAsJsonArray()){var a=raw.getAsJsonArray();list.add(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));}out.put(e.getKey(),List.copyOf(list));}
  return Map.copyOf(out);}
 /** Cells of a house design that stay free at every level so its people reach their beds (the way to the big house's stair). */
 public static List<BlockPos> wayUp(String type){return WAY_UP.getOrDefault(type,List.of());}
 /** Beds levels II…VI of a house design add (none for any other design). */
 public static List<LevelBed> levelBedCells(String type){return LEVEL_BED_CELLS.getOrDefault(type,List.of());}
 /** AD-143: the local cell a new resident of a house appears in (the n-th of its people): free floor under open air. The cottage's front row
  *  z2 is its wall — its door jambs are posts since AD-143 — so its people appear in the lane before its door (3,1,1|0), which no
  *  level's equipment takes; in any other house on its ground floor behind the front wall (z2). */
 public static BlockPos spawnCell(String type,int n){return BuildingBlueprints.base(type).equals("home")?new BlockPos(3,1,1-n%2):new BlockPos(2+(n%2)*2,1,2);}
 public static boolean levelBeds(){var o=levelBedsOverride;return o!=null?o:LEVEL_BEDS;}
 /** The research a housing design needs before it may be ordered, or null for the catalogue's own gate. */
 public static String designResearch(String design){var d=ROOT.getAsJsonObject("design_research");var base=BuildingBlueprints.base(design);return d.has(base)?d.get(base).getAsString():null;}
 /** Residents a house of this type holds at a level, from the ladder (the beds its level design is to have). */
 public static int capacity(String type,int level){var v=BEDS.get(BuildingBlueprints.base(type));if(v==null)throw new IllegalArgumentException("Not a house: "+type);return v[Math.max(1,Math.min(v.length,level))-1];}
 /** Active ticks between births: the Housing III interval once that research is done, otherwise the base. */
 public static long birthInterval(ServerLevel l,SettlementData.Entry e){var done=ResearchKnobs.done(l,e);long best=Population.BIRTH_INTERVAL;
  for(var r:ROOT.getAsJsonObject("birth_interval_research").entrySet())if(done.contains(r.getKey()))best=Math.min(best,r.getValue().getAsLong());return best;}
 public static long birthIntervalWith(String node){var r=ROOT.getAsJsonObject("birth_interval_research");return r.has(node)?r.get(node).getAsLong():Population.BIRTH_INTERVAL;}
 /** A bed foot whose head stands beside it: one whole bed. */
 public static boolean wholeFoot(net.minecraft.world.level.Level l,BlockPos pos,net.minecraft.world.level.block.state.BlockState s){
  if(!(s.getBlock() instanceof BedBlock)||s.getValue(BedBlock.PART)!=BedPart.FOOT)return false;var head=l.getBlockState(pos.relative(s.getValue(BedBlock.FACING)));
  return head.getBlock() instanceof BedBlock&&head.getValue(BedBlock.PART)==BedPart.HEAD;
 }
 private static boolean whole(net.minecraft.world.level.Level l,BlockPos pos,net.minecraft.world.level.block.state.BlockState s,BedPart part){
  if(part==BedPart.FOOT)return wholeFoot(l,pos,s);
  if(!(s.getBlock() instanceof BedBlock)||s.getValue(BedBlock.PART)!=BedPart.HEAD)return false;var foot=l.getBlockState(pos.relative(s.getValue(BedBlock.FACING).getOpposite()));
  return foot.getBlock() instanceof BedBlock&&foot.getValue(BedBlock.PART)==BedPart.FOOT;
 }
 /** Cells (FOOT or HEAD) of the whole beds really standing in a building's footprint box; null while a part of it is unloaded. */
 public static List<BlockPos> beds(ServerLevel l,SettlementData.Entry e,Settlement.Building b,BedPart part){
  var origin=BuildingPlacement.origin(e,b);var size=BuildingPlacement.size(b.type(),b.rotation());int top=0;
  for(var p:BuildingBlueprints.layout(BuildingTiers.layoutId(b.type(),Math.max(1,b.level())),BlockPos.ZERO).keySet())top=Math.max(top,p.getY());
  var out=new ArrayList<BlockPos>();
  for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++){var column=origin.offset(x,0,z);if(!l.hasChunkAt(column))return null;
   for(int y=0;y<=top;y++){var pos=column.above(y);var s=l.getBlockState(pos);if(whole(l,pos,s,part))out.add(pos);}}
  out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY));
  return out;
 }
 /** H1: after a finished upgrade (or a repair that put a level's beds back) the house holds min(ladder, beds really standing); never fewer than before. */
 public static int upgraded(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level){
  if(!levelBeds()||!BuildingOrders.HOUSING.contains(b.type()))return -1;var s=e.settlement();
  var home=s.homes().stream().filter(h->h.id().equals(b.id())).findFirst().orElse(null);if(home==null)return -1;
  var feet=beds(l,e,b,BedPart.FOOT);if(feet==null)return home.capacity();
  int cap=Math.min(capacity(b.type(),level),feet.size());
  if(cap>home.capacity()){s.upgradeHome(b.id(),cap);SettlementData.get(l.getServer()).setDirty();}
  return Math.max(cap,home.capacity());
 }
 /** H4 (C7): the design an NPC mayor builds for housing: the big house once the hall allows it and its research is done, else the standard house. */
 public static String houseFor(ServerLevel l,SettlementData.Entry e){
  return e.settlement().civilization().level()>=2&&ResearchGate.designRefusal(l,e,"home_2").isEmpty()?"home_2":"home";
 }
 private static Component text(String key,Object... args){return Component.translatable("research.villageastra.fact.housing."+key,args);}
 /** Card lines of the housing ladder for one node (AD-123 §1); only lines that are true today. */
 public static void facts(String id,List<Component> out){
  var n=ResearchCatalog.get(id);if(!n.branch().equals("housing"))return;int t=n.tier();
  for(var d:ROOT.getAsJsonObject("design_research").entrySet())if(d.getValue().getAsString().equals(id)){
   out.add(ResearchEffects.tagged(text("design",Component.translatable("building.villageastra."+d.getKey()),BuildingOrders.capacity(d.getKey())),"new"));}
  var birth=ROOT.getAsJsonObject("birth_interval_research");
  if(birth.has(id)){long after=birth.get(id).getAsLong();out.add(ResearchEffects.tagged(text("births",Population.BIRTH_INTERVAL,after,String.format(Locale.ROOT,"%.1f",Population.BIRTH_INTERVAL/24000.0),String.format(Locale.ROOT,"%.1f",after/24000.0)),"existing"));}
  // Level unlocks of the house (research of level t+1 is this node): capacity lines only when the ladder is live, else "unchanged".
  for(var type:BEDS.keySet())for(int level=2;level<=BuildingTiers.MAX;level++){if(!BuildingTiers.research(type,level).contains(id))continue;
   int before=capacity(type,level-1),after=capacity(type,level);
   if(levelBeds()&&after>before){out.add(ResearchEffects.tagged(text("capacity",Component.translatable("building.villageastra."+type),level,before,after),"rebuild"));}
   else if(type.equals("home"))out.add(text("capacity_unchanged",Component.translatable("building.villageastra."+type),level,BuildingOrders.capacity(BuildingTiers.layoutId(type,level))));}
 }
}
