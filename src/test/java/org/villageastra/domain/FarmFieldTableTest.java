package org.villageastra.domain;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** AD-130: the farm's table in balance/farm_fields.json — the 18 fields numbered floor by floor, each level containing the one below, every
 *  upper field over a field of the floor below, all of it inside the barn's reserve, and the core's field/feeds/floors/farmers matching it. */
class FarmFieldTableTest {
 private static JsonObject read(String path){try(var s=FarmFieldTableTest.class.getResourceAsStream(path)){return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception e){throw new IllegalStateException(e);}}
 private static final JsonObject T=read("/data/villageastra/balance/farm_fields.json");
 private static List<int[]> modules(int level){var out=new ArrayList<int[]>();var levels=T.getAsJsonObject("levels");for(int l=1;l<=level;l++)for(var raw:levels.getAsJsonArray(String.valueOf(l))){var a=raw.getAsJsonArray();out.add(new int[]{a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()});}return out;}
 private static int[] ints(JsonElement e){return e.getAsJsonArray().asList().stream().mapToInt(JsonElement::getAsInt).toArray();}
 @Test void theEighteenFieldsNestAndStandOverEachOther(){
  assertEquals(2,T.get("schema").getAsInt());
  int[] count={1,4,6,12,18,18};for(int l=1;l<=6;l++)assertEquals(count[l-1],modules(l).size(),"fields at level "+l);
  var all=modules(6);var seen=new HashSet<String>();
  for(int i=0;i<all.size();i++){var m=all.get(i);assertTrue(seen.add(m[0]+","+m[1]+","+m[2]),"field "+(i+1)+" once");
   // Floor-major numbering: fields 1..6 on the ground, 7..12 on floor 1, 13..18 on floor 2, each floor in the ground's order.
   assertEquals(i/6,m[2],"field "+(i+1)+" floor");var g=all.get(i%6);assertTrue(g[0]==m[0]&&g[1]==m[1],"field "+(i+1)+" stands over field "+(i%6+1));}
  var reserve=ints(T.get("reserve"));var origin=ints(T.get("origin"));int module=T.get("module").getAsInt();
  for(var m:all){int x0=origin[0]+module*m[0],z0=origin[1]+module*m[1];
   assertTrue(x0>reserve[0]&&x0+module-1<reserve[2]&&z0>reserve[1]&&z0+module-1<reserve[3],"field inside the barn walls of the reserve: "+Arrays.toString(m));}
  // A west farm mirrors x -> 6-x about its farmhouse: the reserve stays 22 wide either way.
  assertEquals(22,reserve[2]-reserve[0]+1);assertEquals(38,reserve[3]-reserve[1]+1);
  assertArrayEquals(OrganicLots.farmReserve(false),reserve,"OrganicLots keeps the same footprint");
 }
 @Test void theCoreTableIsTheFieldTable(){
  var farm=CoreEffects.effects("farm");int[] floors={1,1,1,2,3,3};
  for(int level=1;level<=6;level++){var mods=modules(level);
   assertEquals(mods.size(),CoreEffects.value("farm","field",level));
   int f=(int)mods.stream().mapToInt(m->m[2]).distinct().count();assertEquals(floors[level-1],f);assertEquals(f,CoreEffects.value("farm","floors",level));
   // Every floor grows on its own (a deck parts them): wheat a day is the sum over floors; feeds = floor(wheat / 5), at least the owner's target.
   double wheat=0;for(int fl=0;fl<3;fl++){var plots=new ArrayList<int[]>();for(var m:mods)if(m[2]==fl)for(int x=0;x<9;x++)for(int z=0;z<9;z++)if(!(x==4&&z==4))plots.add(new int[]{9*m[0]+x,9*m[1]+z});if(!plots.isEmpty())wheat+=FarmYield.wheatPerDay(plots,3);}
   int feeds=FarmYield.ratedResidents(wheat,T.get("rating_divisor").getAsInt());
   assertEquals(feeds,CoreEffects.value("farm","feeds",level),"feeds at level "+level);
   assertTrue(feeds>=T.getAsJsonObject("targets").get(String.valueOf(level)).getAsInt(),"level "+level+" feeds at least the owner's target");}
  assertArrayEquals(new int[]{1,1,1,2,3,0},ints(T.get("farmers")),"farmers 1/1/1/2/3 and none at VI (owner 2026-09-22)");
  assertArrayEquals(new int[]{0,0,0,0,0,18},CoreEffects.effect("farm","machine_fields").values());
 }
}
