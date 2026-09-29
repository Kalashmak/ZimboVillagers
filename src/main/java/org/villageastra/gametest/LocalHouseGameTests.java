package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LocalHouseGameTests {
 @GameTest(template="empty",batch="local_house_palette",timeoutTicks=200)
 public static void localHousePaletteSurvivesSaveMoveAndUpgrade(GameTestHelper h){
  for(var wood:List.of("oak","birch","spruce","jungle","acacia","cherry","dark_oak"))for(int turn=0;turn<4;turn++){
   var s=new Settlement(UUID.randomUUID());var b=new Settlement.Building(UUID.randomUUID(),"home",4,0,4,turn,1,wood);s.addBuilding(b);
   var e=new SettlementData.Entry(s,h.getLevel().dimension().location().toString(),h.absolutePos(BlockPos.ZERO));
   var original=BuildingPlacement.layout("home",BuildingPlacement.origin(e,b),turn);var actual=BuildingPlacement.layout(e,b,"home");
   h.assertTrue(actual.size()==original.size(),"Palette preserves geometry");
   for(var cell:original.entrySet()){
    var next=actual.get(cell.getKey());for(var property:cell.getValue().getProperties())if(next.hasProperty(property))h.assertTrue(next.getValue(property).equals(cell.getValue().getValue(property)),"Palette preserves rotation, halves, joins and waterlogging");
   }
   var data=new SettlementData();data.add(e);var loaded=SettlementData.load(data.save(new CompoundTag()));var restored=loaded.entry(s.id()).settlement();
   h.assertTrue(restored.buildings().iterator().next().wood().equals(wood),"Timber is saved with the building");
   restored.moveBuilding(b.id(),20,1,20,(turn+1)%4);restored.raiseBuildingLevel(b.id(),2);
   h.assertTrue(restored.buildings().iterator().next().wood().equals(wood),"Move and upgrade preserve timber");
  }
  var before=Blocks.DARK_OAK_STAIRS.defaultBlockState();h.assertTrue(BuildingWood.apply(Map.of(BlockPos.ZERO,before),"").get(BlockPos.ZERO).equals(before),"Existing houses retain their legacy materials");h.succeed();
 }
 @GameTest(template="empty",batch="local_house_forest",timeoutTicks=200)
 public static void newHouseUsesObservedBirchRatherThanImaginaryDarkOak(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   var logs=ForestWork.wildOak(t.l,t.wood(4,1),5);var leaves=t.leavesAround(logs);
   for(var p:logs)t.l.setBlock(p,Blocks.BIRCH_LOG.defaultBlockState(),2);
   for(var p:leaves)t.l.setBlock(p,Blocks.BIRCH_LEAVES.defaultBlockState(),2);
   String wood="";for(int i=0;i<80&&wood.isEmpty();i++)wood=BuildingWood.choose(t.l,t.e,"home");
   h.assertTrue(wood.equals("birch"),"The house selects the forester's real birch tree: "+wood);
  }finally{t.done();}h.succeed();
 }
 @GameTest(template="empty",batch="local_house_survey",timeoutTicks=200)
 public static void localHouseBillRegistrationAndRepairUseSameTimber(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);var l=t.l;var e=t.e;var origin=e.center().offset(35,0,0);
  try{
   for(int x=-4;x<=17;x++)for(int z=-4;z<=17;z++){l.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(origin.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<24;y++)l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   var survey=BuildingOrders.survey(l,e,"home",1,origin,null,-1,false,null,"birch");var state=survey.state();
   h.assertTrue(survey.reason().isEmpty(),"A real flat lot surveys: "+survey.reason());var cost=state.getCompound("cost");
   h.assertTrue(cost.getInt("minecraft:birch_log")>0&&cost.getInt("villageastra:birch_framed_window")>0,"Bill uses birch for structure and windows");
   h.assertTrue(cost.getAllKeys().stream().noneMatch(k->k.contains("dark_oak")||k.contains("spruce")),"No phantom dark oak or spruce in the bill");
   // Geometry fixture, not an autonomous completion claim: place the exact quoted operations.
   for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var op=HallConstructionPlan.step((CompoundTag)raw);l.setBlock(op.pos(),op.after(),3);}
   h.assertTrue(BuildingOrders.complete(l,e,state),"Quoted final geometry registers");
   var b=e.settlement().buildings().stream().filter(x->x.id().equals(BuildingOrders.buildingId(state))).findFirst().orElseThrow();
   h.assertTrue(b.wood().equals("birch")&&BuildingRepairs.damage(l,e,b).isEmpty(),"Repair does not replace birch with legacy dark oak");
   var plaque=BuildingSigns.position(e,b);BuildingSigns.refresh(l,e);
   h.assertTrue(BuildingSigns.target(l,plaque)!=null&&BuildingSigns.owned(l,e,b,plaque),"The birch entrance plaque still opens the building");
   var sign=(net.minecraft.world.level.block.entity.SignBlockEntity)l.getBlockEntity(plaque);
   h.assertTrue(sign.getFrontText().getMessage(0,false).equals(net.minecraft.network.chat.Component.translatable("building.villageastra.home")),"The local wood sign receives its building label");
   var cell=BuildingPlacement.layout(e,b,"home").entrySet().stream().filter(c->c.getValue().is(Blocks.BIRCH_LOG)).findFirst().orElseThrow();l.setBlock(cell.getKey(),Blocks.AIR.defaultBlockState(),3);
   var repair=BuildingOrders.survey(l,e,"home",b.rotation(),origin,b);
   h.assertTrue(repair.state().getString("wood").equals("birch")&&repair.state().getCompound("cost").getInt("minecraft:birch_log")==1,"Repair quotes one real birch log");
   l.setBlock(cell.getKey(),cell.getValue(),3);var destination=origin.south(25);
   for(int x=-4;x<=17;x++)for(int z=-4;z<=17;z++){l.setBlock(destination.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(destination.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<24;y++)l.setBlock(destination.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   var move=Relocations.plan(l,e,b,destination,2);
   h.assertTrue(move.reason().isEmpty()&&move.state().getString("wood").equals("birch"),"Relocation retains the saved palette: "+move.reason());
   h.assertTrue(move.state().getCompound("cost").getAllKeys().stream().noneMatch(k->k.contains("dark_oak")||k.contains("spruce")),"Relocation does not charge for a different wood species");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="local_house_binding",timeoutTicks=200)
 public static void allLocalTimbersHavePaidStrippingRecipes(GameTestHelper h){
  for(var wood:List.of("oak","birch","spruce","jungle","acacia","cherry","dark_oak")){
   var logs=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("minecraft",wood+"_log"));
   var stripped=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("minecraft","stripped_"+wood+"_log"));
   var c=new net.minecraft.world.SimpleContainer(new ItemStack(logs),new ItemStack(Items.WOODEN_AXE));
   var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),c,List.of(new Workshops.Want(Ingredient.of(stripped),1,UUID.randomUUID())));
   h.assertTrue(job!=null&&job.recipe().equals("custom:strip_"+wood+"_log")&&job.toolDamage()==1,"Local timber has a paid, tool-wearing stripping recipe: "+wood);
  }h.succeed();
 }
 @GameTest(template="empty",batch="local_house_binding",timeoutTicks=200)
 public static void allLocalWindowsCraftFromRawGlassAndWood(GameTestHelper h){
  for(var wood:List.of("oak","birch","spruce","jungle","acacia","cherry","dark_oak")){
   var registry=net.minecraft.core.registries.BuiltInRegistries.ITEM;
   var plank=registry.get(new net.minecraft.resources.ResourceLocation("minecraft",wood+"_planks"));
   var window=registry.get(new net.minecraft.resources.ResourceLocation("villageastra",wood+"_framed_window"));
   var c=new net.minecraft.world.SimpleContainer(new ItemStack(Items.GLASS_PANE),new ItemStack(Items.STICK,7),new ItemStack(plank));
   var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),c,List.of(new Workshops.Want(Ingredient.of(window),1,UUID.randomUUID())));
   h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(window))&&job.inputs().stream().noneMatch(in->in.ingredient().test(new ItemStack(window))),"A local frame can be crafted from raw supplies without another frame: "+wood);
  }h.succeed();
 }
 @GameTest(template="empty",batch="local_house_binding",timeoutTicks=200)
 public static void bookDemandUsesExistingProducers(GameTestHelper h){
  var t=ResearchV2Town.town(h,"farm");
  try{
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);
   t.s.admit(r,Settlement.childId(t.s.id(),"home"));
   var chest=new net.minecraft.world.SimpleContainer(new ItemStack(Items.PAPER,3),new ItemStack(Items.WHEAT,2));
   var wants=List.of(new Workshops.Want(Ingredient.of(Items.BOOK),1,t.hall().id()));
   var needs=Workshops.needs(t.l,t.e,Workshops.spec("town_hall"),chest,wants);
   h.assertTrue(needs.stream().noneMatch(in->in.ingredient().test(new ItemStack(Items.LEATHER))),"No livestock: request renewable paper binding instead of absent leather");
   h.assertTrue(needs.stream().anyMatch(in->in.ingredient().test(new ItemStack(Items.SUGAR_CANE))),"Three sheets are not enough: gather cane for the remaining paper");
   chest.setItem(1,new ItemStack(Items.LEATHER));
   var job=Workshops.plan(t.l,Workshops.spec("town_hall"),chest,wants);
   h.assertTrue(job!=null&&!job.recipe().equals("custom:paper_binding"),"Real leather in stock still allows the ordinary book recipe");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="local_house_binding",timeoutTicks=200)
 public static void paperBoundBooksConsumeRenewableIngredients(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);var hall=Workshops.hall(t.e);var chest=LogisticsRoutes.chest(t.l,t.e,hall);
  try{
   chest.clearContent();var wants=List.of(new Workshops.Want(Ingredient.of(Items.BOOK),1,hall.id()));
   h.assertTrue(Workshops.plan(t.l,Workshops.spec("town_hall"),chest,wants)==null,"No free book");
   chest.setItem(0,new ItemStack(Items.PAPER,6));chest.setItem(1,new ItemStack(Items.WHEAT,2));
   var job=Workshops.plan(t.l,Workshops.spec("town_hall"),chest,wants);h.assertTrue(job!=null&&job.recipe().equals("custom:paper_binding"),"Paper and wheat paste replace leather binding through an explicit recipe");
   for(int i=0;i<1000&&chest.countItem(Items.BOOK)==0;i++)Workshops.advance(t.l,t.e,hall,t.l.getGameTime()+20L*i,wants);
   h.assertTrue(chest.countItem(Items.BOOK)==1&&chest.countItem(Items.PAPER)==0&&chest.countItem(Items.WHEAT)==0,"One book, paid inputs and actual workshop labor");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
