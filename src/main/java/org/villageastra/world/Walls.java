package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.Profession;
import org.villageastra.domain.Resident;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
/** AD-094: the castle wall of a settlement. A ring around the hall, square or round, three blocks of stone brick on the ground it
 *  stands on with a crenellated top, gateways where the roads go out (and on the four sides when no road does), and archer towers
 *  standing in the ring as buildings of their own — archers are assigned to them like to the archery. The wall is a builders' project
 *  paid block for block from the hall; the towers follow one at a time through the ordinary building queue. */
public final class Walls {
 private Walls(){}
 /** AD-127: FITTED is the wall drawn round the village's real extent that follows it as it grows. */
 public enum Shape{SQUARE,ROUND,FITTED}
 public static final int MIN_RADIUS=16,MAX_RADIUS=64,HEIGHT=3,GATE=3,TOWER_EVERY=28;
 public static final String TOWER="wall_tower";
 /** Archers one tower platform holds. */
 public static final int ARCHERS_PER_TOWER=2;
 /** The whole wall. A fitted one also carries its profile (radii ×10 per sector), the cells of the old ring it takes down, the columns
  *  whose obstacles pushed it out, and the natural blocks the crew clears first. */
 public record Plan(Shape shape,int radius,List<BlockPos> ring,List<BlockPos> gates,List<BlockPos> towers,Map<BlockPos,BlockState> blocks,String reason,
   int[] radiiX10,List<BlockPos> retire,List<BlockPos> pushed,List<BlockPos> clear){
  public Plan(Shape shape,int radius,List<BlockPos> ring,List<BlockPos> gates,List<BlockPos> towers,Map<BlockPos,BlockState> blocks,String reason){
   this(shape,radius,ring,gates,towers,blocks,reason,new int[0],List.of(),List.of(),List.of());}
 }
 /** AD-127: balance of the fitted wall. */
 public static final WallOutline.Config FIT=config();
 private static WallOutline.Config config(){
  try(var s=Walls.class.getResourceAsStream("/data/villageastra/balance/walls.json")){if(s==null)throw new IllegalStateException("Missing wall balance");
   var o=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(s,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
   return new WallOutline.Config(o.get("sectors").getAsInt(),o.get("margin").getAsInt(),o.get("headroom").getAsInt(),o.get("trigger").getAsInt(),o.get("max_radius").getAsInt(),
    o.get("obstacle_push").getAsInt(),o.get("push_step").getAsInt(),o.get("cliff_step").getAsInt(),o.get("min_radius").getAsInt(),o.get("shallow_water").getAsInt(),
    o.get("extend_every").getAsInt(),o.get("min_turn_deg").getAsDouble());
  }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 /** AD-127: the fitted wall's look — a cobblestone plinth course, two courses of stone brick, crenels of brick wall, and a spruce timber
  *  pilaster at every other vertex of its profile. The AD-094 shapes keep their all-brick look. */
 public static final BlockState PLINTH=Blocks.COBBLESTONE.defaultBlockState(),BODY=Blocks.STONE_BRICKS.defaultBlockState(),CRENEL=Blocks.STONE_BRICK_WALL.defaultBlockState(),
  PILASTER=Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
 /** AD-157 (owner's Defence ladder): what a fitted wall is laid of by the village's Defence - I-II a wooden palisade (spruce logs,
  *  spruce fence crenels), III small stone (cobblestone), IV and on big stone (the AD-127 look). The pilasters stay spruce timber. */
 public record Style(String id,BlockState plinth,BlockState body,BlockState crenel,BlockState pilaster){}
 public static final Style WOOD=new Style("wood",Blocks.SPRUCE_LOG.defaultBlockState(),Blocks.SPRUCE_LOG.defaultBlockState(),Blocks.SPRUCE_FENCE.defaultBlockState(),PILASTER),
  SMALL_STONE=new Style("small_stone",PLINTH,Blocks.COBBLESTONE.defaultBlockState(),Blocks.COBBLESTONE_WALL.defaultBlockState(),PILASTER),
  BIG_STONE=new Style("big_stone",PLINTH,BODY,CRENEL,PILASTER);
 public static final String DEFENCE="defense";
 /** The village's Defence: the highest defense.N it has learned (0 without any). */
 public static int defence(ServerLevel l,SettlementData.Entry e){var done=ResearchKnobs.done(l,e);int n=0;for(int i=1;i<=6;i++)if(done.contains(DEFENCE+"."+i))n=i;return n;}
 /** The Defence a wall is laid for: the village's, never below what its wall was laid for (an AD-127 wall with no mark is big stone). */
 static int built(ServerLevel l,SettlementData.Entry e,CompoundTag rec){int laid=rec==null?0:rec.contains("defence")?rec.getInt("defence"):4;return Math.max(laid,defence(l,e));}
 public static Style style(int defence){return defence>=4?BIG_STONE:defence==3?SMALL_STONE:WOOD;}
 /** A block any wall of a village is laid of. */
 public static boolean palette(BlockState s){return s.is(Blocks.STONE_BRICKS)||s.is(Blocks.STONE_BRICK_WALL)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.STRIPPED_SPRUCE_LOG)
   ||s.is(Blocks.SPRUCE_LOG)||s.is(Blocks.SPRUCE_FENCE)||s.is(Blocks.COBBLESTONE_WALL);}
 /** A fitted wall's headroom as ordered: 0..24, whatever a client sends. */
 public static int headroom(int level){return Math.max(0,Math.min(24,level));}
 /** A refusal with a place in it ("water@12,-40") as a language key and its arguments. */
 public static String reasonKey(String reason){int at=reason.indexOf('@');return at<0?reason:reason.substring(0,at)+"_at";}
 public static Object[] reasonArgs(String reason){int at=reason.indexOf('@');if(at<0)return new Object[0];var xz=reason.substring(at+1).split(",");return new Object[]{xz[0],xz.length>1?xz[1]:""};}
 private static Path path(MinecraftServer s,UUID village){return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-walls/"+village+".bin");}
 public static CompoundTag record(MinecraftServer s,UUID village){var p=path(s,village);return Files.exists(p)?NbtRecord.read(p):null;}
 /** The ring of columns around the hall: a square of the given half-side, or a circle of the given radius — one column thick. */
 public static List<BlockPos> ring(BlockPos center,Shape shape,int radius){
  var out=new ArrayList<BlockPos>();var seen=new HashSet<Long>();
  if(shape==Shape.SQUARE){
   // Side after side, the way a builder walks round it: north, east, south, west.
   for(int i=-radius;i<radius;i++)add(out,seen,center.offset(i,0,-radius));
   for(int i=-radius;i<radius;i++)add(out,seen,center.offset(radius,0,i));
   for(int i=-radius;i<radius;i++)add(out,seen,center.offset(-i,0,radius));
   for(int i=-radius;i<radius;i++)add(out,seen,center.offset(-radius,0,-i));
  }else{
   // A digital circle, one column thick and closed: every column whose distance from the centre rounds to the radius and that touches the outside.
   for(int dx=-radius-1;dx<=radius+1;dx++)for(int dz=-radius-1;dz<=radius+1;dz++){
    double d=Math.sqrt(dx*dx+dz*dz);if(Math.abs(d-radius)>0.5)continue;add(out,seen,center.offset(dx,0,dz));}
   out.sort(Comparator.comparingDouble(p->Math.atan2(p.getZ()-center.getZ(),p.getX()-center.getX())));
  }
  return out;
 }
 private static void add(List<BlockPos> out,Set<Long> seen,BlockPos p){if(seen.add(p.asLong()))out.add(p);}
 /** Where the wall is opened: where a road of the village crosses it, or on the four sides when none does. */
 static List<BlockPos> gates(ServerLevel l,SettlementData.Entry e,List<BlockPos> ring,Shape shape,int radius){
  var out=new ArrayList<BlockPos>();var c=e.center();
  for(var p:ring){var top=ground(l,p);if(top!=null&&Roads.cell(l,top)!=null&&out.stream().noneMatch(g->g.distSqr(p)<=(GATE+2)*(GATE+2)))out.add(p);}
  if(out.isEmpty())for(var side:List.of(c.offset(0,0,-radius),c.offset(radius,0,0),c.offset(0,0,radius),c.offset(-radius,0,0))){
   var nearest=ring.stream().min(Comparator.comparingDouble(p->p.distSqr(side))).orElse(null);if(nearest!=null)out.add(nearest);}
  return out;
 }
 /** The ground of a column: the top solid block, or null when that column is not loaded. */
 static BlockPos surface(ServerLevel l,BlockPos column){
  if(!l.hasChunkAt(column))return null;int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ())-1;
  return new BlockPos(column.getX(),y,column.getZ());
 }
 /** The ground of a column under whatever of the village's own wall already stands there: ordering the same ring again repairs its
  *  breaches, it never stacks a second wall on the first. */
 static BlockPos ground(ServerLevel l,BlockPos column){
  var top=surface(l,column);if(top==null)return null;int floor=top.getY()-(HEIGHT+2);
  while(top.getY()>floor&&(wallBlock(l,top)||l.getBlockState(top).isAir()))top=top.below();
  return top;
 }
 /** Square walls keep their towers off the corners: a corner tower's door would open straight into the wall's other arm. Each stands on a
  *  side three columns from its corner, going round clockwise, so the corner column closes the ring against the tower's flank. */
 static List<BlockPos> squareTowers(BlockPos c,int radius){
  return List.of(c.offset(-radius+3,0,-radius),c.offset(radius,0,-radius+3),c.offset(radius-3,0,radius),c.offset(-radius,0,radius-3));
 }
 /** Where a tower stood at a corner in walls ordered before its sites moved to the sides (the same corner, clockwise). */
 static BlockPos movedCorner(BlockPos c,int radius,BlockPos site){
  int dx=site.getX()-c.getX(),dz=site.getZ()-c.getZ();if(Math.abs(dx)!=radius||Math.abs(dz)!=radius)return site;
  if(dx<0&&dz<0)return c.offset(-radius+3,0,-radius);if(dx>0&&dz<0)return c.offset(radius,0,-radius+3);
  if(dx>0)return c.offset(radius-3,0,radius);return c.offset(-radius,0,radius-3);
 }
 /** Whether a tower can stand on a site: its five by five ground roughly level, nothing of the village in its body and nothing solid there
  *  that the crew may not clear. A site that fails is not left as a gap: the wall simply runs through it. */
 static boolean towerFits(ServerLevel l,BlockPos site){
  var ground=ground(l,site);if(ground==null)return true;
  for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){var column=ground(l,site.offset(dx,0,dz));if(column==null)return true;
   if(Math.abs(column.getY()-ground.getY())>2)return false;
   for(int y=1;y<=9;y++){var at=new BlockPos(site.getX()+dx,ground.getY()+y,site.getZ()+dz);var state=l.getBlockState(at);
    if(org.villageastra.server.OwnershipEvents.protectedBlock(l,at))return false;
    if(!state.isAir()&&!state.canBeReplaced()&&!state.is(net.minecraft.tags.BlockTags.LEAVES))return false;}}
  return true;
 }
 /** The whole wall: its ring, gates, tower sites and every block to put up. Refused with a reason when it cannot stand there. */
 public static Plan plan(ServerLevel l,SettlementData.Entry e,Shape shape,int radius){
  // AD-127: for the fitted wall the number is its headroom; the ring comes from the village.
  if(shape==Shape.FITTED)return fitted(l,e,headroom(radius));
  if(radius<MIN_RADIUS||radius>MAX_RADIUS)return new Plan(shape,radius,List.of(),List.of(),List.of(),Map.of(),"radius");
  var ring=ring(e.center(),shape,radius);
  var gates=gates(l,e,ring,shape,radius);
  // Towers stand at even steps along the ring, never in a gateway: at the corners of a square, spread round a circle.
  var towers=new ArrayList<BlockPos>();
  if(shape==Shape.SQUARE){var c=e.center();towers.addAll(squareTowers(c,radius));
   // A long side gets one more tower in its middle.
   if(2*radius>TOWER_EVERY*3/2)for(var middle:List.of(c.offset(0,0,-radius),c.offset(radius,0,0),c.offset(0,0,radius),c.offset(-radius,0,0)))towers.add(middle);}
  else{int count=Math.max(4,ring.size()/TOWER_EVERY);for(int i=0;i<count;i++)towers.add(ring.get((int)((long)i*ring.size()/count+ring.size()/(count*2))%ring.size()));}
  towers.removeIf(t->gates.stream().anyMatch(g->g.distSqr(t)<=(GATE+4)*(GATE+4)));
  towers.removeIf(t->!towerFits(l,t));
  var blocks=new LinkedHashMap<BlockPos,BlockState>();
  for(var column:ring){
   if(towers.stream().anyMatch(t->Math.abs(t.getX()-column.getX())<=2&&Math.abs(t.getZ()-column.getZ())<=2))continue;
   var ground=ground(l,column);if(ground==null)return new Plan(shape,radius,ring,gates,towers,Map.of(),"unloaded");
   // A gateway is open three columns wide — and as wide as the road that goes through it.
   if(Roads.cell(l,ground)!=null||gates.stream().anyMatch(g->Math.abs(g.getX()-column.getX())<=GATE/2&&Math.abs(g.getZ()-column.getZ())<=GATE/2)){
    // A gateway has a lintel over its opening, as high as the wall: people and carts pass under it.
    var lintel=ground.above(HEIGHT+1);if(!org.villageastra.server.OwnershipEvents.protectedBlock(l,lintel)||wallBlock(l,lintel))blocks.put(lintel,Blocks.STONE_BRICKS.defaultBlockState());continue;}
   // The wall is not laid over the village's own buildings or anything protected: then the ring has to go elsewhere.
   for(int y=1;y<=HEIGHT+1;y++){var at=ground.above(y);if(org.villageastra.server.OwnershipEvents.protectedBlock(l,at)&&!wallBlock(l,at))return new Plan(shape,radius,ring,gates,towers,Map.of(),"crosses");}
   for(int y=1;y<=HEIGHT;y++)blocks.put(ground.above(y),Blocks.STONE_BRICKS.defaultBlockState());
   // Crenellations: every other column carries one more block.
   if(((column.getX()+column.getZ())&1)==0)blocks.put(ground.above(HEIGHT+1),Blocks.STONE_BRICK_WALL.defaultBlockState());
  }
  return new Plan(shape,radius,ring,gates,towers,blocks,"");
 }
 // ---- AD-127: the wall fitted to the village ----
 private static final Map<String,GrowthPlots.Box> BOXES=new HashMap<>();
 /** A building's growth plot (GrowthPlots.box), cached per design and turn; a design the blueprints no longer know falls back to its
  *  size plus the extension, so a renamed design never stops the wall. */
 static GrowthPlots.Box plot(String type,BlockPos origin,int rotation){
  var rel=BOXES.computeIfAbsent(type+"#"+rotation,k->{
   try{return GrowthPlots.box(type,BlockPos.ZERO,rotation);}
   catch(RuntimeException ex){int[] size;try{size=BuildingPlacement.size(type,rotation);}catch(RuntimeException again){size=new int[]{12,12};}
    return new GrowthPlots.Box(-GrowthPlots.EXTENSION,-GrowthPlots.EXTENSION,size[0]-1+GrowthPlots.EXTENSION,size[1]-1+GrowthPlots.EXTENSION);}});
  return new GrowthPlots.Box(rel.west()+origin.getX(),rel.north()+origin.getZ(),rel.east()+origin.getX(),rel.south()+origin.getZ());
 }
 /** The village's extent as columns: the hall's centre and the edge of every building's growth plot (its exterior equipment, the farm's
  *  reserved field and the room to extend), and the plot the mayor has chosen for the next building. Wall towers and roads are not. */
 public static List<int[]> extent(SettlementData.Entry e){
  var out=new ArrayList<int[]>();out.add(new int[]{e.center().getX(),e.center().getZ()});
  for(var b:e.settlement().buildings()){if(b.type().equals(TOWER))continue;var box=plot(b.type(),BuildingPlacement.origin(e,b),b.rotation());out.addAll(WallOutline.perimeter(box.west(),box.north(),box.east(),box.south()));}
  var proposal=MayorPlanner.proposal(e.settlement().id());
  if(proposal!=null&&proposal.site()!=null){var box=plot(proposal.design(),proposal.site(),0);out.addAll(WallOutline.perimeter(box.west(),box.north(),box.east(),box.south()));}
  return out;
 }
 /** How far out, sector by sector, the village needs its wall (margin included, headroom not). */
 public static double[] need(SettlementData.Entry e){return WallOutline.need(e.center().getX(),e.center().getZ(),extent(e),FIT.sectors(),FIT.margin(),FIT.minRadius());}
 /** The profile of a recorded wall of any shape. */
 static double[] profile(CompoundTag t){
  var shape=t.getString("shape");int radius=t.getInt("radius");
  if(shape.equals("fitted")&&t.getIntArray("radii").length==FIT.sectors())return WallOutline.fromX10(t.getIntArray("radii"));
  return shape.equals("square")?WallOutline.square(radius,FIT.sectors()):WallOutline.circle(radius,FIT.sectors());
 }
 /** The village has grown past its wall (less the trigger) somewhere round it. */
 public static boolean outgrown(SettlementData.Entry e,CompoundTag t){return t!=null&&WallOutline.outgrown(profile(t),need(e),FIT.trigger());}
 private static long column(BlockPos p){return BlockPos.asLong(p.getX(),0,p.getZ());}
 /** The natural ground of a column: under trees (trunks, leaves), plants, snow, water and the village's own wall cells (CF2, CF4). */
 static BlockPos naturalGround(ServerLevel l,BlockPos column,Set<Long> own){
  var top=surface(l,column);if(top==null)return null;int floor=top.getY()-48;
  while(top.getY()>floor){var s=l.getBlockState(top);
   boolean loose=s.isAir()||s.is(net.minecraft.tags.BlockTags.LOGS)||s.is(net.minecraft.tags.BlockTags.LEAVES)||!s.getFluidState().isEmpty()||s.canBeReplaced()||own.contains(top.asLong());
   // Whatever somebody put on the wall (a chest, a sign, a block) is not the ground: the column's ground is still under the wall.
   if(!loose&&!onOwn(top,own))break;
   top=top.below();}
  return top;
 }
 private static boolean onOwn(BlockPos p,Set<Long> own){if(own.isEmpty())return false;for(int k=1;k<=HEIGHT+3;k++)if(own.contains(p.below(k).asLong()))return true;return false;}
 private static Plan refusedFit(String reason){return new Plan(Shape.FITTED,0,List.of(),List.of(),List.of(),Map.of(),reason);}
 /** Standing towers of the village as their sites (the centre column of their five by five body). */
 private static List<BlockPos> standingTowers(SettlementData.Entry e){
  var out=new ArrayList<BlockPos>();for(var b:e.settlement().buildings())if(b.type().equals(TOWER)){var o=BuildingPlacement.origin(e,b);out.add(new BlockPos(o.getX()+2,e.center().getY(),o.getZ()+2));}return out;}
 private static boolean near(BlockPos a,BlockPos b,int d){return Math.abs(a.getX()-b.getX())<=d&&Math.abs(a.getZ()-b.getZ())<=d;}
 /** AD-127: the ring fitted to the village. With a fitted wall already standing it grows only where the village outgrew it (else it is
  *  the same ring, and ordering it again repairs it); an AD-094 wall is replaced. Water, cliffs, foreign buildings and roads running
  *  along the ring push the sectors they stand in outward, a few blocks at a time, or refuse with the kind and the place. */
 static Plan fitted(ServerLevel l,SettlementData.Entry e,int headroom){
  var c=e.center();int n=FIT.sectors();var id=e.settlement().id();
  var rec=record(l.getServer(),id);var look=style(built(l,e,rec));boolean grows=rec!=null&&rec.getString("shape").equals("fitted");
  var need=need(e);
  double[] r=grows?WallOutline.grow(profile(rec),need,headroom,FIT.trigger(),FIT.minTurn()):WallOutline.fresh(need,headroom,FIT.minTurn());
  var own=new HashSet<Long>();if(rec!=null)for(long x:rec.getLongArray("cells"))own.add(x);
  // The atlas's hard bound: a round ring's far side lies beyond the survey margin of most villages, so only this limit refuses it.
  var hub=new net.minecraft.world.level.ChunkPos(c);
  var standing=standingTowers(e);
  var pushes=new int[n];var pushed=new LinkedHashSet<BlockPos>();
  List<BlockPos> ring;BlockPos[] ground;boolean[] road;
  for(int round=0;;round++){
   if(WallOutline.max(r)>FIT.maxRadius())return refusedFit("too_large");
   ring=new ArrayList<>();for(var p:WallOutline.raster(c.getX(),c.getZ(),r))ring.add(new BlockPos(p[0],c.getY(),p[1]));
   ground=new BlockPos[ring.size()];road=new boolean[ring.size()];
   boolean outside=false;
   for(int i=0;i<ring.size();i++){var col=ring.get(i);outside|=Math.max(Math.abs((col.getX()>>4)-hub.x),Math.abs((col.getZ()>>4)-hub.z))>Atlas.RADIUS_LIMIT;
    ground[i]=naturalGround(l,col,own);if(ground[i]==null)return refusedFit("unloaded");road[i]=Roads.cell(l,ground[i])!=null;}
   var obstacles=obstacles(l,ring,ground,road,own,standing);
   // Beyond the atlas is refused only once nothing else is in the way: the obstacle that pushed it there is the better answer.
   if(obstacles.isEmpty()){if(outside)return refusedFit("outside");break;}
   var first=obstacles.entrySet().iterator().next();
   if(round>=8)return refusedFit(first.getValue()+"@"+ring.get(first.getKey()).getX()+","+ring.get(first.getKey()).getZ());
   var raise=new LinkedHashMap<Integer,String>();
   for(var o:obstacles.entrySet()){var col=ring.get(o.getKey());pushed.add(col);int k=WallOutline.sectorBelow(c.getX(),c.getZ(),col.getX(),col.getZ(),n);
    var why=o.getValue()+"@"+col.getX()+","+col.getZ();raise.putIfAbsent(k,why);raise.putIfAbsent((k+1)%n,why);}
   for(var k:raise.entrySet()){if((pushes[k.getKey()]+1)*FIT.pushStep()>FIT.obstaclePush())return refusedFit(k.getValue());pushes[k.getKey()]++;r[k.getKey()]+=FIT.pushStep();}
   r=WallOutline.round(r,FIT.minTurn());
  }
  int size=ring.size();
  // Gates where the village's roads and trails cross the ring; without any, on the four sides (north, east, south, west).
  var gates=new ArrayList<BlockPos>();
  for(int i=0;i<size;i++){var col=ring.get(i);if(road[i]&&gates.stream().noneMatch(g->g.distSqr(col)<=(GATE+2)*(GATE+2)))gates.add(col);}
  if(gates.isEmpty())for(int k:new int[]{3*n/4,0,n/4,n/2}){var v=WallOutline.vertex(c.getX(),c.getZ(),r[k],k,n);var side=new BlockPos(v[0],c.getY(),v[1]);
   ring.stream().min(Comparator.comparingDouble(p->p.distSqr(side))).ifPresent(gates::add);}
  // Towers: those standing on the ring stay, unbuilt sites of the old ring that the new one still passes are kept, and new sites follow
  // at even steps (CF5). A standing tower the ring has left behind stays a watchtower; an unbuilt one off the ring is forgotten.
  var columns=new HashSet<Long>();for(var col:ring)columns.add(column(col));
  var sites=new ArrayList<BlockPos>();var record=new ArrayList<BlockPos>();
  for(var s:standing){boolean on=ring.stream().anyMatch(col->near(col,s,2));if(on)sites.add(s);record.add(s);}
  if(rec!=null){boolean square=rec.getString("shape").equals("square");int oldRadius=rec.getInt("radius");
   for(long raw:rec.getLongArray("towers")){var s=square?movedCorner(c,oldRadius,BlockPos.of(raw)):BlockPos.of(raw);
    if(standing.stream().anyMatch(t->near(t,s,0))||!columns.contains(column(s)))continue;
    if(gates.stream().noneMatch(g->g.distSqr(s)<=(GATE+4)*(GATE+4))&&towerFits(l,s)){sites.add(s);record.add(s);}}}
  int count=Math.max(4,size/TOWER_EVERY);
  for(int i=0;i<count;i++){var s=ring.get((int)((long)i*size/count+size/(count*2))%size);
   if(gates.stream().anyMatch(g->g.distSqr(s)<=(GATE+4)*(GATE+4))||sites.stream().anyMatch(t->t.distSqr(s)<(TOWER_EVERY/2)*(TOWER_EVERY/2))||!towerFits(l,s))continue;
   sites.add(s);record.add(s);}
  // Pilasters at every other vertex of the profile: the same vertices keep them when the ring grows elsewhere.
  var pilasters=new HashSet<Long>();for(int k=0;k<n;k+=2){var v=WallOutline.vertex(c.getX(),c.getZ(),r[k],k,n);pilasters.add(BlockPos.asLong(v[0],0,v[1]));}
  var blocks=new LinkedHashMap<BlockPos,BlockState>();var clear=new ArrayList<BlockPos>();
  var furniture=new HashSet<Long>();for(var list:List.of(Roads.fences(l.getServer(),id),Roads.posts(l.getServer(),id),Roads.gates(l.getServer(),id)))for(long x:list)furniture.add(x);
  for(int i=0;i<size;i++){var col=ring.get(i);var g=ground[i];
   if(sites.stream().anyMatch(t->near(t,col,2)))continue;
   boolean gateway=road[i]||gates.stream().anyMatch(gt->near(gt,col,GATE/2));
   // A column in a dip gets up to two blocks of plinth under it, so the top does not drop into every hollow.
   int low=Math.min(ground[(i+size-1)%size].getY(),ground[(i+1)%size].getY())-g.getY();int fill=gateway?0:Math.max(0,Math.min(2,low));
   // Cleared is what the wall will fill (its crenel only where it has one) and a gateway's passage; above that only trunks.
   int top=g.getY()+(gateway?HEIGHT+1:fill+HEIGHT+(((col.getX()+col.getZ())&1)==0?1:0));var surface=surface(l,col);int reach=Math.max(top,surface==null?top:surface.getY());
   for(int y=reach;y>g.getY();y--){var at=new BlockPos(col.getX(),y,col.getZ());var s=l.getBlockState(at);
    if(s.isAir()||!s.getFluidState().isEmpty()||s.canBeReplaced()||own.contains(at.asLong()))continue;
    if(!(y<=top||s.is(net.minecraft.tags.BlockTags.LOGS)))continue;
    // Nothing protected is cleared, and a gateway keeps its road's fences, gates and lamp posts: people pass under the lintel beside them.
    if(org.villageastra.server.OwnershipEvents.protectedBlock(l,at)||l.getBlockEntity(at)!=null||gateway&&furniture.contains(at.asLong()))continue;
    clear.add(at);}
   if(gateway){var lintel=g.above(HEIGHT+1);if(!org.villageastra.server.OwnershipEvents.protectedBlock(l,lintel)||own.contains(lintel.asLong()))blocks.put(lintel,look.body());continue;}
   boolean pilaster=pilasters.contains(column(col));
   for(int y=1;y<=fill;y++)blocks.put(g.above(y),look.plinth());
   var base=g.above(fill);
   blocks.put(base.above(1),pilaster?look.pilaster():look.plinth());
   for(int y=2;y<=HEIGHT;y++)blocks.put(base.above(y),pilaster?look.pilaster():look.body());
   if(((col.getX()+col.getZ())&1)==0)blocks.put(base.above(HEIGHT+1),look.crenel());
  }
  // The old ring's cells the new one does not keep are taken down, each column from the top.
  var retire=new ArrayList<BlockPos>();
  if(rec!=null){var byColumn=new LinkedHashMap<Long,List<BlockPos>>();
   for(long raw:rec.getLongArray("cells")){var p=BlockPos.of(raw);if(!blocks.containsKey(p))byColumn.computeIfAbsent(column(p),k->new ArrayList<>()).add(p);}
   for(var list:byColumn.values()){list.sort(Comparator.comparingInt((BlockPos p)->p.getY()).reversed());retire.addAll(list);}}
  return new Plan(Shape.FITTED,(int)Math.ceil(WallOutline.max(r)),ring,gates,record,blocks,"",WallOutline.toX10(r),retire,List.copyOf(pushed),clear);
 }
 /** Columns the ring may not stand on: deep water or lava, a cliff from the column before, something protected or with contents in
  *  the wall's body, and a road running along the ring longer than a gateway (CF6). Columns of a standing tower are its own. */
 private static LinkedHashMap<Integer,String> obstacles(ServerLevel l,List<BlockPos> ring,BlockPos[] ground,boolean[] road,Set<Long> own,List<BlockPos> standing){
  var out=new LinkedHashMap<Integer,String>();int size=ring.size();
  for(int i=0;i<size;i++){var col=ring.get(i);var g=ground[i];
   if(standing.stream().anyMatch(t->near(t,col,2)))continue;
   int depth=0;for(var at=g.above();depth<=FIT.shallowWater()+1;at=at.above()){var fluid=l.getFluidState(at);if(fluid.isEmpty())break;if(fluid.is(net.minecraft.tags.FluidTags.LAVA)){depth=99;break;}depth++;}
   if(depth>FIT.shallowWater()){out.put(i,"water");continue;}
   if(Math.abs(ground[(i+size-1)%size].getY()-g.getY())>FIT.cliffStep()){out.put(i,"cliff");continue;}
   // Only the wall's own body counts: a chest or a sign put on the wall's top does not push the ring away (same fill rule as the plan).
   int low=Math.min(ground[(i+size-1)%size].getY(),ground[(i+1)%size].getY())-g.getY();
   int body=road[i]?HEIGHT+1:Math.max(0,Math.min(2,low))+HEIGHT+(((col.getX()+col.getZ())&1)==0?1:0);
   // A chest standing where the wall would go is not built over: it is somebody's.
   if(l.getBlockEntity(g)!=null&&!own.contains(g.asLong())){out.put(i,"crosses");continue;}
   for(int y=1;y<=body;y++){var at=g.above(y);if(own.contains(at.asLong()))continue;
    if(org.villageastra.server.OwnershipEvents.protectedBlock(l,at)||l.getBlockEntity(at)!=null){out.put(i,"crosses");break;}}
  }
  int start=0;while(start<size&&road[start])start++;
  if(start==size){out.putIfAbsent(0,"road");return out;}
  for(int i=1,run=0;i<=size;i++){int at=(start+i)%size;
   if(road[at]&&i<size){run++;continue;}
   if(run>GATE+2)out.putIfAbsent((start+i-1-run/2+size)%size,"road");run=0;}
  return out;
 }
 private static final Map<String,Plan> PREVIEWS=new HashMap<>();
 /** The map's dry run of a fitted wall, planned at most once a second per village, headroom and wall generation (CF13). */
 public static Plan previewFitted(ServerLevel l,SettlementData.Entry e,int headroom){
  var rec=record(l.getServer(),e.settlement().id());
  var key=e.settlement().id()+"|"+headroom(headroom)+"|"+(rec==null?-1:rec.getInt("generation"))+"|"+(rec==null?"":rec.getString("shape"))+"|"+l.getGameTime()/20;
  var hit=PREVIEWS.get(key);if(hit!=null)return hit;
  if(PREVIEWS.size()>64)PREVIEWS.clear();var plan=fitted(l,e,headroom(headroom));PREVIEWS.put(key,plan);return plan;
 }
 /** The builders' project of a fitted wall: round the ring once, each column first cleared of trees and loose rock from the top, then
  *  laid from the ground up; last the old ring's cells the new one does not keep, each column from its top. Clearing and taking down
  *  are loose operations (the leaves' state may change once their trunk is gone); the material comes back to the hall. */
 private static CompoundTag fittedProject(ServerLevel l,Plan plan){
  var ops=new ListTag();var cost=new CompoundTag();var air=Blocks.AIR.defaultBlockState();var cleared=new HashSet<>(plan.clear());
  var clears=new HashMap<Long,List<BlockPos>>();for(var p:plan.clear())clears.computeIfAbsent(column(p),k->new ArrayList<>()).add(p);
  var laid=new LinkedHashMap<Long,List<Map.Entry<BlockPos,BlockState>>>();for(var col:plan.ring())laid.put(column(col),new ArrayList<>());
  for(var cell:plan.blocks().entrySet())laid.computeIfAbsent(column(cell.getKey()),k->new ArrayList<>()).add(cell);
  for(var col:laid.entrySet()){
   for(var p:clears.getOrDefault(col.getKey(),List.of())){var now=l.getBlockState(p);if(now.isAir())continue;ops.add(loose("clear",p,now,air));}
   for(var cell:col.getValue()){var now=l.getBlockState(cell.getKey());if(now.getBlock()==cell.getValue().getBlock())continue;
    BlockState before;if(cleared.contains(cell.getKey()))before=air;
    // AD-157: the wall's own block of the old material (a palisade growing into stone) is taken down first, its material back to the hall.
    else if(palette(now)){ops.add(loose("wall_clear",cell.getKey(),now,air));before=air;}
    else if(!now.isAir()&&!now.canBeReplaced())continue;else before=now;
    var item=BuiltInRegistries.ITEM.getKey(cell.getValue().getBlock().asItem()).toString();
    var op=new CompoundTag();op.putString("kind","wall");op.putLong("pos",cell.getKey().asLong());op.put("before",NbtUtils.writeBlockState(before));op.put("after",NbtUtils.writeBlockState(cell.getValue()));op.putString("item",item);
    ops.add(op);cost.putInt(item,cost.getInt(item)+1);}
  }
  for(var p:plan.retire()){var now=l.getBlockState(p);if(palette(now))ops.add(loose("wall_clear",p,now,air));}
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putString("kind","wall");t.put("ops",ops);t.put("cost",cost);t.putInt("index",0);
  t.put("cargo",new ListTag());t.put("returns",new ListTag());t.putInt("withdrawals",0);t.putInt("deposits",0);t.putBoolean("complete",ops.isEmpty());t.put("cells",new ListTag());
  return t;
 }
 private static CompoundTag loose(String kind,BlockPos pos,BlockState before,BlockState after){
  var op=new CompoundTag();op.putString("kind",kind);op.putLong("pos",pos.asLong());op.put("before",NbtUtils.writeBlockState(before));op.put("after",NbtUtils.writeBlockState(after));op.putString("item","");op.putBoolean("loose",true);return op;}
 /** Queues a fitted wall's project and remembers it (schema 2). The draft record rides in the project, so a crash between the two
  *  writes is recovered by tick (CF8). */
 private static String commit(ServerLevel l,SettlementData.Entry e,Plan plan,int headroom,CompoundTag project,boolean repair){
  var id=e.settlement().id();var previous=record(l.getServer(),id);
  var t=new CompoundTag();t.putInt("schema",2);t.putString("shape","fitted");t.putInt("radius",plan.radius());t.putIntArray("radii",plan.radiiX10());t.putInt("headroom",headroom);
  t.putInt("generation",(previous==null?0:previous.getInt("generation"))+1);t.putInt("defence",built(l,e,previous));t.putUUID("project",project.getUUID("id"));t.putBoolean("repair",repair);
  var cells=new LinkedHashSet<Long>();if(previous!=null)for(long c:previous.getLongArray("cells"))cells.add(c);
  for(var p:plan.blocks().keySet())cells.add(p.asLong());t.putLongArray("cells",cells.stream().mapToLong(Long::longValue).toArray());
  t.putLongArray("retiring",plan.retire().stream().mapToLong(BlockPos::asLong).toArray());
  t.putLongArray("gates",plan.gates().stream().mapToLong(BlockPos::asLong).toArray());
  t.putLongArray("towers",plan.towers().stream().mapToLong(BlockPos::asLong).toArray());
  t.putLong("center",e.center().asLong());
  project.put("wallRecord",t.copy());
  if(!Roads.order(l,e,project))return "busy";
  NbtRecord.write(path(l.getServer(),id),t);WALL_CELLS.remove(id);PREVIEWS.clear();
  return "";
 }
 /** Whether the hall holds the whole cost of a project (CF10): the wall that follows the village never holds the builders waiting. */
 static boolean affordable(ServerLevel l,SettlementData.Entry e,CompoundTag cost){
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(chest==null)return false;
  for(var key:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(key));if(HallReserve.count(l,e,hall,chest,s->s.is(item))<cost.getInt(key))return false;}
  return true;
 }
 /** Whether the hall's chest, once the project's stone is taken out, can hold everything the crew brings back (the old stretch's stone,
  *  the cleared trunks and rock). A crew that cannot unload never finishes: the project would hold the builders — and every road of the
  *  village — for good, so an automatic extension waits for room instead. */
 public static boolean room(ServerLevel l,SettlementData.Entry e,CompoundTag project){
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(chest==null)return false;
  var slots=new ArrayList<net.minecraft.world.item.ItemStack>();for(int i=0;i<chest.getContainerSize();i++)slots.add(chest.getItem(i).copy());
  var cost=project.getCompound("cost");
  for(var key:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(key));int left=cost.getInt(key);
   for(var st:slots){if(left<=0)break;if(!st.is(item))continue;int take=Math.min(left,st.getCount());st.shrink(take);left-=take;}}
  var blocks=l.holderLookup(net.minecraft.core.registries.Registries.BLOCK);
  for(var raw:project.getList("ops",Tag.TAG_COMPOUND)){var op=(CompoundTag)raw;if(!op.getString("item").isEmpty())continue;
   var back=Roads.drop(NbtUtils.readBlockState(blocks,op.getCompound("before")));if(back.isEmpty())continue;boolean put=false;
   for(var st:slots)if(!st.isEmpty()&&net.minecraft.world.item.ItemStack.isSameItemSameTags(st,back)&&st.getCount()<Math.min(chest.getMaxStackSize(),st.getMaxStackSize())){st.grow(1);put=true;break;}
   if(!put)for(int i=0;i<slots.size();i++)if(slots.get(i).isEmpty()){slots.set(i,back.copy());put=true;break;}
   if(!put)return false;}
  return true;
 }
 private static final Map<UUID,Long> FOLLOW_AT=new HashMap<>();
 /** AD-127: a fitted wall follows its village — at most once per extend_every, when the builders are free: grown past it, the ring is
  *  extended (new stretch first, the old one taken down after, its stone back in the hall); otherwise its breaches are mended. An old
  *  square or round wall is replaced only when outgrown (NPC mayors call this for theirs). Ordered only when the hall holds the whole
  *  cost. Returns "extend:<generation>", "repair", "wall_waiting_stone", a refusal or empty. */
 public static String follow(ServerLevel l,SettlementData.Entry e,boolean now){
  var id=e.settlement().id();var t=record(l.getServer(),id);if(t==null)return "";
  long time=l.getGameTime();var at=FOLLOW_AT.get(id);if(!now&&at!=null&&time<at)return "";FOLLOW_AT.put(id,time+FIT.extendEvery());
  if(Roads.active(l,id)||!refusal(l,e).isEmpty())return "";
  boolean fitted=t.getString("shape").equals("fitted"),out=outgrown(e,t);
  if(!fitted&&!out)return "";
  int headroom=t.contains("headroom")?t.getInt("headroom"):FIT.headroom();
  var plan=fitted(l,e,headroom);if(!plan.reason().isEmpty())return plan.reason();
  var project=fittedProject(l,plan);
  boolean work=project.getList("ops",Tag.TAG_COMPOUND).stream().anyMatch(o->!((CompoundTag)o).getString("kind").equals("clear"));
  if(!work)return "";
  if(!affordable(l,e,project.getCompound("cost")))return "wall_waiting_stone";
  if(!room(l,e,project))return "wall_waiting_room";
  var reason=commit(l,e,plan,headroom,project,!out);
  return !reason.isEmpty()?reason:out?"extend:"+record(l.getServer(),id).getInt("generation"):"repair";
 }
 /** The builders' project that puts the wall up, block by block from the hall's stone: once round the ring, each column from the ground
  *  up where the builder stands, so it walks the ring once instead of once per course. */
 public static CompoundTag project(ServerLevel l,Plan plan){
  if(plan.shape()==Shape.FITTED)return fittedProject(l,plan);
  var ops=new ListTag();var cost=new CompoundTag();
  var cells=new ArrayList<>(plan.blocks().entrySet());
  for(var cell:cells){var now=l.getBlockState(cell.getKey());if(now.getBlock()==cell.getValue().getBlock())continue;if(!now.isAir()&&!now.canBeReplaced())continue;
   var item=BuiltInRegistries.ITEM.getKey(cell.getValue().getBlock().asItem()).toString();
   var op=new CompoundTag();op.putString("kind","wall");op.putLong("pos",cell.getKey().asLong());op.put("before",NbtUtils.writeBlockState(now));op.put("after",NbtUtils.writeBlockState(cell.getValue()));op.putString("item",item);
   ops.add(op);cost.putInt(item,cost.getInt(item)+1);}
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putString("kind","wall");t.put("ops",ops);t.put("cost",cost);t.putInt("index",0);
  t.put("cargo",new ListTag());t.put("returns",new ListTag());t.putInt("withdrawals",0);t.putInt("deposits",0);t.putBoolean("complete",ops.isEmpty());t.put("cells",new ListTag());
  return t;
 }
 /** Research: the wall itself takes guard posts (defense.1), its archer towers archer stations (defense.2). */
 public static String refusal(ServerLevel l,SettlementData.Entry e){
  if(!ResearchGate.has(l,e,"defense.1"))return "research";
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  return "";
 }
 /** The mayor orders the wall: planned, queued for the builders and remembered with its gates and tower sites. */
 public static String order(ServerLevel l,SettlementData.Entry e,Shape shape,int radius){
  var refusal=refusal(l,e);if(!refusal.isEmpty())return refusal;
  var plan=plan(l,e,shape,radius);if(!plan.reason().isEmpty())return plan.reason();
  // AD-127: a fitted wall ordered again is rebuilt to the village as it is now — extended where outgrown, else mended.
  if(shape==Shape.FITTED){var project=fittedProject(l,plan);var old=record(l.getServer(),e.settlement().id());
   return commit(l,e,plan,headroom(radius),project,old!=null&&old.getString("shape").equals("fitted")&&!outgrown(e,old));}
  var project=project(l,plan);
  if(!Roads.order(l,e,project))return "busy";
  var t=new CompoundTag();t.putInt("schema",1);t.putString("shape",shape.name().toLowerCase(Locale.ROOT));t.putInt("radius",radius);t.putUUID("project",project.getUUID("id"));
  // The cells of a wall ordered before stay the village's: a second ring does not unprotect the first.
  var cells=new LinkedHashSet<Long>();var previous=record(l.getServer(),e.settlement().id());if(previous!=null)for(long c:previous.getLongArray("cells"))cells.add(c);
  for(var p:plan.blocks().keySet())cells.add(p.asLong());t.putLongArray("cells",cells.stream().mapToLong(Long::longValue).toArray());
  t.putLongArray("gates",plan.gates().stream().mapToLong(BlockPos::asLong).toArray());
  t.putLongArray("towers",plan.towers().stream().mapToLong(BlockPos::asLong).toArray());
  NbtRecord.write(path(l.getServer(),e.settlement().id()),t);WALL_CELLS.remove(e.settlement().id());
  return "";
 }
 private static final Map<UUID,Set<Long>> WALL_CELLS=new HashMap<>();
 /** A block of a settlement's wall: nobody takes it down, as nobody takes down a building of the village. */
 public static boolean wallBlock(ServerLevel l,BlockPos pos){
  for(var e:SettlementData.get(l.getServer()).entries()){
   if(!e.dimension().equals(l.dimension().location().toString()))continue;
   var cells=WALL_CELLS.computeIfAbsent(e.settlement().id(),id->{var t=record(l.getServer(),id);var set=new HashSet<Long>();if(t!=null)for(long c:t.getLongArray("cells"))set.add(c);return set;});
   if(cells.contains(pos.asLong())&&!l.getBlockState(pos).isAir())return true;
  }
  return false;
 }
 public static void forget(){WALL_CELLS.clear();BOXES.clear();PREVIEWS.clear();FOLLOW_AT.clear();}
 /** Once the wall stands, its towers are built one after another through the ordinary building queue — when the research allows. */
 public static String tick(ServerLevel l,SettlementData.Entry e){
  // A registered tower can also stand alone, without an enclosing wall project.
  Ballistas.tick(l,e);
  var id=e.settlement().id();var project=Roads.project(l,id);var t=record(l.getServer(),id);
  // AD-127 (CF8): a fitted wall's project carries its record; one queued without its record written (a crash between) is taken up.
  if(project!=null&&project.contains("wallRecord")&&(t==null||!t.hasUUID("project")||!project.getUUID("id").equals(t.getUUID("project")))){
   t=project.getCompound("wallRecord").copy();NbtRecord.write(path(l.getServer(),id),t);WALL_CELLS.remove(id);}
  if(t==null)return TowerStages.follow(l,e);
  // CF7: the old ring's cells stay the village's until their project is done (or replaced); then they are nobody's.
  if(t.getLongArray("retiring").length>0&&(project==null||!project.getUUID("id").equals(t.getUUID("project"))||project.getBoolean("complete"))){
   var gone=new HashSet<Long>();for(long c:t.getLongArray("retiring"))gone.add(c);
   t.putLongArray("cells",Arrays.stream(t.getLongArray("cells")).filter(c->!gone.contains(c)).toArray());t.putLongArray("retiring",new long[0]);
   NbtRecord.write(path(l.getServer(),id),t);WALL_CELLS.remove(id);}
  // AD-127: a fitted wall follows the village; once its project is done it is checked once for blocks the builders could not lay (CF3).
  if(t.getString("shape").equals("fitted")){
   boolean done=project!=null&&project.getUUID("id").equals(t.getUUID("project"))&&project.getBoolean("complete");
   if(done&&!t.getBoolean("checked")){t.putBoolean("checked",true);NbtRecord.write(path(l.getServer(),id),t);if(!t.getBoolean("repair"))follow(l,e,true);}
   else follow(l,e,false);
   t=record(l.getServer(),id);project=Roads.project(l,id);}
  if(project!=null&&project.getUUID("id").equals(t.getUUID("project"))&&!project.getBoolean("complete"))return "walling";
  if(!ResearchGate.has(l,e,"defense.2"))return "research";
  if(HallUpgradeGoal.pending(l,e.settlement().id()))return "busy";
  var retrofit=TowerStages.follow(l,e);if(!retrofit.isEmpty())return retrofit;
  // A site that cannot be built on now (somebody put something there) is passed over for the next one, and tried again later.
  String last="";
  boolean square=t.getString("shape").equals("square");int radius=t.getInt("radius");
  for(long raw:t.getLongArray("towers")){
   var site=square?movedCorner(e.center(),radius,BlockPos.of(raw)):BlockPos.of(raw);
   // A tower that stands is known by its place on the ground plan: once built, the top of its column is its own lantern, not the ground.
   boolean standing=e.settlement().buildings().stream().anyMatch(b->b.type().equals(TOWER)&&BuildingPlacement.origin(e,b).getX()==site.getX()-2&&BuildingPlacement.origin(e,b).getZ()==site.getZ()-2);
   if(standing)continue;
   var ground=ground(l,site);if(ground==null){last="unloaded";continue;}
   var origin=ground.offset(-2,0,-2);
   // The tower's door faces the village.
   int turns=towerTurns(e.center(),site);
   var reason=BuildingOrders.approve(l,e,TOWER,turns,origin);
   if(reason.isEmpty())return "tower";
   last=reason;
  }
  return last.isEmpty()?"done":"tower_"+last;
 }
 /** The archers serving on one tower. */
 public static long archers(Settlement s,Settlement.Building tower){
  return s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.ARCHER_GUARD&&s.workplace(r.id())!=null&&s.workplace(r.id()).id().equals(tower.id())).count();}
 /** Who the mayor may send up a tower: a trained adult without a trade first, then an archer of the archery. Never an untrained villager
  *  (AD-095) and never a guard with a sword: an archer needs a bow. */
 public static Resident candidate(Settlement s){
  for(int pass=0;pass<2;pass++)for(var r:s.residents()){
   if(!r.alive()||r.life()!=Resident.Life.ADULT||!r.military())continue;var at=s.workplace(r.id());
   if(pass==0?r.profession()==null:r.profession()==Profession.ARCHER_GUARD&&at!=null&&at.type().equals("archery"))return r;}
  return null;}
 /** Why an archer cannot be sent up this tower now, or empty. */
 public static String postRefusal(Settlement s,Settlement.Building tower){
  if(!tower.type().equals(TOWER))return "building";
  if(archers(s,tower)>=ARCHERS_PER_TOWER)return "full";
  return candidate(s)==null?"no_archer":"";}
 /** The mayor's order: the chosen archer serves on this tower from now on. */
 public static String post(Settlement s,Settlement.Building tower){
  var reason=postRefusal(s,tower);if(!reason.isEmpty())return reason;
  s.assign(candidate(s).id(),Profession.ARCHER_GUARD,tower.id());return "";}
 /** Quarter turns that put a tower's door toward the village centre: the door is on the tower's north side (local z=0), and each
  *  clockwise turn carries that side east, south, west (BuildingPlacement.turn). */
 public static int towerTurns(BlockPos center,BlockPos site){
  int dx=center.getX()-site.getX(),dz=center.getZ()-site.getZ();
  if(Math.abs(dz)>=Math.abs(dx))return dz<0?0:2;
  return dx>0?1:3;
 }
}
