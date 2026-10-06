package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
/** AD-076 (A07-AUTO-001, OWNER_REQUEST §6): what the upper levels of a building actually do.
 *  <ul><li><b>IV — mechanized</b>: the building's own machinery turns its bench, so a workshop keeps working with nobody at it (as a drive does, AD-055).</li>
 *  <li><b>V — automatic</b>: it runs its own cycle too — the farm sows and reaps its field (AD-131: the forester's hut has its own saw and grove, ForestryMachines),
 *      the quarry works out its chunk and the mine drives its adit on, each into its own chest, out of real blocks and real stock.</li>
 *  <li><b>VI — complex</b>: it also moves goods, taking one delivery of the village's own logistics per turn without a porter.</li></ul>
 *  Nothing here creates an item out of nothing: every block is taken from the world through the journal and every input comes out of a real chest.
 *  A worker at the bench always takes precedence, and a besieged settlement's machines build nothing (AD-070). */
public final class Machines {
 private Machines(){}
 /** How often a level takes its turn: a pair of hands still works faster than any of them. */
 public static int period(int level){return level>=6?30:level>=5?40:60;}
 /** AD-122: the adit and the quarry of level V dig on their own clock, one block each time an iron-pick player would have broken it
  *  (8 ticks for stone, 15 for deepslate): no machine digs slower than a player with a pick. Not saved: after a restart the next block is due. */
 private static final Map<UUID,Long> NEXT_DIG=new HashMap<>();
 private static final ItemStack PICK=new ItemStack(Items.IRON_PICKAXE);
 private static int digTicks;
 /** Whether this building works without hands now: the top of its own ladder (AD-136, balance/automation.json), or the mill drive of AD-055. */
 public static boolean mechanized(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  // AD-136 (D9): no shared mechanics branch any more; a drive the player built out of gears and a wheel is theirs already.
  if(Automation.at(l,e,b).any())return true;
  return Drive.driven(l,e,b)&&Drive.wheel(l,e);
 }
 /** One turn of every machine of a settlement; returns how many did real work. */
 public static int tick(ServerLevel l,SettlementData.Entry e,long now,List<Workshops.Want> wants){
  return tick(l,e,now,()->wants);
 }
 public static int tick(ServerLevel l,SettlementData.Entry e,long now,java.util.function.Supplier<List<Workshops.Want>> wants){
  int worked=0;
  for(var b:List.copyOf(e.settlement().buildings())){
   int level=BuildingLevels.level(l,e,b);
   var grant=Automation.at(l,e,b);boolean drive=Drive.driven(l,e,b)&&Drive.wheel(l,e);
   if(!grant.any()&&!drive)continue;
   boolean turn=now%period(level)==0,digger=b.type().equals("mine")||b.type().equals(Quarry.BUILDING);
   boolean dig=digger&&grant.dig()&&now>=NEXT_DIG.getOrDefault(b.id(),Long.MIN_VALUE);
   if(!turn&&!dig)continue;
   // AD-147: the warehouse of VI sorts its store with its couriers at work in it (a move inside one container crosses no one's load).
   if(grant.sort()){if(WarehouseSort.step(l,e,b,now)>0)worked++;continue;}
   // AD-138 VI: the yard's machine works beside its keeper (he shears and brings stock in; it fills, bins and sends the wolves).
   if(busy(l,e,b)&&!b.type().equals("livestock"))continue;
   if(dig){digTicks=0;boolean dug=cycle(l,e,b);NEXT_DIG.put(b.id(),now+(dug&&digTicks>0?digTicks:period(level)));if(dug)worked++;}
   if(!turn)continue;
   var job=Workshops.inspect(l,b.id());
   // AD-136: a bench turns by its own ladder's machinery (bench) or by the wheel's drive.
   if(Workshops.spec(b.type())!=null&&(grant.bench()||drive)&&!(job.getBoolean("physicalSmelt")&&job.hasUUID("worker")&&!job.getString("stage").equals("idle"))){
    var result=Workshops.advance(l,e,b,now,wants.get());
    if(result.equals("workshop_working")||result.equals("workshop_funding")||result.equals("workshop_complete"))worked++;
   }
   // AD-130: the farm of a layout-6 village runs its field at VI (every floor), an older village's at V as before.
   if(!digger&&grant.cycle()&&cycle(l,e,b))worked++;
   if(grant.haul()&&haul(l,e,b))worked++;
  }
  // AD-077, AD-123, AD-136: with Roads VI the machines keep the village surfaces themselves, out of the hall stock; trail cells are the trail crew's.
  if(now%40==0&&ResearchGate.has(l,e,ROAD_REPAIR)&&roads(l,e))worked++;
  return worked;
 }
 /** One step of the village's own road repair, done by the machines instead of a builder. */
 private static boolean roads(ServerLevel l,SettlementData.Entry e){
  var hall=Workshops.hall(e);if(hall==null||BuildingLevels.level(l,e,hall)<5)return false;
  var project=Roads.project(l,e.settlement().id());
  // A finished project's file stays: a new round is planned whenever none is active, not only before the first project.
  if(!Roads.active(l,e.settlement().id())){var repair=Roads.maintenance(l,e);if(repair==null||!repair.getString("kind").equals("repair"))return false;
   if(!Roads.order(l,e,repair))return false;project=Roads.project(l,e.settlement().id());}
  if(project==null||project.getBoolean("complete")||!project.getString("kind").equals("repair"))return false;
  var result=Roads.apply(l,e,project);
  if(result.equals("missing")||result.equals("unload")){int moved=Roads.load(l,e,hall,project);
   // As with the builder (RoadWorkGoal): a round the hall cannot supply never holds the road slot against the player's orders —
   // with nothing carried it is dropped, and a repair whose surface the hall lacks is left to the next round.
   if(result.equals("missing")&&moved==0){var op=Roads.current(project);var cargo=project.getList("cargo",net.minecraft.nbt.Tag.TAG_COMPOUND);
    if(cargo.isEmpty()){project.putBoolean("complete",true);Roads.save(l,e.settlement().id(),project);}
    else if(op!=null&&cargo.stream().noneMatch(raw->ItemStack.of((CompoundTag)raw).is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(op.getString("item")))))){
     project.putInt("index",project.getInt("index")+1);Roads.save(l,e.settlement().id(),project);}}}
  return result.equals("done")||result.equals("complete");
 }
 /** A worker of this building standing at its bench: the machine never works the same module at the same time. */
 private static boolean busy(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var at=Workshops.spec(b.type())!=null?Workshops.station(e,b):BuildingPlacement.origin(e,b);
  for(var npc:l.getEntitiesOfClass(ResidentEntity.class,new net.minecraft.world.phys.AABB(at).inflate(8))){
   if(!npc.isAlive())continue;
   var r=e.settlement().resident(npc.getUUID());if(r==null||r.profession()==null)continue;
   // AD-139: a restaurant's courier passing by is not at the stove.
   if(r.profession()==org.villageastra.domain.Profession.PORTER&&Dining.restaurant(b))continue;
   var work=e.settlement().workplace(r.id());if(work!=null&&work.id().equals(b.id()))return true;
  }
  return false;
 }
 /** The cycle of a level-V building that is not a bench: field, quarry or adit. */
 private static boolean cycle(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  return switch(b.type()){
   case "farm"->field(l,e,b);
   case Quarry.BUILDING->quarry(l,e,b);
   case "mine"->adit(l,e,b);
   case "livestock"->LivestockMachine.cycle(l,e,b);
   default->false;
  };
 }
 /** AD-130: the fields a farm of a layout-6 village of this level has its machine work alone (all of them at VI, none below). */
 public static int automaticFields(int level){return level>=6?FarmField.modules(level).size():0;}
 /** AD-130, AD-136: the level from which a building runs its own cycle (balance/automation.json): a farm of a layout-6 village at VI (it has
  *  no farmer then), an older village's at V. */
 public static int cycleLevel(SettlementData.Entry e,Settlement.Building b){int n=Automation.level(b.type(),b.type().equals("farm")&&FarmField.legacy(e.settlement()));return n==0?7:n;}
 /** AD-136 (D9): the research that lets the machines repair the village's roads (was Mechanics V). */
 public static final String ROAD_REPAIR="roads.6";
 private static UUID op(Settlement.Building b,String what,long now){return Settlement.childId(b.id(),"machine/"+what+"/"+now);}
 /** The automatic field: one cell per turn — sown from the farm's own seed or reaped into its own chest. */
 /** Why the last turn of a machine did nothing — for tests and probes. */
 public static String lastReason="";
 private static boolean field(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var chest=LogisticsRoutes.chest(l,e,b);if(chest==null){lastReason="no chest";return false;}
  if(!FarmField.legacy(e.settlement()))return floors(l,e,b,chest);
  var crop=FarmPolicies.get(l.getServer()).crop(e.settlement().id(),b.id());
  // A sowing begun on an earlier turn is finished first, whatever the chest holds now: its seed is already out.
  if(record(record(l,b)).hasUUID("sow"))return sow(l,e,b,chest,null,crop,"sow");
  int cells=0,mine=0,air=0,plantable=0;
  // AD-112: this farm's own worked plots — the modules its core's level works within the land laid.
  for(var cell:FarmField.workedCells(l,e,b)){
   cells++;
   if(!l.hasChunkAt(cell))continue;
   mine++;
   var mature=FarmCrops.harvest(l,cell);
   if(mature!=null)return reap(l,e,b,chest,mature);
   if(!l.getBlockState(cell).isAir())continue;
   air++;
   if(!crop.block.defaultBlockState().canSurvive(l,cell))continue;
   plantable++;
   if(chest.countItem(crop.seed)<=0){lastReason="no seed";return false;}
   if(sow(l,e,b,chest,cell,crop,"sow"))return true;
   lastReason="the sowing was refused";return false;
  }
  lastReason="cells="+cells+" mine="+mine+" air="+air+" plantable="+plantable+" crop="+crop.id();
  return false;
 }
 /** AD-130: the machine of a level-VI farm — one operation on every floor it works each turn: a ripe plot is reaped into the farm chest and
  *  sown again at once with its field's crop, else an empty plot is sown (its soil tilled first); the seed comes from the floor's seed bin,
  *  then the farm chest, and the bone meal of agriculture as a farmer gives it. The porters bring seed and bone meal and take the harvest
  *  (Workshops.wants, LogisticsRoutes). */
 private static boolean floors(ServerLevel l,SettlementData.Entry e,Settlement.Building b,Container chest){
  var worked=FarmField.worked(l,e,b);var policies=FarmPolicies.get(l.getServer());boolean any=false;var why=new StringBuilder();
  for(int f:FarmField.floors(worked)){String key=f==0?"sow":"sow"+f;
   // A sowing begun on an earlier turn is finished first, whatever the chest holds now: its seed is already out.
   if(record(record(l,b)).hasUUID(key)){if(sow(l,e,b,chest,null,null,key))any=true;continue;}
   var mods=new ArrayList<int[]>();for(var m:worked)if(FarmField.floor(m)==f)mods.add(m);
   var cells=new ArrayList<BlockPos>();for(var p:FarmField.localCells(mods))cells.add(BuildingPlacement.at(e,b,p.getX(),p.getY(),p.getZ()));
   BlockPos ripe=null,empty=null;
   for(var cell:cells){if(!l.hasChunkAt(cell))continue;var mature=FarmCrops.harvest(l,cell);if(mature!=null){ripe=mature;break;}if(empty==null&&l.getBlockState(cell).isAir())empty=cell;}
   BlockPos cell=ripe!=null?ripe:empty;if(cell==null){why.append(" f").append(f).append(":waiting");continue;}
   if(ripe!=null&&!reap(l,e,b,chest,ripe)){why.append(" f").append(f).append(":").append(lastReason);continue;}
   if(ripe!=null)any=true;
   // Sugar cane grows from its own root: a reaped cane plot is not sown again.
   if(!l.getBlockState(cell).isAir())continue;
   var crop=policies.at(e,cell);var soil=cell.below();var ground=l.getBlockState(soil);
   if(crop.block instanceof net.minecraft.world.level.block.CropBlock&&!ground.is(Blocks.FARMLAND)&&(ground.is(Blocks.DIRT)||ground.is(Blocks.GRASS_BLOCK))){
    if(!WorldJournal.place(l,op(b,"till/"+soil.asLong(),l.getGameTime()/20),soil,ground,Blocks.FARMLAND.defaultBlockState())){why.append(" f").append(f).append(":till");continue;}}
   if(!crop.block.defaultBlockState().canSurvive(l,cell)){why.append(" f").append(f).append(":").append(crop.id()).append(" cannot grow");continue;}
   if(sow(l,e,b,chest,cell,crop,key))any=true;else why.append(" f").append(f).append(":").append(lastReason);
  }
  lastReason=any?"":why.toString().trim();return any;
 }
 /** The seed bin of a floor's machine (VI): the barrel on its landing, when it stands. */
 private static Container bin(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int floor){var pos=FarmBarn.at(e,b,FarmBarn.bin(floor));return l.hasChunkAt(pos)&&l.getBlockEntity(pos) instanceof Container c?c:null;}
 /** A level-V farm's own record: the sowing it has begun. Every sowing has an id of its own (a cell is sown again after each harvest),
  *  written down before its seed is taken, so a sowing cut short by a stop is finished under the same id — never repeated, never lost. */
 private static java.nio.file.Path record(ServerLevel l,Settlement.Building b){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-machines/"+b.id()+".bin");}
 private static CompoundTag record(java.nio.file.Path file){return java.nio.file.Files.exists(file)?org.villageastra.persistence.NbtRecord.read(file):new CompoundTag();}
 /** AD-130: the sowing of one machine (key: "sow" the ground floor's and the level-V field's, "sow1"/"sow2" the upper floors'); written down
  *  (cell, source and crop) before its seed is taken, so a sowing cut short by a stop is finished under the same id — never repeated, never lost. */
 private static boolean sow(ServerLevel l,SettlementData.Entry e,Settlement.Building b,Container chest,BlockPos cell,FarmCrops crop,String key){
  var file=record(l,b);var state=record(file);String suffix=key.substring(3);
  if(!state.hasUUID(key)){if(cell==null||crop==null)return false;
   // AD-130: the seed bin of the floor's machine first, then the farm chest.
   var pos=LogisticsRoutes.position(e,b);
   if(!FarmField.legacy(e.settlement())){int floor=suffix.isEmpty()?0:Integer.parseInt(suffix);var bin=bin(l,e,b,floor);if(bin!=null&&bin.countItem(crop.seed)>0)pos=FarmBarn.at(e,b,FarmBarn.bin(floor));}
   if(!(l.getBlockEntity(pos) instanceof Container source)||source.countItem(crop.seed)<=0){lastReason="no seed";return false;}
   state.putUUID(key,UUID.randomUUID());state.putLong("cell"+suffix,cell.asLong());state.putLong("from"+suffix,pos.asLong());state.putString("crop"+suffix,crop.id());org.villageastra.persistence.NbtRecord.write(file,state);}
  var id=state.getUUID(key);var at=BlockPos.of(state.getLong("cell"+suffix));var pos=state.contains("from"+suffix)?BlockPos.of(state.getLong("from"+suffix)):LogisticsRoutes.position(e,b);
  if(state.contains("crop"+suffix))crop=FarmCrops.from(state.getString("crop"+suffix));else if(crop==null)crop=FarmPolicies.get(l.getServer()).crop(e.settlement().id(),b.id());
  var seed=WorldJournal.recoverAmount(l,id);
  if(seed.isEmpty()&&!WorldJournal.exists(l,id)&&l.getBlockEntity(pos) instanceof Container source){
   int slot=-1;for(int i=0;i<source.getContainerSize();i++)if(source.getItem(i).is(crop.seed)){slot=i;break;}
   // One seed, not the whole stack: the machine takes exactly what it sows.
   if(slot>=0)seed=WorldJournal.takeAmount(l,id,pos,slot,source.getItem(slot).copy(),1);
  }
  // Nothing was taken (no seed, or the chest changed under the withdrawal): the sowing is over before it began.
  if(seed.isEmpty()){forget(file,state,key);return false;}
  FarmCrops sown=crop;for(var c:FarmCrops.values())if(seed.is(c.seed))sown=c;
  var plant=Settlement.childId(id,"plant");
  boolean placed=WorldJournal.recoverExisting(l,plant)!=null&&l.getBlockState(at).is(sown.block)
   ||WorldJournal.place(l,plant,at,Blocks.AIR.defaultBlockState(),sown.block.defaultBlockState());
  // The cell was taken meanwhile (a hand sowed it, a block stands there): the seed goes back to the chest it came from.
  if(!placed&&!WorldJournal.deposit(l,Settlement.childId(id,"return"),pos,seed))return false;
  // AD-130: agriculture's bone meal, as the farmer gives it after sowing (out of the farm chest).
  if(placed&&!FarmField.legacy(e.settlement()))ResearchKnobs.feedCrop(l,e,LogisticsRoutes.position(e,b),at,id);
  forget(file,state,key);return placed;
 }
 private static void forget(java.nio.file.Path file,CompoundTag state,String key){String suffix=key.substring(3);state.remove(key);state.remove("cell"+suffix);state.remove("from"+suffix);state.remove("crop"+suffix);org.villageastra.persistence.NbtRecord.write(file,state);}
 private static boolean reap(ServerLevel l,SettlementData.Entry e,Settlement.Building b,Container chest,BlockPos cell){
  // A ripe plant is reaped only when its whole yield has room in the chest: a drop without room would be lost.
  int free=0;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).isEmpty())free++;
  if(free<2){lastReason="the chest is full";return false;}
  var id=op(b,"reap/"+cell.asLong(),l.getGameTime()/20);
  var before=l.getBlockState(cell);
  var loot=WorldJournal.harvest(l,id,cell,before,ItemStack.EMPTY);
  if(loot==null)return false;
  for(int i=0;i<loot.size();i++)if(!loot.get(i).isEmpty())WorldJournal.deposit(l,Settlement.childId(id,"drop/"+i),LogisticsRoutes.position(e,b),loot.get(i));
  return true;
 }
 /** The automatic quarry works out its own chunk, block by block, exactly as a miner would. */
 private static boolean quarry(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(Quarry.record(l,e.settlement().id())==null)Quarry.open(l,e);
  var next=Quarry.next(l,e);if(next==null)return false;
  digTicks=MinerSpeed.breakTicks(l.getBlockState(next),l,next,PICK);
  return Quarry.dig(l,e,b,next).isEmpty();
 }
 /** The automatic adit drives the mine's own shaft on, one block a turn, into the mine's chest. */
 private static boolean adit(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)return false;
  // AD-112: the same MineDrive as the miner — the stair to the floor of the mine's working level, then its galleries, then nothing.
  var state=MineWork.read(l,b);
  var next=MineWork.next(l,e,b,state);MineWork.chose(state,next);
  if(next.floor()){lastReason="mine_floor";return false;}
  var target=MineWork.at(e,b,next.cell());
  if(!l.hasChunkAt(target))return false;
  var before=l.getBlockState(target);
  if(MineWork.gallery(state)&&MineWork.unsafeGallery(l,target)){MineWork.blocked(state);MineWork.write(l,b,state);lastReason="the gallery is blocked";return false;}
  if(before.isAir()||MineWork.builtLining(l,e,b,target,state)){if(MineWork.gallery(state))MineWork.claim(l,e,b,state);MineWork.advance(l,b,state);return false;}
  if(!MineWork.diggable(l,e,b,target,state))return false;
  var id=op(b,"adit/"+target.asLong(),0);
  if(WorldJournal.exists(l,id))return false;
  var loot=WorldJournal.harvest(l,id,target,before,PICK.copy());
  if(loot==null)return false;
  digTicks=MinerSpeed.breakTicks(before,l,target,PICK);
  int kept=0;for(var drop:loot){if(drop.isEmpty())continue;if(WorldJournal.deposit(l,Settlement.childId(id,"drop/"+kept),LogisticsRoutes.position(e,b),drop))kept++;}
  MineWork.claim(l,e,b,state);
  MineWork.advance(l,b,state);
  return true;
 }
 /** One delivery of the village's own logistics, done by the machine instead of a porter. */
 private static boolean haul(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var route=LogisticsRoutes.choose(l,e);
  if(route==null){lastReason="the village asks for no delivery";return false;}
  if(!(route.source().id().equals(b.id())||route.destination().id().equals(b.id()))){lastReason="the delivery "+route.source().type()+"->"+route.destination().type()+" is not this building's";return false;}
  var from=LogisticsRoutes.chest(l,e,route.source());var to=LogisticsRoutes.chest(l,e,route.destination());
  if(from==null||to==null){lastReason="a chest of the delivery is missing";return false;}
  int slot=-1;for(int i=0;i<from.getContainerSize();i++)if(ItemStack.isSameItemSameTags(from.getItem(i),route.item())){slot=i;break;}
  if(slot<0){lastReason="the source no longer holds "+route.item();return false;}
  var id=op(b,"haul/"+route.destination().id(),l.getGameTime()/20);
  var stack=from.getItem(slot).copy();stack.setCount(Math.min(stack.getCount(),route.item().getCount()));
  var taken=WorldJournal.takeAmount(l,id,LogisticsRoutes.position(e,route.source()),slot,from.getItem(slot).copy(),stack.getCount());
  if(taken.isEmpty())return false;
  return WorldJournal.deposit(l,Settlement.childId(id,"put"),LogisticsRoutes.position(e,route.destination()),taken);
 }
}
