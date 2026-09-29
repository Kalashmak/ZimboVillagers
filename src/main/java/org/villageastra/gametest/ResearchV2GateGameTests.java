package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-136 (spec 6.2, owner answer 2, CF5): the gates of tree v2 - the hall's levels follow its own branch, no building is raised above the
 *  hall, level VI of every branch needs Engineering V, the school, warehouse and laboratory need their level I, the engineering office stops
 *  at V, and a side building (mill, stoneworks, carpentry) never outgrows its parent. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResearchV2GateGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void theHallsLevelsFollowItsOwnBranch(GameTestHelper h){
  for(int n=2;n<=6;n++)h.assertTrue(BuildingTiers.hallResearch(n).equals(List.of("town_hall."+n)),"Hall "+n+" needs town_hall."+n+": "+BuildingTiers.hallResearch(n));
  // AD-073: the node of the next hall level opens one hall level early, or the hall could never be raised.
  var hall2=ResearchCatalog.get("town_hall.2");
  h.assertTrue(ResearchRules.reason(Set.of(),1,hall2,BuildingTiers.hallResearch(2)).equals("available"),"Hall II's own node is open to a hall I");
  h.assertTrue(ResearchRules.reason(Set.of("town_hall.2"),1,ResearchCatalog.get("town_hall.3"),BuildingTiers.hallResearch(2)).equals("hall_level"),"Hall III's waits for hall II");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void levelSixOfEveryBranchNeedsEngineeringFive(GameTestHelper h){
  int six=0;
  for(var n:ResearchCatalog.NODES.values()){if(n.tier()!=6)continue;six++;var done=new HashSet<>(n.requires());done.remove(ResearchCatalog.GATE_VI);
   h.assertTrue(ResearchRules.reason(done,6,n,List.of()).equals("dependencies"),n.id()+" stays closed without Engineering V");
   done.add(ResearchCatalog.GATE_VI);h.assertTrue(ResearchRules.reason(done,6,n,List.of()).equals("available"),n.id()+" opens with it");}
  h.assertTrue(six==18,"17 branches and the hall have a level VI: "+six);
  h.assertTrue(BuildingTiers.research("farm",6).contains("agriculture.6"),"A building's level VI takes its branch's VI");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theSchoolWarehouseAndLaboratoryNeedTheirLevelOne(GameTestHelper h){
  var t=town(h,null);
  try{var pairs=Map.of("school","education.1","warehouse","logistics.1","laboratory","research.1");
   for(var p:pairs.entrySet()){h.assertTrue(ResearchGate.designRefusal(t.l,t.e,p.getKey()).equals("research"),p.getKey()+" is refused before "+p.getValue());
    learn(t,p.getValue());h.assertTrue(ResearchGate.designRefusal(t.l,t.e,p.getKey()).isEmpty(),p.getKey()+" may be ordered with "+p.getValue());}
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theEngineeringOfficeStopsAtFive(GameTestHelper h){
  var t=town(h,"engineering");
  try{h.assertTrue(BuildingTiers.max("engineering")==5&&BuildingTiers.max("farm")==6,"Five levels for the office, six for the rest");
   learn(t,"engineering.1","engineering.2","engineering.3","engineering.4","engineering.5");
   h.assertTrue(BuildingTiers.researchedGrade(t.l,t.e,"engineering")==5,"Researched up to V, never VI (CF5): "+BuildingTiers.researchedGrade(t.l,t.e,"engineering"));
   var b=raise(t,5);h.assertTrue(BuildingTiers.refusal(t.l,t.e,b).equals("done"),"An office at V is finished: "+BuildingTiers.refusal(t.l,t.e,b));
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void noBuildingIsRaisedAboveTheHall(GameTestHelper h){
  var t=town(h,"laboratory");
  try{learn(t,"research.1","research.2","research.3");var lab=t.kept();
   h.assertTrue(BuildingTiers.hallLevel(t.e)==1,"The hall stands at I");
   h.assertTrue(BuildingTiers.refusal(t.l,t.e,lab).equals("hall"),"Laboratory II is refused while the hall is at I: "+BuildingTiers.refusal(t.l,t.e,lab));
   t.s.civilization().completedHallUpgrade(2);
   h.assertTrue(!BuildingTiers.refusal(t.l,t.e,lab).equals("hall"),"With the hall at II the laboratory may follow: "+BuildingTiers.refusal(t.l,t.e,lab));
   var at2=raise(t,2);
   h.assertTrue(BuildingTiers.refusal(t.l,t.e,at2).equals("hall"),"Laboratory III waits for hall III: "+BuildingTiers.refusal(t.l,t.e,at2));
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void aSideBuildingNeverOutgrowsItsParent(GameTestHelper h){
  var t=town(h,null);
  try{h.assertTrue(BuildingTiers.research("mill",1).contains("milling.2"),"The mill opens on the side node milling.2");
   var p=BuildingTiers.parent("mill",3);h.assertTrue(p!=null&&p.type().equals("restaurant")&&p.level()==3,"Mill III needs the restaurant at III: "+p);
   learn(t,"milling.2");
   h.assertTrue(BuildingTiers.researchedGrade(t.l,t.e,"mill")==1,"Without its parent the mill is not open beyond I (CF5): "+BuildingTiers.researchedGrade(t.l,t.e,"mill"));
   h.assertTrue(BuildingTiers.parent("masonry",2)!=null&&BuildingTiers.parent("masonry",2).type().equals("mine"),"The stoneworks follows the mine");
  }finally{done(t);}
  h.succeed();
 }
}
