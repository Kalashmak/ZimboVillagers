package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
/** Field fills may displace flowing water from this project's committed irrigation source. */
public final class FieldWaterDrift {
 private FieldWaterDrift(){}
 public static boolean reconcile(ServerLevel l,CompoundTag project,CompoundTag op){
  if(!project.getBoolean("funded")||!project.hasUUID("id")||!project.getString("design").equals("farm")||!op.getBoolean("field"))return false;
  var step=HallConstructionPlan.step(op);var surveyed=op.contains("fieldSurveyBefore")?op.getCompound("fieldSurveyBefore"):op.getCompound("before");
  if(!surveyed.getString("Name").equals("minecraft:air")||!step.after().is(Blocks.DIRT)||l.getBlockEntity(step.pos())!=null)return false;
  var now=l.getBlockState(step.pos());
  if(op.contains("fieldSurveyBefore")&&now.isAir()){op.put("before",NbtUtils.writeBlockState(now));return true;}
  if(!now.is(Blocks.WATER)||now.getFluidState().isSource())return false;
  var ops=project.getList("ops",Tag.TAG_COMPOUND);
  for(int i=0;i<ops.size();i++){
   var raw=ops.getCompound(i);if(!raw.getBoolean("field")||!raw.getBoolean("done"))continue;
   var source=HallConstructionPlan.step(raw);
   if(!source.after().is(Blocks.WATER)||!source.after().getFluidState().isSource()||source.pos().getY()!=step.pos().getY()||source.pos().distManhattan(step.pos())>8)continue;
   var actual=l.getBlockState(source.pos());if(!actual.is(Blocks.WATER)||!actual.getFluidState().isSource())continue;
   var receipt=WorldJournal.inspectCommitted(l,Settlement.childId(project.getUUID("id"),"block/"+i));
   if(receipt==null||!receipt.getString("kind").equals("block")||receipt.getLong("pos")!=source.pos().asLong()||!receipt.getCompound("after").getString("Name").equals("minecraft:water"))continue;
   var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();queue.add(step.pos());seen.add(step.pos());
   while(!queue.isEmpty()){
    var p=queue.remove();if(p.equals(source.pos())){if(!op.contains("fieldSurveyBefore"))op.put("fieldSurveyBefore",surveyed.copy());op.put("before",NbtUtils.writeBlockState(now));return true;}
    for(var d:Direction.Plane.HORIZONTAL){var q=p.relative(d);if(q.distManhattan(source.pos())>8||!seen.add(q)||!l.hasChunkAt(q))continue;
     var water=l.getBlockState(q);if(water.is(Blocks.WATER)&&(!water.getFluidState().isSource()||q.equals(source.pos())))queue.add(q);}
   }
  }
  return false;
 }
}
