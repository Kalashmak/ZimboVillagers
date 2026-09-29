package org.villageastra.domain;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** AD-143: the 22 furniture blocks — a chair and a table of every wood — each have a blockstate (chairs by facing, tables multipart with a leg
 *  per free corner), a model on the shared template naming the wood's planks and stripped log, an item model, a drop, a recipe (one each),
 *  names, a price and their tags; the carpentry, the hall and the bootstrap workshop make them. Pure JSON, no registry. */
final class FurnitureResourcesTest {
 private static final Path RES=Path.of("").toAbsolutePath().resolve("src/main/resources");
 private static JsonObject json(String p) throws IOException {var f=RES.resolve(p);assertTrue(Files.exists(f),"Missing "+p);return JsonParser.parseString(Files.readString(f,StandardCharsets.UTF_8)).getAsJsonObject();}
 private static List<String> strings(JsonArray a){return a.asList().stream().map(JsonElement::getAsString).toList();}
 @Test void everyWoodHasAChairAndATable() throws IOException {
  assertEquals(11,Furniture.WOODS.size());
  var ru=json("assets/villageastra/lang/ru_ru.json");var en=json("assets/villageastra/lang/en_us.json");var value=json("data/villageastra/balance/levels.json").getAsJsonObject("value");
  assertEquals("minecraft:block/block",json("assets/villageastra/models/block/chair.json").get("parent").getAsString());
  assertTrue(json("assets/villageastra/models/block/chair.json").getAsJsonArray("elements").size()>=6,"A modelled chair: legs, seat, back");
  int files=0;
  for(var wood:Furniture.WOODS){
   for(var name:List.of(Furniture.chairPath(wood),Furniture.tablePath(wood))){var id="villageastra:"+name;boolean chair=name.endsWith("_chair");
    var bs=json("assets/villageastra/blockstates/"+name+".json");
    if(chair){var v=bs.getAsJsonObject("variants");assertEquals(Set.of("facing=north","facing=east","facing=south","facing=west"),v.keySet(),name);
     assertEquals(90,v.getAsJsonObject("facing=east").get("y").getAsInt());assertEquals(180,v.getAsJsonObject("facing=south").get("y").getAsInt());assertEquals(270,v.getAsJsonObject("facing=west").get("y").getAsInt());
     assertEquals("villageastra:block/"+name,v.getAsJsonObject("facing=north").get("model").getAsString());}
    else{var parts=bs.getAsJsonArray("multipart");assertEquals(5,parts.size(),"The board and four legs: "+name);
     assertFalse(parts.get(0).getAsJsonObject().has("when"),"The board always shows");
     for(int i=1;i<5;i++){var when=parts.get(i).getAsJsonObject().getAsJsonObject("when");assertEquals(2,when.size(),"A leg shows where both sides of its corner are free");
      for(var k:when.keySet())assertEquals("false",when.get(k).getAsString());}
     for(var part:List.of(name+"_top",name+"_leg")){var m=json("assets/villageastra/models/block/"+part+".json");assertTrue(m.get("parent").getAsString().startsWith("villageastra:block/table_"),part);files++;}}
    var model=json("assets/villageastra/models/block/"+name+".json");var tex=model.getAsJsonObject("textures");
    assertEquals("villageastra:block/"+(chair?"chair":"table"),model.get("parent").getAsString());
    assertEquals("minecraft:block/"+wood+"_planks",tex.get("planks").getAsString());
    assertTrue(tex.get("log").getAsString().matches("minecraft:block/stripped_"+wood+"_(log|stem|block)"),"The legs are the wood's stripped log: "+tex);
    assertEquals("villageastra:block/"+name,json("assets/villageastra/models/item/"+name+".json").get("parent").getAsString());
    var entry=json("data/villageastra/loot_tables/blocks/"+name+".json").getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
    assertEquals(id,entry.get("name").getAsString(),"Drops itself");
    var r=json("data/villageastra/recipes/"+name+".json");assertEquals("minecraft:crafting_shaped",r.get("type").getAsString());
    assertEquals(chair?List.of("S  ","SPP","S S"):List.of("PPP","S S"),strings(r.getAsJsonArray("pattern")));
    assertEquals("minecraft:stick",r.getAsJsonObject("key").getAsJsonObject("S").get("item").getAsString());
    assertEquals("minecraft:"+wood+"_planks",r.getAsJsonObject("key").getAsJsonObject("P").get("item").getAsString());
    assertEquals(id,r.getAsJsonObject("result").get("item").getAsString());assertEquals(chair?Furniture.CHAIR_YIELD:Furniture.TABLE_YIELD,r.getAsJsonObject("result").get("count").getAsInt());
    assertTrue(ru.get("block.villageastra."+name).getAsString().startsWith(chair?"Стул (":"Стол ("),"ru name of "+name);
    assertTrue(en.get("block.villageastra."+name).getAsString().endsWith(chair?" Chair":" Table"),"en name of "+name);
    assertEquals(chair?2:3,value.get(id).getAsInt(),"Priced: "+id);
    files+=6;}}
  assertEquals(22*6+11*2,files,"Every file of the 22 blocks");
  for(var kind:List.of("chairs","tables"))for(var reg:List.of("items","blocks"))
   assertEquals(Furniture.WOODS.stream().map(w->kind.equals("chairs")?Furniture.chairId(w):Furniture.tableId(w)).toList(),strings(json("data/villageastra/tags/"+reg+"/"+kind+".json").getAsJsonArray("values")));
  var axe=strings(json("data/minecraft/tags/blocks/mineable/axe.json").getAsJsonArray("values"));
  assertTrue(axe.containsAll(List.of("#"+Furniture.CHAIRS,"#"+Furniture.TABLES,"#"+FramedWindows.TAG)),"An axe cuts furniture and windows: "+axe);
  assertTrue(ru.has("entity.villageastra.seat")&&en.has("entity.villageastra.seat")&&ru.has("message.villageastra.chair_taken")&&ru.has("work.villageastra.resting"));
 }
 @Test void theVillageMakesThem() throws IOException {
  var shops=json("data/villageastra/balance/workshops.json").getAsJsonObject("workshops");
  var carpentry=shops.getAsJsonObject("carpentry").getAsJsonArray("output_tags");
  assertTrue(carpentry.contains(new JsonPrimitive(Furniture.CHAIRS))&&carpentry.contains(new JsonPrimitive(Furniture.TABLES)),"The carpentry makes furniture");
  var hall=shops.getAsJsonObject("town_hall").getAsJsonObject("research_outputs").getAsJsonArray("engineering.1");
  assertTrue(hall.contains(new JsonPrimitive("#"+Furniture.CHAIRS))&&hall.contains(new JsonPrimitive("#"+Furniture.TABLES)),"Engineering I teaches the hall furniture");
  var boot=json("data/villageastra/balance/bootstrap_workshop.json").getAsJsonArray("output_tags");
  assertTrue(boot.contains(new JsonPrimitive(Furniture.CHAIRS))&&boot.contains(new JsonPrimitive(Furniture.TABLES)),"The hall makes furniture slowly before Engineering I");
 }
 @Test void legsStandOnlyAtTheOuterCorners(){
  assertArrayEquals(new boolean[]{true,true,true,true},Furniture.legs(false,false,false,false));
  assertArrayEquals(new boolean[]{false,false,false,false},Furniture.legs(false,true,false,true),"The middle of a row stands on its neighbours");
  assertArrayEquals(new boolean[]{true,false,false,true},Furniture.legs(false,true,false,false),"The west end of a row: its two west legs");
  assertArrayEquals(new boolean[]{true,false,false,false},Furniture.legs(false,true,true,false),"The north-west table of a square: one leg");
  assertEquals("spruce",Furniture.wood(Furniture.chairId("spruce")));assertTrue(Furniture.table(Furniture.tableId("oak"))&&!Furniture.chair(Furniture.tableId("oak")));
 }
}
