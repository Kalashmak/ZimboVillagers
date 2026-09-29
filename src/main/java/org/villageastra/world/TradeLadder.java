package org.villageastra.world;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.CoreEffects;
import org.villageastra.server.SettlementData;
/** AD-160, the external trade ladder (owner's ladder of 2026-09-23, the caravans renamed): what the levels of a village's trade centre
 *  add. This step is level **II** — trade at a distance is only with villages the village has really met and that keep a centre of their
 *  own: «remote trade with other villages (you must meet them first and they must have the same centre)».
 *  <p>Meeting is the cartographers' work (AD-158 III): a village knows of a neighbour when its own second cartographer has found it, or
 *  when an envoy of its own has been there. A mayor's own contract is not touched by this: what the mayor arranges by hand, they arrange. */
public final class TradeLadder {
 private TradeLadder(){}
 public static final String CORE="caravan",CENTRE="caravan";
 /** What the knob of the ladder gives at that level of the centre (the registry of the core effects asks this). */
 public static int on(String effect,int level){return level>0&&CoreEffects.active(CORE,effect)?CoreEffects.value(CORE,effect,level):0;}
 /** The best working level of this village's trade centres, or 0 without one. */
 public static int level(ServerLevel l,SettlementData.Entry e){return BuildingLevels.best(l,e,CENTRE);}
 /** II: the centre trades with villages away from home — and only with those, by the rule below. */
 public static boolean remote(ServerLevel l,SettlementData.Entry e){
  if(!CoreEffects.active(CORE,"remote"))return false;
  int level=level(l,e);return level>0&&CoreEffects.value(CORE,"remote",level)>0;
 }
 /** Whether these two villages may trade at a distance of their own accord: both keep a centre, and the one being served knows the other
  *  (AD-158). Without the ladder's knob — a world without the trade ladder — nothing is refused, and the caravans work as they did. */
 public static boolean mayTrade(ServerLevel l,SettlementData.Entry destination,SettlementData.Entry source){
  if(destination==null||source==null)return false;
  if(!CoreEffects.active(CORE,"remote"))return true;
  // A village with no centre of its own is not in this trade at all; one whose centre is still at its first level trades at home.
  if(!remote(l,destination)||level(l,source)<=0)return false;
  return CartographyLadder.knows(l,destination.settlement().id(),source.settlement().id())
   ||CartographyLadder.knows(l,source.settlement().id(),destination.settlement().id());
 }
}
