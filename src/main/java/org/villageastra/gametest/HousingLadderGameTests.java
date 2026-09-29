package org.villageastra.gametest;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-123: the owner's housing ladder — Housing II opens the big house, Housing III shortens births, a house holds more people only when a
 *  finished project really placed the beds (level_beds), capacity never drops, and the third and fourth resident find their own beds. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HousingLadderGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building house,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var house=new Settlement.Building(Settlement.childId(s.id(),"building/house"),"home",20,0,0);s.addBuilding(house);
  for(int x=-2;x<32;x++)for(int z=-2;z<12;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<10;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,s,e,house,center);
 }
 private static void learn(Town t,String... nodes){var record=BookResearch.inspect(t.l,t.e);var done=record.getList("legacyDone",Tag.TAG_STRING);for(var n:nodes)done.add(StringTag.valueOf(n));record.put("legacyDone",done);BookResearch.store(t.l,t.e,record);ResearchKnobs.forget(t.s.id());}
 private static void done(Town t){HousingLadder.levelBedsOverride=null;SettlementData.get(t.l.getServer()).remove(t.s.id());}
 /** The standing house of its first design, block by block. */
 private static void build(Town t){for(var cell:BuildingPlacement.layout(t.e,t.house,"home").entrySet())t.l.setBlock(cell.getKey(),cell.getValue(),2);}
 /** A whole bed: foot at local (x,1,z), head one step toward {@code facing}. */
 private static void bed(Town t,int x,int z,Direction facing){var foot=BuildingPlacement.at(t.e,t.house,x,1,z);
  t.l.setBlock(foot,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,facing).setValue(BedBlock.PART,BedPart.FOOT),2);
  t.l.setBlock(foot.relative(facing),Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,facing).setValue(BedBlock.PART,BedPart.HEAD),2);}
 /** The two extra beds of the handed-over cell plan (H6): feet at (2,5) and (4,5), heads outward. */
 private static void extraBeds(Town t){bed(t,2,5,Direction.WEST);bed(t,4,5,Direction.EAST);}
 private static Settlement.Home home(Town t,int capacity){var h=new Settlement.Home(t.house.id(),1,capacity,true);t.s.addHome(h);return h;}
 private static int capacity(Town t){return t.s.homes().stream().filter(x->x.id().equals(t.house.id())).findFirst().orElseThrow().capacity();}
 @GameTest(template="empty",timeoutTicks=100) public static void homeTwoNeedsHousingTwo(GameTestHelper h){
  var t=town(h);
  try{
   h.assertTrue(ResearchGate.forDesign("home_2").equals(List.of("housing.2"))&&ResearchGate.forDesign("home").isEmpty(),"The big house takes Housing II, the standard house nothing");
   h.assertTrue(ResearchGate.designRefusal(t.l,t.e,"home_2").equals("research"),"Without Housing II the big house is refused");
   learn(t,"housing.1","housing.2");h.assertTrue(ResearchGate.designRefusal(t.l,t.e,"home_2").isEmpty(),"With Housing II it may be ordered");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void upgradeRaisesCapacityOnlyWithBeds(GameTestHelper h){
  var t=town(h);
  try{
   build(t);home(t,2);
   HousingLadder.levelBedsOverride=false;HousingLadder.upgraded(t.l,t.e,t.house,3);h.assertTrue(capacity(t)==2,"Flag off: an upgrade never raises capacity");
   HousingLadder.levelBedsOverride=true;HousingLadder.upgraded(t.l,t.e,t.house,3);h.assertTrue(capacity(t)==2,"Flag on, only two beds stand: capacity stays 2");
   extraBeds(t);h.assertTrue(HousingLadder.beds(t.l,t.e,t.house,BedPart.FOOT).size()==4,"Four whole beds stand in the footprint");
   HousingLadder.upgraded(t.l,t.e,t.house,2);h.assertTrue(capacity(t)==2,"Level II of the ladder is still 2 residents");
   HousingLadder.upgraded(t.l,t.e,t.house,3);h.assertTrue(capacity(t)==HousingLadder.capacity("home",3)&&capacity(t)==4,"Level III with four beds: 4 residents");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void capacityNeverDrops(GameTestHelper h){
  var t=town(h);
  try{
   build(t);home(t,4);HousingLadder.levelBedsOverride=true;
   HousingLadder.upgraded(t.l,t.e,t.house,3);h.assertTrue(capacity(t)==4,"Two beds standing never lower a capacity of 4");
   t.s.upgradeHome(t.house.id(),1);h.assertTrue(capacity(t)==4,"upgradeHome only raises");
  }finally{done(t);}
  h.succeed();
 }
 /** H3: a house is damaged for its beds only when fewer whole beds stand than the people it holds. */
 @GameTest(template="empty",timeoutTicks=100) public static void missingExtraBedIsNotDamage(GameTestHelper h){
  var t=town(h);
  try{
   build(t);var base=BuildingPlacement.origin(t.e,t.house);
   h.assertTrue(BuildingIntegrity.home(t.l,base,"home",0,2)==BuildingIntegrity.Result.USABLE,"The built house holds its two");
   var foot=HousingLadder.beds(t.l,t.e,t.house,BedPart.FOOT).get(0);var state=t.l.getBlockState(foot);
   t.l.setBlock(foot.relative(state.getValue(BedBlock.FACING)),Blocks.AIR.defaultBlockState(),2);t.l.setBlock(foot,Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(BuildingIntegrity.home(t.l,base,"home",0,1)==BuildingIntegrity.Result.USABLE,"A house holding one keeps its one bed usable");
   h.assertTrue(BuildingIntegrity.home(t.l,base,"home",0,2)==BuildingIntegrity.Result.DAMAGED,"A house holding two with one bed is damaged");
   h.assertTrue(BuildingIntegrity.home(t.l,base,"home",0)==BuildingIntegrity.Result.DAMAGED,"The exact design check still sees the missing bed");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void birthsFasterWithHousingThree(GameTestHelper h){
  var t=town(h);
  try{
   h.assertTrue(Population.BIRTH_INTERVAL==72000&&HousingLadder.birthInterval(t.l,t.e)==72000,"Base interval 72 000 active ticks");
   learn(t,"housing.1","housing.2","housing.3");h.assertTrue(HousingLadder.birthInterval(t.l,t.e)==54000,"Housing III: 54 000");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void mayorFallsBackToStandardHome(GameTestHelper h){
  var t=town(h);
  try{
   t.s.civilization().completedHallUpgrade(2);
   h.assertTrue("home_2".equals(MayorPlanner.need(t.s)),"The research-blind need asks for the big house at civilization II");
   h.assertTrue("home".equals(MayorPlanner.need(t.l,t.e)),"Without Housing II the NPC mayor builds the standard house");
   learn(t,"housing.1","housing.2");h.assertTrue("home_2".equals(MayorPlanner.need(t.l,t.e)),"With Housing II the big house");
  }finally{done(t);}
  h.succeed();
 }
 /** C8: while level_beds is off only the flag-off invariants are asserted and the per-layout gap is logged; with it on the targets must hold. */
 @GameTest(template="empty",timeoutTicks=100) public static void layoutsMatchBedTargets(GameTestHelper h){
  var gaps=new ArrayList<String>();
  for(var type:List.of("home","home_2"))for(int level=1;level<=BuildingTiers.MAX;level++){
   int beds=BuildingOrders.capacity(BuildingTiers.layoutId(type,level)),want=HousingLadder.capacity(type,level);
   if(beds!=want)gaps.add(BuildingTiers.layoutId(type,level)+" beds "+beds+" target "+want);
   if(level>1)h.assertTrue(want>=HousingLadder.capacity(type,level-1),"The ladder never drops: "+type+" "+level);}
  h.assertTrue(HousingLadder.capacity("home",1)==BuildingOrders.capacity("home")&&HousingLadder.capacity("home_2",1)==BuildingOrders.capacity("home_2"),"Level I of the ladder is the built design");
  h.assertTrue(HousingLadder.capacity("home",2)==2&&HousingLadder.capacity("home",3)==4,"Owner ladder: I and II of the standard house");
  if(HousingLadder.levelBeds())h.assertTrue(gaps.isEmpty(),"level_beds is on, so every level design has its beds: "+gaps);
  else LogUtils.getLogger().info("ASTRA_HOUSING_LADDER level_beds=false; layouts still short of the ladder: {}",gaps);
  h.succeed();
 }
 /** C6: the third and fourth resident sleep in the beds really standing in the house, not only in the two of its first design. */
 @GameTest(template="empty",timeoutTicks=100) public static void fourthResidentFindsABed(GameTestHelper h){
  var t=town(h);
  try{
   build(t);extraBeds(t);home(t,2);t.s.upgradeHome(t.house.id(),4);
   var beds=new HashSet<BlockPos>();
   for(int i=0;i<4;i++){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,t.house.id());}
   for(var r:t.s.residents()){var bed=SleepGoal.bed(t.l,t.e,r);h.assertTrue(bed!=null&&t.l.getBlockState(bed).getBlock() instanceof BedBlock,"Every resident has a bed");beds.add(bed);}
   h.assertTrue(beds.size()==4,"Four residents, four different beds: "+beds.size());
  }finally{done(t);}
  h.succeed();
 }
 /** OWNER-HOUSING-BEDS: a house built to its level design really holds the ladder — the standard house III four residents on its ground floor,
  *  the big house VI ten (six of them upstairs) — and every resident finds a bed of their own. */
 @GameTest(template="empty",timeoutTicks=100) public static void builtLevelsHoldTheLadder(GameTestHelper h){
  var t=town(h);
  try{
   HousingLadder.levelBedsOverride=true;
   var big=new Settlement.Building(Settlement.childId(t.s.id(),"building/big"),"home_2",4,0,0);t.s.addBuilding(big);
   for(var c:List.of(new Object[]{t.house,"home",3},new Object[]{big,"home_2",6})){var b=(Settlement.Building)c[0];var type=(String)c[1];int level=(Integer)c[2];
    for(var cell:BuildingPlacement.layout(t.e,b,BuildingTiers.layoutId(type,level)).entrySet())t.l.setBlock(cell.getKey(),cell.getValue(),2);
    t.s.addHome(new Settlement.Home(b.id(),1,HousingLadder.capacity(type,1),true));
    int want=HousingLadder.capacity(type,level);
    h.assertTrue(HousingLadder.beds(t.l,t.e,b,BedPart.FOOT).size()==want,type+" "+level+": "+want+" whole beds stand");
    h.assertTrue(HousingLadder.upgraded(t.l,t.e,b,level)==want,type+" "+level+" holds "+want+" after its project");
    for(int i=0;i<want;i++)t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1),b.id());
    var beds=new HashSet<BlockPos>();
    for(var r:t.s.residents())if(b.id().equals(r.home())){var bed=SleepGoal.bed(t.l,t.e,r);h.assertTrue(bed!=null&&t.l.getBlockState(bed).getBlock() instanceof BedBlock,"Every resident of "+type+" has a bed");beds.add(bed);}
    h.assertTrue(beds.size()==want,type+" "+level+": "+want+" residents, "+want+" different beds: "+beds.size());}
  }finally{done(t);}
  h.succeed();
 }
 /** OWNER-HOUSING-BEDS: a level bed is entered from a free cell behind its foot, and never shares a cell with the equipment of any level. */
 @GameTest(template="empty",timeoutTicks=100) public static void levelBedsKeepTheirWayIn(GameTestHelper h){
  for(var type:List.of("home","home_2")){var equipment=new HashSet<BlockPos>();for(var p:LevelArchitecture.equipment(type))equipment.add(p.local());
   var top=BuildingBlueprints.layout(BuildingTiers.layoutId(type,BuildingTiers.MAX),BlockPos.ZERO);
   for(var way:HousingLadder.wayUp(type))h.assertTrue(!equipment.contains(way)&&top.getOrDefault(way,Blocks.AIR.defaultBlockState()).isAir(),type+": the way up is open at "+way);
   for(var bed:HousingLadder.levelBedCells(type)){
    h.assertTrue(!equipment.contains(bed.foot())&&!equipment.contains(bed.head())&&!equipment.contains(bed.approach()),type+": no equipment on the bed at "+bed.foot());
    var way=top.getOrDefault(bed.approach(),Blocks.AIR.defaultBlockState());var over=top.getOrDefault(bed.approach().above(),Blocks.AIR.defaultBlockState());
    var under=top.getOrDefault(bed.approach().below(),Blocks.AIR.defaultBlockState());
    h.assertTrue(way.isAir()&&over.isAir()&&under.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO),type+": the way into the bed at "+bed.foot()+" is open");}}
  h.succeed();
 }
}
