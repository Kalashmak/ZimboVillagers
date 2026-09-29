package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-138 V (owner 2026-09-22: "animals may leave the pen and graze on village land when their food runs out; at day's end the wolves
 *  drive them back into the pens"; spec §6). A pen is out to graze while its yard works at level V or more, the village knows
 *  livestock.5, it is day, its feeder is empty and the yard chest holds none of its feed — the real state of the world, no timer. Then its
 *  keeper keeps the gate open and the herd (GrazeGoal) walks out onto the village's grass: within the village's land (its buildings' plots,
 *  {@link #margin} blocks beyond), never on a farm's worked soil, a protected block or into another pen. At dusk, or once the feed is back,
 *  every beast walks home to its pen; the kennel wolves run behind the stragglers and the keeper drives in whoever is left. */
public final class LivestockGrazing {
 private LivestockGrazing(){}
 public static final String RESEARCH="livestock.5";
 /** Blocks beyond the village's plots the herd may still graze. */
 public static final int margin=6;
 /** The yard of a tagged beast and its pen, or null. */
 public record Home(SettlementData.Entry entry,Settlement.Building yard,LivestockPens.Pen pen){}
 public static Home home(ServerLevel l,Animal a){
  var t=a.getPersistentData();if(!t.hasUUID(LivestockGoal.OWNER)||!t.hasUUID(LivestockPens.YARD_TAG))return null;
  var e=SettlementData.get(l.getServer()).entry(t.getUUID(LivestockGoal.OWNER));if(e==null)return null;var id=t.getUUID(LivestockPens.YARD_TAG);
  var yard=e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElse(null);var pen=LivestockPens.pen(t.getInt(LivestockPens.PEN_TAG));
  return yard==null||pen==null?null:new Home(e,yard,pen);
 }
 /** The yard may send its pens out: level V or more and the research known. */
 public static boolean allowed(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){
  return BuildingLevels.level(l,e,yard)>=5&&ResearchKnobs.done(l,e).contains(RESEARCH);
 }
 /** The pen's feed has run out: its feeder is empty and the yard chest holds none of its kind's feed. */
 public static boolean hungry(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,LivestockPens.Pen p){
  var f=l.getBlockState(LivestockPens.at(e,yard,p.feeder()));if(!(f.getBlock() instanceof FeederBlock)||f.getValue(FeederBlock.FEED)>0)return false;
  var chest=LogisticsRoutes.chest(l,e,yard);var feed=LivestockPens.feed(LivestockGoal.species(l,e,yard,p));
  return chest==null||LogisticsRoutes.count(chest,s->s.is(feed))==0;
 }
 /** The pen is out to graze now. */
 public static boolean out(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,LivestockPens.Pen p){
  return !SleepGoal.night(l)&&allowed(l,e,yard)&&hungry(l,e,yard,p);
 }
 /** How far from the village centre its land reaches: the farthest edge of its buildings' plots, and the margin. */
 public static double reach(SettlementData.Entry e){
  double best=0;var c=e.center();for(var p:Walls.extent(e)){double dx=p[0]-c.getX(),dz=p[1]-c.getZ();best=Math.max(best,Math.sqrt(dx*dx+dz*dz));}
  return best+margin;
 }
 /** A cell a grazing beast may stand on: grass under it, on the village's land, not a farm's worked soil, not protected, in no pen. */
 public static boolean pasture(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,BlockPos feet,double reach,Set<BlockPos> farmed){
  var c=e.center();double dx=feet.getX()-c.getX(),dz=feet.getZ()-c.getZ();if(dx*dx+dz*dz>reach*reach)return false;
  var ground=feet.below();if(!l.getBlockState(ground).is(Blocks.GRASS_BLOCK)||!l.getBlockState(feet).isAir()||!l.getBlockState(feet.above()).isAir())return false;
  if(farmed.contains(ground)||farmed.contains(feet)||org.villageastra.server.OwnershipEvents.protectedBlock(l,feet))return false;
  for(var p:LivestockPens.all())if(LivestockPens.fenced(e,yard,p,feet))return false;
  return true;
 }
 /** A pasture cell some blocks from where the beast stands, or null after a few tries. */
 public static BlockPos spot(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,BlockPos from,Random r){
  double reach=reach(e);var farmed=new HashSet<>(FarmField.workedCells(l,e));
  for(int i=0;i<16;i++){var at=from.offset(r.nextInt(17)-8,0,r.nextInt(17)-8);
   var top=l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at);
   if(pasture(l,e,yard,top,reach,farmed))return top;}
  return null;
 }
 /** Gives a beast of a yard its grazing life once: when it is taken into a pen and whenever it joins a level (the goal is not saved). */
 public static void attach(Animal a){
  if(!a.getPersistentData().hasUUID(LivestockPens.YARD_TAG)||a.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof GrazeGoal))return;
  a.goalSelector.addGoal(4,new GrazeGoal(a));
 }
}
