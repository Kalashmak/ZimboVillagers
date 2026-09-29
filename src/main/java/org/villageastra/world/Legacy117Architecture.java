package org.villageastra.world;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import org.villageastra.VillageAstra;
/** Frozen pre-AD117 layout evaluator for migration only. Do not update to new architecture.
 * AD-073: what a building of level II…VI looks like. The level-I design is rebuilt in place, inside the same lot:
 *  <ul><li>II — a stone brick plinth, lamps under the ceiling and the first equipment;</li>
 *  <li>III — a laid stone floor, storage and more equipment;</li>
 *  <li>IV (mechanized) — a polished floor, a brick roof, a drive post and iron equipment;</li>
 *  <li>V (automatic) — a deepslate floor and roof, brick walls, a lantern over a chest, a wall post on the roof and automation equipment;</li>
 *  <li>VI (complex) — deepslate tile floor, roof, plinth and walls, dark oak posts and the richest equipment.</li></ul>
 *  AD-112: from II every work building holds its core in one cell nearest its middle (the hall at (3,1,3) of its own blueprints); the core
 *  of level N stands there at grade N, so a level's equipment includes its core and each level above II adds one ring to it.
 *  Every next level rebuilds more of the shell than the one before and adds a bigger kit, so every upgrade costs more. Equipment goes to free cells
 *  along the inner walls, never in a doorway, beside a bed, the stock chest or a ladder; the cells are the same whatever level is asked for. */
