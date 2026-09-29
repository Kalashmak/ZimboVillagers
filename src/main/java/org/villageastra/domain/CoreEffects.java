package org.villageastra.domain;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** AD-112: the numbers a core gives its building at each working level, read from balance/core_levels.json. Pure (classpath only),
 *  so the server's knobs, the client's tooltips and JUnit read the same table. Only an active effect has numbers; an inactive one is
 *  shown as "not yet active" and asking for its value is a defect. */
public final class CoreEffects {
 private CoreEffects(){}
 public static final int LEVELS=6;
 /** One effect of a core: its values at levels I..VI (null while it is not active), unit, and the test that holds it to its knob. */
 public record Effect(String id,int[] values,String unit,boolean active,String test){
  public int at(int level){if(values==null)throw new IllegalStateException("Core effect "+id+" is not active");return values[Math.max(1,Math.min(LEVELS,level))-1];}
 }
 /** The mine's floors by working level and the shape of its galleries. */
 public record Mine(int[] floorY,int minStepsPerLevel,int galleryLength,int galleryHeight,int beamEvery){
  public int floorY(int level){return floorY[Math.max(1,Math.min(LEVELS,level))-1];}
 }
 private static final JsonObject ROOT=read();
 private static final Map<String,List<Effect>> EFFECTS=effects();
 private static final Mine MINE=mine(ROOT.getAsJsonObject("mine"));
 private static final Map<String,Integer> RINGS=readRings();
 private static JsonObject read(){try(var s=CoreEffects.class.getResourceAsStream("/data/villageastra/balance/core_levels.json")){if(s==null)throw new IllegalStateException("Missing core levels balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static int[] ints(JsonArray a){var out=new int[a.size()];for(int i=0;i<out.length;i++)out[i]=a.get(i).getAsInt();return out;}
 private static Map<String,List<Effect>> effects(){
  var cores=ROOT.getAsJsonObject("cores");var out=new LinkedHashMap<String,List<Effect>>();
  for(var type:CoreCatalog.TYPES){var core=cores.getAsJsonObject(type);if(core==null)throw new IllegalStateException("Core levels miss "+type);
   if(!CoreCatalog.coreId(type).equals(core.get("core").getAsString()))throw new IllegalStateException("Core of "+type+" is "+core.get("core"));
   var list=new ArrayList<Effect>();
   for(var raw:core.getAsJsonArray("effects")){var o=raw.getAsJsonObject();boolean active=o.get("active").getAsBoolean();var v=o.get("values");
    // AD-139: an effect that is a crew (the restaurant's couriers) names its row of balance/staff.json ("type/trade") instead of copying it.
    var staff=o.get("staff");if(staff!=null)v=staff(staff.getAsString());
    int[] values=v==null||v.isJsonNull()?null:ints(v.getAsJsonArray());var test=o.get("test");
    if(active!=(values!=null))throw new IllegalStateException("Core effect "+type+"/"+o.get("id")+": an active effect has values, an inactive one none");
    if(values!=null&&values.length!=LEVELS)throw new IllegalStateException("Core effect "+type+"/"+o.get("id")+" needs "+LEVELS+" values");
    list.add(new Effect(o.get("id").getAsString(),values,o.get("unit").getAsString(),active,test==null||test.isJsonNull()?null:test.getAsString()));}
   out.put(type,List.copyOf(list));}
  return Collections.unmodifiableMap(out);
 }
 private static JsonArray staff(String row){var at=row.split("/");
  try(var s=CoreEffects.class.getResourceAsStream("/data/villageastra/balance/staff.json")){if(s==null)throw new IllegalStateException("Missing staff balance");
   var r=JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("slots").get(at[0]);
   if(r!=null&&r.isJsonObject()&&at.length==2&&r.getAsJsonObject().has(at[1]))return r.getAsJsonObject().getAsJsonArray(at[1]);
   throw new IllegalStateException("Staff has no row "+row);
  }catch(IOException e){throw new IllegalStateException(e);}}
 private static Mine mine(JsonObject m){var f=ints(m.getAsJsonArray("floor_y"));if(f.length!=LEVELS)throw new IllegalStateException("The mine needs "+LEVELS+" floors");
  return new Mine(f,m.get("min_steps_per_level").getAsInt(),m.get("gallery_length").getAsInt(),m.get("gallery_height").getAsInt(),m.get("beam_every").getAsInt());}
 private static Map<String,Integer> readRings(){var out=new LinkedHashMap<String,Integer>();for(var e:ROOT.getAsJsonObject("rings").entrySet())out.put(e.getKey(),e.getValue().getAsInt());return Collections.unmodifiableMap(out);}
 /** Every effect of the core a building type holds (the quarry's are the mine's); empty for a building without a core. */
 public static List<Effect> effects(String buildingType){var t=CoreCatalog.coreType(buildingType);return t==null?List.of():EFFECTS.get(t);}
 /** One effect of a building type's core, or null. */
 public static Effect effect(String buildingType,String effect){for(var e:effects(buildingType))if(e.id().equals(effect))return e;return null;}
 public static boolean active(String buildingType,String effect){var e=effect(buildingType,effect);return e!=null&&e.active();}
 /** The value of an active effect at a working level (clamped to I..VI). An unknown or inactive effect is a defect. */
 public static int value(String buildingType,String effect,int level){var e=effect(buildingType,effect);if(e==null)throw new IllegalArgumentException("No core effect "+effect+" for "+buildingType);return e.at(level);}
 public static Mine mine(){return MINE;}
 /** Ring item id → the grade it raises a core to. */
 public static Map<String,Integer> rings(){return RINGS;}
}
