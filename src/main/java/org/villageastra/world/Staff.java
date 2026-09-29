package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** AD-138: how many workers a workplace takes on its own by the level it is kept at (balance/staff.json); a type not named takes one.
 *  The farm keeps its own rule (FarmField.farmers, AD-130). One table, so every building with a crew adds a row, not code.
 *  AD-139: a row is either the counts of the workplace's own trade, or an object whose "workers" are its own trade and whose other
 *  keys (a Profession, lower case) are the other trades it posts — the restaurant's cook and its couriers (porters) are one row. */
public final class Staff {
 private Staff(){}
 /** Own trade's counts I..VI, and the other trades a workplace posts. */
 private record Row(int[] workers,Map<String,int[]> others){}
 private static final Map<String,Row> SLOTS=read();
 private static int[] levels(String what,JsonElement raw){var a=raw.getAsJsonArray();
  if(a.size()!=BuildingTiers.MAX)throw new IllegalStateException("Staff of "+what+" needs "+BuildingTiers.MAX+" levels");var v=new int[a.size()];for(int i=0;i<v.length;i++)v[i]=a.get(i).getAsInt();return v;}
 private static Map<String,Row> read(){
  try(var s=Staff.class.getResourceAsStream("/data/villageastra/balance/staff.json")){if(s==null)throw new IllegalStateException("Missing staff balance");
   var out=new HashMap<String,Row>();
   for(var e:JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("slots").entrySet()){
    if(e.getValue().isJsonArray()){out.put(e.getKey(),new Row(levels(e.getKey(),e.getValue()),Map.of()));continue;}
    var o=e.getValue().getAsJsonObject();if(!o.has("workers"))throw new IllegalStateException("Staff of "+e.getKey()+" names no workers");var others=new LinkedHashMap<String,int[]>();
    for(var t:o.entrySet())if(!t.getKey().equals("workers")){org.villageastra.domain.Profession.valueOf(t.getKey().toUpperCase(Locale.ROOT));others.put(t.getKey(),levels(e.getKey()+"/"+t.getKey(),t.getValue()));}
    out.put(e.getKey(),new Row(levels(e.getKey(),o.get("workers")),Collections.unmodifiableMap(others)));}
   return Map.copyOf(out);
  }catch(IOException ex){throw new IllegalStateException(ex);}
 }
 private static int at(int[] v,int level){return v[Math.max(1,Math.min(v.length,level))-1];}
 /** Workers of a workplace type's own trade at a kept level. */
 public static int slots(String type,int level){var r=SLOTS.get(type);return r==null?1:at(r.workers(),level);}
 /** Workers of another trade (a Profession, lower case) a workplace type posts at a kept level; 0 where its row names none. */
 public static int slots(String type,String trade,int level){var r=SLOTS.get(type);var v=r==null?null:r.others().get(trade);return v==null?0:at(v,level);}
 /** The other trades a workplace type posts besides its own (the restaurant's porters: its couriers). */
 public static Set<String> others(String type){var r=SLOTS.get(type);return r==null?Set.of():r.others().keySet();}
}
