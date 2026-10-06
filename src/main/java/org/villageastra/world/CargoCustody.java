package org.villageastra.world;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import java.nio.file.*;
import java.util.*;

/** Durable custody transfer: return physically, or leave a finite container at the actual death location. */
public final class CargoCustody {
 private CargoCustody(){}
 private static final Map<MinecraftServer,Map<UUID,Optional<CompoundTag>>> CACHE=new WeakHashMap<>();
 private static final Map<MinecraftServer,Map<UUID,String>> ADMITTED=new WeakHashMap<>();
 private static Path directory(MinecraftServer server){return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-custody");}
 private static CompoundTag load(MinecraftServer server,UUID owner){return CACHE.computeIfAbsent(server,k->new HashMap<>()).computeIfAbsent(owner,k->{var file=directory(server).resolve(owner+".bin");if(!Files.exists(file))return Optional.empty();var t=NbtRecord.read(file);if(t.getInt("schema")!=1||!t.hasUUID("id")||!t.hasUUID("settlement")||!t.getUUID("owner").equals(owner))throw new IllegalStateException("Invalid custody record");return Optional.of(t);}).orElse(null);}
 private static void save(MinecraftServer server,CompoundTag t){NbtRecord.write(directory(server).resolve(t.getUUID("owner")+".bin"),t);CACHE.computeIfAbsent(server,k->new HashMap<>()).put(t.getUUID("owner"),Optional.of(t));}
 public static boolean dead(MinecraftServer server,UUID owner){var t=load(server,owner);return t!=null&&t.getBoolean("dead");}
 public static boolean pending(MinecraftServer server,UUID owner){var t=load(server,owner);return t!=null&&!t.getBoolean("complete");}
 public static CompoundTag inspect(MinecraftServer server,UUID owner){var t=load(server,owner);return t==null?new CompoundTag():t.copy();}
 private static CompoundTag begin(ResidentEntity worker,boolean death){
  var server=worker.getServer();
  // A resident whose settlement no longer exists (annexed, dissolved) has nowhere to return anything to: there is no custody to open.
  if(worker.settlementId()==null||SettlementData.get(server).entry(worker.settlementId())==null)return null;
  var previous=load(server,worker.getUUID());
  if(previous!=null&&previous.getBoolean("dead"))return previous;
  if(previous!=null&&!previous.getBoolean("complete")){
   if(death){previous.putString("dimension",worker.level().dimension().location().toString());previous.putBoolean("dead",true);previous.putLong("deathPos",worker.blockPosition().asLong());save(server,previous);}return previous;
  }
  var snapshot=JobCargo.snapshot(worker,death);if(!death&&snapshot.jobs().isEmpty())return null;
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putUUID("owner",worker.getUUID());t.putUUID("settlement",worker.settlementId());
  t.putString("dimension",worker.level().dimension().location().toString());t.putString("sourceDimension",SettlementData.get(server).entry(worker.settlementId()).dimension());t.putBoolean("dead",death);t.putLong("deathPos",worker.blockPosition().asLong());t.put("jobs",snapshot.jobs());t.put("items",snapshot.items());save(server,t);return t;
 }
 public static boolean mayStartWork(ResidentEntity worker){
  return mayStartWork(worker,false);
 }
 /** Only a courier's verified food recovery may work through hunger; other custody gates remain. */
 public static boolean mayStartFoodTransport(ResidentEntity worker){return mayStartWork(worker,true);}
 private static boolean mayStartWork(ResidentEntity worker,boolean foodTransport){
  var server=worker.getServer();if(dead(server,worker.getUUID())||pending(server,worker.getUUID()))return false;
  var entry=SettlementData.get(server).entry(worker.settlementId());if(entry==null)return true;
  var resident=entry.settlement().resident(worker.getUUID());var building=entry.settlement().workplace(worker.getUUID());
  if(!Population.mayWork(resident)&&!(foodTransport&&PorterWork.foodEmergency(worker))){worker.workStatus("hungry");return false;}
  String signature=worker.settlementId()+"/"+(resident==null?"missing":resident.life()+"/"+resident.profession())+"/"+(building==null?"none":building.id())+"/"+(worker.blockWork()==null?"none":worker.blockWork().id());
  var admitted=ADMITTED.computeIfAbsent(server,k->new HashMap<>());if(signature.equals(admitted.get(worker.getUUID())))return true;
  if(beginReturn(worker))return false;admitted.put(worker.getUUID(),signature);return true;
 }
 public static boolean beginReturn(ResidentEntity worker){var t=begin(worker,false);return t!=null&&!t.getBoolean("complete")&&!t.getBoolean("dead");}
 public static void onDeath(ResidentEntity worker){var t=begin(worker,true);if(t!=null)progress((ServerLevel)worker.level(),t,null);worker.displayWorkItem(ItemStack.EMPTY);}
 private static void markDead(MinecraftServer server,CompoundTag t){
  var data=SettlementData.get(server);var e=data.entry(t.getUUID("settlement"));if(e!=null){var r=e.settlement().resident(t.getUUID("owner"));if(r!=null&&r.alive()){r.die();data.setDirty();}}
 }
 public static void returnStep(ResidentEntity worker,boolean simulationActive){if(!simulationActive)return;var t=load(worker.getServer(),worker.getUUID());if(t!=null&&!t.getBoolean("dead"))progress((ServerLevel)worker.level(),t,worker);}
 private static void progress(ServerLevel level,CompoundTag t,ResidentEntity worker){
  var server=level.getServer();boolean death=t.getBoolean("dead");if(death)markDead(server,t);if(t.getBoolean("complete"))return;
  var items=t.getList("items",Tag.TAG_COMPOUND);int index=t.getInt("index");var id=t.getUUID("id");
  String sourceDimension=t.contains("sourceDimension")?t.getString("sourceDimension"):t.getString("dimension");
  var sourceLevel=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new ResourceLocation(sourceDimension)));
  if(sourceLevel==null)throw new IllegalStateException("Return source dimension unavailable");
  // A return receipt may have been flushed immediately before the worker died or the process stopped.
  while(index<items.size()&&WorldJournal.recoverExisting(sourceLevel,Settlement.childId(id,"return/"+index))!=null){t.putInt("index",++index);save(server,t);}
  if(index<items.size()){
   if(death){
    var pos=t.contains("dropPos")?BlockPos.of(t.getLong("dropPos")):null;
    if(pos==null){
     var origin=BlockPos.of(t.getLong("deathPos"));
     outer:for(int radius=0;radius<=3;radius++)for(var candidate:BlockPos.betweenClosed(origin.offset(-radius,0,-radius),origin.offset(radius,2,radius)))
      if(!level.isOutsideBuildHeight(candidate)&&level.getWorldBorder().isWithinBounds(candidate)&&level.hasChunkAt(candidate)&&level.getBlockState(candidate).isAir()&&level.getBlockEntity(candidate)==null){pos=candidate.immutable();break outer;}
     if(pos==null)return;t.putLong("dropPos",pos.asLong());save(server,t);
    }
    var contents=new ListTag();for(int i=index;i<Math.min(items.size(),index+27);i++)contents.add(items.getCompound(i).copy());
    if(!WorldJournal.dropCargo(level,Settlement.childId(id,"drop/"+index),pos,t.getUUID("settlement"),contents))return;
    var drops=t.getList("drops",Tag.TAG_COMPOUND);var drop=new CompoundTag();drop.putLong("pos",pos.asLong());drop.putUUID("operation",Settlement.childId(id,"drop/"+index));drops.add(drop);t.put("drops",drops);
    t.putInt("index",index+contents.size());t.remove("dropPos");save(server,t);return;
   }
   if(worker==null)return;
   if(!level.dimension().location().toString().equals(sourceDimension)){worker.workStatus("needs_return_route");return;}
   var entry=SettlementData.get(server).entry(t.getUUID("settlement"));if(entry==null)return;var stock=HallSite.stock(entry);
   var item=ItemStack.of(items.getCompound(index));worker.displayWorkItem(item);worker.workStatus("returning_cargo");
   boolean hall=t.getList("jobs",Tag.TAG_COMPOUND).stream().anyMatch(raw->((CompoundTag)raw).getString("kind").equals("hall"));
   if(hall&&!HallSite.castle(entry.settlement())&&worker.getY()>entry.center().getY()+2){
    var ladder=entry.center().offset(5,1,5);double dx=ladder.getX()+.5-worker.getX(),dz=ladder.getZ()+.5-worker.getZ();
    if(dx*dx+dz*dz>4)worker.getNavigation().moveTo(ladder.getX()+.5,worker.getY(),ladder.getZ()+.5,.8);
    else {worker.getNavigation().stop();worker.setDeltaMovement(Math.max(-.12,Math.min(.12,dx)),worker.onClimbable()?-.2:worker.getDeltaMovement().y,Math.max(-.12,Math.min(.12,dz)));}return;
   }

   if(worker.distanceToSqr(stock.getX()+1.5,stock.getY(),stock.getZ()+.5)>6.25){worker.getNavigation().moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5,.8);return;}
   worker.getNavigation().stop();
   if(!WorldJournal.deposit(level,Settlement.childId(id,"return/"+index),stock,item)){worker.workStatus("return_stock_full");return;}
   t.putInt("index",index+1);save(server,t);return;
  }
  JobCargo.release(level,t.getUUID("owner"),t.getList("jobs",Tag.TAG_COMPOUND));t.putBoolean("complete",true);save(server,t);if(worker!=null)worker.displayWorkItem(ItemStack.EMPTY);
 }
 private static ServerLevel level(MinecraftServer server,CompoundTag t){return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new ResourceLocation(t.getString("dimension"))));}
 public static void recover(MinecraftServer server){
  var dir=directory(server);if(!Files.exists(dir))return;
  try(var files=Files.list(dir)){for(var file:files.filter(p->p.getFileName().toString().endsWith(".bin")).toList()){
   var owner=UUID.fromString(file.getFileName().toString().replace(".bin",""));var t=load(server,owner);if(t.getBoolean("dead")){markDead(server,t);var level=level(server,t);if(level!=null)progress(level,t,null);}
  }}catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 public static void tick(MinecraftServer server){
  for(var value:CACHE.getOrDefault(server,Map.of()).values())if(value.isPresent()){
   var t=value.get();if(t.getBoolean("dead")&&!t.getBoolean("complete")){var level=level(server,t);if(level!=null)progress(level,t,null);}
  }
 }
}
