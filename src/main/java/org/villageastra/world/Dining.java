package org.villageastra.world;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-139: the restaurant's dining hall. A resident whose meal is due is invited to a free seat (Population.meal asks {@link #meal} before the
 *  pantries); {@link DineGoal} walks it there, it sits sit_ticks and {@link #serve} takes the dish from the restaurant's own chest through the
 *  journal and calls {@code ate(due)}. An invitation lives wait_ticks of the village clock; an expired one, a full hall past wait_ticks, the
 *  night, a siege, a far resident or an empty kitchen send the meal down the old pantry path with the same due: the hall never costs a meal.
 *  Seats are read from the world (a chair beside its table — AD-143; the spruce stair of an older hall still counts), never counted from the level. The hall's record (invitations, seats
 *  taken, the leftover of the open dish, the day's count, the couriers' trips) is data/astra-dining/&lt;village&gt;.bin. */
public final class Dining {
 private Dining(){}
 public static final int SIT_TICKS,WAIT_TICKS,DINE_RADIUS,NEAR_WORK_RADIUS,LEFTOVER_MAX,KEEP_ITEMS,COURIER_RADIUS,COURIER_CART_RADIUS,HAND_PORTIONS,CART_PORTIONS,LOOKAHEAD,SERVE_TICKS,RESERVE_TICKS;
 private static final int[] GROUPS=new int[7];
 public static final String TYPE="restaurant";
 static{
  JsonObject o;try(var s=Dining.class.getResourceAsStream("/data/villageastra/balance/dining.json")){if(s==null)throw new IllegalStateException("Missing dining balance");o=JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}
  SIT_TICKS=o.get("sit_ticks").getAsInt();WAIT_TICKS=o.get("wait_ticks").getAsInt();DINE_RADIUS=o.get("dine_radius").getAsInt();NEAR_WORK_RADIUS=o.get("near_work_radius").getAsInt();
  LEFTOVER_MAX=o.get("leftover_max").getAsInt();KEEP_ITEMS=o.get("keep_items").getAsInt();COURIER_RADIUS=o.get("courier_radius").getAsInt();COURIER_CART_RADIUS=o.get("courier_cart_radius").getAsInt();
  HAND_PORTIONS=o.get("courier_hand_portions").getAsInt();CART_PORTIONS=o.get("courier_cart_portions").getAsInt();LOOKAHEAD=o.get("courier_lookahead_ticks").getAsInt();
  SERVE_TICKS=o.get("courier_serve_ticks").getAsInt();RESERVE_TICKS=o.get("courier_reserve_ticks").getAsInt();
  for(int lv=1;lv<=6;lv++){GROUPS[lv]=o.getAsJsonObject("seat_groups").get(String.valueOf(lv)).getAsInt();if(GROUPS[lv]<GROUPS[lv-1]||GROUPS[lv]>VillageStyle.RESTAURANT_TABLES.length)throw new IllegalStateException("Invalid seat groups");}
  if(GROUPS[1]!=VillageStyle.RESTAURANT_FIRST||SIT_TICKS<20||WAIT_TICKS<SIT_TICKS||LEFTOVER_MAX<0)throw new IllegalStateException("Inconsistent dining balance");
 }
 /** Table groups standing at a level of the restaurant (balance/dining.json seat_groups). */
 public static int groups(int level){return GROUPS[Math.max(1,Math.min(6,level))];}
 /** The seats the design gives a level: two per table group. */
 public static int designSeats(int level){return 2*groups(level);}
 // ---- the hall in the world -------------------------------------------------------------------
 /** A restaurant, or an old world's bakery that works as one (CoreCatalog.canonical). */
 public static boolean restaurant(Settlement.Building b){return b!=null&&TYPE.equals(CoreCatalog.canonical(b.type()));}
 public static List<Settlement.Building> restaurants(SettlementData.Entry e){return e.settlement().buildings().stream().filter(Dining::restaurant).toList();}
 /** The local cells of the 16 seats of a restaurant design, in the order of its groups (west seat, east seat); none for an old bakery's design. */
 public static List<BlockPos> seatCells(String type){if(!TYPE.equals(type))return List.of();var out=new ArrayList<BlockPos>();
  for(var t:VillageStyle.RESTAURANT_TABLES){out.add(new BlockPos(t[0]-1,1,t[1]));out.add(new BlockPos(t[0]+1,1,t[1]));}return out;}
 private static BlockPos tableCell(int seat){var t=VillageStyle.RESTAURANT_TABLES[seat/2];return new BlockPos(t[0],1,t[1]);}
 public static BlockPos seatPos(SettlementData.Entry e,Settlement.Building b,int seat){var c=seatCells(b.type()).get(seat);return BuildingPlacement.at(e,b,c.getX(),c.getY(),c.getZ());}
 public static BlockPos tablePos(SettlementData.Entry e,Settlement.Building b,int seat){var c=tableCell(seat);return BuildingPlacement.at(e,b,c.getX(),c.getY(),c.getZ());}
 /** A seat stands: a chair (AD-143) or, in a hall built before it, a stair in its cell, and its table beside it (a table, or an old hall's
  *  fence or barrel). */
 public static boolean standing(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int seat){
  var at=seatPos(e,b,seat);var table=tablePos(e,b,seat);if(!l.hasChunkAt(at)||!l.hasChunkAt(table))return false;
  return seat(l.getBlockState(at))&&table(l.getBlockState(table));}
 /** What a diner sits on: a chair, or an old hall's stair. */
 public static boolean seat(BlockState s){return s.getBlock() instanceof ChairBlock||s.getBlock() instanceof StairBlock;}
 /** What a diner eats at: a table, or an old hall's fence or barrel. */
 public static boolean table(BlockState s){return s.getBlock() instanceof TableBlock||s.getBlock() instanceof FenceBlock||s.is(Blocks.BARREL);}
 /** A seat's chair someone other than {@code resident} sits on (a player, say): it is not offered. */
 static boolean satOn(ServerLevel l,BlockPos at,UUID resident){var who=SeatEntity.sitter(l,at);return who!=null&&!who.getUUID().equals(resident);}
 private record Count(long at,int seats){}
 private static final Map<UUID,Count> SEATS=new java.util.concurrent.ConcurrentHashMap<>();
 /** Seats that stand in the world now (cached for BuildingLevels.BEST_TICKS). */
 public static int seats(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  long now=l.getGameTime();var c=SEATS.get(b.id());if(c!=null&&now>=c.at()&&now-c.at()<BuildingLevels.BEST_TICKS)return c.seats();
  int n=0;for(int i=0;i<seatCells(b.type()).size();i++)if(standing(l,e,b,i))n++;SEATS.put(b.id(),new Count(now,n));return n;}
 public static void forgetSeats(){SEATS.clear();}
 /** AD-139: the furniture a level lays (LevelArchitecture.apply): its table groups, table cloths from III, the hitching post and fodder from V.
  *  AD-143: a group is a table joined to the other of its pair (z 2+3 and 5+6) and two chairs facing it from west and east. */
 public static void furnish(String type,int level,BlockPos base,Map<BlockPos,BlockState> m){
  if(!TYPE.equals(type))return;
  var wood=org.villageastra.domain.Furniture.RESTAURANT_WOOD;
  var table=org.villageastra.VillageAstra.TABLES.get(wood).get().defaultBlockState();var chair=org.villageastra.VillageAstra.CHAIRS.get(wood).get().defaultBlockState();
  for(int g=0;g<groups(level);g++){var t=VillageStyle.RESTAURANT_TABLES[g];boolean north=VillageStyle.restaurantJoinsNorth(g);
   m.put(base.offset(t[0],1,t[1]),table.setValue(TableBlock.NORTH,north).setValue(TableBlock.SOUTH,!north));
   m.put(base.offset(t[0]-1,1,t[1]),chair.setValue(ChairBlock.FACING,Direction.EAST));
   m.put(base.offset(t[0]+1,1,t[1]),chair.setValue(ChairBlock.FACING,Direction.WEST));
   if(level>=3)m.put(base.offset(t[0],2,t[1]),Blocks.WHITE_CARPET.defaultBlockState());}
  // Keep the two existing porch barrels (3 and 7): a hitch and fodder must not replace stored goods.
  if(level>=5){m.put(base.offset(2,1,0),Blocks.SPRUCE_FENCE.defaultBlockState());m.put(base.offset(2,2,0),Blocks.LANTERN.defaultBlockState());m.put(base.offset(8,1,0),Blocks.HAY_BLOCK.defaultBlockState());}
 }
 // ---- food --------------------------------------------------------------------------------------
 /** The dishes the hall and the couriers serve and the restaurant keeps: made food, not raw crops. */
 public static final Set<Item> DISHES=Set.of(Items.BREAD,Items.BAKED_POTATO,Items.PUMPKIN_PIE,Items.COOKED_BEEF,Items.COOKED_PORKCHOP,Items.COOKED_CHICKEN,Items.COOKED_MUTTON,Items.COOKED_COD,Items.COOKED_SALMON,Items.COOKED_RABBIT);
 public static boolean dish(ItemStack s){return !s.isEmpty()&&DISHES.contains(s.getItem())&&Population.nutrition(s)>0;}
 public static OwnedChestEntity chest(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return LogisticsRoutes.chest(l,e,b);}
 public static int dishRations(ServerLevel l,SettlementData.Entry e,Settlement.Building b){var c=chest(l,e,b);if(c==null)return 0;int n=0;for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(dish(s))n+=Population.nutrition(s)*s.getCount();}return n;}
 public static ItemStack firstDish(ServerLevel l,SettlementData.Entry e,Settlement.Building b){var c=chest(l,e,b);if(c==null)return ItemStack.EMPTY;for(int i=0;i<c.getContainerSize();i++)if(dish(c.getItem(i)))return c.getItem(i).copyWithCount(1);return ItemStack.EMPTY;}
 /** LogisticsRoutes.reserve: dishes a restaurant keeps for its hall before the rest goes to the stock; an old bakery (no hall) keeps none. */
 public static int keep(Settlement.Building b,ItemStack s){return restaurant(b)&&!seatCells(b.type()).isEmpty()&&DISHES.contains(s.getItem())?KEEP_ITEMS:0;}
 /** Population.meal: the loaded chests of the restaurants, read after the pantries and for their dishes only (never the cook's raw inputs). */
 public static List<OwnedChestEntity> larders(ServerLevel l,SettlementData.Entry e){var out=new ArrayList<OwnedChestEntity>();for(var b:restaurants(e)){var c=chest(l,e,b);if(c!=null)out.add(c);}return out;}
 /** Workshops.wants: a restaurant with a hall asks the stock for dishes while its chest holds fewer than keep_items. */
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e){var out=new ArrayList<Workshops.Want>();
  for(var b:restaurants(e)){if(seatCells(b.type()).isEmpty())continue;var c=chest(l,e,b);if(c==null)continue;int have=LogisticsRoutes.count(c,Dining::dish);
   if(have<KEEP_ITEMS)out.add(new Workshops.Want(Ingredient.of(DISHES.toArray(Item[]::new)),KEEP_ITEMS-have,b.id()));}
  return out;}
 // ---- the record --------------------------------------------------------------------------------
 static final class Hall{int leftover;long day=-1;int served;int cooled;final Map<Integer,UUID> taken=new TreeMap<>();final Map<Integer,Long> since=new HashMap<>();}
 public record Invite(UUID building,long due,long expires){}
 static final class State{final Path file;final Map<UUID,Hall> halls=new HashMap<>();final Map<UUID,Invite> invites=new HashMap<>();final Map<UUID,CompoundTag> trips=new HashMap<>();State(Path f){file=f;}}
 private static final Map<Path,State> STATES=new java.util.concurrent.ConcurrentHashMap<>();
 public static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-dining/"+village+".bin");}
 static State state(ServerLevel l,UUID village){var p=path(l,village);return STATES.computeIfAbsent(p,f->read(l,f));}
 /** Forget every record read (a test or a reload that must read the files again). */
 public static void forget(){STATES.clear();SEATS.clear();}
 private static State read(ServerLevel l,Path p){var st=new State(p);if(!Files.exists(p))return st;var t=NbtRecord.read(p);if(t.getInt("schema")!=1)throw new IllegalStateException("Invalid dining record");
  long now=l.getGameTime();
  var halls=t.getCompound("halls");for(var k:halls.getAllKeys()){var h=halls.getCompound(k);var hall=new Hall();hall.leftover=h.getInt("leftover");hall.day=h.getLong("day");hall.served=h.getInt("served");hall.cooled=h.getInt("cooled");
   // §8: a seat taken before a restart is free again — whoever sat there sits down anew (no ghost seat after a crash).
   st.halls.put(UUID.fromString(k),hall);}
  var inv=t.getCompound("invites");for(var k:inv.getAllKeys()){var i=inv.getCompound(k);st.invites.put(UUID.fromString(k),new Invite(i.getUUID("building"),i.getLong("due"),i.getLong("expires")));}
  var trips=t.getCompound("trips");for(var k:trips.getAllKeys())st.trips.put(UUID.fromString(k),trips.getCompound(k));
  return st;}
 static void save(State st){var t=new CompoundTag();t.putInt("schema",1);
  var halls=new CompoundTag();for(var en:st.halls.entrySet()){var h=new CompoundTag();var v=en.getValue();h.putInt("leftover",v.leftover);h.putLong("day",v.day);h.putInt("served",v.served);h.putInt("cooled",v.cooled);
   var seats=new CompoundTag();for(var s:v.taken.entrySet()){var q=new CompoundTag();q.putUUID("resident",s.getValue());q.putLong("since",v.since.getOrDefault(s.getKey(),0L));seats.put(String.valueOf(s.getKey()),q);}h.put("seats",seats);
   halls.put(en.getKey().toString(),h);}
  t.put("halls",halls);
  var inv=new CompoundTag();for(var en:st.invites.entrySet()){var i=new CompoundTag();i.putUUID("building",en.getValue().building());i.putLong("due",en.getValue().due());i.putLong("expires",en.getValue().expires());inv.put(en.getKey().toString(),i);}
  t.put("invites",inv);var trips=new CompoundTag();for(var en:st.trips.entrySet())trips.put(en.getKey().toString(),en.getValue());t.put("trips",trips);
  NbtRecord.write(st.file,t);}
 static Hall hall(State st,UUID building){return st.halls.computeIfAbsent(building,k->new Hall());}
 /** The invitation a resident holds, or null. */
 public static Invite invite(ServerLevel l,SettlementData.Entry e,UUID resident){return state(l,e.settlement().id()).invites.get(resident);}
 /** A courier's trip as recorded (a copy), or null. */
 public static CompoundTag tripOf(ServerLevel l,SettlementData.Entry e,UUID worker){var t=state(l,e.settlement().id()).trips.get(worker);return t==null?null:t.copy();}
 public static int invites(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return (int)state(l,e.settlement().id()).invites.values().stream().filter(i->i.building().equals(b.id())).count();}
 public static int taken(ServerLevel l,SettlementData.Entry e,Settlement.Building b){var h=state(l,e.settlement().id()).halls.get(b.id());return h==null?0:h.taken.size();}
 public static int leftover(ServerLevel l,SettlementData.Entry e,Settlement.Building b){var h=state(l,e.settlement().id()).halls.get(b.id());return h==null?0:h.leftover;}
 public static int served(ServerLevel l,SettlementData.Entry e,Settlement.Building b){var h=state(l,e.settlement().id()).halls.get(b.id());return h==null?0:h.served;}
 // ---- the meal ----------------------------------------------------------------------------------
 public enum Outcome{PANTRY,WAIT}
 /** Asked by Population.meal once a meal is due, before the pantries: WAIT keeps the meal waiting (an invitation or a courier holds it, or the
  *  hall is full and the wait is not over), PANTRY sends it down the old path now (with the same due). Never eats and never misses a meal. */
 public static Outcome meal(ServerLevel l,SettlementData.Entry e,Resident r,long now,long due){
  if(restaurants(e).isEmpty())return Outcome.PANTRY;
  var st=state(l,e.settlement().id());var inv=st.invites.get(r.id());
  // An invitation holds the meal only while its hall can seat the diner: at dusk, in a siege or with the restaurant gone it ends now, not at its expiry.
  if(inv!=null){boolean open=day(l,e)&&!Sieges.besieged(l.getServer(),e.settlement().id())&&e.settlement().buildings().stream().anyMatch(x->x.id().equals(inv.building()));
   if(inv.due()==due&&now<=inv.expires()&&open)return Outcome.WAIT;st.invites.remove(r.id());release(st,r.id());save(st);return Outcome.PANTRY;}
  if(Couriers.holds(st,r.id(),now))return Outcome.WAIT;
  var b=hallFor(l,e,r);if(b==null)return Outcome.PANTRY;
  var why=why(l,e,r,b);
  if(why.isEmpty()){st.invites.put(r.id(),new Invite(b.id(),due,now+WAIT_TICKS));save(st);return Outcome.WAIT;}
  // §3.4 (5): the hall is full — wait for a seat as long as an invitation would have lived, then the pantry.
  if(why.equals("full")&&now<due+WAIT_TICKS)return Outcome.WAIT;
  return Outcome.PANTRY;
 }
 /** The restaurant a resident would dine at: the nearest one with seats standing. */
 static Settlement.Building hallFor(ServerLevel l,SettlementData.Entry e,Resident r){
  Settlement.Building best=null;double d=Double.MAX_VALUE;var npc=l.getEntity(r.id());
  for(var b:restaurants(e)){if(seatCells(b.type()).isEmpty())continue;var at=LogisticsRoutes.position(e,b);double dd=npc==null?0:npc.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5);if(dd<d){d=dd;best=b;}}
  return best;}
 /** AD-139: the hall is open by day (the drill ground's expression, Population.drill). Tests set a village's own time of day (testDay). */
 static boolean day(ServerLevel l,SettlementData.Entry e){var t=TEST_DAY.get(e.settlement().id());return (t!=null?t:l.getDayTime())%24000<12000;}
 private static final Map<UUID,Long> TEST_DAY=new java.util.concurrent.ConcurrentHashMap<>();
 /** GameTests: this village's time of day (null: the level's own). */
 public static void testDay(UUID village,Long dayTime){if(dayTime==null)TEST_DAY.remove(village);else TEST_DAY.put(village,dayTime);}
 /** Why this resident does not dine at this restaurant now (§3.4), or empty: it may. */
 public static String why(ServerLevel l,SettlementData.Entry e,Resident r,Settlement.Building b){
  if(b==null)return "none";
  if(!day(l,e))return "closed_night";
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  // §14.6 (owner default): children eat from the pantry and do not come to the hall.
  if(r.life()!=Resident.Life.ADULT)return "child";
  if(!(l.getEntity(r.id()) instanceof ResidentEntity npc)||!npc.isAlive()||!TouchLoad.ticking(l,npc.blockPosition()))return "away";
  if(npc.escortPlayer()!=null||npc.getPersistentData().hasUUID(Camps.QUEST_TAG))return "escort";
  var at=LogisticsRoutes.position(e,b);
  if(npc.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)>(double)DINE_RADIUS*DINE_RADIUS)return "too_far";
  // §5.4: from IV, a resident working further than near_work_radius from the hall is the courier's while the restaurant has one.
  if(BuildingLevels.level(l,e,b)>=4&&Couriers.posted(e,b)>0){var w=e.settlement().workplace(r.id());
   if(w!=null&&!w.id().equals(b.id())){var wp=LogisticsRoutes.position(e,w);if(wp.distSqr(at)>(double)NEAR_WORK_RADIUS*NEAR_WORK_RADIUS)return "courier";}}
  int seats=seats(l,e,b);if(seats==0)return "no_seats";
  var st=state(l,e.settlement().id());var h=st.halls.get(b.id());
  // The food of the diners already invited is theirs: a new invitation needs a meal beyond it (an invitation the chest cannot serve only waits).
  int invited=invites(l,e,b);
  if(dishRations(l,e,b)+(h==null?0:h.leftover)<Population.MEAL_NUTRITION*(invited+1))return "no_food";
  if(invited>=seats)return "full";
  return "";
 }
 /** DineGoal: a free seat standing for this invited resident, the nearest to the door first (its own when it holds one); -1 when none. */
 public static int takeSeat(ServerLevel l,SettlementData.Entry e,Settlement.Building b,UUID resident){
  var st=state(l,e.settlement().id());var h=hall(st,b.id());
  for(var en:h.taken.entrySet())if(en.getValue().equals(resident))return en.getKey();
  int best=-1;double d=Double.MAX_VALUE;var door=BuildingPlacement.at(e,b,BuildingBlueprints.doorX(b.type()),1,0);
  for(int i=0;i<seatCells(b.type()).size();i++){if(h.taken.containsKey(i)||!standing(l,e,b,i)||satOn(l,seatPos(e,b,i),resident))continue;double dd=seatPos(e,b,i).distSqr(door);if(dd<d){d=dd;best=i;}}
  if(best>=0){h.taken.put(best,resident);h.since.put(best,l.getGameTime());save(st);}
  return best;}
 /** The seat is left (a finished meal, death, a siege, the night, a moved building): always freed. */
 public static void leave(ServerLevel l,SettlementData.Entry e,UUID resident){var st=state(l,e.settlement().id());if(release(st,resident))save(st);}
 private static boolean release(State st,UUID resident){boolean any=false;for(var h:st.halls.values())for(var it=h.taken.entrySet().iterator();it.hasNext();){var en=it.next();if(en.getValue().equals(resident)){it.remove();h.since.remove(en.getKey());any=true;}}return any;}
 /** The dish a seated resident is served: the leftover of the open dish first, then whole dishes from the restaurant's chest through the
  *  journal (one operation each, replayed by its id). Returns whether the meal was eaten; the invitation ends and the seat is freed. */
 public static boolean serve(ServerLevel l,SettlementData.Entry e,Settlement.Building b,Resident r){
  var st=state(l,e.settlement().id());var inv=st.invites.get(r.id());if(inv==null||!inv.building().equals(b.id()))return false;
  long due=inv.due();var h=hall(st,b.id());int meal=Population.MEAL_NUTRITION;
  int eaten=Math.min(h.leftover,meal),left=h.leftover-eaten;var chest=chest(l,e,b);var pos=LogisticsRoutes.position(e,b);
  for(int n=0;eaten<meal&&n<meal;n++){var id=Settlement.childId(r.id(),"dine/"+due+"/"+n);var got=WorldJournal.recoverAmount(l,id);
   if(got.isEmpty()&&!WorldJournal.exists(l,id)&&chest!=null)for(int slot=0;slot<chest.getContainerSize();slot++){var s=chest.getItem(slot);if(!dish(s))continue;got=WorldJournal.takeAmount(l,id,pos,slot,s.copy(),1);break;}
   if(got.isEmpty())break;int ration=Population.nutrition(got)*got.getCount();int take=Math.min(ration,meal-eaten);eaten+=take;left+=ration-take;}
  if(eaten<=0)return false;
  r.ate(due);h.leftover=Math.min(LEFTOVER_MAX,left);h.served++;st.invites.remove(r.id());release(st,r.id());save(st);
  SettlementData.get(l.getServer()).setDirty();return true;
 }
 /** Housekeeping of one loaded village (Population.tick, every 20 ticks): the day's count, the leftover cooling at dusk, seats of residents that
  *  no longer hold an invitation, invitations of the dead, abandoned courier trips. */
 public static boolean tick(ServerLevel l,SettlementData.Entry e,long now){
  if(restaurants(e).isEmpty()&&!Files.exists(path(l,e.settlement().id())))return false;
  var st=state(l,e.settlement().id());boolean changed=false;long day=l.getDayTime()/24000;boolean night=!day(l,e);
  for(var b:restaurants(e)){var h=hall(st,b.id());
   if(h.day!=day){h.day=day;h.served=0;h.cooled=0;changed=true;}
   // §3.5: the open dish cools at dusk — the leftover is lost, and the card says how much.
   if(night&&h.leftover>0){h.cooled+=h.leftover;h.leftover=0;changed=true;}
   for(var it=h.taken.entrySet().iterator();it.hasNext();){var en=it.next();var inv=st.invites.get(en.getValue());long since=h.since.getOrDefault(en.getKey(),0L);
    if(inv==null||!inv.building().equals(b.id())||l.getGameTime()-since>(long)WAIT_TICKS+SIT_TICKS||l.getGameTime()<since){it.remove();h.since.remove(en.getKey());changed=true;}}}
  for(var it=st.invites.entrySet().iterator();it.hasNext();){var en=it.next();var r=e.settlement().resident(en.getKey());
   if(r==null||!r.alive()||e.settlement().buildings().stream().noneMatch(x->x.id().equals(en.getValue().building()))){it.remove();changed=true;}}
  changed|=Couriers.housekeeping(l,e,st,now);
  if(changed)save(st);return changed;
 }
 /** The dining card of a restaurant (BuildingCards): the hall's seats, those taken and waiting, why it is closed, the day's meals, the
  *  leftover and what cooled, the kitchen's level and dishes, the couriers and the cart. */
 public static CompoundTag card(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var t=new CompoundTag();int level=BuildingLevels.level(l,e,b);int seats=seats(l,e,b);t.putInt("level",level);t.putInt("seats",seats);t.putInt("designSeats",seatCells(b.type()).isEmpty()?0:designSeats(level));
  t.putInt("taken",taken(l,e,b));t.putInt("invited",invites(l,e,b));t.putInt("served",served(l,e,b));t.putInt("leftover",leftover(l,e,b));
  var h=state(l,e.settlement().id()).halls.get(b.id());t.putInt("cooled",h==null?0:h.cooled);
  t.putString("state",seats==0?"no_seats":!day(l,e)?"closed_night":Sieges.besieged(l.getServer(),e.settlement().id())?"besieged":dishRations(l,e,b)+(h==null?0:h.leftover)<Population.MEAL_NUTRITION?"no_food":"open");
  t.putInt("dishes",dishRations(l,e,b)/Math.max(1,Population.MEAL_NUTRITION));
  // §9: an hour of the hall seats a diner for sit_ticks, two meals a day, twelve thousand ticks of daylight — seats × 10 residents.
  t.putInt("feeds",seats*12000/SIT_TICKS/2);
  t.putBoolean("meat",level>=2);t.putBoolean("greatOven",level>=3);t.putBoolean("automatic",level>=6);
  t.put("couriers",Couriers.card(l,e,b,level));
  return t;
 }
}
