package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** AD-139 §3.5: the restaurant's portions, pure arithmetic — a dish is opened once and what a meal leaves of it feeds the next diner. */
class DiningMathTest {
 private static final int MEAL=5,MAX=19;
 @Test void aDishOfEightFeedsAndLeavesThree(){
  assertArrayEquals(new int[]{1,3,5},Portions.serve(0,8,MEAL,MAX),"Cooked beef: one piece, 3 rations left over");
  assertArrayEquals(new int[]{1,6,5},Portions.serve(3,8,MEAL,MAX),"The next diner eats the 3 and opens a piece: 6 left");
  assertArrayEquals(new int[]{0,1,5},Portions.serve(6,8,MEAL,MAX),"The third eats from the leftover alone");
 }
 @Test void breadLeavesNothingAndBeetrootLeavesOne(){
  assertArrayEquals(new int[]{1,0,5},Portions.serve(0,5,MEAL,MAX),"Bread is exactly a meal");
  assertArrayEquals(new int[]{3,1,5},Portions.serve(0,2,MEAL,MAX),"Three beetroots, one ration left over");
 }
 @Test void theHallSavesWhatThePantryThrowsAway(){
  // §3.5 table: meat 37.5 %, beetroot 20 %, bread 0 % less food for the same meals (⌈5·N/ration⌉ against N whole items to the meal).
  int n=40;
  assertEquals(25,Portions.dishes(n,8,MEAL,MAX));assertEquals(40,Portions.pantryItems(n,8,MEAL));
  assertEquals(100,Portions.dishes(n,2,MEAL,MAX));assertEquals(120,Portions.pantryItems(n,2,MEAL));
  assertEquals(40,Portions.dishes(n,5,MEAL,MAX));assertEquals(40,Portions.pantryItems(n,5,MEAL));
  assertEquals(0.375,1-(double)Portions.dishes(n,8,MEAL,MAX)/Portions.pantryItems(n,8,MEAL),1e-9,"Meat saves 37.5 %");
 }
 @Test void theLeftoverIsCappedAndNothingIsOpenedForNothing(){
  assertArrayEquals(new int[]{1,19,5},Portions.serve(0,30,MEAL,MAX),"A dish larger than the cap keeps only leftover_max");
  assertArrayEquals(new int[]{0,0,0},Portions.serve(0,0,MEAL,MAX),"A dish of no rations is never opened");
  assertThrows(IllegalArgumentException.class,()->Portions.serve(-1,5,MEAL,MAX));
 }
}
