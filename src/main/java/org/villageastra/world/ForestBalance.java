package org.villageastra.world;
import com.google.gson.*;
import java.util.*;
/** AD-131: the numbers of the forester's hut I..VI (balance/forester.json, schema 1), checked for range when the class loads. */
public final class ForestBalance {
 private ForestBalance(){}
 public static final int SCAN_COLUMNS,LEAVES_MIN,MAX_LOGS,CROWN_LEAVES_MAX,NEXT_TREE_REACH,BARE_FEET_MAX,SAPLING_STOCK;
 /** How far above or below the hut door a tree of his may stand: his reach is a cylinder, not the whole column of the world. */
 public static final int RISE;
 public static final int SAW_FROM,PLANKS_PER_LOG,LOG_KEEP,PLANK_TARGET,GROW_TICKS,SAW_TICKS_PER_LOG,GROVE_TURN;
 private static final int[] RADIUS,TREES_PER_TRIP,SAW_PERIOD;private static final double[] WALK_SPEED;
 /** The six grove cells of the level-VI courtyard, hut-local {x,z}. */
 public static final List<int[]> GROVE;
 /** The kinds a grove raises: compact 1x1 trees only. */
 public static final List<String> GROVE_SPECIES;
 static{
  JsonObject o;
  try(var s=ForestBalance.class.getResourceAsStream("/data/villageastra/balance/forester.json")){if(s==null)throw new IllegalStateException("Missing forester balance");o=JsonParser.parseReader(new java.io.InputStreamReader(s,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
  catch(java.io.IOException e){throw new IllegalStateException(e);}
  if(o.get("schema").getAsInt()!=1)throw new IllegalStateException("Unsupported forester balance");
  RADIUS=ints(o,"radius");TREES_PER_TRIP=ints(o,"trees_per_trip");var ws=o.getAsJsonArray("walk_speed");WALK_SPEED=new double[ws.size()];for(int i=0;i<ws.size();i++)WALK_SPEED[i]=ws.get(i).getAsDouble();
  RISE=o.get("rise").getAsInt();SCAN_COLUMNS=o.get("scan_columns").getAsInt();LEAVES_MIN=o.get("leaves_min").getAsInt();MAX_LOGS=o.get("max_logs").getAsInt();CROWN_LEAVES_MAX=o.get("crown_leaves_max").getAsInt();
  NEXT_TREE_REACH=o.get("next_tree_reach").getAsInt();BARE_FEET_MAX=o.get("bare_feet_max").getAsInt();SAPLING_STOCK=o.get("sapling_stock").getAsInt();
  var saw=o.getAsJsonObject("saw");SAW_FROM=saw.get("from").getAsInt();PLANKS_PER_LOG=saw.get("planks_per_log").getAsInt();SAW_PERIOD=ints(saw,"period");LOG_KEEP=saw.get("log_keep").getAsInt();PLANK_TARGET=saw.get("plank_target").getAsInt();
  var g=o.getAsJsonObject("grove");GROW_TICKS=g.get("grow_ticks").getAsInt();SAW_TICKS_PER_LOG=g.get("saw_ticks_per_log").getAsInt();GROVE_TURN=g.get("turn").getAsInt();
  var cells=new ArrayList<int[]>();for(var c:g.getAsJsonArray("cells")){var a=c.getAsJsonArray();cells.add(new int[]{a.get(0).getAsInt(),a.get(1).getAsInt()});}GROVE=List.copyOf(cells);
  var kinds=new ArrayList<String>();for(var k:g.getAsJsonArray("species"))kinds.add(k.getAsString());GROVE_SPECIES=List.copyOf(kinds);
  boolean ok=RADIUS.length==6&&TREES_PER_TRIP.length==6&&WALK_SPEED.length==6&&SAW_PERIOD.length==6&&RISE>=8&&RISE<=64&&SCAN_COLUMNS>=16&&SCAN_COLUMNS<=4096&&LEAVES_MIN>=1&&MAX_LOGS>=4&&MAX_LOGS<=256
   &&CROWN_LEAVES_MAX>=0&&CROWN_LEAVES_MAX<=256&&NEXT_TREE_REACH>=2&&BARE_FEET_MAX>=1&&BARE_FEET_MAX<=64&&SAPLING_STOCK>=1&&SAPLING_STOCK<=64&&SAW_FROM>=2&&SAW_FROM<=6
   &&PLANKS_PER_LOG>=1&&PLANKS_PER_LOG<=16&&LOG_KEEP>=0&&PLANK_TARGET>0&&GROW_TICKS>=20&&SAW_TICKS_PER_LOG>=1&&GROVE_TURN>=1&&GROVE.size()==6&&!GROVE_SPECIES.isEmpty();
  for(int i=0;i<6&&ok;i++)ok=RADIUS[i]>=8&&RADIUS[i]<=48&&TREES_PER_TRIP[i]>=1&&TREES_PER_TRIP[i]<=8&&WALK_SPEED[i]>0&&WALK_SPEED[i]<=2&&(i+1<SAW_FROM?SAW_PERIOD[i]==0:SAW_PERIOD[i]>0);
  if(!ok)throw new IllegalStateException("Invalid forester balance");
 }
 private static int[] ints(JsonObject o,String key){var a=o.getAsJsonArray(key);var out=new int[a.size()];for(int i=0;i<out.length;i++)out[i]=a.get(i).getAsInt();return out;}
 private static int at(int level){return Math.max(1,Math.min(6,level))-1;}
 /** How far from the hut door a forester of this working level fells wild trees (blocks, horizontal). */
 public static int radius(int level){return RADIUS[at(level)];}
 public static int treesPerTrip(int level){return TREES_PER_TRIP[at(level)];}
 public static double walkSpeed(int level){return WALK_SPEED[at(level)];}
 /** Ticks between two logs the sawmill cuts at this level (0: no saw). */
 public static int sawPeriod(int level){return SAW_PERIOD[at(level)];}
 /** The largest search radius of any level: the spiral is laid out once for it. */
 public static int maxRadius(){int m=0;for(int r:RADIUS)m=Math.max(m,r);return m;}
}
