package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MineAreaTest {
 @Test void protectionFollowsTheDescendingCorridor(){
  var area=new MineArea(10,3);
  assertTrue(area.contains(3,-10,17,0));assertTrue(area.contains(8,-10,17,3));assertFalse(area.contains(9,-10,17,3));
  assertTrue(area.contains(3,-3,20,3));assertFalse(area.contains(3,-2,20,3));
  assertFalse(area.contains(3,1,17,3),"Do not claim the entire rectangle above a deep shaft");
 }
 @Test void invalidMiningAreasAreRejected(){
  assertThrows(IllegalArgumentException.class,()->new MineArea(-1,3));assertThrows(IllegalArgumentException.class,()->new MineArea(4,2));
 }
}
