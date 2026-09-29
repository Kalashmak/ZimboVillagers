package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.domain.*;
import org.villageastra.world.*;
/** AD-058: one card per building of the settlement — what it is, who works or lives there and the settings that belong to that one building.
 *  The map shows the cards of the buildings it has opened; the office lists all of them while the settlement has no map yet. */
public final class BuildingCards {
 /** Most people named on one card. */
 private static final int NAMES=6;
 private BuildingCards(){}
 public static CompoundTag card(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var t=new CompoundTag();var s=e.settlement();var design=BuildingBlueprints.designs().stream().filter(d->d.id().equals(b.type())).findFirst().orElse(null);
  t.putUUID("id",b.id());t.putString("type",b.type());t.putLong("pos",e.center().offset(b.x(),b.y(),b.z()).asLong());
  var size=org.villageastra.world.BuildingPlacement.size(b.type(),b.rotation());t.putInt("w",size[0]);t.putInt("d",size[1]);t.putInt("rotation",b.rotation());
  var workers=new ListTag();var dwellers=new ListTag();int staff=0;String status="";
  for(var r:s.residents()){
   if(!r.alive())continue;
   var work=s.workplace(r.id());
   boolean works=work!=null&&work.id().equals(b.id())&&r.profession()!=null;
   boolean lives=b.id().equals(r.home());
   if(!works&&!lives)continue;
   var person=new CompoundTag();person.putString("name",l.getEntity(r.id()) instanceof ResidentEntity npc?npc.getName().getString():"");
   person.putString("profession",r.profession()==null?"":r.profession().id());
   if(works){staff++;if(l.getEntity(r.id()) instanceof ResidentEntity npc)status=worse(status,status(npc.workStatus()));}
   if(works&&workers.size()<NAMES)workers.add(person);
   if(lives&&dwellers.size()<NAMES)dwellers.add(person.copy());
  }
  t.put("workers",workers);t.put("dwellers",dwellers);
  // The office colours a card by what its workers last said (their live work status); nothing is advanced to find out.
  int slots=slots(s,b);t.putInt("staff",staff);if(slots>0)t.putInt("slots",slots);
  t.putString("status",slots>0&&staff==0?"no_worker":status);
  var home=s.homes().stream().filter(h->h.id().equals(b.id())).findFirst().orElse(null);
  if(home!=null){t.putInt("beds",home.capacity());t.putInt("bedsFree",home.usable()?(int)Math.max(0,home.capacity()-s.occupancy(home.id())):0);}
  // AD-073: the level it is kept at, the level it works at, what the next level takes and why it cannot be ordered yet.
  if(BuildingTiers.upgradable(b.type())){t.put("upgrade",BuildingTiers.view(l,e,b));core(l,e,b,t);}
  if(b.type().equals("farm")){t.putString("crop",FarmPolicies.get(l.getServer()).crop(s.id(),b.id()).id());
   // AD-093: which crops this village has opened; the others wait for their quest.
   var open=new ListTag();for(var c:org.villageastra.world.FarmCrops.values())if(org.villageastra.world.CropUnlocks.unlocked(l,e,org.villageastra.world.CropUnlocks.of(c)))open.add(StringTag.valueOf(c.id()));
   t.put("openCrops",open);
   field(l,e,b,t);}
  // AD-131: the forester's hut — its reach, the day's felling and planting, the mix and what it asks for, the saw, the grove.
  if(b.type().equals(ForesterHut.TYPE))t.put("forest",ForestWork.card(l,e,b));
  // AD-138: the yard's pens - kind, head of sixteen, feeder.
  if(b.type().equals("livestock"))t.put("pens",LivestockPens.view(l,e,b));
  // AD-139: the restaurant — its hall, the day's meals and the leftover, the kitchen's level, the couriers and the cart.
  if(Dining.restaurant(b))t.put("dining",Dining.card(l,e,b));
  // AD-147: the warehouse — its store, couriers, carts, wolves and sorting.
  if(WarehouseStore.is(b))t.put("warehouse",WarehouseTrips.card(l,e,b));
  // AD-135: the annexes this building may take (built, queued, ready or why not); an annex names the building it belongs to.
  var annexes=Annexes.view(l,e,b);if(!annexes.isEmpty())t.put("annexes",annexes);
  var parent=s.annexParent(b.id());if(parent!=null)s.buildings().stream().filter(x->x.id().equals(parent)).findFirst().ifPresent(x->{t.putUUID("annexOf",parent);t.putString("annexOfType",x.type());});
  // AD-094: the archers on a tower of the wall, and whether the mayor has a trained one to send up.
  if(b.type().equals(org.villageastra.world.Walls.TOWER)){t.putInt("towerArchers",(int)org.villageastra.world.Walls.archers(s,b));t.putInt("towerPlaces",org.villageastra.world.Walls.ARCHERS_PER_TOWER);
   t.putBoolean("towerCandidate",org.villageastra.world.Walls.candidate(s)!=null);}
  // AD-085: a trip of the village out on the road with its cart, which the mayor may order to leave the cart and go on on foot.
  if(b.type().equals("caravan"))for(var trip:org.villageastra.world.Caravans.contracts(l.getServer())){
   var state=trip.getString("state");
   if(trip.getUUID("source").equals(s.id())&&trip.contains("cart")&&(state.equals(org.villageastra.world.Caravans.TRANSIT)||state.equals(org.villageastra.world.Caravans.RETURNING))){
    t.putUUID("cartTrip",trip.getUUID("id"));break;}}
  if(b.type().equals(Quarry.BUILDING)){
   var record=Quarry.record(l,s.id());var pit=new CompoundTag();
   boolean mine=record!=null&&record.hasUUID("building")&&record.getUUID("building").equals(b.id());
   pit.putBoolean("claimed",mine);pit.putInt("taken",mine?record.getInt("taken"):0);pit.putInt("layer",mine?record.getInt("layer"):0);
   pit.putInt("floor",mine?record.getInt("floor"):MapOrders.floor(e));pit.putBoolean("worked",mine&&record.getBoolean("worked"));
   pit.putBoolean("miner",Excavation.miner(e)!=null);
   t.put("quarry",pit);
  }
  return t;
 }
 /** A worker's status as the office groups it: blocked (it cannot go on here), missing_input (it waits for something brought), idle, or fine (''). */
 public static String status(String work){
  if(work==null||work.isEmpty())return "";
  // A missing station chest or desk, and a miner or forester stopped by the ground itself, need the mayor, not a delivery.
  if(work.endsWith("missing_chest")||work.endsWith("missing_desk")||BLOCKED.contains(work)||work.equals("needs_access")||work.equals("needs_return_route")||work.endsWith("_blocked")||work.endsWith("stock_full")||work.equals("output_full")||work.equals("besieged"))return "blocked";
  if(work.contains("missing"))return "missing_input";
  return work.endsWith("_idle")?"idle":"";
 }
 private static final Set<String> BLOCKED=Set.of("bottom_of_world","unsafe_ground","support_conflict","invalid_planting_ground","mine_floor");
 private static final List<String> ORDER=List.of("","idle","missing_input","blocked");
 private static String worse(String a,String b){return ORDER.indexOf(b)>ORDER.indexOf(a)?b:a;}
 /** How many workers the village posts here on its own (Population.opening fills one per workplace; barracks and towers take more); 0 where nobody is posted. */
 public static int slots(Settlement s,Settlement.Building b){
  if(b.type().equals("barracks"))return 8;
  if(b.type().equals(Walls.TOWER))return Walls.ARCHERS_PER_TOWER;
  // AD-138/AD-139: a workplace's posts by its level from Staff (a farm's farmers, a yard's keepers, the restaurant's cook and couriers).
  return org.villageastra.world.Population.role(b.type())==null?0:org.villageastra.world.Population.posts(s,b);
 }
 /** AD-104: the field of a farm as it stands (its plots, those sown and those on wet farmland; plots in unloaded chunks are not looked at)
  *  and what its layout grows a day by vanilla's crop rule at this world's randomTickSpeed: the residents it is rated to feed with a
  *  quarter spare, the most it could feed, and the target of the farm's level; then (P2) what its own crops feed and by which way.
  *  AD-130: every floor grows on its own; "fields" is the grid of the 18 fields (crop, own choice, built, worked, plots, sown). */
 private static void field(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  // AD-112: the farmer works the modules its core allows (min of working level and the field laid); the plots laid beyond them wait for a core or ring.
  var cells=FarmField.workedCells(l,e,b);int sown=0,wet=0,unseen=0;
  for(var p:cells){if(!l.hasChunkAt(p)){unseen++;continue;}var plant=l.getBlockState(p);for(var c:FarmCrops.values())if(plant.is(c.block)){sown++;break;}
   var soil=l.getBlockState(p.below());if(soil.is(net.minecraft.world.level.block.Blocks.FARMLAND)&&soil.getValue(net.minecraft.world.level.block.FarmBlock.MOISTURE)==net.minecraft.world.level.block.FarmBlock.MAX_MOISTURE)wet++;}
  var modules=FarmField.worked(l,e,b);
  int speed=l.getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING);double wheat=FarmField.wheatPerDay(modules,speed);
  t.putInt("fieldPlots",cells.size());t.putInt("fieldPlotsBuilt",FarmField.cells(e,b).size());t.putInt("fieldModules",modules.size());t.putInt("fieldModulesBuilt",FarmField.modules(e,b).size());
  t.putInt("sown",sown);t.putInt("hydrated",wet);t.putInt("fieldUnseen",unseen);
  // A hair up before rounding: level I's exact 31.25 sums to 31.2499… in doubles and would read 31.2.
  t.putDouble("wheatPerDay",Math.round(wheat*10+1e-6)/10.0);t.putInt("feeds",FarmYield.ratedResidents(wheat,FarmField.RATING));t.putInt("feedsMax",(int)Math.floor(FarmYield.maxResidents(wheat)));
  // The target of the level it works at (what its worked field is rated for), and of the level it is kept at. AD-130: what the fields of that
  // level really feed on wheat (the core's "feeds"); a village keeping the AD-104 table keeps the owner's AD-104 targets.
  boolean legacy=FarmField.legacy(e.settlement());
  t.putInt("target",legacy?FarmField.target(BuildingTiers.level(l,e,b)):FarmField.feeds(BuildingTiers.level(l,e,b)));t.putInt("targetBuilt",legacy?FarmField.target(BuildingTiers.built(e,b)):FarmField.feeds(BuildingTiers.built(e,b)));
  // AD-130: the grid of the 18 fields. A village that keeps the AD-104 table has no 18 fields and no floors: its card keeps the one crop
  // button (BuildingsPanel), and no grid pretends its 16 old modules stand on three floors.
  if(legacy){food(l,e,b,t,modules,speed);return;}
  var policies=FarmPolicies.get(l.getServer());var built=FarmField.modules(e,b);boolean west=e.settlement().westField(b.id());var grid=new ListTag();
  for(int n=1;n<=FarmField.FIELDS;n++){var row=new CompoundTag();row.putInt("n",n);row.putString("crop",policies.crop(e.settlement().id(),b.id(),n).id());row.putBoolean("own",policies.own(e.settlement().id(),b.id(),n));
   var m=FarmField.field(n,west,legacy);row.putInt("level",FarmField.levelOf(n,legacy));
   if(m!=null){row.putInt("floor",FarmField.floor(m));boolean isBuilt=contains(built,m),isWorked=contains(modules,m);row.putBoolean("built",isBuilt);row.putBoolean("worked",isWorked);
    if(isBuilt){int plots=0,s=0;for(var p:FarmField.localCells(List.of(m))){var at=BuildingPlacement.at(e,b,p.getX(),p.getY(),p.getZ());plots++;if(l.hasChunkAt(at)){var plant=l.getBlockState(at);for(var c:FarmCrops.values())if(plant.is(c.block)){s++;break;}}}
     row.putInt("plots",plots);row.putInt("sown",s);}}
   grid.add(row);}
  t.put("fields",grid);
  food(l,e,b,t,modules,speed);
 }
 private static boolean contains(List<int[]> modules,int[] m){for(var x:modules)if(x[0]==m[0]&&x[1]==m[1]&&FarmField.floor(x)==FarmField.floor(m))return true;return false;}
 /** AD-112: the core standing in the building (grade 0 when missing, it then works at I) and what its active effects give now; an
  *  inactive effect carries no number. A building type without a core gets nothing. */
 private static void core(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  var type=CoreCatalog.coreType(b.type());if(type==null)return;
  var cell=BuildingTiers.coreCell(l,e,b);var at=BuildingPlacement.at(e,b,cell.getX(),cell.getY(),cell.getZ());
  var now=l.hasChunkAt(at)?l.getBlockState(at):null;var c=new CompoundTag();c.putString("item",CoreCatalog.coreId(b.type()));c.putInt("grade",now!=null&&Cores.isCoreOf(now,b.type())?Cores.grade(now):0);
  int working=BuildingTiers.level(l,e,b);c.putInt("working",working);var effects=new ListTag();
  for(var effect:CoreEffects.effects(type)){var row=new CompoundTag();row.putString("id",effect.id());row.putBoolean("active",effect.active());if(effect.active())row.putInt("now",BuildingTiers.effectNow(l,e,b,type,effect.id(),working));effects.add(row);}
  c.put("effects",effects);t.put("core",c);
 }
 /** AD-104 P2: how village meals eat, as the farm card counts them — the rations of a meal, meals a day and the P1 card's quarter spare. */
 public static FoodYield.Meals meals(){return new FoodYield.Meals(Population.MEAL_NUTRITION,(double)FarmYield.DAY/Population.MEAL_INTERVAL,FoodYield.spare(FarmField.RATING));}
 /** AD-104 P2: what the farm's own crop yields a day, and the residents it feeds the way the village eats that crop now: wheat as bread by hand
  *  at the hall (5 wheat for 2 bread) until the mill and the bakery both work, then as their bread (2 for 1); carrots, potatoes and beetroots as
  *  they are harvested; cane feeds nobody. The P1 keys above stay the layout's wheat rating (the farm generation test reads them). */
 private static void food(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t,List<int[]> modules,int speed){
  // AD-130: the fields grow the crops chosen for them; a crop's plots halve only beside the same crop (vanilla), every floor on its own.
  var policies=FarmPolicies.get(l.getServer());boolean legacy=FarmField.legacy(e.settlement());var meals=meals();
  var growth=new EnumMap<FoodYield.Crop,Double>(FoodYield.Crop.class);var cane=new EnumMap<FoodYield.Crop,Integer>(FoodYield.Crop.class);
  for(int f:FarmField.floors(modules)){var byCrop=new EnumMap<FoodYield.Crop,List<int[]>>(FoodYield.Crop.class);var water=new ArrayList<int[]>();
   for(var m:modules){if(FarmField.floor(m)!=f)continue;var crop=FoodYield.Crop.valueOf(policies.crop(e.settlement().id(),b.id(),FarmField.number(m,legacy)).name());
    var list=byCrop.computeIfAbsent(crop,k->new ArrayList<>());for(var p:FarmField.localCells(List.of(m)))list.add(new int[]{p.getX(),p.getZ()});var w=FarmField.localWater(m);water.add(new int[]{w.getX(),w.getZ()});}
   FarmYield.perDay(byCrop,speed).forEach((c,v)->growth.merge(c,v,Double::sum));
   for(var en:byCrop.entrySet())cane.merge(en.getKey(),FoodYield.besideWater(en.getValue(),water),Integer::sum);}
  double own=0;int rated=0,upTo=0,chainRated=0,chainUpTo=0;String mode="none";var rows=new ListTag();boolean chainStaffed=HandBread.chainStaffed(l,e);
  for(var en:growth.entrySet()){var crop=en.getKey();double wheatEq=en.getValue();
   double items=crop==FoodYield.Crop.SUGAR_CANE?FoodYield.canePerDay(cane.getOrDefault(crop,0),speed):FoodYield.itemsPerDay(wheatEq,crop);own+=items;
   // Rations as a village meal counts them (the balance's table, else vanilla nutrition; nothing for food with effects).
   int ration=crop.food==null?0:Population.nutrition(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(crop.food))));
   FoodYield.Feeds now;String how;
   if(ration<=0){now=FoodYield.Feeds.NONE;how="none";}
   else if(crop!=FoodYield.Crop.WHEAT){now=meals.feeds(items,ration);how="raw";}
   else{var chain=meals.feeds(FoodYield.bread(items,FoodYield.CHAIN_WHEAT,FoodYield.CHAIN_BREAD),ration);
    if(chainStaffed){now=chain;how="chain";}
    // Baked by hand, the card also says what a working mill and bakery would feed from the same field.
    else{now=meals.feeds(FoodYield.bread(items,HandBread.WHEAT_PER_UNIT,HandBread.BREAD_PER_UNIT),ration);how="hand";chainRated+=chain.rated()-now.rated();chainUpTo+=chain.upTo()-now.upTo();}}
   rated+=now.rated();upTo+=now.upTo();
   // The way the largest feeder eats sets the card's mode (hand before raw before none when they tie: bread is the village's staple).
   if(mode.equals("none")||how.equals("hand")||how.equals("chain")||how.equals("raw")&&!mode.equals("hand")&&!mode.equals("chain"))mode=how;
   var row=new CompoundTag();row.putString("crop",crop.name().toLowerCase(Locale.ROOT));row.putDouble("perDay",Math.round(items*10+1e-6)/10.0);row.putInt("feeds",now.rated());rows.add(row);}
  if(mode.equals("hand")){t.putInt("feedsChain",chainRated+rated);t.putInt("feedsChainMax",chainUpTo+upTo);}
  // The farm's crop (all fields without their own), what all its fields yield a day, and whom they feed by which way.
  t.putString("food",policies.crop(e.settlement().id(),b.id()).id());t.putDouble("foodPerDay",Math.round(own*10+1e-6)/10.0);t.putString("mode",mode);t.putInt("feedsNow",rated);t.putInt("feedsNowMax",upTo);t.put("foodRows",rows);
 }
 /** Cards of every building, and whether the viewer may change their settings. */
 public static void addView(ServerPlayer p,CompoundTag tag){
  if(!tag.hasUUID("village"))return;var e=SettlementData.get(p.server).entry(tag.getUUID("village"));if(e==null)return;
  var cards=new ListTag();for(var b:e.settlement().buildings())cards.add(card(p.serverLevel(),e,b));
  tag.put("cards",cards);tag.putInt("atlasChunks",Atlas.surveyed(Atlas.inspect(p.serverLevel(),e.settlement().id())).size());
 }
}
