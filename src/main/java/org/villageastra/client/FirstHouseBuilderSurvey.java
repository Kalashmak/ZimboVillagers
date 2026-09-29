package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.core.*;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** One read-only comparison of local and expedition routes from a resumed building worker. */
final class FirstHouseBuilderSurvey {
 private static boolean sampled;
 static void sample(ServerLevel l,SettlementData.Entry e){
  if(sampled||!HallUpgradeGoal.exists(l,e.settlement().id()))return;
  var project=HallUpgradeGoal.inspect(l,e.settlement().id());if(!project.getBoolean("funded")||!project.hasUUID("worker"))return;
  if(!(l.getEntity(project.getUUID("worker")) instanceof ResidentEntity npc)||!npc.onGround())return;
  var ops=project.getList("ops",Tag.TAG_COMPOUND);int index=project.getInt("index");if(index>=ops.size())return;
  var op=ops.getCompound(index);if(!op.contains("stand"))return;sampled=true;
  var column=BlockPos.of(op.getLong("stand"));var base=new BlockPos(column.getX(),op.getInt("standBase"),column.getZ());
  for(var d:Direction.Plane.HORIZONTAL){var target=base.relative(d);compare(npc,target,"column_"+d);}
  BlockPos door=null;double best=Double.MAX_VALUE;
  for(var p:BlockPos.betweenClosed(npc.blockPosition().offset(-6,-1,-6),npc.blockPosition().offset(6,1,6)))if(l.getBlockState(p).getBlock() instanceof DoorBlock&&p.distSqr(npc.blockPosition())<best){best=p.distSqr(npc.blockPosition());door=p.immutable();}
  if(door!=null)compare(npc,door,"bedroom_door");
 }
 private static void compare(ResidentEntity npc,BlockPos target,String kind){
  var ordinary=npc.routeTo(target,0);var expanded=npc.routeTo(target,0,NaturalSupplyGoal.ROUTE_RANGE);
  LogUtils.getLogger().info("ASTRA_FIRST_HOUSE builderRoute kind={} pos={} target={} ordinary={} expanded={}",kind,npc.position(),target,describe(ordinary),describe(expanded));
 }
 private static String describe(net.minecraft.world.level.pathfinder.Path p){return p==null?"null":"reach="+p.canReach()+" nodes="+p.getNodeCount()+" end="+p.getEndNode().asBlockPos();}
}
