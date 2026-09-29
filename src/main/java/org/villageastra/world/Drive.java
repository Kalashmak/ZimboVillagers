package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-055: a water wheel at the mill and a chain shaft to a workshop — a real line of blocks that lets the station work without a pair of hands. */
public final class Drive {
 /** Longest shaft a settlement keeps true without Mechanics I–III (AD-123: {@link ResearchKnobs#driveReach}), and the offset of a drive post inside any building. */
 public static final int REACH=24;
 public static final BlockPos POST=new BlockPos(1,1,1);
 private Drive(){}
 private static BlockPos post(SettlementData.Entry e,Settlement.Building b){return BuildingPlacement.at(e,b,POST.getX(),POST.getY(),POST.getZ());}
 public static BlockPos postOf(SettlementData.Entry e,Settlement.Building b){return post(e,b);}
 /** The mill's wheel: a real wheel block standing in flowing or still water beside the mill's drive post. */
 public static boolean wheel(ServerLevel l,SettlementData.Entry e){
  var mill=e.settlement().buildings().stream().filter(b->b.type().equals("mill")).findFirst().orElse(null);
  if(mill==null)return false;
  var at=post(e,mill);
  if(!l.getBlockState(at).is(Blocks.CHAIN))return false;
  for(var side:Direction.values()){
   var neighbour=at.relative(side);
   if(!l.getBlockState(neighbour).getFluidState().isEmpty())return true;
  }
  return false;
 }
 /** True when an unbroken chain shaft runs from the mill's post to this building's post. */
 public static boolean driven(ServerLevel l,SettlementData.Entry e,Settlement.Building building){
  if(building.type().equals("mill"))return wheel(l,e);
  if(!wheel(l,e))return false;
  var mill=e.settlement().buildings().stream().filter(b->b.type().equals("mill")).findFirst().orElse(null);
  if(mill==null)return false;
  BlockPos from=post(e,mill),to=post(e,building);int reach=ResearchKnobs.driveReach(l,e);
  if(from.distManhattan(to)>reach)return false;
  // The shaft is followed block by block: one missing chain and the station stands still.
  var seen=new HashSet<Long>();var queue=new ArrayDeque<BlockPos>();
  queue.add(from);seen.add(from.asLong());
  while(!queue.isEmpty()){
   var at=queue.poll();
   if(at.equals(to))return true;
   for(var side:Direction.values()){
    var next=at.relative(side);
    if(!seen.add(next.asLong()))continue;
    if(next.distManhattan(from)>reach)continue;
    if(next.equals(to)||l.getBlockState(next).is(Blocks.CHAIN))queue.add(next);
   }
  }
  return false;
 }
 /** Stations of a settlement that a shaft really drives. */
 public static List<Settlement.Building> stations(ServerLevel l,SettlementData.Entry e){
  var out=new ArrayList<Settlement.Building>();
  if(!wheel(l,e))return out;
  for(var b:e.settlement().buildings())if(Workshops.spec(b.type())!=null&&driven(l,e,b))out.add(b);
  return out;
 }
}
