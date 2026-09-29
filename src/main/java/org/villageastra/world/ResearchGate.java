package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.server.*;
/** AD-077, AD-136: what the research tree opens. The branches open the levels of their own building (AD-073; the town hall its own «Ратуша»
 *  branch); <b>roads</b> (AD-123, the owner's ladder) opens the village's roads — gravel inside the village (1), trails between villages (2),
 *  lamps and fenced corridors (3), cobblestone ×1.5 (4), bridges and tunnels on trails (5), stone bricks ×2, roads wider than three blocks and
 *  the machines' road repair (6). The machinery of a building is the top of its own ladder ({@link Automation}), not a branch of its own.
 *  The surface gate applies to new orders only: repairs keep the cell's own tier without a research check.
 *  Research opens a possibility; it never changes a block, hands out equipment or starts free production. */
public final class ResearchGate {
 private ResearchGate(){}
 public static Set<String> done(ServerLevel l,SettlementData.Entry e){return BookResearch.completed(e,BookResearch.inspect(l,e));}
 public static boolean has(ServerLevel l,SettlementData.Entry e,String node){return done(l,e).contains(node);}
 /** Research a settlement still lacks for these nodes. */
 public static List<String> missing(ServerLevel l,SettlementData.Entry e,List<String> nodes){
  var have=done(l,e);var out=new ArrayList<String>();
  for(var node:nodes)if(!have.contains(node))out.add(node);
  return out;
 }
 /** The research a building type takes before it may be ordered at all, from the progression catalogue. */
 /** AD-094: an archer tower of the wall needs archer stations, wherever it is ordered from. */
 public static List<String> forDesign(String design){if(BuildingBlueprints.base(design).equals(Walls.TOWER))return List.of("defense.2");var own=HousingLadder.designResearch(design);if(own!=null)return List.of(own);return BuildingTiers.research(BuildingBlueprints.base(design),1);}
 public static String designRefusal(ServerLevel l,SettlementData.Entry e,String design){
  // AD-136 (CF15, owner answer 1): one laboratory per village; its places grow with its level, a second one is refused.
  if(BuildingBlueprints.base(design).equals("laboratory")&&e.settlement().buildings().stream().anyMatch(b->b.type().equals("laboratory")))return "unique";
  return missing(l,e,forDesign(design)).isEmpty()?"":"research";
 }
 /** The research one road variant takes: its surface, its lamps and fence, and its width. */
 public static List<String> forRoad(int variant){
  var out=new ArrayList<String>();int surface=(variant>>2)&3;
  // AD-123: each surface names the rung of its own ladder: gravel roads.1, cobblestone roads.4, stone bricks roads.6.
  if(surface>=1)out.add("roads.1");
  if(surface==2)out.add("roads.4");
  if(surface>=3)out.add("roads.6");
  if(((variant>>4)&1)==1||((variant>>5)&1)==1)out.add("roads.3");
  if(MayorSurvey.width(variant)>3&&!out.contains("roads.6"))out.add("roads.6");
  return out;
 }
 public static String roadRefusal(ServerLevel l,SettlementData.Entry e,int variant){
  return missing(l,e,forRoad(variant)).isEmpty()?"":"research";
 }
}
