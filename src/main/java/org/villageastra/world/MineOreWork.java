package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
/** Visit needed exposed mineral faces from existing dry galleries; never cut their floor or advance the main drive. */
public final class MineOreWork {
 private MineOreWork(){}
 private static final String KEY="mineOre";
 public static boolean active(CompoundTag t){return t.contains(KEY,Tag.TAG_COMPOUND);}
 public static BlockPos stand(CompoundTag t){return BlockPos.of(t.getCompound(KEY).getLong("stand"));}
 public static BlockPos target(CompoundTag t){return BlockPos.of(t.getCompound(KEY).getLong("pos"));}
 public static void clear(ServerLevel l,ResidentEntity npc,CompoundTag t){if(active(t))l.destroyBlockProgress(npc.getId(),target(t),-1);t.remove(KEY);}
 public static void reconcile(ServerLevel l,CompoundTag t){
  if(!active(t))return;var job=t.getCompound(KEY);var receipt=WorldJournal.recoverExisting(l,job.getUUID("id"));if(receipt==null)return;
  var cargo=t.getList("cargo",Tag.TAG_COMPOUND).copy();cargo.addAll(receipt.getList("loot",Tag.TAG_COMPOUND).copy());t.put("cargo",cargo);
  var tool=ItemStack.of(job.getCompound("tool"));if(tool.isDamageableItem()){tool.setDamageValue(tool.getDamageValue()+1);if(tool.getDamageValue()>=tool.getMaxDamage())tool=ItemStack.EMPTY;}
  t.put("tool",tool.save(new CompoundTag()));t.remove(KEY);
 }
 private static boolean standing(ServerLevel l,BlockPos p){return l.hasChunkAt(p)&&l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir()&&l.getFluidState(p).isEmpty()&&l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP);}
 private static boolean safe(ServerLevel l,BlockPos p,ItemStack tool){
  if(!l.hasChunkAt(p)||l.getBlockEntity(p)!=null)return false;var b=l.getBlockState(p);
  if(b.isAir()||b.getDestroySpeed(l,p)<0||b.requiresCorrectToolForDrops()&&!tool.isCorrectToolForDrops(b)||l.getBlockState(p.above()).getBlock() instanceof FallingBlock)return false;
  if(!b.getFluidState().isEmpty())return false;for(var d:Direction.values())if(!l.getFluidState(p.relative(d)).isEmpty())return false;return true;
 }
 private static boolean visible(ServerLevel l,ResidentEntity npc,Vec3 eye,BlockPos p){var center=Vec3.atCenterOf(p);if(eye.distanceToSqr(center)>16)return false;var hit=l.clip(new ClipContext(eye,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,npc));return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(p);}
 private static boolean floor(MineArea a,BlockPos p){
  int x=p.getX(),y=p.getY(),z=p.getZ(),left=a.width()==1?3:2,right=a.width()==1?3:4;
  int row=z-7;if(row>=0&&row<=a.lastStep()&&x>=left&&x<=right&&y<=-row-a.descent())return true;
  for(var g:a.galleries()){int from=g.side()==MineDrive.EAST?right+1:left-g.length(),to=g.side()==MineDrive.EAST?right+g.length():left-1;if(z==7+g.step()&&x>=from&&x<=to&&y<=-g.step()-a.descent())return true;}
  return false;
 }
 public static boolean begin(ResidentEntity npc,SettlementData.Entry e,Settlement.Building mine,CompoundTag t){
  if(active(t))return true;if(!t.getString("stage").equals("choose"))return false;
  var l=(ServerLevel)npc.level();var tool=ItemStack.of(t.getCompound("tool"));if(!(tool.getItem() instanceof PickaxeItem)||tool.getDamageValue()>=tool.getMaxDamage())return false;
  var area=e.settlement().mineAreas().get(mine.id());if(area==null)return false;var wanted=MineProspecting.needed(l,e,mine);if(wanted.isEmpty())return false;
  BlockPos chosen=null,foot=null;double best=Double.POSITIVE_INFINITY;int height=MineDrive.Shape.galleryHeight(area.height());
  int limit=MineWork.floorStep(l,e,mine,t);
  for(var g:area.galleries())for(int run=0;g.step()<=limit&&run<g.length();run++){
   int x=g.side()==MineDrive.EAST?(area.width()==1?4:5)+run:(area.width()==1?2:1)-run,y=-g.step()-area.descent(),z=7+g.step();
   var stand=BuildingPlacement.at(e,mine,x,y,z);if(!standing(l,stand))continue;var eye=Vec3.atBottomCenterOf(stand).add(0,npc.getEyeHeight(),0);
   for(int dy=0;dy<=height;dy++)for(var side:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.UP}){
    var local=new BlockPos(x,y+dy,z).relative(side);if(side==Direction.UP&&dy!=height-1||side==Direction.EAST&&g.side()==MineDrive.EAST&&run<g.length()-1||side==Direction.WEST&&g.side()==MineDrive.WEST&&run<g.length()-1)continue;
    var p=BuildingPlacement.at(e,mine,local.getX(),local.getY(),local.getZ());var b=l.getBlockState(p);
    if(!MineOutcrops.wanted(b,wanted)||floor(area,local)||!area.contains(local.getX(),local.getY(),local.getZ(),0)||org.villageastra.server.OwnershipEvents.protectedBlock(l,p,bld->!bld.id().equals(mine.id()))||!safe(l,p,tool)||!visible(l,npc,eye,p))continue;
    double distance=npc.distanceToSqr(Vec3.atBottomCenterOf(stand));if(distance>=best)continue;
    chosen=p;foot=stand;best=distance;
   }
  }
  if(chosen==null)return false;var job=new CompoundTag();job.putUUID("id",UUID.randomUUID());job.putLong("pos",chosen.asLong());job.putLong("stand",foot.asLong());job.putLong("lastProgress",l.getGameTime());job.putDouble("distance",Double.POSITIVE_INFINITY);job.put("before",NbtUtils.writeBlockState(l.getBlockState(chosen)));job.put("tool",tool.save(new CompoundTag()));t.put(KEY,job);return true;
 }
 public static boolean approaching(ResidentEntity npc,CompoundTag t){
  var l=(ServerLevel)npc.level();var job=t.getCompound(KEY);double d=npc.distanceToSqr(Vec3.atBottomCenterOf(stand(t)));
  if(d+1<job.getDouble("distance")){job.putDouble("distance",d);job.putLong("lastProgress",l.getGameTime());}
  if(l.getGameTime()-job.getLong("lastProgress")>2400){clear(l,npc,t);return false;}return true;
 }
 /** Called only after physical travel to stand; successful receipts survive save/reload and custody. */
 public static void tick(ResidentEntity npc,CompoundTag t){
  var l=(ServerLevel)npc.level();if(!active(t))return;var job=t.getCompound(KEY);var p=target(t);var id=job.getUUID("id");
  if(WorldJournal.exists(l,id)){reconcile(l,t);if(active(t))clear(l,npc,t);l.destroyBlockProgress(npc.getId(),p,-1);return;}
  var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),job.getCompound("before"));var tool=ItemStack.of(job.getCompound("tool"));
  if(!l.getBlockState(p).equals(before)||!standing(l,stand(t))||!safe(l,p,tool)||!visible(l,npc,npc.getEyePosition(),p)){t.putString("oreFailure","changed="+!l.getBlockState(p).equals(before)+" standing="+standing(l,stand(t))+" safe="+safe(l,p,tool)+" visible="+visible(l,npc,npc.getEyePosition(),p)+" pos="+npc.position()+" stand="+stand(t)+" ore="+p);clear(l,npc,t);return;}
  npc.getNavigation().stop();int labor=job.getInt("labor")+1;job.putInt("labor",labor);int ticks=MinerSpeed.breakTicks(before,l,p,tool);npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);MinerSpeed.progress(l,npc,p,before,labor,ticks);
  if(labor>=ticks&&WorldJournal.harvest(l,id,p,before,tool)!=null){MinerSpeed.broken(l,npc,p,before);reconcile(l,t);}
  t.putString("status","working");npc.workStatus("working");
 }
}
