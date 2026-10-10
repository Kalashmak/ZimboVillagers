package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-147 §2 (CF-F..CF-M): the trips of a warehouse with carts. A courier of a level-II warehouse with Logistics II takes a cart of the
 *  warehouse (WarehouseCarts) and carries, besides its hand parcel (leg cart 0, the level's load), up to five stacks in the cart (legs
 *  cart 1, core effect cart_stacks); at V a free wolf pulls a second cart after it (legs cart 2, five more); at VI the warehouse sends its
 *  wolves alone (kind "wolf", no hand parcel). Without a cart, below II or without the research a courier walks with PorterWork (schema 1),
 *  and a trip and a parcel never run at once (CF-G).
 *  <p>The record data/astra-haul/&lt;puller&gt;.bin (schema 1) is the trip's truth: {id, settlement, warehouse, puller, kind, cart, cart2?,
 *  wolf?, cartEpoch, legs:[{source,destination,item,cart}], stops:[{building,take}], stop, stage}. A leg is one stack of one item; its
 *  journal operations are take/i, put/i and back/i (Settlement.childId of the trip): taken while take/i exists, delivered once put/i or
 *  back/i does. What is taken and not delivered is in the record's custody (at most 11 stacks), never in the cart: the cart only shows how
 *  many stacks it carries (CartEntity.show) and is marked travelling with the trip, so no hopper or player touches it. A courier that dies
 *  or loses its post hands its custody to CargoCustody (JobCargo: one drop at its death); a wolf lost on the way drops it at its cart (CF-L).
 *  <p>The way: the courier (or the wolf) takes the hitch at the bay, goes with the cart to each stop - the sources in the order nearest
 *  first, then the destinations - leaves the cart before the door, works the chest (the whole stop at once when the cart stands within
 *  CartEntity.REACH of the chest, else a stack a walk between them; the machine of VI works it through the journal for a wolf, CF-M) and
 *  takes the hitch again. A cart stuck for 200 ticks on end turns the courier home with the load (the building is marked for a day as
 *  one the cart does not reach); the cart stays and is fetched back later (CF-K). What a destination does not take goes back to the
 *  warehouse at the end (back/i); a full warehouse keeps it in custody (output_full) - nothing is ever put on the ground. */
