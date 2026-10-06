package org.villageastra.world;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** Local material demand; jobs reserve finite real stacks before a second porter plans. */
public final class LogisticsRoutes {
 private LogisticsRoutes(){}
 /** Whether every stack fits into the container one after another, each whole into one slot, as the journal deposits them. */
 public static boolean fits(net.minecraft.world.Container c,List<ItemStack> stacks){
  var slots=new ItemStack[c.getContainerSize()];for(int i=0;i<slots.length;i++)slots[i]=c.getItem(i).copy();
  outer:for(var stack:stacks){if(stack.isEmpty())continue;
   for(int pass=0;pass<2;pass++)for(int i=0;i<slots.length;i++){var before=slots[i];
    if(pass==0&&before.isEmpty()||pass==1&&!before.isEmpty())continue;
    if(!before.isEmpty()&&!ItemStack.isSameItemSameTags(before,stack))continue;
    int count=before.getCount()+stack.getCount();if(count>Math.min(c.getMaxStackSize(),stack.getMaxStackSize()))continue;
    slots[i]=stack.copyWithCount(count);continue outer;}
   return false;}
  return true;
 }
 public record Demand(String key,Predicate<ItemStack> matches,int target){}
 public record Route(Settlement.Building source,Settlement.Building destination,ItemStack item){}
 public static BlockPos position(SettlementData.Entry e,Settlement.Building b){return BuildingPlacement.at(e,b,1,1,4);}
 public static OwnedChestEntity chest(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var pos=position(e,b);if(!e.dimension().equals(l.dimension().location().toString())||!l.hasChunkAt(pos)||e.settlement().buildings().stream().noneMatch(x->x.equals(b))||!(l.getBlockEntity(pos) instanceof OwnedChestEntity c))return null;
  if(!c.getPersistentData().contains("AstraSettlement")){c.getPersistentData().putUUID("AstraSettlement",e.settlement().id());c.setChanged();}
  return c.getPersistentData().hasUUID("AstraSettlement")&&c.getPersistentData().getUUID("AstraSettlement").equals(e.settlement().id())?c:null;
 }
 private static Demand item(Item item,int target){return new Demand(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),s->s.is(item),target);}
 /** AD-136: the laboratory is sent scientific works from the stock (a quest's relic stays where it is, CF11); paper and bindings no more. */
 public static List<Demand> science(){return List.of(new Demand("works",org.villageastra.server.BookResearch::work,org.villageastra.domain.ScienceBalance.CARRY_TO_LAB));}
 public static int count(OwnedChestEntity c,Predicate<ItemStack> matches){int n=0;for(int i=0;i<c.getContainerSize();i++)if(matches.test(c.getItem(i)))n+=c.getItem(i).getCount();return n;}
 public static int reserve(Settlement.Building b,ItemStack item){int dining=Dining.keep(b,item);if(dining>0)return dining;
  // AD-138: a yard keeps its feed, a lead and its shears for its own pens.
  if(b.type().equals("livestock")){if(LivestockPens.FEEDS.contains(item.getItem()))return LivestockPens.FEED_STOCK;if(item.getItem() instanceof net.minecraft.world.item.ShearsItem)return 1;return 0;}
  // AD-138 IV: a kennel keeps its wolves' meat.
  if(b.type().equals(VillageWolves.TYPE))return VillageWolves.MEAT.contains(item.getItem())?VillageWolves.MEAT_STOCK:0;
  if(b.type().equals("farm")){if(item.is(Items.SUGAR_CANE))return 1;if(item.is(Items.CARROT)||item.is(Items.POTATO))return 4;if(item.is(Items.WHEAT_SEEDS)||item.is(Items.BEETROOT_SEEDS))return 8;}
  if(item.is(net.minecraft.tags.ItemTags.LOGS)){if(b.type().equals("mine"))return 8;if(b.type().equals("forester")&&b.level()>=ForestBalance.SAW_FROM)return ForestBalance.LOG_KEEP;}return 0;}
 private static boolean output(ItemStack s){return s.is(net.minecraft.tags.ItemTags.LOGS)||Set.of(Items.COBBLESTONE,Items.COBBLED_DEEPSLATE,Items.RAW_IRON,Items.RAW_COPPER,Items.RAW_GOLD,Items.COAL,Items.DIAMOND,Items.REDSTONE,Items.LAPIS_LAZULI,Items.EMERALD,Items.WHEAT,Items.CARROT,Items.POTATO,Items.BEETROOT,Items.SUGAR_CANE,
  // AD-130: the earth the miner digs out is the hall's stock too — the barn's upper fields are laid of it (with the quarry's and the roads' spoil).
  Items.DIRT).contains(s.getItem());}
 private static boolean room(OwnedChestEntity c,ItemStack item){for(int i=0;i<c.getContainerSize();i++){var current=c.getItem(i);if(current.isEmpty()||ItemStack.isSameItemSameTags(current,item)&&current.getCount()+item.getCount()<=Math.min(current.getMaxStackSize(),c.getMaxStackSize()))return true;}return false;}
 private static Route find(ServerLevel l,SettlementData.Entry e,Settlement.Building dest,Demand demand){return find(l,e,dest,demand,LOAD);}
 private static Route find(ServerLevel l,SettlementData.Entry e,Settlement.Building dest,Demand demand,int load){return find(l,e,dest,demand,load,null,null,null);}
 /** AD-147: the same search, the sources limited to {@code only} (null: every source) and taken nearest to {@code from} first (null: by id,
  *  as before), and a route kept only when {@code accept} takes it (null: any). */
 private static Route find(ServerLevel l,SettlementData.Entry e,Settlement.Building dest,Demand demand,int load,Predicate<Settlement.Building> only,BlockPos from,Predicate<Route> accept){var target=chest(l,e,dest);if(target==null)return null;int missing=demand.target-count(target,demand.matches)-PorterWork.reserved(l,e,dest.id(),demand.matches,true);if(missing<=0)return null;
  var order=e.settlement().buildings().stream().sorted(Comparator.comparing(Settlement.Building::id));if(from!=null)order=order.sorted(Comparator.comparingDouble(b->position(e,b).distSqr(from)));
  for(var source:order.toList()){if(source.id().equals(dest.id())||!(Set.of("farm","forester","mine","livestock","town_hall","warehouse").contains(source.type())||Workshops.spec(source.type())!=null)||only!=null&&!only.test(source))continue;var c=chest(l,e,source);if(c==null)continue;for(int slot=0;slot<c.getContainerSize();slot++){var stack=c.getItem(slot);if(stack.isEmpty()||!demand.matches.test(stack))continue;int available=count(c,s->ItemStack.isSameItemSameTags(s,stack))-reserve(source,stack)-constructionReserve(l,e,source,stack)-PorterWork.reserved(l,e,source.id(),s->ItemStack.isSameItemSameTags(s,stack),false);int amount=Math.min(load,Math.min(missing,Math.min(available,stack.getCount())));if(amount>0){var item=stack.copyWithCount(amount);if(room(target,item)){var route=new Route(source,dest,item);if(accept==null||accept.test(route))return route;}}}}return null;
 }
 /** Hall stock already counted for an approved project stays at the hall (AD-137: the reserve of HallReserve). */
 public static int constructionReserve(ServerLevel l,SettlementData.Entry e,Settlement.Building source,ItemStack stack){
  if(source.type().equals("town_hall"))return HallReserve.reserved(l,e,stack.getItem());
  if(source.type().equals("mine")){var t=MineWork.read(l,source);if(t.getString("stage").equals("stair")&&stack.is(MineStairWork.stone(t)))return MineStairWork.missing(l,e,source,t);}
  return 0;
 }
 /** AD-112: the parcel a porter carries at level I, and the largest any warehouse core allows (a parcel never exceeds its stack either). */
 public static final int LOAD=16,MAX_LOAD=org.villageastra.domain.CoreEffects.value("warehouse","load",org.villageastra.domain.CoreEffects.LEVELS);
 /** AD-112: the parcel of a porter of a warehouse at this working level. */
 public static int load(int level){return org.villageastra.domain.CoreEffects.value("warehouse","load",level);}
 /** The parcel of a porter working at this building: a warehouse's by its level, any other post (the hall, a worker's own supply) LOAD. */
 public static int load(ServerLevel l,SettlementData.Entry e,Settlement.Building post){return post!=null&&post.type().equals("warehouse")?load(BuildingLevels.level(l,e,post)):LOAD;}
 public static Route choose(ServerLevel l,SettlementData.Entry e){return choose(l,e,null);}
 /** AD-147 §1.3 (CF-C, CF-D): the need classes of a warehouse's courier, the first class with a route wins: 1 the approved construction's
  *  shortfall to the hall, 2 workers' supplies and workshops' inputs, then a producer's chest at least half full, 3 food (the pantry's bread,
  *  the restaurant's dishes), 4 research (its level-I price, scientific works to the laboratory), 5 roads and trails, the clinic's bandages, an
  *  automatic farm's seed, the store's carts, 6 the producers' output to the stock, fullest chest first. Within a class the order of
  *  Workshops.wants (what the workshops craft by), and the source nearest the courier. Construction reserves stay at the hall; only emergency surplus grain returns to farms (AD345). */
 public static final int NEED_BUILD=1,NEED_SUPPLY=2,NEED_FOOD=3,NEED_RESEARCH=4,NEED_WAYS=5,NEED_OUTPUT=6;
 /** The producers whose output goes to the stock (a yard's products only, AD-138). */
 private static final Set<String> PRODUCERS=Set.of("farm","forester","mine","livestock");
 /** The part of a chest's slots that holds anything. */
 public static double fill(net.minecraft.world.Container c){int n=0;for(int i=0;i<c.getContainerSize();i++)if(!c.getItem(i).isEmpty())n++;return c.getContainerSize()==0?0:(double)n/c.getContainerSize();}
 /** A chest this full is emptied before food and research (a full chest stops its producer, CF-D). */
 public static final double FULL=0.5;
 /** A producer's own product that goes to the stock. */
 public static boolean product(Settlement.Building b,ItemStack item){return !item.isEmpty()&&(b.type().equals("livestock")?LivestockPens.product(item):output(item)||b.type().equals(ForesterHut.TYPE)&&(item.is(net.minecraft.tags.ItemTags.PLANKS)||item.is(Items.APPLE))||b.type().equals("mine")&&mineral(item));}
 /** Excavated intermediates are usable stock too; sandstone need not be crafted again from sand. */
 /** Generic bulk exports leave real slots for food, tools and paid outputs. Explicit wants still take precedence. */
 private static boolean surplusFits(OwnedChestEntity stock,ItemStack item){
  // A scarce staple may refill one stack; abundant food obeys the same space
  // reserve as other bulk exports. Explicit food/construction wants are above this filter.
  if((item.is(Items.WHEAT)||Population.nutrition(item)>0)&&stock.countItem(item.getItem())<item.getMaxStackSize())return true;
  int empty=0;for(int i=0;i<stock.getContainerSize();i++)if(stock.getItem(i).isEmpty())empty++;
  return empty>12;
 }
 /** A crowded hall returns only surplus grain to loaded farms, keeping construction reservations and four stacks for food. */
 private static Route grainOverflow(ServerLevel l,SettlementData.Entry e,BlockPos from,int load,Predicate<Route> accept){
  var hall=Workshops.hall(e);var c=hall==null?null:chest(l,e,hall);if(c==null)return null;
  int empty=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty())empty++;
  if(empty>=12)return null;
  int available=count(c,s->s.is(Items.WHEAT))-256-HallReserve.reserved(l,e,Items.WHEAT)-PorterWork.reserved(l,e,hall.id(),s->s.is(Items.WHEAT),false);
  if(available<=0)return null;
  var farms=e.settlement().buildings().stream().filter(b->b.type().equals("farm"));
  if(from!=null)farms=farms.sorted(Comparator.comparingDouble(b->position(e,b).distSqr(from)));
  for(var farm:farms.toList()){
   if(!l.hasChunkAt(position(e,farm)))continue;var dest=chest(l,e,farm);if(dest==null)continue;
   for(int slot=0;slot<c.getContainerSize();slot++){
    var stack=c.getItem(slot);if(!stack.is(Items.WHEAT))continue;
    var item=stack.copyWithCount(Math.min(load,Math.min(available,stack.getCount())));
    if(!room(dest,item))continue;var route=new Route(hall,farm,item);if(accept==null||accept.test(route))return route;
   }
  }
  return null;
 }
 private static boolean mineral(ItemStack item){return Workshops.mined(item.getItem())||Set.of(Items.SANDSTONE,Items.RED_SANDSTONE,Items.SAND,Items.RED_SAND).contains(item.getItem());}
 /** AD-147: the next route of a warehouse's courier by need (see NEED_*): {@code from} the courier's place (null: sources by id), {@code load} the
  *  most one leg takes, {@code accept} what a cart leg must fit (null: anything). */
 public static Route byNeed(ServerLevel l,SettlementData.Entry e,Settlement.Building stock,BlockPos from,int load,Predicate<Route> accept){
  var wants=Workshops.wants(l,e);Route r;
  if((r=grainOverflow(l,e,from,load,accept))!=null)return r;
  if((r=wants(l,e,wants,NEED_BUILD,from,load,accept))!=null)return r;
  if((r=wants(l,e,wants,NEED_SUPPLY,from,load,accept))!=null)return r;
  if((r=constructionInputs(l,e,wants,from,load,accept))!=null)return r;
  if((r=products(l,e,stock,from,load,accept,true))!=null)return r;
  if((r=wants(l,e,wants,NEED_FOOD,from,load,accept))!=null)return r;
  if((r=wants(l,e,wants,NEED_RESEARCH,from,load,accept))!=null)return r;
  {var b=ScienceWorks.lab(e);var c=b==null?null:chest(l,e,b);if(c!=null&&count(c,org.villageastra.server.BookResearch::work)<org.villageastra.domain.ScienceBalance.CARRY_TO_LAB)for(var demand:science()){if((r=find(l,e,b,demand,load,null,from,accept))!=null)return r;}}
  if((r=wants(l,e,wants,NEED_WAYS,from,load,accept))!=null)return r;
  return products(l,e,stock,from,load,accept,false);
 }
 /** A busy workshop publishes only its current job. The unpaid house still needs
  * ingredients for its other products before recurring generic stock replenishment. */
 private static Route constructionInputs(ServerLevel l,SettlementData.Entry e,List<Workshops.Want> wants,BlockPos from,int load,Predicate<Route> accept){
  for(var want:wants)if(want.need()==NEED_BUILD){var dest=e.settlement().buildings().stream().filter(b->b.id().equals(want.destination())).findFirst().orElse(null);if(dest==null)continue;
   var c=chest(l,e,dest);var spec=Workshops.spec(l,e,dest);if(c==null||spec==null)continue;
   for(var input:Workshops.needs(l,e,spec,HallReserve.view(l,e,c),List.of(want))){var route=find(l,e,dest,new Demand("construction_input",input::matches,input.count()+count(c,input::matches)),load,null,from,accept);if(route!=null)return route;}
  }return null;
 }
 /** The need class a route of this building's courier answers (for the card): the class of the first want it serves, or output. */
 private static Route wants(ServerLevel l,SettlementData.Entry e,List<Workshops.Want> wants,int need,BlockPos from,int load,Predicate<Route> accept){
  for(var want:wants){int n=want.need()==0?NEED_SUPPLY:want.need();if(n!=need)continue;var dest=e.settlement().buildings().stream().filter(b->b.id().equals(want.destination())).findFirst().orElse(null);if(dest==null)continue;var c=chest(l,e,dest);if(c==null)continue;
   var route=find(l,e,dest,new Demand("want",want::matches,want.count()+count(c,want::matches)),load,null,from,accept);if(route!=null)return route;}
  return null;
 }
 /** Class 6 (and the full chests before class 3): a producer's output to the stock, the fullest chest first (ties by id), from that chest only. */
 private static Route products(ServerLevel l,SettlementData.Entry e,Settlement.Building stock,BlockPos from,int load,Predicate<Route> accept,boolean fullOnly){
  if(stock==null)return null;var c=chest(l,e,stock);if(c==null)return null;
  var producers=new ArrayList<Settlement.Building>();var fills=new HashMap<UUID,Double>();
  for(var b:e.settlement().buildings())if(PRODUCERS.contains(b.type())){var source=chest(l,e,b);if(source==null)continue;double f=fill(source);if(fullOnly&&f<FULL)continue;producers.add(b);fills.put(b.id(),f);}
  producers.sort(Comparator.<Settlement.Building>comparingDouble(b->-fills.get(b.id())).thenComparing(Settlement.Building::id));
  for(var b:producers){var source=chest(l,e,b);
   for(int i=0;i<source.getContainerSize();i++){var item=source.getItem(i);if(!product(b,item))continue;
    // The stock takes a producer's surplus whatever it holds already (a stack more than it has and has coming), as long as it has room:
    // a cart takes several stacks of one harvest in one trip.
    if(!surplusFits(c,item))continue;
    Predicate<ItemStack> same=s->ItemStack.isSameItemSameTags(s,item);
    var route=find(l,e,stock,new Demand("stock",same,count(c,same)+PorterWork.reserved(l,e,stock.id(),same,true)+item.getMaxStackSize()),load,x->x.id().equals(b.id()),from,accept);if(route!=null)return route;}}
  return null;
 }
 /** The next route of a porter working at this post (null: a route with the level-I parcel). */
 public static Route choose(ServerLevel l,SettlementData.Entry e,Settlement.Building post){return choose(l,e,post,null);}
 /** The next route of a porter at this post standing at {@code from}: a warehouse's courier by need (AD-147 §1.3), any other the AD-029 order. */
 public static Route choose(ServerLevel l,SettlementData.Entry e,Settlement.Building post,BlockPos from){int load=load(l,e,post);
  if(WarehouseStore.is(post))return byNeed(l,e,post,from,load,null);
  if(SmithyDelivery.post(post))return SmithyDelivery.route(l,e,post,load);
  var recovery=grainOverflow(l,e,from,load,null);if(recovery!=null)return recovery;
  // AD-029: approved construction materials to the hall, then inputs published by workshops, then other workshop products.
  for(var want:Workshops.wants(l,e)){var dest=e.settlement().buildings().stream().filter(b->b.id().equals(want.destination())).findFirst().orElse(null);if(dest==null)continue;var c=chest(l,e,dest);if(c==null)continue;
   var route=find(l,e,dest,new Demand("want",want::matches,want.count()+count(c,want::matches)),load);if(route!=null)return route;}
  {var b=ScienceWorks.lab(e);var c=b==null?null:chest(l,e,b);if(c!=null&&count(c,org.villageastra.server.BookResearch::work)<org.villageastra.domain.ScienceBalance.CARRY_TO_LAB)for(var demand:science()){var route=find(l,e,b,demand,load);if(route!=null)return route;}}
  var stock=e.settlement().buildings().stream().filter(b->b.type().equals("warehouse")).findFirst().orElseGet(()->e.settlement().buildings().stream().filter(b->b.type().equals("town_hall")).findFirst().orElse(null));if(stock==null)return null;var c=chest(l,e,stock);if(c==null)return null;
  var inputs=constructionInputs(l,e,Workshops.wants(l,e),from,load,null);if(inputs!=null)return inputs;
  // A full producer must be able to finish delivery even when the hall already
  // holds the ordinary one-stack target. All requested routes still come first.
  var overflow=products(l,e,stock,from,load,null,true);if(overflow!=null)return overflow;
  // AD-138 (spec F6): a yard's products go to the stock too - and only those: its feed is not the farm's harvest.
  for(var b:e.settlement().buildings())if(PRODUCERS.contains(b.type())){var source=chest(l,e,b);if(source==null)continue;for(int i=0;i<source.getContainerSize();i++){var item=source.getItem(i);if(product(b,item)&&surplusFits(c,item)){var route=find(l,e,stock,new Demand("stock",s->ItemStack.isSameItemSameTags(s,item),64),load);if(route!=null)return route;}}}return null;
 }
 /** AD-155: the next want met from this building's chest only (the smithy's courier and wolves), the wants in Workshops.wants order. */
 public static Route from(ServerLevel l,SettlementData.Entry e,Settlement.Building source,int load){
  for(var want:Workshops.wants(l,e)){var dest=e.settlement().buildings().stream().filter(b->b.id().equals(want.destination())).findFirst().orElse(null);if(dest==null||dest.id().equals(source.id()))continue;var c=chest(l,e,dest);if(c==null)continue;
   var route=find(l,e,dest,new Demand("want",want::matches,want.count()+count(c,want::matches)),load,x->x.id().equals(source.id()),null,null);if(route!=null)return route;}
  return null;
 }
 /** A worker only fetches for their own workplace or exports its stock to a real outstanding demand. */
 public static Route workerRoute(ServerLevel l,SettlementData.Entry e,Settlement.Building own){
  if(own==null)return null;var wants=Workshops.wants(l,e);
  for(var want:wants)if(want.destination().equals(own.id())){var c=chest(l,e,own);if(c==null)continue;var route=find(l,e,own,new Demand("worker",want::matches,want.count()+count(c,want::matches)));if(route!=null)return route;}
  var source=chest(l,e,own);if(source==null)return null;
  // AD-155: the smith of a smithy that delivers (its courier, its wolves) stays at the anvil.
  if(SmithyDelivery.post(own)&&SmithyDelivery.delivers(l,e))return null;
  for(var want:wants)if(!want.destination().equals(own.id())){var dest=e.settlement().buildings().stream().filter(b->b.id().equals(want.destination())).findFirst().orElse(null);if(dest==null)continue;var target=chest(l,e,dest);if(target==null)continue;
   for(int i=0;i<source.getContainerSize();i++){var stack=source.getItem(i);if(stack.isEmpty()||!want.matches(stack))continue;int amount=Math.min(LOAD,Math.min(want.count()-PorterWork.reserved(l,e,dest.id(),want::matches,true),Math.min(stack.getCount(),count(source,s->ItemStack.isSameItemSameTags(s,stack))-reserve(own,stack)-constructionReserve(l,e,own,stack)-PorterWork.reserved(l,e,own.id(),s->ItemStack.isSameItemSameTags(s,stack),false))));if(amount>0&&room(target,stack.copyWithCount(amount)))return new Route(own,dest,stack.copyWithCount(amount));}
  }return null;
 }
}
