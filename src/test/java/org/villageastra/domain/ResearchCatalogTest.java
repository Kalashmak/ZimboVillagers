package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** AD-136 (spec 6.1): the research tree v2 as the owner's ladders of 2026-09-23 and his answers give it. */
class ResearchCatalogTest {
 private static final Set<String> REMOVED=Set.of("civic","exploration","mechanics");
 @Test void ownerCatalogHasAllBranchesPricesAndAcyclicPrerequisites(){
  // 17 branches of six levels, engineering I-V, the town hall II-VI (owner answer 2) and three side nodes: 115 nodes in 22 branches.
  assertEquals(22,ResearchCatalog.BRANCHES.size());assertEquals(115,ResearchCatalog.NODES.size());
  int sum=0;for(var b:ResearchCatalog.BRANCHES.values())sum+=b.size();assertEquals(sum,ResearchCatalog.NODES.size(),"nodes are the sum of the branches' levels");
  var eng=ResearchCatalog.branch("engineering");assertEquals(1,eng.from());assertEquals(5,eng.to());assertFalse(ResearchCatalog.NODES.containsKey("engineering.6"));
  var hall=ResearchCatalog.branch("town_hall");assertEquals(2,hall.from());assertEquals(6,hall.to());
  for(var id:REMOVED)assertFalse(ResearchCatalog.BRANCHES.containsKey(id),"removed branch "+id);
  long main=ResearchCatalog.BRANCHES.values().stream().filter(b->!b.side()&&b.size()==6).count();assertEquals(17,main);
  for(var n:ResearchCatalog.NODES.values()){
   assertEquals(n.tier()==1?0:7*(n.tier()-1),n.works(),"price of "+n.id());
   for(var parent:n.requires()){assertNotNull(ResearchCatalog.get(parent));assertFalse(REMOVED.contains(parent.substring(0,parent.indexOf('.'))),n.id()+" needs a removed branch "+parent);}
   if(n.tier()==6)assertTrue(n.requires().contains(ResearchCatalog.GATE_VI),n.id()+" needs engineering V");
   assertTrue(ResearchCatalog.STATUSES.contains(n.status()),n.id()+" "+n.status());
   if(n.status().equals("INTERIM")||n.status().equals("PLANNED"))assertFalse(n.futureRu().isBlank()||n.futureEn().isBlank(),n.id()+" has its future line");
  }
  assertTrue(ResearchCatalog.get("agriculture.6").requires().contains("engineering.5"),"agriculture VI needs engineering V, not mechanics");
  assertTrue(ResearchCatalog.get("engineering.5").requires().contains("research.4"));
  var reachable=new HashSet<String>();for(int pass=0;pass<ResearchCatalog.NODES.size();pass++)for(var n:ResearchCatalog.NODES.values())if(reachable.containsAll(n.requires()))reachable.add(n.id());
  assertEquals(ResearchCatalog.NODES.size(),reachable.size(),"acyclic");
  for(var b:ResearchCatalog.BRANCHES.values())for(int i=b.from();i<=b.to();i++)assertNotNull(ResearchCatalog.get(b.id()+"."+i));
 }
 /** CF12, D2: a side branch is one node at the level of its parent it hangs from, and needs its parent's node of that level. */
 @Test void sideBranchesAreOneNodeUnderTheirParent(){
  var sides=new TreeMap<String,String>();for(var b:ResearchCatalog.BRANCHES.values())if(b.side())sides.put(b.id(),b.sideOf()+"."+b.from());
  assertEquals(Map.of("carpentry","forestry.3","masonry","mining.3","milling","baking.2"),sides);
  for(var e:sides.entrySet()){var b=ResearchCatalog.branch(e.getKey());assertEquals(1,b.size());var n=ResearchCatalog.get(e.getKey()+"."+b.from());
   assertTrue(n.requires().contains(e.getValue()),n.id()+" hangs from "+e.getValue());assertEquals(7*(n.tier()-1),n.works());}
  assertEquals("milling.2",ResearchCatalog.renamed("milling.5"));assertEquals("masonry.3",ResearchCatalog.renamed("masonry.1"));assertEquals("carpentry.3",ResearchCatalog.renamed("carpentry.6"));
  assertNull(ResearchCatalog.renamed("civic.3"));assertNull(ResearchCatalog.renamed("mechanics.4"));assertNull(ResearchCatalog.renamed("engineering.6"));assertEquals("roads.4",ResearchCatalog.renamed("roads.4"));
 }
 /** CF2: every level-I price is made of what a village makes without research or quests; level II-VI is works only. */
 @Test void levelOnePricesComeFromWhatAVillageMakesAlone(){
  for(var n:ResearchCatalog.NODES.values()){
   if(n.tier()!=1){assertTrue(n.resources().isEmpty(),n.id()+" is paid in works");continue;}
   assertFalse(n.resources().isEmpty(),n.id()+" has a resource price");
   for(var c:n.resources()){assertTrue(ScienceBalance.TIER1_SOURCES.contains(c.key()),n.id()+" asks for "+c.key());assertTrue(c.count()>=1&&c.count()<=256);}
  }
  assertEquals(List.of("engineering.1","research.1","education.1","construction.1"),ScienceBalance.MAYOR_PRIORITY);
  for(var id:ScienceBalance.MAYOR_PRIORITY)assertEquals(1,ResearchCatalog.get(id).tier());
  // The owner's numbers: II..VI = 7/14/21/28/35; one work per 36 000 ticks (30 minutes); places 1..6.
  assertEquals(List.of(0,7,14,21,28,35),java.util.stream.IntStream.rangeClosed(1,6).map(ScienceBalance::works).boxed().toList());
  assertEquals(36000L,ScienceBalance.WORK_TICKS);for(int l=1;l<=6;l++)assertEquals(l,ScienceBalance.seats(l));
 }
 /** Spec 4.3/4.5: honest statuses - the one prerequisite-only node is forestry I; the hall's own branch, science and roads are live. */
 @Test void cardStatusesAreHonest(){
  var pre=new TreeSet<String>();for(var n:ResearchCatalog.NODES.values())if(n.status().equals("PREREQUISITE_ONLY"))pre.add(n.id());
  assertEquals(Set.of("forestry.1"),pre);
  for(int t=2;t<=6;t++)assertEquals("ACTIVE",ResearchCatalog.get("town_hall."+t).status());
  for(int t=1;t<=6;t++){assertEquals("ACTIVE",ResearchCatalog.get("research."+t).status(),"research."+t);assertEquals("ACTIVE",ResearchCatalog.get("roads."+t).status(),"roads."+t);}
  for(var id:List.of("metallurgy.2","metallurgy.3","metallurgy.4","metallurgy.5","metallurgy.6","engineering.3","engineering.4","defense.1","defense.3","defense.5","defense.6","military.1","military.2","military.3","military.4","military.5","military.6"))assertEquals("ACTIVE",ResearchCatalog.get(id).status(),id+" (AD-155..AD-159)");
  for(var id:List.of("milling.2","masonry.3","carpentry.3"))assertEquals("INTERIM",ResearchCatalog.get(id).status(),id+" works as the old building until its phase");
 }
 @Test void oldKnowledgeKeepsItsMeaningWithoutResettingPaidLegacyWork(){var old=Civilization.restore(1,List.of("cartography","ironworking"),"milling",400);assertEquals(Set.of("cartography.1","metallurgy.1"),ResearchCatalog.migrated(old));assertEquals("milling",old.active());assertEquals(400,old.progress());assertEquals("milling.2",ResearchCatalog.legacyId("milling"));assertThrows(IllegalArgumentException.class,()->ResearchCatalog.get("invented.1"));}
}