public final class WarehouseTrips {
 private WarehouseTrips(){}
 /** The trips index of a server (active trips by puller), read from the records once (CF-H: the wolves' trips are not residents'). */
 private static final Map<MinecraftServer,Map<UUID,CompoundTag>> INDEX=new WeakHashMap<>();
 /** Buildings a cart did not reach, by village clock tick until which couriers go there by hand. */
 private static final Map<UUID,Long> NO_CART=new java.util.concurrent.ConcurrentHashMap<>();
 public static final int BLOCKED_TICKS=200,LOST_TICKS=200,DAY=24000;
 /** Stacks a cart carries at a level (core effect cart_stacks: 0 at I, 5 from II). */
 public static int cartStacks(int level){return CoreEffects.value("warehouse","cart_stacks",level);}
 static Path dir(ServerLevel l){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-haul");}
 static Path path(ServerLevel l,UUID puller){return dir(l).resolve(puller+".bin");}
 private static Map<UUID,CompoundTag> index(ServerLevel l){
  synchronized(INDEX){var map=INDEX.get(l.getServer());if(map!=null)return map;map=new HashMap<>();var d=dir(l);
   if(Files.isDirectory(d))try(var files=Files.list(d)){for(var f:files.toList()){var name=f.getFileName().toString();if(!name.endsWith(".bin")||name.startsWith("carts-"))continue;
     var t=NbtRecord.read(f);if(t.getInt("schema")==1&&active(t))map.put(t.getUUID("puller"),t);}}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
   INDEX.put(l.getServer(),map);return map;}}
 /** Tests: forget the index of a server (it is read again from the records). */
 public static void forget(MinecraftServer server){synchronized(INDEX){INDEX.remove(server);}}
 public static boolean active(CompoundTag t){return t!=null&&!t.isEmpty()&&!t.getString("stage").equals("complete");}
 /** The active trip of a puller (a courier or a wolf), or an empty tag. */
 public static CompoundTag inspect(ServerLevel l,UUID puller){var t=index(l).get(puller);return t==null?new CompoundTag():t;}
 static void save(ServerLevel l,CompoundTag t){NbtRecord.write(path(l,t.getUUID("puller")),t);var map=index(l);if(active(t))map.put(t.getUUID("puller"),t);else map.remove(t.getUUID("puller"));}
 private static UUID op(CompoundTag t,String kind,int i){return Settlement.childId(t.getUUID("id"),kind+"/"+i);}
 private static ListTag legs(CompoundTag t){return t.getList("legs",Tag.TAG_COMPOUND);}
 /** Taken (its take is in the journal); the record keeps a flag for the reservations, the journal is the truth. */
 static boolean taken(ServerLevel l,CompoundTag t,int i){var leg=legs(t).getCompound(i);if(leg.getBoolean("tk"))return true;if(WorldJournal.exists(l,op(t,"take",i))){leg.putBoolean("tk",true);return true;}return false;}
 static boolean done(ServerLevel l,CompoundTag t,int i){var leg=legs(t).getCompound(i);if(leg.getBoolean("dn"))return true;if(WorldJournal.exists(l,op(t,"put",i))||WorldJournal.exists(l,op(t,"back",i))){leg.putBoolean("dn",true);return true;}return false;}
 /** The stack a leg holds in custody now (taken, not delivered), or empty. */
 static ItemStack held(ServerLevel l,CompoundTag t,int i){if(!taken(l,t,i)||done(l,t,i))return ItemStack.EMPTY;return WorldJournal.recoverAmount(l,op(t,"take",i));}
 /** The journal's account of a trip {taken, delivered, held}: items its take receipts took, its put/back receipts delivered, and what it
  *  holds between them (for the probe and the tests: every leg exactly once means taken == delivered + held). */
 public static int[] audit(ServerLevel l,CompoundTag t){int taken=0,delivered=0,held=0;
  for(int i=0;i<legs(t).size();i++){var take=WorldJournal.exists(l,op(t,"take",i))?WorldJournal.recoverAmount(l,op(t,"take",i)).getCount():0;taken+=take;
   boolean put=WorldJournal.exists(l,op(t,"put",i))||WorldJournal.exists(l,op(t,"back",i));if(put)delivered+=take;else held+=take;}
  return new int[]{taken,delivered,held};}
 /** Everything the trip holds in custody (at most 11 stacks, CF-F). */
 public static List<ItemStack> custody(ServerLevel l,CompoundTag t){var out=new ArrayList<ItemStack>();if(!active(t))return out;for(int i=0;i<legs(t).size();i++){var s=held(l,t,i);if(!s.isEmpty())out.add(s);}return out;}
 /** PorterWork.reserved: what trips of the village still take from a building (legs not taken) or bring to it (legs not delivered). */
 public static int reserved(ServerLevel l,SettlementData.Entry e,UUID building,Predicate<ItemStack> matches,boolean incoming){int n=0;
  for(var t:index(l).values()){if(!t.getUUID("settlement").equals(e.settlement().id()))continue;var list=legs(t);
   for(int i=0;i<list.size();i++){var leg=list.getCompound(i);if(leg.getBoolean("sk")||incoming&&leg.getBoolean("bk"))continue;
    if(incoming?!leg.getUUID("destination").equals(building)||leg.getBoolean("dn"):!leg.getUUID("source").equals(building)||leg.getBoolean("tk"))continue;
    var item=ItemStack.of(leg.getCompound("item"));if(matches.test(item))n+=item.getCount();}}
  return n;}
 /** A cart's body is vouched for while an active trip of its warehouse names it with the same epoch (else it stops showing a load). */
 public static boolean vouches(CartEntity cart){
  if(!(cart.level() instanceof ServerLevel l)||cart.trip()==null)return false;
  for(var t:index(l).values())if(t.getUUID("id").equals(cart.trip()))return t.getLong("cartEpoch")==cart.tripEpoch()&&(t.hasUUID("cart")&&t.getUUID("cart").equals(cart.getUUID())||t.hasUUID("cart2")&&t.getUUID("cart2").equals(cart.getUUID()));
  return false;}
 /** A porter posted at a warehouse. */
 public static boolean courier(SettlementData.Entry e,UUID resident){var r=e.settlement().resident(resident);var w=e.settlement().workplace(resident);return r!=null&&r.alive()&&r.profession()==Profession.PORTER&&WarehouseStore.is(w);}
 public static boolean courier(ResidentEntity w){if(!(w.level() instanceof ServerLevel l)||w.settlementId()==null)return false;var e=SettlementData.get(l.getServer()).entry(w.settlementId());return e!=null&&courier(e,w.getUUID());}
 /** Couriers posted at this warehouse now. */
 public static int posted(SettlementData.Entry e,Settlement.Building b){var s=e.settlement();return (int)s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.PORTER&&s.workplace(r.id())!=null&&s.workplace(r.id()).id().equals(b.id())).count();}
 /** CartHitch: a courier of the cart's own warehouse, which takes carts (II and Logistics II). */
 public static boolean mayPull(ServerLevel l,SettlementData.Entry e,ResidentEntity w,CartEntity cart){
  var b=e.settlement().workplace(w.getUUID());return cart.home()!=null&&b!=null&&b.id().equals(cart.home())&&courier(e,w.getUUID())&&WarehouseCarts.open(l,e,b);}
 /** Wolves of this warehouse on trips now. */
 public static int wolves(ServerLevel l,SettlementData.Entry e,Settlement.Building b){int n=0;for(var t:index(l).values())if(t.getUUID("warehouse").equals(b.id())&&(t.getString("kind").equals("wolf")||t.hasUUID("wolf")))n++;return n;}
 /** A fetch alone has not established replacement transport. The source confirms the hitch as wolf() does; already started trips retain
  *  their recovery grace. A committed take paired with fetch is defensive inconsistent/legacy compatibility, not a proven normal crash window:
  *  the normal writer saves go before the first take. Existing paid recovery remains independent of current pickup readiness.
  *  This observation never replays a receipt or changes the trip. */
 static boolean wolfService(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  for(var t:index(l).values())if(t.getUUID("settlement").equals(e.settlement().id())&&t.getUUID("warehouse").equals(b.id())&&t.getString("kind").equals("wolf")){
   if(!t.getString("stage").equals("fetch"))return true;
   for(int i=0;i<legs(t).size();i++)if(WorldJournal.inspectCommitted(l,op(t,"take",i))!=null)return true;
   var cart=cart(l,t,"cart");if(cart!=null&&b.id().equals(cart.home())&&VillageDogs.pickupReady(l,e,t.getUUID("puller"),cart))return true;
  }return false;
 }
 /** Whether a courier of this warehouse holds anything: a trip's custody or a hand parcel (CF-M: no courier is unposted with a load). */
 public static boolean couriersBusy(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  for(var r:e.settlement().residents()){if(!courier(e,r.id())||!e.settlement().workplace(r.id()).id().equals(b.id()))continue;
   if(active(inspect(l,r.id()))||PorterWork.active(PorterWork.inspect(l,r.id())))return true;}return false;}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(x->x.id().equals(id)).findFirst().orElse(null);}
 /** Where a cart stands at a building: the warehouse's bay (II..), the street before any other building's door. */
 static BlockPos parking(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int bay){
  if(WarehouseStore.is(b)){var bays=WarehouseStore.bays(Math.max(2,BuildingLevels.level(l,e,b)));return WarehouseStore.at(e,b,bays.get(Math.floorMod(bay,bays.size())));}
  return BuildingPlacement.at(e,b,BuildingBlueprints.doorX(b.type()),1,-2);}
 private static boolean near(net.minecraft.world.entity.Entity w,BlockPos p,double r){return w.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5)<=r*r;}
 private static boolean nearChest(ResidentEntity w,BlockPos p){return w.distanceToSqr(p.getX()+1.5,p.getY(),p.getZ()+.5)<=6.25;}
 private static CartEntity cart(ServerLevel l,CompoundTag t,String key){return t.hasUUID(key)&&l.getEntity(t.getUUID(key)) instanceof CartEntity c&&c.isAlive()?c:null;}
 /** A building the cart did not reach recently: its legs go by hand (§2.7). */
 public static boolean noCart(ServerLevel l,UUID building){var until=NO_CART.get(building);return until!=null&&l.getGameTime()<until;}

 // ---------------------------------------------------------------- planning
 /** Plans a trip from a first route: the cart legs follow its direction (to the stock: other sources, the same destination; from a source:
  *  the same source, other destinations), each within 24 blocks of the way from the warehouse to the first point (§2.5). */
 static CompoundTag plan(ServerLevel l,SettlementData.Entry e,Settlement.Building b,UUID puller,String kind,LogisticsRoutes.Route first,boolean hand,CartEntity cart,CartEntity cart2,UUID wolf,BlockPos from){
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putUUID("settlement",e.settlement().id());t.putUUID("warehouse",b.id());t.putUUID("puller",puller);t.putString("kind",kind);
  if(cart!=null)t.putUUID("cart",cart.getUUID());if(cart2!=null)t.putUUID("cart2",cart2.getUUID());if(wolf!=null)t.putUUID("wolf",wolf);t.putLong("cartEpoch",l.getGameTime());
  t.put("legs",new ListTag());t.put("stops",new ListTag());t.putString("stage","plan");t.putInt("bay",WarehouseTrips.bayOf(e,b,puller));
  int level=BuildingLevels.level(l,e,b);
  if(first!=null)addLeg(t,first,hand?0:1);save(l,t);
  if(first!=null){
   boolean toStock=first.destination().id().equals(b.id());var home=LogisticsRoutes.position(e,b);var far=toStock?LogisticsRoutes.position(e,first.source()):LogisticsRoutes.position(e,first.destination());
   Predicate<LogisticsRoutes.Route> along=r->{if(toStock?!r.destination().id().equals(first.destination().id()):!r.source().id().equals(first.source().id()))return false;
    var other=toStock?r.source():r.destination();if(noCart(l,other.id()))return false;return segment(LogisticsRoutes.position(e,other),home,far)<=24;};
   int stacks=cartStacks(level);
   for(int n=cart!=null?(hand?0:1):stacks;n<stacks;n++){var r=LogisticsRoutes.byNeed(l,e,b,from,64,along);if(r==null)break;addLeg(t,r,1);save(l,t);}
   if(cart2!=null)for(int n=0;n<stacks;n++){var r=LogisticsRoutes.byNeed(l,e,b,from,64,along);if(r==null)break;addLeg(t,r,2);save(l,t);}
   stops(l,e,b,t);}
  t.putString("stage","hitch");save(l,t);return t;
 }
 private static void addLeg(CompoundTag t,LogisticsRoutes.Route r,int cart){var leg=new CompoundTag();leg.putUUID("source",r.source().id());leg.putUUID("destination",r.destination().id());leg.put("item",r.item().save(new CompoundTag()));leg.putInt("cart",cart);legs(t).add(leg);t.put("legs",legs(t));}
 /** Distance of a point from the segment a-b (horizontal). */
 static double segment(BlockPos p,BlockPos a,BlockPos b){double dx=b.getX()-a.getX(),dz=b.getZ()-a.getZ(),len=dx*dx+dz*dz;double u=len==0?0:Math.max(0,Math.min(1,((p.getX()-a.getX())*dx+(p.getZ()-a.getZ())*dz)/len));
  double x=a.getX()+u*dx-p.getX(),z=a.getZ()+u*dz-p.getZ();return Math.sqrt(x*x+z*z);}
 /** The stops of a trip: its sources nearest first from the warehouse, then its destinations nearest first from the last source. */
 private static void stops(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  var sources=new LinkedHashSet<UUID>();var dests=new LinkedHashSet<UUID>();for(var raw:legs(t)){var leg=(CompoundTag)raw;sources.add(leg.getUUID("source"));dests.add(leg.getUUID("destination"));}
  var list=new ListTag();var at=LogisticsRoutes.position(e,b);
  for(var group:List.of(sources,dests)){var left=new ArrayList<>(group);boolean take=group==sources;
   while(!left.isEmpty()){final var from=at;var next=left.stream().min(Comparator.comparingDouble(id->{var x=building(e,id);return x==null?Double.MAX_VALUE:LogisticsRoutes.position(e,x).distSqr(from);})).get();left.remove(next);
    var s=new CompoundTag();s.putUUID("building",next);s.putBoolean("take",take);list.add(s);var x=building(e,next);if(x!=null)at=LogisticsRoutes.position(e,x);}}
  t.put("stops",list);t.putInt("stop",0);}
 /** The courier's bay: the first courier of the warehouse B1, the second B2 (IV+), by their order in the village. */
 static int bayOf(SettlementData.Entry e,Settlement.Building b,UUID puller){int i=0;for(var r:e.settlement().residents()){if(r.id().equals(puller))return i;if(courier(e,r.id())&&e.settlement().workplace(r.id()).id().equals(b.id()))i++;}return i;}

 // ---------------------------------------------------------------- the courier
 /** PorterGoal (every 20 ticks) for a courier of a warehouse: a trip with a cart when the warehouse takes carts and one is free; else PorterWork. */
 /** Probe diagnostics (-PwarehouseProbe only): why a courier walks with a hand parcel instead of taking its cart. */
 private static void debug(ResidentEntity w,String why){if(Boolean.getBoolean("villageastra.warehouseProbe"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_WAREHOUSE courier {} {}",w.getUUID(),why);}
 public static void step(ResidentEntity w){
  var l=(ServerLevel)w.level();var e=SettlementData.get(l.getServer()).entry(w.settlementId());if(e==null)return;var b=e.settlement().workplace(w.getUUID());if(!WarehouseStore.is(b))return;
  var t=inspect(l,w.getUUID());
  if(!active(t)){
   // CF-G: a hand parcel begun is finished first; a trip starts only from a free courier.
   boolean parcel=PorterWork.active(PorterWork.inspect(l,w.getUUID()));
   // CF-M: while the wolves carry (the courier posts are closing, CartWolves.relieved) a free courier takes nothing new - the labour office
   // unposts only an empty-handed courier, and the flag is set only while none holds a load; a parcel begun is finished.
   if(!parcel&&CartWolves.relieved(e.settlement().id())&&BuildingLevels.level(l,e,b)>=6){w.getNavigation().stop();w.displayWorkItem(ItemStack.EMPTY);w.workStatus("logistics_idle");return;}
   if(parcel||!WarehouseCarts.open(l,e,b)){PorterWork.step(w);return;}
   var stranded=WarehouseCarts.stranded(l,e,b);
   // CF-K: the stranded cart is claimed at once (travelling with this trip), so a second courier does not go for the same cart.
   if(stranded!=null&&stranded.puller()==null){t=plan(l,e,b,w.getUUID(),"courier",null,true,stranded,null,null,w.blockPosition());t.putString("stage","fetch");save(l,t);stranded.travel(t.getUUID("id"),t.getLong("cartEpoch"));w.workStatus("courier_fetch_cart");return;}
   var cart=WarehouseCarts.free(l,e,b,null);
   if(cart==null){debug(w,"no_free_cart");PorterWork.step(w);if("logistics_idle".equals(w.workStatus())||w.workStatus().isEmpty())w.workStatus("courier_no_cart");return;}
   int level=BuildingLevels.level(l,e,b);var first=LogisticsRoutes.byNeed(l,e,b,w.blockPosition(),LogisticsRoutes.load(level),r->!noCart(l,r.source().id())&&!noCart(l,r.destination().id()));
   if(first==null){debug(w,"no_first");PorterWork.step(w);return;}
   debug(w,"trip "+first.source().type()+"->"+first.destination().type()+" "+first.item());
   CartEntity cart2=null;UUID wolf=null;
   var wolves=CartWolves.available(l,e,b);if(!wolves.isEmpty()){cart2=WarehouseCarts.free(l,e,b,cart.getUUID());if(cart2!=null)wolf=wolves.get(0);}
   t=plan(l,e,b,w.getUUID(),"courier",first,true,cart,cart2,wolf,w.blockPosition());
   cart.travel(t.getUUID("id"),t.getLong("cartEpoch"));if(cart2!=null)cart2.travel(t.getUUID("id"),t.getLong("cartEpoch"));
   w.workStatus(level>=5&&wolf==null&&CartWolves.gate(l,e,b)?"courier_no_wolf":"courier_cart_loading");return;}
  if(!t.getUUID("warehouse").equals(b.id()))return;
  run(l,e,b,w,t);
 }
 private static void move(ResidentEntity w,BlockPos p,CartEntity cart){
  // With the cart: slower, and waiting for it while it lags more than four blocks behind.
  if(cart!=null&&cart.puller()!=null&&cart.puller().equals(w.getUUID())&&cart.distanceTo(w)>4){w.getNavigation().stop();return;}
  w.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,cart!=null&&w.getUUID().equals(cart.puller())?.6:.8);}
 private static void run(ServerLevel l,SettlementData.Entry e,Settlement.Building b,ResidentEntity w,CompoundTag t){
  var cart=cart(l,t,"cart");var cart2=cart(l,t,"cart2");String stage=t.getString("stage");var stops=t.getList("stops",Tag.TAG_COMPOUND);int stop=t.getInt("stop");
  show(l,t,cart,cart2);
  switch(stage){
   case "hitch","fetch"->{
    if(cart==null){if(stage.equals("fetch")||custody(l,t).isEmpty()){finish(l,e,t,null,null);w.workStatus("courier_no_cart");return;}t.putString("stage","return");save(l,t);return;}
    if(!near(w,cart.blockPosition(),CartEntity.REACH-1)){w.workStatus(stage.equals("fetch")?"courier_fetch_cart":"courier_cart_loading");move(w,cart.blockPosition(),null);return;}
    w.getNavigation().stop();var why=CartHitch.hitch(l,e,cart,w);
    if(!why.isEmpty()){w.workStatus("courier_no_cart");
     // A hitch this courier can never take (the warehouse fell below II, the cart went to another village) ends the trip here instead of
     // waiting at the cart for ever: nothing is taken before the hitch (a load goes home in the record); a fetch leaves the cart where it stands.
     if(!why.equals("reach")&&!why.equals("taken")){var id=t.getUUID("id");
      for(var c:new CartEntity[]{cart,cart2})if(c!=null&&id.equals(c.trip())){c.travel(null,0);c.show(0);}
      t.remove("cart");t.remove("cart2");if(!custody(l,t).isEmpty()){t.putString("stage","return");save(l,t);}else finish(l,e,t,null,null);}
     return;}
    cart.travel(t.getUUID("id"),t.getLong("cartEpoch"));
    if(cart2!=null&&t.hasUUID("wolf")){VillageDogs.follow(l,e,t.getUUID("wolf"),w);if(VillageDogs.harness(l,e,t.getUUID("wolf"),cart2)){cart2.settlement(e.settlement().id());cart2.puller(t.getUUID("wolf"));cart2.travel(t.getUUID("id"),t.getLong("cartEpoch"));}
     // No wolf after all: its cart stays at the bay, and the legs planned for it are given up before anything is taken (their reservations go).
     else{VillageDogs.release(l,e,t.getUUID("wolf"));t.remove("wolf");if(t.getUUID("id").equals(cart2.trip())){cart2.travel(null,0);cart2.show(0);}t.remove("cart2");
      for(int i=0;i<legs(t).size();i++){var leg=legs(t).getCompound(i);if(leg.getInt("cart")==2&&!taken(l,t,i))leg.putBoolean("sk",true);}}}
    t.putString("stage",stage.equals("fetch")||stops.isEmpty()?"return":"go");save(l,t);return;}
   case "go"->{
    if(stop>=stops.size()){t.putString("stage","return");save(l,t);return;}
    var target=building(e,stops.getCompound(stop).getUUID("building"));if(target==null){t.putInt("stop",stop+1);save(l,t);return;}
    var park=parking(l,e,target,t.getInt("bay"));var chestAt=LogisticsRoutes.position(e,target);
    if(near(w,park,3)||nearChest(w,chestAt)){if(cart!=null)CartHitch.unhitch(cart);t.putInt("blocked",0);t.putString("stage","work");save(l,t);return;}
    if(blocked(l,t,cart,target)){w.workStatus("courier_cart_blocked");return;}
    w.workStatus(stops.getCompound(stop).getBoolean("take")?"courier_cart_loading":"courier_cart_delivering");w.displayWorkItem(held(l,t,0));move(w,park,cart);return;}
   case "work","shuttle"->{
    var s=stops.getCompound(stop);var target=building(e,s.getUUID("building"));if(target==null){t.putInt("stop",stop+1);t.putString("stage","rehitch");save(l,t);return;}
    var chestAt=LogisticsRoutes.position(e,target);
    if(stage.equals("shuttle")){var c=cart!=null?cart:cart2;if(c!=null&&!near(w,c.blockPosition(),3)){w.workStatus("courier_cart_loading");move(w,c.blockPosition(),null);return;}t.putString("stage","work");save(l,t);return;}
    if(!nearChest(w,chestAt)){w.workStatus(s.getBoolean("take")?"courier_cart_loading":"courier_cart_delivering");w.getNavigation().moveTo(chestAt.getX()+1.5,chestAt.getY(),chestAt.getZ()+.5,.8);return;}
    w.getNavigation().stop();
    int left=work(l,e,b,t,target,s.getBoolean("take"),cart,cart2,chestAt,true);
    if(left<0){w.workStatus("output_full");return;}
    show(l,t,cart,cart2);w.displayWorkItem(held(l,t,0));
    if(left>0){t.putString("stage","shuttle");save(l,t);return;}
    t.putInt("stop",stop+1);t.putString("stage","rehitch");save(l,t);return;}
   case "rehitch"->{
    if(cart==null){t.putString("stage",t.getInt("stop")>=stops.size()?"return":"go");save(l,t);return;}
    if(!near(w,cart.blockPosition(),CartEntity.REACH-1)){move(w,cart.blockPosition(),null);return;}
    w.getNavigation().stop();CartHitch.hitch(l,e,cart,w);t.putString("stage",t.getInt("stop")>=stops.size()?"return":"go");save(l,t);return;}
   case "return"->{
    var park=parking(l,e,b,t.getInt("bay"));var chestAt=LogisticsRoutes.position(e,b);
    boolean with=cart!=null&&w.getUUID().equals(cart.puller());
    if(!(near(w,park,3)||nearChest(w,chestAt))){if(with&&blocked(l,t,cart,b)){w.workStatus("courier_cart_blocked");return;}w.workStatus("courier_cart_returning");move(w,park,with?cart:null);return;}
    if(!custody(l,t).isEmpty()&&!nearChest(w,chestAt)){if(cart!=null&&with)CartHitch.unhitch(cart);w.getNavigation().moveTo(chestAt.getX()+1.5,chestAt.getY(),chestAt.getZ()+.5,.8);return;}
    w.getNavigation().stop();
    if(!back(l,e,b,t)){w.workStatus("output_full");return;}
    finish(l,e,t,cart,cart2);w.displayWorkItem(ItemStack.EMPTY);w.workStatus("logistics_delivered");return;}
   default->{}
  }
 }
 /** §2.7: a cart that has not moved for BLOCKED_TICKS on end turns the trip home; the building is marked for a day; the cart stays (CF-K). */
 private static boolean blocked(ServerLevel l,CompoundTag t,CartEntity cart,Settlement.Building target){
  if(cart==null||!cart.blocked()){if(t.getInt("blocked")!=0){t.putInt("blocked",0);save(l,t);}return false;}
  t.putInt("blocked",t.getInt("blocked")+20);
  // The warehouse itself is never marked: a cart stuck on its way home is left where it stands and fetched back later (CF-K).
  if(t.getInt("blocked")>=BLOCKED_TICKS){if(!target.id().equals(t.getUUID("warehouse")))NO_CART.put(target.id(),l.getGameTime()+DAY);CartHitch.unhitch(cart);cart.travel(null,0);cart.show(0);t.remove("cart");t.putString("stage","return");t.putInt("blocked",0);}
  save(l,t);return true;}
 /** The legs of one stop: taken from its chest (take) or put into it (put). All at once when the cart stands within its reach of the chest
  *  (or for a machine, CF-M), one a walk otherwise. Returns the legs of the stop left, or -1 while the warehouse cannot take a delivery. */
 static int work(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t,Settlement.Building target,boolean take,CartEntity cart,CartEntity cart2,BlockPos chestAt,boolean walker){
  var list=legs(t);int left=0;boolean one=false;
  for(int i=0;i<list.size();i++){var leg=list.getCompound(i);if(leg.getBoolean("sk")||leg.getBoolean("bk"))continue;
   if(take?!leg.getUUID("source").equals(target.id())||taken(l,t,i):!leg.getUUID("destination").equals(target.id())||!taken(l,t,i)||done(l,t,i))continue;
   var c=leg.getInt("cart")==2?cart2:cart;boolean reach=!walker||leg.getInt("cart")==0||c!=null&&c.distanceToSqr(chestAt.getX()+.5,chestAt.getY(),chestAt.getZ()+.5)<=CartEntity.REACH*CartEntity.REACH;
   if(!reach&&one){left++;continue;}
   var item=ItemStack.of(leg.getCompound("item"));
   if(take){var chest=LogisticsRoutes.chest(l,e,target);ItemStack got=ItemStack.EMPTY;
    if(chest!=null)for(int slot=0;slot<chest.getContainerSize();slot++){var st=chest.getItem(slot);
     if(ItemStack.isSameItemSameTags(item,st)&&st.getCount()>=item.getCount()&&LogisticsRoutes.count(chest,x->ItemStack.isSameItemSameTags(x,item))-item.getCount()>=LogisticsRoutes.reserve(target,item)+LogisticsRoutes.constructionReserve(l,e,target,item)){got=WorldJournal.takeAmount(l,op(t,"take",i),chestAt,slot,st.copy(),item.getCount());break;}}
    if(got.isEmpty()){leg.putBoolean("sk",true);}else{leg.putBoolean("tk",true);if(!reach)one=true;}}
   else{boolean ok=WarehouseStore.is(target)?store(l,e,target,op(t,"put",i),item):WorldJournal.deposit(l,op(t,"put",i),chestAt,item);
    if(ok){leg.putBoolean("dn",true);if(!reach)one=true;}
    else if(WarehouseStore.is(target)){save(l,t);return -1;}
    // A destination that takes no more: the stack goes back to the warehouse at the end of the trip.
    else leg.putBoolean("bk",true);}
   save(l,t);}
  return left;}
 /** Into the warehouse's store: at VI straight onto the page of the item's category (WarehouseSort), else wherever it fits. */
 static boolean store(ServerLevel l,SettlementData.Entry e,Settlement.Building b,UUID op,ItemStack item){
  var at=LogisticsRoutes.position(e,b);if(WorldJournal.exists(l,op))return WorldJournal.deposit(l,op,at,item);
  var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)return false;
  if(WarehouseSort.sorting(l,e,b)){int slot=WarehouseSort.depositSlot(chest,item);if(slot>=0)return WorldJournal.putSlot(l,op,at,slot,item);}
  return WorldJournal.deposit(l,op,at,item);}
 /** The end of a trip at the warehouse: what was taken and not delivered goes into the store (back/i); false while it does not fit. */
 static boolean back(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  for(int i=0;i<legs(t).size();i++){var s=held(l,t,i);if(s.isEmpty())continue;
   if(!store(l,e,b,op(t,"back",i),s)){save(l,t);return false;}legs(t).getCompound(i).putBoolean("dn",true);save(l,t);}
  return true;}
 /** How many cart stacks the carts are seen carrying. */
 private static void show(ServerLevel l,CompoundTag t,CartEntity cart,CartEntity cart2){int a=0,c=0;var list=legs(t);
  for(int i=0;i<list.size();i++){if(held(l,t,i).isEmpty())continue;int k=list.getCompound(i).getInt("cart");if(k==1)a++;else if(k==2)c++;}
  if(cart!=null&&cart.shown()!=a)cart.show(a);if(cart2!=null&&cart2.shown()!=c)cart2.show(c);}
 /** Completes a trip: the carts unhitched and no longer travelling, the wolf sent home. */
 static void finish(ServerLevel l,SettlementData.Entry e,CompoundTag t,CartEntity cart,CartEntity cart2){
  for(var c:new CartEntity[]{cart!=null?cart:cart(l,t,"cart"),cart2!=null?cart2:cart(l,t,"cart2")})if(c!=null){c.travel(null,0);c.show(0);c.puller(null);}
  if(t.hasUUID("wolf")&&e!=null)VillageDogs.release(l,e,t.getUUID("wolf"));
  if(t.getString("kind").equals("wolf")&&e!=null)VillageDogs.release(l,e,t.getUUID("puller"));
  if(Boolean.getBoolean("villageastra.warehouseProbe"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_WAREHOUSE trip {} of {} ends at stage {} with {} legs",t.getUUID("id"),t.getUUID("puller"),t.getString("stage"),legs(t).size());
  t.putString("stage","complete");save(l,t);}
 /** JobCargo: the courier's custody went to CargoCustody (its death or the loss of its post); the trip ends where it is. */
 public static void release(ServerLevel l,UUID puller){var t=inspect(l,puller);if(!active(t))return;
  for(int i=0;i<legs(t).size();i++)legs(t).getCompound(i).putBoolean("dn",true);
  var e=SettlementData.get(l.getServer()).entry(t.getUUID("settlement"));finish(l,e,t,null,null);}
 /** The courier's custody for JobCargo when it leaves the trip (death, a new post). */
 public static boolean leaves(ServerLevel l,UUID worker,Resident r,Settlement.Building assigned,boolean death){var t=inspect(l,worker);
  return active(t)&&t.getString("kind").equals("courier")&&(death||r==null||!r.alive()||r.profession()!=Profession.PORTER||assigned==null||!assigned.id().equals(t.getUUID("warehouse")));}

 // ---------------------------------------------------------------- the wolves of VI
 /** Warehouses.tick (every 20 ticks): the running wolf trips of the warehouse, and new ones for its free wolves (VI, Logistics VI, CF-M). */
 public static void tickWolves(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  for(var t:List.copyOf(index(l).values()))if(t.getUUID("warehouse").equals(b.id())&&t.getString("kind").equals("wolf"))wolf(l,e,b,t);
  if(!CartWolves.alone(l,e,b))return;
  for(var wolf:VillageDogs.available(l,e)){if(wolves(l,e,b)>=CartWolves.TEAMS)break;if(active(inspect(l,wolf)))continue;
   var bay=WarehouseStore.at(e,b,WarehouseStore.B1);
   // CF-K: a cart of the warehouse left out on the way (its wolf's hitch slipped, it stuck) is fetched back by a free wolf before new work.
   var stranded=WarehouseCarts.stranded(l,e,b);
   if(stranded!=null&&stranded.puller()==null){if(!VillageDogs.harness(l,e,wolf,stranded))continue;
    var t=plan(l,e,b,wolf,"wolf",null,false,stranded,null,null,bay);stranded.settlement(e.settlement().id());stranded.travel(t.getUUID("id"),t.getLong("cartEpoch"));t.putString("stage","fetch");save(l,t);continue;}
   var cart=WarehouseCarts.free(l,e,b,null);if(cart==null)break;
   var first=LogisticsRoutes.byNeed(l,e,b,bay,64,r->!noCart(l,r.source().id())&&!noCart(l,r.destination().id()));if(first==null)break;
   if(!VillageDogs.harness(l,e,wolf,cart))continue;
   // The trip begins at the cart: the wolf goes to it and takes the hitch (stage fetch), then the stops.
   var t=plan(l,e,b,wolf,"wolf",first,false,cart,null,null,bay);cart.settlement(e.settlement().id());cart.travel(t.getUUID("id"),t.getLong("cartEpoch"));
   t.putString("stage","fetch");save(l,t);}
 }
 private static void wolf(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  var wolf=t.getUUID("puller");var cart=cart(l,t,"cart");var body=l.getEntity(wolf);
  // CF-L: a wolf dead where it is loaded, or gone from its cart's world for LOST_TICKS: the load falls by the cart in one drop.
  boolean dead=body!=null&&!body.isAlive()||body instanceof net.minecraft.world.entity.LivingEntity le&&le.isDeadOrDying();
  // The wolf goes to its cart and takes the hitch (nothing is taken before it: a dead wolf or a gone cart only ends the trip).
  if(t.getString("stage").equals("fetch")){
   if(dead||cart==null){finish(l,e,t,null,null);return;}
   // A cart the wolf cannot come up to (no way there) is given up after a while; the wolf goes back to its kennel and the cart is tried again.
   if(!VillageDogs.near(l,e,wolf,cart.blockPosition(),3)){t.putInt("away",t.getInt("away")+20);if(t.getInt("away")>=3*LOST_TICKS){finish(l,e,t,null,null);return;}VillageDogs.send(l,e,wolf,cart.blockPosition());save(l,t);return;}
   cart.settlement(e.settlement().id());cart.puller(wolf);cart.travel(t.getUUID("id"),t.getLong("cartEpoch"));
   t.putString("stage",t.getList("stops",Tag.TAG_COMPOUND).isEmpty()?"return":"go");t.putInt("away",0);save(l,t);return;}
  if(!dead&&cart!=null&&!VillageDogs.near(l,e,wolf,cart.blockPosition(),CartEntity.LOST))t.putInt("away",t.getInt("away")+20);else if(!dead)t.putInt("away",0);
  if(!dead&&body!=null&&cart!=null&&cart.blocked())t.putInt("blocked",t.getInt("blocked")+20);else t.putInt("blocked",0);
  // Only a wolf that is dead or gone (not in the world where its cart is) is lost: its load falls by the cart.
  if(dead||body==null&&t.getInt("away")>=LOST_TICKS){drop(l,e,t,cart);return;}
  var stops=t.getList("stops",Tag.TAG_COMPOUND);int stop=t.getInt("stop");
  // §2.7 for a wolf that is alive: its cart stuck (BLOCKED_TICKS on end) or left behind (the hitch slipped, LOST_TICKS) stays where it stands
  // and is fetched back later (CF-K); the building is marked for a day, and the load goes home in the record - nothing is put on the ground.
  if(cart!=null&&(t.getInt("away")>=LOST_TICKS||t.getInt("blocked")>=BLOCKED_TICKS)){
   if(t.getString("stage").equals("go")&&stop<stops.size()){var target=building(e,stops.getCompound(stop).getUUID("building"));if(target!=null&&!target.id().equals(b.id()))NO_CART.put(target.id(),l.getGameTime()+DAY);}
   if(wolf.equals(cart.puller()))cart.puller(null);cart.travel(null,0);cart.show(0);t.remove("cart");t.putInt("away",0);t.putInt("blocked",0);cart=null;}
  // Without its cart the wolf loads nothing more: an empty trip ends, a load goes home.
  if(cart==null){if(custody(l,t).isEmpty()){finish(l,e,t,null,null);return;}if(!t.getString("stage").equals("return")){t.putString("stage","return");save(l,t);}}
  show(l,t,cart,null);
  if(t.getString("stage").equals("go")&&stop<stops.size()){
   var target=building(e,stops.getCompound(stop).getUUID("building"));if(target==null){t.putInt("stop",stop+1);save(l,t);return;}
   var park=parking(l,e,target,0);
   if(!VillageDogs.near(l,e,wolf,park,3)){VillageDogs.send(l,e,wolf,park);save(l,t);return;}
   // CF-M: the warehouse's machine works the chest while the cart stands at the building - no body, no teleport.
   if(work(l,e,b,t,target,stops.getCompound(stop).getBoolean("take"),cart,null,LogisticsRoutes.position(e,target),false)<0){save(l,t);return;}
   t.putInt("stop",stop+1);save(l,t);return;}
  var home=parking(l,e,b,0);
  if(!VillageDogs.near(l,e,wolf,home,3)){t.putString("stage","return");VillageDogs.send(l,e,wolf,home);save(l,t);return;}
  if(!back(l,e,b,t))return;
  finish(l,e,t,cart,null);
 }
 /** CF-L: the custody of a lost wolf's trip in one drop by its cart (or where it last stood), the cart freed first. */
 private static void drop(ServerLevel l,SettlementData.Entry e,CompoundTag t,CartEntity cart){
  var items=custody(l,t);var at=cart!=null?cart.blockPosition():l.getEntity(t.getUUID("puller"))!=null?l.getEntity(t.getUUID("puller")).blockPosition():null;
  if(cart!=null){cart.show(0);cart.travel(null,0);cart.puller(null);}
  if(!items.isEmpty()){if(at==null)return;var spot=CartHitch.parking(l,at);if(spot==null)spot=at;var contents=new ListTag();for(var s:items)contents.add(s.save(new CompoundTag()));
   if(!WorldJournal.dropCargo(l,Settlement.childId(t.getUUID("id"),"drop"),spot,e.settlement().id(),contents))return;}
  for(int i=0;i<legs(t).size();i++)legs(t).getCompound(i).putBoolean("dn",true);
  finish(l,e,t,null,null);}

 // ---------------------------------------------------------------- the card
 /** The warehouse's part of a building card: couriers (posts, posted, status, load), carts, wolves, the store, the sorting. */
 public static CompoundTag card(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var c=new CompoundTag();int level=BuildingLevels.level(l,e,b);
  c.putInt("posts",Population.slots(e.settlement(),b));c.putInt("postsRow",Staff.slots(WarehouseStore.TYPE,b.level()));c.putInt("posted",posted(e,b));c.putBoolean("relieved",CartWolves.relieved(e.settlement().id()));
  c.putInt("load",LogisticsRoutes.load(level));c.putInt("stacks",WarehouseCarts.open(l,e,b)?cartStacks(level):0);
  var list=new ListTag();
  for(var r:e.settlement().residents()){if(!courier(e,r.id())||!e.settlement().workplace(r.id()).id().equals(b.id()))continue;var q=new CompoundTag();
   q.putString("name",l.getEntity(r.id()) instanceof ResidentEntity npc?npc.getName().getString():"");q.putString("status",l.getEntity(r.id()) instanceof ResidentEntity npc?npc.workStatus():"");
   var t=inspect(l,r.id());q.putInt("carrying",custody(l,t).size()+(PorterWork.active(PorterWork.inspect(l,r.id()))?1:0));q.putBoolean("cart",active(t)&&t.hasUUID("cart"));list.add(q);}
  c.put("list",list);
  c.putString("carts",!WarehouseCarts.open(l,e,b)?"level":WarehouseCarts.have(l,b.id())<WarehouseCarts.needed(l,e,b)?(WarehouseCarts.maker(l,e)?"ordered":"no_maker"):"ok");
  c.putInt("cartsHave",WarehouseCarts.have(l,b.id()));c.putInt("cartsNeeded",WarehouseCarts.needed(l,e,b));
  c.putString("wolves",CartWolves.refusal(l,e,b));c.putInt("wolfTrips",wolves(l,e,b));
  var chest=LogisticsRoutes.chest(l,e,b);int used=0;if(chest!=null)for(int i=0;i<chest.getContainerSize();i++)if(!chest.getItem(i).isEmpty())used++;
  c.putInt("slots",chest==null?0:chest.getContainerSize());c.putInt("used",used);c.putInt("pages",chest==null?0:chest.getContainerSize()/WarehouseStore.PAGE);c.putInt("levelSlots",WarehouseStore.slots(level));
  c.putBoolean("sorting",WarehouseSort.sorting(l,e,b));c.putInt("categories",WarehouseSort.CATEGORIES-1);c.putInt("unsorted",chest==null?0:WarehouseSort.unsorted(chest));c.putInt("level",level);
  return c;}
}
