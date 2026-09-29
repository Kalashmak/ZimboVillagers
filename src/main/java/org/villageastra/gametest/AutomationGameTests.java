package org.villageastra.gametest;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-136 (D9, spec 3.1, 6.2, CF14): there is no shared mechanics branch any more. A building's machine is the top of its own ladder
 *  (balance/automation.json): the mine digs by itself from its level V (Mining V), a workshop turns without a worker only at its level VI,
 *  never at IV as before; the drive shaft of the mill wheel reaches a fixed 64 blocks in every village. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class AutomationGameTests {
 @GameTest(template="empty",timeoutTicks=300) public static void theMineDigsByItselfFromItsLevelFive(GameTestHelper h){
  var t=town(h,"mine");
  try{h.assertTrue(Automation.level("mine",false)==5&&Automation.grant("mine").dig(),"The table: the mine digs from V");
   var four=raise(t,4);h.assertTrue(BuildingLevels.level(t.l,t.e,four)==4&&!Automation.at(t.l,t.e,four).dig(),"A mine at IV still needs its miners");
   var five=raise(t,5);h.assertTrue(BuildingLevels.level(t.l,t.e,five)==5&&Automation.at(t.l,t.e,five).dig(),"At V it digs itself, no mechanics research asked");
   h.assertTrue(BuildingTiers.research("mine",5).contains("mining.5"),"Level V itself is Mining V");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aWorkshopAtFourNoLongerWorksWithoutHands(GameTestHelper h){
  var t=town(h,"smithy");
  try{var four=raise(t,4);
   h.assertTrue(BuildingLevels.level(t.l,t.e,four)==4&&!Machines.mechanized(t.l,t.e,four),"A smithy at IV needs its smith (the old Mechanics IV rule is gone)");
   var six=raise(t,6);
   h.assertTrue(BuildingLevels.level(t.l,t.e,six)==6&&Automation.at(t.l,t.e,six).bench()&&Machines.mechanized(t.l,t.e,six),"At VI, the top of its ladder, the bench turns by itself");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theDriveShaftReachesAFixedSixtyFour(GameTestHelper h){
  var t=town(h,null);
  try{h.assertTrue(ResearchKnobs.DRIVE_FIXED==64&&ResearchKnobs.driveReach(t.l,t.e)==64,"64 blocks in every village, with no research (CF14)");
   learn(t,"engineering.1","engineering.2");h.assertTrue(ResearchKnobs.driveReach(t.l,t.e)==64,"and no research changes it");
   h.assertTrue(Automation.level("farm",false)==6&&Automation.level("farm",true)==5&&Automation.grant("farm").cycle(),"The farm's machine: VI (an older village's farm V)");
   h.assertTrue(Machines.ROAD_REPAIR.equals("roads.6"),"The machines repair the roads with Roads VI");
  }finally{done(t);}
  h.succeed();
 }
}
