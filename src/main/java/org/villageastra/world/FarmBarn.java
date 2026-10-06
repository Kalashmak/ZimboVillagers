package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-130 (owner 2026-09-22): the farm's barn — from level IV a building around the 2x3 field, with a second floor of six fields (IV) and a
 *  third (V); at VI the machinery on its stair tower's landings sows and reaps every floor. Farm-local, east side (a west farm mirrors x -> 6-x
 *  about its farmhouse), turned with the farm by BuildingPlacement. Not part of the farmhouse's 7x7 design: like the AD-104 fields it is field
 *  work of the farm's upgrade projects (a deliberate exception to "everything in the lot", AD-130).
 *  <ul><li>Walls at x -2 and 17, z 8 and 36 around the field (x -1..16, z 9..35), a stone plinth, a dark oak frame (posts, plate beams at every
 *  deck) and cream plaster; decks of dark oak planks at y4 and y9 (the ground of floors 1 and 2) and a ceiling at y14 from V; three blocks
 *  of air over every floor's plots.</li>
 *  <li>Light: four hanging lanterns over each field, from the deck above (FarmField.localLanterns): every plot gets 9 or more, as crops need.</li>
 *  <li>Roof: two parallel gables along z (x -3..7 and 8..18, ridges x2 and x13) at 45 degrees with a valley between, eaves one block out;
 *  deepslate brick at IV and VI, tile at V (LevelArchitecture's ladder). At V the walls rise to y14 and the roof is relaid on them.</li>
 *  <li>Stair tower (x9..15, z1..7) beside the farmhouse, the farm's landmark: a street door, a passage into the barn, switchback stairs of five
 *  rises a floor with three blocks of air over every step, landings flush with the upper farmland (no trampling), a hipped cap one storey
 *  over the ridge and a hoist beam with a chain and a barrel on its street face.</li>
 *  <li>VI: on each landing (y1, y6, y11) a barrel (seed bin), a grindstone (drive), a chain and a composter — 12 blocks, all workshop goods.</li></ul> */
public final class FarmBarn {
 private FarmBarn(){}
 public static final int WX0=-2,WX1=17,WZ0=8,WZ1=36,TX0=9,TX1=15,TZ0=1,TZ1=7,TC=12,TZC=4;
 /** Posts of the walls along x (front and back) and along z (sides). */
 private static final Set<Integer> POSTS_X=Set.of(-2,1,4,7,8,13,17),POSTS_Z=Set.of(8,11,14,17,20,23,26,29,32,36);
 /** Windows of the side walls (every other bay) and the storeys they light. */
 private static final Set<Integer> WINDOWS_Z=Set.of(9,15,21,27,33);
 /** Wall plate of the barn at a level: IV on the y9 deck, V and VI on the y14 ceiling. */
 public static int top(int level){return level>=5?14:9;}
 /** Top of the tower's walls: one storey over the ridge. */
 public static int towerTop(int level){return top(level)+7;}
 private static final Map<Integer,Map<BlockPos,BlockState>> LAYOUTS=new java.util.concurrent.ConcurrentHashMap<>();
 /** The barn a farm kept at this level has (east, unturned): empty below IV. Explicit air where the barn must be open (doorways, the tower's
  *  stairwell). The upper floors' ground is included (FarmBarn lays it with the deck that carries it). */
 public static Map<BlockPos,BlockState> layout(int level){return level<FarmField.BARN_FROM?Map.of():LAYOUTS.computeIfAbsent(Math.min(6,level),FarmBarn::build);}
 /** The same on a side. */
 public static Map<BlockPos,BlockState> layout(int level,boolean west){
  if(!west)return layout(level);var out=new LinkedHashMap<BlockPos,BlockState>();
  for(var c:layout(level).entrySet())out.put(mirror(c.getKey()),c.getValue().mirror(Mirror.FRONT_BACK));return out;
 }
 static BlockPos mirror(BlockPos p){return new BlockPos(6-p.getX(),p.getY(),p.getZ());}
 /** AD-130: the barn level a farm has laid — its field level from IV (the barn and its floors are laid by the same project), 0 before or in
  *  a village that keeps the AD-104 table. */
 public static int laid(Settlement s,Settlement.Building b){if(FarmField.legacy(s))return 0;int f=s.fieldLevel(b.id());return f>=FarmField.BARN_FROM?Math.min(6,f):0;}
 // ---------- the design ----------
 private record Palette(BlockState plinth,BlockState low,BlockState quoin,BlockState post,BlockState stair,BlockState tiles){}
 private static Palette palette(int level){
  var post=(level>=6?Blocks.STRIPPED_DARK_OAK_LOG:Blocks.DARK_OAK_LOG).defaultBlockState();
  var stair=(level==5?Blocks.DEEPSLATE_TILE_STAIRS:Blocks.DEEPSLATE_BRICK_STAIRS).defaultBlockState();
  var tiles=(level==5?Blocks.DEEPSLATE_TILES:Blocks.DEEPSLATE_BRICKS).defaultBlockState();
  var plinth=(level>=6?Blocks.POLISHED_DEEPSLATE:Blocks.COBBLESTONE).defaultBlockState();
  var low=(level>=5?Blocks.STONE_BRICKS:Blocks.SMOOTH_SANDSTONE).defaultBlockState();
  var quoin=(level>=5?Blocks.CHISELED_STONE_BRICKS:Blocks.DARK_OAK_LOG).defaultBlockState();
  return new Palette(plinth,low,quoin,post,stair,tiles);
 }
 private static final BlockState AIR=Blocks.AIR.defaultBlockState(),PLASTER=Blocks.SMOOTH_SANDSTONE.defaultBlockState(),PLANKS=Blocks.DARK_OAK_PLANKS.defaultBlockState(),
  COBBLE=Blocks.COBBLESTONE.defaultBlockState(),LANTERN=Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true);
 private static BlockState beam(Direction.Axis axis){return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,axis);}
 /** AD-144 (owner 2026-09-23): the barn's windows are dark oak framed windows (AD-142), the wood of its frame, set along their wall. */
 private static BlockState pane(boolean alongX){return org.villageastra.VillageAstra.FRAMED_WINDOWS.get(org.villageastra.domain.FramedWindows.DEFAULT_WOOD).get().defaultBlockState().setValue(FramedWindowBlock.AXIS,alongX?Direction.Axis.X:Direction.Axis.Z);}
 private static BlockState stair(BlockState s,Direction facing){return s.setValue(StairBlock.FACING,facing).setValue(StairBlock.HALF,Half.BOTTOM).setValue(StairBlock.SHAPE,StairsShape.STRAIGHT);}
 /** One wall cell: the plinth course (y0..1), the ground storey (y2..4: plaster at IV, stone brick from V), plaster above with the frame. */
 private static BlockState wall(Palette p,int level,int y,boolean post,boolean corner,Direction.Axis along,boolean plate){
  if(y==0)return p.plinth();
  if(y==1)return level==4?COBBLE:level==5?(corner?p.quoin():p.low()):p.plinth();
  if(y<=4&&level>=5)return corner?p.quoin():p.low();
  if(post||corner)return p.post().setValue(RotatedPillarBlock.AXIS,Direction.Axis.Y);
  if(plate)return beam(along);
  return PLASTER;
 }
 private static Map<BlockPos,BlockState> build(int level){
  var m=new LinkedHashMap<BlockPos,BlockState>();var p=palette(level);int top=top(level),tTop=towerTop(level);
  // Walls around the field: sides (x -2, 17) one course above the plate under the eaves, front and back to the plate, gables above.
  for(int z=WZ0;z<=WZ1;z++)for(int x:new int[]{WX0,WX1})for(int y=0;y<=top+1;y++){boolean corner=z==WZ0||z==WZ1;
   var s=wall(p,level,y,POSTS_Z.contains(z),corner,Direction.Axis.Z,y==4||y==9||y==14);
   if(y==top+1&&!corner)s=PLASTER;
   if(!corner&&WINDOWS_Z.contains(z)&&(y==2||y==6||y==11&&level>=5))s=pane(false);
   m.put(new BlockPos(x,y,z),s);}
  for(int x=WX0+1;x<WX1;x++)for(int z:new int[]{WZ0,WZ1})for(int y=0;y<=top;y++)m.put(new BlockPos(x,y,z),wall(p,level,y,POSTS_X.contains(x),false,Direction.Axis.X,y==4||y==9||y==14));
  // Gables under the two roofs, a loft window in the back ones.
  for(int x=WX0;x<=WX1;x++){int h=roofY(level,x);for(int z:new int[]{WZ0,WZ1})for(int y=top+1;y<h;y++)m.put(new BlockPos(x,y,z),PLASTER);}
  for(int x:new int[]{2,13})m.put(new BlockPos(x,top+2,WZ1),pane(true));
  // Openings: the passage behind the farmhouse and from the tower (ground), the barn doors in the back wall, the doorways of the landings.
  for(int y=1;y<=2;y++){m.put(new BlockPos(3,y,WZ0),AIR);m.put(new BlockPos(TC,y,WZ0),AIR);m.put(new BlockPos(TC,y,TZ1),AIR);m.put(new BlockPos(TC,y,TZ0),AIR);}
  for(int y=1;y<=3;y++)for(int x:new int[]{2,3,11,12})m.put(new BlockPos(x,y,WZ1),AIR);
  landing(m,14,1);if(level>=5)landing(m,10,2);
  // Decks (the upper floors' ground) and the ceiling of V; the upper fields' soil, water and cover; four lanterns over every field.
  var modules=FarmField.modules(level);
  for(int x=WX0+1;x<WX1;x++)for(int z=WZ0+1;z<WZ1;z++){m.put(new BlockPos(x,4,z),PLANKS);m.put(new BlockPos(x,9,z),PLANKS);if(level>=5)m.put(new BlockPos(x,14,z),PLANKS);}
  for(var mod:modules){var c=FarmField.corner(mod);var w=FarmField.localWater(mod);
   if(FarmField.floor(mod)>0)for(int x=0;x<FarmField.MODULE;x++)for(int z=0;z<FarmField.MODULE;z++){var g=c.offset(x,0,z);
    if(g.equals(w)){m.put(g,Blocks.WATER.defaultBlockState());m.put(g.above(),FarmField.COVER);}else m.put(g,Blocks.DIRT.defaultBlockState());}
   for(var lamp:FarmField.localLanterns(mod))m.put(lamp,LANTERN);}
  // At IV the floor-0 fields are enclosed too: their lanterns hang from the first deck.
  for(var mod:FarmField.modules(3))for(var lamp:FarmField.localLanterns(mod))m.put(lamp,LANTERN);
  roof(m,level,p);
  tower(m,level,p,tTop);
  return Collections.unmodifiableMap(FramedWindowBlock.join(m));
 }
 /** The doorway of floor f from the tower at x: a plank threshold flush with the farmland in both walls, two blocks of air over it. */
 private static void landing(Map<BlockPos,BlockState> m,int x,int f){int y=FarmField.FLOOR_PITCH*f;
  for(int z:new int[]{TZ1,WZ0}){m.put(new BlockPos(x,y,z),PLANKS);m.put(new BlockPos(x,y+1,z),AIR);m.put(new BlockPos(x,y+2,z),AIR);}}
 /** Height of the roof over column x: the two gables rise at 45 degrees from the eaves (x -3 and 18) to the ridges (x2 and 13). */
 public static int roofY(int level,int x){int c=x<=7?2:13;return top(level)+6-Math.min(5,Math.abs(x-c));}
 private static void roof(Map<BlockPos,BlockState> m,int level,Palette p){
  for(int x=-3;x<=18;x++){int c=x<=7?2:13;int h=roofY(level,x);
   var s=x==c?p.tiles():stair(p.stair(),x<c?Direction.EAST:Direction.WEST);
   for(int z=7;z<=37;z++){if(z==7&&x>=TX0&&x<=TX1)continue;m.put(new BlockPos(x,h,z),s);}}
 }
 private static void tower(Map<BlockPos,BlockState> m,int level,Palette p,int tTop){
  // The stairwell: everything inside is open but the steps, landings, platforms and (VI) machinery; the floor is cobble.
  for(int x=TX0+1;x<TX1;x++)for(int z=TZ0+1;z<TZ1;z++){m.put(new BlockPos(x,0,z),COBBLE);for(int y=1;y<=tTop;y++)m.put(new BlockPos(x,y,z),AIR);}
  for(int x=TX0;x<=TX1;x++)for(int z=TZ0;z<=TZ1;z++){boolean edge=x==TX0||x==TX1||z==TZ0||z==TZ1;if(!edge)continue;boolean corner=(x==TX0||x==TX1)&&(z==TZ0||z==TZ1);
   for(int y=0;y<=tTop+1;y++){var key=new BlockPos(x,y,z);if(m.containsKey(key))continue;
    var s=wall(p,level,y,corner,corner,z==TZ0||z==TZ1?Direction.Axis.X:Direction.Axis.Z,y%5==4);
    boolean window=(y==7||y==12&&level>=5||y==tTop-2)&&(x==TX0||x==TX1)&&z==TZC||(y==6||y==11&&level>=5)&&z==TZ0&&x==TC;
    m.put(key,window?pane(z!=TZ0&&z!=TZ1?false:true):s);}}
  // Flight to floor 1 along x14 (z2..5, rising south), its landing at (14,5,6); from V the flight to floor 2 west along z6 to (10,9,6).
  var step=Blocks.SPRUCE_STAIRS.defaultBlockState();
  for(int i=0;i<4;i++)m.put(new BlockPos(14,1+i,2+i),stair(step,Direction.SOUTH));
  m.put(new BlockPos(14,5,6),PLANKS);
  for(int x=10;x<=11;x++)for(int z=2;z<=3;z++)m.put(new BlockPos(x,5,z),PLANKS);
  if(level>=5){for(int i=0;i<4;i++)m.put(new BlockPos(13-i,6+i,6),stair(step,Direction.WEST));for(int x=10;x<=11;x++)for(int z=2;z<=3;z++)m.put(new BlockPos(x,10,z),PLANKS);}
  // VI: the machinery of each landing — seed bin, drive, chain and composter.
  if(level>=6)m.putAll(machinery());
  // The hipped cap: four courses from the eave (one out over the walls) to a single block, then the hoist on the street face.
  for(int r=0;r<=4;r++){int x0=TX0-1+r,x1=TX1+1-r,z0=TZ0-1+r,z1=TZ1+1-r,y=tTop+1+r;
   for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){boolean xe=x==x0||x==x1,ze=z==z0||z==z1;if(!xe&&!ze)continue;var at=new BlockPos(x,y,z);
    if(r==4||xe&&ze)m.put(at,p.tiles());else m.put(at,stair(p.stair(),xe?(x==x0?Direction.EAST:Direction.WEST):(z==z0?Direction.SOUTH:Direction.NORTH)));}}
  for(int z=0;z<=1;z++)m.put(new BlockPos(TC,tTop-1,z),Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.Z));
  m.put(new BlockPos(TC,tTop-2,0),Blocks.CHAIN.defaultBlockState());m.put(new BlockPos(TC,tTop-3,0),Blocks.CHAIN.defaultBlockState());
  m.put(new BlockPos(TC,tTop-4,0),Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING,Direction.UP));
 }
 /** AD-130: the machinery of level VI on the three landings (y1, y6, y11): its 12 cells and blocks. */
 public static Map<BlockPos,BlockState> machinery(){
  var out=new LinkedHashMap<BlockPos,BlockState>();
  for(int f=0;f<3;f++){int y=1+FarmField.FLOOR_PITCH*f;
   out.put(new BlockPos(10,y,2),Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING,Direction.UP));
   out.put(new BlockPos(11,y,2),Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE,AttachFace.FLOOR).setValue(GrindstoneBlock.FACING,Direction.SOUTH));
   out.put(new BlockPos(10,y,3),Blocks.COMPOSTER.defaultBlockState());
   out.put(new BlockPos(11,y,3),Blocks.CHAIN.defaultBlockState());}
  return out;
 }
 /** The seed bin of a floor's machine (farm-local, east). */
 public static BlockPos bin(int floor){return new BlockPos(10,1+FarmField.FLOOR_PITCH*floor,2);}
 // ---------- the world ----------
 private static BlockPos local(BlockPos p,boolean west){return west?mirror(p):p;}
 /** World position of a farm-local (east) barn cell of this farm, on its side. */
 public static BlockPos at(SettlementData.Entry e,Settlement.Building b,BlockPos east){var p=local(east,e.settlement().westField(b.id()));return BuildingPlacement.at(e,b,p.getX(),p.getY(),p.getZ());}
 private static BlockState turned(SettlementData.Entry e,Settlement.Building b,BlockState east){var s=e.settlement().westField(b.id())?east.mirror(Mirror.FRONT_BACK):east;return BuildingPlacement.state(s,b.rotation());}
 /** AD-130: whether the barn cells one level brings stand: IV the lanterns of floors 0 and 1, V those of floor 2, VI the machinery. A farm
  *  kept at IV..VI works no higher than the last level whose cells all stand (BuildingTiers.level), so a half-built or wrecked barn
  *  does not work the floor it no longer lights. */
 public static boolean stands(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level){
  var cells=new ArrayList<Map.Entry<BlockPos,BlockState>>();
  if(level==4)for(var mod:FarmField.modules(4))if(FarmField.floor(mod)<=1)for(var lamp:FarmField.localLanterns(mod))cells.add(Map.entry(lamp,LANTERN));
  if(level==5)for(var mod:FarmField.added(5,false))for(var lamp:FarmField.localLanterns(mod))cells.add(Map.entry(lamp,LANTERN));
  if(level==6)cells.addAll(machinery().entrySet());
  for(var c:cells){var pos=at(e,b,c.getKey());if(!l.hasChunkAt(pos)||!l.getBlockState(pos).is(c.getValue().getBlock()))return false;}
  return true;
 }
 /** AD-130: whether a world position is a cell of this farm's barn as laid (protection: the settlement's, nobody else breaks it). */
 public static boolean contains(SettlementData.Entry e,Settlement.Building b,BlockPos world){
  int level=laid(e.settlement(),b);if(level<FarmField.BARN_FROM)return false;
  var p=local(BuildingPlacement.local(e,b,world),e.settlement().westField(b.id()));var s=layout(level).get(p);return s!=null&&!s.isAir();
 }
 /** Farm-local bounds {minX,minY,minZ,maxX,maxY,maxZ} of the barn of a level on a side (empty array below IV). */
 public static int[] box(int level,boolean west){
  var cells=layout(level,west).keySet();if(cells.isEmpty())return new int[0];
  return new int[]{cells.stream().mapToInt(BlockPos::getX).min().orElseThrow(),cells.stream().mapToInt(BlockPos::getY).min().orElseThrow(),cells.stream().mapToInt(BlockPos::getZ).min().orElseThrow(),
   cells.stream().mapToInt(BlockPos::getX).max().orElseThrow(),cells.stream().mapToInt(BlockPos::getY).max().orElseThrow(),cells.stream().mapToInt(BlockPos::getZ).max().orElseThrow()};
 }
 /** Items one cell costs (water none; null: not payable). */
 private static List<String> items(BlockState before,BlockState after){if(after.isAir()||after.is(Blocks.WATER))return List.of();return BuildingOrders.materials(before,after);}
 private static boolean same(BlockState now,BlockState design){return design.isAir()?now.isAir():BuildingRepairs.present(now,design)&&(!(design.getBlock() instanceof StairBlock)||now.equals(design));}
 /** AD-130: what the barn of this level adds to the farm's upgrade on paper — every cell that differs from the barn of the level before. */
 public static Map<String,Integer> addedCost(int level){
  var out=new TreeMap<String,Integer>();if(level<FarmField.BARN_FROM)return out;var before=layout(level-1);
  for(var c:layout(level).entrySet()){var old=before.getOrDefault(c.getKey(),AIR);if(same(old,c.getValue()))continue;var it=items(old,c.getValue());if(it==null)throw new IllegalStateException("Barn block without an item: "+c.getValue());for(var i:it)if(!i.isEmpty())out.merge(i,1,Integer::sum);}
  return out;
 }
 // ---------- the builders' plan ----------
 /** Scaffold columns the barn is built from: inside the barn on plots clear of water and lanterns, and one in the stairwell. */
 private static final int[] COLUMN_X={0,7,15},COLUMN_Z={9,15,21,27,33};
 public record BarnPlan(ListTag ops,Map<String,Integer> cost,Set<BlockPos> conflicts,String reason){public boolean ok(){return reason.isEmpty()&&conflicts.isEmpty();}}
 private record Op(BlockPos pos,BlockState before,BlockState after,String item,String returned,BlockPos stand,boolean field){}
 private static int maxFeet(int level,int x,int z){return x==TC&&z==TZC?towerTop(level)+3:roofY(level,x)-2;}
 /** Cells worked from the stairwell's column: the tower and its cap, whose eave overhangs the barn's front wall above the barn's roof. */
 private static boolean tower(int level,BlockPos p){return p.getX()>=TX0-1&&p.getX()<=TX1+1&&(p.getZ()<=TZ1||p.getZ()==WZ0&&p.getY()>roofY(level,p.getX()));}
 /** The work of bringing this farm's barn to a level: every cell of the level's barn (and the air of the old one it no longer has) that the
  *  world does not show yet; cells above a builder's reach are worked from scaffold columns inside the barn (their caps are the decks and
  *  the upper soil the column passes). Order: clears, the lower work, the columns, the upper work, the upper floors' water and cover, the
  *  columns taken down (caps set). In the way (reason "barn"): anything that is not natural ground or plants and not this barn's own, a
  *  block entity, a fluid, a road. */
 public static BarnPlan plan(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level){
  var conflicts=new LinkedHashSet<BlockPos>();var none=new BarnPlan(new ListTag(),Map.of(),conflicts,"");
  var s=e.settlement();if(FarmField.legacy(s)||level<FarmField.BARN_FROM)return none;int from=laid(s,b);if(level<=from)return none;
  var target=new LinkedHashMap<BlockPos,BlockState>(layout(level));for(var old:layout(from).keySet())target.putIfAbsent(old,AIR);
  var own=new HashSet<BlockState>();for(var st:layout(from).values())own.add(st);
  var cols=new ArrayList<int[]>();for(int x:COLUMN_X)for(int z:COLUMN_Z)cols.add(new int[]{x,z});cols.add(new int[]{TC,TZC});
  var colCells=new HashSet<Long>();for(var c:cols)colCells.add(((long)c[0]<<32)|(c[1]&0xFFFFFFFFL));
  int[] tops=new int[cols.size()];
  var clears=new ArrayList<Op>();var lower=new ArrayList<Op>();var upper=new ArrayList<Op>();var wet=new ArrayList<Op>();var fixtures=new ArrayList<Op>();var lowFixtures=new ArrayList<Op>();var caps=new HashMap<BlockPos,Op>();
  for(var cell:target.entrySet()){var local=cell.getKey();var design=cell.getValue();var pos=at(e,b,local);
   if(!l.hasChunkAt(pos)||l.isOutsideBuildHeight(pos))return new BarnPlan(new ListTag(),Map.of(),conflicts,"unloaded");
   var after=turned(e,b,design);var now=l.getBlockState(pos);
   boolean column=colCells.contains(((long)local.getX()<<32)|(local.getZ()&0xFFFFFFFFL))&&local.getY()>=1;
   // An unchanged deck or roof in the column's shaft still has to be restored
   // after the paid scaffold is removed. Reserve its replacement explicitly.
   if(same(now,after)&&!(column&&caps(after)))continue;
   boolean mine=own.contains(now)&&layout(from).containsKey(local)||now.is(Blocks.FARMLAND)||FarmField.waterCover(now,b)&&local.getY()%FarmField.FLOOR_PITCH==1;
   boolean fluid=!now.getFluidState().isEmpty()&&!(now.is(Blocks.WATER)&&after.is(Blocks.WATER));
   if(l.getBlockEntity(pos)!=null&&!mine||fluid||!mine&&!FarmField.natural(now)||local.getY()<=1&&(Roads.cell(l,pos)!=null)){conflicts.add(pos);continue;}
   var it=items(column&&caps(after)?AIR:now,after);if(it==null)return new BarnPlan(new ListTag(),Map.of(),conflicts,"material");
   boolean soil=local.getY()>0&&local.getY()%FarmField.FLOOR_PITCH==0&&(after.is(Blocks.DIRT)||after.is(Blocks.WATER))||after.equals(FarmField.COVER);
   if(design.isAir()){clears.add(new Op(pos,now,AIR,"","",null,false));continue;}
   String item=it.isEmpty()?"":it.get(0);
   // Inside the barn the ground is farmland (no stand for a builder): everything above the plots is worked from the columns.
   boolean inside=local.getX()>WX0&&local.getX()<WX1&&local.getZ()>WZ0&&local.getZ()<WZ1&&local.getY()>=2;
   if(local.getY()<6&&!inside){var op=new Op(pos,now,after,item,"",null,soil);
    if(column&&caps(after)){caps.put(pos,op);continue;}
    if(after.is(Blocks.WATER)||after.equals(FarmField.COVER))wet.add(op);else if(after.is(Blocks.LANTERN))lowFixtures.add(op);else lower.add(op);continue;}
   if(column&&caps(after)){caps.put(pos,new Op(pos,now,after,item,"",null,soil));continue;}
   // A cell above reach: the nearest column that reaches it, feet one below it (no higher than the column may rise under its roof).
   int best=-1,feet=0;double dist=Double.MAX_VALUE;
   for(int i=0;i<cols.size();i++){var c=cols.get(i);boolean t=tower(level,local);if(t!=(i==cols.size()-1))continue;
    int f=Math.max(1,Math.min(local.getY()-1,maxFeet(level,c[0],c[1])));
    if(!BuildingOrders.reaches(c[0],f,c[1],local))continue;double d=Math.pow(c[0]-local.getX(),2)+Math.pow(c[1]-local.getZ(),2);if(d<dist){dist=d;best=i;feet=f;}}
   if(best<0){conflicts.add(pos);continue;}
   tops[best]=Math.max(tops[best],feet+1);var c=cols.get(best);var stand=at(e,b,new BlockPos(c[0],feet,c[1]));
   var op=new Op(pos,now,after,item,"",stand,soil);
   if(after.is(Blocks.WATER)||after.equals(FarmField.COVER))wet.add(op);else if(after.is(Blocks.LANTERN))fixtures.add(op);else upper.add(op);
  }
  if(!conflicts.isEmpty())return new BarnPlan(new ListTag(),Map.of(),conflicts,"barn");
  // A column rises through every cap above reach at its place (a deck, upper soil, the cap's last block), so the cap is set from it.
  var origin=BuildingPlacement.origin(e,b);
  for(var cap:caps.values()){var local=BuildingPlacement.local(e,b,cap.pos());var east=local(local,s.westField(b.id()));if(east.getY()<2)continue;
   for(int i=0;i<cols.size();i++)if(cols.get(i)[0]==east.getX()&&cols.get(i)[1]==east.getZ())tops[i]=Math.max(tops[i],east.getY());}
  var cleared=new HashSet<BlockPos>();for(var c:clears)cleared.add(c.pos());
  // The columns: a crop in the way is reaped by hand (the plot is sown again after), an old cell of the barn is cleared first.
  var scaffold=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState();var up=new ArrayList<Op>();var down=new ArrayList<Op>();
  for(int i=0;i<cols.size();i++){var c=cols.get(i);for(int y=1;y<=tops[i];y++){var local=new BlockPos(c[0],y,c[1]);var pos=at(e,b,local);
    if(!l.hasChunkAt(pos))return new BarnPlan(new ListTag(),Map.of(),conflicts,"unloaded");var now=l.getBlockState(pos);
    if(caps.containsKey(pos)){up.add(new Op(pos,caps.get(pos).before(),scaffold,"villageastra:timber_scaffold","",y>=3?at(e,b,new BlockPos(c[0],y-2,c[1])):null,false));continue;}
    if(!now.isAir()&&!cleared.contains(pos)){if(l.getBlockEntity(pos)!=null||!now.getFluidState().isEmpty()){conflicts.add(pos);continue;}clears.add(new Op(pos,now,AIR,"","",null,now.getBlock() instanceof CropBlock));}
    up.add(new Op(pos,AIR,scaffold,"villageastra:timber_scaffold","",y>=3?at(e,b,new BlockPos(c[0],y-2,c[1])):null,false));}
   // AD-130: a column stands on the farm's own plots; the cell it leaves is field work, so a crop the farmer sows there while the rest of
   // the barn goes up does not keep the project from completing (BuildingOrders.complete).
   for(int y=tops[i];y>=1;y--){var pos=at(e,b,new BlockPos(c[0],y,c[1]));boolean plot=i<cols.size()-1&&y<=2*FarmField.FLOOR_PITCH+1&&y%FarmField.FLOOR_PITCH==1;
    down.add(new Op(pos,scaffold,AIR,"","villageastra:timber_scaffold",null,plot));var cap=caps.remove(pos);
    if(cap!=null)down.add(new Op(pos,AIR,cap.after(),cap.item(),"",y>=3?at(e,b,new BlockPos(c[0],Math.max(1,y-2),c[1])):null,cap.field()));}}
  if(!conflicts.isEmpty())return new BarnPlan(new ListTag(),Map.of(),conflicts,"barn");
  // Caps no column passes (a column shorter than the cell): set in their turn.
  for(var cap:caps.values())(cap.pos().getY()-origin.getY()<6?lower:upper).add(cap);
  Comparator<Op> order=Comparator.<Op>comparingInt(o->o.pos().getY()).thenComparingInt(o->o.pos().getZ()).thenComparingInt(o->o.pos().getX());
  clears.sort(Comparator.<Op>comparingInt(o->-o.pos().getY()).thenComparingInt(o->o.pos().getZ()).thenComparingInt(o->o.pos().getX()));
  lower.sort(order);upper.sort(order);wet.sort(order);fixtures.sort(order);lowFixtures.sort(order);
  var all=new ArrayList<Op>();all.addAll(clears);all.addAll(lower);all.addAll(lowFixtures);
  var lowWet=wet.stream().filter(o->o.stand()==null).toList();all.addAll(lowWet);
  all.addAll(up);all.addAll(upper);all.addAll(fixtures);all.addAll(wet.stream().filter(o->o.stand()!=null).toList());all.addAll(down);
  var ops=new ListTag();var cost=new TreeMap<String,Integer>();int base=BuildingPlacement.origin(e,b).getY()+1;
  for(var op:all){var t=new CompoundTag();t.putLong("pos",op.pos().asLong());t.put("before",NbtUtils.writeBlockState(op.before()));t.put("after",NbtUtils.writeBlockState(op.after()));
   t.putBoolean("barn",true);if(op.field())t.putBoolean("field",true);
   if(!op.item().isEmpty()){t.putString("item",op.item());cost.merge(op.item(),1,Integer::sum);}
   if(!op.returned().isEmpty())t.putString("return",op.returned());
   if(op.stand()!=null){t.putLong("stand",op.stand().asLong());t.putInt("standBase",base);}
   ops.add(t);}
  return new BarnPlan(ops,Collections.unmodifiableMap(cost),conflicts,"");
 }
 /** What stands in the way of a barn plan, for tests and probes: the first cells farm-local (east) with the block found there. */
 public static String describe(ServerLevel l,SettlementData.Entry e,Settlement.Building b,Collection<BlockPos> conflicts){
  var out=new StringBuilder();int n=0;for(var p:conflicts){if(n++>=6)break;var local=local(BuildingPlacement.local(e,b,p),e.settlement().westField(b.id()));
   out.append(' ').append(local.toShortString()).append('=').append(id(l.getBlockState(p)));}return out.toString().trim();}
 /** A cell a column may pass through and set on its way down: a full, plain block (a deck plank, upper soil). */
 private static boolean caps(BlockState s){return !s.isAir()&&!(s.getBlock() instanceof EntityBlock)&&s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO);}
 /** The item id of a block, for tests and the card. */
 static String id(BlockState s){return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();}
}
