package org.villageastra.domain;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** AD-104 P2, owner decision 2 of 2026-09-19: village meals count carrot 1, potato 1, beetroot 2, bread and baked potato 5; other safe food keeps its vanilla value. */
class RationsTest {
 private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
 @Test void theShippedTableIsTheOwners(){
  assertEquals(Map.of("minecraft:carrot",1,"minecraft:potato",1,"minecraft:beetroot",2,"minecraft:bread",5,"minecraft:baked_potato",5),Rations.load());
  assertThrows(UnsupportedOperationException.class,()->Rations.load().put("minecraft:apple",20),"The loaded table cannot be changed behind the balance file");
 }
 @Test void unlistedFoodKeepsItsVanillaValue(){
  var table=Rations.load();
  assertEquals(4,Rations.value(table,"minecraft:apple",4),"An apple is not in the table: its vanilla 4");
  assertEquals(1,Rations.value(table,"minecraft:carrot",3),"A carrot counts 1 for a village meal, not its vanilla 3");
  assertEquals(1,Rations.value(table,"minecraft:potato",1));assertEquals(2,Rations.value(table,"minecraft:beetroot",1),"Beetroot counts 2, not its vanilla 1");
  assertEquals(5,Rations.value(table,"minecraft:bread",5));assertEquals(5,Rations.value(table,"minecraft:baked_potato",5));
  assertEquals(6,Rations.value(table,null,6),"No id: the vanilla value");
 }
 @Test void aBrokenTableFailsTheLoad(){
  for(var bad:List.of("{\"minecraft:carrot\":-1}","{\"minecraft:carrot\":21}","{\"carrot\":1}","{\":carrot\":1}","{\"minecraft:\":1}","{\"Minecraft:Carrot\":1}",
    "{\"minecraft:carrot\":1.5}","{\"minecraft:carrot\":\"1\"}","{\"minecraft:carrot\":null}","{\"minecraft:carrot\":[1]}"))
   assertThrows(IllegalArgumentException.class,()->Rations.parse(json(bad)),bad);
  assertThrows(IllegalArgumentException.class,()->Rations.parse(null),"A missing table is an error, not vanilla food");
  assertEquals(Map.of("minecraft:carrot",0,"minecraft:apple",20,"villageastra:field_ration",3),Rations.parse(json("{\"minecraft:carrot\":0,\"minecraft:apple\":20,\"villageastra:field_ration\":3}")),"0 and 20 are the bounds; any namespace");
 }
 @Test void aMealTakesWholeItemsUntilItIsFull(){
  // Population.meal takes items until it has eaten 5: carrots and potatoes 5 a meal, beetroot 3 (6 rations), bread and baked potato 1.
  var table=Rations.load();
  for(var food:List.of("minecraft:carrot","minecraft:potato"))assertEquals(5,Rations.itemsPerMeal(Rations.value(table,food,0),5),food);
  assertEquals(3,Rations.itemsPerMeal(Rations.value(table,"minecraft:beetroot",0),5));
  for(var food:List.of("minecraft:bread","minecraft:baked_potato"))assertEquals(1,Rations.itemsPerMeal(Rations.value(table,food,0),5),food);
  assertEquals(2,Rations.itemsPerMeal(3,5));assertEquals(2,Rations.itemsPerMeal(4,5));assertEquals(1,Rations.itemsPerMeal(20,5));
  assertEquals(0,Rations.itemsPerMeal(0,5),"Food worth nothing makes no meal");assertEquals(0,Rations.itemsPerMeal(-1,5));
  // The rest of a meal, one slot's share in one withdrawal: 3 rations eaten, 2 carrots more; 1 missing, one beetroot or one bread.
  assertEquals(2,Rations.itemsPerMeal(1,2));assertEquals(1,Rations.itemsPerMeal(2,1));assertEquals(1,Rations.itemsPerMeal(5,1));
  assertThrows(IllegalArgumentException.class,()->Rations.itemsPerMeal(1,0));
 }
 @Test void residentsFedCountMealsAsTheyAreEaten(){
  // Two meals a day: 60 carrots, 36 beetroots or 12 bread feed 6; beetroot's 6th ration a meal is lost (48.61 a day feed 8.10, not 9.72).
  assertEquals(6,Rations.residentsFed(60,1,5,2),1e-12);assertEquals(6,Rations.residentsFed(36,2,5,2),1e-12);assertEquals(6,Rations.residentsFed(12,5,5,2),1e-12);
  assertEquals(8.10,Rations.residentsFed(48.61,2,5,2),0.005);
  assertEquals(0,Rations.residentsFed(100,0,5,2),"Food worth nothing feeds nobody");assertEquals(0,Rations.residentsFed(100,5,5,0));
 }
}
