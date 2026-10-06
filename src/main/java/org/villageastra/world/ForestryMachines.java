package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
/** AD-131: the forester's hut's own machinery — no worker, no player and no mechanics research (an exception to AD-077: the forestry
 *  research alone opens it). <ul>
 *  <li>IV+ the sawmill: every sawPeriod ticks one log of the hut chest becomes PLANKS_PER_LOG planks of its kind, while the chest keeps more
 *  than LOG_KEEP logs (oak counted apart: the mine's beams are oak), no demand of the village wants that log as a log, and the hall holds
 *  fewer than PLANK_TARGET planks. The log's take and the planks' deposit share one journal batch.</li>
 *  <li>VI the courtyard grove: six cells, each EMPTY → PLANTED → GROWN → SAWING → EMPTY. A sapling from the hut chest is planted, grows into a
 *  compact tree of its kind GROW_TICKS later (24 times vanilla's pace, only in these six cells), the one automatic saw fells the grown trees in
 *  turn, SAW_TICKS_PER_LOG a log, and the whole tree and its crown go into the hut chest in one journal batch.</li></ul>
 *  A turn is taken only where the village ticks (Automation) and never under siege. */
public final class ForestryMachines {
 private ForestryMachines(){}
 public static Path path(ServerLevel l,UUID building){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-grove/"+building+".bin");}
 private record Read(long modified,CompoundTag tag){}
 private static final Map<Path,Read> READS=new java.util.concurrent.ConcurrentHashMap<>();
 /** The hut's record (read again only when its file changed: the grove's demand is asked every tick). */
 public static CompoundTag inspect(ServerLevel l,UUID building){var p=path(l,building);
  try{if(!Files.exists(p)){READS.remove(p);return fresh();}long m=Files.getLastModifiedTime(p).toMillis()^Files.size(p)<<40;var c=READS.get(p);
   if(c==null||c.modified()!=m){var t=NbtRecord.read(p);if(t.getInt("schema")!=1)throw new IllegalStateException("Unsupported grove record "+p);c=new Read(m,t);READS.put(p,c);}
   return c.tag().copy();}catch(java.io.IOException ex){throw new IllegalStateException(ex);}}
 private static CompoundTag fresh(){var t=new CompoundTag();t.putInt("schema",1);var cells=new ListTag();for(int i=0;i<ForestBalance.GROVE.size();i++){var c=new CompoundTag();c.putString("stage","empty");cells.add(c);}t.put("cells",cells);t.putInt("saw",-1);return t;}
 private static void store(ServerLevel l,UUID building,CompoundTag t){NbtRecord.write(path(l,building),t);READS.remove(path(l,building));}
 /** One tick of every forester's hut of a settlement; how many did real work. */
 public static int tick(ServerLevel l,SettlementData.Entry e,long now,List<Workshops.Want> wants){
  return tick(l,e,now,()->wants);
 }
 public static int tick(ServerLevel l,SettlementData.Entry e,long now,java.util.function.Supplier<List<Workshops.Want>> wants){
  int worked=0;
  for(var b:List.copyOf(e.settlement().buildings())){if(!b.type().equals(ForesterHut.TYPE))continue;
   boolean saw=false,grove=false;int period=0;
   if(now%ForestBalance.GROVE_TURN==0)grove=true;
   if(!grove){for(int lv=ForestBalance.SAW_FROM;lv<=6;lv++){int p=ForestBalance.sawPeriod(lv);if(p>0&&now%p==0){saw=true;break;}}}
   else saw=true;
   if(!saw&&!grove)continue;
   int level=BuildingLevels.level(l,e,b);if(level<ForestBalance.SAW_FROM)continue;
   if(Sieges.besieged(l.getServer(),e.settlement().id()))continue;
   period=ForestBalance.sawPeriod(level);
   if(period>0&&now%period==0&&saw(l,e,b,level,now,wants.get()))worked++;
   if(groveCells(level)>0&&grove&&grove(l,e,b,now))worked++;
  }
  return worked;
 }
 /** Planks a log gives at a working level: the sawmill's from SAW_FROM, a crafting table's four before. */
 public static int planksPerLog(int level){return level>=ForestBalance.SAW_FROM?ForestBalance.PLANKS_PER_LOG:4;}
 /** The grove's places at a working level (all six at VI, none before). */
 public static int groveCells(int level){return level>=6?ForestBalance.GROVE.size():0;}
 // ---------- the sawmill ----------
 private static boolean oak(ItemStack s){return s.is(Items.OAK_LOG);}
 /** One cut of the sawmill of a hut of this level, due now. False: nothing was cut (and why is in the record's "sawStatus"). */
 public static boolean saw(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level,long now,List<Workshops.Want> wants){
  if(level<ForestBalance.SAW_FROM)return false;var t=inspect(l,b.id());
  String why=cut(l,e,b,level,now,wants,t);
  if(!why.equals(t.getString("sawStatus"))||why.isEmpty()){t.putString("sawStatus",why);store(l,b.id(),t);}
  return why.isEmpty();
 }
 private static String cut(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level,long now,List<Workshops.Want> wants,CompoundTag t){
  if(!ForestPolicies.get(l.getServer()).sawOn(e,b))return "saw_off";
  var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)return "unloaded";
  var hall=Workshops.hall(e);var stock=hall==null?null:LogisticsRoutes.chest(l,e,hall);
  // AD-137: planks the hall keeps for the active project do not count toward the target: the saw works on for the rest of the village.
  if(stock!=null&&HallReserve.count(l,e,hall,stock,s->s.is(ItemTags.PLANKS))>=ForestBalance.PLANK_TARGET)return "saw_idle_target";
  var pos=LogisticsRoutes.position(e,b);UUID op=Settlement.childId(b.id(),"saw/"+now);UUID take=Settlement.childId(op,"take"),put=Settlement.childId(op,"put");
  if(WorldJournal.exists(l,take)){var got=WorldJournal.recoverAmount(l,take);var planks=got.isEmpty()?null:ForestWork.planks(got.getItem());if(planks!=null)WorldJournal.deposit(l,put,pos,new ItemStack(planks,planksPerLog(level)));return "replayed";}
  int oaks=LogisticsRoutes.count(chest,ForestryMachines::oak),others=LogisticsRoutes.count(chest,s->s.is(ItemTags.LOGS)&&!oak(s));
  for(int slot=0;slot<chest.getContainerSize();slot++){var stack=chest.getItem(slot);if(stack.isEmpty()||!stack.is(ItemTags.LOGS))continue;
   var planks=ForestWork.planks(stack.getItem());if(planks==null)continue;
   if((oak(stack)?oaks:others)<=ForestBalance.LOG_KEEP)continue;
   // A log the village wants as a log (the mine's beams, a workshop's input) stays a log.
   final var log=stack.copyWithCount(1);if(wants.stream().anyMatch(w->w.matches(log)))continue;
   var out=new ItemStack(planks,planksPerLog(level));
   var after=new ArrayList<ItemStack>();for(int i=0;i<chest.getContainerSize();i++)after.add(i==slot?stack.copyWithCount(stack.getCount()-1):chest.getItem(i).copy());
   if(!fits(after,out,chest.getMaxStackSize()))return "output_full";
   final int at=slot;final var before=stack.copy();
   boolean done=WorldJournal.batch(l,()->{var got=WorldJournal.takeAmount(l,take,pos,at,before,1);return !got.isEmpty()&&WorldJournal.deposit(l,put,pos,out);});
   if(!done)return "changed";
   long day=l.getDayTime()/24000L;if(t.getLong("sawDay")!=day){t.putLong("sawDay",day);t.putInt("sawnToday",0);}t.putInt("sawnToday",t.getInt("sawnToday")+1);
   l.playSound(null,ForesterHut.at(e,b,ForesterHut.SAW),net.minecraft.sounds.SoundEvents.AXE_STRIP,net.minecraft.sounds.SoundSource.BLOCKS,.6f,.8f);
   return "";
  }
  return oaks+others<=ForestBalance.LOG_KEEP?"saw_idle_reserve":"saw_idle_wanted";
 }
 private static boolean fits(List<ItemStack> slots,ItemStack add,int max){for(var s:slots){if(s.isEmpty())return true;if(ItemStack.isSameItemSameTags(s,add)&&s.getCount()+add.getCount()<=Math.min(max,s.getMaxStackSize()))return true;}return false;}
 // ---------- the courtyard grove ----------
 /** The world cells of the grove of every level-VI hut the machines have turned (for the growth event); in memory. */
 private static final Set<String> CELLS=java.util.concurrent.ConcurrentHashMap.newKeySet();
 private static String key(ServerLevel l,BlockPos p){return l.dimension().location()+"/"+p.asLong();}
 public static boolean groveCell(ServerLevel l,BlockPos p){return CELLS.contains(key(l,p));}
 /** The kind the grove raises: the mayor's preferred kind when it is a compact one and opened, else the oak. */
 public static Item kind(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var id=ForestPolicies.get(l.getServer()).preferred(e,b);
  return ForestBalance.GROVE_SPECIES.contains(id)&&CropUnlocks.unlocked(l,e,id)?BuiltInRegistries.ITEM.get(new ResourceLocation(id)):Items.OAK_SAPLING;
 }
 /** The compact feature a grove sapling of this block grows into (never a fancy oak with branches through the roofs, never a bee nest). */
 public static ResourceKey<ConfiguredFeature<?,?>> feature(Block sapling){
  if(sapling==Blocks.BIRCH_SAPLING)return TreeFeatures.BIRCH;if(sapling==Blocks.SPRUCE_SAPLING)return TreeFeatures.SPRUCE;
  if(sapling==Blocks.JUNGLE_SAPLING)return TreeFeatures.JUNGLE_TREE_NO_VINE;return TreeFeatures.OAK;
 }
 public static Holder<ConfiguredFeature<?,?>> holder(ServerLevel l,Block sapling){return l.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(feature(sapling));}
 /** Grows the sapling of a grove cell into its compact tree now; false (the sapling back) when the tree does not fit. */
 static boolean grow(ServerLevel l,BlockPos at,long seed){
  var sapling=l.getBlockState(at);if(!sapling.is(BlockTags.SAPLINGS))return false;
  l.setBlock(at,Blocks.AIR.defaultBlockState(),4);
  if(holder(l,sapling.getBlock()).value().place(l,l.getChunkSource().getGenerator(),RandomSource.create(seed),at))return true;
  l.setBlock(at,sapling,4);return false;
 }
 private static final ItemStack AXE=new ItemStack(Items.IRON_AXE);
 /** One turn of the grove of a level-VI hut: every cell moves on as far as it can, the saw works one tree. */
 public static boolean grove(ServerLevel l,SettlementData.Entry e,Settlement.Building b,long now){
  var chestPos=LogisticsRoutes.position(e,b);var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)return false;
  var t=inspect(l,b.id());var cells=t.getList("cells",Tag.TAG_COMPOUND);var grove=ForesterHut.grove();boolean worked=false,changed=false;
  var kind=kind(l,e,b);var sapling=((BlockItem)kind).getBlock();
  for(int i=0;i<grove.size();i++){var at=ForesterHut.at(e,b,grove.get(i));CELLS.add(key(l,at));if(!l.hasChunkAt(at))continue;var c=cells.getCompound(i);String stage=c.getString("stage");var here=l.getBlockState(at);
   if(stage.equals("empty")){
    if(here.is(BlockTags.LOGS)){c.putString("stage","grown");c.remove("status");changed=true;continue;}
    if(here.is(BlockTags.SAPLINGS)){c.putString("stage","planted");c.putLong("planted",now);c.remove("status");changed=true;continue;}
    if(!here.isAir()||!sapling.defaultBlockState().canSurvive(l,at)){if(!c.getString("status").equals("blocked")){c.putString("status","blocked");changed=true;}continue;}
    int cycle=c.getInt("cycle");UUID op=Settlement.childId(b.id(),"grove/plant/"+i+"/"+cycle);UUID take=Settlement.childId(op,"take"),place=Settlement.childId(op,"place");
    int slot=-1;for(int s=0;s<chest.getContainerSize();s++)if(chest.getItem(s).is(kind)){slot=s;break;}
    if(slot<0&&!WorldJournal.exists(l,take)){if(!c.getString("status").equals("no_sapling")){c.putString("status","no_sapling");changed=true;}continue;}
    final int from=slot;final var before=from<0?ItemStack.EMPTY:chest.getItem(from).copy();final var cellAt=at;
    boolean set=WorldJournal.batch(l,()->{var got=WorldJournal.exists(l,take)?WorldJournal.recoverTake(l,take):WorldJournal.take(l,take,chestPos,from,before);
     return !got.isEmpty()&&(WorldJournal.recoverExisting(l,place)!=null||WorldJournal.place(l,place,cellAt,Blocks.AIR.defaultBlockState(),sapling.defaultBlockState()));});
    if(set){c.putString("stage","planted");c.putLong("planted",now);c.putString("kind",BuiltInRegistries.ITEM.getKey(kind).toString());c.remove("status");changed=worked=true;}
    continue;}
   if(stage.equals("planted")){
    if(here.is(BlockTags.LOGS)){c.putString("stage","grown");c.remove("status");changed=true;continue;}
    // A sapling gone (broken, eaten) leaves the cell empty under a new cycle: its planting's ids are spent.
    if(!here.is(BlockTags.SAPLINGS)){c.putString("stage","empty");c.putInt("cycle",c.getInt("cycle")+1);changed=true;continue;}
    if(now-c.getLong("planted")<ForestBalance.GROW_TICKS)continue;
    long seed=b.id().getMostSignificantBits()^((long)i<<32)^c.getInt("cycle")*0x9E3779B97F4A7C15L;
    if(grow(l,at,seed)){c.putString("stage","grown");c.remove("status");changed=worked=true;}
    else if(!c.getString("status").equals("blocked")){c.putString("status","blocked");changed=true;}
    continue;}
   if(stage.equals("grown")&&!here.is(BlockTags.LOGS)){c.putString("stage","empty");c.putInt("cycle",c.getInt("cycle")+1);changed=true;}
  }
  // Loot the chest had no room for waits in the record and goes in first; the saw stands until it has.
  var pending=t.getList("pending",Tag.TAG_COMPOUND);
  if(!pending.isEmpty()){var left=new ListTag();int k=t.getInt("pendingBase");
   for(int j=0;j<pending.size();j++){var s=ItemStack.of(pending.getCompound(j));if(!WorldJournal.deposit(l,Settlement.childId(b.id(),"grove/drop/pending/"+(k+j)),chestPos,s))left.add(pending.getCompound(j));}
   t.putInt("pendingBase",k+pending.size());t.put("pending",left);changed=true;
   if(!left.isEmpty()){t.putString("sawStatusGrove","the_chest_is_full");store(l,b.id(),t);return worked;}}
  // The one saw: the cell it works, or the next grown one.
  int saw=t.getInt("saw");
  if(saw<0)for(int i=0;i<grove.size();i++)if(cells.getCompound(i).getString("stage").equals("grown")){saw=i;break;}
  // With fewer than two free slots in the hut chest the saw stands (it starts no tree and cuts no further into one).
  if(saw>=0){int free=0;for(int s=0;s<chest.getContainerSize();s++)if(chest.getItem(s).isEmpty())free++;
   if(free<2){if(!t.getString("sawStatusGrove").equals("the_chest_is_full")){t.putString("sawStatusGrove","the_chest_is_full");changed=true;}if(changed)store(l,b.id(),t);return worked;}
   if(t.getInt("saw")<0){var c=cells.getCompound(saw);c.putString("stage","sawing");c.putInt("labor",0);t.putInt("saw",saw);changed=true;}}
  if(saw>=0){var c=cells.getCompound(saw);var at=ForesterHut.at(e,b,grove.get(saw));
   var logs=ForestWork.tree(l,at);
   if(logs==null||logs.isEmpty()||!l.getBlockState(at).is(BlockTags.LOGS)){c.putString("stage","empty");c.putInt("cycle",c.getInt("cycle")+1);c.remove("labor");t.putInt("saw",-1);store(l,b.id(),t);return worked;}
   int labor=c.getInt("labor")+ForestBalance.GROVE_TURN,total=ForestBalance.SAW_TICKS_PER_LOG*logs.size();c.putInt("labor",labor);
   l.destroyBlockProgress(-1-(int)(b.id().getLeastSignificantBits()&0xffff),at,Math.min(9,labor*10/Math.max(1,total)));
   if(labor<total){if(labor%40==0)l.playSound(null,ForesterHut.at(e,b,ForesterHut.AUTO_SAW),net.minecraft.sounds.SoundEvents.AXE_STRIP,net.minecraft.sounds.SoundSource.BLOCKS,.5f,1.2f);store(l,b.id(),t);return true;}
   l.destroyBlockProgress(-1-(int)(b.id().getLeastSignificantBits()&0xffff),at,-1);
   var leaves=ForestWork.crown(l,logs);final int cycle=c.getInt("cycle"),sawing=saw;UUID fell=Settlement.childId(b.id(),"grove/fell/"+sawing+"/"+cycle);
   var loot=new ArrayList<ItemStack>();
   WorldJournal.batch(l,()->{
    for(int i=0;i<logs.size();i++){var p=logs.get(i);var got=WorldJournal.harvest(l,Settlement.childId(fell,"log/"+i),p,l.getBlockState(p),AXE);if(got!=null)loot.addAll(got);}
    for(int j=0;j<leaves.size();j++){var p=leaves.get(j);var got=WorldJournal.harvest(l,Settlement.childId(fell,"leaf/"+j),p,l.getBlockState(p),AXE);if(got!=null)loot.addAll(got);}
    var stacks=ResourceWorkGoal.merged(loot);var left=new ListTag();
    for(int k=0;k<stacks.size();k++){var s=ItemStack.of(stacks.getCompound(k));if(!WorldJournal.deposit(l,Settlement.childId(b.id(),"grove/drop/"+sawing+"/"+cycle+"/"+k),chestPos,s))left.add(stacks.getCompound(k));}
    t.put("pending",left);return null;});
   int felledLogs=(int)loot.stream().filter(s->s.is(ItemTags.LOGS)).mapToInt(ItemStack::getCount).sum();
   long day=l.getDayTime()/24000L;if(t.getLong("groveDay")!=day){t.putLong("groveDay",day);t.putInt("groveLogsToday",0);t.putInt("groveTreesToday",0);}
   t.putInt("groveLogsToday",t.getInt("groveLogsToday")+felledLogs);t.putInt("groveTreesToday",t.getInt("groveTreesToday")+1);t.putInt("groveLogs",t.getInt("groveLogs")+felledLogs);t.putInt("groveTrees",t.getInt("groveTrees")+1);
   c.putString("stage","empty");c.putInt("cycle",cycle+1);c.remove("labor");t.putInt("saw",-1);t.remove("sawStatusGrove");
   l.playSound(null,at,net.minecraft.sounds.SoundEvents.WOOD_BREAK,net.minecraft.sounds.SoundSource.BLOCKS,1f,.8f);
   store(l,b.id(),t);return true;}
  if(changed)store(l,b.id(),t);
  return worked;
 }
 /** The grove's row of a hut's card: growing, grown, the nearest ready in seconds, logs and trees today. */
 public static CompoundTag view(ServerLevel l,SettlementData.Entry e,Settlement.Building b,long now){
  var t=inspect(l,b.id());var out=new CompoundTag();int growing=0,grown=0;long next=-1;var cells=t.getList("cells",Tag.TAG_COMPOUND);
  for(int i=0;i<cells.size();i++){var c=cells.getCompound(i);switch(c.getString("stage")){
   case "planted"->{growing++;long left=Math.max(0,ForestBalance.GROW_TICKS-(now-c.getLong("planted")));if(next<0||left<next)next=left;}
   case "grown","sawing"->grown++;default->{}}}
  long day=l.getDayTime()/24000L;
  out.putInt("growing",growing);out.putInt("grown",grown);out.putLong("nextSeconds",next<0?-1:next/20);
  out.putInt("logsToday",t.getLong("groveDay")==day?t.getInt("groveLogsToday"):0);out.putInt("logs",t.getInt("groveLogs"));out.putInt("trees",t.getInt("groveTrees"));
  out.putInt("sawnToday",t.getLong("sawDay")==day?t.getInt("sawnToday"):0);out.putString("sawStatus",t.getString("sawStatus"));
  // AD-131 §5: the saw's own reason first; otherwise the first cell that stands still says why (no sapling in the chest, the cell taken).
  String why=t.getString("sawStatusGrove");
  if(why.isEmpty())for(int i=0;i<cells.size()&&why.isEmpty();i++)why=cells.getCompound(i).getString("status");
  out.putString("groveStatus",why);
  return out;
 }
 /** Saplings a level-VI hut's grove wants in its chest: four of its kind while a cell stands empty without one. */
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(BuildingLevels.level(l,e,b)<6)return List.of();var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)return List.of();
  var kind=kind(l,e,b);int have=LogisticsRoutes.count(chest,s->s.is(kind));if(have>=4)return List.of();
  var t=inspect(l,b.id());boolean empty=false;for(var raw:t.getList("cells",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getString("stage").equals("empty"))empty=true;
  return empty?List.of(new Workshops.Want(net.minecraft.world.item.crafting.Ingredient.of(kind),4-have,b.id())):List.of();
 }
}
