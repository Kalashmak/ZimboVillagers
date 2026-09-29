package org.villageastra.domain;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import com.google.gson.*;
/** AD-104 P2, owner decision 2 of 2026-09-19: a village meal counts food in rations, so a plot feeds about the same whatever it grows —
 *  carrot 1, potato 1, beetroot 2; bread and baked potato 5 (balance/population.json "rations"). Safe food the table does not list keeps its
 *  vanilla nutrition. Only village meals read it (Population.nutrition); FoodProperties stay vanilla, so the player's own hunger is unchanged.
 *  Pure: no Minecraft classes, so JUnit checks the shipped table and the meal arithmetic the farm card uses. */
public final class Rations {
 /** A player's whole hunger bar: no single item may count for more. */
 public static final int MAX=20;
 /** An item id as ResourceLocation accepts it: a meal looks the table up by the item's registry name, so a bare or malformed id would never match.
  *  A well-formed id of an item that does not exist (a block id such as minecraft:carrots) is not caught here; RationsTest pins the shipped ids. */
 private static final Pattern ID=Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
 private Rations(){}
 /** The shipped table, read from the packaged balance the way Population reads it (for JUnit and pure callers). */
 public static Map<String,Integer> load(){try(var s=Rations.class.getResourceAsStream("/data/villageastra/balance/population.json")){if(s==null)throw new IllegalStateException("Missing population balance");return parse(JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("rations"));}catch(IOException e){throw new IllegalStateException(e);}}
 /** Item id → rations. A missing table, an id without its namespace or a value that is not a whole number 0..20 fails the load,
  *  instead of that food quietly feeding at its vanilla value. */
 public static Map<String,Integer> parse(JsonObject table){
  if(table==null)throw new IllegalArgumentException("Missing rations table");
  var out=new LinkedHashMap<String,Integer>();
  for(var entry:table.entrySet()){
   String id=entry.getKey();var v=entry.getValue();int n;
   if(!ID.matcher(id).matches())throw new IllegalArgumentException("Rations need a namespaced item id: "+id);
   if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Rations of "+id+" must be a number");
   try{n=v.getAsBigDecimal().intValueExact();}catch(ArithmeticException|NumberFormatException e){throw new IllegalArgumentException("Rations of "+id+" must be a whole number");}
   if(n<0||n>MAX)throw new IllegalArgumentException("Rations of "+id+" must be 0.."+MAX);
   out.put(id,n);
  }
  return Collections.unmodifiableMap(out);
 }
 /** Rations one item of safe food gives a village meal: the table's value, else its vanilla nutrition. */
 public static int value(Map<String,Integer> table,String id,int vanilla){return id==null?vanilla:table.getOrDefault(id,vanilla);}
 /** Whole items one meal takes of a food worth this many rations: Population.meal takes items until it has eaten a meal's worth, so at 5 a meal
  *  a carrot (1) costs 5, beetroot (2) costs 3 and bread (5) costs 1. 0 when the food makes no meal at all. Population.meal also asks it for the
  *  rest of a meal (mealNutrition = what is still missing), to take one slot's share in a single withdrawal. */
 public static int itemsPerMeal(int ration,int mealNutrition){if(mealNutrition<1)throw new IllegalArgumentException("A meal needs rations");return ration<=0?0:-Math.floorDiv(-mealNutrition,ration);}
 /** Residents a daily supply of one food feeds as meals really eat it: items a day / (meals a day × items a meal); nobody for food that makes no meal. */
 public static double residentsFed(double itemsPerDay,int ration,int mealNutrition,double mealsPerDay){int n=itemsPerMeal(ration,mealNutrition);return n==0||mealsPerDay<=0?0:itemsPerDay/(mealsPerDay*n);}
}
