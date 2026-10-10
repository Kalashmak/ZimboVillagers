package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Wolf;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
/** AD-138 IV: the wolves of a village — one registry for every branch that uses them (the rescue quest of AD-140 brings them in, the
 *  restaurant's dog cart of AD-139 will draw on them, grazing V and the cull VI will send them). Owner rule: a village's wolves appear only
 *  after their quest, their building (the kennel annex beside the yard) and their research level; nothing here spawns or tames a wolf.
 *  A wolf stays its player's pet and also carries the village's mark ({@link #TAG}, the village; {@link #KENNEL}, the kennel it lives in).
 *  The registry is a file of the village (data/astra-kennel/&lt;village&gt;.bin: schema 1, wolves), so the settlement's schema is untouched;
 *  a wolf leaves it when it dies. */
public final class VillageWolves {
 private VillageWolves(){}
 /** Wolves one kennel houses (its three straw beds and the fourth by the bin). */
 public static final int CAPACITY=4;
 /** Persistent-data marks of a village's wolf. */
 public static final String TAG="AstraWolves",KENNEL="AstraKennel";
 public static final String TYPE="kennel_annex";
 public static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-kennel/"+village+".bin");}
 /** The village's kennel annex, once built (null before). */
 public static Settlement.Building kennel(SettlementData.Entry e){
  var s=e.settlement();for(var b:s.buildings())if(b.type().equals(TYPE)&&s.annexParent(b.id())!=null)return b;return null;
 }
 private static CompoundTag record(ServerLevel l,UUID village){var p=path(l,village);return Files.exists(p)?NbtRecord.read(p):new CompoundTag();}
 /** The wolves enlisted in this village, in the order they came. */
 public static List<UUID> wolves(ServerLevel l,SettlementData.Entry e){
  var out=new ArrayList<UUID>();for(var raw:record(l,e.settlement().id()).getList("wolves",Tag.TAG_INT_ARRAY))out.add(NbtUtils.loadUUID(raw));return out;
 }
 private static void store(ServerLevel l,UUID village,List<UUID> wolves){
  var t=new CompoundTag();t.putInt("schema",1);var list=new ListTag();for(var w:wolves)list.add(NbtUtils.createUUID(w));t.put("wolves",list);NbtRecord.write(path(l,village),t);
 }
 /** Places left in the village's kennel: none without a built kennel. */
 public static int room(ServerLevel l,SettlementData.Entry e){return kennel(e)==null?0:Math.max(0,CAPACITY-wolves(l,e).size());}
 /** Takes a living wolf into the village's kennel: marks it (the village, the kennel), keeps it from despawning and writes it in the
  *  registry. False when there is no room, no such kennel of this village, or the wolf is dead or already another village's. A wolf of
  *  this village already enlisted is true and changes nothing. */
 public static boolean enlist(ServerLevel l,SettlementData.Entry e,Wolf wolf,Settlement.Building kennel){
  if(wolf==null||!wolf.isAlive()||kennel==null||!kennel.type().equals(TYPE)||e.settlement().annexParent(kennel.id())==null)return false;
  var village=e.settlement().id();var data=wolf.getPersistentData();
  if(data.hasUUID(TAG)&&!data.getUUID(TAG).equals(village))return false;
  var list=wolves(l,e);if(list.contains(wolf.getUUID()))return true;
  if(list.size()>=CAPACITY)return false;
  data.putUUID(TAG,village);data.putUUID(KENNEL,kennel.id());wolf.setPersistenceRequired();
  list.add(wolf.getUUID());store(l,village,list);attach(wolf);return true;
 }
 /** Gives a village's wolf its kennel life (WolfKennelGoal) once: on enlisting and whenever it joins a level (the goal is not saved). */
 public static void attach(Wolf wolf){
  if(village(wolf)==null||wolf.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof WolfKennelGoal))return;
  // Above the vanilla follow-owner goal (6) and wandering: with its owner away the wolf keeps to the yard.
  wolf.goalSelector.addGoal(0,new CaravanDogGoal(wolf));wolf.goalSelector.addGoal(3,new WolfPatrolGoal(wolf));wolf.goalSelector.addGoal(4,new WolfKennelGoal(wolf));
 }
 /** Raw meat a wolf eats: one piece a day (spec §9.1), from the kennel's bin. */
 public static final Set<net.minecraft.world.item.Item> MEAT=Set.of(net.minecraft.world.item.Items.BEEF,net.minecraft.world.item.Items.PORKCHOP,
  net.minecraft.world.item.Items.MUTTON,net.minecraft.world.item.Items.CHICKEN,net.minecraft.world.item.Items.RABBIT);
 /** The bin keeps two days of meat for a full kennel. */
 public static final int MEAT_STOCK=2*CAPACITY;
 /** Persistent-data mark of the village day a wolf last ate. */
 public static final String FED="AstraFed";
 static boolean meat(net.minecraft.world.item.ItemStack s){return MEAT.contains(s.getItem())&&!s.hasTag();}
 /** A village's day, by its clock (it runs when the world's daylight cycle is stopped too). */
 public static long day(ServerLevel l){return SettlementData.get(l.getServer()).clock().ticks()/24000L;}
 /** The wolf ate today or yesterday: it works (a hungry wolf does not die — it only stops working, spec §9.1). */
 public static boolean fed(ServerLevel l,Wolf wolf){var d=wolf.getPersistentData();return d.contains(FED)&&d.getLong(FED)>=day(l)-1;}
 /** The wolf's meal of today from its kennel's bin, once a day, through the journal under an id made of the wolf and the day (a crash
  *  or a reload never takes the piece twice). True when it has eaten today, now or before. */
 public static boolean eat(ServerLevel l,SettlementData.Entry e,Wolf wolf){
  long today=day(l);var d=wolf.getPersistentData();if(d.contains(FED)&&d.getLong(FED)>=today)return true;
  var kennel=kennel(e);var chest=kennel==null?null:LogisticsRoutes.chest(l,e,kennel);if(chest==null)return false;
  var pos=LogisticsRoutes.position(e,kennel);var op=Settlement.childId(wolf.getUUID(),"meal/"+today);
  for(int slot=0;slot<chest.getContainerSize();slot++){var st=chest.getItem(slot);if(!meat(st))continue;
   if(org.villageastra.persistence.WorldJournal.takeAmount(l,op,pos,slot,st.copy(),1).isEmpty())return false;
   d.putLong(FED,today);wolf.heal(4);return true;}
  return false;
 }
 /** What the yard asks the porters for its kennel: raw meat up to MEAT_STOCK in the bin, while wolves live there. */
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){
  var kennel=kennel(e);if(kennel==null||!yard.id().equals(e.settlement().annexParent(kennel.id()))||wolves(l,e).isEmpty())return List.of();
  var chest=LogisticsRoutes.chest(l,e,kennel);if(chest==null)return List.of();
  int missing=MEAT_STOCK-LogisticsRoutes.count(chest,VillageWolves::meat);if(missing<=0)return List.of();
  return List.of(new Workshops.Want(net.minecraft.world.item.crafting.Ingredient.of(MEAT.stream().map(net.minecraft.world.item.ItemStack::new)),missing,kennel.id()));
 }
 // ---- the restaurant's dog (AD-139): a kennel wolf goes along with a courier's cart ----------------------------------------------
 /** In memory: the courier each dog goes with (after a restart a courier's trip releases its dog again). */
 private static final Map<UUID,UUID> WITH=new java.util.concurrent.ConcurrentHashMap<>();
 /** AD-141 (logistics, the coordinator's contract): the cart a dog pulls and the place it is sent to. A dog with a courier, a cart or a
  *  place is not free until it is released; release unhitches its cart. In memory, as WITH. */
 private static final Map<UUID,UUID> HARNESS=new java.util.concurrent.ConcurrentHashMap<>();
 private static final Map<UUID,net.minecraft.core.BlockPos> SENT=new java.util.concurrent.ConcurrentHashMap<>();
 /** The place this wolf is sent to, or null. */
 public static net.minecraft.core.BlockPos sentTo(Wolf wolf){return SENT.get(wolf.getUUID());}
 private static boolean busy(UUID id){return WITH.containsKey(id)||HARNESS.containsKey(id)||SENT.containsKey(id);}
 /** Physical and economic availability, before a specialist duty reserves the wolf. */
 private static boolean fit(ServerLevel l,Wolf w){return w.isAlive()&&!w.isOrderedToSit()&&!w.isLeashed()&&!w.isPassenger()&&w.getTarget()==null
  &&!CaravanDogs.reserved(l.getServer(),w.getUUID())&&!CULL.containsKey(w.getUUID())&&fed(l,w);}
 public static boolean ready(ServerLevel l,Wolf w){return !busy(w.getUUID())&&fit(l,w);}
 /** Hitches a kennel wolf of this village to a cart: the cart rolls behind it from now on. */
 public static boolean harnessDog(ServerLevel l,SettlementData.Entry e,UUID dog,CartEntity cart){
  if(cart==null||!wolves(l,e).contains(dog)||!(l.getEntity(dog) instanceof Wolf w)||!w.isAlive())return false;
  if(cart.puller()!=null&&!cart.puller().equals(dog)&&l.getEntity(cart.puller()) instanceof net.minecraft.world.entity.LivingEntity other&&other.isAlive())return false;
  cart.puller(dog);HARNESS.put(dog,cart.getUUID());return true;
 }
 /** Sends a kennel wolf of this village (and its cart) to a place; asked again with the same place, nothing changes. */
 public static boolean sendDog(ServerLevel l,SettlementData.Entry e,UUID dog,net.minecraft.core.BlockPos to){
  if(to==null||!wolves(l,e).contains(dog)||!(l.getEntity(dog) instanceof Wolf w)||!w.isAlive())return false;SENT.put(dog,to.immutable());return true;
 }
 /** The wolf is loaded, alive and within r of the place. */
 public static boolean nearDog(ServerLevel l,UUID dog,net.minecraft.core.BlockPos at,double r){
  return l.getEntity(dog) instanceof Wolf w&&w.isAlive()&&w.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)<=r*r;
 }
 /** Ends a dog's errand: off its courier, its place forgotten, its cart unhitched. */
 public static void releaseDog(ServerLevel l,UUID dog){
  WITH.remove(dog);SENT.remove(dog);var cart=HARNESS.remove(dog);
  if(cart!=null&&l.getEntity(cart) instanceof CartEntity c&&dog.equals(c.puller()))c.puller(null);
 }
 /** The courier this wolf goes with now, or null. */
 public static UUID courier(Wolf wolf){return WITH.get(wolf.getUUID());}
 /** The village's dogs as the restaurant sees them (VillageDogs): its kennel wolves, loaded, fed, free of a courier, not told to sit. */
 public static final VillageDogs.Source DOGS=new VillageDogs.Source(){
  @Override public List<UUID> free(ServerLevel l,SettlementData.Entry e){
   var out=new ArrayList<UUID>();
   for(var id:wolves(l,e))if(l.getEntity(id) instanceof Wolf w&&ready(l,w)&&!CartographyLadder.reserved(l,e,w)&&!MedicineDelivery.reservedWolf(l,e,w.getUUID()))out.add(id);
   return out;}
  @Override public void follow(ServerLevel l,SettlementData.Entry e,UUID dog,ResidentEntity courier){if(courier!=null&&wolves(l,e).contains(dog))WITH.put(dog,courier.getUUID());}
  @Override public void release(ServerLevel l,SettlementData.Entry e,UUID dog){releaseDog(l,dog);}
  // AD-147 (logistics): a kennel wolf pulls a cart, goes where it is sent, and says whether it is there.
  @Override public boolean harness(ServerLevel l,SettlementData.Entry e,UUID dog,CartEntity cart){return harnessDog(l,e,dog,cart);}
  @Override public boolean send(ServerLevel l,SettlementData.Entry e,UUID dog,net.minecraft.core.BlockPos to){return sendDog(l,e,dog,to);}
  @Override public boolean near(ServerLevel l,SettlementData.Entry e,UUID dog,net.minecraft.core.BlockPos at,double r){return nearDog(l,dog,at,r);}
  @Override public boolean pickupReady(ServerLevel l,SettlementData.Entry e,UUID dog,CartEntity cart){
   if(cart.puller()!=null&&!dog.equals(cart.puller())||!(l.getEntity(dog) instanceof Wolf w)||!e.settlement().id().equals(village(w))||!wolves(l,e).contains(dog)||!fit(l,w)
    ||WITH.containsKey(dog)||HARNESS.containsKey(dog)&&!cart.getUUID().equals(HARNESS.get(dog))
    ||SENT.containsKey(dog)&&!cart.blockPosition().equals(SENT.get(dog))||CartographyLadder.reserved(l,e,w)||MedicineDelivery.reservedWolf(l,e,dog))return false;
   return nearDog(l,dog,cart.blockPosition(),3);
  }
  /** AD-147: the kennel's wolves answer harness/send/near (WolfKennelGoal walks a sent wolf, its cart follows), so they pull carts. */
  @Override public boolean pulls(){return true;}
 };
 // ---- the cull of level VI: the machine gives a free wolf a surplus beast of a pen ---------------------------------------------
 /** In memory: the beast each wolf is to cull (a restart drops the order; the machine gives it again). */
 private static final Map<UUID,UUID> CULL=new java.util.concurrent.ConcurrentHashMap<>();
 public static void cull(Wolf wolf,net.minecraft.world.entity.animal.Animal beast){CULL.put(wolf.getUUID(),beast.getUUID());}
 public static UUID cullOrder(Wolf wolf){return CULL.get(wolf.getUUID());}
 public static void culled(Wolf wolf){CULL.remove(wolf.getUUID());}
 /** A beast some wolf is sent to cull already. */
 public static boolean culling(UUID beast){return CULL.containsValue(beast);}
 /** A kennel wolf free for work: loaded, fed, not with a courier, not told to sit, without a cull order. */
 public static Wolf freeWolf(ServerLevel l,SettlementData.Entry e){
  for(var id:wolves(l,e))if(l.getEntity(id) instanceof Wolf w&&ready(l,w)&&!CartographyLadder.reserved(l,e,w)&&!MedicineDelivery.reservedWolf(l,e,w.getUUID()))return w;
  return null;
 }
 // ---- AD-155 (Metallurgy IV): wolf armour -------------------------------------------------------------------------------------
 /** Persistent-data mark of a wolf's armour: the hits it still takes. */
 public static final String ARMOUR="AstraArmour";public static final int ARMOUR_HITS=64;
 /** Puts armour on a village wolf (a wolf of some village, or the player's own): the wolf takes half the damage of the next ARMOUR_HITS hits;
  *  its collar turns grey while it wears it. False for any other creature or a wolf already armoured. */
 public static boolean armour(Wolf wolf){
  if(wolf==null||!wolf.isAlive()||!wolf.isTame()||wolf.getPersistentData().getInt(ARMOUR)>0)return false;
  wolf.getPersistentData().putInt(ARMOUR,ARMOUR_HITS);wolf.setCollarColor(net.minecraft.world.item.DyeColor.GRAY);return true;
 }
 /** Damage an armoured wolf takes: half, and the armour wears by one hit (worn out, the collar turns red again). */
 public static float armoured(Wolf wolf,float amount){
  var d=wolf.getPersistentData();int left=d.getInt(ARMOUR);if(left<=0||amount<=0)return amount;
  if(left==1){d.remove(ARMOUR);wolf.setCollarColor(net.minecraft.world.item.DyeColor.RED);}else d.putInt(ARMOUR,left-1);
  return amount*.5F;
 }
 /** The village a wolf belongs to, or null. */
 public static UUID village(Wolf wolf){var d=wolf.getPersistentData();return d.hasUUID(TAG)?d.getUUID(TAG):null;}
 /** A village's wolf died: it leaves the registry (its kennel place is free for the next rescue). */
 public static void died(ServerLevel l,Wolf wolf){
  releaseDog(l,wolf.getUUID());CULL.remove(wolf.getUUID());var village=village(wolf);if(village==null)return;var e=SettlementData.get(l.getServer()).entry(village);if(e==null)return;
  var list=wolves(l,e);if(list.remove(wolf.getUUID()))store(l,village,list);
 }
}
