package org.villageastra.domain;
import java.util.List;
/** AD-143 (owner 2026-09-23): furniture, a chair and a table per vanilla wood. Pure: no registry, so JUnit, the resource generator's checks
 *  and the client may use it. A chair (two planks, five sticks) seats one — a player or a resident rides an invisible seat on it; tables set
 *  side by side join into one board with legs only at its outer corners. */
public final class Furniture {
 private Furniture(){}
 public static final String NS="villageastra";
 /** Every vanilla 1.20.1 wood, in the order of the creative planks row (the framed window's list). */
 public static final List<String> WOODS=FramedWindows.WOODS;
 /** Item and block tags of every chair and every table: the carpentry's and the hall's output lists name them. */
 public static final String CHAIRS=NS+":chairs",TABLES=NS+":tables";
 /** The woods the village designs furnish with: spruce in the restaurant and the big house (a dark oak floor), dark oak in the cottage. */
 public static final String RESTAURANT_WOOD="spruce",HOME_WOOD="dark_oak",HOME_2_WOOD="spruce";
 /** The height of a chair's seat over its block, in blocks: a seated body's hips rest on it. */
 public static final double SEAT_HEIGHT=0.5;
 /** How many a recipe makes: one chair from two planks and five sticks, one table from three planks and two sticks. */
 public static final int CHAIR_YIELD=1,TABLE_YIELD=1;
 public static String chairPath(String wood){check(wood);return wood+"_chair";}
 public static String tablePath(String wood){check(wood);return wood+"_table";}
 public static String chairId(String wood){return NS+":"+chairPath(wood);}
 public static String tableId(String wood){return NS+":"+tablePath(wood);}
 /** The wood of a chair or table id, or null when the id is neither. */
 public static String wood(String id){if(id==null)return null;for(var w:WOODS)if(id.equals(chairId(w))||id.equals(tableId(w)))return w;return null;}
 public static boolean chair(String id){var w=wood(id);return w!=null&&id.equals(chairId(w));}
 public static boolean table(String id){var w=wood(id);return w!=null&&id.equals(tableId(w));}
 /** The vanilla planks block of a wood (its properties: colour, sound, hardness). */
 public static String planks(String wood){return "minecraft:"+wood+"_planks";}
 private static void check(String wood){if(!WOODS.contains(wood))throw new IllegalArgumentException("No furniture of "+wood);}
 /** The four legs of a table: a leg stands at a corner when neither side of that corner joins another table (north, east, south, west). */
 public static boolean[] legs(boolean n,boolean e,boolean s,boolean w){return new boolean[]{!n&&!w,!n&&!e,!s&&!e,!s&&!w};}
}
