package org.villageastra.world;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-029: physical workshops. A job snapshots exact inputs/outputs when planned; the chest and journal own every item transition. */
public final class Workshops {
 /** A demand of the village. AD-147 (CF-C): {@code need} is its class for a warehouse courier (LogisticsRoutes.NEED_*: 1 an approved
  *  construction, 2 a worker's supplies or a workshop's inputs, 3 food, 4 research, 5 roads, the clinic, a farm's seed, the store's carts);
  *  0 where the maker named none. The list's own order (what a workshop crafts first) is never changed by it. */
 public record Want(Ingredient ingredient,int count,UUID destination,int need){
  public Want(Ingredient ingredient,int count,UUID destination){this(ingredient,count,destination,0);}
  public boolean matches(ItemStack s){return !s.isEmpty()&&ingredient.test(s);}
  /** The same demand in a need class. */
  public Want as(int need){return new Want(ingredient,count,destination,need);}
 }
 private static void add(List<Want> result,int need,Collection<Want> wants){for(var w:wants)result.add(w.need()==0?w.as(need):w);}
 /** Fuel shortages retain their exact missing burn time; count remains the legacy coal-equivalent display amount. */
 public record Input(Ingredient ingredient,int count,int fuelTicks){
  public Input(Ingredient ingredient,int count){this(ingredient,count,0);}
  public boolean matches(ItemStack s){return !s.isEmpty()&&ingredient.test(s);}
 }
 /** AD-139: levels — from a working level on, a recipe's batch and fuel ticks ({batch,fuel}); minLevel — the level that opens it. */
 private record Custom(String id,List<Input> inputs,List<ItemStack> outputs,int fuelTicks,Input tool,int toolDamage,long labor,int batch,int minLevel,Map<Integer,int[]> levels){
  Custom at(int level){if(levels.isEmpty())return this;int[] o=null;for(var en:new TreeMap<>(levels).entrySet())if(en.getKey()<=level)o=en.getValue();
   return o==null?this:new Custom(id,inputs,outputs,o[1],tool,toolDamage,labor,o[0],minLevel,levels);}
 }
 /** AD-139: level — the working level this view of a workshop stands for (1 for a type read without its building); byLevel — the output items
  *  a level adds (the restaurant's meats from II). A kitchen makes nothing its level has not opened. */
 public record Spec(String building,Profession profession,Set<String> modes,long labor,List<TagKey<Item>> outputTags,Set<Item> outputItems,List<Custom> custom,int level,Map<Integer,Set<Item>> byLevel){
  boolean produces(Item item){return building.equals("town_hall")||outputItems.contains(item)||outputTags.stream().anyMatch(t->item.builtInRegistryHolder().is(t));}
  /** Whether a level changes what this workshop makes. */
  public boolean levelled(){return !byLevel.isEmpty()||custom.stream().anyMatch(c->c.minLevel()>1||!c.levels().isEmpty());}
  /** This workshop as a building working at that level has it. */
  public Spec at(int working){int lv=Math.max(1,Math.min(6,working));var all=ALL_LEVELS.get(building);if(all==null||lv==level)return this;
   return VIEWS.computeIfAbsent(building+"|"+lv,k->view(all,lv));}
 }
 /** A concrete batch: inputs to withdraw, fuel ticks, optional tool and exact outputs, for 'units' runs of a recipe that allows up to 'batch' per job (AD-104 P2). */
 public record Job(String recipe,List<Input> inputs,int fuelTicks,Input tool,int toolDamage,List<ItemStack> outputs,long labor,int units,int batch){}
 // AD-139: a levelled workshop's whole table and its views by level, filled while SPECS loads.
 private static final Map<String,Spec> ALL_LEVELS=new HashMap<>();
 /** AD-139: {batch, fuel ticks} of a custom recipe of a workshop at a working level (the great oven's research card), or null. */
 public static int[] custom(String type,String recipe,int level){var s=spec(type,level);if(s==null)return null;for(var c:s.custom())if(c.id().equals(recipe))return new int[]{c.batch(),c.fuelTicks()};return null;}
 private static final Map<String,Spec> VIEWS=new java.util.concurrent.ConcurrentHashMap<>();
 private static final Map<String,Spec> SPECS=load();
 private static final int SMELTING_BATCH=readBatch();
 private Workshops(){}
 private static JsonObject root(){try(var s=Workshops.class.getResourceAsStream("/data/villageastra/balance/workshops.json")){if(s==null)throw new IllegalStateException("Missing workshops balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static int readBatch(){int n=root().get("smelting_batch").getAsInt();if(n<1||n>64)throw new IllegalStateException("Invalid smelting batch");return n;}
 private static Item item(String id){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(id));if(item==Items.AIR)throw new IllegalStateException("Unknown workshop item "+id);return item;}
 private static Input input(JsonObject o){int count=o.has("count")?o.get("count").getAsInt():1;if(count<1||count>64)throw new IllegalStateException("Invalid input count");return new Input(o.has("tag")?Ingredient.of(TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation(o.get("tag").getAsString()))):Ingredient.of(item(o.get("item").getAsString())),count);}
 private static Map<String,Spec> load(){
  var result=new LinkedHashMap<String,Spec>();
  var definitions=root().getAsJsonObject("workshops");
  try(var bootstrap=Workshops.class.getResourceAsStream("/data/villageastra/balance/bootstrap_workshop.json")){if(bootstrap==null)throw new IllegalStateException("Missing bootstrap workshop");var extra=JsonParser.parseReader(new InputStreamReader(bootstrap,StandardCharsets.UTF_8)).getAsJsonObject();var original=definitions.getAsJsonObject("town_hall");if(original!=null){for(var key:List.of("modes","output_tags","output_items","custom"))if(original.has(key)){if(!extra.has(key))extra.add(key,new JsonArray());for(var value:original.getAsJsonArray(key))if(!extra.getAsJsonArray(key).contains(value))extra.getAsJsonArray(key).add(value);}extra.add("profession",original.get("profession"));}definitions.add("town_hall",extra);}catch(IOException ex){throw new IllegalStateException(ex);}
  for(var entry:definitions.entrySet()){
   var o=entry.getValue().getAsJsonObject();var profession=Profession.fromId(o.get("profession").getAsString());
   if(!profession.workplace().equals(entry.getKey()))throw new IllegalStateException("Workshop profession works elsewhere: "+entry.getKey());
   var modes=new LinkedHashSet<String>();if(o.has("modes"))o.getAsJsonArray("modes").forEach(m->modes.add(m.getAsString()));
   if(!Set.of("crafting","smelting","stonecutting").containsAll(modes))throw new IllegalStateException("Unknown workshop mode");
   var tags=new ArrayList<TagKey<Item>>();if(o.has("output_tags"))o.getAsJsonArray("output_tags").forEach(t->tags.add(TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation(t.getAsString()))));
   var items=new LinkedHashSet<Item>();if(o.has("output_items"))o.getAsJsonArray("output_items").forEach(t->items.add(item(t.getAsString())));
   var custom=new ArrayList<Custom>();
   if(o.has("custom"))for(var raw:o.getAsJsonArray("custom")){var c=raw.getAsJsonObject();var inputs=new ArrayList<Input>();c.getAsJsonArray("inputs").forEach(i->inputs.add(input(i.getAsJsonObject())));var outputs=new ArrayList<ItemStack>();c.getAsJsonArray("outputs").forEach(i->outputs.add(new ItemStack(item(i.getAsJsonObject().get("item").getAsString()),i.getAsJsonObject().get("count").getAsInt())));
    Input tool=c.has("tool")?input(c.getAsJsonObject("tool")):null;int damage=c.has("tool")?c.getAsJsonObject("tool").get("damage").getAsInt():0;long labor=20L*c.get("labor_seconds").getAsInt();
    if(labor<20||outputs.isEmpty()||inputs.isEmpty())throw new IllegalStateException("Invalid custom recipe "+c.get("id"));
    // AD-104 P2: one job makes up to 'batch' units. A tool is paid and worn once per job and a deposit is one whole stack, so a batch has no tool and its output fits a stack.
    int batch=c.has("batch")?c.get("batch").getAsInt():1;
    if(batch<1||batch>64||batch>1&&tool!=null||outputs.stream().anyMatch(s->s.getCount()*batch>s.getMaxStackSize()))throw new IllegalStateException("Invalid batch of custom recipe "+c.get("id"));
    // AD-139: a recipe's batch and fuel from a working level on (the restaurant's great oven at III).
    var levels=new HashMap<Integer,int[]>();if(c.has("levels"))for(var lv:c.getAsJsonObject("levels").entrySet()){var o2=lv.getValue().getAsJsonObject();int b2=o2.has("batch")?o2.get("batch").getAsInt():batch;
     if(b2<1||b2>64||b2>1&&tool!=null||outputs.stream().anyMatch(st->st.getCount()*b2>st.getMaxStackSize()))throw new IllegalStateException("Invalid level batch of custom recipe "+c.get("id"));
     levels.put(Integer.parseInt(lv.getKey()),new int[]{b2,o2.has("fuel_ticks")?o2.get("fuel_ticks").getAsInt():(c.has("fuel_ticks")?c.get("fuel_ticks").getAsInt():0)});}
    custom.add(new Custom(c.get("id").getAsString(),List.copyOf(inputs),List.copyOf(outputs),c.has("fuel_ticks")?c.get("fuel_ticks").getAsInt():0,tool,damage,labor,batch,c.has("min_level")?c.get("min_level").getAsInt():1,Map.copyOf(levels)));}
   long labor=20L*(o.has("labor_seconds")?o.get("labor_seconds").getAsInt():5);
   var byLevel=new HashMap<Integer,Set<Item>>();if(o.has("output_items_by_level"))for(var lv:o.getAsJsonObject("output_items_by_level").entrySet()){var set=new LinkedHashSet<Item>();lv.getValue().getAsJsonArray().forEach(t->set.add(item(t.getAsString())));byLevel.put(Integer.parseInt(lv.getKey()),Set.copyOf(set));}
   // AD-139: the table holds the workshop of every level; the one kept by type is its level I (a building's own is read by spec(l,e,b)).
   var all=new Spec(entry.getKey(),profession,Set.copyOf(modes),labor,List.copyOf(tags),Set.copyOf(items),List.copyOf(custom),0,Map.copyOf(byLevel));
   result.put(entry.getKey(),all.levelled()?levelOne(all):all);
  }
  return Map.copyOf(result);
 }
 /** AD-135: an annex works as the workshop it stands for (the carpenter's lean-to is the carpentry). */
 public static Spec spec(String buildingType){return SPECS.get(CoreCatalog.canonical(org.villageastra.domain.AnnexTypes.workplace(buildingType)));}
 /** AD-139: the workshop of a building type as a building working at that level has it (the restaurant's kitchen by its level). */
 public static Spec spec(String buildingType,int level){var s=spec(buildingType);return s==null?null:s.at(level);}
 /** AD-139: the workshop of this building at the level it works at now. */
 public static Spec spec(ServerLevel l,SettlementData.Entry e,Settlement.Building b){var s=spec(b.type());return s==null||!s.levelled()?s:s.at(BuildingLevels.level(l,e,b));}
 /** Level I of a levelled workshop; its whole table (level 0) is kept aside so every other level is read from it. */
 private static Spec levelOne(Spec all){ALL_LEVELS.put(all.building(),all);return view(all,1);}
 private static Spec view(Spec all,int lv){var items=new LinkedHashSet<>(all.outputItems());for(var en:all.byLevel().entrySet())if(en.getKey()<=lv)items.addAll(en.getValue());
  var cs=new ArrayList<Custom>();for(var c:all.custom())if(c.minLevel()<=lv)cs.add(c.at(lv));
  return new Spec(all.building(),all.profession(),all.modes(),all.labor(),all.outputTags(),Set.copyOf(items),List.copyOf(cs),lv,all.byLevel());}
 // ---- AD-136 (§4.2): the hall's crafting lists of Engineering I and II ------------------------------------------------
 /** Ticks a hall job of a learned (or AD-003 basic) item takes: the builder's own pace. */
 public static final long HALL_FAST=20L*root().getAsJsonObject("workshops").getAsJsonObject("town_hall").get("labor_seconds").getAsInt();
 /** Ticks any other hall job takes: the slow bootstrap pace of AD-114, so that early building never stops. */
 public static final long HALL_SLOW=SPECS.get("town_hall").labor();
 private static final Map<String,List<String>> HALL_CRAFTS=hallLists();
 private static Map<String,List<String>> hallLists(){var o=root().getAsJsonObject("workshops").getAsJsonObject("town_hall").getAsJsonObject("research_outputs");var out=new LinkedHashMap<String,List<String>>();
  for(var en:o.entrySet()){var list=new ArrayList<String>();for(var v:en.getValue().getAsJsonArray()){var s=v.getAsString();if(!s.startsWith("#"))item(s);list.add(s);}out.put(en.getKey(),List.copyOf(list));}return Map.copyOf(out);}
 /** The items and tags ("#…") a research node teaches the hall (empty for every node but Engineering I, II and III). */
 public static List<String> hallCrafts(String node){return HALL_CRAFTS.getOrDefault(node,List.of());}
 private static boolean listed(List<String> list,ItemStack s){for(var e:list){if(e.startsWith("#")?s.is(TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation(e.substring(1)))):s.is(item(e)))return true;}return false;}
 /** Whether the hall makes this at the builder's pace: an item the village's Engineering taught it (the rest, AD-003 basics included, at the bootstrap pace). */
 public static boolean hallFast(ServerLevel l,SettlementData.Entry e,ItemStack s){
  var done=ResearchKnobs.done(l,e);
  for(var en:HALL_CRAFTS.entrySet())if(done.contains(en.getKey())&&listed(en.getValue(),s))return true;return false;}
 public static Collection<Spec> specs(){return SPECS.values();}
 public static Path path(ServerLevel l,UUID building){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-workshop/"+building+".bin");}
 private static long INSPECT_READS;
 /** Number of actual verified job reads, for development diagnostics. */
 public static synchronized long inspectReads(){return INSPECT_READS;}
 private record WorkshopStamp(int tick,long revision,java.nio.file.attribute.FileTime modified,java.nio.file.attribute.FileTime created,long size,Object key){}
 private record WorkshopRead(WorkshopStamp stamp,CompoundTag tag){}
 private static final Map<net.minecraft.server.MinecraftServer,Map<Path,WorkshopRead>> WORKSHOP_READS=new WeakHashMap<>();
 /** Share verified job sensing within one actual server tick, never physical inventory or demand.
  * Atomic writes invalidate immediately even when size and timestamp are unchanged; copies protect paid cargo. */
 public static synchronized CompoundTag inspect(ServerLevel l,UUID building){
  var p=path(l,building).toAbsolutePath().normalize();
  if(!Files.exists(p)){var cache=WORKSHOP_READS.get(l.getServer());if(cache!=null)cache.remove(p);return new CompoundTag();}
  try{
   var a=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class);
   var stamp=new WorkshopStamp(l.getServer().getTickCount(),AtomicRecord.revision(p),a.lastModifiedTime(),a.creationTime(),a.size(),a.fileKey());
   var cache=WORKSHOP_READS.computeIfAbsent(l.getServer(),server->new LinkedHashMap<Path,WorkshopRead>(16,.75F,true){
    @Override protected boolean removeEldestEntry(Map.Entry<Path,WorkshopRead> entry){return size()>256;}
   });
   var cached=cache.get(p);if(cached!=null&&cached.stamp().equals(stamp))return cached.tag().copy();
   INSPECT_READS++;var tag=NbtRecord.read(p);cache.put(p,new WorkshopRead(stamp,tag));return tag.copy();
  }catch(IOException ex){throw new IllegalStateException("Cannot read "+p,ex);}
 }
 // ---- demand ------------------------------------------------------------------------------
 private static Settlement.Building stockBuilding(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.type().equals("warehouse")).findFirst().orElseGet(()->e.settlement().buildings().stream().filter(b->b.type().equals("town_hall")).findFirst().orElse(null));}
 public static Settlement.Building hall(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.type().equals("town_hall")).findFirst().orElse(null);}
 /** What the settlement actually lacks, in priority order: approved construction materials, then bread stock. */
 /** An NPC mayor can prepare ordered training that directly improves an unpaid active project. */
 private static boolean craftTrainingHelps(ServerLevel l,SettlementData.Entry e){
  if(e.settlement().governance().playerMayor()!=null||!HallUpgradeGoal.pending(l,e.settlement().id()))return false;
  var project=HallUpgradeGoal.fundingView(l,e.settlement().id());
  if(project.getBoolean("funded")||e.settlement().governance().paused(HallConstructionPlan.projectId(project)))return false;
  return ConstructionFunding.missing(project).keySet().stream().anyMatch(id->listed(hallCrafts("engineering.1"),new ItemStack(item(id))));
 }
 /** AD-130: bone meal an automatic farm keeps in its chest. */
 public static final int FARM_BONE_MEAL=16;
 public static List<Want> wants(ServerLevel l,SettlementData.Entry e){
  var result=new ArrayList<Want>();var hall=hall(e);add(result,LogisticsRoutes.NEED_SUPPLY,WorkerSupplies.wants(l,e));
  // Finish already ordered craft training before repeatedly doing the pending house's woodwork at the bootstrap pace.
  boolean prepareCrafts=craftTrainingHelps(l,e);
  if(prepareCrafts)add(result,LogisticsRoutes.NEED_RESEARCH,org.villageastra.server.BookResearch.wants(l,e,id->id.equals("engineering.1")));
  if(hall!=null&&HallUpgradeGoal.pending(l,e.settlement().id())){
   var state=HallUpgradeGoal.fundingView(l,e.settlement().id());var chest=LogisticsRoutes.chest(l,e,hall);
   if(!state.getBoolean("funded")&&chest!=null){var cost=state.getCompound("cost");
    // Count each physical stack once, rather than decode the entire paid cargo
    // again for every item in a large estimate. Rebuild on every call so a
    // same-tick withdrawal or delivery remains visible without cache invalidation.
    var available=new HashMap<Item,Integer>();
    for(var raw:state.getList("cargo",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);if(!s.isEmpty())available.merge(s.getItem(),s.getCount(),Integer::sum);}
    for(int slot=0;slot<chest.getContainerSize();slot++){var s=chest.getItem(slot);if(!s.isEmpty())available.merge(s.getItem(),s.getCount(),Integer::sum);}
    for(var key:cost.getAllKeys().stream().sorted().toList()){var item=item(key);
     int missing=cost.getInt(key)-available.getOrDefault(item,0);if(missing>0)result.add(new Want(Ingredient.of(item),missing,hall.id(),LogisticsRoutes.NEED_BUILD));}}
  }
  // AD-136 (CF3): the level-I research the mayor ordered paid — what its price still lacks, to the hall.
  add(result,LogisticsRoutes.NEED_RESEARCH,org.villageastra.server.BookResearch.wants(l,e,id->!prepareCrafts||!id.equals("engineering.1")));
  var stock=stockBuilding(e);if(stock!=null){var chest=LogisticsRoutes.chest(l,e,stock);if(chest!=null){int target=16+2*(int)e.settlement().residents().stream().filter(Resident::alive).count();int missing=target-HallReserve.count(l,e,stock,chest,s->s.is(Items.BREAD));if(missing>0)result.add(new Want(Ingredient.of(Items.BREAD),missing,stock.id(),LogisticsRoutes.NEED_FOOD));}}
  // AD-139: a restaurant with a hall keeps dishes for its tables.
  add(result,LogisticsRoutes.NEED_FOOD,Dining.wants(l,e));
  // Paving and lighting materials of the active road project (AD-038).
  add(result,LogisticsRoutes.NEED_WAYS,Roads.wants(l,e,hall));
  add(result,LogisticsRoutes.NEED_WAYS,Trails.wants(l,e,hall));
  // Clinics keep a small bandage reserve (AD-034); AD-112: the clinic's core makes it larger with every level.
  for(var b:e.settlement().buildings())if(b.type().equals("clinic")){var chest=LogisticsRoutes.chest(l,e,b);if(chest!=null){int missing=reserve(l,e,b)-LogisticsRoutes.count(chest,s->s.is(org.villageastra.VillageAstra.BANDAGE.get()));if(missing>0)result.add(new Want(Ingredient.of(org.villageastra.VillageAstra.BANDAGE.get()),missing,b.id(),LogisticsRoutes.NEED_WAYS));}}
  // Every farm needs finite real seed for new plots. Machines also request bone meal.
  for(var b:e.settlement().buildings())if(b.type().equals("farm")){var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)continue;
   var automatic=FarmField.farmers(b.level())==0&&!FarmField.legacy(e.settlement());var policies=org.villageastra.server.FarmPolicies.get(l.getServer());var crops=new LinkedHashSet<FarmCrops>();
   if(automatic)for(int f=1;f<=FarmField.FIELDS;f++)crops.add(policies.crop(e.settlement().id(),b.id(),f));
   else for(var module:FarmField.worked(l,e,b))crops.add(policies.crop(e.settlement().id(),b.id(),FarmField.number(module,FarmField.legacy(e.settlement()))));
   for(var crop:crops){var seed=new ItemStack(crop.seed);int missing=LogisticsRoutes.reserve(b,seed)-LogisticsRoutes.count(chest,st->st.is(crop.seed));if(missing>0)result.add(new Want(Ingredient.of(crop.seed),missing,b.id(),automatic?LogisticsRoutes.NEED_WAYS:LogisticsRoutes.NEED_SUPPLY));}
   if(automatic&&ResearchKnobs.boneMeal(l,e)>0){int missing=FARM_BONE_MEAL-LogisticsRoutes.count(chest,st->st.is(Items.BONE_MEAL));if(missing>0)result.add(new Want(Ingredient.of(Items.BONE_MEAL),missing,b.id(),LogisticsRoutes.NEED_WAYS));}}
  // AD-147 §2.2 (CF-O): the carts the warehouse's couriers and wolves lack, into the warehouse (the carpentry or the hall with Engineering II makes them).
  add(result,LogisticsRoutes.NEED_WAYS,WarehouseCarts.wants(l,e));
  // AD-156: Engineering III - the redstone mechanisms the engineering office puts up for sale, when its shelf is empty.
  add(result,LogisticsRoutes.NEED_WAYS,EngineeringSales.wants(l,e));
  // Inputs one workshop lacks become demand for the others (bread needs flour, flour needs wheat).
  for(var b:e.settlement().buildings())if(spec(b.type())!=null)for(var need:published(l,b.id()))result.add(new Want(need.ingredient(),need.count(),b.id(),LogisticsRoutes.NEED_SUPPLY));
  return result;
 }
 /** AD-112: the bandages a clinic keeps in reserve at its working level. */
 public static int reserve(ServerLevel l,SettlementData.Entry e,Settlement.Building clinic){return org.villageastra.domain.CoreEffects.value("clinic","reserve",BuildingLevels.level(l,e,clinic));}
 /** AD-112: what a resource building of the settlement brings in raw (the mine's ores and stone, the farm's crops, the forester's wood,
  *  the herd's hides, wool and meat). */
 private static final Map<String,Set<Item>> RAW=Map.of(
  "mine",Set.of(Items.COBBLESTONE,Items.COBBLED_DEEPSLATE,Items.CALCITE,Items.TUFF,Items.ANDESITE,Items.DIORITE,Items.GRANITE,Items.COAL,Items.RAW_IRON,Items.RAW_COPPER,Items.RAW_GOLD,Items.REDSTONE,Items.LAPIS_LAZULI,Items.DIAMOND,Items.GRAVEL,Items.FLINT),
  "farm",Set.of(Items.WHEAT,Items.WHEAT_SEEDS,Items.CARROT,Items.POTATO,Items.BEETROOT,Items.BEETROOT_SEEDS,Items.SUGAR_CANE),
  "livestock",Set.of(Items.LEATHER,Items.FEATHER,Items.EGG,Items.BEEF,Items.PORKCHOP,Items.CHICKEN,Items.MUTTON));
 public static boolean mined(Item item){return RAW.get("mine").contains(item);}
 private static boolean raw(SettlementData.Entry e,Item item){
  if(NaturalSupplyGoal.provides(item)&&e.settlement().residents().stream().anyMatch(NaturalSupplyGoal::eligible))return true;
  for(var b:e.settlement().buildings()){var type=b.type().equals("quarry")?"mine":b.type();
   if(RAW.getOrDefault(type,Set.of()).contains(item))return true;
   if(type.equals("livestock")&&item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.WOOL))return true;
   if(type.equals("forester")&&(item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.LOGS)||item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.SAPLINGS)))return true;
   // AD-131: the sawmill of a hut kept at IV or more cuts planks.
   if(type.equals("forester")&&b.level()>=ForestBalance.SAW_FROM&&item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.PLANKS))return true;}
  return false;
 }
 /** AD-112: whether the village can come by this item on its own: it lies in the hall chest already, a resource building brings it in raw, or
  *  a workshop of the village has a recipe whose every input it can come by in turn (at most twelve steps down). A level-V smithy can process actual debris, scraps and ingots into netherite.
  *  Ancient debris is not an advertised raw source until an autonomous expedition can really acquire it.
  *  An NPC mayor orders a level only when the core or ring it lacks is makeable — a project waiting for ever would hold the one slot. */
 public static boolean makeable(ServerLevel l,SettlementData.Entry e,String id){var hall=hall(e);return makeable(l,e,item(id),hall==null?null:LogisticsRoutes.chest(l,e,hall),12,new HashMap<>());}
 /** AD-137 (addendum): whether the village can come by more of this item than already lies in the hall — the rule of makeable without
  *  the hall's own stock of this very item (its inputs may still lie there). An NPC mayor orders a level only when every item short of its
  *  estimate is producible: a raw resource nobody brings in (ancient debris) or a workshop the village lacks refuses it. */
 public static boolean producible(ServerLevel l,SettlementData.Entry e,String id){var hall=hall(e);return produced(l,e,item(id),hall==null?null:LogisticsRoutes.chest(l,e,hall),12,new HashMap<>());}
 private static boolean makeable(ServerLevel l,SettlementData.Entry e,Item item,OwnedChestEntity hall,int depth,Map<Item,Boolean> memo){
  var known=memo.get(item);if(known!=null)return known;
  if(hall!=null&&LogisticsRoutes.count(hall,s->s.is(item))>0){memo.put(item,true);return true;}
  if(Set.of(Items.ANCIENT_DEBRIS,Items.NETHERITE_SCRAP,Items.NETHERITE_INGOT,Items.NETHERITE_BLOCK).contains(item))for(var b:e.settlement().buildings())if(b.type().equals("smithy")){
   var chest=LogisticsRoutes.chest(l,e,b);if(chest!=null&&LogisticsRoutes.count(chest,stack->stack.is(item))>0){memo.put(item,true);return true;}
  }
  return produced(l,e,item,hall,depth,memo);
 }
 private static boolean produced(ServerLevel l,SettlementData.Entry e,Item item,OwnedChestEntity hall,int depth,Map<Item,Boolean> memo){
  if(item==Items.NETHERITE_BLOCK)for(var b:e.settlement().buildings())if(b.type().equals("smithy")){var c=LogisticsRoutes.chest(l,e,b);if(c!=null&&LogisticsRoutes.count(c,s->s.is(item))>0){memo.put(item,true);return true;}}
  if(item==Items.NETHERITE_BLOCK||item==Items.NETHERITE_INGOT){
   // Finite rare stock is not a renewable ore source. Do not promise nine ingots
   // merely because one exists or a reversible decompression recipe was found.
   int scraps=0,ingots=0,blocks=0;var counted=new HashSet<BlockPos>();
   for(var b:e.settlement().buildings())if(b.type().equals("town_hall")||b.type().equals("smithy")){
    var pos=LogisticsRoutes.position(e,b);if(!counted.add(pos))continue;var c=LogisticsRoutes.chest(l,e,b);if(c==null)continue;
    scraps+=LogisticsRoutes.count(c,stack->stack.is(Items.ANCIENT_DEBRIS)||stack.is(Items.NETHERITE_SCRAP));
    ingots+=LogisticsRoutes.count(c,stack->stack.is(Items.NETHERITE_INGOT));blocks+=LogisticsRoutes.count(c,stack->stack.is(Items.NETHERITE_BLOCK));
   }
   if(item==Items.NETHERITE_BLOCK&&blocks*9+ingots+scraps/4<9||item==Items.NETHERITE_INGOT&&blocks==0&&scraps<4){memo.put(item,false);return false;}
  }
  if(raw(e,item)){memo.put(item,true);return true;}
  memo.put(item,false);if(depth<=0)return false;
  for(var b:e.settlement().buildings()){var spec=spec(l,e,b);if(spec==null)continue;
   for(var job:candidates(l,spec,item,1)){boolean all=true;var local=LogisticsRoutes.chest(l,e,b);
    for(var in:job.inputs()){boolean any=false;for(var option:in.ingredient().getItems())if(local!=null&&LogisticsRoutes.count(local,stack->stack.is(option.getItem()))>0||makeable(l,e,option.getItem(),hall,depth-1,memo)){any=true;break;}if(!any){all=false;break;}}
    if(all){memo.put(item,true);return true;}}}
  return false;
 }
 // ---- planning ----------------------------------------------------------------------------
 /** One synchronous planning pass reads each real slot once, then counts only occupied stacks. */
 private static final class PlanInventory extends net.minecraft.world.SimpleContainer {
  private final List<ItemStack> occupied=new ArrayList<>();private final Map<Ingredient,Integer> counts=new IdentityHashMap<>();private int burn;
  private Map<Item,Integer> conversionKeep=Map.of();private final Map<String,Boolean> reversible=new HashMap<>();
  PlanInventory(Container source){super(source.getContainerSize());for(int i=0;i<source.getContainerSize();i++){var item=source.getItem(i).copy();setItem(i,item);if(!item.isEmpty()){occupied.add(item);burn+=WorkshopFuel.ticks(item)*item.getCount();}}}
  int available(Input in){return counts.computeIfAbsent(in.ingredient(),k->{int count=0;for(var item:occupied)if(in.matches(item))count+=item.getCount();return count;});}
 }
 private static int available(Container c,Input in){if(c instanceof PlanInventory stock)return stock.available(in);int n=0;for(int i=0;i<c.getContainerSize();i++)if(in.matches(c.getItem(i)))n+=c.getItem(i).getCount();return n;}
 private static int fuel(Container c){if(c instanceof PlanInventory stock)return stock.burn;int n=0;for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);n+=WorkshopFuel.ticks(s)*s.getCount();}return n;}
 private static List<Input> group(List<Ingredient> ingredients){
  var groups=new LinkedHashMap<String,Input>();
  for(var ing:ingredients){if(ing.isEmpty())continue;var key=ing.toJson().toString();var old=groups.get(key);groups.put(key,new Input(ing,old==null?1:old.count()+1));}
  return List.copyOf(groups.values());
 }
 private static boolean remainders(List<Input> inputs){return inputs.stream().anyMatch(in->Arrays.stream(in.ingredient().getItems()).anyMatch(ItemStack::hasCraftingRemainingItem));}
 private static RecipeManager cachedManager;
 private static final Map<String,List<Job>> CACHE=new HashMap<>();
 /** Candidate jobs that produce the item in this workshop, without regard to stock; each is scaled to the wanted amount up to its recipe's batch
  *  (custom 'batch', smelting_batch for cooking, up to four for hall crafting and one elsewhere; AD-104 P2). */
 static synchronized List<Job> candidates(ServerLevel l,Spec spec,Item target,int wanted){
  var manager=l.getRecipeManager();if(manager!=cachedManager){CACHE.clear();cachedManager=manager;}
  var templates=CACHE.computeIfAbsent(spec.building()+"|"+spec.level()+"|"+BuiltInRegistries.ITEM.getKey(target),k->templates(l,spec,target));
  var result=new ArrayList<Job>();
  // The shared hand bench must reconsider urgent tools between short paid batches.
  // Dedicated workshops retain their published batches and labor per unit.
  int hallBatch=4;
  for(var job:templates){int per=Math.max(1,job.outputs().stream().filter(o->o.is(target)).mapToInt(ItemStack::getCount).sum());int limit=spec.building().equals("town_hall")?Math.min(hallBatch,job.batch()):job.batch();int n=Math.max(1,Math.min(limit,(wanted+per-1)/per));result.add(n>1?scale(job,n):job);}
  return result;
 }
 /** n units of a one-unit job: every input and output count, the fuel and the labour times n. */
 private static Job scale(Job unit,int n){return new Job(unit.recipe(),unit.inputs().stream().map(in->new Input(in.ingredient(),in.count()*n)).toList(),unit.fuelTicks()*n,unit.tool(),unit.toolDamage(),unit.outputs().stream().map(s->s.copyWithCount(s.getCount()*n)).toList(),unit.labor()*n,n,unit.batch());}
 /** The one-unit job a scaled job was made of. */
 private static Job unit(Job job){int n=job.units();return n==1?job:new Job(job.recipe(),job.inputs().stream().map(in->new Input(in.ingredient(),in.count()/n)).toList(),job.fuelTicks()/n,job.tool(),job.toolDamage(),job.outputs().stream().map(s->s.copyWithCount(s.getCount()/n)).toList(),job.labor()/n,1,job.batch());}
 private static List<Job> templates(ServerLevel l,Spec spec,Item target){
  var result=new ArrayList<Job>();
  var custom=new ArrayList<>(spec.custom());if(spec.building().equals("town_hall"))for(var other:SPECS.values())if(!other.building().equals("town_hall"))for(var recipe:other.custom())if(custom.stream().noneMatch(x->x.id().equals(recipe.id())))custom.add(recipe);
  for(var c:custom)if(c.outputs().stream().anyMatch(o->o.is(target)))result.add(new Job("custom:"+c.id(),c.inputs(),c.fuelTicks(),c.tool(),c.toolDamage(),c.outputs(),(spec.building().equals("town_hall")?Math.max(c.labor(),spec.labor()):c.labor()),1,c.batch()));
  if(!spec.produces(target))return List.copyOf(result);
  var manager=l.getRecipeManager();var access=l.registryAccess();
  var recipes=new ArrayList<Recipe<?>>();
  if(spec.modes().contains("crafting"))recipes.addAll(manager.getAllRecipesFor(RecipeType.CRAFTING));
  if(spec.modes().contains("stonecutting"))recipes.addAll(manager.getAllRecipesFor(RecipeType.STONECUTTING));
  if(spec.modes().contains("smelting"))recipes.addAll(manager.getAllRecipesFor(RecipeType.SMELTING));
  recipes.sort(Comparator.comparing(r->r.getId().toString()));
  for(var r:recipes){
   var out=r.getResultItem(access);if(out.isEmpty()||!out.is(target)||r.isSpecial())continue;
   var inputs=group(r.getIngredients());if(inputs.isEmpty()||remainders(inputs))continue;
   if(r instanceof AbstractCookingRecipe cooking)result.add(new Job(r.getId().toString(),List.of(new Input(inputs.get(0).ingredient(),1)),cooking.getCookingTime(),null,0,List.of(out.copy()),Math.max(cooking.getCookingTime(),spec.labor()),1,SMELTING_BATCH));
   else {int batch=spec.building().equals("town_hall")?Math.min(4,out.getMaxStackSize()/out.getCount()):1;result.add(new Job(r.getId().toString(),inputs,0,null,0,List.of(out.copy()),spec.labor(),1,Math.max(1,batch)));}
  }
  return List.copyOf(result);
 }
 /** AD-104 P2: the station's fuel bank (burn time an earlier job paid for and did not use) counts as fuel. */
 private static boolean supplied(Container c,Job job,int bank){return job.inputs().stream().allMatch(in->available(c,in)>=in.count())&&(job.fuelTicks()==0||fuel(c)+bank>=job.fuelTicks())&&(job.tool()==null||available(c,job.tool())>=1);}
 /** Whole units of a one-unit job that the chest and the fuel bank pay for right now. */
 private static int fit(Container c,Job unit,int bank){int n=Integer.MAX_VALUE;for(var in:unit.inputs())n=Math.min(n,available(c,in)/in.count());if(unit.fuelTicks()>0)n=Math.min(n,(fuel(c)+bank)/unit.fuelTicks());return unit.tool()!=null&&available(c,unit.tool())<1?0:n;}
 /** Next job for this workshop: directly for a want, or an intermediate it can make itself. This one counts the station's fuel bank. */
 public static Job plan(ServerLevel l,SettlementData.Entry e,Settlement.Building b,Container chest,List<Want> wants){var spec=b==null?null:spec(l,e,b);return spec==null?null:withFurnace(l,e,spec,chest,wants,inspect(l,b.id()).getInt("fuelBank"));}
 private static Job withFurnace(ServerLevel l,SettlementData.Entry e,Spec spec,Container chest,List<Want> wants,int bank){var ordinary=villagePlan(l,e,spec,chest,wants,bank);if(ordinary!=null)return ordinary;int burn=NaturalFurnace.availableBurn(l,e);if(burn>0)for(var want:wants){var job=villagePlan(l,e,spec,chest,List.of(want),bank+burn);if(job!=null&&NaturalFurnace.recipe(l,job))return job;}return null;}
 /** A non-construction recipe must not bypass the same grain protection used by
  * hand baking. Construction itself sees the real stock and can fund its bed. */
 private static Job villagePlan(ServerLevel l,SettlementData.Entry e,Spec spec,Container chest,List<Want> wants,int bank){
  var hall=e==null?null:hall(e);
  // Finish paid work normally, but do not start another building batch while a
  // staffed kitchen lacks inputs and the real pantries lack one round of meals.
  // The kitchen still pays its own fuel, inputs and full baking labor.
  if(hall!=null&&spec.building().equals("town_hall")&&wants.stream().anyMatch(w->w.need()==LogisticsRoutes.NEED_FOOD)){
   long mouths=e.settlement().residents().stream().filter(Resident::alive).count();
   if(Population.storedNutrition(l,e)<mouths*Population.MEAL_NUTRITION){
    var kitchenInputs=new ArrayList<Want>();
    for(var b:Dining.restaurants(e)){
     boolean staffed=e.settlement().residents().stream().anyMatch(r->b.equals(e.settlement().workplace(r.id()))&&eligible(r,b)&&Population.mayWork(r));
     var c=LogisticsRoutes.chest(l,e,b);if(!staffed||c==null||LogisticsRoutes.count(c,Dining::dish)>=mouths)continue;
     for(var need:published(l,b.id()))kitchenInputs.add(new Want(need.ingredient(),need.count(),b.id(),LogisticsRoutes.NEED_FOOD));
    }
    if(!kitchenInputs.isEmpty()){
     var safe=HallReserve.view(l,e,chest);var food=plan(l,spec,safe,kitchenInputs,bank);if(food!=null)return food;
    }
   }
  }
  int held=hall!=null&&spec.building().equals("town_hall")?HandBread.constructionWheat(l,e,hall):0;
  if(held<=0)return plan(l,spec,chest,wants,bank);
  var other=new net.minecraft.world.SimpleContainer(chest.getContainerSize());
  for(int slot=0;slot<chest.getContainerSize();slot++){var stack=chest.getItem(slot).copy();if(stack.is(Items.WHEAT)){int keep=Math.min(held,stack.getCount());stack.shrink(keep);held-=keep;}other.setItem(slot,stack);}
  for(int from=0;from<wants.size();){int end=groupEnd(wants,from);var job=plan(l,spec,wants.get(from).need()==LogisticsRoutes.NEED_BUILD?chest:other,wants.subList(from,end),bank);if(job!=null)return job;from=end;}return null;
 }
 /** Next job for this workshop without a fuel bank. */
 public static Job plan(ServerLevel l,Spec spec,Container chest,List<Want> wants){return plan(l,spec,chest,wants,0);}
 private static Job plan(ServerLevel l,Spec spec,Container chest,List<Want> wants,int bank){
  var stock=chest instanceof PlanInventory planned?planned:new PlanInventory(chest);var memo=new HashMap<PlanKey,Job>();
  // Finish an affordable requested product before reworking its material into
  // an intermediate for another unpaid bill. Preserve the demand-class order.
  for(int from=0;from<wants.size();){int end=groupEnd(wants,from);
   for(var want:wants.subList(from,end))for(var option:want.ingredient().getItems())for(var template:candidates(l,spec,option.getItem(),want.count())){
    var job=withoutAncestors(template,Set.of(option.getItem()));if(job==null)continue;var ready=funded(l,spec,stock,job,bank,false);if(ready!=null)return ready;
   }
   stock.conversionKeep=conversionKeep(l,spec,stock,wants.subList(from,end));memo.clear();
   for(var want:wants.subList(from,end))for(var option:want.ingredient().getItems()){var job=plan(l,spec,stock,option.getItem(),want.count(),0,bank,memo);if(job!=null)return job;}
   from=end;
  }
  return null;
 }
 private static int groupEnd(List<Want> wants,int from){int end=from+1;while(end<wants.size()&&wants.get(end).need()==wants.get(from).need())end++;return end;}
 private static Job funded(ServerLevel l,Spec spec,Container chest,Job job,int bank,boolean protect){
  Job ready=supplied(chest,job,bank)?job:null;
  if(ready==null&&job.units()>1){var unit=unit(job);int fit=fit(chest,unit,bank);if(fit>=1)ready=scale(unit,Math.min(job.units(),fit));}
  if(ready!=null&&protect&&chest instanceof PlanInventory stock&&reversible(l,spec,unit(ready),stock))for(var in:ready.inputs())for(var option:in.ingredient().getItems())if(available(stock,in)-in.count()<stock.conversionKeep.getOrDefault(option.getItem(),0))return null;
  return ready;
 }
 /** Hold one fully accumulated ingredient for a final product against only
  * lossless reverse conversions. Ordinary woodwork, fuel and tool dependencies
  * can still spend their inputs; this is planning, not a paid reservation. */
 private static Map<Item,Integer> conversionKeep(ServerLevel l,Spec spec,PlanInventory stock,List<Want> wants){
  var keep=new HashMap<Item,Integer>();for(var want:wants)for(var option:want.ingredient().getItems())for(var job:candidates(l,spec,option.getItem(),want.count()))for(var in:unit(job).inputs())if(in.ingredient().getItems().length==1&&available(stock,in)>=in.count())keep.merge(in.ingredient().getItems()[0].getItem(),in.count(),Math::max);return keep;
 }
 private static boolean reversible(ServerLevel l,Spec spec,Job job,PlanInventory stock){
  if(job.tool()!=null||job.fuelTicks()!=0||job.inputs().size()!=1||job.outputs().size()!=1||job.inputs().get(0).ingredient().getItems().length!=1)return false;
  return stock.reversible.computeIfAbsent(job.recipe(),key->{var in=job.inputs().get(0);var item=in.ingredient().getItems()[0].getItem();var out=job.outputs().get(0);
   for(var reverse:candidates(l,spec,item,1)){reverse=unit(reverse);if(reverse.tool()!=null||reverse.fuelTicks()!=0||reverse.inputs().size()!=1||reverse.outputs().size()!=1||reverse.inputs().get(0).ingredient().getItems().length!=1)continue;
    var back=reverse.inputs().get(0);var result=reverse.outputs().get(0);if(back.matches(out)&&result.is(item)&&out.getCount()*result.getCount()==in.count()*back.count())return true;
   }return false;
  });
 }
 private record PlanKey(Item item,int count,int depth,Set<Item> ancestors){}
 private static Job plan(ServerLevel l,Spec spec,Container chest,Item target,int wanted,int depth,int bank){return plan(l,spec,chest,target,wanted,depth,bank,new HashMap<>());}
 private static Job plan(ServerLevel l,Spec spec,Container chest,Item target,int wanted,int depth,int bank,Map<PlanKey,Job> memo){return plan(l,spec,chest,target,wanted,depth,bank,memo,new HashSet<>());}
 private static Job plan(ServerLevel l,Spec spec,Container chest,Item target,int wanted,int depth,int bank,Map<PlanKey,Job> memo,Set<Item> visiting){
  if(!visiting.add(target))return null;
  try{var key=new PlanKey(target,wanted,depth,Set.copyOf(visiting));if(memo.containsKey(key))return memo.get(key);var result=seek(l,spec,chest,target,wanted,depth,bank,memo,visiting);memo.put(key,result);return result;}finally{visiting.remove(target);}
 }
 // Accumulating three ingots must not spend an existing ingot on nuggets and then craft it back forever.
 // Direct nugget demand still permits that recipe: an ingot is not an ancestor of that independent request.
 private static Job withoutAncestors(Job job,Set<Item> visiting){
  List<Input> inputs=null;
  for(int index=0;index<job.inputs().size();index++){var in=job.inputs().get(index);if(visiting.stream().noneMatch(item->in.matches(new ItemStack(item))))continue;
   var allowed=Arrays.stream(in.ingredient().getItems()).filter(stack->!visiting.contains(stack.getItem())).toArray(ItemStack[]::new);if(allowed.length==0)return null;
   if(inputs==null)inputs=new ArrayList<>(job.inputs());inputs.set(index,new Input(Ingredient.of(allowed),in.count()));
  }
  return inputs==null?job:new Job(job.recipe(),List.copyOf(inputs),job.fuelTicks(),job.tool(),job.toolDamage(),job.outputs(),job.labor(),job.units(),job.batch());
 }
 // Prefer shortages with real village suppliers over an uncraftable compressed block or loot item.
 // This ranks missing inputs only; an already supplied recipe remains usable regardless of its source.
 private static long unsupported(SettlementData.Entry e,List<Input> inputs){return inputs.stream().filter(in->Arrays.stream(in.ingredient().getItems()).noneMatch(s->e!=null?raw(e,s.getItem()):RAW.values().stream().anyMatch(items->items.contains(s.getItem()))||NaturalSupplyGoal.provides(s.getItem())||s.is(net.minecraft.tags.ItemTags.LOGS)||s.is(net.minecraft.tags.ItemTags.SAPLINGS)||s.is(net.minecraft.tags.ItemTags.WOOL))).mapToLong(Input::count).sum();}
 // Equipment recycling consumes existing surplus equipment; it never warrants
 // manufacturing new equipment solely to burn it for a single nugget.
 private static boolean recyclesEquipment(Job job){
  if(job.fuelTicks()<=0||job.inputs().size()!=1||job.outputs().size()!=1)return false;
  var out=job.outputs().get(0);if(!out.is(Items.IRON_NUGGET)&&!out.is(Items.GOLD_NUGGET))return false;
  var options=job.inputs().get(0).ingredient().getItems();
  return options.length>0&&Arrays.stream(options).allMatch(s->s.isDamageableItem()||s.getItem() instanceof HorseArmorItem);
 }
 /** Missing tools are production dependencies too; they remain reusable tools in the paid job. */
 private static List<Input> dependencies(Job job){if(job.tool()==null)return job.inputs();var all=new ArrayList<>(job.inputs());all.add(job.tool());return all;}
 private static Job seek(ServerLevel l,Spec spec,Container chest,Item target,int wanted,int depth,int bank,Map<PlanKey,Job> memo,Set<Item> visiting){
  for(var template:candidates(l,spec,target,wanted)){
   var job=withoutAncestors(template,visiting);if(job==null)continue;
   var ready=funded(l,spec,chest,job,bank,true);if(ready!=null)return ready;
   if(recyclesEquipment(job))continue;
   // A mixed tag may use a real stocked alternative, but must not reopen an
   // unfunded forest of interchangeable wood recipes. Raw shortages are
   // published separately by leaves; reconsider when the gatherer supplies it.
   if(job!=template){boolean absent=false;for(int i=0;i<job.inputs().size();i++)if(job.inputs().get(i)!=template.inputs().get(i)&&available(chest,job.inputs().get(i))<job.inputs().get(i).count()/job.units()){absent=true;break;}if(absent)continue;}
   // Start whole affordable units of an allowed recipe batch rather
   // than strand three paid inputs while waiting for a fourth. Unit costs,
   // fuel, labor and the maximum batch remain unchanged.
   if(depth>=(spec.building().equals("town_hall")?12:3))continue;
   for(var in:dependencies(job)){int missing=in.count()-available(chest,in);if(missing<=0)continue;
    for(var option:in.ingredient().getItems()){var sub=plan(l,spec,chest,option.getItem(),missing,depth+1,bank,memo,visiting);if(sub!=null)return sub;}}
  }
  return null;
 }
 /** Raw inputs this workshop lacks for the first want it could produce, for the whole job that want calls for (up to the recipe's batch) even when a smaller job could start; published for porters. */
 public static List<Input> needs(ServerLevel l,Spec spec,Container chest,List<Want> wants){return needs(l,null,spec,chest,wants,0);}
 public static List<Input> needs(ServerLevel l,SettlementData.Entry e,Spec spec,Container chest,List<Want> wants){return needs(l,e,spec,chest,wants,0);}
 /** Current self-supply shortages include the real station's already paid fuel bank. */
 public static List<Input> needs(ServerLevel l,SettlementData.Entry e,Settlement.Building b,Container chest,List<Want> wants){
  var spec=spec(l,e,b);return spec==null?List.of():needs(l,e,spec,chest,wants,inspect(l,b.id()).getInt("fuelBank"));
 }
 private static List<Input> needs(ServerLevel l,SettlementData.Entry e,Spec spec,Container chest,List<Want> wants,int bank){
  var stock=chest instanceof PlanInventory planned?planned:new PlanInventory(chest);
  for(int from=0;from<wants.size();){int end=groupEnd(wants,from);
   stock.conversionKeep=conversionKeep(l,spec,stock,wants.subList(from,end));
   for(var want:wants.subList(from,end))for(var option:want.ingredient().getItems()){var result=new ArrayList<Input>();if(needs(l,e,spec,stock,option.getItem(),want.count(),0,bank,result))return result;}
   from=end;
  }
  return List.of();
 }
 private static boolean needs(ServerLevel l,SettlementData.Entry e,Spec spec,Container chest,Item target,int wanted,int depth,int bank,List<Input> result){
  if(candidates(l,spec,target,wanted).isEmpty())return false;var leaves=leaves(l,e,spec,chest,target,wanted,depth,bank,new HashSet<>(),new HashMap<>(),new int[]{2048});if(leaves==null)return false;result.addAll(leaves);return true;
 }
 private static List<Input> leaves(ServerLevel l,SettlementData.Entry e,Spec spec,Container chest,Item target,int wanted,int depth,int bank,Set<Item> visiting,Map<PlanKey,List<Input>> memo,int[] budget){
  if(depth>12||--budget[0]<0||!visiting.add(target))return null;
  try{var key=new PlanKey(target,wanted,depth,Set.copyOf(visiting));if(memo.containsKey(key))return memo.get(key);var jobs=candidates(l,spec,target,wanted);if(jobs.isEmpty()||RAW.values().stream().anyMatch(items->items.contains(target))||target==Items.WHITE_WOOL||target==Items.CLAY_BALL)return List.of(new Input(Ingredient.of(target),Math.max(1,wanted)));
   List<Input> best=null;long bestCost=Long.MAX_VALUE,bestUnsupported=Long.MAX_VALUE;
   // Shortage expansion retains its conservative cycle pruning. Unlike a
   // stocked job, an unfunded tag cannot establish which alternative will
   // actually be supplied; widening it here exhausts the finite search budget.
   for(var job:jobs){if(job.inputs().stream().anyMatch(in->visiting.stream().anyMatch(item->in.matches(new ItemStack(item)))))continue;
    if(recyclesEquipment(job)&&job.inputs().stream().anyMatch(in->available(chest,in)<in.count()))continue;
    var path=new ArrayList<Input>();boolean possible=true;
    for(var in:dependencies(job)){int held=available(chest,in);
     if(chest instanceof PlanInventory stock&&reversible(l,spec,unit(job),stock))held=Math.max(0,held-stock.conversionKeep.getOrDefault(in.ingredient().getItems()[0].getItem(),0));
     int missing=in.count()-held;if(missing<=0)continue;List<Input> selected=null;long score=Long.MAX_VALUE,unavailable=Long.MAX_VALUE;
     for(var option:in.ingredient().getItems()){var sub=leaves(l,e,spec,chest,option.getItem(),missing,depth+1,bank,visiting,memo,budget);if(sub==null)continue;long cost=sub.stream().mapToLong(Input::count).sum(),absent=unsupported(e,sub);if(absent<unavailable||absent==unavailable&&cost<score){score=cost;unavailable=absent;selected=sub;}}
     if(selected==null){possible=false;break;}path.addAll(selected);}
    if(!possible)continue;int fuel=fuel(chest)+bank;if(job.fuelTicks()>fuel)path.add(new Input(WorkshopFuel.demand(),Math.max(1,(job.fuelTicks()-fuel+1599)/1600),job.fuelTicks()-fuel));
    long cost=path.stream().mapToLong(Input::count).sum(),absent=unsupported(e,path);
    if(absent<bestUnsupported||absent==bestUnsupported&&cost<bestCost){bestCost=cost;bestUnsupported=absent;best=path;}
   }if(best!=null)memo.put(key,best);return best;
  }finally{visiting.remove(target);}
 }
 // ---- durable execution -------------------------------------------------------------------
 private static ListTag inputsTag(List<Input> inputs){var list=new ListTag();for(var in:inputs){var t=new CompoundTag();t.putString("ingredient",in.ingredient().toJson().toString());t.putInt("count",in.count());if(in.fuelTicks()>0)t.putInt("fuelTicks",in.fuelTicks());list.add(t);}return list;}
 private static List<Input> inputs(ListTag list){var result=new ArrayList<Input>();for(var raw:list){var t=(CompoundTag)raw;result.add(new Input(Ingredient.fromJson(JsonParser.parseString(t.getString("ingredient"))),t.getInt("count"),t.getInt("fuelTicks")));}return result;}
 private static List<ItemStack> stacks(ListTag list){var result=new ArrayList<ItemStack>();for(var raw:list)result.add(ItemStack.of((CompoundTag)raw));return result;}
 private static ListTag stacksTag(List<ItemStack> stacks){var list=new ListTag();for(var s:stacks)list.add(s.save(new CompoundTag()));return list;}
 private static int burn(ItemStack s){return net.minecraftforge.common.ForgeHooks.getBurnTime(s,RecipeType.SMELTING)*s.getCount();}
 public static BlockPos station(SettlementData.Entry e,Settlement.Building b){return LogisticsRoutes.position(e,b);}
 /** Trusted service called by the physical worker at the station (or tests with an explicit clock). */
 public static String advance(ServerLevel l,SettlementData.Entry e,Settlement.Building b,long now,List<Want> wants){return advance(l,e,b,now,wants,null);}
 public static String advance(ServerLevel l,SettlementData.Entry e,Settlement.Building b,long now,List<Want> wants,UUID contributor){
  var spec=spec(b.type());if(spec==null||e.settlement().buildings().stream().noneMatch(x->x.equals(b)))throw new IllegalArgumentException("Not a workshop");spec=spec(l,e,b);
  var real=LogisticsRoutes.chest(l,e,b);if(real==null)return "workshop_missing_chest";var pos=station(e,b);
  // AD-137: the hall as a workshop plans and takes from its stock less the active project's reserve; the take names the real slot.
  var chest=b.type().equals("town_hall")?HallReserve.view(l,e,real):real;
  var file=path(l,b.id());var t=inspect(l,b.id());int bank=t.getInt("fuelBank");
  if(b.type().equals("town_hall")&&t.getBoolean("toolRepair"))chest=HallReserve.repairView(l,e,real);
  if(t.isEmpty()||t.getString("stage").equals("idle")){
   Job job=null;boolean repair=false;
   if(b.type().equals("town_hall")&&ToolSupplyReserve.needed(l,e)){
    var repairWants=wants.stream().filter(w->java.util.Arrays.stream(w.ingredient().getItems()).anyMatch(ToolSupplyReserve::tool)).toList();
    job=withFurnace(l,e,spec,HallReserve.repairView(l,e,real),repairWants,bank);repair=job!=null;
   }
   if(job==null)job=withFurnace(l,e,spec,chest,wants,bank);if(job!=null&&NaturalFurnace.recipe(l,job))job=NaturalFurnace.fit(job,chest);
   var record=new CompoundTag();record.putInt("schema",1);record.putUUID("building",b.id());record.putLong("lastTick",now);
   // AD-104 P2: burn time a finished job paid for and did not use stays with the station, in its idle record and in its next job's.
   if(bank>0)record.putInt("fuelBank",bank);
   if(job==null){
    var needs=needs(l,e,spec,chest,wants,bank);record.putString("stage","idle");record.put("needs",inputsTag(needs));
    if(!record.equals(t.isEmpty()?null:withoutTick(t,now)))NbtRecord.write(file,record);
    return needs.isEmpty()?"workshop_idle":"workshop_missing_inputs";
   }
   if(repair)record.putBoolean("toolRepair",true);
   record.putUUID("id",UUID.randomUUID());record.putString("recipe",job.recipe());record.putString("stage","fund");record.put("inputs",inputsTag(job.inputs()));record.putInt("fuelTicks",job.fuelTicks());
   if(job.tool()!=null){record.putString("tool",job.tool().ingredient().toJson().toString());record.putInt("toolDamage",job.toolDamage());}
   // AD-136: a hall job of a learned item takes the builder's pace, any other the slow bootstrap one.
   long need=b.type().equals("town_hall")&&!job.outputs().isEmpty()&&job.outputs().stream().allMatch(o->hallFast(l,e,o))?Math.min(job.labor(),HALL_FAST):job.labor();
   record.put("outputs",stacksTag(job.outputs()));record.putLong("needLabor",need);record.putLong("labor",0);record.put("paid",new ListTag());record.putInt("withdrawals",0);record.putInt("output",0);
   if(NaturalFurnace.recipe(l,job)){record.putBoolean("physicalSmelt",true);record.putString("stage","smelt_raw");}
   NbtRecord.write(file,record);return "workshop_funding";
  }
  var person=contributor==null?null:e.settlement().resident(contributor);
  boolean helping=b.type().equals("town_hall")&&person!=null&&eligible(person,b)&&t.getString("stage").equals("work");
  if(helping){
   if(t.contains("workSince")&&now-t.getLong("workSince")<20)return "workshop_wait_tick";
   var hands=t.getCompound("laborWorkers");String key=contributor.toString();
   if(hands.contains(key)&&now-hands.getLong(key)<20)return "workshop_wait_tick";
   hands.putLong(key,now);t.put("laborWorkers",hands);
  }else if(now-t.getLong("lastTick")<20)return "workshop_wait_tick";
  t.putLong("lastTick",now);
  if(t.getBoolean("physicalSmelt"))return NaturalFurnace.advance(l,e,b,t,now);
  var id=t.getUUID("id");var paid=stacks(t.getList("paid",Tag.TAG_COMPOUND));
  if(t.getString("stage").equals("refund")){
   int index=t.getInt("refundOutput");
   if(index<paid.size()){
    if(!WorldJournal.deposit(l,Settlement.childId(id,"refund/"+index),pos,paid.get(index)))return "output_full";
    t.putInt("refundOutput",index+1);NbtRecord.write(file,t);return "workshop_funding";
   }
   var idle=new CompoundTag();idle.putInt("schema",1);idle.putUUID("building",b.id());idle.putString("stage","idle");idle.putLong("lastTick",now);if(bank>0)idle.putInt("fuelBank",bank);
   NbtRecord.write(file,idle);return "workshop_complete";
  }
  if(t.getString("stage").equals("fund")){
   Input need=null;int missing=0;boolean fuelNeed=false,toolNeed=false;
   var ins=inputs(t.getList("inputs",Tag.TAG_COMPOUND));var used=new boolean[paid.size()];
   Input tool=t.contains("tool")?new Input(Ingredient.fromJson(JsonParser.parseString(t.getString("tool"))),1):null;
   // Secure the reusable tool before consuming recipe inputs. A producer can
   // otherwise claim the only axe between planning and the later withdrawal.
   if(tool!=null){boolean has=false;for(int i=0;i<paid.size();i++)if(!used[i]&&tool.matches(paid.get(i))){has=true;used[i]=true;break;}if(!has){need=tool;missing=1;toolNeed=true;}}
   if(need==null)for(var in:ins){int held=0;for(int i=0;i<paid.size();i++)if(!used[i]&&in.matches(paid.get(i))){held+=paid.get(i).getCount();used[i]=true;}if(held<in.count()){need=in;missing=in.count()-held;break;}}
   int fuelTicks=t.getInt("fuelTicks"),burnt=bank;
   if(need==null&&fuelTicks>0){for(int i=0;i<paid.size();i++)if(!used[i])burnt+=burn(paid.get(i));if(burnt<fuelTicks){fuelNeed=true;missing=1;}}
   // AD-104 P2: the bank burns first; what the paid fuel leaves over becomes the new bank in the same write that starts the work, so a replay never counts it twice.
   if(need==null&&!fuelNeed){t.putString("stage","work");t.putLong("workSince",now);if(fuelTicks>0){if(burnt>fuelTicks)t.putInt("fuelBank",burnt-fuelTicks);else t.remove("fuelBank");}NbtRecord.write(file,t);return "workshop_working";}
   var withdrawal=Settlement.childId(id,"input/"+t.getInt("withdrawals"));var taken=WorldJournal.recoverAmount(l,withdrawal);
   if(taken.isEmpty()&&!WorldJournal.exists(l,withdrawal))for(int slot=0;slot<chest.getContainerSize();slot++){var s=chest.getItem(slot);
    boolean fits=fuelNeed?WorkshopFuel.ticks(s)>0&&ins.stream().noneMatch(in->in.matches(s))&&(tool==null||!tool.matches(s)):need.matches(s);
    if(!fits)continue;int amount=fuelNeed||toolNeed?1:Math.min(missing,s.getCount());taken=WorldJournal.takeAmount(l,withdrawal,pos,slot,real.getItem(slot).copy(),amount);break;}
   if(taken.isEmpty()){
    if(need!=null&&b.type().equals("town_hall")&&withFurnace(l,e,spec,chest,List.of(new Want(need.ingredient(),missing,b.id())),bank)!=null){
     // Another consumer may claim either the tool or ingredients after planning.
     // Free this station to make the missing dependency, refunding paid stacks
     // through durable receipts before re-planning the original order.
     t.putString("stage","refund");t.putInt("refundOutput",0);NbtRecord.write(file,t);return "workshop_funding";
    }
    NbtRecord.write(file,t);return "workshop_missing_inputs";
   }
   t.getList("paid",Tag.TAG_COMPOUND).add(taken.save(new CompoundTag()));t.putInt("withdrawals",t.getInt("withdrawals")+1);NbtRecord.write(file,t);return "workshop_funding";
  }
  // AD-052: a workshop with its second-level equipment really does more work per turn.
  if(t.getString("stage").equals("work")){t.putLong("labor",Math.min(t.getLong("needLabor"),t.getLong("labor")+BuildingLevels.labor(l,e,b)));if(t.getLong("labor")>=t.getLong("needLabor"))t.putString("stage","output");NbtRecord.write(file,t);return "workshop_working";}
  var outputs=new ArrayList<>(stacks(t.getList("outputs",Tag.TAG_COMPOUND)));
  if(t.contains("tool")){var tool=new Input(Ingredient.fromJson(JsonParser.parseString(t.getString("tool"))),1);for(var s:paid)if(tool.matches(s)){var worn=s.copyWithCount(1);worn.setDamageValue(worn.getDamageValue()+t.getInt("toolDamage"));if(worn.getDamageValue()<worn.getMaxDamage())outputs.add(worn);break;}}
  int index=t.getInt("output");
  if(index<outputs.size()){if(!WorldJournal.deposit(l,Settlement.childId(id,"output/"+index),pos,outputs.get(index))){NbtRecord.write(file,t);return "output_full";}t.putInt("output",index+1);NbtRecord.write(file,t);return "workshop_output";}
  t.putString("stage","idle");t.remove("needs");NbtRecord.write(file,t);return "workshop_complete";
 }
 private static CompoundTag withoutTick(CompoundTag t,long now){var copy=t.copy();copy.putLong("lastTick",now);return copy;}
 /** Published missing inputs of an idle workshop (for porters). */
 public static List<Input> published(ServerLevel l,UUID building){var t=inspect(l,building);return (t.getString("stage").equals("idle")||t.getBoolean("physicalSmelt"))&&t.contains("needs")?inputs(t.getList("needs",Tag.TAG_COMPOUND)):List.of();}
 public static boolean eligible(Resident r,Settlement.Building b){var spec=b==null?null:spec(b.type());return spec!=null&&r!=null&&r.alive()&&(r.profession()==spec.profession()||b.type().equals("town_hall")&&(r.profession()==Profession.MAYOR||r.profession()==null&&r.life()==Resident.Life.ADULT))&&(!spec.profession().educationRequired()||r.educated());}
}
