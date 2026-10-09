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
 private static final TicketType<UUID> MINE_RETURN=TicketType.create("zimbovillagers_mine_return",Comparator.comparing(UUID::toString),400);
 public static void hold(ResidentEntity npc){if(npc.level() instanceof ServerLevel l)l.getChunkSource().addRegionTicket(TICKET,npc.chunkPosition(),3,npc.getUUID());}
 private record Trip(ServerLevel level,boolean test,long renewed,long stalledSince,long chunk){}
 private static final Map<UUID,Trip> MOVING=new HashMap<>();
 public static void follow(ResidentEntity npc,boolean test){MOVING.put(npc.getUUID(),new Trip((ServerLevel)npc.level(),test,npc.getServer().getTickCount(),-1,npc.chunkPosition().toLong()));hold(npc);}
 /** A late lower-status callback can leave a ready holder tracked but not ticking.
  * Reconcile only a sustained disagreement on the current, fully ready native holder.
  * This sends the native notification; it neither ticks the body nor completes a future. */
 private static Trip renewed(ResidentEntity npc,Trip trip,long now){
  var l=trip.level();var cp=npc.chunkPosition();long key=cp.toLong(),since=-1;
  if(!TouchLoad.ticking(l,npc.blockPosition())){
   since=trip.chunk()==key&&trip.stalledSince()>=0?trip.stalledSince():now;
   if(now-since>=100){
    var map=l.getChunkSource().chunkMap;var holder=map.getVisibleChunkIfPresent(key);
    if(holder!=null&&holder==map.getUpdatingChunkIfPresent(key)
      &&holder.getFullStatus()==FullChunkStatus.ENTITY_TICKING&&map.getDistanceManager().inEntityTickingRange(key)){
     var future=holder.getEntityTickingChunkFuture();
     if(future.isDone()&&!future.isCompletedExceptionally()&&!future.isCancelled()){
      var ready=future.getNow(null);
      if(ready!=null&&ready.left().filter(c->c.getPos().equals(cp)).isPresent()){
       map.onFullChunkStatusChange(cp,FullChunkStatus.ENTITY_TICKING);
       com.mojang.logging.LogUtils.getLogger().info("ZimboVillagers restored expedition chunk status: resident={} chunk={}",npc.getUUID(),cp);
       since=now;
      }
     }
    }
   }
  }
  return new Trip(l,trip.test(),now,since,key);
 }
 /** Read-only probe diagnostic; no loading or change to saved cargo. */
 public static String status(ResidentEntity npc){
  var l=(ServerLevel)npc.level();var trip=MOVING.get(npc.getUUID());
  return "enrolled="+(trip!=null)+" sameLevel="+(trip!=null&&trip.level()==l)+" renewed="+(trip==null?-1:trip.renewed())
   +" now="+l.getServer().getTickCount()+" chunk="+npc.chunkPosition()+" feetChunk="+new net.minecraft.world.level.ChunkPos(npc.blockPosition())
   +" entityTicking="+TouchLoad.ticking(l,npc.blockPosition())+" registered="+(l.getEntity(npc.getUUID())==npc);
 }
 /** Assigned workers at a visited village's loaded edge cannot start their own lease.
  * Use actual player proximity, not a lease-powered center, to avoid keeping abandoned villages awake. */
 private static boolean localWorker(ResidentEntity npc,boolean test){
  var l=(ServerLevel)npc.level();var e=SettlementData.get(l.getServer()).entry(npc.settlementId());
  if(e==null||!e.dimension().equals(l.dimension().location().toString())||npc.escortPlayer()!=null
    ||npc.blockPosition().distSqr(e.center())>HomeNeighborhood.RECOVERY_REACH*HomeNeighborhood.RECOVERY_REACH)return false;
  var r=e.settlement().resident(npc.getUUID());
  if(r==null||!r.alive()||r.profession()==null||e.settlement().workplace(r.id())==null)return false;
  return visited(l,e,test);
 }
 private static boolean visited(ServerLevel l,SettlementData.Entry e,boolean test){
  if(test)return TouchLoad.ticking(l,e.center());
  var cp=new net.minecraft.world.level.ChunkPos(e.center());int reach=l.getServer().getPlayerList().getSimulationDistance();
  return l.players().stream().anyMatch(p->Math.abs(p.chunkPosition().x-cp.x)<=reach&&Math.abs(p.chunkPosition().z-cp.z)<=reach);
 }
 /** Ordinary miners also leave the player's view while walking their long galleries. */
 public static void working(ResidentEntity npc,boolean test){
  if(npc.level() instanceof ServerLevel l&&l.getEntity(npc.getUUID())==npc&&localWorker(npc,test))follow(npc,test);
 }
 /** A body already saved outside the view edge cannot enroll itself. Reopen only its
  * claimed, bounded mine route while the village is visited; native persistence restores it. */
 private static void recoverMineRoutes(ServerLevel l,boolean test){
  for(var e:SettlementData.get(l.getServer()).entries()){
   if(!e.dimension().equals(l.dimension().location().toString())||!visited(l,e,test))continue;
   for(var r:e.settlement().residents()){
    if(!r.alive()||r.profession()!=org.villageastra.domain.Profession.MINER||l.getEntity(r.id())!=null)continue;
    var b=e.settlement().workplace(r.id());
    if(b==null||!b.type().equals("mine")||!java.nio.file.Files.exists(MineWork.path(l,b.id())))continue;
    var work=MineWork.read(l,b);
    if(!work.hasUUID("worker")||!work.getUUID("worker").equals(r.id())||work.getBoolean("complete")
      ||!work.contains("access",net.minecraft.nbt.Tag.TAG_INT_ARRAY))continue;
    var access=work.getIntArray("access");if(access.length!=3)continue;
    var to=BuildingPlacement.at(e,b,access[0],access[1],access[2]);
    if(to.distSqr(e.center())>HomeNeighborhood.RECOVERY_REACH*HomeNeighborhood.RECOVERY_REACH
      ||to.getY()<l.getMinBuildHeight()||to.getY()>=l.getMaxBuildHeight())continue;
    var start=new net.minecraft.world.level.ChunkPos(BuildingPlacement.origin(e,b));var end=new net.minecraft.world.level.ChunkPos(to);
    int steps=Math.max(Math.abs(end.x-start.x),Math.abs(end.z-start.z));
    for(int i=0;i<=steps;i++){
     int x=steps==0?start.x:start.x+(int)Math.round((end.x-start.x)*(double)i/steps);
     int z=steps==0?start.z:start.z+(int)Math.round((end.z-start.z)*(double)i/steps);
     var cp=new net.minecraft.world.level.ChunkPos(x,z);
     if(TouchLoad.ensure(l,cp.getWorldPosition())!=TouchLoad.Touch.OK)continue;
     l.getChunkSource().addRegionTicket(MINE_RETURN,cp,3,r.id());
    }
   }
  }
 }
 /** A loaded saved body at the non-ticking view edge cannot run its goal to enroll itself after restart. */
 public static void recoverLoaded(ServerLevel l,boolean test){
  if(!test&&l.getServer().getPlayerCount()==0)return;
  recoverMineRoutes(l,test);
  var data=SettlementData.get(l.getServer());
  for(var entity:l.getAllEntities())if(entity instanceof ResidentEntity npc&&npc.isAlive()&&npc.settlementId()!=null&&!MOVING.containsKey(npc.getUUID())){
   var e=data.entry(npc.settlementId());var record=e==null?null:e.settlement().resident(npc.getUUID());
   if(record!=null&&record.alive()&&e.dimension().equals(l.dimension().location().toString())&&(NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,npc.getUUID()))||HomeNeighborhood.recovery(npc)||localWorker(npc,test)))follow(npc,test);
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
   if(e==null||!e.dimension().equals(l.dimension().location().toString())||!NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,npc.getUUID()))&&!HomeNeighborhood.recovery(npc)&&!localWorker(npc,trip.test())){it.remove();continue;}
   hold(npc);en.setValue(renewed(npc,trip,server.getTickCount()));
  }
 }
 public static void clear(){MOVING.clear();}
}
