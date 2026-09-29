package org.villageastra.domain;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
/** AD-136: the numbers of research v2 from balance/science.json — the price of a level in scientific works, the time one seated scientist
 *  takes to write one, the scientist places of each laboratory level and what a level-I price may be made of. No Minecraft types. */
public final class ScienceBalance {
 private ScienceBalance(){}
 private static final JsonObject ROOT=read();
 private static JsonObject read(){try(var s=ScienceBalance.class.getResourceAsStream("/data/villageastra/balance/science.json")){if(s==null)throw new IllegalStateException("Missing science balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static int positive(String key){int n=ROOT.get(key).getAsInt();if(n<1)throw new IllegalStateException("Invalid science balance "+key);return n;}
 /** Works a level costs per level above I (the owner's 7: II 7, III 14 … VI 35). */
 public static final int WORKS_PER_TIER=positive("works_per_tier");
 /** Village-clock ticks one seated scientist takes for one work (36000 = 30 minutes at 20 TPS). */
 public static final long WORK_TICKS=positive("work_ticks");
 /** The laboratory holds fewer works than this: porters bring more from the stock. */
 public static final int CARRY_TO_LAB=positive("carry_to_lab_max");
 /** Level-I nodes a mayor may have waiting for their resources at once. */
 public static final int RESOURCE_ORDERS=positive("resource_orders_max");
 private static final int[] SEATS=seats();
 private static int[] seats(){var a=ROOT.getAsJsonArray("seats");if(a.size()!=6)throw new IllegalStateException("Science seats need six levels");var v=new int[6];for(int i=0;i<6;i++){v[i]=a.get(i).getAsInt();if(v[i]<1||i>0&&v[i]<v[i-1])throw new IllegalStateException("Science seats must not drop");}return v;}
 /** Scientist places of a laboratory working at this level (1..6). */
 public static int seats(int level){return SEATS[Math.max(1,Math.min(6,level))-1];}
 /** The price of a node of this tier in works: 0 for level I (paid in resources), works_per_tier*(tier-1) above it. */
 public static int works(int tier){return tier<=1?0:Math.multiplyExact(WORKS_PER_TIER,tier-1);}
 /** Items ("minecraft:wheat") and tags ("#minecraft:logs") a level-I price may name (CF2). */
 public static final Set<String> TIER1_SOURCES=strings("tier1_sources");
 /** Level-I nodes an NPC mayor pays first, in this order, once their resources lie in the stock. */
 public static final List<String> MAYOR_PRIORITY=List.copyOf(strings("mayor_priority"));
 private static LinkedHashSet<String> strings(String key){var out=new LinkedHashSet<String>();for(var e:ROOT.getAsJsonArray(key))out.add(e.getAsString());return out;}
}
