package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.OwnershipEvents;
import org.villageastra.server.SettlementData;
/** AD-125: a building moved through the atlas. One ordinary crew project in phases — 0 the new ground, 1 the old building taken apart
 *  block by block into the project's cargo (container contents packed slot by slot), 2 the same building at its kept level built from that
 *  cargo, 3 the old lot back to ground. The building keeps its id, level, core grade, workers and dwellers; its record moves once, at the
 *  end of phase 2. Every block, slot and item goes through the world journal exactly once. */
public final class Relocations {
 private Relocations(){}
 /** Seconds one operation takes a builder on average (walk, labour); the estimate shown before the order. */
 public static final double SECONDS_PER_OP=1.6;
 /** A move whose old place stands at less than this part of its design is refused: the builders repair it first. */
 public static final double WHOLE=.9;
 /** What may move: every field-ordered building except the livestock yard (its herd), the wall tower (the ring) and the quarry (its pit). */
 public static final Set<String> MOVABLE;
 static{var set=new TreeSet<>(BuildingOrders.ORDERABLE);set.removeAll(Set.of("livestock","wall_tower","quarry"));MOVABLE=Collections.unmodifiableSet(set);}
 public record Plan(CompoundTag state,Set<BlockPos> conflicts,Set<BlockPos> oldConflicts,List<BlockPos> oldCells,String reason,CompoundTag stats){
  public boolean ok(){return reason.isEmpty()&&conflicts.isEmpty()&&oldConflicts.isEmpty();}}
 private static Plan refused(String reason){return new Plan(new CompoundTag(),Set.of(),Set.of(),List.of(),reason,new CompoundTag());}
 /** Dismantling order: the build order (course by course from the floor up) reversed; a core's chain keeps its own order (stable sort). */
 private static final Comparator<Op> TOP_DOWN=Comparator.<Op>comparingInt(o->-o.pos.getY()).thenComparingInt(o->-o.pos.getZ()).thenComparingInt(o->-o.pos.getX());
 private record Op(BlockPos pos,BlockState before,BlockState after,String item,String returned,BlockPos stand,boolean dismantle,int phase,boolean pack){}

 // ---------- refusals ----------
 /** The cheap part of the refusal, without a world scan: the type, the old architecture, the starter house, a siege and a busy crew. */
 public static String quick(ServerLevel l,SettlementData.Entry e,Settlement.Building b,boolean besieged,boolean busy){
  var t=b.type();
  if(t.startsWith("town_hall"))return "hall";if(t.equals("farm"))return "field";if(t.equals("forester"))return "grove";
  if(t.equals("mine")||e.settlement().mineAreas().containsKey(b.id()))return "shaft";if(t.equals("wall_tower"))return "wall";
  // AD-135: an annex stands where its parent names it, and a parent does not leave its annex behind.
  if(org.villageastra.domain.AnnexTypes.annex(t)||!e.settlement().annexes(b.id()).isEmpty())return "annex";
  if(t.equals("livestock"))return "animals";if(t.equals("quarry"))return "pit";if(!MOVABLE.contains(t))return "design";
  // C9: the same expression OwnershipEvents uses to protect the whole starter layout — only while that house is registered there.
  var starter=Settlement.childId(e.settlement().id(),"house/0");
  if(b.id().equals(starter)&&e.settlement().buildings().stream().anyMatch(x->x.id().equals(starter)&&x.x()==10&&x.z()==0))return "starter";
  if(ArchitectureMigration.waiting(l,e,b))return "legacy";
  if(besieged)return "besieged";if(busy)return "busy";
  return "";
 }
 private static boolean busy(ServerLevel l,SettlementData.Entry e){var id=e.settlement().id();return HallUpgradeGoal.pending(l,id)&&!HallUpgradeGoal.yields(l,id);}
 /** First reason this building cannot move now, or empty. */
 public static String refusal(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var q=quick(l,e,b,Sieges.besieged(l.getServer(),e.settlement().id()),busy(l,e));if(!q.isEmpty())return q;
  var from=BuildingPlacement.origin(e,b);var size=BuildingPlacement.size(b.type(),b.rotation());
  for(int x=0;x<size[0];x+=Math.max(1,size[0]-1))for(int z=0;z<size[1];z+=Math.max(1,size[1]-1))if(!l.hasChunkAt(from.offset(x,0,z)))return "unloaded";
  // C10: a building that no longer stands as its design is repaired first; the move takes only the design's footprint.
  var design=BuildingTiers.layoutId(b.type(),BuildingTiers.built(e,b));int solid=0,present=0;
  for(var cell:BuildingPlacement.layout(e,b,design).entrySet()){if(cell.getValue().isAir()||cell.getValue().is(BlockTags.SAPLINGS)||cell.getValue().getBlock() instanceof CropBlock)continue;
   solid++;if(!l.hasChunkAt(cell.getKey()))return "unloaded";if(BuildingRepairs.present(l.getBlockState(cell.getKey()),cell.getValue()))present++;}
  if(present<solid*WHOLE)return "outdated";
  // C11: a smelting job holds the exact position of a furnace; it finishes against the old one before that furnace moves.
  for(var w:e.settlement().buildings()){var job=Workshops.inspect(l,w.id());if(job.isEmpty()||job.getString("stage").equals("idle"))continue;
   for(var key:List.of("furnace","installAt"))if(job.contains(key,Tag.TAG_LONG)){var p=BlockPos.of(job.getLong(key));
    if(p.getX()>=from.getX()&&p.getX()<from.getX()+size[0]&&p.getZ()>=from.getZ()&&p.getZ()<from.getZ()+size[1]&&p.getY()>=from.getY()-2&&p.getY()<=from.getY()+24)return "working";}}
  return "";
 }
 public static Settlement.Building at(SettlementData.Entry e,BlockPos origin){return e.settlement().buildings().stream().filter(b->BuildingPlacement.origin(e,b).equals(origin)).findFirst().orElse(null);}

