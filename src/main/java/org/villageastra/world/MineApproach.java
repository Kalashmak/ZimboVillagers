package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** Route a surface worker through the real mouth before asking for a distant underground face. */
public final class MineApproach {
 private MineApproach(){}
 public static BlockPos waypoint(ResidentEntity worker,SettlementData.Entry e,Settlement.Building mine,CompoundTag state,BlockPos target){
  int descent=state.getInt("descent");if(descent<=0)return target;
  var dest=BuildingPlacement.local(e,mine,target);if(dest.getY()>-descent)return target;
  var here=BuildingPlacement.local(e,mine,worker.blockPosition());int x=here.getX(),z=here.getZ();
  // A path search from a lower branch can settle under the destination instead of
  // finding the stairs. Use the same physical ascent as an ordinary cargo delivery.
  var area=e.settlement().mineAreas().get(mine.id());
  // A half tread can briefly bring the feet to the destination's elevation
  // one row too early. Reach that gallery's stair landing before turning
  // sideways, instead of alternating between ascent and the lower branch.
  int targetRow=dest.getZ()-7;
  if(area!=null&&area.contains(x,here.getY(),z,0)&&targetRow>=0&&targetRow<=area.lastStep()
    &&Math.abs(dest.getY()+descent+targetRow)<=1
    &&(here.getY()<dest.getY()||x>=1&&x<=5&&z>dest.getZ()&&here.getY()<=dest.getY()+1))
   return BuildingPlacement.at(e,mine,3,1-targetRow-descent,dest.getZ());
  if(here.getY()<dest.getY()&&area!=null&&area.contains(x,here.getY(),z,0))return BuildingPlacement.at(e,mine,2,1,1);
  // Surface ground above a gallery is not an entrance, even when it is slightly below the lot.
  if(here.getY()>1-descent){
   boolean mouth=x>=2&&x<=4&&z>=1&&z<=6&&here.getY()<1;
   if(mouth&&z<6)return target; // The existing short shaft walker takes over here.
   if(!mouth){var entry=BuildingPlacement.at(e,mine,2,1,1);
   if(worker.distanceToSqr(entry.getX()+.5,entry.getY(),entry.getZ()+.5)>4)return entry;
   return BuildingPlacement.at(e,mine,3,-5,6);}
  }
  // Keep each path search on a short, already excavated section of the descending stair.
  int end=Math.min(state.getInt("step")-1,state.contains("floorStep")?state.getInt("floorStep"):state.getInt("step")-1);
  int toward=Math.min(dest.getZ(),7+end);
  if(x>=2&&x<=4&&z>=6&&z<toward-4){int next=Math.min(z+4,toward),row=next-7;
   if(row>=0&&row<state.getInt("step"))return BuildingPlacement.at(e,mine,3,1-row-descent,next);
  }
  return target;
 }
}
