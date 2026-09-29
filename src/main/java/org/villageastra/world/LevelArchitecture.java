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
/** AD-073: what a building of level II…VI looks like. The level-I design is rebuilt in place, inside the same lot.
 *  AD-122 (owner review 2026-09-21: "level VI must read as an upgrade, not as decay"): one family with the village style (VillageStyle). The
 *  cream plaster above the ground storey is kept at every level; stone grows up from the ground, the frame stays dark oak. Owner review 2026-09-22:
 *  every material of a level is one the village can get by then, so no deepslate before IV (AD-112: a level-I..II mine stops above it) —
 *  levels I..III keep the wooden shingle roof of the design and the slate comes at IV, the stone ground storey one level later than before
 *  so that every next level still costs more:
 *  <ul><li>II — a stone brick plinth (a stone brick one turns polished andesite), lamps under the ceiling and the first equipment;</li>
 *  <li>III — a laid stone floor, storage and more equipment;</li>
 *  <li>IV (mechanized) — a chiseled and polished andesite floor, the roof relaid in deepslate brick, a drive post and iron equipment;</li>
 *  <li>V (automatic) — a deepslate floor, the ground storey up to y3 in stone brick (plaster and the posts and braces set in the wall), chiseled
 *  stone brick quoins at its corners, the roof in deepslate tile, a lantern over a chest, a wall post on the roof and automation equipment;</li>
 *  <li>VI (complex) — a deepslate tile floor, a polished deepslate plinth, the whole ground storey stone to its plate (y4) with chiseled quoins,
 *  the roof relaid in deepslate brick, every other post of the frame dressed (stripped dark oak), and the richest equipment.</li></ul>
 *  Village-style designs name their plinth, plaster, roof and deck cells themselves; older designs are read by their lot edge.
 *  AD-112: from II every work building holds its core in one cell nearest its middle (the hall at (3,1,3) of its own blueprints); the core
 *  of level N stands there at grade N, so a level's equipment includes its core and each level above II adds one ring to it.
 *  Every next level rebuilds more of the shell than the one before and adds a bigger kit, so every upgrade costs more. Equipment goes to free cells
 *  along the inner walls, never in a doorway, beside a bed, the stock chest or a ladder; the cells are the same whatever level is asked for. */