 // ---------- the plan ----------
 private static boolean soil(BlockState s){return s.is(BlockTags.DIRT)||s.is(Blocks.FARMLAND)||s.is(Blocks.DIRT_PATH);}
 private static boolean natural(BlockState s){
  return MapOrders.minersOnly(s)||s.is(BlockTags.DIRT)||s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL)||s.is(BlockTags.LEAVES)||s.is(Blocks.SNOW)||s.is(Blocks.SNOW_BLOCK)
   ||s.canBeReplaced()||s.is(BlockTags.FLOWERS)||s.is(BlockTags.SAPLINGS)||s.getBlock() instanceof BushBlock;
 }
 private static final Map<Block,Boolean> CONTAINERS=new java.util.concurrent.ConcurrentHashMap<>();
 /** Whether a block keeps its items in a container: the only kind whose contents are packed and unpacked. */
 public static boolean container(BlockState s){return s.getBlock() instanceof EntityBlock eb&&CONTAINERS.computeIfAbsent(s.getBlock(),k->eb.newBlockEntity(BlockPos.ZERO,s) instanceof Container);}
 /** A block entity that holds items without being a container (a lectern's book, a campfire's food): taking it would drop them. */
 private static boolean holdsItems(net.minecraft.world.level.block.entity.BlockEntity be){
  if(be instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern)return lectern.hasBook();
  if(be instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity fire)return fire.getItems().stream().anyMatch(s->!s.isEmpty());
  return false;
 }
 private static String item(BlockState s){var m=BuildingOrders.material(BuildingOrders.payable(s));return m==null?"":m;}
 private static String key(ItemStack s){return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();}
 public static Plan plan(ServerLevel l,SettlementData.Entry e,Settlement.Building b,BlockPos to,int turns){
  var reason=refusal(l,e,b);if(!reason.isEmpty())return refused(reason);
  if(turns<0||turns>3)return refused("rotation");
  if(!Atlas.area(l,e).contains(new ChunkPos(to)))return refused("outside");
  var from=BuildingPlacement.origin(e,b);int rot=b.rotation();
  if(from.equals(to)&&turns==rot)return refused("same_place");
  int kept=BuildingTiers.built(e,b);String design=BuildingTiers.layoutId(b.type(),kept);
  var local=BuildingWood.apply(BuildingPlacement.layout(design,BlockPos.ZERO,rot),b.wood());var size=BuildingPlacement.size(b.type(),rot);
  int top=local.keySet().stream().mapToInt(BlockPos::getY).max().orElse(4)+1;
  // C6: the core is found by scanning the old place — its highest grade is the grade that moves.
  int grade=grade(l,e,b);
  var survey=BuildingOrders.survey(l,e,design,turns,to,null,grade,true,null,b.wood());
  if(survey.state().isEmpty())return new Plan(new CompoundTag(),survey.conflicts(),Set.of(),List.of(),survey.reason(),new CompoundTag());
  // ---- phase 1: the old building, taken apart in the reverse order of building it ----
  var plan=BuildingOrders.scaffoldPlan(design,rot);var hints=plan.hints()==null?Map.<BlockPos,BlockPos>of():plan.hints();var stands=BuildingOrders.stands(local);
  var near=new HashSet<UUID>();for(var other:e.settlement().buildings())if(!other.id().equals(b.id())&&BuildingPlacement.origin(e,other).distSqr(from)<=96*96)near.add(other.id());
  var oldConflicts=new LinkedHashSet<BlockPos>();var air=Blocks.AIR.defaultBlockState();var scaffold=scaffoldBlock().defaultBlockState();
  var fluids=new ArrayList<Op>();var pairs=new ArrayList<Op>();var fragileLow=new ArrayList<Op>();var solidLow=new ArrayList<Op>();var fragileUp=new ArrayList<Op>();var solidUp=new ArrayList<Op>();
  var columnCells=new HashSet<BlockPos>();for(var c:plan.columns())for(int y=1;y<=c.top();y++)columnCells.add(new BlockPos(c.x(),y,c.z()));
  boolean anyUpper=false;var inColumns=new HashMap<BlockPos,List<Op>>();
  for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++)for(int y=top;y>=1;y--){
   var rel=new BlockPos(x,y,z);var pos=from.offset(rel);var st=l.getBlockState(pos);if(st.isAir())continue;
   // C15: standing water or lava goes first, so nothing runs out when the walls around it come down.
   if(st.getBlock() instanceof LiquidBlock){if(st.getFluidState().isSource())fluids.add(new Op(pos,st,air,"","",null,true,1,false));continue;}
   var designed=local.get(rel);boolean ours=designed!=null&&!designed.isAir()&&BuildingRepairs.present(st,designed);
   if(!ours&&natural(st)||st.getBlock().defaultDestroyTime()<0)continue;
   if(OwnershipEvents.protectedBlock(l,pos,other->near.contains(other.id()))||Roads.cell(l,pos)!=null)continue;
   var be=l.getBlockEntity(pos);if(be!=null&&!(be instanceof Container)&&holdsItems(be)){oldConflicts.add(pos);continue;}
   BlockPos stand=hints.get(rel);boolean upper=stand!=null;
   if(!upper&&!BuildingOrders.standReach(stands,rel)){
    for(var c:plan.columns())for(int feet=1;feet<c.top()&&stand==null;feet++)if(BuildingOrders.reaches(c.x(),feet,c.z(),rel))stand=new BlockPos(c.x(),feet,c.z());
    if(stand==null){oldConflicts.add(pos);continue;}upper=true;}
   anyUpper|=upper;
   var ops=chain(b,pos,st,upper?from.offset(stand):null,be instanceof Container);
   if(columnCells.contains(rel)){inColumns.put(rel,ops);continue;}
   boolean pair=st.getBlock() instanceof DoorBlock||st.getBlock() instanceof BedBlock;
   boolean fragile=!st.isCollisionShapeFullBlock(l,pos);
   // relocate-fix: only what nobody can stand in or bump into (a torch, a carpet, a flower) and the containers go first from the lower storey. A
   // window pane taken early left a hole on the plinth under the jetty beam, where the builder walked in and stuck; panes, fences and stair
   // benches come down with their wall, course by course.
   boolean early=be instanceof Container||st.getCollisionShape(l,pos).isEmpty();
   (upper?fragile?fragileUp:solidUp:pair?pairs:fragile&&early?fragileLow:solidLow).addAll(ops);
  }
  // C2: a door comes out lower half first (it carries the item), a bed head first; with flag 18 neither half takes the other along.
  pairs.sort(Comparator.<Op>comparingInt(o->o.before.getBlock() instanceof BedBlock&&o.before.getValue(BedBlock.PART)==BedPart.FOOT?1:0).thenComparingInt(o->o.pos.getY()));
  for(var entity:l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new AABB(from,from.offset(size[0],top+1,size[1])),x->x instanceof net.minecraft.world.entity.decoration.HangingEntity||x instanceof net.minecraft.world.entity.decoration.ArmorStand))oldConflicts.add(entity.blockPosition());
  var ops=new ArrayList<Op>();ops.addAll(fluids);ops.addAll(pairs);ops.addAll(fragileLow);
  if(anyUpper){
   for(var c:plan.columns()){
    var ground=from.offset(c.x(),0,c.z());boolean inside=c.x()>=0&&c.z()>=0&&c.x()<size[0]&&c.z()<size[1];
    if(!inside&&!l.getBlockState(ground).isFaceSturdy(l,ground,net.minecraft.core.Direction.UP))oldConflicts.add(ground);
    for(int y=1;y<=c.top();y++){var rel=new BlockPos(c.x(),y,c.z());var pos=from.offset(rel);var taken=inColumns.remove(rel);if(taken!=null)ops.addAll(taken);
     else{var st=l.getBlockState(pos);if(!st.isAir()&&!st.canBeReplaced())oldConflicts.add(pos);}
     var hint=hints.get(rel);ops.add(new Op(pos,air,scaffold,"villageastra:timber_scaffold","",hint==null?null:from.offset(hint),false,1,false));}
   }
   // relocate-fix: the upper cells come down as the build put them up, played backwards — the whole roof, then course by course down to
   // the deck (a scan column by column once left the ridge of the AD-129 home to the last and its builder in pockets of half-taken walls).
   // Each cell then stands in the same world its column was planned for when it was set.
   var upper=new ArrayList<Op>();upper.addAll(fragileUp);upper.addAll(solidUp);upper.sort(TOP_DOWN);ops.addAll(upper);
   // relocate-fix: a column comes down from inside itself like a new building's (two blocks under the cell), not from the ground — out of
   // reach from below, its top cells were given up after three tries and the scaffolds stayed on the old lot.
   for(var c:plan.columns())for(int y=c.top();y>=1;y--){var hint=hints.get(new BlockPos(c.x(),y,c.z()));
    ops.add(new Op(from.offset(c.x(),y,c.z()),scaffold,air,"","villageastra:timber_scaffold",hint==null?null:from.offset(hint),false,1,false));}
  }
  for(var left:inColumns.values())ops.addAll(left);
  // The lower storey likewise from its top course down, so no plinth or half wall is left under a standing course for a worker to climb into.
  solidLow.sort(TOP_DOWN);ops.addAll(solidLow);
  // ---- phase 3: the old lot back to ground, floor first; the cobblestone under it stays in the earth ----
  var lot=new ArrayList<Op>();
  for(var cell:local.entrySet()){var rel=cell.getKey();if(rel.getY()>0||cell.getValue().isAir())continue;var pos=from.offset(rel);var st=l.getBlockState(pos);
   if(st.isAir()||soil(st)||!BuildingRepairs.present(st,cell.getValue())||l.getBlockEntity(pos)!=null||Roads.cell(l,pos)!=null||OwnershipEvents.protectedBlock(l,pos,other->near.contains(other.id())))continue;
   lot.add(new Op(pos,st,Blocks.DIRT.defaultBlockState(),"minecraft:dirt",item(st),null,true,3,false));}
  lot.sort(Comparator.<Op>comparingInt(o->-o.pos.getY()).thenComparingInt(o->o.pos.getZ()).thenComparingInt(o->o.pos.getX()));
  // ---- the whole list: phase 0 and 2 from the survey, 1 and 3 from here ----
  var state=survey.state();var built=state.getList("ops",Tag.TAG_COMPOUND);var list=new ListTag();
  for(var raw:built){var t=(CompoundTag)raw;if(t.getInt("phase")==0){soilReturn(t);list.add(t);}}
  int firstOld=list.size();
  for(var op:ops)list.add(tag(op,from));
  int firstNew=list.size();
  for(var raw:built){var t=(CompoundTag)raw;if(t.getInt("phase")==2){soilReturn(t);list.add(t);}}
  for(var op:lot)list.add(tag(op,from));
  // Unpack: the container of a design cell goes into the same design cell of the copy; furniture the design lacks goes to the hall.
  var w=BuildingBlueprints.design(b.type()).width();var d=BuildingBlueprints.design(b.type()).depth();int containers=0,packed=0;
  for(int i=firstOld;i<firstNew;i++){var t=list.getCompound(i);if(!t.getBoolean("pack"))continue;containers++;var pos=BlockPos.of(t.getLong("pos"));
   if(l.getBlockEntity(pos) instanceof Container c)for(int s=0;s<c.getContainerSize();s++)packed+=c.getItem(s).getCount();
   var design0=BuildingPlacement.unturn(pos.subtract(from),w,d,rot);var target=to.offset(BuildingPlacement.turn(design0.getX(),design0.getY(),design0.getZ(),w,d,turns));boolean found=false;
   for(int j=firstNew;j<list.size()&&!found;j++){var n=list.getCompound(j);if(n.getInt("phase")!=2||n.getLong("pos")!=target.asLong()||n.contains("unpack"))continue;
    if(container(HallConstructionPlan.step(n).after())){n.putLong("unpack",pos.asLong());found=true;}}
   if(!found)t.putBoolean("orphan",true);}
  state.put("ops",list);
  var cost=netCost(list,0,new HashMap<>());state.put("cost",cost);state.put("quotedCost",cost.copy());
  state.putBoolean("relocate",true);state.putUUID("building",b.id());state.putLong("from",from.asLong());state.putInt("fromRotation",rot);
  state.putInt("keptLevel",kept);state.putInt("grade",grade);state.put("stored",new ListTag());
  int[] phases=new int[4];for(var raw:list)phases[((CompoundTag)raw).getInt("phase")]++;state.putIntArray("phases",phases);
  // ---- what the mayor sees ----
  var stats=new CompoundTag();var need2=new HashMap<String,Integer>();var back1=new HashMap<String,Integer>();int place=0,dismantle=0,returned=0;
  for(var raw:list){var t=(CompoundTag)raw;int phase=t.getInt("phase");
   if(phase==2&&t.contains("item")){need2.merge(t.getString("item"),1,Integer::sum);if(!t.getString("item").equals("villageastra:timber_scaffold"))place++;}
   if(phase==1&&t.contains("return")&&t.getBoolean("dismantle")){dismantle++;returned++;back1.merge(t.getString("return"),1,Integer::sum);}}
  int reused=0;for(var k:need2.entrySet())reused+=Math.min(k.getValue(),back1.getOrDefault(k.getKey(),0));
  int workers=0,dwellers=0;for(var r:e.settlement().residents()){if(!r.alive())continue;var job=e.settlement().workplace(r.id());if(job!=null&&job.id().equals(b.id()))workers++;if(b.id().equals(r.home()))dwellers++;}
  stats.putInt("dismantle",dismantle);stats.putInt("returned",returned);stats.putInt("place",place);stats.putInt("reused",reused);stats.putInt("containers",containers);stats.putInt("packed",packed);
  stats.putInt("seconds",(int)Math.round(list.size()*SECONDS_PER_OP));stats.putInt("downtime",(int)Math.round((phases[1]+phases[2])*SECONDS_PER_OP));
  stats.putInt("workers",workers);stats.putInt("dwellers",dwellers);stats.putInt("level",kept);stats.putInt("grade",grade);
  var oldCells=new ArrayList<BlockPos>();for(int i=firstOld;i<firstNew;i++){var t=list.getCompound(i);if(t.getBoolean("dismantle"))oldCells.add(BlockPos.of(t.getLong("pos")));}
  // The old building's own buffer keeps the new place off: a move goes at least four blocks away from where the building stands.
  var conflicts=survey.conflicts();String why=survey.reason();
  if(!conflicts.isEmpty()){int h=top+3;boolean close=conflicts.stream().anyMatch(p->p.getX()>=from.getX()-3&&p.getX()<from.getX()+size[0]+3&&p.getZ()>=from.getZ()-3&&p.getZ()<from.getZ()+size[1]+3&&p.getY()>=from.getY()-3&&p.getY()<=from.getY()+h);
   stats.putBoolean("tooClose",close);if(close)why="too_close";}
  if(why.isEmpty()&&!oldConflicts.isEmpty())why="old_blocked";
  // Nothing of the old building could be taken (all of it held by something else): the move would only build a second copy.
  if(why.isEmpty()&&oldCells.isEmpty())why="old_blocked";
  return new Plan(state,conflicts,oldConflicts,oldCells,why,stats);
 }
 /** C6: the highest grade of this building's own core standing anywhere on its lot (1 when it has none). */
 public static int grade(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var from=BuildingPlacement.origin(e,b);var size=BuildingPlacement.size(b.type(),b.rotation());
  int top=BuildingPlacement.layout(BuildingTiers.layoutId(b.type(),BuildingTiers.built(e,b)),BlockPos.ZERO,b.rotation()).keySet().stream().mapToInt(BlockPos::getY).max().orElse(4)+1,grade=1;
  for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++)for(int y=0;y<=top;y++){var s=l.getBlockState(from.offset(x,y,z));if(Cores.isCoreOf(s,b.type()))grade=Math.max(grade,Cores.grade(s));}
  return grade;
 }
 private static Block scaffoldBlock(){return org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get();}
 /** The operations that take one cell: a core comes down grade by grade, each ring or the core itself going back to the cargo. */
 private static List<Op> chain(Settlement.Building b,BlockPos pos,BlockState st,BlockPos stand,boolean pack){
  if(st.getBlock() instanceof BuildingCoreBlock core&&(core.type().equals(b.type())||org.villageastra.domain.CoreCatalog.canonical(b.type()).equals("restaurant")&&core.type().equals(org.villageastra.domain.CoreCatalog.coreType(b.type())))){var out=new ArrayList<Op>();
   for(int g=st.getValue(BuildingCoreBlock.GRADE);g>=2;g--){var at=core.defaultBlockState().setValue(BuildingCoreBlock.GRADE,g);
    out.add(new Op(pos,at,g==2?Blocks.AIR.defaultBlockState():core.defaultBlockState().setValue(BuildingCoreBlock.GRADE,g-1),"",BuildingOrders.coreItem(core,g),stand,true,1,false));}
   return out;}
  return List.of(new Op(pos,st,Blocks.AIR.defaultBlockState(),"",item(st),stand,true,1,pack));
 }
 private static CompoundTag tag(Op op,BlockPos site){
  var t=new CompoundTag();t.putLong("pos",op.pos.asLong());t.put("before",NbtUtils.writeBlockState(op.before));t.put("after",NbtUtils.writeBlockState(op.after));
  if(!op.item.isEmpty())t.putString("item",op.item);if(!op.returned.isEmpty())t.putString("return",op.returned);
  if(op.stand!=null){t.putLong("stand",op.stand.asLong());t.putInt("standBase",site.getY()+1);}
  if(op.dismantle)t.putBoolean("dismantle",true);if(op.pack)t.putBoolean("pack",true);
  t.putInt("phase",op.phase);t.putLong("site",site.asLong());return t;
 }
 /** The lot's own soil is kept: a cell of dirt the new building takes gives its dirt to the cargo, for the old lot's floor later. */
 private static void soilReturn(CompoundTag t){var s=HallConstructionPlan.step(t);if(!t.contains("return")&&soil(s.before())&&!soil(s.after())&&!s.before().isAir())t.putString("return","minecraft:dirt");}
 /** C13: what the hall has to give so that no operation from {@code from} on ever lacks its item — the lowest running balance per item,
  *  starting from what the cargo already holds. Phase 3 is left out: its dirt comes from the lot's own soil or the cell stays as it is. */
 static CompoundTag netCost(ListTag ops,int from,Map<String,Integer> held){
  var balance=new HashMap<String,Integer>(held);var low=new HashMap<String,Integer>(held);
  for(int i=from;i<ops.size();i++){var t=ops.getCompound(i);if(t.getBoolean("done")||t.getInt("phase")>=3)continue;
   if(t.contains("item")){var k=t.getString("item");int v=balance.getOrDefault(k,0)-1;balance.put(k,v);low.merge(k,v,Math::min);}
   if(t.contains("return"))balance.merge(t.getString("return"),1,Integer::sum);}
  var cost=new CompoundTag();
  for(var k:low.keySet()){int have=held.getOrDefault(k,0),need=have+Math.max(0,-low.get(k));if(need>0)cost.putInt(k,need);}
  return cost;
 }

 // ---------- preview and order ----------
 public static CompoundTag preview(ServerLevel l,SettlementData.Entry e,BlockPos from,BlockPos to,int turns){
  var t=new CompoundTag();var b=at(e,from);t.putLong("origin",to.asLong());t.putLong("from",from.asLong());t.putInt("variant",turns);
  if(b==null){t.putString("reason","building");return t;}
  var size=BuildingPlacement.size(b.type(),turns);t.putInt("width",size[0]);t.putInt("depth",size[1]);t.putString("type",b.type());t.putUUID("building",b.id());
  var p=plan(l,e,b,to,turns);boolean besieged=Sieges.besieged(l.getServer(),e.settlement().id());
  t.putString("reason",besieged?"besieged":p.reason());t.putBoolean("ok",p.ok()&&!besieged);t.putBoolean("besieged",besieged);
  t.put("conflicts",new LongArrayTag(p.conflicts().stream().limit(Plans.MAX_CONFLICTS).map(BlockPos::asLong).toList()));t.putInt("conflictCount",p.conflicts().size());
  t.put("oldConflicts",new LongArrayTag(p.oldConflicts().stream().limit(Plans.MAX_CONFLICTS).map(BlockPos::asLong).toList()));
  t.putLongArray("oldCells",p.oldCells().stream().limit(Plans.MAX_VOLUME).mapToLong(BlockPos::asLong).toArray());
  t.putBoolean("busyBuilders",Roads.active(l,e.settlement().id()));t.merge(p.stats());
  if(p.state().isEmpty())return t;
  var ops=p.state().getList("ops",Tag.TAG_COMPOUND);var future=new LinkedHashMap<Long,Integer>();var demolish=new LinkedHashSet<Long>();var replace=new LinkedHashSet<Long>();var scaffold=scaffoldBlock();
  for(var raw:ops){var op=(CompoundTag)raw;int phase=op.getInt("phase");if(phase==1||phase==3)continue;var step=HallConstructionPlan.step(op);if(step.before().equals(step.after())||step.after().is(scaffold))continue;
   if(step.after().isAir()){if(!step.before().is(scaffold)&&!future.containsKey(step.pos().asLong()))demolish.add(step.pos().asLong());}
   else{future.put(step.pos().asLong(),Block.getId(step.after()));demolish.remove(step.pos().asLong());if(!step.before().isAir()&&!step.before().is(scaffold))replace.add(step.pos().asLong());}}
  var volume=new ArrayList<Integer>();
  for(var cell:future.entrySet()){if(volume.size()>=Plans.MAX_VOLUME*4)break;var at=BlockPos.of(cell.getKey());volume.add(at.getX()-to.getX());volume.add(at.getY()-to.getY());volume.add(at.getZ()-to.getZ());volume.add(cell.getValue());}
  t.putIntArray("future",volume.stream().mapToInt(Integer::intValue).toArray());
  t.putLongArray("demolish",demolish.stream().limit(Plans.MAX_VOLUME).mapToLong(Long::longValue).toArray());
  t.putLongArray("replaceCells",replace.stream().limit(Plans.MAX_VOLUME).mapToLong(Long::longValue).toArray());
  t.putInt("operations",ops.size());
  var cost=p.state().getCompound("cost");var materials=new ListTag();int items=0,missing=0;
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);
  for(var name:cost.getAllKeys().stream().sorted().toList()){int need=cost.getInt(name);items+=need;var item=BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(name));
   int free=chest==null?0:Math.min(need,HallReserve.count(l,e,hall,chest,s->s.is(item)));var row=new CompoundTag();row.putString("item",name);row.putInt("need",need);row.putInt("free",free);row.putInt("missing",need-free);materials.add(row);missing+=need-free;}
  t.put("materials",materials);t.putInt("items",items);t.putInt("shortage",missing);
  return t;
 }
 /** Server re-plan at the moment of the order; nothing from the client is trusted but the two places and the turn. */
 public static String order(ServerLevel l,SettlementData.Entry e,BlockPos from,BlockPos to,int turns){
  var b=at(e,from);if(b==null)return "building";
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  var p=plan(l,e,b,to,turns);if(!p.ok())return p.reason().isEmpty()?"conflicts":p.reason();
  HallUpgradeGoal.yield(l,e.settlement().id());HallUpgradeGoal.enqueue(l,e,p.state());forget(e.settlement().id());SettlementData.get(l.getServer()).setDirty();return "";
 }

 // ---------- the builder's steps ----------
 public static int held(CompoundTag state,String key){int n=0;for(var raw:state.getList("cargo",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);if(key(s).equals(key))n+=s.getCount();}return n;}
 private static void consume(CompoundTag state,String key){var cargo=state.getList("cargo",Tag.TAG_COMPOUND);for(int i=0;i<cargo.size();i++){var s=ItemStack.of(cargo.getCompound(i));if(!s.isEmpty()&&key(s).equals(key)){s.shrink(1);cargo.set(i,s.save(new CompoundTag()));return;}}}
 private static void cargo(CompoundTag state,ItemStack stack){if(!stack.isEmpty())state.getList("cargo",Tag.TAG_COMPOUND).add(stack.save(new CompoundTag()));}
 private static void finish(CompoundTag state,CompoundTag op,boolean skipped){op.putBoolean("done",true);if(skipped)op.putBoolean("skipped",true);state.putInt("progress",state.getInt("progress")+1);}
 private static UUID blockId(CompoundTag state,CompoundTag op,int current){int a=op.getInt("attempt");return Settlement.childId(state.getUUID("id"),"block/"+current+(a>0?"/"+a:""));}
 /** Before the builder walks to an operation: a block change already committed is only booked (replay), an old cell somebody has taken or
  *  changed into another block is skipped (C3), a lot cell without its dirt stays as it is (C12). True when the operation is settled. */
 public static boolean settle(ServerLevel l,CompoundTag state,int current,Runnable save){
  unpackPending(l,state,save);
  var op=state.getList("ops",Tag.TAG_COMPOUND).getCompound(current);if(op.getBoolean("done"))return true;
  var id=blockId(state,op,current);
  if(WorldJournal.exists(l,id)){
   if(WorldJournal.recoverExisting(l,id)!=null){if(op.getBoolean("pack"))collect(l,state,op,current,save);book(l,state,op,save);return true;}
   if(!op.getBoolean("dismantle"))return false;op.putInt("attempt",op.getInt("attempt")+1);save.run();}
  if(op.getBoolean("dismantle")){var step=HallConstructionPlan.step(op);if(!l.hasChunkAt(step.pos()))return false;var now=l.getBlockState(step.pos());var before=step.before();
   boolean same=now.getBlock()==before.getBlock()&&(!(before.getBlock() instanceof BuildingCoreBlock)||now.getValue(BuildingCoreBlock.GRADE).equals(before.getValue(BuildingCoreBlock.GRADE)));
   if(!same){finish(state,op,true);save.run();return true;}
   if(!now.equals(before))op.put("before",NbtUtils.writeBlockState(now));}
  if(op.getInt("phase")==3&&op.contains("item")&&held(state,op.getString("item"))<1){finish(state,op,true);save.run();return true;}
  return false;
 }
 /** The builder's hand on one operation at its cell: packs a container, changes the block through the journal, books the item, unpacks.
  *  Empty when the operation is done; otherwise the work status to show (and nothing was booked). */
 public static String effect(ServerLevel l,CompoundTag state,int current,Runnable save){
  if(settle(l,state,current,save))return "";
  var ops=state.getList("ops",Tag.TAG_COMPOUND);var op=ops.getCompound(current);var step=HallConstructionPlan.step(op);
  if(op.contains("item")&&held(state,op.getString("item"))<1){
   // Top-up: the cargo lacks what this cell needs (a return went elsewhere) — the tail is priced again and the hall pays the difference.
   var have=new HashMap<String,Integer>();for(var raw:state.getList("cargo",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);if(!s.isEmpty())have.merge(key(s),s.getCount(),Integer::sum);}
   var cost=netCost(ops,0,have);if(!cost.contains(op.getString("item")))cost.putInt(op.getString("item"),held(state,op.getString("item"))+1);
   state.put("cost",cost);state.putBoolean("funded",false);save.run();return "missing_building_materials";}
  // C11: a smelting job holds this furnace by its position; the furnace waits for the job to end instead of leaving it pointing at air.
  if(op.getBoolean("pack")&&smelting(l,state,step.pos()))return "relocation_furnace_busy";
  if(op.getBoolean("pack"))pack(l,state,op,current,step.pos(),save);
  var id=blockId(state,op,current);
  boolean ok=op.getBoolean("dismantle")?WorldJournal.dismantle(l,id,step.pos(),step.before(),step.after()):WorldJournal.place(l,id,step.pos(),step.before(),step.after());
  if(!ok){if(op.getBoolean("dismantle")){op.putInt("attempt",op.getInt("attempt")+1);save.run();}return "changed_target";}
  book(l,state,op,save);return "";
 }
 /** C11: a workshop job that is not idle holds this exact position (its furnace, or where it installs one). */
 static boolean smelting(ServerLevel l,CompoundTag state,BlockPos pos){
  var id=BuildingOrders.buildingId(state);
  for(var e:SettlementData.get(l.getServer()).entries()){if(e.settlement().buildings().stream().noneMatch(b->b.id().equals(id)))continue;
   for(var w:e.settlement().buildings()){var job=Workshops.inspect(l,w.id());if(job.isEmpty()||job.getString("stage").equals("idle"))continue;
    for(var key:List.of("furnace","installAt"))if(job.contains(key,Tag.TAG_LONG)&&job.getLong(key)==pos.asLong())return true;}}
  return false;
 }
 private static void book(ServerLevel l,CompoundTag state,CompoundTag op,Runnable save){
  if(op.contains("item")&&held(state,op.getString("item"))>0)consume(state,op.getString("item"));
  if(op.contains("return"))cargo(state,new ItemStack(BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(op.getString("return")))));
  finish(state,op,false);save.run();unpackPending(l,state,save);
 }
 /** The packing half of an operation on its own (GameTests: a slot refilled between packing and the removal). */
 public static void pack(ServerLevel l,CompoundTag state,int current,Runnable save){var op=state.getList("ops",Tag.TAG_COMPOUND).getCompound(current);if(op.getBoolean("pack"))pack(l,state,op,current,BlockPos.of(op.getLong("pos")),save);}
 private static void pack(ServerLevel l,CompoundTag state,CompoundTag op,int current,BlockPos pos,Runnable save){
  collect(l,state,op,current,save);
  if(!(l.getBlockEntity(pos) instanceof Container c))return;
  for(int slot=0;slot<c.getContainerSize();slot++){var stack=c.getItem(slot);if(stack.isEmpty())continue;
   var pid=Settlement.childId(state.getUUID("id"),"pack/"+current+"/"+op.getInt("attempt")+"/"+slot);if(WorldJournal.exists(l,pid))continue;
   var got=WorldJournal.takeAmount(l,pid,pos,slot,stack.copy(),stack.getCount());if(got.isEmpty())continue;store(state,op,slot,pid,got);save.run();}
 }
 /** C4: every pack journal of this operation, of every attempt, is in custody once — found by its id, whatever the record forgot. */
 private static void collect(ServerLevel l,CompoundTag state,CompoundTag op,int current,Runnable save){
  var known=new HashSet<UUID>();for(var raw:state.getList("stored",Tag.TAG_COMPOUND))known.add(((CompoundTag)raw).getUUID("id"));
  for(int a=0;a<=op.getInt("attempt");a++)for(int slot=0;slot<54;slot++){var pid=Settlement.childId(state.getUUID("id"),"pack/"+current+"/"+a+"/"+slot);
   if(known.contains(pid)||!WorldJournal.exists(l,pid))continue;var got=WorldJournal.recoverAmount(l,pid);if(got.isEmpty())continue;store(state,op,slot,pid,got);save.run();}
 }
 private static void store(CompoundTag state,CompoundTag op,int slot,UUID pid,ItemStack got){
  var entry=new CompoundTag();entry.putUUID("id",pid);entry.putLong("cell",op.getLong("pos"));entry.putInt("slot",slot);entry.put("item",got.save(new CompoundTag()));
  // Furniture the new design has no place for: its contents go with the cargo to the hall.
  if(op.getBoolean("orphan")){cargo(state,got);entry.putBoolean("unpacked",true);entry.putBoolean("cargo",true);}
  state.getList("stored",Tag.TAG_COMPOUND).add(entry);
 }
 /** Stored contents whose container stands again go back into it, slot for slot; a slot that is taken sends its stack to the hall. */
 private static void unpackPending(ServerLevel l,CompoundTag state,Runnable save){
  var stored=state.getList("stored",Tag.TAG_COMPOUND);if(stored.isEmpty())return;var ops=state.getList("ops",Tag.TAG_COMPOUND);
  for(var raw:stored){var entry=(CompoundTag)raw;if(entry.getBoolean("unpacked"))continue;
   CompoundTag target=null;for(var o:ops){var t=(CompoundTag)o;if(t.contains("unpack")&&t.getLong("unpack")==entry.getLong("cell")){target=t;break;}}
   if(target==null||!target.getBoolean("done"))continue;var pos=BlockPos.of(target.getLong("pos"));if(!l.hasChunkAt(pos))continue;
   var item=ItemStack.of(entry.getCompound("item"));var uid=Settlement.childId(entry.getUUID("id"),"unpack");
   if(!WorldJournal.putSlot(l,uid,pos,entry.getInt("slot"),item)){cargo(state,item);entry.putBoolean("cargo",true);}
   entry.putBoolean("unpacked",true);save.run();}
 }
 /** C14: an old cell nobody could reach three times stays where it is — unless it is a container, whose contents must come along. */
 public static boolean giveUp(CompoundTag state,CompoundTag op){
  // relocate-fix: only a cell of the old building or lot is given up, never a scaffold of the crew's own columns (going up or coming down):
  // a skipped column removal left scaffolds standing on the old lot for good (home_2, smithy).
  // A cell the plan gives a scaffold stand is reachable by design: it is given more tries (six) than a cell nobody planned a stand for.
  if(!state.getBoolean("relocate")||op.getInt("deferred")<(op.contains("stand")?6:3)||op.getBoolean("pack")||!op.getBoolean("dismantle")||op.getInt("phase")!=1&&op.getInt("phase")!=3)return false;
  finish(state,op,true);return true;
 }
 /** C12: when phase 2 stands, the building is at its new place — before the old lot is levelled and before the leftovers are carried. */
 public static boolean complete(ServerLevel l,SettlementData.Entry e,CompoundTag state){
  var id=BuildingOrders.buildingId(state);var s=e.settlement();var b=s.buildings().stream().filter(x->x.id().equals(id)).findFirst().orElse(null);if(b==null)return false;
  var to=BlockPos.of(state.getLong("origin"));var offset=to.subtract(e.center());int turns=BuildingPlacement.turns(state.getInt("rotation"));
  boolean there=b.x()==offset.getX()&&b.y()==offset.getY()&&b.z()==offset.getZ()&&b.rotation()==turns;
  if(!there){
   var last=new LinkedHashMap<BlockPos,BlockState>();
   for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;int phase=t.getInt("phase");if(phase==1||phase==3||t.getBoolean("skipped"))continue;var step=HallConstructionPlan.step(t);last.put(step.pos(),step.after());}
   boolean whole=true;
   for(var cell:last.entrySet()){if(!l.hasChunkAt(cell.getKey()))return false;var now=l.getBlockState(cell.getKey());
    // A cell of the copy lost after it was built (a creeper, TNT, fire): its operations go again instead of the move standing still for good.
    if(!BuildingRepairs.present(now,cell.getValue())&&!(cell.getValue().isAir()&&now.canBeReplaced()&&now.getFluidState().isEmpty())){reopen(state,cell.getKey(),now);whole=false;}}
   if(!whole)return false;
   s.moveBuilding(id,offset.getX(),offset.getY(),offset.getZ(),turns);
  }
  if(s.homes().stream().anyMatch(h->h.id().equals(id)&&!h.usable()))s.restoreHome(id);
  forget(s.id());SettlementData.get(l.getServer()).setDirty();return true;
 }
 /** The last operation of a new-site cell (all the links of a core above the grade that stands) is undone, to be done again from what the
  *  cell holds now, under a new journal id; its item is paid again through the ordinary top-up. The record's pointer goes back to it. */
 static void reopen(CompoundTag state,BlockPos cell,BlockState now){
  var ops=state.getList("ops",Tag.TAG_COMPOUND);var again=new ArrayList<Integer>();int last=-1;var cores=new ArrayList<Integer>();
  for(int i=0;i<ops.size();i++){var t=ops.getCompound(i);int phase=t.getInt("phase");if(phase==1||phase==3||t.getBoolean("skipped")||!t.getBoolean("done")||t.getLong("pos")!=cell.asLong())continue;
   last=i;var after=HallConstructionPlan.step(t).after();
   if(after.getBlock() instanceof BuildingCoreBlock){int stands=now.is(after.getBlock())?now.getValue(BuildingCoreBlock.GRADE):1;if(after.getValue(BuildingCoreBlock.GRADE)>stands)cores.add(i);}}
  if(last<0)return;
  if(HallConstructionPlan.step(ops.getCompound(last)).after().getBlock() instanceof BuildingCoreBlock)again.addAll(cores);else again.add(last);
  if(again.isEmpty())return;
  for(int n=0;n<again.size();n++){var t=ops.getCompound(again.get(n));t.putBoolean("done",false);t.remove("retry");t.remove("deferred");t.remove("deferredAt");
   // What the operation gave back (a scaffold, the lot's dirt) is in the cargo already: done again, it gives nothing twice.
   if(t.contains("return")){t.putString("returned",t.getString("return"));t.remove("return");}t.putInt("attempt",t.getInt("attempt")+1);if(n==0)t.put("before",NbtUtils.writeBlockState(now));state.putInt("progress",Math.max(0,state.getInt("progress")-1));}
  state.putInt("index",Math.min(state.getInt("index"),again.get(0)));
 }
 /** After a crash: a record that says the building moved while the settlement still has it at the old place moves it (once). */
 public static boolean recover(ServerLevel l,SettlementData.Entry e,CompoundTag state){
  if(!state.getBoolean("moved"))return false;var id=BuildingOrders.buildingId(state);var b=e.settlement().buildings().stream().filter(x->x.id().equals(id)).findFirst().orElse(null);if(b==null)return false;
  var offset=BlockPos.of(state.getLong("origin")).subtract(e.center());int turns=BuildingPlacement.turns(state.getInt("rotation"));
  if(!e.settlement().moveBuilding(id,offset.getX(),offset.getY(),offset.getZ(),turns))return false;SettlementData.get(l.getServer()).setDirty();return true;
 }
 /** C12: leftovers the hall chest cannot take go to the moved building's own chest, and after a minute of a full hall into one cargo chest
  *  beside the hall. True when a stack left the cargo (the caller saves). */
 public static boolean overflow(ServerLevel l,SettlementData.Entry e,CompoundTag state,int slot){
  var cargo=state.getList("cargo",Tag.TAG_COMPOUND);var id=state.getUUID("id");var b=e.settlement().buildings().stream().filter(x->x.id().equals(BuildingOrders.buildingId(state))).findFirst().orElse(null);
  var own=b==null?null:LogisticsRoutes.chest(l,e,b);
  if(own!=null&&WorldJournal.deposit(l,Settlement.childId(id,"return/"+state.getInt("returns")),own.getBlockPos(),ItemStack.of(cargo.getCompound(slot)))){
   cargo.set(slot,ItemStack.EMPTY.save(new CompoundTag()));state.putInt("returns",state.getInt("returns")+1);return true;}
  long now=l.getGameTime();if(!state.contains("fullSince")){state.putLong("fullSince",now);return true;}
  if(now-state.getLong("fullSince")<1200)return false;
  // The cargo holds one stack per returned block: merged into full stacks first, so one chest takes as much as it can.
  var merged=new ArrayList<ItemStack>();
  for(var raw:cargo){var s=ItemStack.of((CompoundTag)raw);
   for(var m:merged){if(s.isEmpty())break;if(ItemStack.isSameItemSameTags(m,s)&&m.getCount()<m.getMaxStackSize()){int n=Math.min(s.getCount(),m.getMaxStackSize()-m.getCount());m.grow(n);s.shrink(n);}}
   while(!s.isEmpty()){merged.add(s.split(s.getMaxStackSize()));}}
  var contents=new ListTag();for(int i=0;i<merged.size()&&i<27;i++)contents.add(merged.get(i).save(new CompoundTag()));
  var drop=Settlement.childId(id,"leftover/"+state.getInt("leftovers"));
  // A chest already put down before a crash is only booked: the spot is not searched again (the first one is taken by that chest now,
  // and the same id with another place would block the journal). One that could not be put down gives its id up for a new one.
  if(WorldJournal.exists(l,drop)){
   if(WorldJournal.recoverExisting(l,drop)==null){state.putInt("leftovers",state.getInt("leftovers")+1);return true;}
  }else{
   var hall=HallSite.stock(e);BlockPos spot=null;
   for(int r=1;r<=4&&spot==null;r++)for(int dx=-r;dx<=r&&spot==null;dx++)for(int dz=-r;dz<=r&&spot==null;dz++){var p=hall.offset(dx,0,dz);if(l.getBlockState(p).isAir()&&l.getBlockEntity(p)==null&&l.getBlockState(p.below()).isFaceSturdy(l,p.below(),net.minecraft.core.Direction.UP))spot=p;}
   if(spot==null||!WorldJournal.dropCargo(l,drop,spot,e.settlement().id(),contents))return false;}
  var rest=new ListTag();for(int i=27;i<merged.size();i++)rest.add(merged.get(i).save(new CompoundTag()));state.put("cargo",rest);
  state.putInt("leftovers",state.getInt("leftovers")+1);state.remove("fullSince");return true;
 }

 // ---------- while a move is under way ----------
 private record Active(UUID project,UUID building,boolean moved,int x0,int y0,int z0,int x1,int y1,int z1){}
 private static final Map<UUID,Object[]> ACTIVE=new java.util.concurrent.ConcurrentHashMap<>();
 private static final Map<String,int[]> HEIGHTS=new java.util.concurrent.ConcurrentHashMap<>();
 public static void forget(UUID village){ACTIVE.remove(village);}
 /** The pending move of a village, looked at no more than once a second (C16). */
 private static Active active(ServerLevel l,SettlementData.Entry e){
  var id=e.settlement().id();long now=l.getGameTime();var seen=ACTIVE.get(id);if(seen!=null&&now-(long)seen[0]>=0&&now-(long)seen[0]<20)return (Active)seen[1];
  Active a=null;
  if(HallUpgradeGoal.pending(l,id)){var t=HallUpgradeGoal.inspect(l,id);
   if(t.getBoolean("relocate")){var o=BlockPos.of(t.getLong("origin"));var design=t.getString("design");int turns=t.getInt("rotation");var size=BuildingPlacement.size(design,turns);
    var h=HEIGHTS.computeIfAbsent(design,k->{var ys=BuildingBlueprints.layout(k,BlockPos.ZERO).entrySet().stream().filter(c->!c.getValue().isAir()).mapToInt(c->c.getKey().getY()).toArray();return new int[]{Arrays.stream(ys).min().orElse(0),Arrays.stream(ys).max().orElse(8)};});
    a=new Active(HallConstructionPlan.projectId(t),BuildingOrders.buildingId(t),t.getBoolean("moved"),o.getX()-3,o.getY()+h[0]-3,o.getZ()-3,o.getX()+size[0]+2,o.getY()+h[1]+3,o.getZ()+size[1]+2);}}
  ACTIVE.put(id,new Object[]{now,a});return a;
 }
 /** The building is being moved: its dwellers keep their home and nobody evicts them while it is apart. */
 public static boolean moving(ServerLevel l,SettlementData.Entry e,UUID building){var a=active(l,e);return a!=null&&a.building.equals(building)&&!a.moved;}
 /** The new lot of a pending move, with the usual three-block buffer, is the village's until the move is done. */
 public static boolean reserved(ServerLevel l,BlockPos p){
  for(var e:SettlementData.get(l.getServer()).entries()){if(!e.dimension().equals(l.dimension().location().toString()))continue;var a=active(l,e);
   if(a!=null&&p.getX()>=a.x0&&p.getX()<=a.x1&&p.getY()>=a.y0&&p.getY()<=a.y1&&p.getZ()>=a.z0&&p.getZ()<=a.z1)return true;}
  return false;
 }
 /** Atlas page: why a building cannot move (only the cheap reasons), and the move under way with its phase and progress. */
 public static CompoundTag view(ServerLevel l,SettlementData.Entry e){
  var out=new CompoundTag();var refusals=new CompoundTag();boolean besieged=Sieges.besieged(l.getServer(),e.settlement().id()),busy=busy(l,e);
  for(var b:e.settlement().buildings()){var r=quick(l,e,b,besieged,busy);if(!r.isEmpty())refusals.putString(b.id().toString(),r);}
  out.put("refusals",refusals);
  var id=e.settlement().id();
  if(HallUpgradeGoal.pending(l,id)){var t=HallUpgradeGoal.inspect(l,id);
   if(t.getBoolean("relocate")){var a=new CompoundTag();var ops=t.getList("ops",Tag.TAG_COMPOUND);int done=0,phase=3;boolean found=false;
    for(var raw:ops){var op=(CompoundTag)raw;if(op.getBoolean("done"))done++;else if(!found){phase=op.getInt("phase");found=true;}}
    var size=BuildingPlacement.size(t.getString("design"),t.getInt("rotation"));
    a.putUUID("building",BuildingOrders.buildingId(t));a.putString("design",t.getString("design"));a.putLong("origin",t.getLong("origin"));a.putInt("width",size[0]);a.putInt("depth",size[1]);
    a.putLong("from",t.getLong("from"));a.putInt("phase",phase);a.putInt("done",done);a.putInt("total",ops.size());a.putBoolean("moved",t.getBoolean("moved"));a.putBoolean("funded",t.getBoolean("funded"));
    out.put("active",a);}}
  return out;
 }
}
