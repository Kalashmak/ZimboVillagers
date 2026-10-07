package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-137: the part of the active construction project's estimate its builder has not withdrawn yet stays in the hall stock for him.
 *  Derived from the queued project (data/astra-upgrades) only: cost less the builder's cargo while the project is neither funded, complete nor
 *  paused by the mayor. Every other consumer of the hall sees the stock less this reserve; the builder's own funding take is recognised by its
 *  journal id (project id / fund/&lt;withdrawals&gt;). Nothing new is saved. */
public final class HallReserve {
 private HallReserve(){}
 private record Stamp(Path path,long revision,java.nio.file.attribute.FileTime modified,java.nio.file.attribute.FileTime created,long length,Object key){}
 private record Snapshot(Stamp stamp,UUID project,UUID target,Map<Item,Integer> reserved,UUID builderTake){}
 private static final Map<UUID,Snapshot> CACHE=new HashMap<>();
 private static final Snapshot NONE=new Snapshot(null,null,null,Map.of(),null);
 private static Path file(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+village+".bin");}
 private static Snapshot snapshot(ServerLevel l,UUID village){
  // Full timestamps, replacement identity and the writer revision invalidate even
  // two internal payments in one filesystem timestamp. Unchanged plans are not
  // repeatedly read/checksummed/hashed for each individual stock item.
  var f=file(l,village).toAbsolutePath().normalize();byte[] bytes;Stamp stamp;
  try{if(!Files.exists(f)){CACHE.remove(village);return NONE;}
   long revision=org.villageastra.persistence.AtomicRecord.revision(f);
   var a=Files.readAttributes(f,java.nio.file.attribute.BasicFileAttributes.class);
   stamp=new Stamp(f,revision,a.lastModifiedTime(),a.creationTime(),a.size(),a.fileKey());
   var old=CACHE.get(village);if(old!=null&&stamp.equals(old.stamp()))return old;
   bytes=org.villageastra.persistence.AtomicRecord.read(f);
  }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  CompoundTag state;try{state=NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes)));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}var reserved=new LinkedHashMap<Item,Integer>();UUID take=null;
  if(!state.getBoolean("complete")&&!state.getBoolean("funded")&&state.hasUUID("id")){
   var held=new HashMap<Item,Integer>();for(var raw:state.getList("cargo",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);if(!s.isEmpty())held.merge(s.getItem(),s.getCount(),Integer::sum);}
   var cost=state.getCompound("cost");
   for(var key:cost.getAllKeys().stream().sorted().toList()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));if(item==Items.AIR)continue;int n=cost.getInt(key)-held.getOrDefault(item,0);if(n>0)reserved.merge(item,n,Integer::sum);}
   take=Settlement.childId(state.getUUID("id"),"fund/"+state.getInt("withdrawals"));
  }
  // The building the project works on: its own id, a field order's or the hall (null) for a hall upgrade.
  UUID target=state.hasUUID("building")?state.getUUID("building"):BuildingOrders.isBuilding(state)&&state.hasUUID("id")?BuildingOrders.buildingId(state):null;
  var snap=new Snapshot(stamp,state.hasUUID("id")?HallConstructionPlan.projectId(state):null,target,Map.copyOf(reserved),take);CACHE.put(village,snap);return snap;
 }
 private static Snapshot active(ServerLevel l,SettlementData.Entry e){
  var s=snapshot(l,e.settlement().id());if(s.reserved().isEmpty())return NONE;
  // A project the mayor paused holds nothing: the village may use the stock meanwhile (it is reserved again once resumed).
  return s.project()!=null&&e.settlement().governance().paused(s.project())?NONE:s;
 }
 /** What the active project still has to withdraw, by item (empty without one). */
 public static Map<Item,Integer> reserved(ServerLevel l,SettlementData.Entry e){return active(l,e).reserved();}
 public static int reserved(ServerLevel l,SettlementData.Entry e,Item item){return active(l,e).reserved().getOrDefault(item,0);}
 /** Of the hall stock of this item, what is kept for the builder: the reserve, at most what lies there. */
 public static int held(ServerLevel l,SettlementData.Entry e,Item item){var c=chest(l,e);return c==null?0:Math.min(reserved(l,e,item),LogisticsRoutes.count(c,s->s.is(item)));}
 /** Of the hall stock of this item, what is kept for a project other than one of this building (a card of its next level shows the rest). */
 public static int keptFrom(ServerLevel l,SettlementData.Entry e,Settlement.Building b,Item item){
  var snap=active(l,e);if(snap.reserved().isEmpty())return 0;
  boolean own=snap.target()==null?b.type().equals("town_hall"):snap.target().equals(b.id());if(own)return 0;
  // Reuse this call's verified plan; stock remains live and the next call checks its own stamp.
  var c=chest(l,e);return c==null?0:Math.min(snap.reserved().getOrDefault(item,0),LogisticsRoutes.count(c,s->s.is(item)));
 }
 /** The hall stock of this item any consumer but the builder may take. */
 public static int available(ServerLevel l,SettlementData.Entry e,Item item){var c=chest(l,e);return c==null?0:Math.max(0,LogisticsRoutes.count(c,s->s.is(item))-reserved(l,e,item));}
 private static OwnedChestEntity chest(ServerLevel l,SettlementData.Entry e){var hall=Workshops.hall(e);return hall==null?null:LogisticsRoutes.chest(l,e,hall);}
 /** Count of a building's chest as other consumers see it: for the hall, every matching item less its reserve. */
 public static int count(ServerLevel l,SettlementData.Entry e,Settlement.Building b,Container c,Predicate<ItemStack> matches){
  if(c==null)return 0;if(b==null||!b.type().equals("town_hall"))return plain(c,matches);
  var reserved=reserved(l,e);if(reserved.isEmpty())return plain(c,matches);
  var per=new HashMap<Item,Integer>();for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(!s.isEmpty()&&matches.test(s))per.merge(s.getItem(),s.getCount(),Integer::sum);}
  int n=0;for(var en:per.entrySet())n+=Math.max(0,en.getValue()-reserved.getOrDefault(en.getKey(),0));return n;
 }
 /** Count of the hall chest as other consumers see it. */
 public static int count(ServerLevel l,SettlementData.Entry e,Container hall,Predicate<ItemStack> matches){return count(l,e,Workshops.hall(e),hall,matches);}
 private static int plain(Container c,Predicate<ItemStack> m){int n=0;for(int i=0;i<c.getContainerSize();i++)if(m.test(c.getItem(i)))n+=c.getItem(i).getCount();return n;}
 /** The hall chest as other consumers see it for planning: the reserve is hidden from the last slots of each item. Read only;
  *  a withdrawal still names the real slot and its real stack (WorldJournal checks it), at most the count this view shows. */
 public static Container view(ServerLevel l,SettlementData.Entry e,Container hall){return view(l,e,hall,false);}
 /** The funding selector and its journal guard agree on the small maintenance buffer. */
 public static Container buildView(ServerLevel l,SettlementData.Entry e,Container hall){
  if(!ToolSupplyReserve.needed(l,e))return hall;
  var result=new net.minecraft.world.SimpleContainer(hall.getContainerSize());var left=new HashMap<Item,Integer>();
  for(int i=hall.getContainerSize()-1;i>=0;i--){var stack=hall.getItem(i).copy();int keep=left.computeIfAbsent(stack.getItem(),ToolSupplyReserve::quantity);
   int cut=Math.min(keep,stack.getCount());stack.shrink(cut);left.put(hall.getItem(i).getItem(),keep-cut);result.setItem(i,stack);}
  return result;
 }
 public static Container repairView(ServerLevel l,SettlementData.Entry e,Container hall){return view(l,e,hall,true);}
 private static Container view(ServerLevel l,SettlementData.Entry e,Container hall,boolean repair){
  if(hall==null)return null;var reserved=reserved(l,e);if(reserved.isEmpty())return hall;
  var left=new HashMap<>(reserved);
  if(repair&&ToolSupplyReserve.needed(l,e))for(var item:reserved.keySet())left.put(item,Math.min(reserved.get(item),Math.max(0,plain(hall,s->s.is(item))-ToolSupplyReserve.quantity(item))));
  var stacks=new ItemStack[hall.getContainerSize()];
  for(int i=stacks.length-1;i>=0;i--){var s=hall.getItem(i).copy();int r=left.getOrDefault(s.getItem(),0);if(r>0&&!s.isEmpty()){int cut=Math.min(r,s.getCount());s.shrink(cut);left.put(s.getItem(),r-cut);}stacks[i]=s.isEmpty()?ItemStack.EMPTY:s;}
  return new Container(){
   public int getContainerSize(){return stacks.length;}
   public boolean isEmpty(){for(var s:stacks)if(!s.isEmpty())return false;return true;}
   public ItemStack getItem(int i){return stacks[i];}
   public ItemStack removeItem(int i,int n){throw new UnsupportedOperationException("Hall reserve view is read only");}
   public ItemStack removeItemNoUpdate(int i){throw new UnsupportedOperationException("Hall reserve view is read only");}
   public void setItem(int i,ItemStack s){throw new UnsupportedOperationException("Hall reserve view is read only");}
   public int getMaxStackSize(){return hall.getMaxStackSize();}
   public void setChanged(){}
   public boolean stillValid(Player p){return false;}
   public void clearContent(){throw new UnsupportedOperationException("Hall reserve view is read only");}
  };
 }
 /** The settlement whose hall stock chest (its anchor or one of its double-chest parts) stands at this position, or null. */
 static SettlementData.Entry owner(ServerLevel l,BlockPos pos){
  var at=pos;if(l.hasChunkAt(pos)&&l.getBlockEntity(pos) instanceof OwnedChestEntity c&&c.getPersistentData().contains("AstraHallMaster"))at=BlockPos.of(c.getPersistentData().getLong("AstraHallMaster"));
  var dim=l.dimension().location().toString();
  for(var e:SettlementData.get(l.getServer()).entries()){if(!e.dimension().equals(dim))continue;var hall=Workshops.hall(e);if(hall==null)continue;
   if(at.equals(LogisticsRoutes.position(e,hall))||at.equals(HallSite.stock(e)))return e;}
  return null;
 }
 /** How much of a stock a withdrawal from this position may take: all of it anywhere but the hall; at the hall the builder's funding take
  *  of the active project may take all, any other only what lies above the reserve. */
 public static int free(ServerLevel l,BlockPos pos,ItemStack stack){
  if(stack.isEmpty())return 0;var e=owner(l,pos);if(e==null)return Integer.MAX_VALUE;var snap=active(l,e);int r=snap.reserved().getOrDefault(stack.getItem(),0);if(r==0)return Integer.MAX_VALUE;
  return available(l,e,stack.getItem());
 }
 /** WorldJournal's guard of a new withdrawal: the amount it may take now (0 refuses it). */
 public static int allow(ServerLevel l,UUID take,BlockPos pos,ItemStack before,int amount){
  if(before.isEmpty())return amount;var e=owner(l,pos);if(e==null)return amount;var snap=active(l,e);
  int r=snap.reserved().getOrDefault(before.getItem(),0);
  if(take.equals(snap.builderTake())){var chest=chest(l,e);int total=chest==null?0:plain(chest,s->s.is(before.getItem()));return Math.max(0,Math.min(amount,total-ToolSupplyReserve.quantity(l,e,before.getItem())));}
  if(r==0)return amount;
  int free=available(l,e,before.getItem());
  if(ToolSupplyReserve.payment(l,e,take))free+=ToolSupplyReserve.quantity(before.getItem());
  return Math.max(0,Math.min(amount,free));
 }
}
