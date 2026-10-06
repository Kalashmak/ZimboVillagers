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
  if(kinds.isEmpty())return false;var kind=kinds.get(0);var door=ForesterHut.door(e,hut);int cursor=t.getInt("renewalScan");boolean wrapped=false;
  for(int n=0;n<256;n++){
   if(cursor>0&&Math.floorMod(cursor,CELLS.size())==0)wrapped=true;var offset=CELLS.get(Math.floorMod(cursor++,CELLS.size()));t.putInt("renewalScan",cursor);var column=door.offset(offset);if(!l.hasChunkAt(column))continue;
   var foot=new BlockPos(column.getX(),l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ()),column.getZ());
   if(Math.abs(foot.getY()-door.getY())<=ForestBalance.RISE&&!t.contains("renewalSoil")&&sandy(l,foot)&&HarvestAccess.find(worker,foot)!=null)t.putLong("renewalSoil",foot.asLong());
   if(Math.abs(foot.getY()-door.getY())>ForestBalance.RISE||!safe(l,foot)||!((BlockItem)kind).getBlock().defaultBlockState().canSurvive(l,foot)||HarvestAccess.find(worker,foot)==null)continue;
   t.putLong("target",foot.asLong());t.putLongArray("plantCells",new long[]{foot.asLong()});t.putString("species",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(kind).toString());t.putBoolean("fromBare",true);t.remove("plantSource");
   t.remove("renewalSoil");t.putString("stage",ResourceWorkGoal.count(t.getList("cargo",Tag.TAG_COMPOUND),kind)>0?"replant":"sapling");t.putUUID("operation",UUID.randomUUID());return true;
  }
  // Try ordinary fertile sites for a complete bounded scan before spending soil.
  if(wrapped&&t.contains("renewalSoil")){
   var foot=BlockPos.of(t.getLong("renewalSoil"));t.remove("renewalSoil");
   if(sandy(l,foot)&&HarvestAccess.find(worker,foot)!=null){
    t.putLong("target",foot.asLong());t.putLongArray("plantCells",new long[]{foot.asLong()});t.putString("species",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(kind).toString());t.putBoolean("fromBare",true);t.remove("plantSource");
    t.put("soilBefore",NbtUtils.writeBlockState(l.getBlockState(foot.below())));t.remove("soilHeld");t.remove("soilLabor");t.putString("stage","nursery_soil");t.putUUID("operation",UUID.randomUUID());return true;
   }
  }return false;
 }
}
