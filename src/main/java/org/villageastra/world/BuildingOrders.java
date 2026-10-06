package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.*;
/** Field-ordered new buildings on the existing paid construction queue (AD-028). Pure survey; approval writes the same queue file. */
public final class BuildingOrders {
 /** Field-orderable designs: housing and workplaces without special areas (farm/forester/mine fields, towers of the hall, siege camps stay separate). */
 public static final Set<String> ORDERABLE=Set.of("farm","home","home_2","carpentry","masonry","mill","restaurant","smithy","warehouse","school","laboratory","clinic","engineering","cartographer","expedition","caravan","livestock","barracks","guard_house","archery","quarry","wall_tower");
 /** AD-046: designs that plan and build correctly on paper but that a live builder does not finish yet — surveyed and tested, never offered
  *  in the palette. The two-storey house left this list once a live builder really finished it (761 of 761 operations, AD-084). */
 public static final Set<String> WITHHELD=Set.of();
 public static boolean planned(String design){return ORDERABLE.contains(design)||WITHHELD.contains(design);}
 public static final Set<String> HOUSING=Set.of("home","home_2");
 public static final int REACH=150,MAX_FOUNDATION=3,SITE_DISTANCE=16,ATTIC=5;
 /** AD-046: a builder with tools and trestles works at 6 blocks from eye (1.62) to the cell centre — a player still reaches 4.5.
  * Every block above that is worked from a scaffold column, and each column costs the site a walk around the house. */
 public static final double REACH_SQ=36,EYE=1.62;
 /** reason is empty when the site is orderable; conflicts are the red cells shown in the field. */
 public record Survey(CompoundTag state,Set<BlockPos> conflicts,String reason){public boolean ok(){return reason.isEmpty()&&conflicts.isEmpty();}}
 private record Op(BlockPos pos,BlockState before,BlockState after,String item,String returned){}
 private BuildingOrders(){}
 public static int capacity(String design){return (int)BuildingBlueprints.layout(design,BlockPos.ZERO).values().stream().filter(s->s.getBlock() instanceof BedBlock&&s.getValue(BedBlock.PART)==BedPart.FOOT).count();}
 public static int homeLevel(String design){return design.equals("home_2")?2:1;}
 public static UUID buildingId(CompoundTag state){return state.hasUUID("building")?state.getUUID("building"):Settlement.childId(HallConstructionPlan.projectId(state),"building");}
 public static boolean isBuilding(CompoundTag state){return state.getString("kind").equals("building");}
 /** Item paid for one placed cell; empty = no separate item (upper door half, bed head); null = design cannot be paid yet. */
 static String material(BlockState state){
  if(state.isAir())return "";
  if(state.getBlock() instanceof DoorBlock&&state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER)return "";
  if(state.getBlock() instanceof BedBlock&&state.getValue(BedBlock.PART)==BedPart.HEAD)return "";
  if(state.is(VillageAstra.OWNED_CHEST.get()))return "minecraft:chest";
  var item=state.getBlock().asItem();return item==Items.AIR?null:BuiltInRegistries.ITEM.getKey(item).toString();
 }
 /** AD-112: the items a cell costs to go from before to after — for a core, the core itself when none of this building's stands there
  *  (a lower grade is kept), then one ring for each grade above what stands, up to the design's; null when the design cannot be paid. */
 public static List<String> materials(BlockState before,BlockState after){
  if(after.getBlock() instanceof BuildingCoreBlock core){int to=after.getValue(BuildingCoreBlock.GRADE),from=before.is(core)?before.getValue(BuildingCoreBlock.GRADE):1;var out=new ArrayList<String>();
   for(int g=from<2?2:from+1;g<=to;g++)out.add(coreItem(core,g));return out;}
  var item=material(after);return item==null?null:List.of(item);
 }
 /** The item that raises a core to this grade: the core at II, the ring of that level above it. */
 static String coreItem(BuildingCoreBlock core,int grade){return grade<=org.villageastra.domain.CoreCatalog.CORE_LEVEL?org.villageastra.domain.CoreCatalog.NS+":"+org.villageastra.domain.CoreCatalog.path(core.type()):org.villageastra.domain.CoreCatalog.ringId(grade);}
 /** AD-112: a core cell as a chain of operations on the same cell, one item each: the core (grade II), then one ring per grade, up to the
  *  design's grade but never above the grade the village has researched for this building (cap). Empty when nothing is to be set. */
 static List<BlockState[]> coreChain(BlockState before,BlockState after,int cap){
  var core=(BuildingCoreBlock)after.getBlock();int to=Math.min(cap,after.getValue(BuildingCoreBlock.GRADE)),from=before.is(core)?before.getValue(BuildingCoreBlock.GRADE):1;var out=new ArrayList<BlockState[]>();var at=before;
  for(int g=from<2?2:from+1;g<=to;g++){var next=core.defaultBlockState().setValue(BuildingCoreBlock.GRADE,g);out.add(new BlockState[]{at,next});at=next;}
  return out;
 }
 /** Decorative blocks without a single item are built in their plain form (potted plant → empty pot). */
 public static BlockState payable(BlockState state){if(state.is(Blocks.GRASS_BLOCK))return Blocks.DIRT.defaultBlockState();return state.getBlock() instanceof FlowerPotBlock&&state.getBlock()!=Blocks.FLOWER_POT?Blocks.FLOWER_POT.defaultBlockState():state;}
 private static boolean clearable(BlockState before,boolean floor){
  if(before.canBeReplaced()||before.is(BlockTags.LEAVES)||before.is(BlockTags.FLOWERS)||before.is(BlockTags.SAPLINGS)||before.is(Blocks.SNOW))return true;
  return floor&&(before.is(BlockTags.DIRT)||before.is(BlockTags.BASE_STONE_OVERWORLD)||before.is(BlockTags.SAND)||before.is(Blocks.GRAVEL));
 }
 /** A lower stand in the live world (ground or floor level, two free cells, sturdy below) within reach of the cell. */
 public static boolean lowReach(ServerLevel l,BlockPos target,BlockPos origin){
  for(int y=origin.getY()-2;y<=origin.getY()+1;y++)for(int dx=-6;dx<=6;dx++)for(int dz=-6;dz<=6;dz++){
   var s=new BlockPos(target.getX()+dx,y,target.getZ()+dz);
   if(Math.pow(dx,2)+Math.pow(y+EYE-(target.getY()+.5),2)+Math.pow(dz,2)>REACH_SQ)continue;
   if(l.getBlockState(s).getCollisionShape(l,s).isEmpty()&&l.getBlockState(s.above()).getCollisionShape(l,s.above()).isEmpty()&&l.getBlockState(s.below()).isFaceSturdy(l,s.below(),Direction.UP))return true;
  }
  return false;
 }
 /** A scaffold column in design coordinates: cells 1..top at (x,z); feet level 1 is ground outside or the floor inside. */
 record Column(int x,int z,int top){}
 private static boolean free(BlockState s){return s==null||s.isAir();}
 /** Plans keep a margin (5.8) below the worker reach (6): eye height and hovering in a column drift by a few hundredths. */
 public static final double PLAN_REACH_SQ=33.64;
 static boolean reaches(double sx,double feet,double sz,BlockPos p){return reaches(sx,feet,sz,p,PLAN_REACH_SQ);}
 static boolean reaches(double sx,double feet,double sz,BlockPos p,double sq){return Math.pow(sx-p.getX(),2)+Math.pow(feet+EYE-(p.getY()+.5),2)+Math.pow(sz-p.getZ(),2)<=sq;}
 /** AD-153: a builder's work reach (squared) at a reach multiple of the plain 6 blocks (Construction IV: x2, VI: x3). */
 public static double workReachSq(int mul){return REACH_SQ*mul*mul;}
 /** AD-153: the plan's reach (squared) at a reach multiple — the same 0.2-block margin under the work reach as the plain 5.8. */
 public static double planReachSq(int mul){return mul<=1?PLAN_REACH_SQ:Math.pow(6*mul-.2,2);}
 /** Ground stands outside and floor stands inside (feet y=1) of the finished design on flat ground. */
 static List<BlockPos> stands(Map<BlockPos,BlockState> local){
  int maxX=local.keySet().stream().mapToInt(BlockPos::getX).max().orElse(0),maxZ=local.keySet().stream().mapToInt(BlockPos::getZ).max().orElse(0);
  var stands=new ArrayList<BlockPos>();
  for(int x=-3;x<=maxX+3;x++)for(int z=-3;z<=maxZ+3;z++){
   var ground=new BlockPos(x,1,z);var floor=local.get(ground.below());
   if((floor==null||floor.isSolid())&&free(local.get(ground))&&free(local.get(ground.above())))stands.add(ground);
  }
  return stands;
 }
 static boolean standReach(List<BlockPos> stands,BlockPos p){return standReach(stands,p,PLAN_REACH_SQ);}
 static boolean standReach(List<BlockPos> stands,BlockPos p,double sq){return stands.stream().anyMatch(s->!(s.getX()==p.getX()&&s.getZ()==p.getZ()&&(p.getY()==s.getY()||p.getY()==s.getY()+1))&&reaches(s.getX(),s.getY(),s.getZ(),p,sq));}
 static boolean reachable(Map<BlockPos,BlockState> local){return unreachable(local)==null;}
 /** First design cell no ground/floor stand reaches (scaffold columns are then planned for it). */
 public static BlockPos unreachable(Map<BlockPos,BlockState> local){
  var stands=stands(local);
  for(var cell:local.entrySet())if(!cell.getValue().isAir()&&!standReach(stands,cell.getKey()))return cell.getKey();
  return null;
 }
 /** Interior full-cube block (floor/ceiling layer) a column may pass through; it is placed as a cap when the column is dismantled. */
 private static boolean cap(Map<BlockPos,BlockState> local,BlockPos pos,int maxX,int maxZ){
  var state=local.get(pos);if(state==null||state.isAir()||pos.getY()<2)return false;
  if(pos.getX()<1||pos.getX()>maxX-1||pos.getZ()<1||pos.getZ()>maxZ-1)return false;
  var block=state.getBlock();if(block instanceof EntityBlock||block instanceof DoorBlock||block instanceof BedBlock||!state.isSolid())return false;
  return state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
 }
 public static BlockPos lastImpossible;
 /** Design-only scaffold plan, computed once per design (world checks stay per survey). */
 record Plan(List<Column> columns,Set<BlockPos> caps,Map<BlockPos,BlockPos> hints){}
 private static final Map<String,Plan> PLANS=new java.util.concurrent.ConcurrentHashMap<>();
 /** The cached scaffold plan of a design turned this many times (hints null: no plan reaches every cell). */
 static Plan scaffoldPlan(String design,int variant){return scaffoldPlan(design,variant,1);}
 /** AD-153: the plan for builders of this reach multiple (Construction IV/VI): a longer reach leaves fewer cells to columns. */
 static Plan scaffoldPlan(String design,int variant,int mul){return PLANS.computeIfAbsent(design+"/"+variant+(mul>1?"/x"+mul:""),k->{var local=BuildingPlacement.layout(design,BlockPos.ZERO,variant);local.entrySet().removeIf(c->c.getValue().getBlock() instanceof SignBlock);var cs=new ArrayList<Column>();var cp=new LinkedHashSet<BlockPos>();var hs=scaffolds(local,cs,cp,planReachSq(mul));return new Plan(List.copyOf(cs),Set.copyOf(cp),hs==null?null:Map.copyOf(hs));});}
 /** Whether a new column at (x,z) would have two columns beside it, or give a column beside it a second neighbouring column. */
 private static boolean clusters(List<Column> columns,int x,int z){
  var near=columns.stream().filter(c->Math.abs(c.x()-x)+Math.abs(c.z()-z)==1).toList();
  if(near.size()>=2)return true;
  for(var n:near)if(columns.stream().anyMatch(c->c!=n&&Math.abs(c.x()-n.x())+Math.abs(c.z()-n.z())==1))return true;
  return false;
 }
 /** Minimal scaffold columns for every unreachable cell; hints map a cell to the feet position inside its column; caps collects floor cells the columns pass. */
 static Map<BlockPos,BlockPos> scaffolds(Map<BlockPos,BlockState> local,List<Column> columns,Set<BlockPos> caps){return scaffolds(local,columns,caps,PLAN_REACH_SQ);}
 static Map<BlockPos,BlockPos> scaffolds(Map<BlockPos,BlockState> local,List<Column> columns,Set<BlockPos> caps,double sq){
  // Spaced columns first; a design that cannot be planned so takes the plan of the nearest columns, as before.
  var spacedColumns=new ArrayList<Column>();var spacedCaps=new LinkedHashSet<BlockPos>();var hints=scaffolds(local,spacedColumns,spacedCaps,true,sq);
  if(hints!=null){columns.addAll(spacedColumns);caps.addAll(spacedCaps);return hints;}
  return scaffolds(local,columns,caps,false,sq);
 }
 private static Map<BlockPos,BlockPos> scaffolds(Map<BlockPos,BlockState> local,List<Column> columns,Set<BlockPos> caps,boolean spaced,double sq){
  var stands=stands(local);var hints=new LinkedHashMap<BlockPos,BlockPos>();var d=local.keySet();
  int maxX=d.stream().mapToInt(BlockPos::getX).max().orElse(0),maxZ=d.stream().mapToInt(BlockPos::getZ).max().orElse(0);
  var doors=local.entrySet().stream().filter(c->c.getValue().getBlock() instanceof DoorBlock).map(Map.Entry::getKey).toList();
  var cells=local.entrySet().stream().filter(c->!c.getValue().isAir()&&!standReach(stands,c.getKey(),sq)).map(Map.Entry::getKey).sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)).toList();
  for(var p:cells){
   if(caps.contains(p)&&columns.stream().anyMatch(c->c.x()==p.getX()&&c.z()==p.getZ()))continue;
   int bestCost=Integer.MAX_VALUE;int bx=0,bz=0,bTop=0,bFeet=0,replace=-1;boolean found=false,bOut=true;
   for(int x=-1;x<=maxX+1;x++)for(int z=-1;z<=maxZ+1;z++){
    final int fx=x,fz=z;boolean outside=x<0||x>maxX||z<0||z>maxZ;
    if(doors.stream().anyMatch(o->Math.abs(o.getX()-fx)+Math.abs(o.getZ()-fz)<=(outside?2:1)))continue;
    if(!outside){var floor=local.get(new BlockPos(x,0,z));if(floor==null||!floor.isSolid())continue;}
    int existing=-1;for(int i=0;i<columns.size();i++)if(columns.get(i).x()==x&&columns.get(i).z()==z)existing=i;
    // relocate-fix: no column gets more than one other column beside it (when the design allows a plan without). In a cluster, the
    // columns of the level-VI expedition boxed each other's feet in: the way to one ran through the next, where the builder climbed,
    // stepped back out and went to and fro for good. A pair side by side is fine (the front corner of the AD-129 home needs one).
    if(spaced&&existing<0&&clusters(columns,fx,fz))continue;
    for(int feet=1;feet<=16;feet++){
     boolean ok=true;
     for(int y=1;y<=feet+1&&ok;y++){var cell=new BlockPos(x,y,z);ok=free(local.get(cell))||!outside&&cap(local,cell,maxX,maxZ);}
     if(!ok)break;
     if(x==p.getX()&&z==p.getZ())break;
     if(!reaches(x,feet,z,p,sq))continue;
     int top=feet+1;int cost=existing>=0?Math.max(0,top-columns.get(existing).top()):top;
     double dist=Math.pow(x-p.getX(),2)+Math.pow(z-p.getZ(),2);
     // AD-122: a column on the building's own floor goes before one outside the lot, whose ground may be a road, a neighbour's lot or a slope.
     boolean better=!found||bOut&&!outside||bOut==outside&&(cost<bestCost||cost==bestCost&&dist<Math.pow(bx-p.getX(),2)+Math.pow(bz-p.getZ(),2));
     if(better){bestCost=cost;bx=x;bz=z;bTop=existing>=0?Math.max(top,columns.get(existing).top()):top;bFeet=feet;replace=existing;found=true;bOut=outside;}
     break;
    }
   }
   if(!found){lastImpossible=p;return null;}
   var column=new Column(bx,bz,bTop);if(replace>=0)columns.set(replace,column);else columns.add(column);
   hints.put(p,new BlockPos(bx,bFeet,bz));
  }
  for(var c:columns)for(int y=1;y<=c.top();y++){
   var cell=new BlockPos(c.x(),y,c.z());if(!free(local.get(cell)))caps.add(cell);
   if(!standReach(stands,cell,sq))hints.put(cell,new BlockPos(c.x(),Math.max(1,y-2),c.z()));
  }
  return hints;
 }
 public static Survey survey(ServerLevel l,SettlementData.Entry e,String design,int variant,BlockPos origin){return survey(l,e,design,variant,origin,null);}
 /** AD-059: with a building given, the survey is the repair of that building at its own place — its own cells are not in anybody's way,
  *  only what is missing from the design becomes work, and scaffolds go up only when a missing block needs them. */
 public static Survey survey(ServerLevel l,SettlementData.Entry e,String design,int variant,BlockPos origin,Settlement.Building repair){return survey(l,e,design,variant,origin,repair,-1,false);}
 /** AD-125: a moved building is surveyed as a new one at its new place, at its kept level ({@code anyDesign} lets a {@code type@N} design in),
  *  with its core capped at the grade that stands (cap, -1 = research) — and every operation marked with its phase: 0 ground, 2 building. */
 public static Survey survey(ServerLevel l,SettlementData.Entry e,String design,int variant,BlockPos origin,Settlement.Building repair,int cap,boolean anyDesign){return survey(l,e,design,variant,origin,repair,cap,anyDesign,null);}
 /** AD-135: an annex is surveyed as a new building at its site beside its parent, the parent's buffer left out ({@code beside}). */
 public static Survey survey(ServerLevel l,SettlementData.Entry e,String requested,int variant,BlockPos origin,Settlement.Building repair,int cap,boolean anyDesign,UUID beside){
  return survey(l,e,requested,variant,origin,repair,cap,anyDesign,beside,repair==null?BuildingWood.choose(l,e,requested):repair.wood());
 }
 /** A relocation retains its saved timber instead of choosing a new local species. */
 public static Survey survey(ServerLevel l,SettlementData.Entry e,String requested,int variant,BlockPos origin,Settlement.Building repair,int cap,boolean anyDesign,UUID beside,String wood){
  return survey(l,e,requested,variant,origin,repair,cap,anyDesign,beside,wood,false);
 }
 /** A mayor may survey a food rescue while an untouched, unfunded project waits. */
 public static Survey foodSurvey(ServerLevel l,SettlementData.Entry e,int rotation,BlockPos site){
  return foodSurvey(l,e,"farm",rotation,site);
 }
 public static Survey foodSurvey(ServerLevel l,SettlementData.Entry e,String design,int rotation,BlockPos site){
  return survey(l,e,design,rotation,site,null,-1,false,null,BuildingWood.choose(l,e,design),FoodConstruction.maySuspend(l,e)&&design.equals(FoodConstruction.rescueDesign(l,e)));
 }
 private static Survey survey(ServerLevel l,SettlementData.Entry e,String requested,int variant,BlockPos origin,Settlement.Building repair,int cap,boolean anyDesign,UUID beside,String wood,boolean priorityFood){
  var conflicts=new LinkedHashSet<BlockPos>();var empty=new CompoundTag();
  if(repair==null&&!anyDesign&&!planned(requested))return new Survey(empty,conflicts,"design");
  String design=repair==null&&requested.equals(Walls.TOWER)?TowerStages.design(l,e):requested;
  // AD-068: a design may be turned by quarter turns; a repair keeps the turn of the building it repairs.
  if(variant<0||variant>3)return new Survey(empty,conflicts,"rotation");
  // AD-078: the crew's own repair, still untouched, is no reason to refuse a survey.
  if(HallUpgradeGoal.pending(l,e.settlement().id())&&!HallUpgradeGoal.yields(l,e.settlement().id())&&!priorityFood)return new Survey(empty,conflicts,"busy");
  if(repair==null&&e.settlement().buildings().stream().noneMatch(b->{var at=e.center().offset(b.x(),b.y(),b.z());double dx=at.getX()-origin.getX(),dz=at.getZ()-origin.getZ();return dx*dx+dz*dz<=REACH*REACH;}))return new Survey(empty,conflicts,"reach");
  var local=BuildingWood.apply(BuildingPlacement.layout(design,BlockPos.ZERO,variant),wood);
  // An upgrade retires only its own entrance plaque, including a plaque outside the next footprint.
  if(repair!=null&&origin.equals(BuildingPlacement.origin(e,repair))){var plaque=BuildingSigns.position(e,repair);if(plaque!=null&&BuildingSigns.owned(l,e,repair,plaque))local.putIfAbsent(plaque.subtract(origin),Blocks.AIR.defaultBlockState());}
  // AD-153: builders of Construction IV/VI reach two or three times as far: the plan leaves fewer cells (or none) to scaffold columns.
  var plan=scaffoldPlan(design,variant,ResearchKnobs.reach(l,e));
  var columns=plan.columns();var caps=plan.caps();var hints=plan.hints();
  if(hints==null)return new Survey(empty,conflicts,"access");
  var clears=new ArrayList<Op>();var fills=new ArrayList<Op>();var lower=new ArrayList<Op>();var upper=new ArrayList<Op>();var capOps=new HashMap<BlockPos,Op>();
  // AD-112 (owner, 2026-09-19): a core or ring is set only up to the grade the research of this building's own branch allows.
  int[] researched={-1};
  for(var cell:local.entrySet()){
   var pos=origin.offset(cell.getKey());var after=payable(cell.getValue());
   if(!l.hasChunkAt(pos)||l.isOutsideBuildHeight(pos))return new Survey(empty,conflicts,"unloaded");
   var before=l.getBlockState(pos);if(repair==null&&OwnershipEvents.disallowedPlacement(l,pos,beside)){conflicts.add(pos);continue;}
   // AD-147: the warehouse's page chests stand in cells its plans keep free (WarehouseStorage sets them down): its upgrade or repair leaves them.
   if(repair!=null&&(repair.type().equals("town_hall")||WarehouseStore.is(repair))&&after.isAir()&&HallStorage.partAt(l,pos))continue;
   boolean floor=cell.getKey().getY()==0;
   // Retain the existing civic plaque on site while a scaffold occupies its cell; restore it after dismantling.
   // No new item is manufactured or charged: this is the same registered plaque, not an arbitrary sign.
   if(before.equals(after)&&BuildingSigns.owned(l,e,repair,pos)&&columns.stream().anyMatch(c->c.x()==cell.getKey().getX()&&c.z()==cell.getKey().getZ()&&cell.getKey().getY()<=c.top())){
    clears.add(new Op(pos,before,Blocks.AIR.defaultBlockState(),"",""));lower.add(new Op(pos,Blocks.AIR.defaultBlockState(),after,"",""));continue;
   }
   // A framed window, fence, pane or wall already standing is the design's whatever its joins (they follow the neighbours as the rest is laid).
   if(before.equals(after)||FramedWindowBlock.sameWindow(before,after)||FenceJoins.sameJoinable(before,after)||(repair!=null||after.getBlock() instanceof BuildingCoreBlock)&&BuildingRepairs.present(before,cell.getValue()))continue;
   if(l.getBlockEntity(pos)!=null&&!BuildingSigns.owned(l,e,repair,pos)||(repair==null?!before.getFluidState().isEmpty():before.getBlock() instanceof LiquidBlock)||(repair==null&&!before.isAir()&&!clearable(before,floor))){conflicts.add(pos);continue;}
   if(after.getBlock() instanceof BuildingCoreBlock){if(researched[0]<0)researched[0]=cap>=0?cap:repair==null?BuildingTiers.MAX:BuildingTiers.researchedGrade(l,e,repair.type());
    for(var link:coreChain(before,after,researched[0]))(hints.containsKey(cell.getKey())?upper:lower).add(new Op(pos,link[0],link[1],coreItem((BuildingCoreBlock)after.getBlock(),link[1].getValue(BuildingCoreBlock.GRADE)),""));continue;}
   String item=material(after);if(item==null)return new Survey(empty,conflicts,"material");
   if(caps.contains(cell.getKey())){if(!before.isAir())clears.add(new Op(pos,before,Blocks.AIR.defaultBlockState(),"",""));capOps.put(cell.getKey(),new Op(pos,Blocks.AIR.defaultBlockState(),after,item,""));continue;}
   if(after.isAir()){clears.add(new Op(pos,before,after,"",""));continue;}
   (hints.containsKey(cell.getKey())?upper:lower).add(new Op(pos,before,after,item,""));
  }
  var scaffold=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState();var air=Blocks.AIR.defaultBlockState();
  // A repair puts up scaffolds only when a missing block needs somebody standing above the ground.
  boolean stands=repair==null||!upper.isEmpty()||!capOps.isEmpty();
  // AD-131: a scaffold may stand where this very project first takes a block away (a level that raises a roof clears the old one before its
  // scaffolds go up: the clears come first in the operations).
  var cleared=new HashSet<BlockPos>();for(var op:clears)cleared.add(op.pos);
  for(var c:columns)for(int y=1;y<=c.top();y++){var rel=new BlockPos(c.x(),y,c.z());if(caps.contains(rel))continue;var pos=origin.offset(rel);if(!l.hasChunkAt(pos)||l.isOutsideBuildHeight(pos))return new Survey(empty,conflicts,"unloaded");var before=l.getBlockState(pos);
   if(!stands)break;
   // AD-135: an annex's scaffolds stand beside its parent as its blocks do (the tower mill's passage is scaffolded inside the restaurant's buffer).
   if(repair==null&&OwnershipEvents.disallowedPlacement(l,pos,beside)||l.getBlockEntity(pos)!=null&&!(cleared.contains(pos)&&BuildingSigns.owned(l,e,repair,pos))||!before.getFluidState().isEmpty()||!before.isAir()&&!before.canBeReplaced()&&!cleared.contains(pos))conflicts.add(pos);}
  for(var c:columns){var rel=new BlockPos(c.x(),0,c.z());if(!local.containsKey(rel)){var ground=origin.offset(rel);if(!l.hasChunkAt(ground)||!l.getBlockState(ground).isFaceSturdy(l,ground,Direction.UP))conflicts.add(ground);}}
  var size=BuildingPlacement.size(design,variant);
  // AD-123: Construction I–IV let a site take deeper fill; read once per survey (the NPC planner runs many surveys a pass).
  int foundation=ResearchKnobs.foundation(l,e);
  for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++){
   var column=new ArrayList<Op>();boolean supported=false;
   for(int depth=1;depth<=foundation+1&&!supported;depth++){
    var pos=origin.offset(x,-depth,z);if(!l.hasChunkAt(pos)||l.isOutsideBuildHeight(pos))return new Survey(empty,conflicts,"unloaded");
    var before=l.getBlockState(pos);
    // A kept mine's designed stairwell is not missing foundation. Never fill its existing underground passage.
    var below=local.get(new BlockPos(x,-depth,z));
    if(repair!=null&&below!=null&&BuildingRepairs.present(before,payable(below))){supported=true;break;}
    if(before.isFaceSturdy(l,pos,Direction.UP)&&before.getFluidState().isEmpty()){supported=true;break;}
    if(depth>foundation||repair==null&&OwnershipEvents.disallowedPlacement(l,pos)||l.getBlockEntity(pos)!=null||!before.getFluidState().isEmpty()||!before.canBeReplaced()){conflicts.add(pos);break;}
    column.add(new Op(pos,before,Blocks.COBBLESTONE.defaultBlockState(),"minecraft:cobblestone",""));
   }
   if(supported){Collections.reverse(column);fills.addAll(column);}
  }
  Comparator<Op> order=Comparator.<Op>comparingInt(o->o.pos.getY()).thenComparingInt(o->o.pos.getZ()).thenComparingInt(o->o.pos.getX());
  clears.sort(Comparator.<Op>comparingInt(o->-o.pos.getY()).thenComparingInt(o->o.pos.getZ()).thenComparingInt(o->o.pos.getX()));lower.sort(order);upper.sort(order);
  // AD-069: the doorway stays an open gap until the very end — a closed door stops a builder who steps the last block straight in,
  // so the door leaves are the last blocks of the building, set after the scaffolds are down. Plaques also wait until their supports stand.
  var doors=new ArrayList<Op>();for(var list:List.of(lower,upper))for(var it=list.iterator();it.hasNext();){var op=it.next();if(op.after.getBlock() instanceof DoorBlock||op.after.getBlock() instanceof SignBlock){doors.add(op);it.remove();}}
  doors.sort(order);
  var ops=new ArrayList<Op>();ops.addAll(clears);ops.addAll(fills);ops.addAll(lower);
  if(stands)for(var c:columns)for(int y=1;y<=c.top();y++)ops.add(new Op(origin.offset(c.x(),y,c.z()),air,scaffold,"villageastra:timber_scaffold",""));
  ops.addAll(upper);
  if(stands)for(var c:columns)for(int y=c.top();y>=1;y--){var rel=new BlockPos(c.x(),y,c.z());ops.add(new Op(origin.offset(rel),scaffold,air,"","villageastra:timber_scaffold"));var capOp=capOps.get(rel);if(capOp!=null)ops.add(capOp);}
  else ops.addAll(capOps.values());
  ops.addAll(doors);
  var list=new ListTag();var cost=new CompoundTag();var ground=new HashSet<Op>(clears);ground.addAll(fills);
  for(var op:ops){
   var t=new CompoundTag();if(anyDesign)t.putInt("phase",ground.contains(op)?0:2);t.putLong("pos",op.pos.asLong());t.put("before",NbtUtils.writeBlockState(op.before));t.put("after",NbtUtils.writeBlockState(op.after));
   if(!op.item.isEmpty()){t.putString("item",op.item);cost.putInt(op.item,cost.getInt(op.item)+1);}
   if(!op.returned.isEmpty())t.putString("return",op.returned);
   var hint=hints.get(op.pos.subtract(origin));
   if(hint!=null){t.putLong("stand",origin.offset(hint).asLong());t.putInt("standBase",origin.getY()+1);}
   list.add(t);
  }
  var state=new CompoundTag();state.putInt("schema",2);state.putString("kind","building");var id=UUID.randomUUID();state.putUUID("id",id);state.putUUID("project",id);
  state.putInt("architectureRevision",119);state.putString("design",design);state.putLong("origin",origin.asLong());state.putBoolean("noHatch",true);state.putLong("hatch",origin.offset(-100,0,-100).asLong());state.putInt("level",0);state.putInt("rotation",variant);
  state.put("ops",list);state.put("cost",cost);state.put("cargo",new ListTag());
  if(!wood.isEmpty())state.putString("wood",wood);
  if(repair==null&&BuildingBlueprints.base(design).equals(Walls.TOWER))state.putInt("upgradeLevel",BuildingBlueprints.level(design));
  if(repair!=null){state.putBoolean("repair",true);state.putUUID("building",repair.id());}
  if(repair==null&&design.equals("farm")&&conflicts.isEmpty()){
   var offset=origin.subtract(e.center());var farm=new Settlement.Building(buildingId(state),"farm",offset.getX(),offset.getY(),offset.getZ(),variant,1,wood);
   var field=FarmField.initial(l,e,farm);if(!field.ok())return new Survey(state,field.conflicts(),field.reason());
   for(var op:field.ops())list.add(op);field.cost().forEach((k,v)->cost.putInt(k,cost.getInt(k)+v));state.putInt("fieldLevel",1);state.putInt("fieldTimberRevision",328);
  }
  return new Survey(state,conflicts,conflicts.isEmpty()?"":"conflicts");
 }
 /** Server re-survey at the moment of approval; nothing from the client except the chosen design/site is trusted. */
 /** AD-043: a besieged settlement approves no new building. */
 public static String approve(ServerLevel l,SettlementData.Entry e,String design,int variant,BlockPos origin){
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  // AD-077: research opens the design itself, not only its levels.
  var research=ResearchGate.designRefusal(l,e,design);if(!research.isEmpty())return research;
  return approveWhenFree(l,e,design,variant,origin);
 }
 private static String approveWhenFree(ServerLevel l,SettlementData.Entry e,String design,int variant,BlockPos origin){
  // AD-078: one project at a time — but the crew's own repair gives way to what the mayor orders.
  if(HallUpgradeGoal.pending(l,e.settlement().id())&&!HallUpgradeGoal.yields(l,e.settlement().id()))return "busy";
  var survey=survey(l,e,design,variant,origin);if(!survey.ok())return survey.reason();
  HallUpgradeGoal.yield(l,e.settlement().id());
  HallUpgradeGoal.enqueue(l,e,survey.state());SettlementData.get(l.getServer()).setDirty();return "";
 }
 /** Natural drift since the survey (snowy grass, stair shape, grass spreading, vanished plants) is accepted; player-made or protected changes are not. */
 public static boolean reconcile(ServerLevel l,CompoundTag op,BlockPos origin,CompoundTag project){
  if(FieldWaterDrift.reconcile(l,project,op))return true;
  return reconcile(l,op,origin);
 }
 public static boolean reconcile(ServerLevel l,CompoundTag op,BlockPos origin){
  var step=HallConstructionPlan.step(op);var now=l.getBlockState(step.pos());
  if(now.equals(step.before()))return true;
  if(l.getBlockEntity(step.pos())!=null||!now.getFluidState().isEmpty()||now.is(Blocks.LADDER)||step.before().is(Blocks.LADDER)||now.is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())||step.before().is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get()))return false;
  // Removing a roof or wall also drops its lanterns, torches and other supported
  // decoration. An already empty demolition cell needs no second removal;
  // it never grants materials or bypasses a core, ladder or scaffold operation.
  if(now.isAir()&&step.after().isAir()&&!(step.before().getBlock() instanceof BuildingCoreBlock)){
   op.put("before",NbtUtils.writeBlockState(now));return true;}
  boolean floor=step.pos().getY()<=origin.getY();
  // AD-112: a core of another grade than planned is no drift: taking it as one could let a later operation lower the grade.
  if(now.getBlock() instanceof BuildingCoreBlock||step.before().getBlock() instanceof BuildingCoreBlock)return false;
  // AD-130: a crop the farmer sowed where the plan left air (a plot a barn column stands on) is drift the builder takes; elsewhere a crop
  // in the way is still a conflict the survey reports.
  boolean sown=step.before().isAir()&&(now.getBlock() instanceof net.minecraft.world.level.block.CropBlock||now.is(Blocks.SUGAR_CANE));
  if(now.getBlock()!=step.before().getBlock()&&!sown&&!(clearable(now,floor)&&clearable(step.before(),floor)))return false;
  op.put("before",NbtUtils.writeBlockState(now));return true;
 }
 /** Final geometry must match the last planned state of every cell; registration is idempotent. */
 public static boolean complete(ServerLevel l,SettlementData.Entry e,CompoundTag state){
  if(state.getBoolean("relocate"))return Relocations.complete(l,e,state);
  var last=new LinkedHashMap<BlockPos,BlockState>();var field=new HashSet<BlockPos>();
  for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var step=HallConstructionPlan.step((CompoundTag)raw);last.put(step.pos(),step.after());if(((CompoundTag)raw).getBoolean("field"))field.add(step.pos());}
  // AD-112: a core counts at its grade or above; on the field's plots, grass grown back where the builders cleared counts as cleared
  // (AD-130: and a crop the farmer sowed again on a plot a barn column stood on).
  for(var cell:last.entrySet()){if(!l.hasChunkAt(cell.getKey()))return false;var now=l.getBlockState(cell.getKey());
   if(!BuildingRepairs.present(now,cell.getValue())&&!(field.contains(cell.getKey())&&cell.getValue().isAir()&&(now.canBeReplaced()||now.getBlock() instanceof net.minecraft.world.level.block.CropBlock)&&now.getFluidState().isEmpty()))return false;}
  var id=buildingId(state);var s=e.settlement();var origin=BlockPos.of(state.getLong("origin"));var design=state.getString("design");
  if(s.buildings().stream().noneMatch(b->b.id().equals(id))){var offset=origin.subtract(e.center());s.addBuilding(new Settlement.Building(id,BuildingBlueprints.base(design),offset.getX(),offset.getY(),offset.getZ(),BuildingPlacement.turns(state.getInt("rotation")),1,state.getString("wood")));}
  // AD-135: a finished annex belongs to the building it was ordered for.
  if(state.hasUUID("annexOf")&&s.buildings().stream().anyMatch(b->b.id().equals(state.getUUID("annexOf"))))s.linkAnnex(id,state.getUUID("annexOf"));
  if(state.getInt("architectureRevision")<119)s.markLegacyArchitecture(id);
  if(HOUSING.contains(design)&&s.homes().stream().noneMatch(h->h.id().equals(id)))s.addHome(new Settlement.Home(id,homeLevel(design),capacity(design),true));
  // AD-073: a finished upgrade keeps the building at its new level. AD-112: a farm project that laid field keeps that field and its side.
  if(state.getInt("upgradeLevel")>1){var built=s.buildings().stream().filter(b->b.id().equals(id)).findFirst().orElse(null);
   if(built!=null&&built.type().equals("farm")){if(state.getBoolean("fieldWest"))s.turnFieldWest(id);if(state.getInt("fieldLevel")>0)s.raiseFieldLevel(id,state.getInt("upgradeLevel"));}
   if(built!=null)BuildingTiers.completed(e,built,state.getInt("upgradeLevel"));
   // AD-123 (H1, C5): a house whose finished level placed more beds holds more people; ArchitectureMigration never gets here.
   if(built!=null)HousingLadder.upgraded(l,e,built,state.getInt("upgradeLevel"));}
  // AD-059: a repaired home is a home again.
  if(state.getBoolean("repair")&&s.homes().stream().anyMatch(h->h.id().equals(id)&&!h.usable()))s.restoreHome(id);
  // AD-123 (H2): a repair or refit that put its level's beds back raises the capacity the same way; no bed is handed out for free.
  if(state.getBoolean("repair")){var built=s.buildings().stream().filter(b->b.id().equals(id)).findFirst().orElse(null);if(built!=null)HousingLadder.upgraded(l,e,built,built.level());}
  SettlementData.get(l.getServer()).setDirty();return true;
 }
}
