package org.villageastra.domain;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** AD-139 §9: the restaurant's numbers agree — the core's seats are two per table group of dining.json, the couriers 0/0/0/1/2/2, the hall
 *  feeds every AD-104 farm target with room to spare, and the level ladder, the automation and the research catalogue all name the restaurant. */
class RestaurantCapacityTest {
 private static JsonObject json(String path){try(var s=RestaurantCapacityTest.class.getResourceAsStream(path)){assertNotNull(s,path);return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new UncheckedIOException(e);}}
 @Test void seatsAreTwoPerTableGroupAndFeedTheFarmTargets(){
  var dining=json("/data/villageastra/balance/dining.json");var groups=dining.getAsJsonObject("seat_groups");
  int[] seats={4,8,8,12,12,16},targets={6,12,24,36,64,100};int sit=dining.get("sit_ticks").getAsInt();
  assertEquals(seats.length,CoreEffects.LEVELS);
  for(int level=1;level<=6;level++){assertEquals(seats[level-1],2*groups.get(String.valueOf(level)).getAsInt(),"Level "+level);
   assertEquals(seats[level-1],CoreEffects.value("restaurant","seats",level),"The core's seats at "+level);
   // Two meals a day of sit_ticks each in 12000 ticks of daylight: a seat feeds 12000/sit/2 residents.
   double fed=seats[level-1]*12000.0/sit/2;assertTrue(fed>=1.5*targets[level-1],"Level "+level+" feeds "+fed+" for a target of "+targets[level-1]);}
  int[] couriers={0,0,0,1,2,2};for(int level=1;level<=6;level++)assertEquals(couriers[level-1],CoreEffects.value("restaurant","couriers",level));
  assertEquals(CoreEffects.value("restaurant","labour",1),20);
 }
 @Test void theRestaurantReplacedTheBakeryEverywhereButItsCoreId(){
  assertEquals("restaurant",CoreCatalog.canonical("bakery"));assertEquals("bakery",CoreCatalog.coreType("restaurant"),"The restaurant's core is the bakery's core type");
  assertEquals("villageastra:core_bakery",CoreCatalog.coreId("restaurant"));assertEquals("villageastra:core_bakery",CoreCatalog.coreId("bakery"));
  assertEquals("restaurant",Profession.BAKER.workplace(),"The cook's save id stays baker; his workplace is the restaurant");
  var levels=json("/data/villageastra/balance/levels.json");assertEquals("restaurant",levels.getAsJsonObject("catalog").get("restaurant").getAsString());
  assertEquals("restaurant",levels.getAsJsonObject("catalog").get("bakery").getAsString(),"An old bakery reads the restaurant's research");
  assertTrue(json("/data/villageastra/balance/automation.json").getAsJsonObject("buildings").has("restaurant"));
  var workshops=json("/data/villageastra/balance/workshops.json").getAsJsonObject("workshops");assertTrue(workshops.has("restaurant")&&!workshops.has("bakery"));
  assertEquals(7,workshops.getAsJsonObject("restaurant").getAsJsonObject("output_items_by_level").getAsJsonArray("2").size(),"Seven roasts from II");
  boolean found=false;for(var raw:json("/data/villageastra/catalog/building_progression.json").getAsJsonArray("catalog")){var b=raw.getAsJsonObject();
   assertNotEquals("bakery",b.get("building_id").getAsString());if(b.get("building_id").getAsString().equals("restaurant"))found=true;
   for(var lv:b.getAsJsonArray("levels"))if(lv.getAsJsonObject().has("requires_building"))assertNotEquals("bakery",lv.getAsJsonObject().getAsJsonObject("requires_building").get("type").getAsString());}
  assertTrue(found);
 }
 @Test void theBakingCardsSayWhatWorks(){
  var tree=json("/data/villageastra/catalog/research_tree.json");
  for(var raw:tree.getAsJsonArray("nodes")){var n=raw.getAsJsonObject();var id=n.get("id").getAsString();if(!id.startsWith("baking."))continue;
   // Every level works today: the dog and cart of V came with the kennel wolves of the livestock work (AD-138 IV), so no card promises.
   assertEquals("ACTIVE",n.get("status").getAsString(),id);
   assertFalse(n.has("future"),id+": an active card has no grey future line");}
 }
}
