package org.villageastra.world;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import java.nio.file.*;
import java.util.*;

/** Takes a consistent cargo snapshot, including the last journal operation ahead of the job checkpoint. */
public final class JobCargo {
 private JobCargo(){}
 public record Snapshot(ListTag jobs,ListTag items){}
 static Path root(ServerLevel level){return level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);}
 private static void add(ListTag items,ItemStack stack){if(!stack.isEmpty())items.add(stack.copy().save(new CompoundTag()));}
 private static ItemStack taken(CompoundTag receipt){if(receipt==null)return ItemStack.EMPTY;var before=ItemStack.of(receipt.getCompound("before"));return before.copyWithCount(before.getCount()-ItemStack.of(receipt.getCompound("after")).getCount());}
 private static void job(ListTag jobs,String kind,UUID id,CompoundTag reset){var j=new CompoundTag();j.putString("kind",kind);j.putUUID("id",id);j.put("reset",reset);jobs.add(j);}
 public static Snapshot snapshot(ResidentEntity worker,boolean death){
  var level=(ServerLevel)worker.level();var jobs=new ListTag();var items=new ListTag();
  var entry=SettlementData.get(worker.getServer()).entry(worker.settlementId());if(entry==null)return new Snapshot(jobs,items);
  var jobLevel=worker.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(entry.dimension())));
  if(jobLevel==null)throw new IllegalStateException("Cargo source dimension is unavailable");
  var resident=entry.settlement().resident(worker.getUUID());var assigned=entry.settlement().workplace(worker.getUUID());
  var natural=NaturalSupplyGoal.inspect(jobLevel,worker.getUUID());if(NaturalSupplyGoal.active(natural)&&(death||!NaturalSupplyGoal.eligible(resident))){for(var raw:NaturalSupplyGoal.cargo(jobLevel,natural))items.add(raw.copy());job(jobs,"natural",worker.getUUID(),new CompoundTag());}
  var parcel=PorterWork.inspect(jobLevel,worker.getUUID());
  if(PorterWork.active(parcel)&&(death||parcel.getBoolean("returnOverflow")||(parcel.getBoolean("selfSupply")?!WorkerSupplies.eligible(resident,assigned):resident==null||!resident.alive()||resident.profession()!=Profession.PORTER)||assigned==null||!assigned.id().equals(parcel.getUUID("assignment")))){add(items,PorterWork.cargo(jobLevel,parcel));job(jobs,"porter",worker.getUUID(),new CompoundTag());}
  // AD-147 (CF-G): a warehouse courier's trip with a cart - all it has taken and not delivered, in the same one drop.
  if(WarehouseTrips.leaves(jobLevel,worker.getUUID(),resident,assigned,death)){for(var s:WarehouseTrips.custody(jobLevel,WarehouseTrips.inspect(jobLevel,worker.getUUID())))add(items,s);job(jobs,"haul",worker.getUUID(),new CompoundTag());}
  for(var building:entry.settlement().buildings()){
   var smelt=Workshops.inspect(jobLevel,building.id());if(smelt.getBoolean("physicalSmelt")&&!smelt.getString("stage").equals("idle")&&smelt.hasUUID("worker")&&smelt.getUUID("worker").equals(worker.getUUID())&&(death||!Workshops.eligible(resident,building)||(assigned!=null?!assigned.id().equals(building.id()):!building.type().equals("town_hall"))))job(jobs,"smelting",building.id(),NaturalFurnace.custody(jobLevel,smelt,items));
   var file=root(level).resolve("data/astra-work/"+building.id()+".bin");if(!Files.exists(file))continue;var state=NbtRecord.read(file);
   if(!state.hasUUID("worker")||!state.getUUID("worker").equals(worker.getUUID()))continue;
   boolean valid=assigned!=null&&assigned.id().equals(building.id())&&resident!=null&&resident.alive()&&resident.profession()==(building.type().equals("mine")?Profession.MINER:building.type().equals("farm")?Profession.FARMER:Profession.FORESTER);
   if(!death&&valid)continue;
   job(jobs,"resource",building.id(),resource(jobLevel,state,building.type().equals("mine"),building.type().equals("farm"),items));
  }
  var hall=root(level).resolve("data/astra-upgrades/"+entry.settlement().id()+".bin");
  if(Files.exists(hall)){
   var state=NbtRecord.read(hall);
   if(!state.getBoolean("complete")&&state.hasUUID("worker")&&state.getUUID("worker").equals(worker.getUUID())&&(death||resident==null||!resident.alive()||resident.profession()!=Profession.BUILDER))
    job(jobs,"hall",entry.settlement().id(),hall(jobLevel,state,items));
  }
  if(worker.blockWork()!=null&&!WorkClaim.retired(root(level).resolve("data/astra-journal"),worker.blockWork().id())&&(death||resident==null||resident.profession()!=Profession.BUILDER)){
   var state=worker.blockWork().save();var id=state.getUUID("id");WorkClaim.acquire(root(level).resolve("data/astra-journal"),id,worker.getUUID());
   var blockLevel=worker.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(state.getString("dimension"))));
   if(blockLevel==null)throw new IllegalStateException("Block work dimension unavailable");
   var fetch=WorldJournal.recoverExisting(blockLevel,Settlement.childId(id,"fetch"));
   var placed=WorldJournal.recoverExisting(blockLevel,Settlement.childId(id,"place"));
   if(placed==null)add(items,fetch!=null?taken(fetch):ItemStack.of(state.getCompound("carried")));
   job(jobs,"block",id,new CompoundTag());
  }
  return new Snapshot(jobs,items);
 }
 private static CompoundTag resource(ServerLevel level,CompoundTag original,boolean miner,boolean farmer,ListTag held){
  var s=original.copy();var id=s.getUUID("operation");String stage=s.getString("stage");
  if(miner){MineOreWork.reconcile(level,s);s.remove("mineOre");MineClearance.reconcile(level,s);s.remove("mineClearance");}
  if(!miner&&!farmer&&NurserySoil.active(s)){s.put("cargo",NurserySoil.cargo(level,s));s.remove("soilHeld");s.remove("soilBefore");s.remove("soilLabor");stage="deliver";s.putInt("delivered",0);}
  if(!s.contains("width"))s.putInt("width",1);if(!s.contains("height"))s.putInt("height",3);
  var tool=ItemStack.of(s.getCompound("tool"));if(stage.equals("upgrade_tool")&&WorldJournal.recoverExisting(level,Settlement.childId(id,"return_unfit"))!=null)tool=ItemStack.EMPTY;
  if(stage.equals("tool")){var r=WorldJournal.recoverExisting(level,id);if(r!=null)tool=taken(r);}
  // Owner 2026-09-19: a forester felling a whole tree harvests each of its logs under its own entry; those that really came down are
  // his cargo, and each wore the axe one point.
  // AD-131: and the crown's leaves under leaf/j, all in one batch; the tree joins the load he already carries on a trip.
  if(stage.equals("dig")&&s.contains("tree")){
   var loot=new ArrayList<ItemStack>();int felled=0;var logs=s.getLongArray("tree");
   for(int i=0;i<logs.length;i++){var r=WorldJournal.recoverExisting(level,Settlement.childId(id,"log/"+i));if(r==null)continue;felled++;for(Tag raw:r.getList("loot",Tag.TAG_COMPOUND))loot.add(ItemStack.of((CompoundTag)raw));}
   var leaves=s.getLongArray("treeLeaves");
   if(felled>0)for(int j=0;j<leaves.length;j++){var r=WorldJournal.recoverExisting(level,Settlement.childId(id,"leaf/"+j));if(r==null)continue;for(Tag raw:r.getList("loot",Tag.TAG_COMPOUND))loot.add(ItemStack.of((CompoundTag)raw));}
   if(felled>0){s.put("cargo",ResourceWorkGoal.carry(s.getList("cargo",Tag.TAG_COMPOUND),loot,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),-1));s.putInt("delivered",0);stage="deliver";tool.setDamageValue(tool.getDamageValue()+felled);if(tool.getDamageValue()>=tool.getMaxDamage())tool=ItemStack.EMPTY;}
  }
  // AD-131: a forester's saplings set on a foot are paid from his load; one taken for a bare foot is in it; whatever he carries goes home.
  if(!miner&&!farmer&&stage.equals("replant")){var kind=BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(s.getString("species")));int cells=s.getLongArray("plantCells").length,set=0;
   for(int k=0;k<cells;k++)if(WorldJournal.recoverExisting(level,Settlement.childId(id,k==0?"replant":"replant/"+k))!=null)set++;
   if(set>0)s.put("cargo",ResourceWorkGoal.without(s.getList("cargo",Tag.TAG_COMPOUND),kind,set));}
  if(!miner&&!farmer&&stage.equals("sapling")){var r=WorldJournal.recoverExisting(level,Settlement.childId(id,"sapling"));if(r!=null)s.put("cargo",ResourceWorkGoal.carry(s.getList("cargo",Tag.TAG_COMPOUND),List.of(taken(r)),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),-1));}
  if(!miner&&!farmer&&Set.of("choose","dig","replant","sapling").contains(stage)&&!s.getList("cargo",Tag.TAG_COMPOUND).isEmpty()){stage="deliver";s.putInt("delivered",0);}
  else if(stage.equals("dig")){
   var r=WorldJournal.recoverExisting(level,id);
   // AD-112: a dug stair cell extends the stair's claim; a dug gallery cell is claimed as its gallery on release.
   if(r!=null){if(miner&&MineWork.gallery(s)){var g=MineWork.gallery(s,true);if(g!=null)s.putIntArray("galleryClaim",new int[]{g.step(),g.side(),g.length()});}
    else if(miner)s.putInt("extentStep",Math.max(s.getInt("step"),s.contains("extentStep")?s.getInt("extentStep"):0));
    // AD-104 P2: a farmer's loot joins the batch he carries, with the seed allowance his goal fixed before reaping, and counts as reaped; reaping wears no hoe.
    if(farmer){var loot=new ArrayList<ItemStack>();for(Tag raw:r.getList("loot",Tag.TAG_COMPOUND))loot.add(ItemStack.of((CompoundTag)raw));s.put("cargo",ResourceWorkGoal.carry(s.getList("cargo",Tag.TAG_COMPOUND),loot,NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),r.getCompound("before")),s.contains("seedAllowance")?s.getInt("seedAllowance"):-1));s.putInt("reaped",s.getInt("reaped")+1);}
    // AD-122: a miner's block joins the batch he carries; its cell is stepped on at the delivery below ("advanced" is not set).
    else if(miner){var loot=new ArrayList<ItemStack>();for(Tag raw:r.getList("loot",Tag.TAG_COMPOUND))loot.add(ItemStack.of((CompoundTag)raw));s.put("cargo",ResourceWorkGoal.carry(s.getList("cargo",Tag.TAG_COMPOUND),loot,NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),r.getCompound("before")),-1));s.remove("advanced");}
    else s.put("cargo",r.getList("loot",Tag.TAG_COMPOUND).copy());
    s.putInt("delivered",0);stage="deliver";if(!farmer){tool.setDamageValue(tool.getDamageValue()+1);if(tool.getDamageValue()>=tool.getMaxDamage())tool=ItemStack.EMPTY;}}
  }
  if(stage.equals("till")&&WorldJournal.recoverExisting(level,id)!=null){tool.setDamageValue(tool.getDamageValue()+1);if(tool.getDamageValue()>=tool.getMaxDamage())tool=ItemStack.EMPTY;}
  // AD-104 P2: a sowing from the batch that took place has spent its seed. Whatever a farmer carries between reapings is booked whole:
  // no delivery receipt exists under an operation before 'deliver'.
  if(farmer&&stage.equals("replant")&&WorldJournal.recoverExisting(level,Settlement.childId(id,"replant"))!=null)s.put("cargo",ResourceWorkGoal.without(s.getList("cargo",Tag.TAG_COMPOUND),FarmCrops.pending(s).seed,1));
  if(farmer&&Set.of("choose","dig","replant").contains(stage)){stage="deliver";s.putInt("delivered",0);}
  // AD-122: lights — a take at the hall the record has not booked yet, a light the journal hung that the record has not paid for; what he
  // holds is his cargo, the lights not hung stay due for his successor.
  if(miner){
   int lights=s.getInt("lightsHeld");String kind=s.getString("lightItem");
   if(Set.of("tool","support_fetch").contains(stage)&&!id.toString().equals(s.getString("lightsOp"))){var r=WorldJournal.recoverExisting(level,Settlement.childId(id,"lights"));if(r!=null){var got=taken(r);if(!got.isEmpty()){lights+=got.getCount();kind=BuiltInRegistries.ITEM.getKey(got.getItem()).toString();}}}
   if(stage.equals("light")&&WorldJournal.recoverExisting(level,Settlement.childId(id,"light"))!=null){var due=s.getIntArray("lightsDue");if(due.length>=8){lights--;s.putIntArray("lightsDue",java.util.Arrays.copyOfRange(due,8,due.length));}}
   if(lights>0&&!kind.isEmpty())add(held,new ItemStack(BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(kind)),lights));
   if(stage.equals("light")){stage="choose";}
  }
  // AD-122: stairs under way — a stone taken from the chest that the record does not hold yet is carried; a stair the journal set that the
  // record has not paid for is paid from the batch; the stairs not set yet stay due ("stairStep") for his successor.
  if(miner&&stage.equals("stair")){
   MineStairWork.reconcile(level,s);
   if(s.getInt("stairPlaced")>=MineDrive.stairs(s.getInt("stairStep"),MineWork.shape(s)).size())s.remove("stairStep");
   for(var key:List.of("stairPlaced","stairItem","stairTaken","stairTakeRound"))s.remove(key);
   stage="deliver";s.putInt("delivered",0);s.putBoolean("advanced",true);
  }
  // AD-122: a miner's batch between cells is booked whole — its cells stepped the drive on as they were dug; the cell chosen but not dug keeps no beam.
  if(miner&&Set.of("choose","dig").contains(stage)&&!s.getList("cargo",Tag.TAG_COMPOUND).isEmpty()){stage="deliver";s.putInt("delivered",0);s.putBoolean("advanced",true);s.remove("beam");}
  if(miner&&MineSealing.active(s)){MineSealing.reconcile(level,s);MineSealing.clear(s);stage="deliver";s.putInt("delivered",0);s.putBoolean("advanced",true);s.remove("beam");}
  add(held,tool);
  if(stage.equals("deliver")){
   var cargo=s.getList("cargo",Tag.TAG_COMPOUND);int delivered=s.getInt("delivered");
   for(int i=delivered;i<cargo.size();i++)if(WorldJournal.recoverExisting(level,Settlement.childId(id,"delivery/"+i))==null)add(held,ItemStack.of(cargo.getCompound(i)));
   // AD-112: the drive steps on exactly as the miner would have, under the floor its cell was chosen with (MineDrive).
   if(miner&&!s.getBoolean("advanced"))MineWork.step(s);
  }
  if(stage.equals("sapling")&&farmer){var r=WorldJournal.recoverExisting(level,id);if(r!=null)add(held,taken(r));}
  if(farmer&&stage.equals("plant")&&WorldJournal.recoverExisting(level,s.hasUUID("plantPlacement")?s.getUUID("plantPlacement"):Settlement.childId(id,"plant"))==null&&WorldJournal.recoverExisting(level,Settlement.childId(id,"plant_return"))==null)add(held,new ItemStack(FarmCrops.pending(s).seed));
  // AD-112: the beam is the one MineDrive gave the chosen cell — the stair's width of logs, or a gallery's one — under its own ids.
  int placed=s.getInt("support_placed"),fetched=s.getInt("support_fetched"),width=miner?MineWork.beam(s).count():s.getInt("width");
  if(stage.equals("support_fetch")||stage.equals("support_place"))for(var raw:MineTimber.carried(level,s))add(held,ItemStack.of((CompoundTag)raw));
  if(stage.equals("support_place")){
   var r=placed<width?WorldJournal.recoverExisting(level,MineWork.beamId(s,id,placed)):null;if(r!=null){placed++;if(!MineWork.gallery(s))s.putInt("extentStep",Math.max(s.getInt("step")-1,s.contains("extentStep")?s.getInt("extentStep"):0));}
  }
  boolean support=miner&&(stage.startsWith("support_")||s.getBoolean("resumeSupport")||(stage.equals("deliver")&&MineWork.needsBeam(s)));
  if(stage.equals("support_place")&&placed>=width)support=false;
  s.putString("stage","tool");s.putUUID("operation",UUID.randomUUID());s.putBoolean("resumeSupport",support);
  for(String key:List.of("worker","tool","cargo","delivered","advanced","lightsHeld","lightItem","lightsOp","labor","target","before","status","plantSource","plantPlacement","sapling","crop","seekSeedHarvest","seedAllowance","tree","treeBefore","support_fetched","support_placed","supportTimber","treeLeaves","base","trees","lastFoot","forestDeliveryAt","plantCells","species","fromBare","felledKind"))s.remove(key);
  if(support)s.putInt("support_placed",placed);else s.remove("beam");
  return s;
 }
 private static CompoundTag hall(ServerLevel level,CompoundTag original,ListTag held){
  var s=original.copy();var id=s.getUUID("id");var cargo=s.getList("cargo",Tag.TAG_COMPOUND);int index=s.getInt("index");var ops=s.getList("ops",Tag.TAG_COMPOUND);
  // A field-ordered building places blocks out of turn: a done operation is behind the pointer and is not paid for again.
  while(index<ops.size()&&ops.getCompound(index).getBoolean("done"))index++;
  if(!s.getBoolean("funded")){
   var r=WorldJournal.recoverExisting(level,Settlement.childId(id,"fund/"+s.getInt("withdrawals")));if(r!=null)add(cargo,taken(r));
  }else if(index<ops.size()&&WorldJournal.recoverExisting(level,Settlement.childId(id,"block/"+index))!=null){
   String key=ops.getCompound(index).getString("item");
   for(int i=0;!key.isEmpty()&&i<cargo.size();i++){var stack=ItemStack.of(cargo.getCompound(i));if(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(key)&&!stack.isEmpty()){stack.shrink(1);cargo.set(i,stack.save(new CompoundTag()));break;}}
   index++;
  }
  for(Tag raw:cargo)add(held,ItemStack.of((CompoundTag)raw));
  var cost=new CompoundTag();for(int i=index;i<ops.size();i++){var op=ops.getCompound(i);String key=op.getString("item");if(!key.isEmpty()&&!op.getBoolean("done"))cost.putInt(key,cost.getInt(key)+1);}
  s.putUUID("project",HallConstructionPlan.projectId(original));s.putUUID("id",UUID.randomUUID());s.putInt("index",index);s.put("cost",cost);s.put("cargo",new ListTag());s.putBoolean("funded",false);s.putInt("withdrawals",0);s.remove("worker");return s;
 }
 public static void release(ServerLevel level,UUID worker,ListTag jobs){
  for(Tag raw:jobs){var job=(CompoundTag)raw;String kind=job.getString("kind");var id=job.getUUID("id");
   if(kind.equals("resource")){
    var reset=job.getCompound("reset").copy();var claim=reset.getIntArray("galleryClaim");reset.remove("galleryClaim");NbtRecord.write(root(level).resolve("data/astra-work/"+id+".bin"),reset);
    if(reset.contains("extentStep")){var data=SettlementData.get(level.getServer());for(var entry:data.entries())if(entry.settlement().buildings().stream().anyMatch(b->b.id().equals(id)&&b.type().equals("mine")))if(entry.settlement().noteMine(id,reset.getInt("extentStep"),reset.getInt("width"),reset.getInt("height")))data.setDirty();}
    // AD-112: a gallery cell dug just before the release is claimed like the stair (its mine's stair is claimed by then).
    if(claim.length==3){var g=claim;var data=SettlementData.get(level.getServer());for(var entry:data.entries())if(entry.settlement().mineAreas().containsKey(id)&&entry.settlement().noteMine(id,new MineArea.Gallery(g[0],g[1],g[2])))data.setDirty();}
   }
   else if(kind.equals("hall")){
    // The slot is written back only while it still holds the same unfinished project: one called off, replaced (AD-078) or finished meanwhile stays as it is.
    var file=root(level).resolve("data/astra-upgrades/"+id+".bin");var reset=job.getCompound("reset");
    if(Files.exists(file)){var now=NbtRecord.read(file);if(!now.getBoolean("complete")&&HallConstructionPlan.projectId(now).equals(HallConstructionPlan.projectId(reset)))NbtRecord.write(file,reset);}
   }
   else if(kind.equals("block"))WorkClaim.retire(root(level).resolve("data/astra-journal"),id,worker);
   else if(kind.equals("porter"))PorterWork.release(level,id);
   else if(kind.equals("haul"))WarehouseTrips.release(level,id);
   else if(kind.equals("natural"))NaturalSupplyGoal.release(level,id);
   else if(kind.equals("smelting"))NaturalFurnace.release(level,id,job.getCompound("reset"));
   else throw new IllegalStateException("Unknown cargo source");
  }
 }
}
