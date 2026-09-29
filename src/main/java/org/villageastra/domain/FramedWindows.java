package org.villageastra.domain;
import java.util.List;
/** AD-142 (owner 2026-09-23): the framed window, one block and item per vanilla wood — a glass pane in a frame of that wood. Pure: no
 *  registry, so JUnit, the resource generator's checks and the client may use it. Eight sticks round a glass pane make the default (dark oak,
 *  the village's timber); eight windows of any wood round a log or planks of a wood make eight windows of that wood. */
public final class FramedWindows {
 private FramedWindows(){}
 public static final String NS="villageastra";
 /** Every vanilla 1.20.1 wood, in the order of the creative planks row. */
 public static final List<String> WOODS=List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","bamboo","crimson","warped");
 /** The wood the sticks recipe makes: the village's dark oak timber. */
 public static final String DEFAULT_WOOD="dark_oak";
 /** Item tag of every framed window: the recolour recipe takes any of them. */
 public static final String TAG=NS+":framed_windows";
 /** Registry path of a wood's window, e.g. dark_oak_framed_window. */
 public static String path(String wood){if(!WOODS.contains(wood))throw new IllegalArgumentException("No framed window of "+wood);return wood+"_framed_window";}
 public static String id(String wood){return NS+":"+path(wood);}
 /** The wood of a window id, or null when the id is no framed window. */
 public static String wood(String id){if(id==null)return null;for(var w:WOODS)if(id.equals(id(w)))return w;return null;}
 /** The vanilla planks block of a wood (its properties: colour, sound, hardness). */
 public static String planks(String wood){return "minecraft:"+wood+"_planks";}
}
