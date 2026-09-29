package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.Settlement;
import org.villageastra.server.*;
/** AD-131: which trees round his hut are the forester's, and which of them he fells next. A tree is his when it grows wild: a log foot on soil
 *  with no liquid at it, at most MAX_LOGS logs, natural (non-persistent) leaves round it, nothing built touching it, no cell of it on any
 *  village's ground or within three blocks of a lot, not planted by a player or for show, and its foot within the radius of his level from
 *  the hut door, in a loaded chunk. The search walks a spiral of columns from the door, SCAN_COLUMNS a call, and keeps the feet it finds in
 *  memory (a restart only searches again). */
public final class ForestWork {
 private ForestWork(){}
 /** The kinds a forester plants, in the order a tie of the mix is broken by (the mangrove never: it wants a swamp, not a wood). */
 public static final List<String> PLANTED=List.of("minecraft:oak_sapling","minecraft:birch_sapling","minecraft:spruce_sapling","minecraft:jungle_sapling",
  "minecraft:acacia_sapling","minecraft:cherry_sapling","minecraft:dark_oak_sapling");
 public record Tree(BlockPos foot,List<BlockPos> logs,List<BlockPos> leaves,List<BlockPos> base,Item sapling){}
 /** The sapling of a tree by its log (null: no forester's kind — mangrove, the nether stems, bamboo). */
 public static Item sapling(BlockState log){
  var key=BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath().replace("stripped_","");
  for(var kind:List.of("oak","birch","spruce","jungle","acacia","cherry","dark_oak"))if(key.equals(kind+"_log")||key.equals(kind+"_wood"))return BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft",kind+"_sapling"));
  return null;
 }
 /** The planks a log saws into, of its own kind (null: not a log the saw takes). */
 public static Item planks(Item log){
  var key=BuiltInRegistries.ITEM.getKey(log).getPath().replace("stripped_","");String kind=null;
  for(var suffix:List.of("_log","_wood","_stem","_hyphae"))if(key.endsWith(suffix)){kind=key.substring(0,key.length()-suffix.length());break;}
  if(key.equals("bamboo_block"))kind="bamboo";
  if(kind==null)return null;var planks=BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft",kind+"_planks"));return planks==Items.AIR?null:planks;
 }
 private static boolean mangrove(BlockState s){return s.is(Blocks.MANGROVE_LOG)||s.is(Blocks.MANGROVE_ROOTS)||s.is(Blocks.MUDDY_MANGROVE_ROOTS)||s.is(Blocks.MANGROVE_WOOD);}
 private static boolean wildLeaves(BlockState s){return s.getBlock() instanceof LeavesBlock&&!s.getValue(LeavesBlock.PERSISTENT);}
 /** What may touch a wild tree: air, leaves, logs, soil, the stone, sand and gravel of the ground, plants, snow, vines, a bee nest. A plank,
  *  a fence, glass or cobblestone beside a log makes it somebody's (a tree house, a tree by a path). */
 static boolean natural(BlockState s){
  if(s.isAir()||s.is(BlockTags.LEAVES)||s.is(BlockTags.LOGS)||s.is(BlockTags.DIRT)||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL)
   ||s.is(BlockTags.REPLACEABLE_BY_TREES)||s.is(BlockTags.FLOWERS)||s.is(BlockTags.SAPLINGS)||s.is(Blocks.SNOW)||s.is(Blocks.SNOW_BLOCK)||s.is(Blocks.POWDER_SNOW)||s.is(Blocks.VINE)
   ||s.is(Blocks.BEE_NEST)||s.is(Blocks.COCOA)||s.is(Blocks.MOSS_CARPET)||s.is(Blocks.MOSS_BLOCK)||s.is(Blocks.BROWN_MUSHROOM)||s.is(Blocks.RED_MUSHROOM)||s.is(Blocks.SWEET_BERRY_BUSH)
   ||s.is(Blocks.CLAY)||s.is(Blocks.ICE)||s.is(Blocks.PACKED_ICE)||s.is(Blocks.CALCITE)||s.is(Blocks.PODZOL)||s.is(Blocks.MUD))return true;
  return false;
 }
 /** The tree standing on this foot: every log joined to it (sides and corners), up to 4 blocks aside and 31 up; more than MAX_LOGS returns
  *  null (a giant is left whole). Foot first, then upwards. */
 public static List<BlockPos> tree(ServerLevel l,BlockPos foot){
  var out=new ArrayList<BlockPos>();var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();queue.add(foot);seen.add(foot);
  while(!queue.isEmpty()){var at=queue.poll();out.add(at);if(out.size()>ForestBalance.MAX_LOGS)return null;
   for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){var next=at.offset(dx,dy,dz);
    if(!seen.add(next)||next.getY()<foot.getY()||next.getY()>foot.getY()+31||Math.abs(next.getX()-foot.getX())>4||Math.abs(next.getZ()-foot.getZ())>4)continue;
    if(!l.hasChunkAt(next)||!l.getBlockState(next).is(BlockTags.LOGS))continue;queue.add(next);}}
  out.sort(Comparator.comparingInt(BlockPos::getY));return out;
 }
 /** The crown of a tree: wild leaves within 3 of its logs whose nearest log (Chebyshev) is one of this tree's, nearest first, at most
  *  CROWN_LEAVES_MAX. Leaves of a neighbour's crown stay with the neighbour. */
 public static List<BlockPos> crown(ServerLevel l,List<BlockPos> logs){
  if(logs.isEmpty())return List.of();var own=new HashSet<>(logs);
  int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,y1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
  for(var p:logs){x0=Math.min(x0,p.getX());y0=Math.min(y0,p.getY());z0=Math.min(z0,p.getZ());x1=Math.max(x1,p.getX());y1=Math.max(y1,p.getY());z1=Math.max(z1,p.getZ());}
  var foreign=new ArrayList<BlockPos>();var leaves=new ArrayList<BlockPos>();
  for(int x=x0-6;x<=x1+6;x++)for(int y=y0-6;y<=y1+6;y++)for(int z=z0-6;z<=z1+6;z++){var p=new BlockPos(x,y,z);if(!l.hasChunkAt(p))continue;var s=l.getBlockState(p);
   if(s.is(BlockTags.LOGS)){if(!own.contains(p))foreign.add(p);}
   else if(x>=x0-3&&x<=x1+3&&y>=y0-3&&y<=y1+3&&z>=z0-3&&z<=z1+3&&wildLeaves(s))leaves.add(p);}
  var dist=new HashMap<BlockPos,Integer>();var out=new ArrayList<BlockPos>();
  for(var leaf:leaves){int mine=cheb(leaf,logs),theirs=cheb(leaf,foreign);if(mine<=3&&mine<=theirs){out.add(leaf);dist.put(leaf,mine);}}
  out.sort(Comparator.<BlockPos>comparingInt(dist::get).thenComparingInt(BlockPos::getY).thenComparingLong(BlockPos::asLong));
  return out.size()>ForestBalance.CROWN_LEAVES_MAX?List.copyOf(out.subList(0,ForestBalance.CROWN_LEAVES_MAX)):List.copyOf(out);
 }
 private static int cheb(BlockPos a,List<BlockPos> set){int best=Integer.MAX_VALUE;for(var b:set)best=Math.min(best,Math.max(Math.abs(a.getX()-b.getX()),Math.max(Math.abs(a.getY()-b.getY()),Math.abs(a.getZ()-b.getZ()))));return best;}
 /** Why this foot is no forester's tree ("" when it is his): the rules of AD-131 §3.1 in order. */
 public static String check(ServerLevel l,SettlementData.Entry e,Settlement.Building hut,BlockPos foot,int radius){return candidate(l,e,hut,foot,radius,false).reason();}
 public record Candidate(String reason,Tree tree){}
 /** The tree on this foot with its crown, or why it is not his. */
 public static Candidate candidate(ServerLevel l,SettlementData.Entry e,Settlement.Building hut,BlockPos foot,int radius){return candidate(l,e,hut,foot,radius,true);}
 /** The same; without the crown (its leaves are found only for the tree he goes to fell — the search asks about many). */
 static Candidate candidate(ServerLevel l,SettlementData.Entry e,Settlement.Building hut,BlockPos foot,int radius,boolean withCrown){
  if(!l.hasChunkAt(foot))return new Candidate("unloaded",null);
  var door=ForesterHut.door(e,hut);long dx=foot.getX()-door.getX(),dz=foot.getZ()-door.getZ();if(dx*dx+dz*dz>(long)radius*radius)return new Candidate("radius",null);
  // The reach is a cylinder: a tree on the cliff over the hut (or on the surface over a wood deep below) is no more his than one beyond the radius.
  if(Math.abs(foot.getY()-door.getY())>ForestBalance.RISE)return new Candidate("radius",null);
  var s=l.getBlockState(foot);if(!s.is(BlockTags.LOGS)||mangrove(s))return new Candidate("no_log",null);
  if(!l.getBlockState(foot.below()).is(BlockTags.DIRT))return new Candidate("no_soil",null);
  for(var d:Direction.values())if(!l.getFluidState(foot.relative(d)).isEmpty())return new Candidate("fluid",null);
  var logs=tree(l,foot);if(logs==null)return new Candidate("giant",null);
  // The foot of a 2x2 trunk: every log of the lowest course on soil; the tree is known by the least of them.
  var base=logs.stream().filter(p->p.getY()==foot.getY()&&l.getBlockState(p.below()).is(BlockTags.DIRT)).sorted(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).toList();
  int wild=0;var counted=new HashSet<BlockPos>();var own=new HashSet<>(logs);
  for(var log:logs){
   for(var d:Direction.values()){var n=log.relative(d);if(!natural(l.getBlockState(n)))return new Candidate("built",null);}
   for(int ox=-1;ox<=1;ox++)for(int oy=-1;oy<=1;oy++)for(int oz=-1;oz<=1;oz++){var n=log.offset(ox,oy,oz);if(!own.contains(n)&&counted.add(n)&&wildLeaves(l.getBlockState(n)))wild++;}
  }
  if(wild<ForestBalance.LEAVES_MIN)return new Candidate("not_wild",null);
  for(var log:logs)if(OwnershipEvents.protectedBlock(l,log)||OwnershipEvents.disallowedPlacement(l,log))return new Candidate("protected",null);
  var plantings=ForestPlantings.get(l.getServer());
  for(var cell:base)if(plantings.isPlayer(l,cell))return new Candidate("player",null);
  for(var cell:base)if(plantings.isDecor(l,cell))return new Candidate("decor",null);
  return new Candidate("",new Tree(base.get(0),logs,withCrown?crown(l,logs):List.of(),base,sapling(s)));
 }
 /** The lowest log of this column's trunk, when its top (leaves not counted) is a log standing on soil within 32 blocks down. */
 public static BlockPos footOf(ServerLevel l,int x,int z){return trunk(l,new BlockPos(x,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1,z));}
 /** The same within the hut's own window of height: the top of a column is read from the world's heightmap only while it stands inside the
  *  window — over the forester's wood there may be a roof, a hill or a sea, and then the column is read from the window's top down. */
 static BlockPos footOf(ServerLevel l,int x,int z,int from,int to){
  int top=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
  if(top<=from)return top<to?null:trunk(l,new BlockPos(x,top,z));
  for(var p=new BlockPos(x,from,z);p.getY()>=to;p=p.below())if(l.getBlockState(p).is(BlockTags.LOGS))return trunk(l,p);
  return null;
 }
 /** Down this trunk to its lowest log, when that log stands on soil (32 logs at most). */
 private static BlockPos trunk(ServerLevel l,BlockPos p){
  if(!l.getBlockState(p).is(BlockTags.LOGS))return null;
  for(int n=0;n<32&&l.getBlockState(p.below()).is(BlockTags.LOGS);n++)p=p.below();
  return l.getBlockState(p.below()).is(BlockTags.DIRT)?p:null;
 }
 // ---------- the search ----------
 private static final List<int[]> SPIRAL=new ArrayList<>();
 static{int r=ForestBalance.maxRadius();for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(x*x+z*z<=r*r)SPIRAL.add(new int[]{x,z});SPIRAL.sort(Comparator.comparingInt(c->c[0]*c[0]+c[1]*c[1]));}
 private static final class Scan{int cursor;boolean full;final LinkedHashSet<Long> found=new LinkedHashSet<>();final Map<Long,Long> refused=new HashMap<>();int inReach=-1;}
 private static final Map<UUID,Scan> SCANS=new java.util.concurrent.ConcurrentHashMap<>();
 /** Forget what the search of a hut has found (a test that plants a new wood). */
 public static void forget(UUID hut){SCANS.remove(hut);}
 /** Trees within reach the last full round of the search counted (-1 before the first round ends). */
 public static int inReach(UUID hut){var s=SCANS.get(hut);return s==null?-1:s.inReach;}
 /** Whether the search has gone once round the whole radius. */
 public static boolean searched(UUID hut){var s=SCANS.get(hut);return s!=null&&s.full;}
 /** A foot refused for a day: no way to it. */
 public static void refuse(UUID hut,BlockPos foot,long now){SCANS.computeIfAbsent(hut,k->new Scan()).refused.put(foot.asLong(),now+24000L);}
 /** The next tree for the forester of this hut, nearest to {@code near} (and within {@code reach} of it when reach > 0), or null. One call
  *  walks SCAN_COLUMNS columns of the spiral on from where the last one stopped. */
 public static Tree next(ServerLevel l,SettlementData.Entry e,Settlement.Building hut,int level,BlockPos near,int reach,long now){
  var scan=SCANS.computeIfAbsent(hut.id(),k->new Scan());int radius=ForestBalance.radius(level);var door=ForesterHut.door(e,hut);
  for(int n=0;n<ForestBalance.SCAN_COLUMNS;n++){
   if(scan.cursor>=SPIRAL.size()){scan.cursor=0;scan.full=true;scan.inReach=0;for(var f:List.copyOf(scan.found))if(!l.hasChunkAt(BlockPos.of(f))||check(l,e,hut,BlockPos.of(f),radius).isEmpty())scan.inReach++;else scan.found.remove(f);}
   var c=SPIRAL.get(scan.cursor++);if(c[0]*c[0]+c[1]*c[1]>radius*radius){scan.cursor=SPIRAL.size();continue;}
   int x=door.getX()+c[0],z=door.getZ()+c[1];if(!l.hasChunkAt(new BlockPos(x,door.getY(),z)))continue;
   var foot=footOf(l,x,z,door.getY()+ForestBalance.RISE,door.getY()-ForestBalance.RISE);if(foot==null)continue;var got=candidate(l,e,hut,foot,radius,false);if(got.tree()!=null)scan.found.add(got.tree().foot().asLong());
  }
  scan.refused.values().removeIf(until->until<=now);
  // The nearest foot first; only it is looked at again (and a foot that is no longer his is forgotten on the way).
  var order=scan.found.stream().filter(f->!scan.refused.containsKey(f)).map(BlockPos::of).filter(p->reach<=0||p.distSqr(near)<=(double)reach*reach)
   .sorted(Comparator.comparingDouble(p->p.distSqr(near))).toList();
  for(var foot:order){if(!l.hasChunkAt(foot))continue;var got=candidate(l,e,hut,foot,radius,true);if(got.tree()==null){scan.found.remove(foot.asLong());continue;}return got.tree();}
  return null;
 }
 /** AD-131 §3.5: the kind a level-III forester plants — of the kinds opened for the village and those he carries, the one he has planted least
  *  round his hut; a tie goes to the mayor's preferred kind, then to the order of PLANTED. Null when none of them is at hand. */
 public static Item nextSpecies(ServerLevel l,SettlementData.Entry e,Settlement.Building hut,int level,Set<Item> atHand,Set<Item> carried){
  var counts=ForestPlantings.get(l.getServer()).planted(l,hut.id(),ForesterHut.door(e,hut),ForestBalance.radius(level));
  var preferred=ForestPolicies.get(l.getServer()).preferred(e,hut);Item best=null;int bestCount=Integer.MAX_VALUE;int bestRank=Integer.MAX_VALUE;
  for(int i=0;i<PLANTED.size();i++){var id=PLANTED.get(i);var item=BuiltInRegistries.ITEM.get(new ResourceLocation(id));
   if(!atHand.contains(item))continue;if(!carried.contains(item)&&!CropUnlocks.unlocked(l,e,id))continue;
   int n=counts.getOrDefault(id,0),rank=id.equals(preferred)?-1:i;
   if(n<bestCount||n==bestCount&&rank<bestRank){best=item;bestCount=n;bestRank=rank;}}
  return best;
 }
 /** AD-131 §5: the forester hut's block of its card — reach and trees in reach, felled today; II+ planted today and bare feet; III+ the mix of
  *  kinds he has planted and what the hut asks for (a quest on the board, a missing stock); IV+ the saw; VI the grove; the preferred kind. */
 public static net.minecraft.nbt.CompoundTag card(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var t=new net.minecraft.nbt.CompoundTag();int lv=BuildingLevels.level(l,e,b);t.putInt("level",lv);t.putInt("radius",ForestBalance.radius(lv));t.putInt("inReach",inReach(b.id()));
  var file=MineWork.path(l,b.id());var work=java.nio.file.Files.exists(file)?org.villageastra.persistence.NbtRecord.read(file):new net.minecraft.nbt.CompoundTag();long day=l.getDayTime()/24000L;
  boolean today=work.getLong("felledDay")==day;t.putInt("felled",today?work.getInt("felledToday"):0);t.putInt("planted",today?work.getInt("plantedToday"):0);
  t.putInt("bare",work.getList("bare",net.minecraft.nbt.Tag.TAG_COMPOUND).size());t.putString("status",work.getString("status"));
  if(lv>=3){var mix=new net.minecraft.nbt.ListTag();ForestPlantings.get(l.getServer()).planted(l,b.id(),ForesterHut.door(e,b),ForestBalance.radius(lv)).forEach((id,n)->{var m=new net.minecraft.nbt.CompoundTag();m.putString("item",id);m.putInt("count",n);mix.add(m);});t.put("mix",mix);
   var asks=new net.minecraft.nbt.ListTag();for(var w:WorkerSupplies.wants(l,e,b.id())){var items=w.ingredient().getItems();if(items.length==0||!items[0].is(net.minecraft.tags.ItemTags.SAPLINGS)||!w.destination().equals(b.id()))continue;
    var a=new net.minecraft.nbt.CompoundTag();a.putString("item",BuiltInRegistries.ITEM.getKey(items[0].getItem()).toString());a.putInt("count",w.count());asks.add(a);}
   var quest=CropQuests.saplingQuest(l,e);if(!quest.isEmpty()){var a=new net.minecraft.nbt.CompoundTag();a.putString("item",quest);a.putInt("count",CropQuests.count(quest));a.putBoolean("quest",true);asks.add(a);}
   t.put("asks",asks);}
  if(lv>=ForestBalance.SAW_FROM){var g=ForestryMachines.view(l,e,b,l.getGameTime());t.putBoolean("sawOn",ForestPolicies.get(l.getServer()).sawOn(e,b));t.putInt("planksPerLog",ForestBalance.PLANKS_PER_LOG);
   t.putInt("sawn",g.getInt("sawnToday"));t.putString("sawStatus",g.getString("sawStatus"));if(lv>=6)t.put("grove",g);}
  t.putString("preferred",ForestPolicies.get(l.getServer()).preferred(e,b));
  int kinds=0;for(var id:PLANTED)if(CropUnlocks.known(id)&&CropUnlocks.unlocked(l,e,id))kinds++;t.putInt("kinds",kinds);
  return t;
 }
 /** AD-131: the resource smoke probe's tree — a wild oak of five logs with a branch off its fourth, 18 blocks in front of the hut door (out
  *  of every lot's ground), on the grass there. Returns its foot. */
 public static BlockPos probeTree(ServerLevel l,SettlementData.Entry e,Settlement.Building hut){
  var door=ForesterHut.door(e,hut);var foot=new BlockPos(door.getX(),l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,door.getX(),door.getZ()-18),door.getZ()-18);
  wildOak(l,foot,5);l.setBlock(foot.offset(1,3,0),Blocks.OAK_LOG.defaultBlockState(),3);return foot;
 }
 /** A wild oak for fixtures and probes: a trunk of {@code height} logs on the foot and a round crown of wild leaves (their distance set, so
  *  none decays), the way a young oak of the world grows. */
 public static List<BlockPos> wildOak(ServerLevel l,BlockPos foot,int height){return wildTree(l,foot,height,Blocks.OAK_LOG,Blocks.OAK_LEAVES);}
 public static List<BlockPos> wildTree(ServerLevel l,BlockPos foot,int height,Block log,Block leaf){
  var logs=new ArrayList<BlockPos>();for(int y=0;y<height;y++){var p=foot.above(y);l.setBlock(p,log.defaultBlockState(),3);logs.add(p);}
  int top=foot.getY()+height-1;
  for(int y=top-2;y<=top+1;y++){int r=y>=top?1:2;for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){var p=new BlockPos(foot.getX()+dx,y,foot.getZ()+dz);
   if(r==2&&Math.abs(dx)==2&&Math.abs(dz)==2)continue;if(y==top+1&&Math.abs(dx)+Math.abs(dz)>1)continue;if(!l.getBlockState(p).isAir())continue;
   int d=Math.min(7,Math.max(1,Math.abs(dx)+Math.abs(dz)+Math.max(0,y-top)));
   l.setBlock(p,leaf.defaultBlockState().setValue(LeavesBlock.DISTANCE,d).setValue(LeavesBlock.PERSISTENT,false),2);}}
  return logs;
 }
}
