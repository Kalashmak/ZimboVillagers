package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** A07-09/AD-054: every profession of the settlement is really executed by exactly one worker routine, and nothing in that matrix is free. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ProfessionMatrixGameTests {
 /** The declared matrix: which routine does the work of each profession. A workshop profession is served by the station, everybody else by their own goal. */
 private static final Map<Profession,String> EXECUTOR=Map.ofEntries(
  Map.entry(Profession.MAYOR,"MayorSiteGoal"),
  Map.entry(Profession.GUARD,"GuardGoal"),
  Map.entry(Profession.ARCHER_GUARD,"GuardGoal"),
  Map.entry(Profession.FORESTER,"ResourceWorkGoal"),
  Map.entry(Profession.MINER,"QuarryGoal"),
  Map.entry(Profession.FARMER,"ResourceWorkGoal"),
  Map.entry(Profession.TEACHER,"SchoolGoal"),
  Map.entry(Profession.CARTOGRAPHER,"CartographerGoal"),
  Map.entry(Profession.BLACKSMITH,"WorkshopGoal"),
  Map.entry(Profession.SCIENTIST,"ResearchGoal"),
  Map.entry(Profession.BUILDER,"HallUpgradeGoal"),
  Map.entry(Profession.EXPEDITIONER,"ExpeditionGoal"),
  Map.entry(Profession.PORTER,"PorterGoal"),
  Map.entry(Profession.SOLDIER,"SoldierGoal"),
  Map.entry(Profession.LIVESTOCK_FARMER,"LivestockGoal"),
  Map.entry(Profession.MILLER,"WorkshopGoal"),
  Map.entry(Profession.BAKER,"WorkshopGoal"),
  Map.entry(Profession.MASON,"WorkshopGoal"),
  Map.entry(Profession.CARPENTER,"WorkshopGoal"),
  Map.entry(Profession.CARAVANEER,"CaravanGoal"),
  Map.entry(Profession.DOCTOR,"DoctorGoal"),
  Map.entry(Profession.ENGINEER,"RoadWorkGoal"));
 @GameTest(template="empty",timeoutTicks=200) public static void everyProfessionHasExactlyOneExecutor(GameTestHelper h){
  var l=h.getLevel();
  var npc=VillageAstra.RESIDENT.get().create(l);npc.setNoAi(true);
  npc.moveTo(h.absolutePos(new BlockPos(2,2,2)).getX()+.5,h.absolutePos(new BlockPos(2,2,2)).getY(),h.absolutePos(new BlockPos(2,2,2)).getZ()+.5,0,0);
  l.addFreshEntity(npc);
  var goals=new HashSet<>(npc.goalNames());
  h.assertTrue(EXECUTOR.size()==Profession.values().length,"The matrix covers every profession: "+EXECUTOR.size()+" of "+Profession.values().length);
  var missing=new TreeSet<String>();
  for(var profession:Profession.values()){
   var executor=EXECUTOR.get(profession);
   if(executor==null||!goals.contains(executor))missing.add(profession.id()+"->"+executor);
   // A workshop profession must have its station recipe; a profession without a station must not have one, so no job has two executors.
   boolean station=Workshops.spec(profession.workplace())!=null;
   boolean expected=executor.equals("WorkshopGoal")||profession==Profession.DOCTOR||profession==Profession.MAYOR||profession==Profession.BUILDER;
   if(station!=expected)missing.add(profession.id()+" station="+station);
  }
  h.assertTrue(missing.isEmpty(),"Every profession is executed exactly once: "+missing);
  npc.discard();h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theMatrixHasNoFreeCitizensBooksOrSupplies(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));
  var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var school=new Settlement.Building(Settlement.childId(s.id(),"building/school"),"school",8,0,0);s.addBuilding(school);
  for(int x=-2;x<40;x++)for(int z=-2;z<22;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=0;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  // No free citizen: a resident without a usable home is not admitted.
  var homeless=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);
  boolean refused=false;
  try{s.admit(homeless,Settlement.childId(s.id(),"home/none"));}catch(RuntimeException ex){refused=true;}
  h.assertTrue(refused,"A settlement admits nobody without a real home");
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),2,2,true);s.addHome(home);
  var uneducated=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(uneducated,home.id());
  // No free book: professions that need schooling refuse an uneducated resident.
  var needsSchool=new TreeSet<String>();
  for(var profession:Profession.values()){
   if(!profession.educationRequired())continue;
   boolean threw=false;
   try{s.assign(uneducated.id(),profession,school.id());}catch(RuntimeException ex){threw=true;}
   if(!threw)needsSchool.add(profession.id());
  }
  h.assertTrue(needsSchool.isEmpty(),"Schooling is never free: "+needsSchool);
  // No free supplies: a station with an empty chest never produces anything, whatever the settlement wants.
  var free=new TreeSet<String>();
  int offset=0;
  for(var spec:Workshops.specs()){
   // Each station gets its own spot: a settlement keeps its buildings, so the test does not take them back.
   var building=new Settlement.Building(Settlement.childId(s.id(),"building/"+spec.building()+"-matrix"),spec.building(),16+(offset%4)*6,0,(offset/4)*6);
   offset++;s.addBuilding(building);
   var stationChest=LogisticsRoutes.position(e,building);
   l.setBlock(stationChest.below(),Blocks.COBBLESTONE.defaultBlockState(),3);
   l.setBlock(stationChest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
   var chest=LogisticsRoutes.chest(l,e,building);
   if(chest==null){free.add(spec.building()+"/no_chest");continue;}
   chest.clearContent();
   for(int turn=0;turn<6;turn++)Workshops.advance(l,e,building,turn*40L,Workshops.wants(l,e));
   int produced=0;for(int slot=0;slot<chest.getContainerSize();slot++)produced+=chest.getItem(slot).getCount();
   if(produced>0)free.add(spec.building()+"/produced_"+produced);
  }
  h.assertTrue(free.isEmpty(),"No station makes something out of nothing: "+free);
  SettlementData.get(l.getServer()).remove(s.id());h.succeed();
 }
}
