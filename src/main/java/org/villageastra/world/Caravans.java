package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
/** AD-039: inter-village trade contracts carried by a real caravaneer. The contract record is the only owner of the cargo and of the caravaneer's position while travelling. */
public final class Caravans {
 public static final int CARRY=64,PROPOSE_EVERY=1200,MATERIALIZE_RADIUS=48,ARRIVE=6,GATE=14;public static final double SPEED=0.2;
 public static final String PROPOSED="proposed",ACCEPTED="accepted",SECURED="secured",TRANSIT="in_transit",DELIVERED="delivered",PARTIAL="partial",LOST="lost",CANCELLED="cancelled",RETURNING="returning",CLOSED="closed";
 /** AD-111: relevant far villages whose 1200-tick pass waits for the touch budget, in arrival order; memory only, rebuilt at the next pass. */
 private static final Set<UUID> PENDING=new LinkedHashSet<>();
 private static final Map<UUID,CompoundTag> CONTRACTS=new LinkedHashMap<>();private static final Map<UUID,Long> EPOCHS=new HashMap<>();private static final Map<UUID,CompoundTag> STOCK=new HashMap<>();private static MinecraftServer loaded;
 public static final String EPOCH="AstraCaravanEpoch";
 /** AD-103: the kind of trip that fetches home a cart the village left on the road, and how many seconds it looks for it there. */
 public static final String RECOVER="recover";public static final int RECOVER_SEARCH=30;
 private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> CART_TICKET=net.minecraft.server.level.TicketType.create("villageastra_cart",Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong),100);
 private Caravans(){}
 private static Path dir(MinecraftServer s){return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-caravans");}
 public static void clear(){CONTRACTS.clear();EPOCHS.clear();STOCK.clear();PENDING.clear();loaded=null;}
 private static synchronized void ensure(MinecraftServer s){
  if(loaded==s)return;CONTRACTS.clear();EPOCHS.clear();STOCK.clear();loaded=s;var d=dir(s);if(!Files.isDirectory(d))return;
  try(var files=Files.list(d)){for(var f:files.sorted().toList()){var name=f.getFileName().toString();if(!name.endsWith(".bin"))continue;var t=NbtRecord.read(f);
   if(name.equals("epochs.bin")){for(var raw:t.getList("epochs",Tag.TAG_COMPOUND)){var e=(CompoundTag)raw;EPOCHS.put(e.getUUID("resident"),e.getLong("epoch"));}}
   else if(name.startsWith("stock-"))STOCK.put(t.getUUID("village"),t);else CONTRACTS.put(t.getUUID("id"),t);}}catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 public static Collection<CompoundTag> contracts(MinecraftServer s){ensure(s);return Collections.unmodifiableCollection(CONTRACTS.values());}
 public static CompoundTag contract(MinecraftServer s,UUID id){ensure(s);return CONTRACTS.get(id);}
 public static void update(MinecraftServer s,CompoundTag t){save(s,t);}
 private static void save(MinecraftServer s,CompoundTag t){ensure(s);CONTRACTS.put(t.getUUID("id"),t);NbtRecord.write(dir(s).resolve(t.getUUID("id")+".bin"),t);}
 private static void stamp(CompoundTag t,String state,long now){t.putString("state",state);t.getCompound("stamps").putLong(state,now);t.put("stamps",t.getCompound("stamps"));}
 static boolean open(CompoundTag t){var s=t.getString("state");return !s.equals(CLOSED)&&!s.equals(CANCELLED);}
 /** Active trip of a resident: the contract whose caravaneer is travelling (secured and later, not closed). */
 public static CompoundTag trip(MinecraftServer s,UUID resident){ensure(s);for(var t:CONTRACTS.values())if(open(t)&&t.getUUID("caravaneer").equals(resident)&&travelling(t))return t;return null;}
 static boolean travelling(CompoundTag t){var s=t.getString("state");return s.equals(TRANSIT)||s.equals(RETURNING)||s.equals(DELIVERED)||s.equals(PARTIAL)||(s.equals(CLOSED)&&t.getBoolean("needsEntity"));}
 public static long epoch(MinecraftServer s,UUID resident){ensure(s);return EPOCHS.getOrDefault(resident,0L);}
 static long bump(MinecraftServer s,UUID resident){ensure(s);long next=epoch(s,resident)+1;EPOCHS.put(resident,next);var list=new ListTag();EPOCHS.forEach((id,e)->{var x=new CompoundTag();x.putUUID("resident",id);x.putLong("epoch",e);list.add(x);});var tag=new CompoundTag();tag.put("epochs",list);NbtRecord.write(dir(s).resolve("epochs.bin"),tag);return next;}
 /** A loaded resident entity whose caravan epoch is older than the registry is a stale copy of a travelling identity and must not join. */
 public static boolean stale(MinecraftServer s,ResidentEntity npc){long registered=epoch(s,npc.getUUID());return registered>0&&npc.getPersistentData().getLong(EPOCH)<registered;}
 // ---- stock snapshots ------------------------------------------------------------------------------
 static Settlement.Building stock(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.type().equals("warehouse")).findFirst().orElseGet(()->Workshops.hall(e));}
 public static Settlement.Building yard(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElse(null);}
 /** Unreserved surplus of the stock chest, taken while its chunk is loaded; proposals for other villages use this snapshot. */
 public static CompoundTag snapshot(ServerLevel l,SettlementData.Entry e,long now){
  var b=stock(e);var chest=b==null?null:LogisticsRoutes.chest(l,e,b);if(chest==null)return null;var wants=Workshops.wants(l,e);var counts=new LinkedHashMap<Item,Integer>();
  for(int i=0;i<chest.getContainerSize();i++){var s=chest.getItem(i);if(!s.isEmpty()&&Trade.plain(s)&&Trade.price(s.getItem())!=null)counts.merge(s.getItem(),s.getCount(),Integer::sum);}
  var items=new CompoundTag();for(var c:counts.entrySet()){var stack=new ItemStack(c.getKey());int keep=LogisticsRoutes.constructionReserve(l,e,b,stack)+LogisticsRoutes.reserve(b,stack)+reservedOut(l.getServer(),e.settlement().id(),c.getKey());for(var w:wants)if(w.matches(stack))keep+=w.count();
   // The settlement's own food stock target stays at home even when it is currently full.
   if(c.getKey()==Items.BREAD)keep+=16+2*(int)e.settlement().residents().stream().filter(Resident::alive).count();if(c.getKey()==Items.WHEAT_SEEDS)keep+=16;int free=c.getValue()-keep;if(free>0)items.putInt(BuiltInRegistries.ITEM.getKey(c.getKey()).toString(),free);}
  var t=new CompoundTag();t.putUUID("village",e.settlement().id());t.putLong("tick",now);t.put("items",items);STOCK.put(e.settlement().id(),t);NbtRecord.write(dir(l.getServer()).resolve("stock-"+e.settlement().id()+".bin"),t);return t;
 }
 private static int reservedOut(MinecraftServer s,UUID village,Item item){int n=0;for(var t:contracts(s))if(open(t)&&t.getUUID("source").equals(village)&&(t.getString("state").equals(PROPOSED)||t.getString("state").equals(ACCEPTED))&&t.getString("item").equals(BuiltInRegistries.ITEM.getKey(item).toString()))n+=t.getInt("count");return n;}
 private static ResidentEntity caravaneerEntity(ServerLevel l,UUID id){return l.getEntity(id) instanceof ResidentEntity npc?npc:null;}
 static Resident freeCaravaneer(MinecraftServer s,SettlementData.Entry e){
  for(var r:e.settlement().residents()){if(!r.alive()||r.profession()!=Profession.CARAVANEER)continue;var b=e.settlement().workplace(r.id());if(b==null||!b.type().equals("caravan"))continue;
   boolean busy=contracts(s).stream().anyMatch(t->open(t)&&t.getUUID("caravaneer").equals(r.id()));if(!busy)return r;}
  return null;
 }
 // ---- the cart (ROAD-008, AD-085) -----------------------------------------------------------------
 /** The village's cart standing at its caravan yard (or its stock), empty and pulled by nobody — the one a trip may take. */
 public static CartEntity freeCart(ServerLevel l,SettlementData.Entry e){
  var yard=yard(e);var at=yard!=null?BuildingPlacement.origin(e,yard):LogisticsRoutes.position(e,stock(e));if(!l.hasChunkAt(at))return null;
  return CartHitch.near(l,e,at,16,c->c.home()==null&&!c.travelling()&&c.isEmpty()&&c.puller()==null);
 }
 /** The cart travels with the record of the trip, as the goods do: one cart, in exactly one place. */
 static void stowCart(CompoundTag t,CartEntity cart){var tag=new CompoundTag();cart.saveWithoutId(tag);tag.remove("UUID");t.put("cart",tag);cart.discard();}
 /** Puts the travelling cart back into the world at a spot, and takes it out of the record. */
 static CartEntity placeCart(ServerLevel l,CompoundTag t,BlockPos near){
  if(!t.contains("cart"))return null;var at=CartHitch.parking(l,near);if(at==null)return null;
  var cart=new CartEntity(VillageAstra.CART.get(),l);cart.load(t.getCompound("cart"));cart.puller(null);cart.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);
  if(!l.addFreshEntity(cart))return null;t.remove("cart");return cart;
 }
 /** Home from a trip or never gone: the cart is put back at the caravan yard of its village. */
 /** AD-112: what one caravaneer carries at this working level of the caravan yard (CARRY at level I); a cart doubles it. */
 public static int carry(int level){return org.villageastra.domain.CoreEffects.value("caravan","carry",level);}
 /** What a caravaneer of this village carries, by its best caravan yard (a village without one: CARRY). */
 public static int carry(ServerLevel l,SettlementData.Entry source){return source==null?CARRY:carry(BuildingLevels.best(l,source,"caravan"));}
 static void parkCart(ServerLevel l,CompoundTag t,SettlementData.Entry e){
  if(!t.contains("cart")||e==null)return;var yard=yard(e);var at=yard!=null?BuildingPlacement.origin(e,yard):LogisticsRoutes.position(e,stock(e));
  TouchLoad.force(l,at);placeCart(l,t,at);
 }
 /** ROAD-008 (T215): on the mayor's order a trip whose cart cannot pass leaves it where it stands and goes on on foot. The caravaneer
  *  keeps what one pair of hands carries; the rest of the load stays in the cart's hold on the spot — in one place, never lost, never
  *  doubled — until the village fetches the cart. Returns the number of items left in the cart, or -1 when there is no cart to leave. */
 public static int leaveCart(ServerLevel l,CompoundTag t){
  if(CaravanDogs.isDog(t)&&t.hasUUID("leaderTrip")){var parent=contract(l.getServer(),t.getUUID("leaderTrip"));if(parent!=null&&open(parent))t=parent;}
  var dog=CaravanDogs.child(l.getServer(),t);int extra=dog!=null?leaveOneCart(l,dog):0;
  int own=leaveOneCart(l,t);return own<0?extra>0?extra:own:own+Math.max(0,extra);
 }
 private static int leaveOneCart(ServerLevel l,CompoundTag t){
  if(!t.contains("cart"))return -1;var state=t.getString("state");if(!state.equals(TRANSIT)&&!state.equals(RETURNING))return -1;
  var npc=t.getBoolean("materialized")?l.getEntity(t.getUUID("caravaneer")):null;
  var at=npc!=null?npc.blockPosition():position(l,t,t.getDouble("progress"));
  var cargo=t.getList("cargo",Tag.TAG_COMPOUND);var keep=new ListTag();var rest=new ArrayList<ItemStack>();int carried=0;int hands=CaravanDogs.isDog(t)||CaravanHorses.isHorse(t)?0:carry(l,SettlementData.get(l.getServer()).entry(t.getUUID("source")));
  for(var raw:cargo){var st=ItemStack.of((CompoundTag)raw);int take=Math.max(0,Math.min(st.getCount(),hands-carried));
   if(take>0){keep.add(st.copyWithCount(take).save(new CompoundTag()));carried+=take;}
   if(st.getCount()>take)rest.add(st.copyWithCount(st.getCount()-take));}
  if(!l.hasChunkAt(at))l.getChunkAt(at);
  // The travelling body, if a player sees one, goes: the cart that stays is an ordinary cart of the village, standing on the road.
  for(var body:l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(at).inflate(CartEntity.LOST),c->t.getUUID("id").equals(c.trip())))body.discard();
  // The cart is left at the roadside, not on the road: parked on the route line it would bar the way home (cart-recovery-client-02).
  var cart=placeCart(l,t,roadside(l,t,at));if(cart==null)return -1;t.putUUID("leftCart",cart.getUUID());t.remove("recovery");t.remove("cartFetched");t.remove("cartGone");
  int left=0;for(var st:rest){var over=CartHitch.put(cart,st);left+=st.getCount()-over.getCount();
   if(!over.isEmpty()){var drop=new ItemEntity(l,cart.getX(),cart.getY()+.5,cart.getZ(),over);drop.setDefaultPickUpDelay();l.addFreshEntity(drop);}}
  t.put("cargo",keep);t.putInt("leftInCart",t.getInt("leftInCart")+left);t.putLong("cartLeftAt",at.asLong());save(l.getServer(),t);
  return left;
 }
 /** A cart standing at the yard with goods in its hold (what a full stock could not take when it came home) is emptied into the stock
  *  as room appears, through the journal, so it is a free cart for the next trip again. Returns the items moved. */
 public static int emptyYardCarts(ServerLevel l,SettlementData.Entry e,long now){
  var yard=yard(e);var at=yard!=null?BuildingPlacement.origin(e,yard):LogisticsRoutes.position(e,stock(e));if(!l.hasChunkAt(at))return 0;
  var to=LogisticsRoutes.position(e,stock(e));int moved=0;
  for(var cart:l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(at).inflate(16),c->e.settlement().id().equals(c.settlement())&&c.home()==null&&!c.travelling()&&c.puller()==null&&!c.isEmpty()))
   for(int slot=0;slot<cart.getContainerSize();slot++){var st=cart.getItem(slot);if(st.isEmpty())continue;
    moved+=CartHitch.unload(l,cart,to,slot,st.getCount(),Settlement.childId(e.settlement().id(),"yard-unload/"+cart.getUUID()+"/"+now+"/"+slot));}
  return moved;
 }
 /** A spot beside the route at a point of it: three blocks to either side, where a cart may stand; the point itself when neither will do. */
 static BlockPos roadside(ServerLevel l,CompoundTag t,BlockPos at){
  var a=from(t);var b=to(t);double dx=b.getX()-a.getX(),dz=b.getZ()-a.getZ(),n=Math.max(1,Math.sqrt(dx*dx+dz*dz));
  for(int side:new int[]{1,-1}){var aside=at.offset((int)Math.round(-dz/n*3*side),0,(int)Math.round(dx/n*3*side));var spot=CartHitch.parking(l,aside);
   if(spot!=null&&offRoute(t,spot)>=2)return spot;}
  return at;
 }
 /** How far a spot lies from the line of the route, in blocks. */
 public static double offRoute(CompoundTag t,BlockPos p){var a=from(t);var b=to(t);double dx=b.getX()-a.getX(),dz=b.getZ()-a.getZ(),n=Math.max(1,Math.sqrt(dx*dx+dz*dz));
  return Math.abs((p.getX()-a.getX())*dz-(p.getZ()-a.getZ())*dx)/n;}
 /** ROAD-008 (AD-103): a cart the village left on the road is fetched home, with what its hold still carries, by a free caravaneer of
  *  the village — a trip of its own out to the cart and back, run like any trip: a record while nobody watches, a walk when somebody does. */
 public static CompoundTag proposeRecovery(ServerLevel l,SettlementData.Entry e,long now){
  var s=l.getServer();
  for(var left:List.copyOf(contracts(s))){
   if(!left.hasUUID("leftCart")||!left.getUUID("source").equals(e.settlement().id())||left.getBoolean("cartFetched")||left.getBoolean("cartGone")||left.hasUUID("recovery"))continue;
   var caravaneer=freeCaravaneer(s,e);if(caravaneer==null)return null;
   var at=BlockPos.of(left.getLong("cartLeftAt"));
   var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",Settlement.childId(left.getUUID("id"),"recovery/"+now));t.putString("kind",RECOVER);
   t.putUUID("source",e.settlement().id());t.putUUID("destination",e.settlement().id());t.putString("dimension",left.getString("dimension"));
   t.putUUID("caravaneer",caravaneer.id());t.putUUID("fetchCart",left.getUUID("leftCart"));t.putUUID("trip",left.getUUID("id"));t.putString("item","");
   t.put("cargo",new ListTag());t.put("stamps",new CompoundTag());t.putInt("count",0);t.putInt("takes",0);t.putInt("drops",0);t.putInt("delivered",0);t.putInt("lost",0);
   t.putLong("from",gate(l,e,at).asLong());t.putLong("to",at.asLong());t.putDouble("progress",0);t.putBoolean("materialized",false);stamp(t,PROPOSED,now);save(s,t);
   left.putUUID("recovery",t.getUUID("id"));save(s,left);return t;}
  return null;
 }
 /** At the cart: it is taken up with its hold, or — if it is gone (a player took it) — the caravaneer turns home empty-handed. The cart's
  *  chunk is held loaded while its entities come in. */
 private static void pickUp(ServerLevel l,CompoundTag t,long now){
  var s=l.getServer();var at=BlockPos.of(t.getLong("to"));var chunk=new net.minecraft.world.level.ChunkPos(at);
  l.getChunkSource().addRegionTicket(CART_TICKET,chunk,1,chunk);l.getChunkAt(at);
  var left=contract(s,t.getUUID("trip"));
  var cart=l.getEntity(t.getUUID("fetchCart")) instanceof CartEntity c&&!c.isRemoved()&&c.puller()==null&&!c.travelling()?c:null;
  if(cart==null){int searches=t.getInt("searches")+1;t.putInt("searches",searches);
   if(searches<RECOVER_SEARCH){save(s,t);return;}
   if(left!=null){left.putBoolean("cartGone",true);save(s,left);}}
  else{stowCart(t,cart);if(left!=null){left.putBoolean("cartFetched",true);save(s,left);}
   var npc=t.getBoolean("materialized")?caravaneerEntity(l,t.getUUID("caravaneer")):null;if(npc!=null)cartBody(l,t,npc,npc.blockPosition());}
  t.putBoolean("home",true);t.putDouble("progress",0);stamp(t,RETURNING,now);save(s,t);
 }
 /** Home again: what the fetched cart's hold carries goes to the stock like any returned goods, and the empty cart to the yard. */
 private static void emptyHold(ServerLevel l,CompoundTag t){
  if(!t.contains("cart"))return;var cart=new CartEntity(VillageAstra.CART.get(),l);cart.load(t.getCompound("cart"));var cargo=t.getList("cargo",Tag.TAG_COMPOUND);
  for(int i=0;i<cart.getContainerSize();i++){var st=cart.getItem(i);if(!st.isEmpty())cargo.add(st.copy().save(new CompoundTag()));}
  cart.clearContent();var tag=new CompoundTag();cart.saveWithoutId(tag);tag.remove("UUID");t.put("cart",tag);t.put("cargo",cargo);
 }
 // ---- the 1200-tick pass (AD-111) --------------------------------------------------------------------
 /** Every chest and spot the pass reads: the stock and hall chests, every warehouse and clinic chest, and the yard where carts stand. */
 public static List<BlockPos> touchPoints(SettlementData.Entry e){
  var ps=new LinkedHashSet<BlockPos>();var st=stock(e);if(st!=null)ps.add(LogisticsRoutes.position(e,st));var hall=Workshops.hall(e);if(hall!=null)ps.add(LogisticsRoutes.position(e,hall));
  for(var b:e.settlement().buildings())if(b.type().equals("warehouse")||b.type().equals("clinic"))ps.add(LogisticsRoutes.position(e,b));
  var yard=yard(e);if(yard!=null)ps.add(BuildingPlacement.origin(e,yard));return List.copyOf(ps);
 }
 private static ServerLevel level(MinecraftServer s,SettlementData.Entry e){return s.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new ResourceLocation(e.dimension())));}
 /** Called every 20 ticks. At each PROPOSE_EVERY tick loaded villages and villages that do not matter to the player run at once, exactly as before (skipped when there
  *  is no snapshot); a relevant village with an unloaded chest waits in PENDING. PENDING drains in order under the touch budget: OK runs the village, FAILED drops it
  *  until the next pass (one broken chunk never blocks the rest), DEFERRED stops the drain for this call since the budget is spent for everyone behind it too. */
 public static void pass(MinecraftServer s,long now){
  var data=SettlementData.get(s);
  if(now%PROPOSE_EVERY==0)for(var e:List.copyOf(data.entries())){var l=level(s,e);if(l==null)continue;var id=e.settlement().id();
   if(!touchPoints(e).stream().allMatch(l::hasChunkAt)&&TouchLoad.relevant(s,e)){PENDING.add(id);continue;}
   PENDING.remove(id);passVillage(l,e,now);}
  for(var it=PENDING.iterator();it.hasNext();){var e=data.entry(it.next());var l=e==null?null:level(s,e);if(l==null){it.remove();continue;}
   var touch=TouchLoad.ensureAll(l,touchPoints(e));if(touch==TouchLoad.Touch.DEFERRED)break;it.remove();if(touch==TouchLoad.Touch.OK)passVillage(l,e,now);}
 }
 /** One village's pass, with its chests already loaded or touched by the caller: snapshot, trade and migration proposals, cart recovery and yard carts. */
 public static void passVillage(ServerLevel l,SettlementData.Entry e,long now){
  if(snapshot(l,e,now)!=null){propose(l,e,now);proposeMigration(l,e,now);}
  proposeRecovery(l,e,now);emptyYardCarts(l,e,now);
 }
 // ---- state machine -------------------------------------------------------------------------------
 /** Destination demand that the village lacks, matched to the nearest other village whose snapshot has surplus and a free caravaneer. */
 public static CompoundTag propose(ServerLevel l,SettlementData.Entry destination,long now){return propose(l,destination,null,now);}
 /** With a named partner only that village is considered (explicit mayor contract or a test). */
 public static CompoundTag propose(ServerLevel l,SettlementData.Entry destination,SettlementData.Entry partner,long now){
  var s=l.getServer();var wants=Workshops.wants(l,destination);
  for(var want:wants){
   for(var source:(partner!=null?List.of(partner):SettlementData.get(s).entries().stream().filter(x->!x.settlement().id().equals(destination.settlement().id())&&x.dimension().equals(destination.dimension())).sorted(Comparator.comparingDouble(x->x.center().distSqr(destination.center()))).toList())){
    var snap=STOCK.get(source.settlement().id());if(snap==null)continue;
    // AD-111: an automatic proposal never trusts a stock snapshot older than two passes (a far village nobody touched); an explicit partner keeps the old snapshot.
    if(partner==null&&now-snap.getLong("tick")>2L*PROPOSE_EVERY)continue;var horse=freeCart(l,source)!=null?VillageHorses.free(l,source):null;var caravaneer=freeCaravaneer(s,source);if(caravaneer==null&&horse==null)continue;
    // AD-100: no caravan goes between villages on hostile terms.
    if(Relations.score(s,source.settlement().id(),destination.settlement().id())<=Relations.HOSTILE)continue;
    // AD-160 II: of its own accord a village trades only with one it has really met and that keeps a centre of its own; a mayor's
    // own contract (a named partner) is the mayor's business and is left alone.
    if(partner==null&&!TradeLadder.mayTrade(l,destination,source))continue;
    for(var key:snap.getCompound("items").getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));if(!want.matches(new ItemStack(item)))continue;
     boolean duplicate=contracts(s).stream().anyMatch(t->open(t)&&t.getUUID("destination").equals(destination.settlement().id())&&t.getString("item").equals(key));if(duplicate)continue;
     int carry=carry(l,source)*(freeCart(l,source)!=null?2:1)+CaravanDogs.extraCapacity(l,source);
     int count=Math.min(carry,Math.min(want.count(),snap.getCompound("items").getInt(key)-reservedOut(s,source.settlement().id(),item)));if(count<=0)continue;var price=Trade.price(item);
     var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",Settlement.childId(destination.settlement().id(),"caravan/"+key+"/"+now));t.putString("kind","trade");t.putUUID("source",source.settlement().id());t.putUUID("destination",destination.settlement().id());t.putString("dimension",destination.dimension());
     t.putString("item",key);t.putInt("count",count);t.putInt("price",price.coins());t.putInt("per",price.per());t.putUUID("caravaneer",horse!=null?horse.getUUID():caravaneer.id());if(horse!=null)t.putBoolean("horseTrip",true);t.put("cargo",new ListTag());t.put("stamps",new CompoundTag());t.putInt("takes",0);t.putInt("drops",0);t.putInt("delivered",0);t.putInt("lost",0);
     t.putLong("from",gate(l,source,destination.center()).asLong());t.putLong("to",gate(l,destination,source.center()).asLong());t.putDouble("progress",0);t.putBoolean("materialized",false);stamp(t,PROPOSED,now);save(s,t);return t;}
   }
  }
  return null;
 }
 /** MULTI-003: one homeless adult of an overcrowded village travels to the nearest village with a free bed. The bed is reserved before departure; the identity is moved, never cloned. */
 public static CompoundTag proposeMigration(ServerLevel l,SettlementData.Entry source,long now){return proposeMigration(l,source,null,now);}
 public static CompoundTag proposeMigration(ServerLevel l,SettlementData.Entry source,SettlementData.Entry partner,long now){
  var s=l.getServer();var migrant=source.settlement().residents().stream().filter(r->r.alive()&&r.life()==Resident.Life.ADULT&&r.home()==null&&r.profession()!=Profession.MAYOR).filter(r->contracts(s).stream().noneMatch(t->open(t)&&t.getUUID("caravaneer").equals(r.id()))).findFirst().orElse(null);
  if(migrant==null)return null;
  for(var d:(partner!=null?List.of(partner):SettlementData.get(s).entries().stream().filter(x->!x.settlement().id().equals(source.settlement().id())&&x.dimension().equals(source.dimension())).sorted(Comparator.comparingDouble(x->x.center().distSqr(source.center()))).toList())){
   var home=freeHome(s,d);if(home==null)continue;
   var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",Settlement.childId(migrant.id(),"migration/"+now));t.putString("kind","migration");t.putUUID("source",source.settlement().id());t.putUUID("destination",d.settlement().id());t.putString("dimension",source.dimension());
   t.putUUID("caravaneer",migrant.id());t.putUUID("home",home.id());t.put("cargo",new ListTag());t.put("stamps",new CompoundTag());t.putInt("count",0);
   t.putLong("from",gate(l,source,d.center()).asLong());t.putLong("to",gate(l,d,source.center()).asLong());t.putDouble("progress",0);t.putBoolean("materialized",false);stamp(t,PROPOSED,now);save(s,t);return t;}
  return null;
 }
 /** A usable home with a free bed after counting open migration reservations. */
 static Settlement.Home freeHome(MinecraftServer s,SettlementData.Entry d){
  for(var h:d.settlement().homes()){if(!h.usable())continue;long reserved=contracts(s).stream().filter(t->open(t)&&t.getString("kind").equals("migration")&&t.getUUID("destination").equals(d.settlement().id())&&t.getUUID("home").equals(h.id())).count();
   if(d.settlement().occupancy(h.id())+reserved<h.capacity())return h;}
  return null;
 }
 /** Route ends at a village gate outside the buildings, not inside a hall: a body may never materialize on a roof. Goods are handed over to the stock chest from the gate. */
 public static BlockPos gate(ServerLevel l,SettlementData.Entry e,BlockPos toward){
  double dx=toward.getX()-e.center().getX(),dz=toward.getZ()-e.center().getZ(),len=Math.max(1,Math.sqrt(dx*dx+dz*dz));
  // The gate steps outward past whatever of the village stands there: a caravaneer on the yard's roof never reaches its cart below
  // (caravan-cart-client, 2026-09-18).
  for(int r=GATE;r<=GATE+24;r++){int x=(int)Math.round(e.center().getX()+dx/len*r),z=(int)Math.round(e.center().getZ()+dz/len*r);
   if(!l.hasChunkAt(new BlockPos(x,0,z)))break;int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
   if(!org.villageastra.server.OwnershipEvents.protectedBlock(l,new BlockPos(x,y-1,z)))return new BlockPos(x,y,z);}
  int x=(int)Math.round(e.center().getX()+dx/len*GATE),z=(int)Math.round(e.center().getZ()+dz/len*GATE);
  return new BlockPos(x,l.hasChunkAt(new BlockPos(x,0,z))?l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z):e.center().getY(),z);
 }
 /** Where a body may appear near a route point: on open ground, never on a roof or a wall of a village. */
 static BlockPos ground(ServerLevel l,BlockPos at){
  if(!org.villageastra.server.OwnershipEvents.protectedBlock(l,at.below())&&l.getFluidState(at.below()).isEmpty())return at;
  for(int r=1;r<=8;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
   int x=at.getX()+dx,z=at.getZ()+dz;if(!l.hasChunkAt(new BlockPos(x,0,z)))continue;int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
   var top=new BlockPos(x,y-1,z);if(org.villageastra.server.OwnershipEvents.protectedBlock(l,top)||!l.getFluidState(top).isEmpty())continue;return new BlockPos(x,y,z);}
  return at;
 }
 static BlockPos from(CompoundTag t){return BlockPos.of(t.getString("state").equals(RETURNING)||t.getBoolean("home")?t.getLong("to"):t.getLong("from"));}
 static BlockPos to(CompoundTag t){return BlockPos.of(t.getString("state").equals(RETURNING)||t.getBoolean("home")?t.getLong("from"):t.getLong("to"));}
 public static double length(CompoundTag t){var a=from(t);var b=to(t);return Math.sqrt(Math.pow(b.getX()-a.getX(),2)+Math.pow(b.getZ()-a.getZ(),2));}
 /** Current route point (surface height when the column is loaded, otherwise interpolated). */
 public static BlockPos position(ServerLevel l,CompoundTag t,double progress){var a=from(t);var b=to(t);double len=Math.max(1,length(t)),f=Math.min(1,Math.max(0,progress/len));int x=(int)Math.round(a.getX()+(b.getX()-a.getX())*f),z=(int)Math.round(a.getZ()+(b.getZ()-a.getZ())*f);
  int y=l.hasChunkAt(new BlockPos(x,0,z))?l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z):(int)Math.round(a.getY()+(b.getY()-a.getY())*f);return new BlockPos(x,y,z);}
 private static ServerLevel level(MinecraftServer s,CompoundTag t){return s.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new ResourceLocation(t.getString("dimension"))));}
 /** Loads goods from the source stock chest through the journal. The chunk is loaded for this single bounded access. */
 public static int secure(ServerLevel l,CompoundTag t,long now){
  if(!CaravanHorses.prepare(l,t))return 0;
  var s=l.getServer();var source=SettlementData.get(s).entry(t.getUUID("source"));if(source==null){stamp(t,CANCELLED,now);save(s,t);return 0;}
  var b=stock(source);var pos=LogisticsRoutes.position(source,b);TouchLoad.force(l,pos);var chest=LogisticsRoutes.chest(l,source,b);if(chest==null)return 0;
  var item=BuiltInRegistries.ITEM.get(new ResourceLocation(t.getString("item")));var cargo=t.getList("cargo",Tag.TAG_COMPOUND);int have=0;for(var raw:cargo)have+=ItemStack.of((CompoundTag)raw).getCount();
  // ROAD-008: the village's free cart goes with the trip; without one the caravaneer carries what one pair of hands carries.
  if(!t.getString("kind").equals("migration")&&!t.contains("cart")){var cart=freeCart(l,source);if(cart!=null)stowCart(t,cart);}
  int capacity=Math.min(t.getInt("count"),carry(l,source)*(t.contains("cart")&&!CaravanDogs.isDog(t)?2:1));
  while(have<capacity){var id=Settlement.childId(t.getUUID("id"),"take/"+t.getInt("takes"));var got=WorldJournal.recoverAmount(l,id);
   if(got.isEmpty()&&!WorldJournal.exists(l,id))for(int slot=0;slot<chest.getContainerSize();slot++){var st=chest.getItem(slot);if(st.is(item)&&Trade.plain(st)){got=WorldJournal.takeAmount(l,id,pos,slot,st.copy(),Math.min(st.getCount(),capacity-have));break;}}
   if(got.isEmpty())break;cargo.add(got.save(new CompoundTag()));t.put("cargo",cargo);t.putInt("takes",t.getInt("takes")+1);have+=got.getCount();save(s,t);}
  if(have==0){parkCart(l,t,source);stamp(t,CANCELLED,now);save(s,t);return 0;}
  t.putInt("secured",have);stamp(t,SECURED,now);save(s,t);return have;
 }
 /** The caravaneer leaves: any loaded body is removed and the identity epoch advances, so saved copies can never rejoin. */
 public static void dispatch(ServerLevel l,CompoundTag t,long now){
  var s=l.getServer();CaravanHorses.depart(l,t);CaravanDogs.recruit(l,t,now);CaravanEscorts.recruit(l,t,now);var npc=caravaneerEntity(l,t.getUUID("caravaneer"));CaravanEscorts.remember(t,npc);if(npc!=null)npc.discard();bump(s,t.getUUID("caravaneer"));
  // AD-123: the road and trail cells of either village along the gate-to-gate line, counted once per departure (an old contract has none: 0).
  t.putDouble("routeBonus",Trails.routeBonus(l,from(t),to(t),t.hasUUID("source")?t.getUUID("source"):null,t.hasUUID("destination")?t.getUUID("destination"):null));
  t.putDouble("progress",0);t.putBoolean("materialized",false);stamp(t,TRANSIT,now);save(s,t);
 }
 /** Spawns the travelling caravaneer at the route position; the only way the identity re-enters the world during a trip. */
 public static ResidentEntity materialize(ServerLevel l,CompoundTag t,BlockPos at){
  if(CaravanHorses.isHorse(t)){CaravanHorses.materialize(l,t,at);return null;}
  var s=l.getServer();var village=SettlementData.get(s).entry(t.getUUID(t.getBoolean("transferred")?"destination":"source"));if(village==null)return null;var r=village.settlement().resident(t.getUUID("caravaneer"));if(r==null||!r.alive())return null;
  var old=caravaneerEntity(l,r.id());if(old!=null)old.discard();long epoch=bump(s,r.id());
  at=ground(l,at);
  var npc=VillageAstra.RESIDENT.get().create(l);if(npc==null)return null;npc.setUUID(r.id());npc.bind(village.settlement().id(),r);npc.getPersistentData().putLong(EPOCH,epoch);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
  CaravanEscorts.restore(t,npc);if(!l.addFreshEntity(npc))return null;t.putBoolean("materialized",true);
  // AD-085: the cart that travels with the trip walks behind the caravaneer while a player can see them.
  cartBody(l,t,npc,at);
  save(s,t);return npc;
 }
 /** The body of the travelling cart, walking behind its caravaneer while a player can see them. */
 static CartEntity cartBody(ServerLevel l,CompoundTag t,net.minecraft.world.entity.LivingEntity npc,BlockPos at){
  if(!t.contains("cart"))return null;var at2=CartHitch.parking(l,at);if(at2==null)return null;long epoch2=t.getLong("cartEpoch")+1;t.putLong("cartEpoch",epoch2);
  var cart=new CartEntity(VillageAstra.CART.get(),l);cart.load(t.getCompound("cart"));cart.setUUID(UUID.randomUUID());cart.travel(t.getUUID("id"),epoch2);
  cart.puller(npc.getUUID());cart.setPos(at2.getX()+.5,at2.getY(),at2.getZ()+.5);return l.addFreshEntity(cart)?cart:null;
 }
 /** A materialized caravaneer's chunk unloaded: the record takes over again and the saved body becomes stale. */
 public static void dematerialize(ResidentEntity npc){
  if(!(npc.level() instanceof ServerLevel l))return;var t=trip(l.getServer(),npc.getUUID());if(t==null||!t.getBoolean("materialized")||npc.getPersistentData().getLong(EPOCH)!=epoch(l.getServer(),npc.getUUID()))return;
  CaravanEscorts.remember(t,npc);t.putBoolean("materialized",false);bump(l.getServer(),npc.getUUID());
  // The cart's body goes with the caravaneer's: a newer epoch makes any copy still standing in the world stale.
  if(t.contains("cart")){t.putLong("cartEpoch",t.getLong("cartEpoch")+1);
   for(var cart:l.getEntitiesOfClass(CartEntity.class,npc.getBoundingBox().inflate(CartEntity.LOST),c->t.getUUID("id").equals(c.trip())))cart.discard();}
  save(l.getServer(),t);
 }
 /** A travelling cart body is stale when its trip is over, no longer carries a cart, or has moved on to a newer body. */
 public static boolean cartStale(CartEntity cart){
  if(!(cart.level() instanceof ServerLevel l)||cart.trip()==null)return false;
  var t=contract(l.getServer(),cart.trip());
  return t==null||!open(t)&&!travelling(t)||!t.contains("cart")||t.getLong("cartEpoch")!=cart.tripEpoch();
 }
 /** Death on the road: carried goods fall where the caravaneer died; the contract records the loss instead of a delivery. */
 public static void died(ResidentEntity npc,long now){
  if(!(npc.level() instanceof ServerLevel l))return;var t=trip(l.getServer(),npc.getUUID());if(t!=null)died(npc,t,now);
 }
 static void died(Entity npc,CompoundTag t,long now){
  if(!(npc.level() instanceof ServerLevel l))return;int lost=0;
  for(var raw:t.getList("cargo",Tag.TAG_COMPOUND)){var st=ItemStack.of((CompoundTag)raw);lost+=st.getCount();var drop=new ItemEntity(l,npc.getX(),npc.getY()+.5,npc.getZ(),st);drop.setDefaultPickUpDelay();l.addFreshEntity(drop);}
  var fallen=t.contains("cart")?placeCart(l,t,roadside(l,t,npc.blockPosition())):null;
  // No spot to set the cart down: it breaks up where the caravaneer died, its hold and its wood on the ground, never gone with the record.
  boolean broken=fallen==null&&t.contains("cart");
  if(broken){var cart=new CartEntity(VillageAstra.CART.get(),l);cart.load(t.getCompound("cart"));
   for(int i=0;i<cart.getContainerSize();i++){var st=cart.getItem(i);if(st.isEmpty())continue;lost+=st.getCount();var drop=new ItemEntity(l,npc.getX(),npc.getY()+.5,npc.getZ(),st.copy());drop.setDefaultPickUpDelay();l.addFreshEntity(drop);}
   var wood=new ItemEntity(l,npc.getX(),npc.getY()+.5,npc.getZ(),new ItemStack(VillageAstra.CART_ITEM.get()));wood.setDefaultPickUpDelay();l.addFreshEntity(wood);t.remove("cart");}
  // AD-103: a fetch trip that ends on the road leaves the cart where it fell, to be fetched again; one that had not reached it lets it wait.
  if(t.getString("kind").equals(RECOVER)){var left=contract(l.getServer(),t.getUUID("trip"));
   if(left!=null){left.remove("recovery");if(fallen!=null){left.putUUID("leftCart",fallen.getUUID());left.putLong("cartLeftAt",fallen.blockPosition().asLong());left.remove("cartFetched");}else if(broken)left.putBoolean("cartGone",true);save(l.getServer(),left);}}
  // A trip that fell with its cart left it on the road like one left on order: the village sends for it.
  else if(fallen!=null){t.putUUID("leftCart",fallen.getUUID());t.putLong("cartLeftAt",fallen.blockPosition().asLong());t.remove("recovery");t.remove("cartFetched");t.remove("cartGone");}
  t.put("cargo",new ListTag());t.putInt("lost",t.getInt("lost")+lost);t.putBoolean("materialized",false);t.putBoolean("needsEntity",false);stamp(t,t.getString("state").equals(TRANSIT)?LOST:CLOSED,now);if(!t.getString("state").equals(CLOSED))stamp(t,CLOSED,now);save(l.getServer(),t);
 }
 /** Unloads cargo into a chest through the journal; returns the count accepted. */
 private static int unload(ServerLevel l,CompoundTag t,SettlementData.Entry e,String tagPrefix){
  var b=stock(e);var pos=LogisticsRoutes.position(e,b);TouchLoad.force(l,pos);if(LogisticsRoutes.chest(l,e,b)==null)return 0;int moved=0;var cargo=t.getList("cargo",Tag.TAG_COMPOUND);
  while(!cargo.isEmpty()){var st=ItemStack.of(cargo.getCompound(0));int unit=Math.min(st.getCount(),st.getMaxStackSize());var part=st.copyWithCount(unit);
   if(!WorldJournal.deposit(l,Settlement.childId(t.getUUID("id"),tagPrefix+"/"+t.getInt("drops")),pos,part)){if(unit>1){part=st.copyWithCount(1);if(!WorldJournal.deposit(l,Settlement.childId(t.getUUID("id"),tagPrefix+"/"+t.getInt("drops")),pos,part))break;unit=1;}else break;}
   st.shrink(unit);if(st.isEmpty())cargo.remove(0);else cargo.set(0,st.save(new CompoundTag()));t.put("cargo",cargo);t.putInt("drops",t.getInt("drops")+1);moved+=unit;save(l.getServer(),t);}
  return moved;
 }
 /** Home with more than the stock chest takes: the rest goes back into the cart's hold, to stand at the yard with it; what the hold cannot
  *  take, or a trip without a cart brings, is put down at the stock. The closed record keeps no goods. */
 private static void restow(ServerLevel l,CompoundTag t,SettlementData.Entry e){
  var cargo=t.getList("cargo",Tag.TAG_COMPOUND);if(cargo.isEmpty())return;CartEntity cart=null;
  if(t.contains("cart")){cart=new CartEntity(VillageAstra.CART.get(),l);cart.load(t.getCompound("cart"));}
  var pos=LogisticsRoutes.position(e,stock(e));
  for(var raw:cargo){var st=ItemStack.of((CompoundTag)raw);var over=cart!=null?CartHitch.put(cart,st):st;
   if(!over.isEmpty()){var drop=new ItemEntity(l,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,over);drop.setDefaultPickUpDelay();l.addFreshEntity(drop);}}
  if(cart!=null){var tag=new CompoundTag();cart.saveWithoutId(tag);tag.remove("UUID");t.put("cart",tag);}
  t.put("cargo",new ListTag());save(l.getServer(),t);
 }
 /** Delivery: coins move from the buyer's treasury to the seller's; any shortfall is emitted once, recorded with the state change. */
 public static void arrive(ServerLevel l,CompoundTag t,long now){
  if(CaravanEscorts.arrive(l,t))return;
  if(t.getString("state").equals(TRANSIT)&&!CaravanDogs.deliverTogether(l,t,now))return;
  var s=l.getServer();var data=SettlementData.get(s);
  if(t.getString("kind").equals(RECOVER)&&t.getString("state").equals(TRANSIT)){pickUp(l,t,now);return;}
  if(t.getString("kind").equals(RECOVER)&&t.getString("state").equals(RETURNING))emptyHold(l,t);
  if(t.getString("kind").equals("migration")&&t.getString("state").equals(TRANSIT)){
   var src=data.entry(t.getUUID("source"));var d=data.entry(t.getUUID("destination"));var r=src==null?null:src.settlement().resident(t.getUUID("caravaneer"));
   if(r==null||!r.alive()||d==null){stamp(t,CANCELLED,now);save(s,t);return;}
   var home=d.settlement().homes().stream().filter(h->h.id().equals(t.getUUID("home"))).findFirst().orElse(null);if(home==null||d.settlement().occupancy(home.id())>=home.capacity())home=freeHome(s,d);
   if(home==null){t.putBoolean("waiting",true);save(s,t);return;}
   // Same Resident object: identity, education, profile and life stay; the old job does not travel.
   src.settlement().release(r.id());d.settlement().admit(r,home.id());data.setDirty();
   var npc=caravaneerEntity(l,r.id());if(npc!=null)npc.bind(d.settlement().id(),r);
   t.putBoolean("home",false);t.putBoolean("transferred",true);t.putBoolean("needsEntity",!t.getBoolean("materialized"));stamp(t,DELIVERED,now);stamp(t,CLOSED,now);save(s,t);return;}
  if(t.getString("state").equals(TRANSIT)){var d=data.entry(t.getUUID("destination"));if(d==null){t.putBoolean("home",true);t.putDouble("progress",0);stamp(t,RETURNING,now);save(s,t);return;}
   int moved=unload(l,t,d,"deliver");t.putInt("delivered",t.getInt("delivered")+moved);long value=(long)t.getInt("delivered")*t.getInt("price")/t.getInt("per");
   var ledger=TradeLedger.get(s);long fromTreasury=Math.min(value,ledger.treasury(d.settlement().id()));t.putLong("paid",value);t.putLong("emitted",value-fromTreasury);
   stamp(t,t.getList("cargo",Tag.TAG_COMPOUND).isEmpty()?DELIVERED:PARTIAL,now);t.putBoolean("home",true);
   // AD-100: goods that really arrived warm the two villages to each other.
   if(moved>0)Relations.change(s,t.getUUID("source"),t.getUUID("destination"),2,"caravan_delivered");t.putDouble("progress",0);stamp(t,RETURNING,now);save(s,t);
   if(fromTreasury>0)ledger.addTreasury(d.settlement().id(),-fromTreasury);if(value>0)ledger.addTreasury(t.getUUID("source"),value);return;}
  if(t.getString("state").equals(RETURNING)){var src=data.entry(t.getUUID("source"));if(src!=null){unload(l,t,src,"return");restow(l,t,src);parkCart(l,t,src);}
   t.putBoolean("needsEntity",!t.getBoolean("materialized"));stamp(t,CLOSED,now);save(s,t);}
 }
 /** Background and materialized progress, called once per second for every open contract. */
 public static void tick(MinecraftServer s,long now){
  ensure(s);
  for(var t:List.copyOf(CONTRACTS.values())){var l=level(s,t);if(l==null)continue;if(CaravanDogs.tick(l,t,now)||CaravanHorses.tick(l,t,now))continue;String state=t.getString("state");
   switch(state){
    case PROPOSED->{var d=SettlementData.get(s).entry(t.getUUID("destination"));if(d==null){stamp(t,CANCELLED,now);save(s,t);}else{stamp(t,ACCEPTED,now);save(s,t);}}
    case ACCEPTED->{if(t.getString("kind").equals("migration")||t.getString("kind").equals(RECOVER)){var d=SettlementData.get(s).entry(t.getUUID("destination"));if(d==null){stamp(t,CANCELLED,now);save(s,t);}else{stamp(t,SECURED,now);dispatch(l,t,now);}}else if(secure(l,t,now)>0)dispatch(l,t,now);}
    case SECURED->dispatch(l,t,now);
    case TRANSIT,RETURNING->{
     CaravanEscorts.sync(l,t);
     var at=position(l,t,t.getDouble("progress"));
     if(t.getBoolean("materialized")){var npc=caravaneerEntity(l,t.getUUID("caravaneer"));if(npc==null){t.putBoolean("materialized",false);save(s,t);}continue;}
     if(l.hasChunkAt(at)&&!l.getPlayers(p->p.blockPosition().distSqr(at)<MATERIALIZE_RADIUS*MATERIALIZE_RADIUS).isEmpty()){materialize(l,t,at);continue;}
     if(CaravanDogs.visibleDog(l,t)||CaravanEscorts.background(l,t))continue;
     t.putDouble("progress",Math.min(length(t),t.getDouble("progress")+SPEED*20*(1+t.getDouble("routeBonus"))));if(t.getDouble("progress")>=length(t))arrive(l,t,now);else save(s,t);}
    case CLOSED->{if(!t.getBoolean("needsEntity"))continue;var home=BlockPos.of(t.getLong(t.getBoolean("transferred")?"to":"from"));if(l.hasChunkAt(home)&&!l.getPlayers(p->p.blockPosition().distSqr(home)<MATERIALIZE_RADIUS*MATERIALIZE_RADIUS).isEmpty()&&materialize(l,t,new BlockPos(home.getX()+2,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,home.getX()+2,home.getZ()),home.getZ()))!=null){t.putBoolean("needsEntity",false);save(s,t);}}
    default->{}
   }
  }
 }
}
