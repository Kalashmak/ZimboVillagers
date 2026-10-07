package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.*;

/** Bring wild cane home as a paid living plant before relying on its renewable harvest. */
public final class ReedNursery {
 // Keep renewable plots near the village, including real shores beyond the first hillside.
 public static final int CAPACITY=8,RADIUS=120;
 private static final List<BlockPos> CELLS=new ArrayList<>();
 static{for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++)if(x*x+z*z<=RADIUS*RADIUS)CELLS.add(new BlockPos(x,0,z));CELLS.sort(Comparator.comparingDouble(p->p.distSqr(BlockPos.ZERO)));}
 private ReedNursery(){}
 private static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-reeds/"+village+".bin");}
 public static List<BlockPos> planted(ServerLevel l,UUID village){
  var file=path(l,village);var out=new ArrayList<BlockPos>();if(!Files.exists(file))return out;
  // Old records could count a paid upper segment as another plant. Keep the
  // real growing base; its already placed upper blocks remain untouched.
  for(long p:NbtRecord.read(file).getLongArray("plants")){var at=BlockPos.of(p);if(!l.hasChunkAt(at)||l.getBlockState(at).is(Blocks.SUGAR_CANE)&&!l.getBlockState(at.below()).is(Blocks.SUGAR_CANE))out.add(at);}return out;
 }
 public static void record(ServerLevel l,UUID village,BlockPos p){var plants=planted(l,village);if((!l.hasChunkAt(p)||!l.getBlockState(p.below()).is(Blocks.SUGAR_CANE))&&!plants.contains(p))plants.add(p.immutable());var t=new CompoundTag();t.putLongArray("plants",plants.stream().mapToLong(BlockPos::asLong).toArray());NbtRecord.write(path(l,village),t);}
 public static boolean safe(ServerLevel l,BlockPos p){
  return l.hasChunkAt(p)&&l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir()&&l.getBlockState(p.above(2)).isAir()
   &&!l.getBlockState(p.below()).is(Blocks.SUGAR_CANE)
   &&Blocks.SUGAR_CANE.defaultBlockState().canSurvive(l,p)
   &&!OwnershipEvents.protectedBlock(l,p)&&!OwnershipEvents.disallowedPlacement(l,p);
 }
 private enum Search { READY, PENDING, UNAVAILABLE }
 private static Search pending(CompoundTag t,int column,int y){t.putInt("nurseryCursor",column);t.putInt("nurseryY",y);return Search.PENDING;}
 private static Search plan(ResidentEntity worker,SettlementData.Entry e,CompoundTag t){
  var l=(ServerLevel)worker.level();if(planted(l,e.settlement().id()).size()>=CAPACITY)return Search.UNAVAILABLE;
  long deadline=System.nanoTime()+2_000_000L,plans=HarvestRouteCache.stats(worker).plans();int visited=0;
  // Only already loaded nearby shores; never dig a canal, replace soil, or force a permanent area.
  try(var sensing=HarvestRouteCache.survey(worker)){
  for(int index=Math.max(0,t.getInt("nurseryCursor"));index<CELLS.size();index++){
   if(visited++>=512||System.nanoTime()>=deadline||HarvestRouteCache.stats(worker).plans()-plans>=2){t.putInt("nurseryCursor",index);return Search.PENDING;}
   var column=e.center().offset(CELLS.get(index));if(!l.hasChunkAt(column)){t.remove("nurseryY");continue;}
   int top=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
   // A real shore may lie below a bank overhang. Cane needs soil, water and
   // three clear cells, not sky visibility; never cut away the covering bank.
   int start=t.contains("nurseryY")?Math.min(top,t.getInt("nurseryY")):top;t.remove("nurseryY");
   for(int y=start;y>=Math.max(l.getMinBuildHeight()+1,top-16);y--){
    if(System.nanoTime()>=deadline||HarvestRouteCache.stats(worker).plans()-plans>=2)return pending(t,index,y);
    var p=new BlockPos(column.getX(),y,column.getZ());if(!safe(l,p))continue;
    if(!ResourceExpedition.survey(worker,p))return pending(t,index,y);
    var stand=HarvestAccess.find(worker,p,NaturalSupplyGoal.ROUTE_RANGE);if(stand==null)continue;
    t.putLong("nurseryTarget",p.asLong());t.putLong("nurseryStand",stand.asLong());return Search.READY;
   }
  }}t.putInt("nurseryCursor",CELLS.size());return Search.UNAVAILABLE;
 }
 public record Harvest(BlockPos target,BlockPos stand){}
 /** Inspect only remembered loaded plots; ordinary random growth supplies their harvestable tops. */
 public static Harvest mature(ResidentEntity worker,SettlementData.Entry e){return mature(worker,e,NaturalSupplyGoal.reservedTargets((ServerLevel)worker.level(),e,worker.getUUID()));}
 public static Harvest mature(ResidentEntity worker,SettlementData.Entry e,Set<BlockPos> reserved){
  var l=(ServerLevel)worker.level();var roots=planted(l,e.settlement().id());roots.sort(Comparator.comparingDouble(p->p.distSqr(worker.blockPosition())));
  for(var root:roots.stream().limit(CAPACITY).toList()){
   if(!l.hasChunkAt(root))continue;var target=root;while(target.getY()<l.getMaxBuildHeight()-1&&l.getBlockState(target.above()).is(Blocks.SUGAR_CANE))target=target.above();
   if(target.equals(root)||reserved.contains(target)||!NaturalSupplyGoal.safe(l,target))continue;
   if(!ResourceExpedition.survey(worker,target))continue;var stand=HarvestAccess.find(worker,target,NaturalSupplyGoal.ROUTE_RANGE);if(stand!=null)return new Harvest(target,stand);
  }return null;
 }
 private static void save(ResidentEntity worker,CompoundTag t){NbtRecord.write(NaturalSupplyGoal.path((ServerLevel)worker.level(),worker.getUUID()),t);}
 public static void resume(CompoundTag t){if(t!=null&&t.contains("nurseryTarget")){t.remove("nurseryBest");t.putInt("nurseryTravel",0);}}
 /** Uses the first single cane in the existing carry record. Its delivery receipt is a placement, so custody cannot refund it twice. */
 public static boolean tick(ResidentEntity worker,SettlementData.Entry e,CompoundTag t){
  if(!t.getString("stage").equals("carry")||t.getInt("delivered")!=0)return false;
  var list=t.getList("cargo",Tag.TAG_COMPOUND);if(list.isEmpty())return false;var seed=ItemStack.of(list.getCompound(0));if(!seed.is(Items.SUGAR_CANE)||seed.getCount()!=1)return false;
  var l=(ServerLevel)worker.level();var receipt=Settlement.childId(t.getUUID("id"),"deliver/0");
  if(!t.getBoolean("nurseryChecked")){
   // Bring the real seed back by the normal cargo route first. A distant cliff
   // should not make every local nursery shore look unreachable from the mine.
   if(worker.blockPosition().distSqr(e.center())>32*32)return false;
   if(worker.tickCount%20!=0){worker.workStatus("seeking_materials");return true;}
   var search=plan(worker,e,t);t.putBoolean("nurseryChecked",search!=Search.PENDING);save(worker,t);
   if(search==Search.PENDING){worker.getNavigation().stop();worker.workStatus("seeking_materials");return true;}
  }
  if(!t.contains("nurseryTarget"))return false;var target=BlockPos.of(t.getLong("nurseryTarget"));var stand=BlockPos.of(t.getLong("nurseryStand"));
  // Settle an applied placement before checking a later terrain change or the nursery's current size.
  if(WorldJournal.recoverExisting(l,receipt)!=null){record(l,e.settlement().id(),target);t.putInt("delivered",1);save(worker,t);return true;}
  if(WorldJournal.exists(l,receipt)){worker.workStatus("changed_target");return true;}
  double distance=worker.distanceToSqr(stand.getX()+.5,stand.getY(),stand.getZ()+.5);
  double remaining=Math.sqrt(distance);if(!t.contains("nurseryBest")||remaining+.5<t.getDouble("nurseryBest")){t.putDouble("nurseryBest",remaining);t.putInt("nurseryTravel",0);}
  if(!safe(l,target)||!HarvestAccess.standing(l,stand,target)||planted(l,e.settlement().id()).size()>=CAPACITY||t.getInt("nurseryTravel")>2400){t.remove("nurseryTarget");t.remove("nurseryStand");save(worker,t);return false;}
  worker.displayWorkItem(seed);
  if(distance>.16){
   if(distance<2.25){worker.getNavigation().stop();worker.getMoveControl().setWantedPosition(stand.getX()+.5,stand.getY(),stand.getZ()+.5,.8);}
   else if(worker.tickCount%20==0&&ResourceExpedition.survey(worker,stand))worker.getNavigation().moveTo(worker.routeTo(stand,0,NaturalSupplyGoal.ROUTE_RANGE),.8);
   t.putInt("nurseryTravel",t.getInt("nurseryTravel")+1);if(worker.tickCount%20==0)save(worker,t);worker.workStatus("walking");return true;
  }
  worker.getNavigation().stop();worker.workStatus("working");if(worker.tickCount%20!=0)return true;
  t.putInt("nurseryLabor",t.getInt("nurseryLabor")+20);save(worker,t);if(t.getInt("nurseryLabor")<200)return true;
  if(WorldJournal.place(l,receipt,target,Blocks.AIR.defaultBlockState(),Blocks.SUGAR_CANE.defaultBlockState())){record(l,e.settlement().id(),target);t.putInt("delivered",1);save(worker,t);}
  return true;
 }
}
