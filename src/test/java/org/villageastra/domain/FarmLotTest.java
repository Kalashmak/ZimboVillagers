package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** AD-112: the farm's organic lot keeps its reserved field (level II: module (0,1), z to 26) free, so it is 27 deep (layout 5);
 *  AD-130: from layout 6 it keeps the barn's final footprint, x -3..18, z 0..37 (22x38). The ring of lots around the hall still finds room
 *  for it in every village. */
class FarmLotTest {
 @Test void everyVillageFindsRoomForTheDeeperFarmLot(){
  assertEquals(27,OrganicLots.depth("farm",OrganicLots.FIELD_MODULES),"The layout-5 farm lot holds the farmhouse and the reserved field");
  assertEquals(9,OrganicLots.width("farm",OrganicLots.FIELD_MODULES));assertEquals(-1,OrganicLots.west("farm",OrganicLots.FIELD_MODULES));
  assertEquals(38,OrganicLots.depth("farm"),"The farm lot holds the farmhouse and the barn's final footprint");
  assertEquals(22,OrganicLots.width("farm"));assertEquals(-3,OrganicLots.west("farm"));
  assertArrayEquals(new int[]{-3,0,18,37},OrganicLots.farmReserve(false));
  assertArrayEquals(new int[]{-12,0,9,37},OrganicLots.farmReserve(true),"Turned west, the footprint mirrors about the 7-wide farmhouse");
  var random=new Random(112);
  for(int n=0;n<2000;n++){
   var id=n<1000?new UUID(n,n*127L):new UUID(random.nextLong(),random.nextLong());
   var lots=assertDoesNotThrow(()->OrganicLots.buildings(id),"Lots of "+id);
   var farm=lots.stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();
   int x=farm.x()+OrganicLots.west("farm");
   assertTrue(x>-48&&x+OrganicLots.width("farm")<=48&&farm.z()>-48&&farm.z()+OrganicLots.depth("farm")<=48,"The farm lot of "+id+" stays inside the piece: "+farm);
   for(var other:lots)if(other!=farm)assertFalse(OrganicLots.overlaps(farm,other),"The farm lot of "+id+" keeps clear of "+other.type());
  }
 }
 /** AD-130/AD-131: the starter village keeps the farm's final footprint free on its west side (the forester's 15x21 lot lies east of
  *  it), its mine moved east of that lot; a natural village's farm is east, and a schema-1 save still migrates to the old places. */
 @Test void theStarterFarmGrowsWestClearOfTheForesterAndTheMine(){
  var id=new UUID(3,5);var s=Settlement.initial(id);
  var farm=s.buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();
  var mine=s.buildings().stream().filter(b->b.type().equals("mine")).findFirst().orElseThrow();
  assertTrue(s.westField(farm.id()),"The starter farm grows west");assertEquals(OrganicLots.CURRENT,s.lotLayout());
  assertEquals(32,mine.x(),"The mine stands east of the forester's lot");
  var reserve=OrganicLots.farmReserve(true);int rx0=farm.x()+reserve[0],rx1=farm.x()+reserve[2],rz0=farm.z()+reserve[1],rz1=farm.z()+reserve[3];
  var sizes=Map.of("town_hall",new int[]{7,7},"home",new int[]{7,7},"forester",new int[]{OrganicLots.FORESTER_WIDTH,OrganicLots.FORESTER_DEPTH},"mine",new int[]{8,9});
  for(var b:s.buildings()){if(b==farm)continue;var d=sizes.get(b.type());
   assertTrue(b.x()+d[0]-1<rx0||b.x()>rx1||b.z()+d[1]-1<rz0||b.z()>rz1,b.type()+" at "+b.x()+","+b.z()+" stays off the farm's footprint");
   for(var o:s.buildings())if(o!=b&&o!=farm){var e=sizes.get(o.type());
    assertTrue(b.x()+d[0]-1<o.x()||o.x()+e[0]-1<b.x()||b.z()+d[1]-1<o.z()||o.z()+e[1]-1<b.z(),b.type()+" and "+o.type()+" do not overlap");}}
  var natural=Settlement.natural(id,new int[7],OrganicLots.CURRENT);
  assertTrue(natural.westFields().isEmpty()&&natural.lotLayout()==OrganicLots.CURRENT,"A natural village's farm stays east, on its lot");
  assertEquals(OrganicLots.FIELD_MODULES,Settlement.natural(id,new int[7],OrganicLots.FIELD_MODULES).lotLayout());
  var old=new Settlement(id);old.migrateStarterWorkplaces();
  assertEquals(24,old.buildings().stream().filter(b->b.type().equals("mine")).findFirst().orElseThrow().x(),"A schema-1 save keeps its mine where it was built");
  assertEquals(0,old.lotLayout(),"A migrated save keeps the old lots");
 }
}
