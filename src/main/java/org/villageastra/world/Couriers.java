package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
/** AD-139 §5: the restaurant's couriers. A courier is a porter posted at the restaurant (the 22 professions stay); PorterWork never takes it.
 *  From level IV the restaurant posts CoreEffects couriers (IV 1, V–VI 2). A trip: the courier takes dishes from the restaurant's chest
 *  through the journal (as many as its load of portions needs), carries them to residents whose meal is due within the lookahead and who
 *  work away from the hall, serves each a portion ({@code ate(due)}) and brings back through the journal what it did not open. The residents
 *  of a trip are held for it (Dining.meal waits) until courier_reserve_ticks, then eat from the pantry. From V, with a free dog of the village
 *  (VillageDogs) and a village cart by the restaurant, it pulls the cart: 54 portions and twice the radius; without a dog, 16 by hand.
 *  A courier that dies or loses its post has its load returned to the restaurant's chest by the housekeeping (nothing is lost or copied).
 *  The trip is kept in the dining record (data/astra-dining) under the courier's id. */
public final class Couriers {
 private Couriers(){}
 /** Courier posts of a restaurant at this level: the porters of its Staff row (balance/staff.json; the core's "couriers" effect reads the same row). */
 public static int posts(int level){return Staff.slots(Dining.TYPE,"porter",level);}
 /** Couriers posted at this restaurant now. */
 public static int posted(SettlementData.Entry e,Settlement.Building b){var s=e.settlement();return (int)s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.PORTER&&s.workplace(r.id())!=null&&s.workplace(r.id()).id().equals(b.id())).count();}
 /** A porter posted at a restaurant. */
 public static boolean courier(SettlementData.Entry e,UUID resident){var r=e.settlement().resident(resident);var w=e.settlement().workplace(resident);return r!=null&&r.alive()&&r.profession()==Profession.PORTER&&Dining.restaurant(w);}
 public static final String CART_RESEARCH="baking.5";
 /** §5.5: whether a courier of this restaurant takes the cart now: level V, Restaurant V researched and a free dog of the village. */
 public static boolean mayTakeCart(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return BuildingLevels.level(l,e,b)>=5&&ResearchKnobs.done(l,e).contains(CART_RESEARCH)&&!VillageDogs.available(l,e).isEmpty();}
 /** Why a level-V courier goes by hand, or empty: "planned" (no village has dogs yet), "no_dog", "no_cart". */
 public static String cartRefusal(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(BuildingLevels.level(l,e,b)<5||!ResearchKnobs.done(l,e).contains(CART_RESEARCH))return "level";
  if(!VillageDogs.provided())return "planned";if(VillageDogs.available(l,e).isEmpty())return "no_dog";
  return CartHitch.near(l,e,LogisticsRoutes.position(e,b),12)==null?"no_cart":"";}
 /** CartHitch: a courier of a restaurant that may take the cart may take the hitch. */
 public static boolean mayPull(ServerLevel l,SettlementData.Entry e,ResidentEntity w){var b=e.settlement().workplace(w.getUUID());return courier(e,w.getUUID())&&mayTakeCart(l,e,b);}
 static boolean holds(Dining.State st,UUID resident,long now){for(var t:st.trips.values())if(!t.getString("stage").equals("return"))for(var raw:t.getList("targets",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;if(q.getUUID("r").equals(resident)&&now<=q.getLong("until"))return true;}return false;}
 private static boolean inTrip(Dining.State st,UUID resident){for(var t:st.trips.values())for(var raw:t.getList("targets",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getUUID("r").equals(resident))return true;return false;}
 /** Residents a courier of this restaurant may feed now, those who missed meals first, then the nearest: meal due within the lookahead, working
  *  (or standing) further than near_work_radius from the hall and within the radius, no invitation, in no other trip, adults with a loaded body. */
 public static List<Resident> targets(ServerLevel l,SettlementData.Entry e,Settlement.Building b,long now,int radius){
  var st=Dining.state(l,e.settlement().id());var at=LogisticsRoutes.position(e,b);var out=new ArrayList<Resident>();var dist=new HashMap<UUID,Double>();
  for(var r:e.settlement().residents()){if(!r.alive()||r.life()!=Resident.Life.ADULT||r.lastMeal()<0||courier(e,r.id())&&e.settlement().workplace(r.id()).id().equals(b.id()))continue;
   if(r.lastMeal()+Population.MEAL_INTERVAL>now+Dining.LOOKAHEAD||st.invites.containsKey(r.id())||inTrip(st,r.id()))continue;
   if(!(l.getEntity(r.id()) instanceof ResidentEntity npc)||!npc.isAlive()||npc.escortPlayer()!=null)continue;
   double d=npc.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5);if(d>(double)radius*radius)continue;
   var w=e.settlement().workplace(r.id());boolean away=w!=null&&!w.id().equals(b.id())&&LogisticsRoutes.position(e,w).distSqr(at)>(double)Dining.NEAR_WORK_RADIUS*Dining.NEAR_WORK_RADIUS;
   if(!away&&d<=(double)Dining.NEAR_WORK_RADIUS*Dining.NEAR_WORK_RADIUS)continue;
   out.add(r);dist.put(r.id(),d);}
  out.sort(Comparator.<Resident>comparingInt(r->-r.missedMeals()).thenComparingDouble(r->dist.get(r.id())));
  return out;}
 private static int rations(ListTag load){int n=0;for(var raw:load){var s=ItemStack.of((CompoundTag)raw);n+=Population.nutrition(s)*s.getCount();}return n;}
 private static boolean near(ResidentEntity w,BlockPos p){return w.distanceToSqr(p.getX()+1.5,p.getY(),p.getZ()+.5)<=6.25;}
 /** One step of a courier (CourierGoal every 10 ticks); {@code waited} is how long it has stood by its current resident. Returns the new wait. */
 public static int step(ResidentEntity w,int waited,long now){
  var l=(ServerLevel)w.level();var data=SettlementData.get(l.getServer());var e=data.entry(w.settlementId());if(e==null)return 0;var b=e.settlement().workplace(w.getUUID());if(!Dining.restaurant(b))return 0;
  var st=Dining.state(l,e.settlement().id());var t=st.trips.get(w.getUUID());int level=BuildingLevels.level(l,e,b);var chestAt=LogisticsRoutes.position(e,b);
  if(t==null){
   w.displayWorkItem(ItemStack.EMPTY);
   if(!Dining.day(l,e)){w.workStatus("courier_idle_night");return 0;}
   if(level<4||posts(level)==0){w.workStatus("courier_idle");return 0;}
   boolean cart=mayTakeCart(l,e,b);int radius=cart?Dining.COURIER_CART_RADIUS:Dining.COURIER_RADIUS,cap=cart?Dining.CART_PORTIONS:Dining.HAND_PORTIONS;
   var targets=targets(l,e,b,now,radius);if(targets.isEmpty()){w.workStatus("courier_idle");return 0;}
   if(Dining.dishRations(l,e,b)<Population.MEAL_NUTRITION){w.workStatus("courier_no_food");return 0;}
   t=new CompoundTag();t.putUUID("id",UUID.randomUUID());t.putUUID("building",b.id());t.putString("stage","load");t.putBoolean("cart",cart);t.put("load",new ListTag());t.putLong("started",l.getGameTime());
   var list=new ListTag();for(var r:targets){if(list.size()>=cap)break;var q=new CompoundTag();q.putUUID("r",r.id());q.putLong("until",now+Dining.RESERVE_TICKS);list.add(q);}t.put("targets",list);
   st.trips.put(w.getUUID(),t);Dining.save(st);
   w.workStatus(level>=5&&!cart&&ResearchKnobs.done(l,e).contains(CART_RESEARCH)?"courier_no_dog":"courier_loading");return 0;}
  var id=t.getUUID("id");var stage=t.getString("stage");var load=t.getList("load",Tag.TAG_COMPOUND);
  if(stage.equals("load")){
   if(!near(w,chestAt)){w.workStatus("courier_loading");w.getNavigation().moveTo(chestAt.getX()+1.5,chestAt.getY(),chestAt.getZ()+.5,.8);return 0;}
   w.getNavigation().stop();var chest=Dining.chest(l,e,b);int need=t.getList("targets",Tag.TAG_COMPOUND).size()*Population.MEAL_NUTRITION;
   while(rations(load)+t.getInt("open")<need){var op=Settlement.childId(id,"take/"+t.getInt("takes"));var got=WorldJournal.recoverAmount(l,op);
    if(got.isEmpty()&&!WorldJournal.exists(l,op)&&chest!=null)for(int slot=0;slot<chest.getContainerSize();slot++){var s=chest.getItem(slot);if(!Dining.dish(s))continue;int ration=Population.nutrition(s);
     int want=Math.min(s.getCount(),(need-rations(load)-t.getInt("open")+ration-1)/ration);got=WorldJournal.takeAmount(l,op,chestAt,slot,s.copy(),Math.max(1,want));break;}
    if(got.isEmpty())break;load.add(got.save(new CompoundTag()));t.putInt("takes",t.getInt("takes")+1);Dining.save(st);}
   if(load.isEmpty()){st.trips.remove(w.getUUID());Dining.save(st);w.workStatus("courier_no_food");return 0;}
   if(t.getBoolean("cart")){var cart=CartHitch.near(l,e,chestAt,12);var dogs=VillageDogs.available(l,e);
    if(cart!=null&&!dogs.isEmpty()&&CartHitch.hitch(l,e,cart,w).isEmpty()){t.putUUID("cartId",cart.getUUID());t.putUUID("dog",dogs.get(0));VillageDogs.follow(l,e,dogs.get(0),w);}
    else{t.putBoolean("cart",false);var q=t.getList("targets",Tag.TAG_COMPOUND);while(q.size()>Dining.HAND_PORTIONS)q.remove(q.size()-1);}}
   t.putString("stage","deliver");Dining.save(st);w.workStatus("courier_delivering");return 0;}
  if(stage.equals("deliver")){
   var q=t.getList("targets",Tag.TAG_COMPOUND);
   while(!q.isEmpty()){var r=e.settlement().resident(((CompoundTag)q.get(0)).getUUID("r"));
    if(r!=null&&r.alive()&&r.lastMeal()+Population.MEAL_INTERVAL<=now+Dining.LOOKAHEAD&&!st.invites.containsKey(r.id())&&l.getEntity(r.id()) instanceof ResidentEntity)break;
    q.remove(0);Dining.save(st);waited=0;}
   if(q.isEmpty()||rations(load)+t.getInt("open")<Population.MEAL_NUTRITION||!Dining.day(l,e)){t.putString("stage","return");Dining.save(st);return 0;}
   var r=e.settlement().resident(((CompoundTag)q.get(0)).getUUID("r"));var npc=(ResidentEntity)l.getEntity(r.id());
   w.displayWorkItem(load.isEmpty()?ItemStack.EMPTY:ItemStack.of(load.getCompound(0)));
   var cart=t.hasUUID("cartId")&&l.getEntity(t.getUUID("cartId")) instanceof CartEntity c?c:null;
   if(w.distanceToSqr(npc)>4){w.workStatus(cart!=null&&cart.blocked()?"courier_cart_blocked":"courier_delivering");w.getNavigation().moveTo(npc,.8);return 0;}
   w.getNavigation().stop();w.getLookControl().setLookAt(npc);w.workStatus("courier_serving");
   if(waited+10<Dining.SERVE_TICKS)return waited+10;
   serve(l,e,st,t,r);return 0;}
  // return: what was not opened goes back into the restaurant's chest through the journal.
  if(!near(w,chestAt)){w.workStatus("courier_returning");w.getNavigation().moveTo(chestAt.getX()+1.5,chestAt.getY(),chestAt.getZ()+.5,.8);return 0;}
  w.getNavigation().stop();finish(l,e,st,w.getUUID(),t);w.displayWorkItem(ItemStack.EMPTY);w.workStatus("courier_idle");return 0;
 }
 /** A portion from the courier's load: the open dish first, then one more dish opened; the meal is eaten at its own due. */
 static boolean serve(ServerLevel l,SettlementData.Entry e,Dining.State st,CompoundTag t,Resident r){
  var load=t.getList("load",Tag.TAG_COMPOUND);int meal=Population.MEAL_NUTRITION,eaten=Math.min(t.getInt("open"),meal),open=t.getInt("open")-eaten;
  while(eaten<meal&&!load.isEmpty()){var s=ItemStack.of(load.getCompound(0));int ration=Population.nutrition(s);s.shrink(1);if(s.isEmpty())load.remove(0);else load.set(0,s.save(new CompoundTag()));
   int take=Math.min(ration,meal-eaten);eaten+=take;open+=ration-take;t.putInt("opened",t.getInt("opened")+1);}
  if(eaten<=0)return false;
  long due=r.lastMeal()+Population.MEAL_INTERVAL;r.ate(due);t.putInt("open",Math.min(Dining.LEFTOVER_MAX,open));t.putInt("served",t.getInt("served")+1);
  var q=t.getList("targets",Tag.TAG_COMPOUND);for(int i=0;i<q.size();i++)if(q.getCompound(i).getUUID("r").equals(r.id())){q.remove(i);break;}
  var b=e.settlement().buildings().stream().filter(x->x.id().equals(t.getUUID("building"))).findFirst().orElse(null);if(b!=null)Dining.hall(st,b.id()).served++;
  Dining.save(st);SettlementData.get(l.getServer()).setDirty();return true;
 }
 /** The end of a trip: the unopened dishes back into the chest (one journal deposit each), the cart unhitched, the dog sent home. */
 static boolean finish(ServerLevel l,SettlementData.Entry e,Dining.State st,UUID worker,CompoundTag t){
  var b=e.settlement().buildings().stream().filter(x->x.id().equals(t.getUUID("building"))).findFirst().orElse(null);var load=t.getList("load",Tag.TAG_COMPOUND);
  if(!load.isEmpty()){
   // The restaurant's chest first; a restaurant gone (or its chest full) — the pantries: a load is never dropped with its trip.
   var to=new ArrayList<BlockPos>();var own=b==null?null:Dining.chest(l,e,b);if(own!=null)to.add(own.getBlockPos());for(var c:Population.pantries(l,e))to.add(c.getBlockPos());
   if(to.isEmpty())return false;
   while(!load.isEmpty()){var s=ItemStack.of(load.getCompound(0));var op=Settlement.childId(t.getUUID("id"),"back/"+t.getInt("backs"));
    boolean put=false;for(var at:to)if(WorldJournal.deposit(l,op,at,s)){put=true;break;}
    if(!put)return false;load.remove(0);t.putInt("backs",t.getInt("backs")+1);Dining.save(st);}}
  if(t.hasUUID("cartId")&&l.getEntity(t.getUUID("cartId")) instanceof CartEntity cart)CartHitch.unhitch(cart);
  if(t.hasUUID("dog"))VillageDogs.release(l,e,t.getUUID("dog"));
  st.trips.remove(worker);Dining.save(st);return true;
 }
 /** Dining.tick: a trip whose courier died or lost its post has its load returned (the courier's custody ends with it). */
 static boolean housekeeping(ServerLevel l,SettlementData.Entry e,Dining.State st,long now){boolean changed=false;
  for(var en:List.copyOf(st.trips.entrySet())){var w=en.getKey();var t=en.getValue();var r=e.settlement().resident(w);var b=e.settlement().workplace(w);
   boolean gone=r==null||!r.alive()||!courier(e,w)||b==null||!b.id().equals(t.getUUID("building"));
   if(gone&&finish(l,e,st,w,t))changed=true;}
  return changed;}
 /** The couriers' part of a restaurant's card: posts at its level, couriers posted, their trips, the cart and the dog. */
 public static CompoundTag card(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level){
  var t=new CompoundTag();t.putInt("posts",posts(level));t.putInt("posted",posted(e,b));t.putInt("radius",Dining.COURIER_RADIUS);
  var st=Dining.state(l,e.settlement().id());var list=new ListTag();
  for(var r:e.settlement().residents()){if(!courier(e,r.id())||!e.settlement().workplace(r.id()).id().equals(b.id()))continue;var c=new CompoundTag();
   c.putString("name",l.getEntity(r.id()) instanceof ResidentEntity npc?npc.getName().getString():"");c.putString("status",l.getEntity(r.id()) instanceof ResidentEntity npc?npc.workStatus():"");
   var trip=st.trips.get(r.id());if(trip!=null){c.putInt("carrying",rations(trip.getList("load",Tag.TAG_COMPOUND))/Population.MEAL_NUTRITION);c.putInt("targets",trip.getList("targets",Tag.TAG_COMPOUND).size());c.putBoolean("cart",trip.hasUUID("cartId"));}
   list.add(c);}
  t.put("list",list);t.putString("cart",level>=5?cartRefusal(l,e,b):"level");
  return t;}
}
