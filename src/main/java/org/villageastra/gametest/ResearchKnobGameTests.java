package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-123: research knobs that are plain numbers (balance/research_knobs.json and the cartographer core) and the game code that reads them. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResearchKnobGameTests {
 private static void learn(ServerLevel l,SettlementData.Entry e,String... nodes){var record=BookResearch.inspect(l,e);var done=record.getList("legacyDone",Tag.TAG_STRING);for(var n:nodes)done.add(StringTag.valueOf(n));record.put("legacyDone",done);BookResearch.store(l,e,record);ResearchKnobs.forget(e.settlement().id());}
 /** A building site over a pit four blocks deep is refused without Construction I and surveyed with it. */
 @GameTest(template="empty",timeoutTicks=100) public static void foundationFollowsConstruction(GameTestHelper h){
  var l=h.getLevel();var site=h.absolutePos(new BlockPos(4,6,4));var center=h.absolutePos(new BlockPos(40,6,30));
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   for(int x=-1;x<16;x++)for(int z=-1;z<16;z++){for(int y=-6;y<0;y++)l.setBlock(site.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(site.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=20;y++)l.setBlock(site.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   for(int y=-4;y<=-1;y++)l.setBlock(site.offset(3,y,3),Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(ResearchKnobs.foundation(l,e)==3,"Without research the fill is 3");
   var before=BuildingOrders.survey(l,e,"home",0,site);h.assertTrue(!before.ok(),"A 4-deep pit is refused with fill 3: "+before.reason());
   learn(l,e,"construction.1");h.assertTrue(ResearchKnobs.foundation(l,e)==4,"Construction I: fill 4");
   var after=BuildingOrders.survey(l,e,"home",0,site);h.assertTrue(after.ok(),"With fill 4 the site is surveyed: "+after.reason()+" "+after.conflicts());
   learn(l,e,"construction.2","construction.3","construction.4");h.assertTrue(ResearchKnobs.foundation(l,e)==8,"Construction IV: fill 8");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 /** AD-136 (D9, CF14): a bakery 30 blocks from the mill wheel is driven at once: the shaft reaches a fixed 64 in every village, with no
  *  research (the old Mechanics III reach, so an old world loses nothing). */
 @GameTest(template="empty",timeoutTicks=100) public static void driveReachIsFixed(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var mill=new Settlement.Building(Settlement.childId(s.id(),"building/mill"),"mill",8,0,0);s.addBuilding(mill);
  var bakery=new Settlement.Building(Settlement.childId(s.id(),"building/restaurant"),"restaurant",38,0,0);s.addBuilding(bakery);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   for(int x=-2;x<52;x++)for(int z=-2;z<10;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   var from=Drive.postOf(e,mill);var to=Drive.postOf(e,bakery);l.setBlock(from,Blocks.CHAIN.defaultBlockState(),3);l.setBlock(from.below(),Blocks.WATER.defaultBlockState(),3);
   for(int x=Math.min(from.getX(),to.getX());x<=Math.max(from.getX(),to.getX());x++)l.setBlock(new BlockPos(x,from.getY(),from.getZ()),Blocks.CHAIN.defaultBlockState(),3);
   for(int z=Math.min(from.getZ(),to.getZ());z<=Math.max(from.getZ(),to.getZ());z++)l.setBlock(new BlockPos(to.getX(),from.getY(),z),Blocks.CHAIN.defaultBlockState(),3);
   int distance=from.distManhattan(to);h.assertTrue(distance>24&&distance<=32,"The fixture shaft is longer than 24 and at most 32: "+distance);
   h.assertTrue(ResearchKnobs.driveReach(l,e)==64&&Drive.driven(l,e,bakery),"With no research the fixed 64-block shaft drives the bakery "+distance+" blocks away");
   learn(l,e,"engineering.1","engineering.2");h.assertTrue(ResearchKnobs.driveReach(l,e)==64,"and no research changes the reach");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 /** Mining I: a newly claimed quarry working takes the deeper knob; a record planned before keeps its floor. */
 @GameTest(template="empty",timeoutTicks=100) public static void quarryDepthWithMiningOne(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  // The hall stands deep (relative y -40), so the settlement's dig floor (MapOrders.floor) lies far below both quarry floors and never hides the knob.
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,-40,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   h.assertTrue(ResearchKnobs.quarryDepth(l,e)==Quarry.DEPTH&&Quarry.DEPTH==24,"Base quarry depth 24");
   ChunkPos chunk=null;for(var c:Atlas.area(l,e)){l.getChunk(c.x,c.z);if(Quarry.refuses(l,e,c).isEmpty()){chunk=c;break;}}
   h.assertTrue(chunk!=null,"A chunk of the atlas area can be claimed");
   int top=Integer.MIN_VALUE;for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)top=Math.max(top,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,chunk.getMinBlockX()+dx,chunk.getMinBlockZ()+dz)-1);
   h.assertTrue(top-32>Math.max(l.getMinBuildHeight()+1,MapOrders.floor(e)),"Fixture: neither the world bottom nor the dig floor clamps the depth (top "+top+", dig floor "+MapOrders.floor(e)+")");
   h.assertTrue(Quarry.claim(l,e,chunk).isEmpty(),"The chunk is claimed");
   int first=Quarry.record(l,s.id()).getInt("floor");h.assertTrue(first==top-24,"Without Mining I the working goes 24 deep: "+(top-first));
   learn(l,e,"mining.1");h.assertTrue(Quarry.record(l,s.id()).getInt("floor")==first,"A planned record keeps its floor");
   h.assertTrue(ResearchKnobs.quarryDepth(l,e)==32,"Mining I: new workings go 32 deep");
   Quarry.clear(l,s.id());h.assertTrue(Quarry.claim(l,e,chunk).isEmpty(),"The chunk is claimed again");
   h.assertTrue(Quarry.record(l,s.id()).getInt("floor")==top-32,"The new working goes 32 deep: "+(top-Quarry.record(l,s.id()).getInt("floor")));
  }finally{Quarry.clear(l,s.id());SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 /** The cartographer core (AD-112) sets the atlas margin 2/3/4/5/6/8 chunks; without a cartographer house the margin stays 2. */
 @GameTest(template="empty",timeoutTicks=100) public static void surveyMarginFollowsCartographer(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   int[] want={2,3,4,5,6,8};for(int level=1;level<=6;level++)h.assertTrue(Atlas.margin(level)==want[level-1]&&CoreEffects.value("cartographer","survey",level)==want[level-1],"Margin at level "+level+": "+Atlas.margin(level));
   h.assertTrue(Atlas.margin(l,e)==Atlas.MARGIN&&Atlas.area(l,e).equals(Atlas.area(e)),"Without a cartographer house the area keeps the base margin");
   var hall=new ChunkPos(center);var wide=Atlas.area(e,8);
   h.assertTrue(wide.size()>Atlas.area(e).size()&&wide.contains(new ChunkPos(hall.x+8,hall.z))&&!Atlas.area(e).contains(new ChunkPos(hall.x+8,hall.z)),"A wider margin reaches farther chunks");
  }finally{Atlas.forgetMargins();SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 /** Agriculture I: a crop just planted is fed with bone meal from the farm chest — never without the research, never twice for one planting. */
 @GameTest(template="empty",timeoutTicks=100) public static void boneMealFeedsOnlyWithAgriculture(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   var chest=h.absolutePos(new BlockPos(2,2,2));var crop=h.absolutePos(new BlockPos(4,2,2));
   l.setBlock(chest,Blocks.CHEST.defaultBlockState(),3);l.setBlock(crop.below(),Blocks.FARMLAND.defaultBlockState(),3);l.setBlock(crop,Blocks.WHEAT.defaultBlockState(),3);
   var box=(net.minecraft.world.Container)l.getBlockEntity(chest);box.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL,3));
   var age=net.minecraft.world.level.block.CropBlock.AGE;
   h.assertTrue(ResearchKnobs.BONE_MEAL_ACTIVE&&ResearchKnobs.boneMeal(l,e)==0,"Without Agriculture I no bone meal is due");
   h.assertTrue(ResearchKnobs.feedCrop(l,e,chest,crop,UUID.randomUUID())==0&&box.countItem(net.minecraft.world.item.Items.BONE_MEAL)==3&&l.getBlockState(crop).getValue(age)==0,"Without the research the chest keeps its bone meal and the crop does not grow");
   learn(l,e,"agriculture.1");h.assertTrue(ResearchKnobs.boneMeal(l,e)==1,"Agriculture I: one bone meal a planting");
   var op=UUID.randomUUID();
   h.assertTrue(ResearchKnobs.feedCrop(l,e,chest,crop,op)==1&&box.countItem(net.minecraft.world.item.Items.BONE_MEAL)==2&&l.getBlockState(crop).getValue(age)>0,"With the research one bone meal leaves the farm chest and the crop grows");
   h.assertTrue(ResearchKnobs.feedCrop(l,e,chest,crop,op)==0&&box.countItem(net.minecraft.world.item.Items.BONE_MEAL)==2,"The same planting replayed never pays twice");
   box.clearContent();l.setBlock(crop,Blocks.WHEAT.defaultBlockState(),3);
   h.assertTrue(ResearchKnobs.feedCrop(l,e,chest,crop,UUID.randomUUID())==0&&l.getBlockState(crop).getValue(age)==0,"Without bone meal in the farm chest nothing grows");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
