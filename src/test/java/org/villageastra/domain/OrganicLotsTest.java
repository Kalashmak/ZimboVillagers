package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OrganicLotsTest {
 @Test void boundedCandidateRejectionPreservesExistingAllocations(){
  var failed=UUID.fromString("7df7eabf-14fb-3273-81e5-bf000396d621");
  assertEquals(-1,OrganicLots.redraws(failed,OrganicLots.CURRENT));
  assertTrue(OrganicLots.tryBuildings(failed,OrganicLots.CURRENT).isEmpty());
  assertThrows(IllegalStateException.class,()->OrganicLots.buildings(failed));
  for(int version=1;version<=OrganicLots.CURRENT;version++){
   var id=new UUID(5,12);
   assertEquals(OrganicLots.buildings(id,version),OrganicLots.tryBuildings(id,version).orElseThrow());
  }
 }
 @Test void variedConnectedSettlementEnvelopeAndStableIdentities(){
  var signatures=new HashSet<String>();
  for(int seed=0;seed<2000;seed++){
   var id=new UUID(seed,seed*127L);var lots=OrganicLots.buildings(id);
   assertEquals(lots,OrganicLots.buildings(id));assertEquals(7,lots.size());
   assertEquals(0,lots.get(0).x());assertEquals(0,lots.get(0).z());
   assertEquals(Settlement.initialBuildings(id).stream().map(Settlement.Building::id).toList(),lots.stream().map(Settlement.Building::id).toList());
   double sx=0,sz=0;
   for(int i=1;i<lots.size();i++){
    var a=lots.get(i);sx+=a.x()+OrganicLots.width(a.type())/2.0-3;sz+=a.z()+OrganicLots.depth(a.type())/2.0-3;
    int x=a.x()+OrganicLots.west(a.type());assertTrue(x>-48&&x+OrganicLots.width(a.type())<49&&a.z()>-48&&a.z()+OrganicLots.depth(a.type())<49);
    for(int j=0;j<i;j++)assertFalse(OrganicLots.overlaps(a,lots.get(j)));
   }
   assertTrue(Math.hypot(sx/6,sz/6)<9,"Hall stays inside the surrounding ring");signatures.add(lots.stream().map(b->b.x()+":"+b.z()).toList().toString());
  }
  assertEquals(2000,signatures.size());
 }
 /** AD-104: a piece saved before the farm's field grew keeps the positions it was generated with (these are what that code gave). */
 @Test void piecesSavedBeforeTheBiggerFieldKeepTheirLots(){
  var expected=new LinkedHashMap<UUID,String>();
  expected.put(new UUID(5,12),"town_hall:0,0 home:18,-29 home:-18,19 home:31,7 farm:16,16 forester:-11,-34 mine:-34,-7");
  expected.put(new UUID(7,7*127L),"town_hall:0,0 home:-10,29 home:11,-26 home:-32,14 farm:25,13 forester:29,-15 mine:-25,-18");
  expected.put(new UUID(42,42*127L),"town_hall:0,0 home:-10,31 home:19,21 home:7,-32 farm:-14,-32 forester:33,-9 mine:-35,-6");
  expected.put(UUID.nameUUIDFromBytes("astra-lots".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"town_hall:0,0 home:13,-32 home:-34,11 home:25,-5 farm:-12,25 forester:-19,-29 mine:19,26");
  expected.forEach((id,lots)->{
   for(int version=2;version<OrganicLots.FIELD_MODULES;version++){
    var got=String.join(" ",OrganicLots.buildings(id,version).stream().map(b->b.type()+":"+b.x()+","+b.z()).toList());
    assertEquals(lots,got,"version "+version+" of "+id);
    assertEquals(OrganicLots.buildings(id,version),Settlement.natural(id,new int[7],version).buildings().stream().map(b->new Settlement.Building(b.id(),b.type(),b.x(),0,b.z(),b.rotation(),b.level())).toList());
   }
  });
  // Layout 5 keeps the positions it gave before layout 6 (these are what that code gave).
  var five=new LinkedHashMap<UUID,String>();
  five.put(new UUID(5,12),"town_hall:0,0 home:18,-29 home:-18,19 home:31,7 farm:16,11 forester:-11,-34 mine:-34,-7");
  five.put(new UUID(7,7*127L),"town_hall:0,0 home:-10,29 home:11,-26 home:-32,14 farm:25,7 forester:29,-15 mine:-25,-18");
  five.put(new UUID(42,42*127L),"town_hall:0,0 home:-10,31 home:19,21 home:7,-32 farm:-14,-37 forester:33,-9 mine:-35,-6");
  five.put(UUID.nameUUIDFromBytes("astra-lots".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"town_hall:0,0 home:13,-32 home:-34,11 home:25,-5 farm:-12,19 forester:-19,-29 mine:19,26");
  five.forEach((id,lots)->{
   assertEquals(lots,String.join(" ",OrganicLots.buildings(id,OrganicLots.FIELD_MODULES).stream().map(b->b.type()+":"+b.x()+","+b.z()).toList()),"version 5 of "+id);
   assertEquals(OrganicLots.buildings(id,OrganicLots.FIELD_MODULES),Settlement.natural(id,new int[7],OrganicLots.FIELD_MODULES).buildings().stream().map(b->new Settlement.Building(b.id(),b.type(),b.x(),0,b.z(),b.rotation(),b.level())).toList());
  });
  assertEquals(OrganicLots.buildings(new UUID(5,12)),OrganicLots.buildings(new UUID(5,12),OrganicLots.CURRENT));
  assertEquals(OrganicLots.BARN_LOTS,OrganicLots.CURRENT,"New villages get the barn lots");
 }
 /** AD-130/AD-131: layout 6 reserves the farm's barn (22x38) and the forester's hut (15x21) from level I; an allocation that cannot place
  *  them redraws with a salted seed, so no village of 5000 fails, and every lot of each stays within 47 of the hall and 5 apart. */
 @Test void barnLotsAllocateEveryVillageWithRedraws(){
  assertEquals(22,OrganicLots.width("farm"));assertEquals(38,OrganicLots.depth("farm"));
  assertEquals(15,OrganicLots.width("forester"));assertEquals(21,OrganicLots.depth("forester"));
  assertEquals(9,OrganicLots.width("forester",OrganicLots.FIELD_MODULES));assertEquals(17,OrganicLots.depth("forester",OrganicLots.FIELD_MODULES));
  var random=new Random(20260922L);int redrawn=0;
  for(int n=0;n<5000;n++){
   var id=n%2==0?new UUID(n,n*127L):new UUID(random.nextLong(),random.nextLong());
   int salt=OrganicLots.redraws(id,OrganicLots.BARN_LOTS);assertTrue(salt>=0&&salt<=OrganicLots.REDRAWS,"Village "+id+" finds its lots");if(salt>0)redrawn++;
   var lots=OrganicLots.buildings(id);assertEquals(lots,OrganicLots.buildings(id));
   for(int i=1;i<lots.size();i++){var a=lots.get(i);int x=a.x()+OrganicLots.west(a.type());
    assertTrue(x>=-47&&x+OrganicLots.width(a.type())-1<=47&&a.z()>=-47&&a.z()+OrganicLots.depth(a.type())-1<=47,"Inside the piece: "+a);
    for(int j=0;j<i;j++)assertFalse(OrganicLots.overlaps(a,lots.get(j)),a.type()+" clear of "+lots.get(j).type()+" in "+id);}
  }
  assertTrue(redrawn>0&&redrawn<5000/2,"Some villages need a redraw, most do not: "+redrawn);
  // Version 5 and older never redraw.
  for(int n=0;n<200;n++)assertTrue(OrganicLots.redraws(new UUID(n,n*127L),OrganicLots.FIELD_MODULES)<=0);
 }
 @Test void legacyLayoutAndElevationsRemainStable(){
  var id=new UUID(5,12);assertEquals(List.copyOf(Settlement.natural(id).buildings()),List.copyOf(Settlement.natural(id,new int[7],1).buildings()));
  int[] elevations={0,12,-12,2,3,4,5};var s=Settlement.natural(id,elevations,2);
  assertEquals(12,s.buildings().stream().skip(1).findFirst().orElseThrow().y());
  assertThrows(IllegalArgumentException.class,()->Settlement.natural(id,elevations,1));
 }
}
