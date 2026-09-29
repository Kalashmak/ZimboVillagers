package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-068 (A07-VIS-004): a building turned by quarter turns clockwise. The design stays in the same corner box — the origin is still the
 *  north-west corner of its footprint, only width and depth trade places on an odd turn — so a local cell of the design (a chest, a desk,
 *  a bed, a fitting) is found in the world through one transform for every system. */
public final class BuildingPlacement {
 private BuildingPlacement(){}
 public static int turns(int variant){return Math.floorMod(variant,4);}
 public static Rotation rotation(int turns){return Rotation.values()[turns(turns)];}
 private static int width(String type){var d=BuildingBlueprints.design(type);return d==null?1:d.width();}
 private static int depth(String type){var d=BuildingBlueprints.design(type);return d==null?1:d.depth();}
 /** Footprint of a design turned this many times: {width along x, depth along z}. */
 public static int[] size(String type,int turns){int w=width(type),d=depth(type);return turns(turns)%2==0?new int[]{w,d}:new int[]{d,w};}
 /** A local cell of a width×depth design after the turn, inside the same corner box. */
 public static BlockPos turn(int x,int y,int z,int width,int depth,int turns){
  return switch(turns(turns)){
   case 1->new BlockPos(depth-1-z,y,x);
   case 2->new BlockPos(width-1-x,y,depth-1-z);
   case 3->new BlockPos(z,y,width-1-x);
   default->new BlockPos(x,y,z);};
 }
 /** The design cell a turned local cell came from. */
 public static BlockPos unturn(BlockPos p,int width,int depth,int turns){
  return switch(turns(turns)){
   case 1->new BlockPos(p.getZ(),p.getY(),depth-1-p.getX());
   case 2->new BlockPos(width-1-p.getX(),p.getY(),depth-1-p.getZ());
   case 3->new BlockPos(width-1-p.getZ(),p.getY(),p.getX());
   default->p;};
 }
 /** North-west corner of a building's footprint in the world. */
 public static BlockPos origin(SettlementData.Entry e,Settlement.Building b){return e.center().offset(b.x(),b.y(),b.z());}
 /** A design cell of the building, in the world. */
 public static BlockPos at(SettlementData.Entry e,Settlement.Building b,int x,int y,int z){return at(e.center(),b,x,y,z);}
 public static BlockPos at(BlockPos center,Settlement.Building b,int x,int y,int z){
  return center.offset(b.x(),b.y(),b.z()).offset(turn(x,y,z,width(b.type()),depth(b.type()),b.rotation()));
 }
 /** The design cell of the building a world position stands in (it may lie outside the design). */
 public static BlockPos local(SettlementData.Entry e,Settlement.Building b,BlockPos world){return local(e.center(),b,world);}
 public static BlockPos local(BlockPos center,Settlement.Building b,BlockPos world){
  return unturn(world.subtract(center).offset(-b.x(),-b.y(),-b.z()),width(b.type()),depth(b.type()),b.rotation());
 }
 /** A block of the design as it stands in the turned building. */
 public static BlockState state(BlockState state,int turns){return turns(turns)==0?state:state.rotate(rotation(turns));}
 /** The whole design turned and placed at a world origin (north-west corner of the turned footprint). */
 public static Map<BlockPos,BlockState> layout(String type,BlockPos origin,int turns){
  if(turns(turns)==0)return BuildingBlueprints.layout(type,origin);
  int w=width(type),d=depth(type);var out=new LinkedHashMap<BlockPos,BlockState>();
  for(var cell:BuildingBlueprints.layout(type,BlockPos.ZERO).entrySet()){var p=cell.getKey();out.put(origin.offset(turn(p.getX(),p.getY(),p.getZ(),w,d,turns)),state(cell.getValue(),turns));}
  return out;
 }
 public static Map<BlockPos,BlockState> layout(SettlementData.Entry e,Settlement.Building b,String type){return BuildingWood.apply(layout(type,origin(e,b),b.rotation()),b.wood());}
}
