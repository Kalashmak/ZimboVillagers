package org.villageastra.domain;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** AD-140: every wood's framed window has its blockstate by axis, its model on the shared template, its item model, its drop, its recolour
 *  recipe, its names and its price; the sticks recipe makes dark oak; the carpentry and Engineering II make them. Pure JSON, no registry. */
final class FramedWindowResourcesTest {
 private static final Path RES=Path.of("").toAbsolutePath().resolve("src/main/resources");
 private static JsonObject json(String p) throws IOException {var f=RES.resolve(p);assertTrue(Files.exists(f),"Missing "+p);return JsonParser.parseString(Files.readString(f,StandardCharsets.UTF_8)).getAsJsonObject();}
 @Test void everyWoodHasItsResources() throws IOException {
  assertEquals(11,FramedWindows.WOODS.size());assertTrue(FramedWindows.WOODS.contains(FramedWindows.DEFAULT_WOOD));
  var ru=json("assets/villageastra/lang/ru_ru.json");var en=json("assets/villageastra/lang/en_us.json");
  var template=json("assets/villageastra/models/block/framed_window.json");
  assertEquals("minecraft:cutout",template.get("render_type").getAsString(),"The pane is cut out like vanilla glass");
  assertEquals("minecraft:block/glass",template.getAsJsonObject("textures").get("glass").getAsString());
  for(var wood:FramedWindows.WOODS){var name=FramedWindows.path(wood);var id=FramedWindows.id(wood);
   // Owner 2026-09-24: the block is laid from parts — per side and corner, the wood's frame piece where it does not join, shared glass
   // where it does; the middle glass always; along Z every part turned a quarter. The wood's own left bar comes first (the particle).
   var parts=json("assets/villageastra/blockstates/"+name+".json").getAsJsonArray("multipart");
   assertEquals(2*(2*JOINS.size()+1),parts.size(),name);
   assertEquals("villageastra:block/"+name+"_left",parts.get(0).getAsJsonObject().getAsJsonObject("apply").get("model").getAsString(),"The first part is the wood's");
   var seen=new HashSet<String>();
   for(var raw:parts){var part=raw.getAsJsonObject();var when=part.getAsJsonObject("when");var apply=part.getAsJsonObject("apply");var model=apply.get("model").getAsString();
    var axis=when.get("axis").getAsString();assertTrue(axis.equals("x")?!apply.has("y"):apply.get("y").getAsInt()==90,"The Z window is the X one turned a quarter: "+part);
    if(when.size()==1){assertEquals("villageastra:block/framed_window_glass_middle",model);seen.add(axis+"/middle");continue;}
    var join=when.keySet().stream().filter(k->!k.equals("axis")).findFirst().orElseThrow();assertTrue(JOINS.contains(join),join);
    boolean joined=when.get(join).getAsString().equals("true");
    assertEquals(joined?"villageastra:block/framed_window_glass_"+join:"villageastra:block/"+name+"_"+join,model,"Glass where it joins, the wood's frame where not: "+part);
    seen.add(axis+"/"+join+"="+joined);
    if(!joined){var piece=json("assets/villageastra/models/block/"+name+"_"+join+".json");
     assertEquals("villageastra:block/framed_window_"+join,piece.get("parent").getAsString());
     assertEquals(json("assets/villageastra/models/block/"+name+".json").getAsJsonObject("textures").get("frame"),piece.getAsJsonObject("textures").get("frame"),"One wood per window: "+piece);}}
   assertEquals(2*(2*JOINS.size()+1),seen.size(),"Every side and corner both ways on both axes: "+seen);
   var model=json("assets/villageastra/models/block/"+name+".json");
   assertEquals("villageastra:block/framed_window",model.get("parent").getAsString());
   assertTrue(model.getAsJsonObject("textures").get("frame").getAsString().matches("minecraft:block/stripped_"+wood+"_(log|stem|block)"),"The frame is the wood's stripped log: "+model);
   assertEquals("villageastra:block/"+name,json("assets/villageastra/models/item/"+name+".json").get("parent").getAsString(),"Item model = block model");
   var entry=json("data/villageastra/loot_tables/blocks/"+name+".json").getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
   assertEquals(id,entry.get("name").getAsString(),"Drops its own wood");
   var r=json("data/villageastra/recipes/"+name+"_from_wood.json");
   assertEquals("minecraft:crafting_shaped",r.get("type").getAsString());
   assertEquals(List.of("WWW","WPW","WWW"),r.getAsJsonArray("pattern").asList().stream().map(JsonElement::getAsString).toList());
   assertEquals(FramedWindows.TAG,r.getAsJsonObject("key").getAsJsonObject("W").get("tag").getAsString(),"Any window");
   var centre=r.getAsJsonObject("key").getAsJsonArray("P").toString();
   assertTrue(centre.contains("minecraft:"+wood+"_planks")&&centre.contains("\"tag\""),"Planks or a log of "+wood+": "+centre);
   assertEquals(id,r.getAsJsonObject("result").get("item").getAsString());assertEquals(8,r.getAsJsonObject("result").get("count").getAsInt());
   assertTrue(ru.has("block.villageastra."+name)&&ru.get("block.villageastra."+name).getAsString().startsWith("Окно в оконной раме"),"ru name of "+name);
   assertTrue(en.has("block.villageastra."+name)&&en.get("block.villageastra."+name).getAsString().endsWith("Framed Window"),"en name of "+name);
   assertEquals(5,json("data/villageastra/balance/levels.json").getAsJsonObject("value").get(id).getAsInt(),"Priced: "+id);}
  var tag=json("data/villageastra/tags/items/framed_windows.json").getAsJsonArray("values").asList().stream().map(JsonElement::getAsString).toList();
  assertEquals(FramedWindows.WOODS.stream().map(FramedWindows::id).toList(),tag);
  assertTrue(json("data/minecraft/tags/blocks/mineable/axe.json").getAsJsonArray("values").toString().contains("#"+FramedWindows.TAG));
 }
 /** The joins of FramedWindowBlock, by property name: four sides and four corners. */
 private static final List<String> JOINS=List.of("left","right","up","down","up_left","up_right","down_left","down_right");
 /** Every part template is cut out like glass; the frame pieces tile the whole 16x16 outline (no gap, no overlap), and the glass pieces fill
  *  what the frame leaves, all in the middle two pixels of the depth, sampling the glass inside its border (joined panes show no seam). */
 @Test void thePartsOfAJoinedWindowTileTheBlockFace() throws IOException {
  var covered=new int[16][16];
  for(var join:JOINS)for(var kind:List.of("framed_window_","framed_window_glass_")){
   var m=json("assets/villageastra/models/block/"+kind+join+".json");assertEquals("minecraft:cutout",m.get("render_type").getAsString(),kind+join);
   var el=m.getAsJsonArray("elements");assertEquals(1,el.size());var e=el.get(0).getAsJsonObject();
   var from=e.getAsJsonArray("from");var to=e.getAsJsonArray("to");
   if(kind.endsWith("glass_")){assertEquals(7,from.get(2).getAsInt());assertEquals(9,to.get(2).getAsInt(),"Glass in the middle of the depth");
    for(var face:e.getAsJsonObject("faces").entrySet())for(var u:face.getValue().getAsJsonObject().getAsJsonArray("uv"))assertTrue(u.getAsInt()>=2&&u.getAsInt()<=14,"Inside the glass border: "+join);}
   else{assertEquals(0,from.get(2).getAsInt());assertEquals(16,to.get(2).getAsInt(),"The frame through the whole depth");}
   // Frame and glass of one join cover the same cells of the face; count the frame's.
   if(kind.endsWith("glass_"))continue;
   for(int x=from.get(0).getAsInt();x<to.get(0).getAsInt();x++)for(int y=from.get(1).getAsInt();y<to.get(1).getAsInt();y++)covered[x][y]++;}
  var middle=json("assets/villageastra/models/block/framed_window_glass_middle.json").getAsJsonArray("elements").get(0).getAsJsonObject();
  for(int x=0;x<16;x++)for(int y=0;y<16;y++){boolean inside=x>=2&&x<14&&y>=2&&y<14;
   assertEquals(inside?0:1,covered[x][y],"The frame pieces cover the outline once: "+x+","+y);}
  assertEquals("[2,2,7]",middle.getAsJsonArray("from").toString());assertEquals("[14,14,9]",middle.getAsJsonArray("to").toString());
 }
 @Test void eightSticksRoundAPaneMakeOneDarkOakWindow() throws IOException {
  var r=json("data/villageastra/recipes/dark_oak_framed_window.json");
  assertEquals(List.of("SSS","SGS","SSS"),r.getAsJsonArray("pattern").asList().stream().map(JsonElement::getAsString).toList());
  assertEquals("minecraft:stick",r.getAsJsonObject("key").getAsJsonObject("S").get("item").getAsString());
  assertEquals("minecraft:glass_pane",r.getAsJsonObject("key").getAsJsonObject("G").get("item").getAsString());
  assertEquals(FramedWindows.id(FramedWindows.DEFAULT_WOOD),r.getAsJsonObject("result").get("item").getAsString());
  assertEquals(1,r.getAsJsonObject("result").get("count").getAsInt());
 }
 @Test void theCarpentryAndEngineeringTwoMakeThem() throws IOException {
  var shops=json("data/villageastra/balance/workshops.json").getAsJsonObject("workshops");
  assertTrue(shops.getAsJsonObject("carpentry").getAsJsonArray("output_tags").contains(new JsonPrimitive(FramedWindows.TAG)));
  assertTrue(shops.getAsJsonObject("town_hall").getAsJsonObject("research_outputs").getAsJsonArray("engineering.2").contains(new JsonPrimitive("#"+FramedWindows.TAG)));
 }
}