public final class Legacy117Architecture {
 public static final int MAX=6;
 private Legacy117Architecture(){}
 private static final JsonObject DATA=read();
 private static JsonObject read(){
  try(var s=Legacy117Architecture.class.getResourceAsStream("/data/villageastra/migrations/ad117-levels.json")){
   if(s==null)throw new IllegalStateException("Missing levels balance");
   return JsonParser.parseReader(new java.io.InputStreamReader(s,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 static JsonObject data(){return DATA;}
 /** Kit type of a design: the second house shares the house kits, the upper halls share the hall kits. */
 static String kitType(String type){return type.equals("home_2")?"home":type.startsWith("town_hall")?"town_hall":type;}
 public static boolean hasLevels(String type){return DATA.getAsJsonObject("kits").has(kitType(type));}
 static List<Block> kit(String type,int level){
  var kits=DATA.getAsJsonObject("kits").getAsJsonObject(kitType(type));if(kits==null||!kits.has(String.valueOf(level)))return List.of();
  var out=new ArrayList<Block>();
  for(var raw:kits.getAsJsonArray(String.valueOf(level))){var block=BuiltInRegistries.BLOCK.get(new ResourceLocation(raw.getAsString()));if(block==Blocks.AIR)throw new IllegalStateException("Unknown kit block "+raw);out.add(block);}
  return out;
 }
 /** A cell of equipment that belongs to one level. */
 public record Placed(BlockPos local,BlockState state,int level){}
 private record Analysis(int w,int d,Set<BlockPos> floor,Set<BlockPos> plinth,Set<BlockPos> infill,Set<BlockPos> roof,Set<BlockPos> posts,List<BlockPos> lamps,List<BlockPos> columns,List<BlockPos> yard,Map<BlockPos,Integer> headroom,BlockPos core,BlockPos top){}
 private static final Map<String,Analysis> ANALYSES=new java.util.concurrent.ConcurrentHashMap<>();
 private static final Map<String,List<Placed>> EQUIPMENT=new java.util.concurrent.ConcurrentHashMap<>();
 private static boolean full(BlockState s){return !s.isAir()&&s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);}
 private static boolean soil(BlockState s){return s.is(BlockTags.DIRT)||s.is(Blocks.FARMLAND)||s.is(Blocks.DIRT_PATH)||s.is(Blocks.COARSE_DIRT)||s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL);}
 /** Structural materials a level may rebuild: masonry, planks, terracotta and stone — not equipment, glass, wool, hay or anything with contents. */
 private static boolean structural(BlockState s){
  if(!full(s)||s.getBlock() instanceof EntityBlock||s.is(BlockTags.LOGS))return false;
  return s.is(BlockTags.PLANKS)||s.is(BlockTags.TERRACOTTA)||s.is(Blocks.TERRACOTTA)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.STONE)||s.is(Blocks.STONE_BRICKS)||s.is(Blocks.BRICKS)
   ||s.is(Blocks.SMOOTH_STONE)||s.is(Blocks.SMOOTH_SANDSTONE)||s.is(Blocks.SANDSTONE)||s.is(Blocks.CALCITE)||s.is(Blocks.MUD_BRICKS)||s.is(Blocks.ANDESITE)||s.is(Blocks.POLISHED_ANDESITE)
   ||s.is(Blocks.CHISELED_STONE_BRICKS)||s.is(Blocks.DEEPSLATE_BRICKS)||s.is(Blocks.WHITE_TERRACOTTA);
 }
 private static Analysis analyse(String type,Map<BlockPos,BlockState> layout){
  var design=Legacy117BuildingBlueprints.design(type);int w=design.width(),d=design.depth();
  var cells=new HashMap<BlockPos,BlockState>();for(var e:layout.entrySet())cells.put(e.getKey(),e.getValue());
  var air=Blocks.AIR.defaultBlockState();
  java.util.function.Function<BlockPos,BlockState> at=p->cells.getOrDefault(p,air);
  var floor=new LinkedHashSet<BlockPos>();var plinth=new LinkedHashSet<BlockPos>();var infill=new LinkedHashSet<BlockPos>();var roof=new LinkedHashSet<BlockPos>();var posts=new LinkedHashSet<BlockPos>();
  var doors=new ArrayList<BlockPos>();var avoid=new HashSet<BlockPos>();var approach=new HashSet<BlockPos>();BlockPos top=null;var preset=new BlockPos[1];
  for(var e:cells.entrySet()){var p=e.getKey();var s=e.getValue();if(s.isAir())continue;
   boolean inside=p.getX()>=0&&p.getX()<w&&p.getZ()>=0&&p.getZ()<d;if(!inside)continue;
   boolean edge=p.getX()==0||p.getX()==w-1||p.getZ()==0||p.getZ()==d-1;
   if(s.getBlock() instanceof DoorBlock&&s.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER)doors.add(p);
   if(s.getBlock() instanceof BedBlock||s.is(VillageAstra.OWNED_CHEST.get())||s.is(Blocks.LADDER))avoid.add(p);
   // AD-112: a design that already holds its core (the hall's own blueprints) keeps that cell for it.
   if(s.getBlock() instanceof BuildingCoreBlock)preset[0]=p;
   // The chest is worked from the cell east of it and a bed is entered at its foot: those two stay free, the rest of the room may be used.
   if(s.is(VillageAstra.OWNED_CHEST.get()))approach.add(p);
   if(s.getBlock() instanceof BedBlock&&s.getValue(BedBlock.PART)==net.minecraft.world.level.block.state.properties.BedPart.FOOT)approach.add(p);
   // Floors are every walking surface of the building: the ground floor and the decks of its upper storeys.
   if(p.getY()==0&&full(s)&&!soil(s)&&!(s.getBlock() instanceof EntityBlock)&&!s.is(BlockTags.RAILS))floor.add(p);
   else if(p.getY()>0&&!edge&&structural(s)&&at.apply(p.above()).isAir())floor.add(p);
   else if(edge&&p.getY()>=1&&structural(s)){if(p.getY()==1)plinth.add(p);else infill.add(p);}
   else if(p.getY()>=3&&(s.getBlock() instanceof StairBlock||s.getBlock() instanceof SlabBlock))roof.add(p);
   if(edge&&p.getY()>=1&&s.is(BlockTags.LOGS)&&s.hasProperty(RotatedPillarBlock.AXIS)&&s.getValue(RotatedPillarBlock.AXIS)==Direction.Axis.Y)posts.add(p);
   if(top==null||p.getY()>top.getY()||p.getY()==top.getY()&&Math.abs(p.getX()-w/2)+Math.abs(p.getZ()-d/2)<Math.abs(top.getX()-w/2)+Math.abs(top.getZ()-d/2))top=p;
  }
  // Columns for equipment: a free cell on a solid floor inside the walls, with headroom, out of every doorway and not beside a bed, the stock or a ladder.
  var columns=new ArrayList<BlockPos>();var lamps=new ArrayList<BlockPos>();var yard=new ArrayList<BlockPos>();var headroom=new HashMap<BlockPos,Integer>();
  // Every floor of the building is looked over, not only the ground one.
  for(int x=1;x<w-1;x++)for(int z=1;z<d-1;z++)for(int y=1;y<=16;y++){
   var cell=new BlockPos(x,y,z);
   if(!at.apply(cell).isAir()||!at.apply(cell.above()).isAir()||!full(at.apply(cell.below())))continue;
   boolean covered=false;int ceiling=-1;for(int above=y+1;above<=24;above++){var c=at.apply(new BlockPos(x,above,z));if(!c.isAir()){covered=true;if(full(c))ceiling=above;break;}}
   boolean blocked=false;
   // The way in through a door stays clear, and so does the step in front of a bed and of the stock chest.
   for(var door:doors)if(door.getY()==y){int dx=Math.abs(door.getX()-x),dz=Math.abs(door.getZ()-z);
    boolean lane=(door.getZ()==0||door.getZ()==d-1)?dx==0&&dz<=2:dz==0&&dx<=2;
    if(dx+dz<=1||lane)blocked=true;}
   for(var kept:avoid){if(kept.getY()!=y)continue;
    if(kept.equals(cell))blocked=true;
    else if(Math.abs(kept.getX()-x)+Math.abs(kept.getZ()-z)==1&&approach.contains(kept))blocked=true;}
   if(blocked)continue;
   // How high equipment may be stacked in this cell before it meets the ceiling.
   int room=0;while(room<3&&at.apply(cell.above(room+1)).isAir())room++;
   headroom.put(cell,room);
   // A yard cell is used only when the rooms are full: equipment belongs inside first.
   if(!covered){yard.add(cell);continue;}
   columns.add(cell);
   if(ceiling>=y+2&&at.apply(new BlockPos(x,ceiling-1,z)).isAir())lamps.add(new BlockPos(x,ceiling-1,z));
  }
  final int cw=w,cd=d;
  java.util.function.ToIntFunction<BlockPos> walls=c->{int n=0;for(var dir:Direction.Plane.HORIZONTAL){var q=c.relative(dir);if(q.getX()<=0||q.getX()>=cw-1||q.getZ()<=0||q.getZ()>=cd-1||!at.apply(q).isAir())n++;}return n;};
  columns.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(c->-walls.applyAsInt(c)).thenComparingInt(c->-c.getZ()).thenComparingInt(BlockPos::getX));
  // AD-112: the core stands nearest the middle of the building, set aside before any kit takes a cell — in the first cell whose
  // occupation leaves every floor cell that can be walked to from the doors still reachable (4-neighbour search over free cells).
  BlockPos core=preset[0];
  if(core==null){var reach=reach(doors,at,cw,cd,null);
   for(var c:columns.stream().sorted(Comparator.<BlockPos>comparingInt(c->Math.abs(c.getX()-cw/2)+Math.abs(c.getZ()-cd/2))).toList()){
    var left=reach(doors,at,cw,cd,c);var lost=new HashSet<>(reach);lost.remove(c);lost.removeAll(left);if(lost.isEmpty()){core=c;break;}}}
  if(core!=null)columns.remove(core);
  lamps.sort(Comparator.<BlockPos>comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
  yard.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(c->-walls.applyAsInt(c)).thenComparingInt(c->-c.getZ()).thenComparingInt(BlockPos::getX));
  return new Analysis(w,d,floor,plinth,infill,roof,posts,lamps,columns,List.copyOf(yard),Map.copyOf(headroom),core,top);
 }
 private static boolean open(BlockState s){return s.isAir()||s.getBlock() instanceof DoorBlock;}
 /** Floor cells a resident walks to from the doors: free with headroom over a full floor, 4-neighbour on each storey; a blocked cell counts as solid. */
 private static Set<BlockPos> reach(List<BlockPos> doors,java.util.function.Function<BlockPos,BlockState> at,int w,int d,BlockPos blocked){
  var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();
  java.util.function.Predicate<BlockPos> walk=p->p.getX()>=0&&p.getX()<w&&p.getZ()>=0&&p.getZ()<d&&!p.equals(blocked)&&open(at.apply(p))&&open(at.apply(p.above()))&&full(at.apply(p.below()));
  for(var door:doors)if(walk.test(door)&&seen.add(door))queue.add(door);
  while(!queue.isEmpty()){var p=queue.poll();for(var dir:Direction.Plane.HORIZONTAL){var q=p.relative(dir);if(walk.test(q)&&seen.add(q))queue.add(q);}}
  return seen;
 }
 /** AD-112: the local cell of a design's core (for the hall, that of its third blueprint, which holds it for levels II…VI). */
 public static BlockPos core(String type){var a=analysis(type.startsWith("town_hall")?"town_hall_3":type);if(a.core()==null)throw new IllegalStateException("No core cell in "+type);return a.core();}
 private static Analysis analysis(String type){return ANALYSES.computeIfAbsent(type,t->analyse(t,Legacy117BuildingBlueprints.layout(t,BlockPos.ZERO)));}
 /** All equipment of levels II…VI of one design, in local cells; level N stands when every cell of levels II…N holds its block. */
 public static List<Placed> equipment(String type){return EQUIPMENT.computeIfAbsent(type,Legacy117Architecture::plan);}
 private static List<Placed> plan(String type){
  var a=analysis(type);var out=new ArrayList<Placed>();var free=new ArrayDeque<>(a.columns());free.addAll(a.yard());var stack=new ArrayDeque<BlockPos>();var room=new HashMap<>(a.headroom());
  // AD-112: fetched now, not in a static initialiser — the block registry is filled by then.
  var core=Cores.block(type);if(core!=null&&a.core()==null)throw new IllegalStateException("No core cell in "+type);
  java.util.function.BiConsumer<BlockState,Integer> put=(state,level)->{
   BlockPos cell=null;
   if(!free.isEmpty())cell=free.poll();else if(!stack.isEmpty())cell=stack.poll();
   if(cell==null)throw new IllegalStateException("No room for the equipment of level "+level+" in "+type);
   out.add(new Placed(cell,state,level));
   // A shelf on top of a solid piece is used when the rooms run out, as long as the ceiling leaves room for it.
   if(full(state)&&!(state.getBlock() instanceof FallingBlock)&&room.getOrDefault(cell,0)>0){stack.add(cell.above());room.put(cell.above(),room.get(cell)-1);}
  };
  for(int level=2;level<=MAX;level++){
   for(var block:kit(type,level))put.accept(standing(block),level);
   if(level==3){put.accept(Blocks.BARREL.defaultBlockState(),3);put.accept(Blocks.BARREL.defaultBlockState(),3);}
   if(level==4){var cell=free.isEmpty()?stack.poll():free.poll();out.add(new Placed(cell,Blocks.SPRUCE_LOG.defaultBlockState(),4));out.add(new Placed(cell.above(),Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE,AttachFace.FLOOR),4));}
   if(level==5){var cell=free.isEmpty()?stack.poll():free.poll();out.add(new Placed(cell,VillageAstra.OWNED_CHEST.get().defaultBlockState(),5));out.add(new Placed(cell.above(),Blocks.LANTERN.defaultBlockState(),5));}
   if(level==6){put.accept(Blocks.DEEPSLATE_TILES.defaultBlockState(),6);put.accept(Blocks.DEEPSLATE_TILES.defaultBlockState(),6);}
   // AD-112: the core of this level, at its grade, in the cell kept for it; written after the lower grades, so level N ends at grade N.
   if(core!=null)out.add(new Placed(a.core(),core.defaultBlockState().setValue(BuildingCoreBlock.GRADE,level),level));
  }
  if(a.top()!=null)out.add(new Placed(a.top().above(),Blocks.STONE_BRICK_WALL.defaultBlockState(),5));
  return List.copyOf(out);
 }
 /** Equipment stands on the floor: a grindstone or a bell is set on its base, not hung on a wall it may not have. */
 private static BlockState standing(Block block){
  var s=block.defaultBlockState();
  if(s.hasProperty(BlockStateProperties.ATTACH_FACE))s=s.setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR);
  if(s.hasProperty(BlockStateProperties.BELL_ATTACHMENT))s=s.setValue(BlockStateProperties.BELL_ATTACHMENT,BellAttachType.FLOOR);
  return s;
 }
 private static BlockState stairs(BlockState from,Block to){
  var s=to.defaultBlockState();
  for(var p:List.of(StairBlock.FACING,StairBlock.HALF,StairBlock.SHAPE))if(from.hasProperty(p))s=copy(from,s,p);
  return s;
 }
 private static BlockState slab(BlockState from,Block to){return from.hasProperty(SlabBlock.TYPE)?to.defaultBlockState().setValue(SlabBlock.TYPE,from.getValue(SlabBlock.TYPE)):to.defaultBlockState();}
 private static <T extends Comparable<T>> BlockState copy(BlockState from,BlockState to,Property<T> p){return to.setValue(p,from.getValue(p));}
 /** Level N of a design, built on its level-I layout placed at base. Level I is returned unchanged. */
 public static void apply(String type,int level,BlockPos base,Map<BlockPos,BlockState> m){
  if(level<=1)return;if(level>MAX)throw new IllegalArgumentException("No building level "+level);
  var a=analysis(type);
  for(var p:a.floor()){boolean mark=(p.getX()+p.getZ())%2==0,accent=p.getX()%3==1&&p.getZ()%3==1;
   // Every level lays a floor of its own materials, so a level really rebuilds the whole floor and never inherits half of it.
   Block block=switch(level){case 2->null;case 3->mark?Blocks.STONE_BRICKS:Blocks.CHISELED_STONE_BRICKS;case 4->accent?Blocks.POLISHED_DIORITE:Blocks.POLISHED_ANDESITE;
    case 5->mark?Blocks.DEEPSLATE_BRICKS:Blocks.POLISHED_DEEPSLATE;default->accent?Blocks.CHISELED_DEEPSLATE:Blocks.DEEPSLATE_TILES;};
   if(block!=null)m.put(base.offset(p),block.defaultBlockState());}
  for(var p:a.plinth()){var now=m.get(base.offset(p));if(now==null)continue;
   Block block=level>=6?Blocks.DEEPSLATE_TILES:now.is(Blocks.STONE_BRICKS)?Blocks.POLISHED_ANDESITE:Blocks.STONE_BRICKS;
   m.put(base.offset(p),block.defaultBlockState());}
  if(level>=5)for(var p:a.infill())m.put(base.offset(p),(level>=6?Blocks.DEEPSLATE_TILES:Blocks.BRICKS).defaultBlockState());
  if(level>=4)for(var p:a.roof()){var now=m.get(base.offset(p));if(now==null)continue;
   if(now.getBlock() instanceof StairBlock)m.put(base.offset(p),stairs(now,level>=6?Blocks.DEEPSLATE_TILE_STAIRS:level==5?Blocks.DEEPSLATE_BRICK_STAIRS:Blocks.BRICK_STAIRS));
   else if(now.getBlock() instanceof SlabBlock)m.put(base.offset(p),slab(now,level>=6?Blocks.DEEPSLATE_TILE_SLAB:level==5?Blocks.DEEPSLATE_BRICK_SLAB:Blocks.BRICK_SLAB));}
  if(level>=6)for(var p:a.posts())m.put(base.offset(p),Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
  for(int i=0;i<Math.min(2,a.lamps().size());i++){var lamp=a.lamps().get(i==0?0:a.lamps().size()-1);m.put(base.offset(lamp),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));}
  for(var placed:equipment(type))if(placed.level()<=level)m.put(base.offset(placed.local()),placed.state());
 }
}
