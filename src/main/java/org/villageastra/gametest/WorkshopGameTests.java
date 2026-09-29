package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-029: workshop jobs pay real inputs, produce exact outputs once, chain intermediates and publish missing inputs. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkshopGameTests {
 private record Bench(ServerLevel l,Settlement s,SettlementData.Entry e,Map<String,Settlement.Building> buildings){
  OwnedChestEntity chest(String type){return LogisticsRoutes.chest(l,e,buildings.get(type));}
 }
 private static Bench bench(GameTestHelper h,String... types){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());var map=new LinkedHashMap<String,Settlement.Building>();int i=0;
  for(var type:types){var b=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,(i%3)*14,0,(i/3)*12);s.addBuilding(b);map.put(type,b);
   var chestPos=center.offset(b.x()+1,b.y()+1,b.z()+4);l.setBlock(chestPos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(center.offset(b.x()+4,b.y()+1,b.z()+4),net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState(),2);i++;}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Bench(l,s,e,map);
 }
 private static List<Workshops.Want> want(Item item,int count,Settlement.Building b){return List.of(new Workshops.Want(Ingredient.of(item),count,b.id()));}
 private static String run(Bench b,String type,List<Workshops.Want> wants,int steps){String last="";long now=1000;for(int i=0;i<steps;i++){last=Workshops.advance(b.l,b.e,b.buildings.get(type),now,wants);var state=Workshops.inspect(b.l,b.buildings.get(type).id());if(state.getBoolean("physicalSmelt")&&state.contains("furnace")){var pos=BlockPos.of(state.getLong("furnace"));if(b.l.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity f)for(int tick=0;tick<20;tick++)net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(b.l,pos,b.l.getBlockState(pos),f);}now+=20;if(last.equals("workshop_complete"))break;}return last;}
 private static int count(OwnedChestEntity c,Item item){return LogisticsRoutes.count(c,s->s.is(item));}
 @GameTest(template="empty",timeoutTicks=100) public static void carpenterMakesPlanksOnceFromRealLogs(GameTestHelper h){
  var b=bench(h,"carpentry");var c=b.chest("carpentry");c.setItem(0,new ItemStack(Items.OAK_LOG,2));var wants=want(Items.OAK_PLANKS,4,b.buildings.get("carpentry"));
  h.assertTrue(run(b,"carpentry",wants,40).equals("workshop_complete"),"Job completes");
  h.assertTrue(count(c,Items.OAK_PLANKS)==4&&count(c,Items.OAK_LOG)==1,"One log became exactly four planks: planks="+count(c,Items.OAK_PLANKS)+" logs="+count(c,Items.OAK_LOG));
  var record=Workshops.inspect(b.l,b.buildings.get("carpentry").id());h.assertTrue(record.getString("stage").equals("idle")&&record.getInt("withdrawals")==1,"Single journaled withdrawal");
  h.assertTrue(Workshops.advance(b.l,b.e,b.buildings.get("carpentry"),5000,List.of()).equals("workshop_idle")&&count(c,Items.OAK_PLANKS)==4,"Without demand nothing more is produced");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void carpenterChainsIntermediatesForLadders(GameTestHelper h){
  var b=bench(h,"carpentry");var c=b.chest("carpentry");c.setItem(0,new ItemStack(Items.OAK_LOG,1));var wants=want(Items.LADDER,3,b.buildings.get("carpentry"));
  for(int job=0;job<6&&count(c,Items.LADDER)==0;job++)run(b,"carpentry",wants,60);
  h.assertTrue(count(c,Items.LADDER)==3&&count(c,Items.OAK_LOG)==0,"Log to planks to sticks to ladders: ladders="+count(c,Items.LADDER)+" sticks="+count(c,Items.STICK)+" planks="+count(c,Items.OAK_PLANKS));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void masonSmeltsGlassWithRealFuelAndPublishesMissingFuel(GameTestHelper h){
  var b=bench(h,"masonry");var c=b.chest("masonry");c.setItem(0,new ItemStack(Items.SAND,4));var wants=want(Items.GLASS,4,b.buildings.get("masonry"));
  h.assertTrue(Workshops.advance(b.l,b.e,b.buildings.get("masonry"),1000,wants).equals("workshop_missing_inputs"),"No fuel: nothing is planned");
  h.assertTrue(Workshops.published(b.l,b.buildings.get("masonry").id()).stream().anyMatch(in->in.matches(new ItemStack(Items.COAL))),"Missing fuel is published for porters");
  c.setItem(1,new ItemStack(Items.COAL,1));h.assertTrue(run(b,"masonry",wants,200).equals("workshop_complete"),"Smelting batch completes");
  h.assertTrue(count(c,Items.GLASS)==4&&count(c,Items.SAND)==0&&count(c,Items.COAL)==0,"Four sand and one coal became four glass: glass="+count(c,Items.GLASS)+" coal="+count(c,Items.COAL));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void breadDemandFlowsFromBakeryToMill(GameTestHelper h){
  var b=bench(h,"town_hall","mill","restaurant");b.chest("restaurant").setItem(0,new ItemStack(Items.OAK_PLANKS,2));b.chest("mill").setItem(0,new ItemStack(Items.WHEAT,4));
  h.assertTrue(Workshops.wants(b.l,b.e).stream().anyMatch(w->w.matches(new ItemStack(Items.BREAD))),"Bread stock is wanted at the hall");
  h.assertTrue(Workshops.advance(b.l,b.e,b.buildings.get("restaurant"),1000,Workshops.wants(b.l,b.e)).equals("workshop_missing_inputs"),"Bakery lacks flour");
  h.assertTrue(Workshops.wants(b.l,b.e).stream().anyMatch(w->w.matches(new ItemStack(VillageAstra.FLOUR.get()))&&w.destination().equals(b.buildings.get("restaurant").id())),"Bakery need becomes a want");
  for(int i=0;i<6&&count(b.chest("mill"),VillageAstra.FLOUR.get())<4;i++)run(b,"mill",Workshops.wants(b.l,b.e),40);
  h.assertTrue(count(b.chest("mill"),VillageAstra.FLOUR.get())==4&&count(b.chest("mill"),Items.WHEAT)==0,"Mill turned wheat into flour for the bakery: flour="+count(b.chest("mill"),VillageAstra.FLOUR.get()));
  var route=LogisticsRoutes.choose(b.l,b.e);
  h.assertTrue(route!=null&&route.source().type().equals("mill")&&route.destination().type().equals("restaurant")&&route.item().is(VillageAstra.FLOUR.get()),"Porter route carries flour from mill to bakery: "+route);
  for(int i=0;i<b.chest("mill").getContainerSize();i++)b.chest("mill").setItem(i,ItemStack.EMPTY);
  b.chest("restaurant").setItem(1,new ItemStack(VillageAstra.FLOUR.get(),4));
  for(int i=0;i<4&&count(b.chest("restaurant"),Items.BREAD)<2;i++)run(b,"restaurant",Workshops.wants(b.l,b.e),60);
  h.assertTrue(count(b.chest("restaurant"),Items.BREAD)==2&&count(b.chest("restaurant"),VillageAstra.FLOUR.get())==0,"Two flour and real fuel per bread: bread="+count(b.chest("restaurant"),Items.BREAD));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void workshopEligibilityFollowsProfessionAndEducation(GameTestHelper h){
  var b=bench(h,"carpentry","smithy");var carpenter=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,Profession.CARPENTER,null,-1);var idle=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);
  h.assertTrue(Workshops.eligible(carpenter,b.buildings.get("carpentry"))&&!Workshops.eligible(carpenter,b.buildings.get("smithy")),"Carpenter works only at carpentry");
  h.assertTrue(!Workshops.eligible(idle,b.buildings.get("smithy")),"Unassigned adult is not a blacksmith");
  var educated=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,Profession.BLACKSMITH,null,-1);h.assertTrue(Workshops.eligible(educated,b.buildings.get("smithy")),"Educated blacksmith works at smithy");
  h.assertTrue(Workshops.spec("farm")==null&&Workshops.spec("town_hall").profession()==Profession.BUILDER,"Builder has only simple hall crafting");
  h.succeed();
 }
}
