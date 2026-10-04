package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.CoreCatalog;
import org.villageastra.domain.Settlement;
import org.villageastra.domain.Resident;
import org.villageastra.world.*;

/** Finite, paid endgame fixtures. They test production, not natural world progression. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class TerminalSupplyGameTests {
 @GameTest(template="empty",batch="terminal_supply",timeoutTicks=200)
 public static void allTerminalEstimatesHaveAutonomousRecipeRoutes(GameTestHelper h){
  var t=ResearchV2Town.town(h,"smithy");
  try{
   ResearchV2Town.raise(t,5);
   t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1),Settlement.childId(t.s.id(),"home"));
   int index=0;for(var type:List.of("mine","farm","forester","livestock"))t.s.addBuilding(new Settlement.Building(UUID.randomUUID(),type,-60,0,20*index++));
   var types=new TreeSet<>(BuildingOrders.ORDERABLE);types.addAll(List.of("town_hall","farm","mine","forester"));
   var absent=new TreeMap<String,Set<String>>();
   for(var type:types)if(BuildingTiers.upgradable(type)){
    var cost=new TreeMap<>(BuildingTiers.cost(type,BuildingTiers.max(type)));
    if(type.equals("farm"))FarmBarn.addedCost(BuildingTiers.max(type)).forEach((id,n)->cost.merge(id,n,Integer::sum));
    for(var id:cost.keySet())if(!Workshops.producible(t.l,t.e,id))absent.computeIfAbsent(id,k->new TreeSet<>()).add(type);
   }
   h.assertTrue(absent.isEmpty(),"Unsupported terminal construction inputs: "+absent);
   h.assertTrue(MayorPlanner.affordable(t.l,t.e,Map.of(CoreCatalog.ringId(6),1)),"NPC mayor accepts the funded production route to ring VI");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="terminal_supply")
 public static void terminalAcceptanceRejectsCountersWithoutWalls(GameTestHelper h){
  var t=ResearchV2Town.town(h,"smithy");
  try{
   var b=ResearchV2Town.raise(t,6);h.assertTrue(TerminalProgress.missing(t.l,t.e,b)==0,"Complete terminal fixture stands");
   var wall=BuildingPlacement.layout(t.e,b,BuildingTiers.layoutId("smithy",6)).entrySet().stream().filter(c->!c.getValue().isAir()&&c.getKey().getY()>t.e.center().getY()+2&&c.getValue().getBlock() instanceof net.minecraft.world.level.block.StairBlock).findFirst().orElseThrow();
   t.l.setBlock(wall.getKey(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(TerminalProgress.missing(t.l,t.e,b)>0,"A missing roof block rejects terminal acceptance even if the kept counter is VI");
   h.assertTrue(TerminalProgress.blockers(t.l,t.e).keySet().stream().anyMatch(k->k.startsWith("smithy/")),"The damaged terminal building is named");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="terminal_supply",timeoutTicks=200)
 public static void smithyFiveMakesTerminalRingFromFiniteOverworldMaterials(GameTestHelper h){
  var t=ResearchV2Town.town(h,"smithy");
  try{
   var b=ResearchV2Town.raise(t,5);var stock=LogisticsRoutes.chest(t.l,t.e,b);stock.clearContent();
   int slot=0;for(var item:List.of(Items.IRON_INGOT,Items.COPPER_INGOT,Items.GOLD_INGOT,Items.DIAMOND))stock.setItem(slot++,new ItemStack(item,36));
   stock.setItem(slot,new ItemStack(Items.COAL,2));
   var ring=VillageAstra.CORE_RINGS.get(6).get();
   var wants=List.of(new Workshops.Want(Ingredient.of(ring),1,b.id()));
   var plan=Workshops.plan(t.l,t.e,b,stock,wants);
   h.assertTrue(plan!=null&&plan.recipe().equals("custom:reinforced_terminal_ring"),"Smithy V has a paid Overworld route to ring VI: "+plan);
   for(long now=1000;now<100000&&stock.countItem(ring)==0;now+=20)Workshops.advance(t.l,t.e,b,now,wants);
   h.assertTrue(stock.countItem(ring)==1,"One finished ring returned to stock");
   for(var item:List.of(Items.IRON_INGOT,Items.COPPER_INGOT,Items.GOLD_INGOT,Items.DIAMOND))h.assertTrue(stock.countItem(item)==0,"All 36 "+item+" consumed");
   h.assertTrue(stock.countItem(Items.COAL)==1,"One real coal consumed");
   h.assertTrue(Workshops.plan(t.l,t.e,b,stock,wants)==null,"Empty ingredients cannot fund a second ring");
   h.assertTrue(!Workshops.makeable(t.l,t.e,"minecraft:netherite_block"),"The alternative does not invent a Nether resource source");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="terminal_supply")
 public static void fallenRoofDecorationDoesNotStallDemolition(GameTestHelper h){
  var l=h.getLevel();var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,3,2));
  l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
  var op=new net.minecraft.nbt.CompoundTag();op.putLong("pos",pos.asLong());
  op.put("before",net.minecraft.nbt.NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState()));
  op.put("after",net.minecraft.nbt.NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
  h.assertTrue(BuildingOrders.reconcile(l,op,pos.below()),"Dropped decoration is already demolished");
  h.assertTrue(HallConstructionPlan.step(op).before().isAir(),"No second destructive operation or free materials");
  op.put("after",net.minecraft.nbt.NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()));
  op.put("before",net.minecraft.nbt.NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState()));
  h.assertTrue(!BuildingOrders.reconcile(l,op,pos.below()),"A disappeared block cannot silently fund replacement");h.succeed();
 }
 @GameTest(template="empty",batch="terminal_supply")
 public static void terminalRingNeedsWorkingSmithyFiveAndEveryIngredient(GameTestHelper h){
  var ring=VillageAstra.CORE_RINGS.get(6).get();var stock=new SimpleContainer(27);
  for(var item:List.of(Items.IRON_INGOT,Items.COPPER_INGOT,Items.GOLD_INGOT,Items.DIAMOND))stock.addItem(new ItemStack(item,36));
  stock.addItem(new ItemStack(Items.COAL));var wants=List.of(new Workshops.Want(Ingredient.of(ring),1,UUID.randomUUID()));
  h.assertTrue(Workshops.plan(h.getLevel(),Workshops.spec("smithy",4),stock,wants)==null,"Smithy IV cannot use the terminal recipe");
  h.assertTrue(Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants)==null,"Bootstrap hall cannot bypass smithy V");
  stock.removeItem(3,1);
  h.assertTrue(Workshops.plan(h.getLevel(),Workshops.spec("smithy",5),stock,wants)==null,"35 diamonds are insufficient");
  var needs=Workshops.needs(h.getLevel(),null,Workshops.spec("smithy",5),stock,wants);
  h.assertTrue(needs.stream().anyMatch(n->n.matches(new ItemStack(Items.DIAMOND))),"Missing diamond is actual supply demand");
  h.succeed();
 }
}
