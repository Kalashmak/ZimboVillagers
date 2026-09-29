package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.server.SettlementData;
/** AD-121 step 4 (owner: "V/VI close the gate and the portcullis, residents hide inside"): a castle hall of level V or more lets its
 *  portcullis down while its village is raided or besieged - iron bars across the passage under the half-raised grate (CastlePlan
 *  x 17..19, y 1..2, z 3) - and draws it up again when the danger is over. Population.tick calls it every 20 ticks. */
public final class CastleGate {
 private CastleGate(){}
 public static final int LEVEL=5,GRATE_Z=3,GRATE_X0=17,GRATE_X1=19;
 /** Whether the village is in danger now: a raid on it or an army's ring round it. */
 public static boolean danger(ServerLevel l,SettlementData.Entry e){return Raids.active(l,e.settlement().id())||Sieges.besieged(l.getServer(),e.settlement().id());}
 /** Lets the portcullis down or draws it up as the danger says. Returns whether it stands closed. */
 public static boolean tick(ServerLevel l,SettlementData.Entry e){
  if(!HallSite.castle(e.settlement()))return false;var hall=Workshops.hall(e);if(hall==null||BuildingTiers.level(l,e,hall)<LEVEL)return false;
  return set(l,HallSite.base(e),danger(l,e));
 }
 /** Sets the grate of a castle whose lot corner is {@code corner}: down (bars in the passage) or up (the passage open). */
 public static boolean set(ServerLevel l,BlockPos corner,boolean down){
  for(int x=GRATE_X0;x<=GRATE_X1;x++){var top=corner.offset(x,3,GRATE_Z);if(!l.hasChunkAt(top))return false;var bars=l.getBlockState(top);if(!bars.is(Blocks.IRON_BARS))continue;
   for(int y=1;y<=2;y++){var at=corner.offset(x,y,GRATE_Z);var now=l.getBlockState(at);
    if(down&&now.isAir())l.setBlock(at,bars,3);else if(!down&&now.is(Blocks.IRON_BARS))l.setBlock(at,Blocks.AIR.defaultBlockState(),3);}}
  return down;
 }
}
