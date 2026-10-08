package org.villageastra.world;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.Settlement;
import org.villageastra.domain.MineDrive;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;

/** Small paid water plugs beside a dry stair face, never excavation into water or lava. */
public final class MineSealing {
 private MineSealing(){}
 public static boolean active(CompoundTag t){return t.getString("stage").startsWith("seal_");}
 public static boolean material(ItemStack s){return s.is(Items.DIRT)||s.is(Items.COBBLESTONE)||s.is(Items.COBBLED_DEEPSLATE);}
 private static ItemStack held(CompoundTag t){for(var raw:t.getList("cargo",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);if(!s.isEmpty()&&material(s))return s;}return ItemStack.EMPTY;}
 public static BlockPos candidate(ServerLevel l,Settlement.Building mine,BlockPos face){
  if(!l.hasChunkAt(face)||!(MineWork.diggable(l.getBlockState(face))||MinePlugs.owns(l,mine,face))||l.getBlockEntity(face)!=null)return null;
  BlockPos first=null;for(var d:Direction.values()){
   var p=face.relative(d);if(!l.hasChunkAt(p))return null;var fluid=l.getFluidState(p);if(fluid.isEmpty())continue;
   if(!fluid.is(FluidTags.WATER)||!l.getBlockState(p).is(Blocks.WATER)||l.getBlockEntity(p)!=null||OwnershipEvents.protectedBlock(l,p,b->!b.id().equals(mine.id())))return null;
   if(first==null)first=p;
  }return first;
 }
 public static boolean begin(ServerLevel l,Settlement.Building mine,CompoundTag t,BlockPos face){
  if(MineWork.gallery(t))return false;var p=candidate(l,mine,face);if(p==null)return false;
  return start(t,face,p);
 }
 /** Only water immediately below a dry, recognized gallery face can be repaired. */
 public static BlockPos galleryFloorFace(ServerLevel l,SettlementData.Entry e,Settlement.Building mine,CompoundTag t,MineDrive.Cell cell){
  var face=BuildingPlacement.at(e,mine,cell.x(),-t.getInt("floorStep")-t.getInt("descent"),cell.z());
  if(!l.hasChunkAt(face)||!l.getFluidState(face).isEmpty()||OwnershipEvents.protectedBlock(l,face,b->!b.id().equals(mine.id())))return null;
  var water=candidate(l,mine,face);return face.below().equals(water)?face:null;
 }
 public static boolean beginFloor(ServerLevel l,SettlementData.Entry e,Settlement.Building mine,CompoundTag t,MineDrive.Cell cell){
  var face=galleryFloorFace(l,e,mine,t,cell);return face!=null&&start(t,face,face.below());
 }
 private static boolean start(CompoundTag t,BlockPos face,BlockPos p){
  clear(t);t.putLong("sealFace",face.asLong());t.putLong("sealAt",p.asLong());t.putString("stage",held(t).isEmpty()?"seal_fetch":"seal_place");t.putUUID("operation",UUID.randomUUID());return true;
 }
 /** Reconstruct payment before any restart/death snapshot; the flags make repeated recovery harmless. */
 public static void reconcile(ServerLevel l,CompoundTag t){
  var id=t.getUUID("operation");var take=WorldJournal.recoverAmount(l,Settlement.childId(id,"seal_take"));
  if(!take.isEmpty()&&!t.getBoolean("sealTaken")){t.put("cargo",ResourceWorkGoal.carry(t.getList("cargo",Tag.TAG_COMPOUND),List.of(take),Blocks.AIR.defaultBlockState(),-1));t.putBoolean("sealTaken",true);}
  if(t.contains("sealItem")&&!t.getBoolean("sealPlaced")&&WorldJournal.recoverExisting(l,Settlement.childId(id,"seal_place"))!=null){
   var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(t.getString("sealItem")));
   t.put("cargo",ResourceWorkGoal.without(t.getList("cargo",Tag.TAG_COMPOUND),item,1));t.putBoolean("sealPlaced",true);
  }
 }
 public static void clear(CompoundTag t){for(var k:List.of("sealFace","sealAt","sealTaken","sealPlaced","sealItem","sealReady"))t.remove(k);}
 private static void finish(CompoundTag t,Runnable save){clear(t);t.putString("stage","choose");t.putUUID("operation",UUID.randomUUID());save.run();}
 public static void tick(ServerLevel l,Settlement.Building mine,CompoundTag t,ResidentEntity worker,BlockPos stock,BlockPos stockStand,BlockPos access,Predicate<BlockPos> near,Runnable save,Consumer<String> status,long now){
  var previous=t.copy();reconcile(l,t);if(!previous.equals(t))save.run();if(t.getBoolean("sealPlaced")){finish(t,save);return;}
  var id=t.getUUID("operation");var face=BlockPos.of(t.getLong("sealFace"));var at=BlockPos.of(t.getLong("sealAt"));
  if(!at.equals(candidate(l,mine,face))){finish(t,save);return;}
  if(t.getString("stage").equals("seal_fetch")){
   if(!near.test(stockStand))return;
   if(held(t).isEmpty()&&l.getBlockEntity(stock) instanceof Container c)for(int slot=0;slot<c.getContainerSize();slot++){
    var s=c.getItem(slot);if(!material(s)||HallReserve.free(l,stock,s)<1)continue;
    WorldJournal.takeAmount(l,Settlement.childId(id,"seal_take"),stock,slot,s.copy(),1);reconcile(l,t);save.run();break;
   }
   if(held(t).isEmpty()){status.accept("missing_building_materials");return;}
   t.putString("stage","seal_place");save.run();return;
  }
  if(!near.test(access)){t.remove("sealReady");return;}
  if(worker.isInWaterOrBubble()||worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at))>20.25||!worker.level().noCollision(worker,worker.getBoundingBox())||!l.getEntities(null,new net.minecraft.world.phys.AABB(at)).isEmpty()){status.accept("needs_access");return;}
  var material=held(t);if(material.isEmpty()){t.putString("stage","seal_fetch");save.run();return;}
  if(!t.contains("sealReady")){t.putLong("sealReady",now+40);save.run();}if(now<t.getLong("sealReady")){status.accept("working");return;}
  t.putString("sealItem",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(material.getItem()).toString());save.run();
  var block=((BlockItem)material.getItem()).getBlock().defaultBlockState();
  MinePlugs.plan(l,mine,at,Settlement.childId(id,"seal_place"));
  if(WorldJournal.place(l,Settlement.childId(id,"seal_place"),at,l.getBlockState(at),block)){reconcile(l,t);save.run();finish(t,save);}else status.accept("fluid_boundary");
 }
}
