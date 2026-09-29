package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** AD-136 (spec 3.4, 6.1, CF1, CF4): a schema-1 research record (every node paid in books, 7 x tier) moves once to schema 2. */
class ResearchMigrationTest {
 private static Map<String,Integer> paid(Object... kv){var m=new LinkedHashMap<String,Integer>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],(Integer)kv[i+1]);return m;}
 @Test void civicDoneIsDroppedAndItsBooksBecomeCredit(){
  var r=ResearchMigration.migrate(Set.of("civic.2","agriculture.1"),"",List.of(),paid("civic.3",5));
  assertEquals(Set.of("agriculture.1"),r.legacyDone(),"a done civic node leaves the record; a done level I in the done list stays done");
  assertEquals(5,r.credit(),"books paid into a removed node are credit");assertTrue(r.paid().isEmpty());
 }
 @Test void mechanicsBooksAreCreditAndARemovedTargetLeaves(){
  var r=ResearchMigration.migrate(Set.of(),"mechanics.4",List.of("exploration.2","engineering.6"),paid("mechanics.4",10));
  assertEquals(10,r.credit());assertEquals("",r.selected());assertTrue(r.queue().isEmpty(),"removed nodes leave the queue: "+r.queue());
 }
 @Test void anyOldSideLevelIsTheOneSideNode(){
  var r=ResearchMigration.migrate(Set.of("milling.3","carpentry.1"),"masonry.4",List.of("milling.5"),paid("masonry.4",9,"milling.2",3));
  assertTrue(r.legacyDone().containsAll(Set.of("milling.2","carpentry.3")),"done side levels become the side node: "+r.legacyDone());
  assertEquals("",r.selected(),"an old side level that is not the side node is no target");assertTrue(r.queue().isEmpty());
  // masonry.4's 9 books are credit; milling.2's 3 books are credit too, milling.2 being done by milling.3 already.
  assertEquals(12,r.credit());assertFalse(r.paid().containsKey("masonry.4"));
 }
 @Test void aLevelOneNodeIsDoneOnlyWhenPaidInFullAndItsBooksAreCredit(){
  var part=ResearchMigration.migrate(Set.of(),"agriculture.1",List.of(),paid("agriculture.1",3));
  assertTrue(part.resourceDone().isEmpty(),"a partly paid level I is not done (CF1)");assertEquals(3,part.credit());assertEquals("",part.selected(),"a level I is never a laboratory target (CF3)");
  assertFalse(part.paid().containsKey("agriculture.1"),"a level-I node never keeps a works count");
  var full=ResearchMigration.migrate(Set.of(),"",List.of(),paid("agriculture.1",7));
  assertEquals(Set.of("agriculture.1"),full.resourceDone());assertEquals(7,full.credit());
 }
 @Test void aLevelThreeNodePaidTwentyOneIsDoneAtFourteenAndTheRestIsCredit(){
  var r=ResearchMigration.migrate(Set.of(),"agriculture.3",List.of("forestry.2"),paid("agriculture.3",21,"forestry.2",4,"mining.4",30));
  assertEquals(14,r.paid().get("agriculture.3"),"paid to its old price (21): done at the new one (14)");assertEquals(4,r.paid().get("forestry.2"));
  // mining.4: 30 books of an old price 28 was done; the new price is 21, 9 over it are credit. agriculture.3: 21-14 = 7.
  assertEquals(21,r.paid().get("mining.4"));assertEquals(7+9,r.credit());
  assertEquals("forestry.2",r.selected(),"a done target gives way to the first open node of the queue");assertTrue(r.queue().isEmpty());
 }
 @Test void aPartlyPaidNodeKeepsWhatFitsTheNewPrice(){
  var r=ResearchMigration.migrate(Set.of(),"roads.3",List.of(),paid("roads.3",20));
  assertEquals(14,r.paid().get("roads.3"),"20 of the old 21 keep 14, the new price");assertEquals(6,r.credit());assertEquals("",r.selected(),"now paid in full, it is done and no target");
  var small=ResearchMigration.migrate(Set.of(),"roads.3",List.of(),paid("roads.3",5));
  assertEquals(5,small.paid().get("roads.3"));assertEquals(0,small.credit());assertEquals("roads.3",small.selected());
 }
 @Test void migrationIsDeterministicAndNeverLosesBooks(){
  var random=new Random(136);var ids=List.of("civic.2","mechanics.5","milling.2","masonry.3","agriculture.1","agriculture.2","roads.6","engineering.6","engineering.5","research.3");
  for(int round=0;round<200;round++){var p=new LinkedHashMap<String,Integer>();for(var id:ids)if(random.nextBoolean())p.put(id,random.nextInt(50));
   var a=ResearchMigration.migrate(Set.of(),"",List.of(),p);var b=ResearchMigration.migrate(Set.of(),"",List.of(),p);assertEquals(a,b);
   int before=p.values().stream().mapToInt(Integer::intValue).sum(),after=a.credit()+a.paid().values().stream().mapToInt(Integer::intValue).sum();
   assertEquals(before,after,"every book is either kept on a node or credit: "+p);}
 }
}
