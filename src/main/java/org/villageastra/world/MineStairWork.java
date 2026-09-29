package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;

/** Persistent stair orders, including partial deliveries and old, skipped rows. */
public final class MineStairWork {
 private MineStairWork(){}
 public static UUID takeId(CompoundTag t){int round=t.getInt("stairTakeRound");return Settlement.childId(t.getUUID("operation"),round==0?"stair_take":"stair_take/"+round);}
 public static Item stone(CompoundTag t){return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(t.getString("stairItem")));}
 public static void reconcile(ServerLevel l,CompoundTag t){
  var cargo=t.getList("cargo",Tag.TAG_COMPOUND);var take=WorldJournal.recoverAmount(l,takeId(t));
  if(!take.isEmpty()&&!t.getBoolean("stairTaken")){cargo=ResourceWorkGoal.carry(cargo,List.of(take),Blocks.AIR.defaultBlockState(),-1);t.putBoolean("stairTaken",true);}
  int placed=t.getInt("stairPlaced"),size=MineDrive.stairs(t.getInt("stairStep"),MineWork.shape(t)).size();
  if(placed<size&&WorldJournal.recoverExisting(l,Settlement.childId(t.getUUID("operation"),"stair/"+placed))!=null){cargo=ResourceWorkGoal.without(cargo,stone(t),1);t.putInt("stairPlaced",placed+1);}
  t.put("cargo",cargo);
 }
 public static int missing(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  if(!t.getString("stage").equals("stair"))return 0;int n=0;var cells=MineDrive.stairs(t.getInt("stairStep"),MineWork.shape(t));
  for(int i=t.getInt("stairPlaced");i<cells.size();i++){var p=MineWork.at(e,b,cells.get(i));if(l.hasChunkAt(p)&&l.getBlockState(p).isAir())n++;}
  return Math.max(0,n-ResourceWorkGoal.count(t.getList("cargo",Tag.TAG_COMPOUND),stone(t)));
 }
 /** Fresh quarry stone repairs its own access before entering the hall's construction reserve; borrowed tools return to the hall. */
 public static long[] deliveries(ServerLevel l,SettlementData.Entry e,UUID worker,ListTag cargo){
  long[] targets=new long[cargo.size()];Arrays.fill(targets,LogisticsRoutes.position(e,Workshops.hall(e)).asLong());
  var b=e.settlement().workplace(worker);if(b==null||!b.type().equals("mine"))return targets;
  var t=MineWork.read(l,b);if(!t.getString("stage").equals("stair"))return targets;var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)return targets;
  var stone=stone(t);int need=missing(l,e,b,t)-LogisticsRoutes.count(chest,s->s.is(stone))-PorterWork.reserved(l,e,b.id(),s->s.is(stone),true);
  for(int i=0;i<cargo.size();i++){var stack=ItemStack.of(cargo.getCompound(i));if(stack.is(stone)&&stack.getCount()<=need){targets[i]=LogisticsRoutes.position(e,b).asLong();need-=stack.getCount();}}
  return targets;
 }
 /** Scan only completed rows; never invent stairs beyond the excavation cursor or in unloaded terrain. */
 public static int repair(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  int end=Math.min(t.getInt("step"),4096);
  for(int row=Math.max(0,t.getInt("stairAudit"));row<end;row++){
   for(var cell:MineDrive.stairs(row,MineWork.shape(t))){var p=MineWork.at(e,b,cell);if(!l.hasChunkAt(p))return -1;if(l.getBlockState(p).isAir())return row;}
   t.putInt("stairAudit",row+1);
  }return -1;
 }
}
