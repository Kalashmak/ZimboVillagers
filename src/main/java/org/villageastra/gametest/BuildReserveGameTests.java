package org.villageastra.gametest;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-137 (BUILD-RESERVE): what the active construction project has not withdrawn yet stays in the hall stock for its builder. Workshops,
 *  the miner, trade, porters and the road crew see the stock less that reserve; the shortfall stays demand; the reserve shrinks with every
 *  withdrawal of the builder and ends with the project. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BuildReserveGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building hall,OwnedChestEntity chest,BlockPos pos){}
  /** A hall with its real stock chest and nobody living there: no worker ticks between the steps. */
 private static Town town(GameTestHelper h,int dx){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2+dx,2,1));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var pos=LogisticsRoutes.position(e,hall);l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var chest=LogisticsRoutes.chest(l,e,hall);if(chest==null)throw new IllegalStateException("No hall chest at "+pos);chest.clearContent();
  return new Town(l,s,e,hall,chest,pos);
 }
 private static void done(Town t){
  HallUpgradeGoal.drop(t.l,t.s.id());
  try{Files.deleteIfExists(Roads.projectPath(t.l,t.s.id()));Files.deleteIfExists(Workshops.path(t.l,t.hall.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  t.l.setBlock(t.pos,Blocks.AIR.defaultBlockState(),2);SettlementData.get(t.l.getServer()).remove(t.s.id());
 }
 private static String key(Item item){return BuiltInRegistries.ITEM.getKey(item).toString();}
 /** A queued, unfunded project with this estimate and this cargo already withdrawn. */
 private static CompoundTag project(Map<Item,Integer> cost,ItemStack... cargo){
  var t=new CompoundTag();t.putInt("schema",1);var id=UUID.randomUUID();t.putUUID("id",id);t.putUUID("project",id);t.put("ops",new ListTag());
  var c=new CompoundTag();cost.forEach((item,n)->c.putInt(key(item),n));t.put("cost",c);var list=new ListTag();for(var s:cargo)list.add(s.save(new CompoundTag()));t.put("cargo",list);return t;
 }
 private static void queue(Town t,CompoundTag state){HallUpgradeGoal.store(t.l,t.s.id(),state);}
 private static int count(Town t,Item item){return LogisticsRoutes.count(t.chest,s->s.is(item));}
 private static int slot(Town t,Item item){for(int i=0;i<t.chest.getContainerSize();i++)if(t.chest.getItem(i).is(item))return i;return -1;}
 /** A withdrawal from the hall under this journal id, as every consumer makes it. */
 private static ItemStack take(Town t,UUID id,Item item,int amount){int i=slot(t,item);if(i<0)return ItemStack.EMPTY;var s=t.chest.getItem(i);return WorldJournal.takeAmount(t.l,id,t.pos,i,s.copy(),Math.min(amount,s.getCount()));}
 private static UUID fund(CompoundTag state){return Settlement.childId(state.getUUID("id"),"fund/"+state.getInt("withdrawals"));}
 /** The builder's step: his funding take, booked into the project's cargo. */
 private static ItemStack builderTakes(Town t,CompoundTag state,Item item,int amount){
  var got=take(t,fund(state),item,amount);if(!got.isEmpty()){state.getList("cargo",Tag.TAG_COMPOUND).add(got.save(new CompoundTag()));state.putInt("withdrawals",state.getInt("withdrawals")+1);queue(t,state);}return got;
 }
 private static CompoundTag road(Item item,int n){var t=new CompoundTag();t.putUUID("id",UUID.randomUUID());var ops=new ListTag();for(int i=0;i<n;i++){var op=new CompoundTag();op.putString("item",key(item));ops.add(op);}t.put("ops",ops);t.put("cargo",new ListTag());t.put("returns",new ListTag());return t;}

 @GameTest(template="empty",timeoutTicks=100) public static void otherConsumersLeaveTheReserveToTheBuilder(GameTestHelper h){
  var t=town(h,0);
  try{
   t.chest.setItem(0,new ItemStack(Items.LANTERN,10));t.chest.setItem(1,new ItemStack(Items.OAK_PLANKS,8));
   var state=project(Map.of(Items.LANTERN,6,Items.OAK_PLANKS,8));queue(t,state);
   h.assertTrue(HallReserve.reserved(t.l,t.e,Items.LANTERN)==6&&HallReserve.available(t.l,t.e,Items.LANTERN)==4,"Six lanterns kept, four free: "+HallReserve.reserved(t.l,t.e));
   h.assertTrue(HallReserve.available(t.l,t.e,Items.OAK_PLANKS)==0,"Every plank is kept");
   // Trade (its sale keeps it) and the porters (their route leaves it) read the same reserve.
   h.assertTrue(LogisticsRoutes.constructionReserve(t.l,t.e,t.hall,new ItemStack(Items.LANTERN))==6,"Trade and porters keep the reserve");
   // The hall as a workshop (the builder's sticks): it plans on the stock less the reserve, publishes the planks as its lack and takes none.
   var spec=Workshops.spec("town_hall");var wants=List.of(new Workshops.Want(Ingredient.of(Items.STICK),4,t.hall.id()));
   h.assertTrue(Workshops.plan(t.l,spec,t.chest,wants)!=null,"Sanity: on the whole stock the hall could make sticks");
   h.assertTrue(Workshops.plan(t.l,spec,HallReserve.view(t.l,t.e,t.chest),wants)==null,"On the stock less the reserve it cannot");
   h.assertTrue(!Workshops.needs(t.l,spec,HallReserve.view(t.l,t.e,t.chest),wants).isEmpty(),"Its lack is published as demand");
   long now=t.l.getGameTime();for(int i=0;i<3;i++)Workshops.advance(t.l,t.e,t.hall,now+40L*i,wants);
   h.assertTrue(count(t,Items.OAK_PLANKS)==8,"The hall workshop took no reserved plank: "+count(t,Items.OAK_PLANKS));
   // The miner restocking his lights: the journal's guard cuts his take to what is free, then refuses.
   h.assertTrue(HallReserve.free(t.l,t.pos,new ItemStack(Items.LANTERN))==4,"Four lanterns are free for the miner");
   var miner=take(t,UUID.randomUUID(),Items.LANTERN,10);
   h.assertTrue(miner.getCount()==4&&count(t,Items.LANTERN)==6,"The miner gets the four free lanterns only: "+miner);
   h.assertTrue(take(t,UUID.randomUUID(),Items.LANTERN,1).isEmpty(),"Then nothing more");
   // The road crew loading at the hall.
   var roadProject=road(Items.OAK_PLANKS,3);
   h.assertTrue(Roads.load(t.l,t.e,t.hall,roadProject)==0&&count(t,Items.OAK_PLANKS)==8,"The road crew takes no reserved plank");
   // The builder himself takes it all.
   h.assertTrue(builderTakes(t,state,Items.LANTERN,6).getCount()==6&&builderTakes(t,state,Items.OAK_PLANKS,8).getCount()==8,"The builder gets his estimate");
   h.assertTrue(HallReserve.reserved(t.l,t.e).isEmpty()&&count(t,Items.LANTERN)==0&&count(t,Items.OAK_PLANKS)==0,"Withdrawn, nothing is reserved any more: "+HallReserve.reserved(t.l,t.e));
  }finally{done(t);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=100) public static void theReserveShrinksWithTheBuilderAndEndsWithTheProject(GameTestHelper h){
  var t=town(h,0);
  try{
   t.chest.setItem(0,new ItemStack(Items.COBBLESTONE,12));
   var state=project(Map.of(Items.COBBLESTONE,10));queue(t,state);
   h.assertTrue(HallReserve.available(t.l,t.e,Items.COBBLESTONE)==2,"Two of twelve are free");
   var other=new Settlement.Building(UUID.randomUUID(),"farm",20,0,0);
   h.assertTrue(HallReserve.keptFrom(t.l,t.e,t.hall,Items.COBBLESTONE)==0&&HallReserve.keptFrom(t.l,t.e,other,Items.COBBLESTONE)==10,"The owner sees its own material; another building keeps the same live reserve");
   h.assertTrue(builderTakes(t,state,Items.COBBLESTONE,4).getCount()==4,"The builder takes four");
   h.assertTrue(HallReserve.reserved(t.l,t.e,Items.COBBLESTONE)==6&&HallReserve.available(t.l,t.e,Items.COBBLESTONE)==2,"The reserve shrinks by what he took, the free part stays: "+HallReserve.reserved(t.l,t.e));
   h.assertTrue(take(t,fund(project(Map.of())),Items.COBBLESTONE,8).getCount()==2,"A stranger's take is still cut to the free part");
   // The mayor pauses the project: the stock is free meanwhile; resumed, it is kept again.
   var mayor=UUID.randomUUID();var g=t.s.governance();g.appointPlayer(mayor);var id=HallConstructionPlan.projectId(state);
   h.assertTrue(g.setPaused(mayor,g.epoch(),g.revision(),id,true)&&HallReserve.reserved(t.l,t.e).isEmpty(),"A paused project keeps nothing");
   h.assertTrue(HallReserve.keptFrom(t.l,t.e,other,Items.COBBLESTONE)==0,"A subsequent view observes pause immediately");
   h.assertTrue(g.setPaused(mayor,g.epoch(),g.revision(),id,false)&&HallReserve.reserved(t.l,t.e,Items.COBBLESTONE)==6,"Resumed, it keeps its rest again");
   h.assertTrue(HallReserve.keptFrom(t.l,t.e,other,Items.COBBLESTONE)==6,"A subsequent view observes resumed remaining material");
   var saved=t.chest.getItem(0).copy();t.chest.setItem(0,ItemStack.EMPTY);
   h.assertTrue(HallReserve.keptFrom(t.l,t.e,other,Items.COBBLESTONE)==0,"Actual stock is read anew even when the plan did not change");t.chest.setItem(0,saved);
   state.putBoolean("funded",true);queue(t,state);
   h.assertTrue(HallReserve.reserved(t.l,t.e).isEmpty(),"Funded, the builder holds all of it: nothing is kept");
   state.putBoolean("funded",false);queue(t,state);
   h.assertTrue(HallReserve.reserved(t.l,t.e,Items.COBBLESTONE)==6,"A top-up is kept again");
   HallUpgradeGoal.drop(t.l,t.s.id());
   h.assertTrue(HallReserve.reserved(t.l,t.e).isEmpty()&&HallReserve.available(t.l,t.e,Items.COBBLESTONE)==6,"Called off, the stock is free");
   h.assertTrue(take(t,UUID.randomUUID(),Items.COBBLESTONE,6).getCount()==6,"And anyone may take it");
  }finally{done(t);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=100) public static void theShortfallStaysDemandAndReservedStockMeetsNoOtherDemand(GameTestHelper h){
  var t=town(h,0);
  try{
   t.chest.setItem(0,new ItemStack(Items.LANTERN,5));
   queue(t,project(Map.of(Items.LANTERN,8)));
   var lantern=new ItemStack(Items.LANTERN);
   int build=Workshops.wants(t.l,t.e).stream().filter(w->w.destination().equals(t.hall.id())&&w.matches(lantern)).mapToInt(Workshops.Want::count).sum();
   h.assertTrue(build==3,"The project's shortfall is the village's demand: "+build);
   Roads.save(t.l,t.s.id(),road(Items.LANTERN,2));
   int roadWant=Roads.wants(t.l,t.e,t.hall).stream().filter(w->w.matches(lantern)).mapToInt(Workshops.Want::count).sum();
   h.assertTrue(roadWant==2,"The kept lanterns do not meet the road's need: it wants two more ("+roadWant+")");
   int all=Workshops.wants(t.l,t.e).stream().filter(w->w.matches(lantern)).mapToInt(Workshops.Want::count).sum();
   h.assertTrue(all==5,"Workshops are asked for the project's three and the road's two: "+all);
   // The workshops deliver five: the project is covered and the road's two lie free.
   t.chest.setItem(1,new ItemStack(Items.LANTERN,5));
   h.assertTrue(Workshops.wants(t.l,t.e).stream().noneMatch(w->w.matches(lantern)),"Nothing is wanted any more");
   h.assertTrue(HallReserve.available(t.l,t.e,Items.LANTERN)==2,"Two lanterns are free for the road");
  }finally{done(t);}
  h.succeed();
 }

 @GameTest(template="empty",batch="construction_demand",timeoutTicks=100)
 public static void splitCargoAndSameTickTransfersKeepOnlyTheRealShortfall(GameTestHelper h){
  var t=town(h,0);
  try{
   var named=new ItemStack(Items.LANTERN,2);named.setHoverName(net.minecraft.network.chat.Component.literal("Stored lanterns"));
   var state=project(Map.of(Items.LANTERN,8,Items.COBBLESTONE,12),new ItemStack(Items.LANTERN),named,new ItemStack(Items.COBBLESTONE,3));
   t.chest.setItem(0,new ItemStack(Items.LANTERN,2));t.chest.setItem(1,new ItemStack(Items.COBBLESTONE,4));queue(t,state);
   java.util.function.Function<Item,Integer> demand=item->Workshops.wants(t.l,t.e).stream().filter(w->w.need()==LogisticsRoutes.NEED_BUILD&&w.destination().equals(t.hall.id())&&w.matches(new ItemStack(item))).mapToInt(Workshops.Want::count).sum();
   h.assertTrue(demand.apply(Items.LANTERN)==3&&demand.apply(Items.COBBLESTONE)==5,"Split cargo and live stock cover their own materials, including named stacks");
   h.assertTrue(builderTakes(t,state,Items.LANTERN,2).getCount()==2,"The builder really withdraws two lanterns");
   h.assertTrue(demand.apply(Items.LANTERN)==3,"Moving paid stock into cargo in the same tick neither loses nor duplicates coverage");
   t.chest.setItem(0,new ItemStack(Items.LANTERN,3));
   h.assertTrue(demand.apply(Items.LANTERN)==0&&demand.apply(Items.COBBLESTONE)==5,"A same-tick delivery closes only its own shortfall");
   t.chest.setItem(0,ItemStack.EMPTY);
   h.assertTrue(demand.apply(Items.LANTERN)==3,"Removing unwithdrawn stock immediately restores demand");
   state.putBoolean("funded",true);queue(t,state);
   h.assertTrue(demand.apply(Items.LANTERN)==0&&demand.apply(Items.COBBLESTONE)==0,"Funded construction no longer requests its estimate");
  }finally{done(t);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=100) public static void twoProjectsNeverCountTwice(GameTestHelper h){
  var a=town(h,0);var b=town(h,6);
  try{
   a.chest.setItem(0,new ItemStack(Items.OAK_PLANKS,6));b.chest.setItem(0,new ItemStack(Items.OAK_PLANKS,6));
   var first=project(Map.of(Items.OAK_PLANKS,6));queue(a,first);queue(b,project(Map.of(Items.OAK_PLANKS,2)));
   h.assertTrue(HallReserve.available(a.l,a.e,Items.OAK_PLANKS)==0&&HallReserve.available(b.l,b.e,Items.OAK_PLANKS)==4,"Each hall keeps its own project's rest only");
   h.assertTrue(take(b,UUID.randomUUID(),Items.OAK_PLANKS,6).getCount()==4,"The other village's project does not hold this hall");
   // The slot takes another project: only the new one is kept, never the sum.
   HallUpgradeGoal.drop(a.l,a.s.id());var second=project(Map.of(Items.OAK_PLANKS,3));HallUpgradeGoal.enqueue(a.l,a.e,second);
   h.assertTrue(HallReserve.reserved(a.l,a.e,Items.OAK_PLANKS)==3&&HallReserve.available(a.l,a.e,Items.OAK_PLANKS)==3,"The new project replaces the old one's reserve: "+HallReserve.reserved(a.l,a.e));
   h.assertTrue(take(a,fund(first),Items.OAK_PLANKS,6).getCount()==3,"The old project's builder take is a stranger's now");
  }finally{done(a);done(b);}
  h.succeed();
 }

 /** The parallel annex work (AD-135): an annex is an ordinary building project with annexOf, kept exactly the same way. */
 @GameTest(template="empty",timeoutTicks=100) public static void anAnnexProjectIsKeptLikeAnyBuilding(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var hut=new Settlement.Building(Settlement.childId(s.id(),"building/forester"),ForesterHut.TYPE,20,0,8,0,3);s.addBuilding(hut);
  for(int x=-4;x<48;x++)for(int z=-4;z<40;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<14;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var pos=LogisticsRoutes.position(e,hall);l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var t=new Town(l,s,e,hall,LogisticsRoutes.chest(l,e,hall),pos);
  try{
   var kind=Annexes.kind("carpentry_annex");
   if(kind.research()!=null&&ResearchCatalog.NODES.containsKey(kind.research())){var record=BookResearch.inspect(l,e);var done=record.getList("legacyDone",Tag.TAG_STRING);done.add(StringTag.valueOf(kind.research()));record.put("legacyDone",done);BookResearch.store(l,e,record);ResearchKnobs.forget(s.id());}
   h.assertTrue(Annexes.order(l,e,hut,kind).isEmpty(),"The annex is ordered");
   var state=HallUpgradeGoal.inspect(l,s.id());h.assertTrue(state.hasUUID("annexOf")&&BuildingOrders.isBuilding(state),"It is a building project with annexOf");
   var cost=state.getCompound("cost");var name=cost.getAllKeys().stream().sorted().filter(k->BuiltInRegistries.ITEM.get(new ResourceLocation(k)).getMaxStackSize()==64).findFirst().orElseThrow();var item=BuiltInRegistries.ITEM.get(new ResourceLocation(name));int need=cost.getInt(name);
   // The estimate and two more, spread over as many slots as it takes.
   t.chest.clearContent();int left=need+2;for(int i=0;left>0;i++){int n=Math.min(left,item.getMaxStackSize());t.chest.setItem(i,new ItemStack(item,n));left-=n;}
   h.assertTrue(HallReserve.reserved(l,e,item)==need&&HallReserve.available(l,e,item)==2,"The annex keeps its "+need+" "+name+": "+HallReserve.reserved(l,e));
   var stranger=take(t,UUID.randomUUID(),item,need+2);
   h.assertTrue(stranger.getCount()==2&&count(t,item)==need,"A stranger gets only what lies above the estimate: "+stranger);
   var got=builderTakes(t,state,item,need);
   h.assertTrue(!got.isEmpty()&&got.getCount()==Math.min(need,t.chest.getItem(0).getCount()+got.getCount()),"The annex's builder takes from the kept stock: "+got);
   h.assertTrue(HallReserve.reserved(l,e,item)==need-got.getCount()&&HallReserve.available(l,e,item)==0,"The reserve shrinks with his withdrawal: "+HallReserve.reserved(l,e));
  }finally{done(t);}
  h.succeed();
 }
}
