package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.*;
/** Adapter of the existing paid queue, not another saved project or scheduler. */
public final class HallConstructionPlan {
 private HallConstructionPlan(){}
 public static UUID projectId(CompoundTag state){return state.hasUUID("project")?state.getUUID("project"):state.getUUID("id");}
 public record Step(BlockPos pos,BlockState before,BlockState after,String item){}
 public static Step step(CompoundTag op){return new Step(BlockPos.of(op.getLong("pos")),NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),op.getCompound("before")),NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),op.getCompound("after")),op.getString("item"));}
 public static ConstructionPlan read(CompoundTag state){
  var result=new ArrayList<ConstructionPlan.Operation>();var ops=state.getList("ops",Tag.TAG_COMPOUND);var id=projectId(state);var workId=state.getUUID("id");
  for(int i=0;i<ops.size();i++){var raw=ops.getCompound(i);var s=step(raw);result.add(new ConstructionPlan.Operation(Settlement.childId(workId,"block/"+i),new ConstructionPlan.Position(s.pos.getX(),s.pos.getY(),s.pos.getZ()),raw.getCompound("before").toString(),raw.getCompound("after").toString(),s.after.isAir()?ConstructionPlan.Kind.CLEAR:ConstructionPlan.Kind.BUILD,s.item.isEmpty()?Map.of():Map.of(s.item,1),20));}
  return new ConstructionPlan(id,1,0,result);
 }
}
