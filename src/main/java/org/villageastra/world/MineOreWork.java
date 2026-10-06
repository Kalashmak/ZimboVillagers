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
/** Visit needed exposed mineral faces; paid stone can replace a shared floor without opening its support. */
public final class MineOreWork {
 private MineOreWork(){}
 private static final String KEY="mineOre";
 public static boolean active(CompoundTag t){return t.contains(KEY,Tag.TAG_COMPOUND);}
 public static BlockPos stand(CompoundTag t){return BlockPos.of(t.getCompound(KEY).getLong("stand"));}
 public static BlockPos target(CompoundTag t){return BlockPos.of(t.getCompound(KEY).getLong("pos"));}
 public static void clear(ServerLevel l,ResidentEntity npc,CompoundTag t){if(active(t))l.destroyBlockProgress(npc.getId(),target(t),-1);reconcile(l,t);t.remove(KEY);}
 private static UUID stoneId(CompoundTag job){return Settlement.childId(job.getUUID("id"),"floor/stone");}
 public static BlockPos materialSource(CompoundTag t){return BlockPos.of(t.getCompound(KEY).getLong("floorSource"));}
 public static boolean needsStone(ServerLevel l,CompoundTag t){reconcile(l,t);return active(t)&&t.getCompound(KEY).getBoolean("floorExchange")&&!t.getCompound(KEY).getBoolean("floorTaken");}
 private static int stoneCount(CompoundTag t){int n=0;for(var raw:t.getList("cargo",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);if(s.is(Items.COBBLESTONE))n+=s.getCount();}return n;}
 private static boolean spareStone(ServerLevel l,SettlementData.Entry e,Settlement.Building mine){var c=LogisticsRoutes.chest(l,e,mine);return c!=null&&LogisticsRoutes.count(c,s->s.is(Items.COBBLESTONE))-LogisticsRoutes.constructionReserve(l,e,mine,new ItemStack(Items.COBBLESTONE))-PorterWork.reserved(l,e,mine.id(),s->s.is(Items.COBBLESTONE),false)>0;}
 public static void fetchStone(ResidentEntity npc,SettlementData.Entry e,Settlement.Building mine,CompoundTag t){
  var l=(ServerLevel)npc.level();if(!needsStone(l,t))return;var job=t.getCompound(KEY);var source=materialSource(t);
  if(!source.equals(LogisticsRoutes.position(e,mine))||npc.distanceToSqr(Vec3.atCenterOf(source))>9||!spareStone(l,e,mine))return;
  if(l.getBlockEntity(source) instanceof net.minecraft.world.Container c)for(int slot=0;slot<c.getContainerSize();slot++)if(c.getItem(slot).is(Items.COBBLESTONE)){WorldJournal.take(l,stoneId(job),source,slot,c.getItem(slot).copy());reconcile(l,t);break;}
  job.putLong("lastProgress",l.getGameTime());job.putDouble("distance",Double.POSITIVE_INFINITY);
 }
 public static void reconcile(ServerLevel l,CompoundTag t){
  if(!active(t))return;var job=t.getCompound(KEY);
  if(job.getBoolean("floorExchange")&&!job.getBoolean("floorTaken")){var stone=WorldJournal.recoverTake(l,stoneId(job));if(!stone.isEmpty()){if(!stone.is(Items.COBBLESTONE))throw new IllegalStateException("Wrong paid floor material");var held=t.getList("cargo",Tag.TAG_COMPOUND).copy();held.add(stone.save(new CompoundTag()));t.put("cargo",held);job.putBoolean("floorTaken",true);}}
  var receipt=WorldJournal.recoverExisting(l,job.getUUID("id"));if(receipt==null)return;
  if(job.getBoolean("floorExchange")){if(!job.getBoolean("floorTaken")||stoneCount(t)<1||!receipt.getCompound("after").equals(NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState())))throw new IllegalStateException("Unpaid or invalid floor exchange");var held=t.getList("cargo",Tag.TAG_COMPOUND).copy();for(int i=0;i<held.size();i++){var s=ItemStack.of(held.getCompound(i));if(s.is(Items.COBBLESTONE)){s.shrink(1);if(s.isEmpty())held.remove(i);else held.set(i,s.save(new CompoundTag()));break;}}t.put("cargo",held);}
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
  BlockPos chosen=null,foot=null;boolean exchange=false,stoneAvailable=spareStone(l,e,mine);int queries=0;double best=Double.POSITIVE_INFINITY;int height=MineDrive.Shape.galleryHeight(area.height());
  int limit=MineWork.floorStep(l,e,mine,t);
  for(var g:area.galleries())for(int run=0;g.step()<=limit&&run<g.length();run++){
   int x=g.side()==MineDrive.EAST?(area.width()==1?4:5)+run:(area.width()==1?2:1)-run,y=-g.step()-area.descent(),z=7+g.step();
   var stand=BuildingPlacement.at(e,mine,x,y,z);if(!standing(l,stand))continue;var eye=Vec3.atBottomCenterOf(stand).add(0,npc.getEyeHeight(),0);
   for(int dy=0;dy<=height;dy++)for(var side:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.UP}){
    var local=new BlockPos(x,y+dy,z).relative(side);if(side==Direction.UP&&dy!=height-1||side==Direction.EAST&&g.side()==MineDrive.EAST&&run<g.length()-1||side==Direction.WEST&&g.side()==MineDrive.WEST&&run<g.length()-1)continue;
    var p=BuildingPlacement.at(e,mine,local.getX(),local.getY(),local.getZ());var b=l.getBlockState(p);
    boolean support=floor(area,local);
    if(!MineOutcrops.wanted(b,wanted)||support&&!stoneAvailable||!area.contains(local.getX(),local.getY(),local.getZ(),0)||org.villageastra.server.OwnershipEvents.protectedBlock(l,p,bld->!bld.id().equals(mine.id()))||!safe(l,p,tool)||!visible(l,npc,eye,p)||support&&stand.below().equals(p))continue;
    double distance=npc.distanceToSqr(Vec3.atBottomCenterOf(stand));if(distance>=best)continue;
    if(support&&(queries++>=4||!HarvestAccess.reversible(HarvestRouteCache.plan(npc,stand,128))))continue;
    chosen=p;foot=stand;best=distance;exchange=support;
   }
  }
  if(chosen==null)return false;var job=new CompoundTag();job.putUUID("id",UUID.randomUUID());job.putLong("pos",chosen.asLong());job.putLong("stand",foot.asLong());job.putLong("lastProgress",l.getGameTime());job.putDouble("distance",Double.POSITIVE_INFINITY);job.put("before",NbtUtils.writeBlockState(l.getBlockState(chosen)));job.put("tool",tool.save(new CompoundTag()));if(exchange){job.putBoolean("floorExchange",true);job.putLong("floorSource",LogisticsRoutes.position(e,mine).asLong());job.putUUID("mine",mine.id());}t.put(KEY,job);return true;
 }
 public static boolean approaching(ResidentEntity npc,CompoundTag t){
  var l=(ServerLevel)npc.level();var job=t.getCompound(KEY);double d=npc.distanceToSqr(Vec3.atBottomCenterOf(job.getBoolean("floorExchange")&&!job.getBoolean("floorTaken")?materialSource(t):stand(t)));
  if(d+1<job.getDouble("distance")){job.putDouble("distance",d);job.putLong("lastProgress",l.getGameTime());}
  if(l.getGameTime()-job.getLong("lastProgress")>2400){clear(l,npc,t);return false;}return true;
 }
 /** Called only after physical travel to stand; successful receipts survive save/reload and custody. */
 public static void tick(ResidentEntity npc,CompoundTag t){
  var l=(ServerLevel)npc.level();if(!active(t))return;var job=t.getCompound(KEY);var p=target(t);var id=job.getUUID("id");
  if(WorldJournal.exists(l,id)){reconcile(l,t);if(active(t))clear(l,npc,t);l.destroyBlockProgress(npc.getId(),p,-1);return;}
  var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),job.getCompound("before"));var tool=ItemStack.of(job.getCompound("tool"));
  if(job.getBoolean("floorExchange")){var e=SettlementData.get(npc.getServer()).entry(npc.settlementId());var mine=e==null?null:e.settlement().workplace(npc.getUUID());var area=mine==null?null:e.settlement().mineAreas().get(mine.id());var local=mine==null?BlockPos.ZERO:BuildingPlacement.local(e,mine,p);if(mine==null||area==null||!mine.id().equals(job.getUUID("mine"))||!area.contains(local.getX(),local.getY(),local.getZ(),0)||!floor(area,local)||local.getY()<-MineWork.floorStep(l,e,mine,t)-area.descent()||org.villageastra.server.OwnershipEvents.protectedBlock(l,p,b->!b.id().equals(mine.id()))||npc.blockPosition().below().equals(p)){clear(l,npc,t);return;}if(needsStone(l,t)||stoneCount(t)<1)return;}
  if(!l.getBlockState(p).equals(before)||!standing(l,stand(t))||!safe(l,p,tool)||!visible(l,npc,npc.getEyePosition(),p)){t.putString("oreFailure","changed="+!l.getBlockState(p).equals(before)+" standing="+standing(l,stand(t))+" safe="+safe(l,p,tool)+" visible="+visible(l,npc,npc.getEyePosition(),p)+" pos="+npc.position()+" stand="+stand(t)+" ore="+p);clear(l,npc,t);return;}
  npc.getNavigation().stop();int labor=job.getInt("labor")+1;job.putInt("labor",labor);int ticks=MinerSpeed.breakTicks(before,l,p,tool)+(job.getBoolean("floorExchange")?20:0);npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);MinerSpeed.progress(l,npc,p,before,labor,ticks);
  if(labor>=ticks&&(job.getBoolean("floorExchange")?WorldJournal.harvestReplace(l,id,p,before,net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(),tool):WorldJournal.harvest(l,id,p,before,tool))!=null){MinerSpeed.broken(l,npc,p,before);reconcile(l,t);}
  t.putString("status","working");npc.workStatus("working");
 }
}