public final class LevelArchitecture {
 public static final int MAX=6;
 private LevelArchitecture(){}
 private static final JsonObject DATA=read();
 private static JsonObject read(){
  try(var s=LevelArchitecture.class.getResourceAsStream("/data/villageastra/balance/levels.json")){
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
 private record Analysis(int w,int d,Set<BlockPos> floor,Set<BlockPos> plinth,Set<BlockPos> infill,Set<BlockPos> roof,Set<BlockPos> posts,Map<BlockPos,Boolean> ground,Set<BlockPos> timber,List<BlockPos> lamps,List<BlockPos> columns,List<BlockPos> yard,Map<BlockPos,Integer> headroom,BlockPos core,BlockPos top){}
 private static final Map<String,Analysis> ANALYSES=new java.util.concurrent.ConcurrentHashMap<>();
 private static final Map<String,List<Placed>> EQUIPMENT=new java.util.concurrent.ConcurrentHashMap<>();
 /** AD-148 (design review 2026-09-24): the equipment, core and lamps of every design with levels that is drawn once, frozen as they stood in
  *  the build whose buildings stand in the worlds (data/villageastra/architecture/equipment.json, written by EquipmentTableGameTests). A building
  *  works at level N while that equipment stands in its cells (BuildingTiers.level), so the cells no longer follow the shape of the design: its
  *  walls, roofs and storeys may be redrawn for looks as long as every frozen cell stays free and reachable (FrozenEquipmentGameTests). A design
  *  not in the table (new types, designs drawn per level, annexes, the warehouse of AD-141) is analysed as before. */
 private static final JsonObject FROZEN=readFrozen();
 private static JsonObject readFrozen(){
  try(var s=LevelArchitecture.class.getResourceAsStream("/data/villageastra/architecture/equipment.json")){
   if(s==null)return new JsonObject();
   return JsonParser.parseReader(new java.io.InputStreamReader(s,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("types");
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 /** AD-148: whether a design's equipment, core and lamps come from the frozen table. */
 public static boolean frozen(String type){return FROZEN.has(type);}
 private static BlockPos cell(com.google.gson.JsonElement e){var a=e.getAsJsonArray();return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
 private static List<Placed> frozenEquipment(String type){var out=new ArrayList<Placed>();
  for(var e:FROZEN.getAsJsonObject(type).getAsJsonArray("equipment")){var o=e.getAsJsonObject();
   out.add(new Placed(new BlockPos(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt()),DistinctArchitecture.state(o.get("state").getAsString()),o.get("level").getAsInt()));}
  return List.copyOf(out);}
 /** AD-148: the lamps level II hangs in a frozen design (the first and the last of the analysis' lamp cells when it was frozen). */
 public static List<BlockPos> frozenLamps(String type){var out=new ArrayList<BlockPos>();for(var e:FROZEN.getAsJsonObject(type).getAsJsonArray("lamps"))out.add(cell(e));return List.copyOf(out);}
 private static boolean full(BlockState s){return !s.isAir()&&s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);}
 private static boolean soil(BlockState s){return s.is(BlockTags.DIRT)||s.is(Blocks.FARMLAND)||s.is(Blocks.DIRT_PATH)||s.is(Blocks.COARSE_DIRT)||s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL);}
 /** Structural materials a level may rebuild: masonry, planks, terracotta and stone — not equipment, glass, wool, hay or anything with contents. */
 private static boolean structural(BlockState s){
  if(!full(s)||s.getBlock() instanceof EntityBlock||s.is(BlockTags.LOGS))return false;
  return s.is(BlockTags.PLANKS)||s.is(BlockTags.TERRACOTTA)||s.is(Blocks.TERRACOTTA)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.STONE)||s.is(Blocks.STONE_BRICKS)||s.is(Blocks.BRICKS)
   ||s.is(Blocks.SMOOTH_STONE)||s.is(Blocks.SMOOTH_SANDSTONE)||s.is(Blocks.SANDSTONE)||s.is(Blocks.CALCITE)||s.is(Blocks.MUD_BRICKS)||s.is(Blocks.ANDESITE)||s.is(Blocks.POLISHED_ANDESITE)
   ||s.is(Blocks.CHISELED_STONE_BRICKS)||s.is(Blocks.DEEPSLATE_BRICKS)||s.is(Blocks.WHITE_TERRACOTTA);
 }
 /** @param late whether the furniture AD-143 set into cells of a design that were empty before (VillageStyle.lateFurniture) stays out of the
  *  analysis: it always sees those cells as the air they were, and with {@code late} never gives them to equipment, so no kit, core or lamp of
  *  an existing building moves. */
 private static Analysis analyse(String type,Map<BlockPos,BlockState> layout,boolean late){
  var design=BuildingBlueprints.design(type);int w=design.width(),d=design.depth();
  var cells=new HashMap<BlockPos,BlockState>();for(var e:layout.entrySet())cells.put(e.getKey(),e.getValue());
  var lateAll=VillageStyle.has(type)?VillageStyle.lateFurniture(type):Set.<VillageStyle.Cell>of();var lateCells=late?lateAll:Set.<VillageStyle.Cell>of();
  for(var c:lateAll)cells.remove(new BlockPos(c.x(),c.y(),c.z()));
  var air=Blocks.AIR.defaultBlockState();
  java.util.function.Function<BlockPos,BlockState> at=p->cells.getOrDefault(p,air);
  var floor=new LinkedHashSet<BlockPos>();var plinth=new LinkedHashSet<BlockPos>();var infill=new LinkedHashSet<BlockPos>();var roof=new LinkedHashSet<BlockPos>();var posts=new LinkedHashSet<BlockPos>();
  var roles=VillageStyle.has(type)?VillageStyle.roles(type):null;var doors=new ArrayList<BlockPos>();var avoid=new HashSet<BlockPos>();var approach=new HashSet<BlockPos>();BlockPos top=null,ridge=null;var preset=new BlockPos[1];
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
   // AD-122: a village-style design names its own shell cells (its walls stand one block in from the lot edge under the eaves).
   Character role=roles==null?null:roles.get(new VillageStyle.Cell(p.getX(),p.getY(),p.getZ()));
   if(roles!=null){
    if(role!=null&&role==VillageStyle.DECK&&full(s)&&!(s.getBlock() instanceof EntityBlock))floor.add(p);
    else if(role!=null&&role==VillageStyle.PLINTH&&structural(s))plinth.add(p);
    else if(role!=null&&role==VillageStyle.INFILL&&structural(s))infill.add(p);
    else if(role!=null&&role==VillageStyle.ROOF&&(s.getBlock() instanceof StairBlock||s.getBlock() instanceof SlabBlock||full(s)))roof.add(p);
   }
   // Floors are every walking surface of the building: the ground floor and the decks of its upper storeys.
   else if(p.getY()==0&&full(s)&&!soil(s)&&!(s.getBlock() instanceof EntityBlock)&&!s.is(BlockTags.RAILS))floor.add(p);
   else if(p.getY()>0&&!edge&&structural(s)&&at.apply(p.above()).isAir())floor.add(p);
   else if(edge&&p.getY()>=1&&structural(s)){if(p.getY()==1)plinth.add(p);else infill.add(p);}
   else if(p.getY()>=3&&(s.getBlock() instanceof StairBlock||s.getBlock() instanceof SlabBlock))roof.add(p);
   if(edge&&p.getY()>=1&&s.is(BlockTags.LOGS)&&s.hasProperty(RotatedPillarBlock.AXIS)&&s.getValue(RotatedPillarBlock.AXIS)==Direction.Axis.Y)posts.add(p);
   if(top==null||p.getY()>top.getY()||p.getY()==top.getY()&&Math.abs(p.getX()-w/2)+Math.abs(p.getZ()-d/2)<Math.abs(top.getX()-w/2)+Math.abs(top.getZ()-d/2))top=p;
   // Round 1 review ("a grey block floats over the chimney"): a village-style design's finial replaces the cap slab of its roof ridge.
   if(roles!=null&&role!=null&&role==VillageStyle.ROOF&&s.getBlock() instanceof SlabBlock&&(ridge==null||p.getY()>ridge.getY()||p.getY()==ridge.getY()&&Math.abs(p.getX()-w/2)+Math.abs(p.getZ()-d/2)<Math.abs(ridge.getX()-w/2)+Math.abs(ridge.getZ()-d/2)))ridge=p;
  }
  // AD-122: the timbers of a village-style ground storey (y2..4) that stand in a wall — posts and braces with wall on two sides — turn to stone with
  // it, the corner ones (wall along x on one side and along z on the other) chiseled quoins; the rest of the dark oak posts are dressed at VI.
  var ground=new HashMap<BlockPos,Boolean>();var timber=new LinkedHashSet<BlockPos>();
  if(roles!=null){
   java.util.function.Predicate<BlockPos> wall=q->{var s=at.apply(q);return !s.isAir()&&(s.getBlock() instanceof IronBarsBlock||s.getBlock() instanceof DoorBlock||full(s)&&!(s.getBlock() instanceof StairBlock));};
   for(var e:cells.entrySet()){var p=e.getKey();var s=e.getValue();Character role=roles.get(new VillageStyle.Cell(p.getX(),p.getY(),p.getZ()));
    boolean post=s.is(Blocks.DARK_OAK_LOG)&&s.getValue(RotatedPillarBlock.AXIS)==Direction.Axis.Y,brace=s.is(Blocks.DARK_OAK_STAIRS);
    if(role==null||role!=VillageStyle.FRAME&&role!=VillageStyle.INFILL||!(post||brace||role==VillageStyle.INFILL&&structural(s)))continue;
    boolean e1=wall.test(p.east()),w1=wall.test(p.west()),s1=wall.test(p.south()),n1=wall.test(p.north());
    int n=(e1?1:0)+(w1?1:0)+(s1?1:0)+(n1?1:0);boolean corner=(e1^w1)&&(s1^n1);
    if(p.getY()>=2&&p.getY()<=4&&n>=2&&(role==VillageStyle.FRAME||corner))ground.put(p,corner);
    else if(post&&role==VillageStyle.FRAME)timber.add(p);}
  }
  // Columns for equipment: a free cell on a solid floor inside the walls, with headroom, out of every doorway and not beside a bed, the stock or a ladder.
  var columns=new ArrayList<BlockPos>();var lamps=new ArrayList<BlockPos>();var yard=new ArrayList<BlockPos>();var headroom=new HashMap<BlockPos,Integer>();
  // Every floor of the building is looked over, not only the ground one.
  // AD-139: cells a design keeps for its own furniture and aisles (the restaurant's tables, seats and aisles) are never equipment.
  var furniture=VillageStyle.kept(type);
  for(int x=1;x<w-1;x++)for(int z=1;z<d-1;z++)for(int y=1;y<=16;y++){
   var cell=new BlockPos(x,y,z);if(furniture.contains(new VillageStyle.Cell(x,y,z))||lateCells.contains(new VillageStyle.Cell(x,y,z)))continue;
   if(!at.apply(cell).isAir()||!at.apply(cell.above()).isAir()||!full(at.apply(cell.below())))continue;
   boolean covered=false;int ceiling=-1;for(int above=y+1;above<=24;above++){var c=at.apply(new BlockPos(x,above,z));if(!c.isAir()){covered=true;if(full(c))ceiling=above;break;}}
   boolean blocked=false;
   // The way in through a door stays clear, and so does the step in front of a bed and of the stock chest.
   for(var door:doors)if(door.getY()==y){int dx=Math.abs(door.getX()-x),dz=Math.abs(door.getZ()-z);
    // A street door set in under the eave or a jetty (z 1..2) keeps its lane into the room as well.
    boolean lane=(door.getZ()<=2||door.getZ()==d-1)?dx==0&&dz<=2:dz==0&&dx<=2;
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
    var left=reach(doors,at,cw,cd,c);var lost=new HashSet<>(reach);lost.remove(c);lost.removeAll(left);
    // A dead-end floor cell may be the only approach to a station or a yard gate.
    // Keeping all OTHER walkable cells is insufficient: that station must keep an approach too.
    boolean cutsStation=cells.entrySet().stream().anyMatch(e->{var p=e.getKey();return !e.getValue().isAir()&&p.getY()>=1&&p.getX()>0&&p.getX()<cw-1&&p.getZ()>0&&p.getZ()<cd-1
     &&Direction.Plane.HORIZONTAL.stream().anyMatch(dir->reach.contains(p.relative(dir)))&&Direction.Plane.HORIZONTAL.stream().noneMatch(dir->left.contains(p.relative(dir)));});
    if(lost.isEmpty()&&!cutsStation){core=c;break;}}}
  if(core!=null)columns.remove(core);
  lamps.sort(Comparator.<BlockPos>comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
  yard.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(c->-walls.applyAsInt(c)).thenComparingInt(c->-c.getZ()).thenComparingInt(BlockPos::getX));
  var finial=ridge!=null?ridge:top==null?null:top.above();
  return new Analysis(w,d,floor,plinth,infill,roof,posts,Map.copyOf(ground),Set.copyOf(timber),lamps,columns,List.copyOf(yard),Map.copyOf(headroom),core,finial);
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
 public static BlockPos core(String type){if(type.equals(ForesterHut.TYPE))return ForesterHut.CORE;if(type.equals(WarehouseStore.TYPE))return WarehouseStore.CORE;String t=type.startsWith("town_hall")?"town_hall_3":type;
  if(frozen(t)&&FROZEN.getAsJsonObject(t).has("core"))return cell(FROZEN.getAsJsonObject(t).get("core"));
  var a=analysis(t);if(a.core()==null)throw new IllegalStateException("No core cell in "+type);return a.core();}
 private static Analysis analysis(String type){return ANALYSES.computeIfAbsent(type,t->analyse(t,BuildingBlueprints.raw(t,BlockPos.ZERO),true));}
 /** All equipment of levels II…VI of one design, in local cells; level N stands when every cell of levels II…N holds its block. */
 public static List<Placed> equipment(String type){return EQUIPMENT.computeIfAbsent(type,t->t.equals(ForesterHut.TYPE)?ForesterHut.equipment():t.equals(WarehouseStore.TYPE)?WarehouseStore.equipment():frozen(t)?frozenEquipment(t):plan(t,analysis(t)));}
 /** GameTests (AD-143): the equipment as it would be planned if the late furniture's cells were free floor, as they were before it — equal to
  *  {@link #equipment} when the furniture took no cell a kit, the core or a lamp stood in. */
 public static List<Placed> equipmentBeforeFurniture(String type){return plan(type,analyse(type,BuildingBlueprints.raw(type,BlockPos.ZERO),false));}
 private static List<Placed> plan(String type,Analysis a){var out=new ArrayList<Placed>();var free=new ArrayDeque<>(a.columns());free.addAll(a.yard());var stack=new ArrayDeque<BlockPos>();var room=new HashMap<>(a.headroom());
  // OWNER-HOUSING-BEDS: the cells of a house's level beds, the step behind each foot and the way up to them are never equipment.
  for(var bed:HousingLadder.levelBedCells(type)){free.remove(bed.foot());free.remove(bed.head());free.remove(bed.approach());}
  free.removeAll(HousingLadder.wayUp(type));
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
  // The finial: a village-style design's on its ridge cap cell (a wall post on a bottom slab would float half a block over it), else over the top.
  if(a.top()!=null)out.add(new Placed(a.top(),Blocks.STONE_BRICK_WALL.defaultBlockState(),5));
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
 /** AD-122: rough ground-storey masonry of level IV — cobblestone and polished andesite in 2x2 clusters, no stone brick, so level V's dressing
  *  in stone brick changes every cell of it. */
 private static Block rubble(BlockPos p){int v=(p.getX()>>1)*73856093^(p.getY()>>1)*19349663^(p.getZ()>>1)*83492791;v^=v>>>13;v*=0x5bd1e995;v^=v>>>15;return (v&0x7fffffff)%100<70?Blocks.COBBLESTONE:Blocks.POLISHED_ANDESITE;}
 /** AD-122: a village-style roof (dark oak or spruce shingle) stays wood to III (owner review 2026-09-22: deepslate only once the mine reaches
  *  it) and is relaid at every level from IV — deepslate brick at IV, deepslate tile at V, deepslate brick at VI. The dearer tile sits at V so
  *  that V costs more than IV (BuildingLevelGameTests); an older design's roof is turned over at IV, V and VI as before. */
 private static BlockState roof(BlockState now,int level,boolean style){
  boolean tile=now.is(Blocks.DEEPSLATE_TILE_STAIRS)||now.is(Blocks.DEEPSLATE_TILE_SLAB)||now.is(Blocks.DEEPSLATE_TILES);
  boolean brick=now.is(Blocks.DEEPSLATE_BRICK_STAIRS)||now.is(Blocks.DEEPSLATE_BRICK_SLAB)||now.is(Blocks.DEEPSLATE_BRICKS);
  boolean toTile;
  if(style){if(level<4)return null;toTile=level==5;}
  else if(tile){if(level<5)return null;toTile=level==6;}
  else if(brick){if(level<5)return null;toTile=level==5;}
  else{if(level<4)return null;toTile=level==5;}
  if(now.getBlock() instanceof StairBlock)return stairs(now,toTile?Blocks.DEEPSLATE_TILE_STAIRS:Blocks.DEEPSLATE_BRICK_STAIRS);
  if(now.getBlock() instanceof SlabBlock)return slab(now,toTile?Blocks.DEEPSLATE_TILE_SLAB:Blocks.DEEPSLATE_BRICK_SLAB);
  return full(now)?(toTile?Blocks.DEEPSLATE_TILES:Blocks.DEEPSLATE_BRICKS).defaultBlockState():null;
 }
 /** Level N of a design, built on its level-I layout placed at base. Level I is returned unchanged. */
 public static void apply(String type,int level,BlockPos base,Map<BlockPos,BlockState> m){
  if(level<=1)return;if(level>MAX)throw new IllegalArgumentException("No building level "+level);
  // AD-147: a plan that draws the materials of each level itself (the store) takes only its stations and its core here.
  if(VillageStyle.OWN_MATERIALS.contains(type)){for(var placed:equipment(type))if(placed.level()<=level)m.put(base.offset(placed.local()),placed.state());return;}
  // AD-131: a design drawn anew for every level (the forester's hut) is read from the plan of that level, and its stations are its own kit.
  var a=analysis(VillageStyle.LEVELLED.contains(type)?type+"@"+level:type);
  for(var p:a.floor()){boolean mark=(p.getX()+p.getZ())%2==0,accent=p.getX()%3==1&&p.getZ()%3==1;
   // Every level lays a floor of its own materials, so a level really rebuilds the whole floor and never inherits half of it.
   Block block=switch(level){case 2->null;case 3->mark?Blocks.STONE_BRICKS:Blocks.CHISELED_STONE_BRICKS;case 4->mark?Blocks.CHISELED_STONE_BRICKS:Blocks.POLISHED_ANDESITE;
    case 5->mark?Blocks.DEEPSLATE_BRICKS:Blocks.POLISHED_DEEPSLATE;default->accent?Blocks.CHISELED_DEEPSLATE:Blocks.DEEPSLATE_TILES;};
   if(block!=null)m.put(base.offset(p),block.defaultBlockState());}
  for(var p:a.plinth()){var now=m.get(base.offset(p));if(now==null)continue;
   Block block=level>=6?Blocks.POLISHED_DEEPSLATE:now.is(Blocks.STONE_BRICKS)?Blocks.POLISHED_ANDESITE:Blocks.STONE_BRICKS;
   m.put(base.offset(p),block.defaultBlockState());}
  boolean style=VillageStyle.has(type);
  if(style){
   // AD-122: the ground storey turns to stone course by course and the plaster above it stays: V to y3, VI to y4 (plaster, and the posts and braces
   // standing in those courses, chiseled at the corners; IV lays the slate roof instead, 2026-09-22); at VI the other posts are dressed.
   int top=level>=6?4:level==5?3:0,timbers=top;
   // Round 1 review ("level III looks the same as level I"): from III the plastered panels of the ground storey are laid in brick nogging
   // between the same dark oak frame (bricks come from the kiln by then); V and VI turn them to stone as before.
   if(level>=3)for(var p:a.infill()){var now=m.get(base.offset(p));if(p.getY()>=2&&p.getY()<=4&&now!=null&&now.is(Blocks.SMOOTH_SANDSTONE))m.put(base.offset(p),Blocks.BRICKS.defaultBlockState());}
   for(var p:a.infill())if(p.getY()>=2&&p.getY()<=top)m.put(base.offset(p),Blocks.STONE_BRICKS.defaultBlockState());
   for(var e:a.ground().entrySet()){var p=e.getKey();if(p.getY()>timbers)continue;
    if(e.getValue())m.put(base.offset(p),Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
    else if(!a.infill().contains(p))m.put(base.offset(p),Blocks.STONE_BRICKS.defaultBlockState());}
   if(level>=6)for(var p:a.timber())m.put(base.offset(p),Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
  }else{
   // Older designs: III the lowest course, IV the ground storey (y<=5) in rubble, V every wall in stone brick, VI in polished andesite, VI dressed posts.
   for(var p:a.infill()){int y=p.getY();Block block;
    if(level==3)block=y==2?Blocks.STONE_BRICKS:null;
    else if(level==4)block=y<=5?rubble(p):null;
    else block=level==5?Blocks.STONE_BRICKS:level==6?Blocks.POLISHED_ANDESITE:null;
    if(block!=null)m.put(base.offset(p),block.defaultBlockState());}
   if(level>=6)for(var p:a.posts())m.put(base.offset(p),Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
  }
  for(var p:a.roof()){var now=m.get(base.offset(p));if(now==null)continue;var to=roof(now,level,style);if(to!=null)m.put(base.offset(p),to);}
  // AD-148: a frozen design hangs its lamps where they hung when it was frozen.
  var lamps=frozen(type)?frozenLamps(type):new ArrayList<BlockPos>();
  if(!frozen(type))for(int i=0;i<Math.min(2,a.lamps().size());i++)lamps.add(a.lamps().get(i==0?0:a.lamps().size()-1));
  for(var lamp:lamps)m.put(base.offset(lamp),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
  for(var placed:equipment(type))if(placed.level()<=level)m.put(base.offset(placed.local()),placed.state());
  // AD-139: the restaurant's table groups of the level (balance/dining.json), cloths from III, the hitching post from V.
  Dining.furnish(type,level,base,m);
  // OWNER-HOUSING-BEDS: the beds of the housing ladder (a flower pot standing on the cell goes: the house's poppy sat on its crafting table).
  for(var bed:HousingLadder.levelBedCells(type)){if(bed.level()>level)continue;
   for(var part:BedPart.values()){var cell=base.offset(part==BedPart.FOOT?bed.foot():bed.head());m.put(cell,bed.state(part));
    var over=m.get(cell.above());if(over!=null&&over.getBlock() instanceof FlowerPotBlock)m.put(cell.above(),Blocks.AIR.defaultBlockState());}}
 }
}
