package org.villageastra.server;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.villageastra.world.*;
/** Read-only, bounded views of loaded approved work; never surveys underground or changes the world. */
public final class ConstructionViews {
 public static final int RADIUS=64,MAX_CELLS=2048;
 private ConstructionViews(){}
 public static CompoundTag nearby(ServerLevel level,BlockPos viewer){
  var result=new CompoundTag();result.putString("dimension",level.dimension().location().toString());
  var entry=SettlementData.get(level.getServer()).entries().stream().filter(e->e.dimension().equals(level.dimension().location().toString())&&HallUpgradeGoal.pending(level,e.settlement().id())&&anchor(level,e).distSqr(viewer)<=RADIUS*RADIUS).min(Comparator.comparingDouble(e->anchor(level,e).distSqr(viewer))).orElse(null);
  if(entry==null)return result;var state=HallUpgradeGoal.inspect(level,entry.settlement().id());if(state.getBoolean("complete"))return result;
  return project(level,entry,viewer,state);
 }
 /** A field-ordered building is shown around its own site, a hall upgrade around the hall. */
 private static BlockPos anchor(ServerLevel level,SettlementData.Entry e){var state=HallUpgradeGoal.headerView(level,e.settlement().id());return BuildingOrders.isBuilding(state)?BlockPos.of(state.getLong("origin")):e.center();}
 public static CompoundTag project(ServerLevel level,SettlementData.Entry entry,BlockPos viewer,CompoundTag state){
  var result=new CompoundTag();result.putString("dimension",level.dimension().location().toString());
  var plan=HallConstructionPlan.read(state);var ops=state.getList("ops",Tag.TAG_COMPOUND);int index=state.getInt("index");
  var g=entry.settlement().governance();result.putUUID("village",entry.settlement().id());result.putString("name",entry.settlement().name());result.putLong("epoch",g.epoch());result.putLong("revision",g.revision());result.putBoolean("paused",g.paused(plan.id()));result.putUUID("id",plan.id());result.putInt("level",state.getInt("level"));result.putString("kind",state.getString("kind"));result.putString("design",state.getString("design"));result.putLong("base",entry.center().asLong());result.putInt("index",index);result.putInt("total",ops.size());result.putLong("unique",plan.uniquePositions());
  var cells=new ListTag();var required=new TreeMap<String,Integer>();int conflicts=0,hidden=0;
  // OWNER_REQUEST 9.10: an operation is checked against what its cell will hold when the crew gets to it — the world now, or what the
  // earlier operations of the queue leave there. A bush cleared first, a scaffold put up and later taken down are the plan, not a change.
  var expected=new HashMap<Long,net.minecraft.world.level.block.state.BlockState>();
  // AD-126: the builder's next operation (the pointer skips work done out of turn, HallUpgradeGoal) and the items of the next 32 steps.
  long target=Long.MIN_VALUE;var upcoming=new LinkedHashMap<String,Integer>();int ahead=0,doneAhead=0;
  for(int i=index;i<ops.size();i++){
   var op=ops.getCompound(i);var step=HallConstructionPlan.step(op);long key=step.pos().asLong();
   // A block the crew already put up out of turn is done: it is neither work left nor a change, only the state of its cell from now on.
   if(op.getBoolean("done")){expected.put(key,step.after());doneAhead++;continue;}
   if(!step.item().isEmpty())required.merge(step.item(),1,Integer::sum);
   if(target==Long.MIN_VALUE)target=key;if(ahead<32){ahead++;if(!step.item().isEmpty()&&(upcoming.containsKey(step.item())||upcoming.size()<6))upcoming.merge(step.item(),1,Integer::sum);}
   var held=expected.get(key);expected.put(key,step.after());
   if(cells.size()>=MAX_CELLS||!level.hasChunkAt(step.pos())||step.pos().distSqr(viewer)>RADIUS*RADIUS){hidden++;continue;}
   var cell=new CompoundTag();cell.putLong("pos",key);cell.put("before",NbtUtils.writeBlockState(step.before()));cell.put("after",NbtUtils.writeBlockState(step.after()));cell.putInt("op",i);
   // Natural drift of the same block (snowy grass, a stair's shape) is accepted by the crew (BuildingOrders.reconcile), so it is no conflict.
   var now=held!=null?held:level.getBlockState(step.pos());boolean conflict=!now.equals(step.before())&&now.getBlock()!=step.before().getBlock()&&!(now.canBeReplaced()&&step.before().canBeReplaced());cell.putBoolean("blocked",conflict);if(conflict)conflicts++;cells.add(cell);
  }
  // AD-153: what is built — the pointer plus the blocks set out of turn (handy work, a crew's helpers); the bars and the HUD show this.
  result.putInt("progress",index+doneAhead);
  if(target!=Long.MIN_VALUE)result.putLong("target",target);
  var next=new ListTag();for(var u:upcoming.entrySet()){var line=new CompoundTag();line.putString("item",u.getKey());line.putInt("count",u.getValue());next.add(line);}result.put("upcoming",next);
  result.put("cells",cells);result.putInt("hidden",hidden);result.putInt("conflicts",conflicts);
  var materials=new ListTag();var held=new HashMap<String,Integer>();for(var raw:state.getList("cargo",Tag.TAG_COMPOUND)){var stack=ItemStack.of((CompoundTag)raw);held.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);}
  Container stock=level.hasChunkAt(entry.center())&&level.getBlockEntity(org.villageastra.world.HallSite.stock(entry)) instanceof Container c?c:null;
  for(var cost:required.entrySet()){
   var line=new CompoundTag();line.putString("item",cost.getKey());line.putInt("required",cost.getValue());line.putInt("held",held.getOrDefault(cost.getKey(),0));int free=0;
   if(stock!=null)for(int slot=0;slot<stock.getContainerSize();slot++){var item=stock.getItem(slot);if(BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(cost.getKey()))free+=item.getCount();}
   line.putInt("stock",free);line.putInt("missing",Math.max(0,cost.getValue()-held.getOrDefault(cost.getKey(),0)-free));
   // AD-137: of that stock, what the hall keeps for this project (nobody else takes it); 0 while it is paused or funded.
   line.putInt("reserved",org.villageastra.world.HallReserve.held(level,entry,BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(cost.getKey()))));materials.add(line);
  }
  result.put("materials",materials);result.putBoolean("funded",state.getBoolean("funded"));
  // AD-137 (addendum): a project of an NPC mayor that has gained nothing for days is named in the office, not called off silently.
  long stalled=MayorPlanner.stalledDays(level,entry,state,level.getGameTime());if(stalled>0)result.putLong("stalledDays",stalled);String status=conflicts>0?"changed_target":hidden>0?"needs_survey":state.getBoolean("funded")?"construction_ready":materials.stream().anyMatch(raw->((CompoundTag)raw).getInt("missing")>0)?"missing_building_materials":"awaiting_builder";
  if(conflicts==0&&hidden==0&&state.hasUUID("worker")&&level.getEntity(state.getUUID("worker")) instanceof ResidentEntity worker)status=worker.workStatus().isEmpty()?status:worker.workStatus();
  result.putString("status",g.paused(plan.id())?"paused_by_mayor":status);
  explain(level,entry,state,result,conflicts);
  return result;
 }
 /** Kind of one operation of a construction: clearing, foundation, a scaffold put up or taken down, or a block of the building itself. */
 public static String kind(net.minecraft.nbt.CompoundTag op,int originY){
  var step=HallConstructionPlan.step(op);var scaffold=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get();
  if(step.after().is(scaffold))return "scaffold_up";
  if(step.before().is(scaffold))return "scaffold_down";
  if(step.after().isAir())return "clear";
  if(step.pos().getY()<originY)return "foundation";
  return "place";
 }
 /** AD-066 (A07-VIS-006): the stage of the work, its parts with their progress, the materials held, delivered and still missing, the crew,
  *  the size and height of the site, and a rough time to the end — so the card says what is going on and why. */
 public static void explain(ServerLevel level,SettlementData.Entry entry,CompoundTag state,CompoundTag result,int conflicts){
  var ops=state.getList("ops",Tag.TAG_COMPOUND);int index=state.getInt("index");int originY=state.contains("origin")?BlockPos.of(state.getLong("origin")).getY():entry.center().getY();
  var done=new TreeMap<String,Integer>();var total=new TreeMap<String,Integer>();
  for(int i=0;i<ops.size();i++){var k=kind(ops.getCompound(i),originY);total.merge(k,1,Integer::sum);if(i<index||ops.getCompound(i).getBoolean("done"))done.merge(k,1,Integer::sum);}
  var parts=new CompoundTag();for(var k:total.keySet()){var part=new CompoundTag();part.putInt("done",done.getOrDefault(k,0));part.putInt("total",total.get(k));parts.put(k,part);}
  result.put("parts",parts);
  var g=entry.settlement().governance();var projectId=HallConstructionPlan.projectId(state);
  String current=index<ops.size()?kind(ops.getCompound(index),originY):"";
  // Blocked means the block the builder is about to work on is not what the plan expects; later operations wait for earlier ones by design.
  boolean blocked=false;
  if(index<ops.size()){var step=HallConstructionPlan.step(ops.getCompound(index));blocked=level.hasChunkAt(step.pos())&&!level.getBlockState(step.pos()).equals(step.before())&&!level.getBlockState(step.pos()).equals(step.after());}
  String stage=state.getBoolean("draft")?"draft":state.getBoolean("complete")?"complete":g.paused(projectId)?"pause":blocked?"blocked"
   :!state.getBoolean("funded")&&index==0?"ready":current.equals("clear")?"clearing":current.equals("foundation")?"foundation":"work";
  result.putString("stage",stage);
  int held=0;for(var raw:state.getList("cargo",Tag.TAG_COMPOUND))held+=ItemStack.of((CompoundTag)raw).getCount();
  int delivered=0,missing=0;for(int i=0;i<index;i++)if(!HallConstructionPlan.step(ops.getCompound(i)).item().isEmpty())delivered++;
  for(var raw:result.getList("materials",Tag.TAG_COMPOUND))missing+=((CompoundTag)raw).getInt("missing");
  result.putInt("reserved",held);result.putInt("delivered",delivered);result.putInt("deficit",missing);
  int builders=0;var names=new ListTag();
  for(var r:entry.settlement().residents())if(r.alive()&&r.profession()==org.villageastra.domain.Profession.BUILDER){builders++;
   if(names.size()<4&&level.getEntity(r.id()) instanceof ResidentEntity npc)names.add(StringTag.valueOf(npc.getName().getString()));}
  result.putInt("builders",builders);result.put("crew",names);
  if(state.contains("design")&&!state.getString("design").isEmpty()){
   var design=BuildingBlueprints.designs().stream().filter(d->d.id().equals(state.getString("design"))).findFirst().orElse(null);
   if(design!=null){var size=org.villageastra.world.BuildingPlacement.size(design.id(),state.getInt("rotation"));result.putInt("width",size[0]);result.putInt("depth",size[1]);}}
  result.putInt("y",originY);result.putBoolean("repair",state.getBoolean("repair"));
  // A rough time: a builder spends about two seconds on one operation, walking and fetching included.
  result.putInt("minutes",Math.max(0,(ops.size()-index)*40/1200));
 }
}
