package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.*;
import org.villageastra.server.SettlementData;

/** Short, moving chunk leases for a real resource trip, including its night-time return. */
public final class ResourceExpedition {
 private ResourceExpedition(){}
 private static final TicketType<UUID> SURVEY=TicketType.create("villageastra_resource_survey",Comparator.comparing(UUID::toString),200);
 /** Read-only route territory. Renew already loaded chunks while the shared per-tick budget loads the rest. */
 public static boolean survey(ResidentEntity npc,net.minecraft.core.BlockPos target){
  var l=(ServerLevel)npc.level();var from=npc.blockPosition();boolean ready=true;
  int minX=(Math.min(from.getX(),target.getX())-16)>>4,maxX=(Math.max(from.getX(),target.getX())+16)>>4;
  int minZ=(Math.min(from.getZ(),target.getZ())-16)>>4,maxZ=(Math.max(from.getZ(),target.getZ())+16)>>4;
  for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);var at=cp.getWorldPosition();
   if(TouchLoad.ensure(l,at)!=TouchLoad.Touch.OK){ready=false;continue;}
   l.getChunkSource().addRegionTicket(SURVEY,cp,0,npc.getUUID());
  }return ready;
 }
 private static final TicketType<UUID> TICKET=TicketType.create("villageastra_resource_trip",Comparator.comparing(UUID::toString),100);
 public static void hold(ResidentEntity npc){if(npc.level() instanceof ServerLevel l)l.getChunkSource().addRegionTicket(TICKET,npc.chunkPosition(),3,npc.getUUID());}
 private record Trip(ServerLevel level,boolean test,long renewed){}
 private static final Map<UUID,Trip> MOVING=new HashMap<>();
 public static void follow(ResidentEntity npc,boolean test){MOVING.put(npc.getUUID(),new Trip((ServerLevel)npc.level(),test,npc.getServer().getTickCount()));hold(npc);}
 /** Read-only probe diagnostic; no loading or change to saved cargo. */
 public static String status(ResidentEntity npc){
  var l=(ServerLevel)npc.level();var trip=MOVING.get(npc.getUUID());
  return "enrolled="+(trip!=null)+" sameLevel="+(trip!=null&&trip.level()==l)+" renewed="+(trip==null?-1:trip.renewed())
   +" now="+l.getServer().getTickCount()+" chunk="+npc.chunkPosition()+" feetChunk="+new net.minecraft.world.level.ChunkPos(npc.blockPosition())
   +" entityTicking="+TouchLoad.ticking(l,npc.blockPosition())+" registered="+(l.getEntity(npc.getUUID())==npc);
 }
 /** A loaded saved body at the non-ticking view edge cannot run its goal to enroll itself after restart. */
 public static void recoverLoaded(ServerLevel l,boolean test){
  if(!test&&l.getServer().getPlayerCount()==0)return;
  var data=SettlementData.get(l.getServer());
  for(var entity:l.getAllEntities())if(entity instanceof ResidentEntity npc&&npc.isAlive()&&npc.settlementId()!=null&&!MOVING.containsKey(npc.getUUID())){
   var e=data.entry(npc.settlementId());var record=e==null?null:e.settlement().resident(npc.getUUID());
   if(record!=null&&record.alive()&&e.dimension().equals(l.dimension().location().toString())&&(NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,npc.getUUID()))||HomeNeighborhood.recovery(npc)))follow(npc,test);
  }
 }
 /** Server-owned renewal also reaches a worker waiting for an asynchronous chunk transition. */
 public static void tick(net.minecraft.server.MinecraftServer server){
  if(server.getTickCount()%20!=0)return;
  if(server.getTickCount()%200==0)for(var l:server.getAllLevels())recoverLoaded(l,false);
  for(var it=MOVING.entrySet().iterator();it.hasNext();){var en=it.next();var trip=en.getValue();var l=trip.level();
   if(l.getServer()!=server||!trip.test()&&server.getPlayerCount()==0){it.remove();continue;}
   if(!(l.getEntity(en.getKey()) instanceof ResidentEntity npc)||!npc.isAlive()||npc.settlementId()==null){it.remove();continue;}
   var e=SettlementData.get(server).entry(npc.settlementId());
   if(e==null||!e.dimension().equals(l.dimension().location().toString())||!NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,npc.getUUID()))&&!HomeNeighborhood.recovery(npc)){it.remove();continue;}
   hold(npc);en.setValue(new Trip(l,trip.test(),server.getTickCount()));
  }
 }
 public static void clear(){MOVING.clear();}
}
