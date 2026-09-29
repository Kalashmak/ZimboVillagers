package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.server.SettlementData;
/** ISO-005/ISO-006: one estimate for a design at a spot — the same ConstructionPlan the builder executes, split into what it clears, places and borrows. */
public final class Plans {
 public static final int MAX_CONFLICTS=64,DRAFTS=3;
 /** Most future and demolished cells one estimate carries to the map. */
 public static final int MAX_VOLUME=6000;
 private Plans(){}
 private static String key(net.minecraft.world.item.Item item){return BuiltInRegistries.ITEM.getKey(item).toString();}
 /** Full estimate of a design at an origin: operations, unique cells, materials with the settlement stock already subtracted, earthworks and conflicts. */
 public static CompoundTag estimate(ServerLevel l,SettlementData.Entry e,String design,int variant,BlockPos origin){
  var t=new CompoundTag();t.putString("design",design);t.putInt("variant",variant);t.putLong("origin",origin.asLong());
  var blueprint=BuildingBlueprints.design(design);
  if(blueprint==null){t.putString("reason","design");return t;}
  var size=BuildingPlacement.size(design,variant);t.putInt("width",size[0]);t.putInt("depth",size[1]);
  t.putBoolean("offered",BuildingOrders.ORDERABLE.contains(design));
  var survey=BuildingOrders.survey(l,e,design,variant,origin);
  // AD-070: an estimate made under a siege is shown, but it is not orderable.
  boolean besieged=Sieges.besieged(l.getServer(),e.settlement().id());
  t.putString("reason",besieged?"besieged":survey.reason());t.putBoolean("ok",survey.ok()&&!besieged);
  var conflicts=new LongArrayTag(survey.conflicts().stream().limit(MAX_CONFLICTS).map(BlockPos::asLong).toList());
  t.put("conflicts",conflicts);t.putInt("conflictCount",survey.conflicts().size());
  if(survey.state().isEmpty())return t;
  var ops=survey.state().getList("ops",Tag.TAG_COMPOUND);var cells=new HashSet<Long>();
  int clear=0,place=0,temporary=0,returned=0;
  var scaffold=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get();
  // A07-VIS-001: the map draws the future volume in blue block by block and what is torn down in red, straight from the operations.
  var future=new LinkedHashMap<Long,Integer>();var demolish=new LinkedHashSet<Long>();var replace=new LinkedHashSet<Long>();
  int replaced=0;
  // ISO-003: a cell that already holds what the design wants is kept — no operation is made for it at all, and the map says so.
  int keep=0;
  for(var cell:BuildingWood.apply(BuildingPlacement.layout(design,origin,variant),survey.state().getString("wood")).entrySet()){
   if(cell.getValue().isAir()||!l.hasChunkAt(cell.getKey()))continue;
   if(l.getBlockState(cell.getKey()).getBlock()==cell.getValue().getBlock())keep++;
  }
  for(var raw:ops){var op=(CompoundTag)raw;var step=HallConstructionPlan.step(op);
   if(step.before().equals(step.after()))continue;
   cells.add(step.pos().asLong());
   if(step.after().is(scaffold))temporary++;
   else if(step.after().isAir()){clear++;if(op.contains("return"))returned++;
    if(!step.before().is(scaffold)&&!future.containsKey(step.pos().asLong()))demolish.add(step.pos().asLong());}
   else{place++;future.put(step.pos().asLong(),net.minecraft.world.level.block.Block.getId(step.after()));demolish.remove(step.pos().asLong());
    // ISO-003: putting a block where another one stands is a replacement, not a new block on empty ground.
    if(!step.before().isAir()&&!step.before().is(scaffold)&&replace.add(step.pos().asLong()))replaced++;}
  }
  var volume=new ArrayList<Integer>();
  for(var cell:future.entrySet()){if(volume.size()>=MAX_VOLUME*4)break;var at=BlockPos.of(cell.getKey());
   volume.add(at.getX()-origin.getX());volume.add(at.getY()-origin.getY());volume.add(at.getZ()-origin.getZ());volume.add(cell.getValue());}
  t.putIntArray("future",volume.stream().mapToInt(Integer::intValue).toArray());
  t.putLongArray("demolish",demolish.stream().limit(MAX_VOLUME).mapToLong(Long::longValue).toArray());
  t.putInt("operations",ops.size());t.putInt("cells",cells.size());
  t.putInt("clear",clear);t.putInt("place",place);t.putInt("temporary",temporary);t.putInt("returns",returned);
  t.putInt("keep",keep);t.putInt("replace",replaced);
  t.putLongArray("replaceCells",replace.stream().limit(MAX_VOLUME).mapToLong(Long::longValue).toArray());
  // ISO-005: free is what the settlement really holds now, missing is what somebody still has to bring.
  var cost=survey.state().getCompound("cost");var materials=new ListTag();int items=0,missing=0;
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);
  for(var name:cost.getAllKeys().stream().sorted().toList()){
   int need=cost.getInt(name);items+=need;
   var item=BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(name));
   // AD-137: what the active project still has to withdraw is not free for another one.
   int free=chest==null?0:Math.min(need,HallReserve.count(l,e,hall,chest,s->s.is(item)));
   var row=new CompoundTag();row.putString("item",name);row.putInt("need",need);row.putInt("free",free);row.putInt("missing",need-free);
   materials.add(row);missing+=need-free;
  }
  t.put("materials",materials);t.putInt("items",items);t.putInt("shortage",missing);
  // ISO-004: earthworks are a separate, engineer-only part of the same plan.
  var terrace=Terraces.plan(l,e,design,origin,variant);
  t.putBoolean("engineer",Terraces.engineer(e)!=null);
  if(terrace!=null){t.putInt("cut",terrace.getInt("cut"));t.putInt("fill",terrace.getInt("fill"));
   t.putInt("earthMissing",Terraces.missing(l,e,terrace));t.putInt("earthOperations",terrace.getList("ops",Tag.TAG_COMPOUND).size());}
  return t;
 }
 /** A draft is a remembered position only: it holds no ground and reserves no item (ISO-003). */
 public static CompoundTag draft(ServerLevel l,SettlementData.Entry e,String design,int variant,BlockPos origin){
  var t=estimate(l,e,design,variant,origin);t.putBoolean("draft",true);return t;
 }
 /** Materials the settlement is still short of, as a readable list for the office. */
 public static Map<String,Integer> shortages(CompoundTag estimate){
  var result=new LinkedHashMap<String,Integer>();
  for(var raw:estimate.getList("materials",Tag.TAG_COMPOUND)){var row=(CompoundTag)raw;if(row.getInt("missing")>0)result.put(row.getString("item"),row.getInt("missing"));}
  return result;
 }
 /** Ghost cells of the design at the origin: the outline the map draws, taken from the blueprint itself. */
 public static long[] footprint(String design,BlockPos origin){
  var blueprint=BuildingBlueprints.design(design);if(blueprint==null)return new long[0];
  var cells=new ArrayList<Long>();
  for(int dx=0;dx<blueprint.width();dx++)for(int dz=0;dz<blueprint.depth();dz++)
   if(dx==0||dz==0||dx==blueprint.width()-1||dz==blueprint.depth()-1)cells.add(origin.offset(dx,0,dz).asLong());
  var result=new long[cells.size()];for(int i=0;i<result.length;i++)result[i]=cells.get(i);return result;
 }
 static ItemStack stack(String name,int count){return new ItemStack(BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(name)),count);}
 static boolean air(ServerLevel l,BlockPos p){return l.getBlockState(p).is(Blocks.AIR);}
}
