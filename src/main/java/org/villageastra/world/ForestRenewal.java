package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.*;
import org.villageastra.server.*;

/** Level I only: when the reachable wood is exhausted, plant actual spare saplings on safe open soil. */
public final class ForestRenewal {
 private ForestRenewal(){}
 private static final class Budget {
  final long deadline=System.nanoTime()+5_000_000L;
  final long first;
  Budget(ResidentEntity worker){first=HarvestRouteCache.stats(worker).plans();}
  boolean exhausted(ResidentEntity worker){return System.nanoTime()>=deadline||HarvestRouteCache.stats(worker).plans()-first>=4;}
 }
 /** Resume the original ordered, individual default-range platforms without skipping a candidate. */
 private static int access(ResidentEntity worker,BlockPos foot,CompoundTag t,Budget budget){
  if(!t.contains("renewalFoot")||t.getLong("renewalFoot")!=foot.asLong()){t.putLong("renewalFoot",foot.asLong());t.putInt("renewalPlatform",0);}
  var platforms=HarvestAccess.platformOffsets();
  for(int i=t.getInt("renewalPlatform");i<platforms.size();i++){
   if(budget.exhausted(worker)){t.putBoolean("renewalPending",true);return -1;}
   var feet=foot.offset(platforms.get(i));
   if(HarvestAccess.standing(worker.level(),feet,foot)&&HarvestAccess.visible(worker,feet,foot)){
    var path=HarvestRouteCache.plan(worker,feet,0);t.putInt("renewalPlatform",i+1);
    if(HarvestAccess.survivesExtraction(path,foot)){clearAccess(t);return 1;}
   }else t.putInt("renewalPlatform",i+1);
  }clearAccess(t);return 0;
 }
 private static void clearAccess(CompoundTag t){t.remove("renewalFoot");t.remove("renewalPlatform");}
 private static boolean begin(ServerLevel l,ResidentEntity worker,CompoundTag t,Item kind,BlockPos foot,boolean soil){
  clearAccess(t);t.remove("renewalPending");t.remove("renewalWrapped");t.remove("renewalFallback");t.remove("renewalSoil");
  t.putLong("target",foot.asLong());t.putLongArray("plantCells",new long[]{foot.asLong()});t.putString("species",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(kind).toString());t.putBoolean("fromBare",true);t.remove("plantSource");
  if(soil){t.put("soilBefore",NbtUtils.writeBlockState(l.getBlockState(foot.below())));t.remove("soilHeld");t.remove("soilLabor");t.putString("stage","nursery_soil");}
  else t.putString("stage",ResourceWorkGoal.count(t.getList("cargo",Tag.TAG_COMPOUND),kind)>0?"replant":"sapling");
  t.putUUID("operation",UUID.randomUUID());return true;
 }
 private static final List<BlockPos> CELLS=new ArrayList<>();
 static{int r=ForestBalance.radius(1);for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(x*x+z*z<=r*r)CELLS.add(new BlockPos(x,0,z));CELLS.sort(Comparator.comparingDouble(p->p.distSqr(BlockPos.ZERO)));}
 public static boolean safe(ServerLevel l,BlockPos p){return l.getBlockState(p.below()).is(BlockTags.DIRT)&&space(l,p);}
 public static boolean sandy(ServerLevel l,BlockPos p){var ground=l.getBlockState(p.below());return (ground.is(net.minecraft.world.level.block.Blocks.SAND)||ground.is(net.minecraft.world.level.block.Blocks.RED_SAND))&&space(l,p);}
 private static boolean space(ServerLevel l,BlockPos p){
  if(!l.hasChunkAt(p)||!l.getBlockState(p).isAir()||!l.canSeeSky(p)||OwnershipEvents.protectedBlock(l,p)||OwnershipEvents.disallowedPlacement(l,p))return false;
  for(var q:BlockPos.betweenClosed(p.offset(-2,0,-2),p.offset(2,6,2))){
   if(!l.hasChunkAt(q)||OwnershipEvents.protectedBlock(l,q)||OwnershipEvents.disallowedPlacement(l,q))return false;
   var state=l.getBlockState(q);
   // Neighboring ground cover does not occupy the trunk and is left untouched by planting.
   if(!state.isAir()&&!state.is(net.minecraft.world.level.block.Blocks.GRASS)&&!state.is(net.minecraft.world.level.block.Blocks.TALL_GRASS)&&!state.is(net.minecraft.world.level.block.Blocks.FERN)&&!state.is(net.minecraft.world.level.block.Blocks.LARGE_FERN)&&!state.is(BlockTags.FLOWERS))return false;
  }
  return true;
 }
 public static boolean plan(ServerLevel l,SettlementData.Entry e,Settlement.Building hut,ResidentEntity worker,CompoundTag t,Set<Item> available){
  var project=HallUpgradeGoal.exists(l,e.settlement().id())?HallUpgradeGoal.headerView(l,e.settlement().id()):new CompoundTag();String wood=project.getString("wood");String preferred=wood.isEmpty()?t.getString("felledKind"):"minecraft:"+wood+"_sapling";
  // A single dark-oak sapling cannot grow; its four-cell planting remains the normal level-II replanting job.
  var kinds=available.stream().filter(i->i instanceof BlockItem&&i!=Items.DARK_OAK_SAPLING&&ForestWork.PLANTED.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString())).sorted(Comparator.<Item>comparingInt(i->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString().equals(preferred)?0:1).thenComparing(i->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString())).toList();
  t.remove("renewalPending");if(kinds.isEmpty()){clearAccess(t);return false;}var kind=kinds.get(0);var door=ForesterHut.door(e,hut);int cursor=t.getInt("renewalScan");
  try(var survey=HarvestRouteCache.survey(worker)){var budget=new Budget(worker);
  if(t.getBoolean("renewalFallback"))return fallback(l,worker,t,kind,budget);
  for(int n=0;n<256;n++){
   if(budget.exhausted(worker)){t.putBoolean("renewalPending",true);return false;}
   if(cursor>0&&Math.floorMod(cursor,CELLS.size())==0)t.putBoolean("renewalWrapped",true);var offset=CELLS.get(Math.floorMod(cursor,CELLS.size()));var column=door.offset(offset);t.putInt("renewalScan",++cursor);if(!l.hasChunkAt(column))continue;
   var foot=new BlockPos(column.getX(),l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ()),column.getZ());
   if(Math.abs(foot.getY()-door.getY())>ForestBalance.RISE)continue;
   boolean soil=!t.contains("renewalSoil")&&sandy(l,foot);boolean fertile=safe(l,foot)&&((BlockItem)kind).getBlock().defaultBlockState().canSurvive(l,foot);
   if(!soil&&!fertile)continue;int result=access(worker,foot,t,budget);
   if(result<0){t.putInt("renewalScan",cursor-1);return false;}
   if(result==0)continue;if(soil)t.putLong("renewalSoil",foot.asLong());if(fertile)return begin(l,worker,t,kind,foot,false);
  }
  // Try ordinary fertile sites for a complete bounded scan before spending soil.
  if(t.getBoolean("renewalWrapped")&&t.contains("renewalSoil")){t.putBoolean("renewalFallback",true);return fallback(l,worker,t,kind,budget);}
  t.remove("renewalWrapped");return false;
  }
 }
 private static boolean fallback(ServerLevel l,ResidentEntity worker,CompoundTag t,Item kind,Budget budget){
  var foot=BlockPos.of(t.getLong("renewalSoil"));
  if(sandy(l,foot)){int result=access(worker,foot,t,budget);if(result<0)return false;if(result>0)return begin(l,worker,t,kind,foot,true);}
  clearAccess(t);t.remove("renewalSoil");t.remove("renewalFallback");t.remove("renewalWrapped");return false;
 }
}
