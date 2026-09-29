package org.villageastra.domain;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** AD-112: every core and ring recipe is real and made from what the village itself makes, mines, grows, cuts or breeds;
 *  the core table is complete and honest; the level kits hold only blocks the village can make. Pure JSON, no registry. */
final class CoreRecipesTest {
 private static final Path RES=Path.of("").toAbsolutePath().resolve("src/main/resources/data/villageastra");
 /** Raw outputs of the resource buildings (AD-112 §3): mine, farm (with its unlocked crops), forester and livestock. */
 private static final Set<String> RAW=Set.of("minecraft:cobblestone","minecraft:cobbled_deepslate","minecraft:andesite","minecraft:diorite","minecraft:granite","minecraft:coal","minecraft:raw_iron","minecraft:raw_copper","minecraft:raw_gold","minecraft:redstone","minecraft:lapis_lazuli","minecraft:diamond","minecraft:gravel","minecraft:flint",
  "minecraft:wheat","minecraft:wheat_seeds","minecraft:carrot","minecraft:potato","minecraft:beetroot","minecraft:beetroot_seeds","minecraft:pumpkin","minecraft:melon_slice","minecraft:sugar_cane",
  "minecraft:leather","minecraft:feather","minecraft:egg","minecraft:beef","minecraft:porkchop","minecraft:chicken","minecraft:mutton");
 /** Tags a resource building fills with raw output: logs (forester), wool (livestock), coals (the mine's coal). */
 private static final Set<String> RAW_TAGS=Set.of("minecraft:logs","minecraft:wool","minecraft:coals");
 private static JsonObject json(Path p) throws IOException {return JsonParser.parseString(Files.readString(p,StandardCharsets.UTF_8)).getAsJsonObject();}
 private static JsonObject recipe(String name) throws IOException {var p=RES.resolve("recipes/"+name+".json");assertTrue(Files.exists(p),"Recipe "+name+" exists");return json(p);}
 private static List<String> names(){var out=new ArrayList<String>();for(var t:CoreCatalog.TYPES)out.add(CoreCatalog.path(t));for(int g=CoreCatalog.FIRST_RING;g<=CoreCatalog.LAST_RING;g++)out.add("core_ring_"+g);return out;}
 /** Items and tags the village's workshops make: output_items, output_tags and custom outputs, and which workshop makes each. */
 private static Map<String,Set<String>> made() throws IOException {
  var out=new HashMap<String,Set<String>>();var shops=json(RES.resolve("balance/workshops.json")).getAsJsonObject("workshops");
  for(var shop:shops.entrySet()){var w=shop.getValue().getAsJsonObject();
   if(w.has("output_items"))for(var i:w.getAsJsonArray("output_items"))out.computeIfAbsent(i.getAsString(),k->new TreeSet<>()).add(shop.getKey());
   if(w.has("output_items_by_level"))for(var lv:w.getAsJsonObject("output_items_by_level").entrySet())for(var i:lv.getValue().getAsJsonArray())out.computeIfAbsent(i.getAsString(),k->new TreeSet<>()).add(shop.getKey());
   if(w.has("output_tags"))for(var i:w.getAsJsonArray("output_tags"))out.computeIfAbsent("#"+i.getAsString(),k->new TreeSet<>()).add(shop.getKey());
   if(w.has("custom"))for(var c:w.getAsJsonArray("custom"))for(var o:c.getAsJsonObject().getAsJsonArray("outputs"))out.computeIfAbsent(o.getAsJsonObject().get("item").getAsString(),k->new TreeSet<>()).add(shop.getKey());}
  return out;
 }
 /** Ingredient → its keys as "item" or "#tag", one per symbol. */
 private static Map<Character,String> keys(JsonObject r){var out=new TreeMap<Character,String>();
  for(var e:r.getAsJsonObject("key").entrySet()){assertEquals(1,e.getKey().length(),"A key is one symbol");var o=e.getValue().getAsJsonObject();
   out.put(e.getKey().charAt(0),o.has("tag")?"#"+o.get("tag").getAsString():o.get("item").getAsString());}
  return out;
 }
 private static Map<String,Integer> totals(JsonObject r){var k=keys(r);var out=new TreeMap<String,Integer>();
  for(var row:r.getAsJsonArray("pattern"))for(char c:row.getAsString().toCharArray())if(c!=' ')out.merge(k.get(c),1,Integer::sum);return out;}
 @Test void everyCoreAndRingRecipeParsesAndUsesOnlyItsKeys() throws IOException {
  assertEquals(25,names().size());
  for(var name:names()){var r=recipe(name);
   assertEquals("minecraft:crafting_shaped",r.get("type").getAsString(),name);
   assertEquals("villageastra:"+name,r.getAsJsonObject("result").get("item").getAsString(),name+" makes itself");
   var k=keys(r);var used=new TreeSet<Character>();var rows=r.getAsJsonArray("pattern");assertEquals(3,rows.size(),name+" is a 3x3 pattern");
   for(var row:rows){assertEquals(3,row.getAsString().length(),name);for(char c:row.getAsString().toCharArray()){if(c==' ')continue;assertTrue(k.containsKey(c),name+" uses undeclared key "+c);used.add(c);}}
   assertEquals(k.keySet(),used,name+" declares only keys it uses");}
 }
 @Test void everyIngredientIsMadeByTheVillage() throws IOException {
  var made=made();var problems=new ArrayList<String>();
  for(var name:names())for(var ing:totals(recipe(name)).keySet()){
   if(name.equals("core_ring_6")&&ing.equals("minecraft:netherite_block"))continue;
   boolean ok=made.containsKey(ing)||RAW.contains(ing)||ing.startsWith("#")&&RAW_TAGS.contains(ing.substring(1));
   if(!ok)problems.add(name+" needs "+ing);}
  assertTrue(problems.isEmpty(),"Ingredients no village building makes: "+problems);
  for(var t:CoreCatalog.TYPES)assertTrue(made.getOrDefault(CoreCatalog.coreId(t),Set.of()).contains("town_hall"),"The builder makes "+t+"'s core");
  for(int g=CoreCatalog.FIRST_RING;g<=CoreCatalog.LAST_RING;g++)assertTrue(made.getOrDefault(CoreCatalog.ringId(g),Set.of()).contains("smithy"),"The smith makes ring "+g);
  assertTrue(totals(recipe("core_ring_6")).containsKey("minecraft:netherite_block"),"Ring VI takes a whole netherite block");
 }
 @Test void aCoreTakesAtMostTwoIronAndNoRedstone() throws IOException {
  for(var t:CoreCatalog.TYPES){var tot=totals(recipe(CoreCatalog.path(t)));
   assertTrue(tot.getOrDefault("minecraft:iron_ingot",0)<=2,t+" takes "+tot.getOrDefault("minecraft:iron_ingot",0)+" iron");
   assertFalse(tot.containsKey("minecraft:redstone"),t+" takes redstone");}
 }
 @Test void theCoreTableIsCompleteAndHonest(){
  assertEquals(21,CoreCatalog.TYPES.size());assertEquals(21,new HashSet<>(CoreCatalog.TYPES).size());
  for(var t:CoreCatalog.TYPES){var effects=CoreEffects.effects(t);assertFalse(effects.isEmpty(),t+" has effects");
   for(var e:effects){
    if(e.active()){assertNotNull(e.values(),t+"/"+e.id());assertEquals(6,e.values().length,t+"/"+e.id());assertNotNull(e.test(),t+"/"+e.id()+" names its test");assertTrue(e.test().contains("#"),t+"/"+e.id()+" test is Class#method");
     // Every effect grows with the level; the mine's floor grows downwards.
     int sign=e.unit().equals("y")?-1:1;for(int i=1;i<6;i++)assertTrue(sign*(e.values()[i]-e.values()[i-1])>=0,t+"/"+e.id()+" never falls back at level "+(i+1));}
    else{assertNull(e.values(),t+"/"+e.id()+" shows no numbers while inactive");assertThrows(IllegalStateException.class,()->e.at(2));}}}
  assertArrayEquals(CoreEffects.effect("mine","floor").values(),CoreEffects.mine().floorY(),"The mine's floor effect is its drive floors");
  assertEquals(CoreEffects.effects("mine"),CoreEffects.effects("quarry"),"The quarry holds the mine's core");
  assertEquals(4,CoreEffects.rings().size());
  assertEquals(60,CoreEffects.value("smithy","labour",3));assertEquals(1440/80,CoreEffects.value("farm","field",6));
 }
 @Test void theCatalogueNamesCoresAndRings(){
  assertEquals("villageastra:core_mine",CoreCatalog.coreId("quarry"));assertEquals("villageastra:core_town_hall",CoreCatalog.coreId("town_hall_3"));
  assertNull(CoreCatalog.coreId("home"));assertNull(CoreCatalog.coreId("home_2"));assertNull(CoreCatalog.coreId("wall"));
  assertTrue(CoreCatalog.isCore("villageastra:core_farm"));assertFalse(CoreCatalog.isCore("villageastra:core_ring_3"));
  assertEquals(5,CoreCatalog.ringGrade("villageastra:core_ring_5"));assertTrue(CoreCatalog.isRing(CoreCatalog.ringId(6)));assertFalse(CoreCatalog.isRing("villageastra:core_ring_2"));
  assertEquals("farm",CoreCatalog.typeOf("villageastra:core_farm"));
  assertThrows(IllegalArgumentException.class,()->CoreCatalog.ringId(2));
 }
 @Test void levelKitsHoldOnlyBlocksTheVillageMakes() throws IOException {
  var levels=json(RES.resolve("balance/levels.json"));var forbidden=List.of("iron_block","gold_block","diamond_block","emerald_block","netherite_block","copper_block","lapis_block","redstone_block","cut_copper",
   "anvil","enchanting_table","lodestone","bell","jukebox","observer","redstone_lamp","brewing_stand","hopper","cauldron","blast_furnace","dispenser","piston","lightning_rod","note_block","target","blackstone","loom");
  for(var kit:levels.getAsJsonObject("kits").entrySet())for(var level:kit.getValue().getAsJsonObject().entrySet())for(var block:level.getValue().getAsJsonArray()){
   var id=block.getAsString();for(var f:forbidden)assertFalse(id.contains(f),kit.getKey()+" level "+level.getKey()+" holds "+id);}
  var value=levels.getAsJsonObject("value");
  for(var t:CoreCatalog.TYPES)assertTrue(value.has(CoreCatalog.coreId(t)),"The estimate weighs "+t+"'s core");
  int last=0;for(int g=CoreCatalog.FIRST_RING;g<=CoreCatalog.LAST_RING;g++){int v=value.get(CoreCatalog.ringId(g)).getAsInt();assertTrue(v>last,"Ring "+g+" weighs more than the one before");last=v;}
 }
}
