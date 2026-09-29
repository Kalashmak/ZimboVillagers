package org.villageastra.gametest;
import java.util.*;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-112 (G5): the knobs a core turns. Each test raises one building through levels I..VI with its whole design and core standing and
 *  reads the knob from the world; level I keeps the number the game had before cores. everyActiveEffectMatchesItsKnob holds
 *  balance/core_levels.json to the live functions and to the tests it names. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CoreEffectGameTests {
 private record Shop(ServerLevel l,SettlementData.Entry e,Settlement.Building shop){}
 /** A hall with its chest and one building of this type standing on its lot at level I (as in BuildingCoreGameTests). */
 private static Shop shop(GameTestHelper h,String type){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var shop=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,10,0,0);s.addBuilding(shop);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true));
  for(int x=-2;x<26;x++)for(int z=-2;z<14;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=0;y<16;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  lay(l,e,shop,type);
  return new Shop(l,e,shop);
 }
 private static void lay(ServerLevel l,SettlementData.Entry e,Settlement.Building b,String design){for(var cell:BuildingPlacement.layout(design,BuildingPlacement.origin(e,b),b.rotation()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);}
 private static Settlement.Building kept(Shop t){return t.e.settlement().buildings().stream().filter(b->b.id().equals(t.shop.id())).findFirst().orElseThrow();}
 /** Raises the building to a kept level with that level's whole design (its core or ring included) standing, and forgets best(). */
 private static Settlement.Building raise(Shop t,int level){
  for(int n=kept(t).level()+1;n<=level;n++)t.e.settlement().raiseBuildingLevel(t.shop.id(),n);var b=kept(t);
  if(level>1)lay(t.l,t.e,b,BuildingTiers.layoutId(b.type(),level));BuildingLevels.forgetBest(t.e.settlement().id());return b;
 }
 private static void done(Shop t){SettlementData.get(t.l.getServer()).remove(t.e.settlement().id());BuildingLevels.forgetBest(t.e.settlement().id());}
 private interface Knob{int at(Shop t,Settlement.Building b);}
 /** Walks one building through I..VI: at each level it must work at that level and the knob must read the table's number. */
 private static void follows(GameTestHelper h,String type,String effect,Knob knob){
  var t=shop(h,type);
  try{for(int level=1;level<=CoreEffects.LEVELS;level++){var b=raise(t,level);
   h.assertTrue(BuildingLevels.level(t.l,t.e,b)==level,type+" raised to "+level+" works at "+BuildingLevels.level(t.l,t.e,b));
   int want=CoreEffects.value(type,effect,level),got=knob.at(t,b);
   h.assertTrue(got==want,type+"/"+effect+" at level "+level+" is "+got+", the table says "+want);}}
  finally{done(t);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theHerdCapFollowsThePastureLevel(GameTestHelper h){
  h.assertTrue(LivestockGoal.max(1)==LivestockGoal.MAX,"Level I keeps the old herd of "+LivestockGoal.MAX);
  follows(h,"livestock","herd",(t,b)->LivestockGoal.max(t.l,t.e,b));
  h.assertTrue(LivestockGoal.max(6)>LivestockGoal.max(1),"A full pen keeps a larger herd");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=600) public static void workshopLabourFollowsTheLevel(GameTestHelper h){
  for(var type:List.of("smithy","carpentry","masonry","mill","restaurant","clinic"))follows(h,type,"labour",(t,b)->BuildingLevels.labor(t.l,t.e,b));
  h.assertTrue(CoreEffects.value("smithy","labour",1)==20,"Level I works 20 a turn as before cores");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theClinicReserveFollowsItsLevel(GameTestHelper h){
  follows(h,"clinic","reserve",(t,b)->Workshops.reserve(t.l,t.e,b));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theDoctorRadiusFollowsTheClinicLevel(GameTestHelper h){
  h.assertTrue(DoctorGoal.radius(1)==DoctorGoal.RADIUS,"Level I keeps the old radius of "+DoctorGoal.RADIUS);
  follows(h,"clinic","radius",(t,b)->DoctorGoal.radius(t.l,t.e,b));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theGuardRadiusAndSightFollowThePostLevel(GameTestHelper h){
  h.assertTrue(GuardGoal.defendRadius(1)==GuardGoal.DEFEND_RADIUS&&GuardGoal.sight("guard_house",1)==GuardGoal.SIGHT,"Level I keeps the old watch");
  follows(h,"guard_house","defend",(t,b)->GuardGoal.defendRadius(t.l,t.e,b));
  follows(h,"guard_house","sight",(t,b)->GuardGoal.sight(t.l,t.e,b));
  h.assertTrue(GuardGoal.sight(Walls.TOWER,1)==GuardGoal.SIGHT&&GuardGoal.rangedMaxSq(Walls.TOWER,1)==GuardGoal.RANGED_MAX_SQ&&GuardGoal.defendRadius(Walls.TOWER,6)==GuardGoal.DEFEND_RADIUS,"A wall tower has no core; before Defence II it keeps the AD-094 watch");
  h.assertTrue(GuardGoal.rangedMaxSq(Walls.TOWER,2)==24*24&&GuardGoal.rangedMaxSq(Walls.TOWER,4)==48*48&&GuardGoal.rangedMaxSq(Walls.TOWER,5)==64*64&&GuardGoal.sight(Walls.TOWER,6)==64,"AD-157: its reach follows the Defence - 24 at II, 48 at IV, 64 from V");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theArcherReachAndSightFollowTheRangeLevel(GameTestHelper h){
  h.assertTrue(GuardGoal.rangedMaxSq("archery",1)==GuardGoal.RANGED_MAX_SQ&&GuardGoal.sight("archery",1)==GuardGoal.SIGHT,"Level I keeps the old reach and sight");
  follows(h,"archery","reach",(t,b)->(int)Math.round(Math.sqrt(GuardGoal.rangedMaxSq(t.l,t.e,b))));
  follows(h,"archery","sight",(t,b)->GuardGoal.sight(t.l,t.e,b));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void drillAndLessonCreditFollowTheBestLevel(GameTestHelper h){
  h.assertTrue(Population.drillCredit(1)==20&&Population.lessonCredit(1)==20,"Level I keeps the old credit of 20");
  follows(h,"barracks","drill",(t,b)->Population.drillCredit(t.l,t.e));
  follows(h,"school","lesson",(t,b)->Population.lessonCredit(t.l,t.e));
  var t=shop(h,"mill");try{h.assertTrue(Population.drillCredit(t.l,t.e)==20&&Population.lessonCredit(t.l,t.e)==20,"A village without a barracks or a school gets the level-I credit");}finally{done(t);}
  h.succeed();
 }
 /** AD-136 (CF9): a laboratory's level no longer speeds a desk up (a work takes the same village-clock time); it seats more scientists. */
 @GameTest(template="empty",timeoutTicks=300) public static void labSeatsFollowTheBestLevel(GameTestHelper h){
  h.assertTrue(org.villageastra.domain.ScienceBalance.seats(1)==1&&org.villageastra.domain.ScienceBalance.seats(6)==6,"Levels I..VI seat 1..6 scientists");
  follows(h,"laboratory","seats",(t,b)->ScienceWorks.places(t.l,t.e));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theExpeditionRangeFollowsTheBestLevel(GameTestHelper h){
  h.assertTrue(Expeditions.range(1)==(Expeditions.MIN_RANGE+Expeditions.MAX_RANGE)/2,"Level I keeps the old range");
  follows(h,"expedition","range",(t,b)->Expeditions.range(t.l,t.e));
  // QUEST-002: how far out along a road this village's word of a place reaches (Far), which is not how far its people walk.
  follows(h,"expedition","far_reach",(t,b)->Far.reach(t.l,t.e));
  // The expeditioner really walks that far: sector 0 lies due east of the centre at the range.
  follows(h,"expedition","range",(t,b)->Expeditions.target(t.l,t.e,0).getX()-t.e.center().getX());
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void thePorterLoadFollowsTheWarehouseLevel(GameTestHelper h){
  h.assertTrue(LogisticsRoutes.load(1)==LogisticsRoutes.LOAD&&LogisticsRoutes.MAX_LOAD==LogisticsRoutes.load(CoreEffects.LEVELS),"Level I keeps the old parcel; the largest is the level-VI one");
  h.assertTrue(LogisticsRoutes.MAX_LOAD<=64,"A parcel never exceeds a stack: "+LogisticsRoutes.MAX_LOAD);
  follows(h,"warehouse","load",(t,b)->LogisticsRoutes.load(t.l,t.e,b));
  var t=shop(h,"warehouse");try{raise(t,6);h.assertTrue(LogisticsRoutes.load(t.l,t.e,Workshops.hall(t.e))==LogisticsRoutes.LOAD&&LogisticsRoutes.load(t.l,t.e,null)==LogisticsRoutes.LOAD,"A porter of the hall carries the level-I parcel");}finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theCaravanCarryFollowsTheBestLevel(GameTestHelper h){
  h.assertTrue(Caravans.carry(1)==Caravans.CARRY,"Level I keeps the old load of "+Caravans.CARRY);
  follows(h,"caravan","carry",(t,b)->Caravans.carry(t.l,t.e));
  var t=shop(h,"mill");try{h.assertTrue(Caravans.carry(t.l,t.e)==Caravans.CARRY&&Caravans.carry(t.l,null)==Caravans.CARRY,"A village without a caravan yard carries the level-I load");}finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void terraceCutAndFillFollowTheBestLevel(GameTestHelper h){
  h.assertTrue(Terraces.cut(1)==Terraces.CUT_HEIGHT&&Terraces.fill(1)==Terraces.FILL_DEPTH,"Level I keeps the old cut and fill");
  Knob engineer=(t,b)->{var s=t.e.settlement();if(Terraces.engineer(t.e)==null){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,s.homes().iterator().next().id());s.assign(r.id(),Profession.ENGINEER,b.id());}return Terraces.level(t.l,t.e);};
  follows(h,"engineering","cut",(t,b)->Terraces.cut(engineer.at(t,b)));
  follows(h,"engineering","fill",(t,b)->Terraces.fill(engineer.at(t,b)));
  var t=shop(h,"engineering");try{raise(t,6);h.assertTrue(Terraces.level(t.l,t.e)==1,"Without an engineer no office sets the earthworks");}finally{done(t);}
  h.succeed();
 }
 /** The live static function of every active effect, level by level. An effect missing here is a defect: the table promises a number no code turns. */
 private static Map<String,IntUnaryOperator> knobs(){
  int bonus=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(CoreEffectGameTests.class.getResourceAsStream("/data/villageastra/balance/levels.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().get("labor_bonus").getAsInt();
  IntUnaryOperator labour=level->level<=1?20:20*(bonus+level-2);var m=new HashMap<String,IntUnaryOperator>();
  m.put("farm/field",level->FarmField.modules(level,false).size());m.put("farm/feeds",FarmField::feeds);
  // AD-130: the floors of the barn the fields stand on, and the fields the level-VI machine works by itself.
  m.put("farm/floors",level->FarmField.floors(FarmField.modules(level)).size());m.put("farm/machine_fields",level->org.villageastra.world.Machines.automaticFields(level));
  m.put("livestock/herd",LivestockGoal::max);m.put("livestock/pens",LivestockPens::pens);m.put("forester/felling",ResourceWorkGoal::fellingLabor);
  // AD-131: the hut's reach, the saw's planks a log and the courtyard grove's places.
  m.put("forester/radius",ForestBalance::radius);m.put("forester/planks",ForestryMachines::planksPerLog);m.put("forester/grove",ForestryMachines::groveCells);m.put("mine/floor",MineDrive::floorY);
  // AD-139: the restaurant's core type is the bakery's (CoreCatalog.coreType).
  for(var type:List.of("smithy","carpentry","masonry","mill","bakery","clinic","town_hall"))m.put(type+"/labour",labour);
  m.put("clinic/radius",DoctorGoal::radius);m.put("guard_house/defend",GuardGoal::defendRadius);m.put("guard_house/sight",level->GuardGoal.sight("guard_house",level));
  m.put("archery/reach",level->(int)Math.round(Math.sqrt(GuardGoal.rangedMaxSq("archery",level))));m.put("archery/sight",level->GuardGoal.sight("archery",level));
  m.put("barracks/drill",Population::drillCredit);m.put("school/lesson",Population::lessonCredit);m.put("school/pupils",Population::pupils);m.put("clinic/beds",Medicine::beds);m.put("school/cadets",Population::cadets);m.put("laboratory/seats",org.villageastra.domain.ScienceBalance::seats);
  m.put("expedition/range",Expeditions::range);m.put("expedition/far_reach",Far::reach);m.put("warehouse/load",LogisticsRoutes::load);
  // AD-147: the store's slots (its plan's pages), the stacks of a courier's cart, the couriers the labour office posts.
  m.put("warehouse/storage",WarehouseStore::slots);m.put("warehouse/cart_stacks",WarehouseTrips::cartStacks);m.put("warehouse/couriers",level->Staff.slots(WarehouseStore.TYPE,level));m.put("caravan/carry",Caravans::carry);
  m.put("bakery/seats",Dining::designSeats);m.put("bakery/couriers",Couriers::posts);m.put("engineering/cut",Terraces::cut);m.put("engineering/fill",Terraces::fill);
  m.put("cartographer/survey",level->Atlas.margin(level));
  for(var step:new String[]{"lighting","scouts","patrol","auto"})m.put("cartographer/"+step,level->org.villageastra.world.CartographyLadder.on(step,level));
  m.put("caravan/remote",level->org.villageastra.world.TradeLadder.on("remote",level));
  return m;
 }
 /** Read from the world only (a chest's stock): held by its own test, not by a static function. */
 private static final Set<String> WORLD_ONLY=Set.of("clinic/reserve");
 @GameTest(template="empty",timeoutTicks=100) public static void everyActiveEffectMatchesItsKnob(GameTestHelper h){
  var knobs=knobs();int checked=0;
  for(var type:CoreCatalog.TYPES)for(var effect:CoreEffects.effects(type)){var key=type+"/"+effect.id();
   if(!effect.active()){h.assertTrue(effect.test()==null,key+" is not active and names no test");continue;}
   var test=effect.test();h.assertTrue(test!=null&&test.contains("#"),key+" is active and must name its test: "+test);
   var cls=test.substring(0,test.indexOf('#'));var name=test.substring(test.indexOf('#')+1);java.lang.reflect.Method method;
   try{method=Class.forName(cls).getMethod(name,GameTestHelper.class);}catch(ReflectiveOperationException ex){throw new GameTestAssertException(key+" names a test that does not exist: "+test);}
   h.assertTrue(method.isAnnotationPresent(GameTest.class)&&java.lang.reflect.Modifier.isStatic(method.getModifiers()),key+" names a method that is not a GameTest: "+test);
   if(WORLD_ONLY.contains(key)){checked++;continue;}
   var knob=knobs.get(key);h.assertTrue(knob!=null,key+" is active but no knob of the game reads it");
   // A level never takes back what the one before gave (the mine's floor goes down, every other number up).
   int way=key.equals("mine/floor")?-1:1;
   for(int level=1;level<=CoreEffects.LEVELS;level++){int want=effect.at(level),got=knob.applyAsInt(level);h.assertTrue(got==want,key+" at level "+level+": the game gives "+got+", the table says "+want);
    if(level>1)h.assertTrue(way*effect.at(level)>=way*effect.at(level-1),key+" gets worse at level "+level);}
   checked++;}
  h.assertTrue(checked>=25,"Every active effect was held to its knob: "+checked);
  h.succeed();
 }
}
