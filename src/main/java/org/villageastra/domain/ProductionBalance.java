package org.villageastra.domain;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
/** Runtime numbers come from the packaged owner balance, not parallel constants in worker code. */
public final class ProductionBalance {
 private static final JsonObject ROOT=read();
 private ProductionBalance(){}
 private static JsonObject read(){try(var stream=ProductionBalance.class.getResourceAsStream("/data/villageastra/catalog/balance.json")){if(stream==null)throw new IllegalStateException("Missing production balance");return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException ex){throw new IllegalStateException(ex);}}
 public static int integer(String path){JsonElement value=ROOT;for(var key:path.split("\\."))value=value.getAsJsonObject().get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalStateException("Missing balance value "+path);var decimal=value.getAsBigDecimal();int n=decimal.intValueExact();if(n<1||n>100000)throw new IllegalStateException("Invalid balance value "+path);return n;}
 public static int recipe(String id,String section,String item){return integer("recipes."+id+"."+section+"."+item);}
 public static long workTicks(String id){return Math.multiplyExact(20L,integer("recipes."+id+"."+(id.equals("research_volume")?"work_seconds":"worker_seconds")));}
 public static int bookUses(){return integer("recipes.writing_tool.book_uses");}
 public static int baseBooks(){return integer("research.base_books");}
}
