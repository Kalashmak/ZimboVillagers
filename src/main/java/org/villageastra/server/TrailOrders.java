package org.villageastra.server;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.world.Trails;
/** AD-123: the mayor's trail orders from the atlas — lay a trail to a neighbour (up to eight waypoints) or send the crew over a finished one. */
public final class TrailOrders {
 private TrailOrders(){}
 public static final int ORDER=0,REPAIR=1,MAX_WIRE=16;
 /** Checks the office and the context as every map order does; returns the refusal, or empty when the crew sets out. */
 public static String handle(ServerPlayer p,UUID village,long epoch,UUID target,int action,int surface,boolean light,long[] waypoints){
  var e=SettlementData.get(p.server).entry(village);
  if(e==null)return "village";
  if(!e.settlement().governance().canManage(p.getUUID(),epoch)||!ManagementOrders.allowedContext(p,e))return "mayor";
  if(!e.dimension().equals(p.serverLevel().dimension().location().toString()))return "dimension";
  if(waypoints.length>Trails.MAX_WAYPOINTS)return "waypoints";
  var points=new ArrayList<BlockPos>();for(long w:waypoints)points.add(BlockPos.of(w));
  String result=switch(action){case ORDER->Trails.order(p.serverLevel(),e,target,surface,light,points);case REPAIR->Trails.repair(p.serverLevel(),e);default->"action";};
  p.displayClientMessage(Component.translatable(result.isEmpty()?"trail.villageastra.ordered."+action:"trail.villageastra.refused."+result),true);
  return result;
 }
}
