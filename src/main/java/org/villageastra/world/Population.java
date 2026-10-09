package org.villageastra.world;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
/** AD-031: food, births, growing up, school and the labor office. All numbers come from balance/population.json. */
public final class Population {
 public static final long MEAL_INTERVAL,BIRTH_INTERVAL,GROW,SCHOOL_REQUIRED,ASSIGN_INTERVAL,DRILL_REQUIRED;
 public static final int MEAL_NUTRITION,HUNGRY,STARVING,DEATH,DAMAGE,BIRTH_FOOD,SCHOOL_RADIUS,DRILL_RADIUS;
 /** AD-104 P2, owner decision 2 of 2026-09-19: rations a village meal counts per item id (domain/Rations); safe food not listed keeps its vanilla nutrition. */
 public static final Map<String,Integer> RATIONS;
 /** AD-095: at most this many recruits drill at once, so the village's other work goes on. */
 public static final int RECRUITS=2;
 static{
  JsonObject o;try(var s=Population.class.getResourceAsStream("/data/villageastra/balance/population.json")){if(s==null)throw new IllegalStateException("Missing population balance");o=JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}
  MEAL_INTERVAL=positive(o,"meal_interval_ticks");BIRTH_INTERVAL=positive(o,"birth_interval_ticks");GROW=positive(o,"child_grow_ticks");SCHOOL_REQUIRED=positive(o,"school_required_ticks");ASSIGN_INTERVAL=positive(o,"assign_interval_ticks");DRILL_REQUIRED=positive(o,"drill_required_ticks");DRILL_RADIUS=(int)positive(o,"drill_radius");
  MEAL_NUTRITION=(int)positive(o,"meal_nutrition");HUNGRY=(int)positive(o,"hungry_missed_meals");STARVING=(int)positive(o,"starving_missed_meals");DEATH=(int)positive(o,"death_missed_meals");DAMAGE=(int)positive(o,"starvation_damage");BIRTH_FOOD=(int)positive(o,"birth_food_nutrition_per_resident");SCHOOL_RADIUS=(int)positive(o,"school_radius");
  RATIONS=Rations.parse(o.getAsJsonObject("rations"));
  if(!(HUNGRY<STARVING&&STARVING<DEATH)||SCHOOL_REQUIRED>=GROW)throw new IllegalStateException("Inconsistent population balance");
 }
 private static long positive(JsonObject o,String key){long v=o.get(key).getAsLong();if(v<1)throw new IllegalStateException("Invalid "+key);return v;}
 private Population(){}
 /** Ordinary work stops after missed meals; farmers and the mayor keep solving the food problem (NEED-001). */
 // AD-152: the sick do not work (the mayor keeps office).
 public static boolean mayWork(Resident r){return r==null||(!r.sick()||r.profession()==Profession.MAYOR)&&(r.missedMeals()<HUNGRY||r.profession()==Profession.FARMER||r.profession()==Profession.MAYOR);}
 private static ServerLevel level(MinecraftServer server,SettlementData.Entry e){return server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(e.dimension())));}
 /** Stock chests that feed residents: warehouses first, then the town hall. */
 static List<OwnedChestEntity> pantries(ServerLevel l,SettlementData.Entry e){
  var result=new ArrayList<OwnedChestEntity>();
  for(var type:List.of("warehouse","town_hall"))for(var b:e.settlement().buildings())if(b.type().equals(type)){var c=LogisticsRoutes.chest(l,e,b);if(c!=null)result.add(c);}
  return result;
 }
 /** AD-111: where the pantries stand, in the order pantries() reads them: what a remote meal or a siege's hunger touch-loads first. */
 public static List<BlockPos> pantryPositions(SettlementData.Entry e){
  var result=new ArrayList<BlockPos>();
  for(var type:List.of("warehouse","town_hall"))for(var b:e.settlement().buildings())if(b.type().equals(type))result.add(LogisticsRoutes.position(e,b));
  return result;
 }
 /** AD-104 P2: rations one item gives a village meal, from the balance table (carrot 1, beetroot 2, bread 5), else its vanilla nutrition; food with
  *  effects feeds nobody. FoodProperties are only read, never changed, so the player's own hunger stays vanilla. Public so cards and tests count what meals eat. */
 public static int nutrition(ItemStack s){var food=s.getItem().getFoodProperties(s,null);return food==null||!food.getEffects().isEmpty()?0:Rations.value(RATIONS,BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),food.getNutrition());}
 /** Rations in the loaded pantries: what meals, births, the siege's hunger and the hand-bread gate count. */
 public static int storedNutrition(ServerLevel l,SettlementData.Entry e){int n=0;for(var c:pantries(l,e))for(int i=0;i<c.getContainerSize();i++)n+=nutrition(c.getItem(i))*c.getItem(i).getCount();return n;}
 public static void tick(MinecraftServer server,SettlementData data,SettlementData.Entry e){
  var l=level(server,e);if(l==null)return;long now=data.clock().ticks();boolean changed=false;
  changed|=serve(l,e,now);
  // AD-139: the restaurant's housekeeping (day count, the cooling leftover, freed seats, returned courier loads) — its own record, not the village's.
  // Population.tick already runs every 20 ticks of the village clock; a second gate on the level's game time (another clock, out of phase) never opened.
  Dining.tick(l,e,now);
  changed|=grow(l,e,now);
  changed|=Medicine.tick(l,e,now);
  // AD-121: a castle of V and VI lets its portcullis down in danger.
  CastleGate.tick(l,e);
  SmithyDelivery.tick(l,e);
  if(now%ASSIGN_INTERVAL<20){changed|=FarmingRelief.tick(l,e);changed|=assign(l,e,ResearchKnobs.builders(l,e));changed|=toStore(l,e);}
  changed|=birth(l,e,now)!=null;
  if(changed)data.setDirty();
 }
 private static final Map<UUID,Long> WARNED=new HashMap<>();
 /** AD-111: every living resident's due meal. A loaded village eats as before. A far village that matters to the player (TouchLoad.relevant), or one
  *  partly loaded, has its pantry chunks touch-loaded and eats its real stock in one journal batch; a refused or failed touch defers the meal
  *  (lastMeal untouched, no intent written), so a later call eats it through the same id at the same due. Any other far village is frozen until
  *  P8: each due passes with Resident.skipMeal, nothing eaten and nothing missed. Returns whether anything changed. */
 public static boolean serve(ServerLevel l,SettlementData.Entry e,long now){
  boolean changed=false;var alive=new ArrayList<Resident>();
  for(var r:List.copyOf(e.settlement().residents()))if(r.alive()){if(r.lastMeal()<0)changed|=meal(l,e,r,now);else if(now>=r.lastMeal()+MEAL_INTERVAL)alive.add(r);}
  if(alive.isEmpty())return changed;
  // Scarce meals go to those who have missed the most first. Registry order
  // must not leave the same builder and porter without food at every due.
  alive.sort(Comparator.comparingInt(Resident::missedMeals).reversed().thenComparingLong(Resident::lastMeal).thenComparing(Resident::id));
  var ps=pantryPositions(e);
  if(ps.isEmpty()||ps.stream().allMatch(l::hasChunkAt)){for(var r:alive)changed|=meal(l,e,r,now);return changed;}
  // A due meal whose intent is already written (a crash between a batch's flush and its commit) is finished through the touch, never skipped.
  boolean written=alive.stream().anyMatch(r->WorldJournal.exists(l,Settlement.childId(r.id(),"meal/"+(r.lastMeal()+MEAL_INTERVAL)+"/0")));
  if(written||TouchLoad.relevant(l.getServer(),e)||ps.stream().anyMatch(l::hasChunkAt)){
   var touch=TouchLoad.ensureAll(l,ps);
   if(touch!=TouchLoad.Touch.OK){
    if(touch==TouchLoad.Touch.FAILED){long t=l.getServer().getTickCount();var last=WARNED.get(e.settlement().id());if(last==null||t<last||t-last>=TouchLoad.WARN_TICKS){WARNED.put(e.settlement().id(),t);com.mojang.logging.LogUtils.getLogger().warn("ZimboVillagers deferred the meals of village {}: its pantry chunks could not be loaded",e.settlement().id());}}
    return changed;
   }
   return WorldJournal.batch(l,()->{boolean ate=false;for(var r:alive)ate|=meal(l,e,r,now);return ate;})||changed;
  }
  for(var r:alive)r.skipMeal(r.lastMeal()+MEAL_INTERVAL);
  return true;
 }
 /** One due meal: real food leaves the pantry through the journal, or the meal is missed. Whole items go until the meal has MEAL_NUTRITION rations,
  *  so 3 beetroots make one meal; Rations.itemsPerMeal counts the same for the farm card. AD-104 P2: the items one slot gives a meal leave it in
  *  one withdrawal (5 carrots are one journal operation and one chunk flush, not five); a replay returns what its receipt recorded, whatever the count. */
 public static boolean meal(ServerLevel l,SettlementData.Entry e,Resident r,long now){
  if(r.lastMeal()<0){r.ate(now);return true;}
  long due=r.lastMeal()+MEAL_INTERVAL;if(now<due)return false;
  // AD-139: the restaurant first — an invitation to its hall or a courier's trip holds the meal (WAIT); otherwise, or once they lapse, the pantry
  // eats it below with this same due, so the hall never costs a meal.
  if(Dining.meal(l,e,r,now,due)==Dining.Outcome.WAIT)return false;
  // AD-139: after the pantries, the dishes the restaurant keeps for its hall (Dining.keep) feed a meal the pantries cannot: no meal is missed
  // (night, children, a far resident, a lapsed invitation) while the village's food stands in the restaurant's chest.
  int eaten=0,take=0;var chests=pantries(l,e);var larders=Dining.larders(l,e);chests.addAll(larders);int stored=0;for(var c:chests)for(int i=0;i<c.getContainerSize();i++)if(!larders.contains(c)||Dining.dish(c.getItem(i)))stored+=nutrition(c.getItem(i))*c.getItem(i).getCount();
  // AD-111: with every pantry loaded, an intent that exists and replays to nothing was never applied (a batch's later intents after the chest changed): its id is spent.
  boolean all=pantryPositions(e).stream().allMatch(l::hasChunkAt);
  for(var c:chests){
   for(int slot=0;slot<c.getContainerSize()&&eaten<MEAL_NUTRITION;slot++){
    var id=Settlement.childId(r.id(),"meal/"+due+"/"+take);var got=WorldJournal.recoverAmount(l,id);
    if(got.isEmpty()&&all&&WorldJournal.exists(l,id)){take++;slot--;continue;}
    if(got.isEmpty()&&!WorldJournal.exists(l,id)){var s=c.getItem(slot);int ration=nutrition(s);if(ration<=0||larders.contains(c)&&!Dining.dish(s))continue;if(eaten==0&&stored<MEAL_NUTRITION)break;got=WorldJournal.takeAmount(l,id,c.getBlockPos(),slot,s.copy(),Math.min(s.getCount(),Rations.itemsPerMeal(ration,MEAL_NUTRITION-eaten)));}
    if(got.isEmpty())continue;eaten+=nutrition(got)*got.getCount();take++;slot--;
   }
   if(eaten>=MEAL_NUTRITION)break;
  }
  if(eaten>0){r.ate(due);return true;}
  r.missedMeal(due);
  var entity=l.getEntity(r.id()) instanceof ResidentEntity npc?npc:null;
  if(r.missedMeals()>=DEATH){if(entity!=null)entity.hurt(l.damageSources().starve(),Float.MAX_VALUE);else r.die();}
  else if(r.missedMeals()>=STARVING&&entity!=null)entity.hurt(l.damageSources().starve(),DAMAGE);
  return true;
 }
 /** Children grow up after the configured active time; school attendance before that educates them. AD-151 (owner's ladder): a class holds the
  *  school's seats (core effect pupils 1/2/2/4/8/8), the eldest children in the room first; from III the eldest of them sit in its military
  *  class (cadets 0/0/1/1/2/2) and grow up trained for arms; the library of VI teaches twice as fast (lesson). */
 public static boolean grow(ServerLevel l,SettlementData.Entry e,long now){
  boolean changed=false;var school=schoolStation(l,e);int lesson=school==null?0:lessonCredit(l,e);
  var taught=school==null?Set.<java.util.UUID>of():classroom(l,e,school);
  for(var r:e.settlement().residents()){
   if(!r.alive())continue;
   if(taught.contains(r.id())){r.attendSchool(lesson);changed=true;}
   if(r.life()==Resident.Life.ADULT){if(r.completeSchool(SCHOOL_REQUIRED))changed=true;continue;}
   if(r.life()!=Resident.Life.CHILD)continue;
   if(r.born()>=0&&now-r.born()>=GROW){boolean cadet=r.cadet();if(r.schoolTicks()>=SCHOOL_REQUIRED)r.educate();r.growUp();
    if(cadet&&r.educated())r.trainMilitary();
    if(l.getEntity(r.id()) instanceof ResidentEntity npc)npc.refreshLife(r);changed=true;}
  }
  changed|=drill(l,e,l.getDayTime()%24000<12000);
  return changed;
 }
 /** AD-095: recruits drill at the barracks by day; that time, like school, is what makes a guard, an archer or a soldier. */
 public static boolean drill(ServerLevel l,SettlementData.Entry e,boolean day){
  boolean changed=false;var ground=drillGround(e);int credit=ground==null?0:drillCredit(l,e);
  for(var r:e.settlement().residents()){
   if(!r.alive()||!r.recruit()||r.military())continue;
   if(ground!=null&&day&&l.getEntity(r.id()) instanceof ResidentEntity recruit&&recruit.distanceToSqr(ground.getX()+.5,ground.getY(),ground.getZ()+.5)<=DRILL_RADIUS*DRILL_RADIUS){r.drill(credit);changed=true;}
   if(r.drillTicks()>=DRILL_REQUIRED&&r.trainMilitary())changed=true;
  }
  return changed;
 }
 /** AD-112: the drill time one pass at the barracks gives a recruit, by the settlement's best barracks. */
 public static int drillCredit(ServerLevel l,SettlementData.Entry e){return drillCredit(BuildingLevels.best(l,e,"barracks"));}
 public static int drillCredit(int level){return org.villageastra.domain.CoreEffects.value("barracks","drill",level);}
 /** AD-151: the children taught this pass — those in the room, eldest first, as many as the best school has seats; the eldest of them fill
  *  its military class. */
 public static Set<java.util.UUID> classroom(ServerLevel l,SettlementData.Entry e,BlockPos school){return classroom(l,e,school,BuildingLevels.best(l,e,"school"));}
 public static Set<java.util.UUID> classroom(ServerLevel l,SettlementData.Entry e,BlockPos school,int level){
  int seats=pupils(level),cadets=cadets(level);
  var present=new ArrayList<Resident>();
  for(var r:e.settlement().residents())if(r.alive()&&!r.educated()&&(r.life()==Resident.Life.CHILD||continuingPupil(l,e,r))&&l.getEntity(r.id()) instanceof ResidentEntity child
    &&child.distanceToSqr(school.getX()+.5,school.getY(),school.getZ()+.5)<=SCHOOL_RADIUS*SCHOOL_RADIUS)present.add(r);
  present.sort(Comparator.comparingLong((Resident x)->x.born()).thenComparing(x->x.id()));
  var out=new LinkedHashSet<java.util.UUID>();
  for(int i=0;i<Math.min(seats,present.size());i++){var r=present.get(i);out.add(r.id());if(i<cadets&&r.life()==Resident.Life.CHILD)r.enlistCadet();}
  return out;
 }
 /** Adults may finish a course begun in childhood, after completing entrusted physical work. */
 public static boolean continuingPupil(ServerLevel l,SettlementData.Entry e,Resident r){
  if(!r.alive()||r.life()!=Resident.Life.ADULT||r.educated()||r.schoolTicks()<=0||r.schoolTicks()>=SCHOOL_REQUIRED
    ||r.profession()==Profession.TEACHER||r.sick()||r.missedMeals()>=HUNGRY
    ||!(l.getEntity(r.id()) instanceof ResidentEntity body)||body.escortPlayer()!=null||body.blockWork()!=null
    ||CargoCustody.pending(l.getServer(),r.id())||NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,r.id()))
    ||PorterWork.active(PorterWork.inspect(l,r.id())))return false;
  return e.settlement().buildings().stream().map(b->Workshops.inspect(l,b.id())).noneMatch(t->t.getBoolean("physicalSmelt")
    &&!t.getString("stage").equals("idle")&&t.hasUUID("worker")&&t.getUUID("worker").equals(r.id()));
 }
 /** Reserve only the school's available seats for the oldest unfinished adult courses. */
 public static boolean continuingSeat(ServerLevel l,SettlementData.Entry e,Resident r){
  return e.settlement().residents().stream().filter(x->continuingPupil(l,e,x))
    .sorted(Comparator.comparingLong((Resident x)->x.born()).thenComparing(Resident::id))
    .limit(pupils(BuildingLevels.best(l,e,"school"))).anyMatch(x->x.id().equals(r.id()));
 }
 /** AD-151: the seats of a class and of its military class at a school's working level (core effects pupils, cadets). */
 public static int pupils(int level){return org.villageastra.domain.CoreEffects.value("school","pupils",level);}
 public static int cadets(int level){return org.villageastra.domain.CoreEffects.value("school","cadets",level);}
 /** AD-112: the school time one pass in class gives a child, by the settlement's best school. */
 public static int lessonCredit(ServerLevel l,SettlementData.Entry e){return lessonCredit(BuildingLevels.best(l,e,"school"));}
 public static int lessonCredit(int level){return org.villageastra.domain.CoreEffects.value("school","lesson",level);}
 /** AD-095: the drill ground — the station of the village's barracks. */
 public static BlockPos drillGround(SettlementData.Entry e){
  for(var b:e.settlement().buildings())if(b.type().equals("barracks"))return LogisticsRoutes.position(e,b);
  return null;
 }
 /** Station of a school whose assigned teacher is physically present. */
 public static BlockPos schoolStation(ServerLevel l,SettlementData.Entry e){
  for(var b:e.settlement().buildings()){if(!b.type().equals("school"))continue;var station=LogisticsRoutes.position(e,b);
   for(var r:e.settlement().residents())if(r.alive()&&Population.mayWork(r)&&!CargoCustody.pending(l.getServer(),r.id())&&r.profession()==Profession.TEACHER&&e.settlement().workplace(r.id())!=null&&e.settlement().workplace(r.id()).id().equals(b.id())&&l.getEntity(r.id()) instanceof ResidentEntity teacher&&teacher.escortPlayer()==null&&teacher.distanceToSqr(station.getX()+.5,station.getY(),station.getZ()+.5)<=SCHOOL_RADIUS*SCHOOL_RADIUS)return station;}
  return null;
 }
 private static int free(Settlement s,Settlement.Home h){return h.usable()?(int)(h.capacity()-s.occupancy(h.id())):0;}
 /** A rare birth into a free home place when adults, housing and a food reserve exist; spawns the real child entity. */
 public static Resident birth(ServerLevel l,SettlementData.Entry e,long now){
  // AD-123: Housing III shortens the interval (HousingLadder.birthInterval, balance/housing.json).
  var s=e.settlement();if(s.lastBirth()>=0&&now-s.lastBirth()<HousingLadder.birthInterval(l,e))return null;
  long adults=s.residents().stream().filter(r->r.alive()&&r.life()==Resident.Life.ADULT&&r.home()!=null).count();if(adults<2)return null;
  if(s.residents().stream().anyMatch(r->r.alive()&&r.missedMeals()>=HUNGRY))return null;
  if(s.governance().playerMayor()==null&&MayorPlanner.foodShortage(l,e))return null;
  long alive=s.residents().stream().filter(Resident::alive).count();if(storedNutrition(l,e)<BIRTH_FOOD*(alive+1))return null;
  var home=s.homes().stream().filter(h->free(s,h)>0).findFirst().orElse(null);if(home==null)return null;
  var building=s.buildings().stream().filter(b->b.id().equals(home.id())).findFirst().orElse(null);if(building==null)return null;
  // AD-111: a touched pantry chunk is loaded but asleep, and its entities are still loading: a child is born only where entities tick.
  var cell=HousingLadder.spawnCell(building.type(),0);var spawn=BuildingPlacement.at(e,building,cell.getX(),cell.getY(),cell.getZ());if(!l.isLoaded(spawn)||!l.hasChunkAt(spawn)||!TouchLoad.ticking(l,spawn))return null;
  var child=new Resident(Settlement.childId(s.id(),"born/"+now),Resident.Life.CHILD,false,null,null,-1);
  var entity=VillageAstra.RESIDENT.get().create(l);if(entity==null||l.getEntity(child.id())!=null)return null;
  s.admit(child,home.id());child.born(now);s.recordBirth(now);
  entity.bind(s.id(),child);entity.refreshLife(child);entity.moveTo(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,0,0);
  if(!l.addFreshEntity(entity))throw new IllegalStateException("Child entity spawn failed");
  return child;
 }
 private record Opening(Profession role,Settlement.Building building){}
 /** Labor office: every unassigned adult takes the most needed implemented job (builder, porter, food, school, workshops). */
 public static boolean assign(SettlementData.Entry e){return assign(e,1);}
 /** World-aware staffing entry point; legacy callers retain their ordinary labor-office pass. */
 public static boolean assign(ServerLevel l,SettlementData.Entry e,int builders){boolean changed=assign(e,builders);changed=EducationStaffing.tick(l,e)||changed;changed=KitchenRelief.tick(l,e)||changed;changed=ResourceIllnessRelief.tick(l,e)||changed;return ResourceStaffing.tick(l,e)||changed;}
 /** AD-153: with the builders the hall posts by the construction research (ResearchKnobs.builders: 1, then 2/2/4/6/8/10). The first builder
  *  comes before any other post, the others after every workplace has its first worker; research is never unlearned, so none is let go. */
 public static boolean assign(SettlementData.Entry e,int builders){
  var s=e.settlement();boolean changed=false;
  // AD-130: a farm that needs fewer hands than it has (level VI: none, the machine works every field) lets the last posted go first.
  // AD-130/AD-138/AD-139: a workplace keeps no more workers of each trade than its level takes (a farm's farmers, a yard's keepers,
  // the restaurant's cook - none at VI, the machine cooks - and its couriers; Staff).
  for(var b:s.buildings()){var others=Staff.others(work(b.type()));
   if(slots(s,b)!=1||!others.isEmpty()){var own=role(b.type());var crew=s.employees(b.id()).stream().filter(id->others.isEmpty()||s.resident(id)!=null&&s.resident(id).profession()==own).toList();
    for(int i=crew.size()-1;i>=slots(s,b);i--){s.unassign(crew.get(i));changed=true;}}
   for(var trade:others){var role=Profession.valueOf(trade.toUpperCase(java.util.Locale.ROOT));int keep=Staff.slots(work(b.type()),trade,b.level());
    var crew=s.employees(b.id()).stream().filter(id->s.resident(id)!=null&&s.resident(id).profession()==role).toList();for(int i=crew.size()-1;i>=keep;i--){s.unassign(crew.get(i));changed=true;}}}
  for(var r:List.copyOf(s.residents())){
   if(!r.alive()||r.sick()||r.life()!=Resident.Life.ADULT||r.profession()!=null)continue;
   boolean wasRecruit=r.recruit();var opening=opening(s,r,builders);if(r.recruit()!=wasRecruit)changed=true;if(opening==null)continue;
   s.assign(r.id(),opening.role(),opening.building().id());changed=true;
  }
  return changed;
 }
 /** AD-147 §1.2 (b): the porter posted at the hall before the village had a warehouse moves to the warehouse's free courier post once it
  *  carries nothing (a parcel begun is finished at the hall first), so a new warehouse has its courier. */
 public static boolean toStore(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();var store=WarehouseStore.of(e);if(store==null||workers(s,Profession.PORTER,store)>=slots(s,store))return false;
  for(var r:List.copyOf(s.residents())){var at=s.workplace(r.id());
   if(!r.alive()||r.profession()!=Profession.PORTER||at==null||!at.type().equals("town_hall")||PorterWork.active(PorterWork.inspect(l,r.id())))continue;
   s.assign(r.id(),Profession.PORTER,store.id());return true;}
  return false;
 }
 private static long workers(Settlement s,Profession role,Settlement.Building b){return s.residents().stream().filter(r->r.alive()&&r.profession()==role&&(b==null||s.workplace(r.id())!=null&&s.workplace(r.id()).id().equals(b.id()))).count();}
 private static Opening opening(Settlement s,Resident r,int builders){
  // A recruit keeps drilling until trained; then the military opening it was enlisted for is its own.
  // AD-095: one that came to a village with no drill ground is discharged to ordinary work; its drill time is kept.
  if(r.recruit()&&!r.military()){if(s.buildings().stream().anyMatch(b->b.type().equals("barracks")))return null;r.discharge();}
  var hall=s.buildings().stream().filter(b->b.type().equals("town_hall")).findFirst().orElse(null);
  if(hall!=null&&workers(s,Profession.BUILDER,null)==0)return new Opening(Profession.BUILDER,hall);
  var stock=s.buildings().stream().filter(b->b.type().equals("warehouse")).findFirst().orElse(hall);
  // AD-139: a courier of the restaurant is a porter too, but not the stock's.
  // AD-147 §1.2: a warehouse that posts no courier (VI while its wolves carry) opens no early post either.
  if(stock!=null&&slots(s,stock)>0&&s.residents().stream().noneMatch(x->x.alive()&&x.profession()==Profession.PORTER&&!Dining.restaurant(s.workplace(x.id()))&&!SmithyDelivery.post(s.workplace(x.id()))))return new Opening(Profession.PORTER,stock);
  boolean children=s.residents().stream().anyMatch(x->x.alive()&&!x.educated()&&(x.life()==Resident.Life.CHILD||x.life()==Resident.Life.ADULT&&x.schoolTicks()>0&&x.schoolTicks()<SCHOOL_REQUIRED));
  var order=new ArrayList<String>(List.of("farm","restaurant","mill","courier"));if(children)order.add(0,"school");order.addAll(List.of("forester","mine","carpentry","masonry","smithy","smithy_courier","livestock","laboratory","clinic","guard_house","archery","wall_tower","cartographer","caravan","expedition","engineering","barracks"));
  // AD-095: a trained adult takes a military post before any other work: that is what it drilled for.
  if(r.military()){var posts=List.of("guard_house","archery","wall_tower","barracks");order.removeAll(posts);order.addAll(0,posts);}
  for(var type:order)for(var b:s.buildings()){
   // AD-139: the restaurant's courier posts (IV: 1, V-VI: 2) come after the food is made (field, kitchen, mill).
   if(type.equals("courier")){if(Dining.restaurant(b)&&workers(s,Profession.PORTER,b)<Couriers.posts(b.level()))return new Opening(Profession.PORTER,b);continue;}
   // AD-155: the smithy's courier (III-IV) after its smith.
   if(type.equals("smithy_courier")){if(SmithyDelivery.post(b)&&workers(s,Profession.PORTER,b)<SmithyDelivery.posts(b.level()))return new Opening(Profession.PORTER,b);continue;}
   if(!CoreCatalog.canonical(org.villageastra.domain.AnnexTypes.workplace(b.type())).equals(type))continue;var role=type.equals("wall_tower")?Profession.ARCHER_GUARD:Arrays.stream(Profession.values()).filter(p->p.workplace().equals(type)&&p!=Profession.MAYOR&&p!=Profession.BUILDER).findFirst().orElse(null);
   if(role==null||role.educationRequired()&&!r.educated())continue;
   if(role.military()&&!r.military()){
    // AD-095: nobody is handed a weapon untrained — an adult without a trade is sent to the drill ground instead, if the village has one.
    if(workers(s,role,b)==0&&s.buildings().stream().anyMatch(x->x.type().equals("barracks"))
      &&s.residents().stream().filter(x->x.alive()&&x.recruit()).count()<RECRUITS){r.enlist();return null;}
    continue;}
   if(workers(s,role,b)==0&&slots(s,b)>0)return new Opening(role,b);
  }
  // AD-153: the hall's further builders (Construction I..VI) once every workplace has its first worker.
  if(hall!=null&&workers(s,Profession.BUILDER,null)<builders)return new Opening(Profession.BUILDER,hall);
  // AD-130: the second and third farmers of a big farm are posted only after every workplace has its first worker.
  for(var b:s.buildings())if(slots(s,b)>1){var role=role(b.type());if(role!=null&&!role.military()&&(!role.educationRequired()||r.educated())&&workers(s,role,b)<slots(s,b))return new Opening(role,b);}
  // AD-159 (owner's Military ladder): the barracks' further places (8/12/16 by its level) take only adults nobody else needs - a trained
  // one as a soldier, an untrained one to the drill ground (RECRUITS at a time).
  for(var b:s.buildings())if(b.type().equals("barracks")&&workers(s,Profession.SOLDIER,b)<slots(s,b)){
   if(r.military())return new Opening(Profession.SOLDIER,b);
   if(!r.recruit()&&s.residents().stream().filter(x->x.alive()&&x.recruit()).count()<RECRUITS){r.enlist();return null;}}
  return null;
 }
 /** AD-130: the workers a workplace takes on its own: a farm of a layout-6 village by the level it is kept at (FarmField.farmers:
  *  1/1/1/2/3/0), any other one. A village that keeps the AD-104 field table has no barn and no level-VI machine, so its farm keeps its
  *  one farmer at every level ("old fields keep working", AD-130). */
 public static int slots(Settlement s,Settlement.Building b){if(WarehouseStore.is(b)&&b.level()>=6&&CartWolves.relieved(s.id()))return 0;return b.type().equals("farm")?FarmField.legacy(s)?1:FarmField.farmers(b.level()):Staff.slots(work(b.type()),b.level());}
 /** Every worker a workplace posts at its kept level: its own trade's and the other trades' of its Staff row (the restaurant's couriers). */
 public static int posts(Settlement s,Settlement.Building b){int n=slots(s,b);for(var trade:Staff.others(work(b.type())))n+=Staff.slots(work(b.type()),trade,b.level());return n;}
 /** The workplace a building type is (an annex as the trade it stands for, an old name as the current one: bakery - restaurant). */
 static String work(String type){return CoreCatalog.canonical(org.villageastra.domain.AnnexTypes.workplace(type));}
 /** The trade that works at a workplace type (an annex as the trade it stands for). */
 public static Profession role(String type){if(type.equals("wall_tower"))return Profession.ARCHER_GUARD;var work=work(type);
  return Arrays.stream(Profession.values()).filter(p->p.workplace().equals(work)&&p!=Profession.MAYOR&&p!=Profession.BUILDER).findFirst().orElse(null);}
}
