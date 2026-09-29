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
 private static final List<BlockPos> CELLS=new ArrayList<>();
 static{int r=ForestBalance.radius(1);for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(x*x+z*z<=r*r)CELLS.add(new BlockPos(x,0,z));CELLS.sort(Comparator.comparingDouble(p->p.distSqr(BlockPos.ZERO)));}
 public static boolean safe(ServerLevel l,BlockPos p){
  if(!l.hasChunkAt(p)||!l.getBlockState(p).isAir()||!l.getBlockState(p.below()).is(BlockTags.DIRT)||!l.canSeeSky(p)||OwnershipEvents.protectedBlock(l,p)||OwnershipEvents.disallowedPlacement(l,p))return false;
  for(var q:BlockPos.betweenClosed(p.offset(-2,0,-2),p.offset(2,6,2)))if(!l.hasChunkAt(q)||!l.getBlockState(q).isAir()||OwnershipEvents.protectedBlock(l,q)||OwnershipEvents.disallowedPlacement(l,q))return false;
  return true;
 }
 public static boolean plan(ServerLevel l,SettlementData.Entry e,Settlement.Building hut,ResidentEntity worker,CompoundTag t,Set<Item> available){
  var project=HallUpgradeGoal.exists(l,e.settlement().id())?HallUpgradeGoal.inspect(l,e.settlement().id()):new CompoundTag();String wood=project.getString("wood");String preferred=wood.isEmpty()?t.getString("felledKind"):"minecraft:"+wood+"_sapling";
  // A single dark-oak sapling cannot grow; its four-cell planting remains the normal level-II replanting job.
  var kinds=available.stream().filter(i->i instanceof BlockItem&&i!=Items.DARK_OAK_SAPLING&&ForestWork.PLANTED.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString())).sorted(Comparator.<Item>comparingInt(i->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString().equals(preferred)?0:1).thenComparing(i->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString())).toList();
  if(kinds.isEmpty())return false;var kind=kinds.get(0);var door=ForesterHut.door(e,hut);int cursor=t.getInt("renewalScan");
  for(int n=0;n<256;n++){
   var offset=CELLS.get(Math.floorMod(cursor++,CELLS.size()));t.putInt("renewalScan",cursor);var column=door.offset(offset);if(!l.hasChunkAt(column))continue;
   var foot=new BlockPos(column.getX(),l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ()),column.getZ());
   if(Math.abs(foot.getY()-door.getY())>ForestBalance.RISE||!safe(l,foot)||!((BlockItem)kind).getBlock().defaultBlockState().canSurvive(l,foot)||HarvestAccess.find(worker,foot)==null)continue;
   t.putLong("target",foot.asLong());t.putLongArray("plantCells",new long[]{foot.asLong()});t.putString("species",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(kind).toString());t.putBoolean("fromBare",true);t.remove("plantSource");
   t.putString("stage",ResourceWorkGoal.count(t.getList("cargo",Tag.TAG_COMPOUND),kind)>0?"replant":"sapling");t.putUUID("operation",UUID.randomUUID());return true;
  }return false;
 }
}
