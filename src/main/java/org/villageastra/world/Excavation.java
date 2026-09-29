package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-057: the stone and ore of a clearing ordered on the map. Only a miner takes them out, top first, with a real pick,
 *  and what they yield goes to the mine stock exactly once. */
public final class Excavation {
 private Excavation(){}
 private static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-dig/"+village+".bin");}
 public static CompoundTag record(ServerLevel l,UUID village){var p=path(l,village);return Files.exists(p)?NbtRecord.read(p):null;}
 private static void save(ServerLevel l,UUID village,CompoundTag t){NbtRecord.write(path(l,village),t);}
 public static boolean pending(ServerLevel l,UUID village){var t=record(l,village);return t!=null&&!t.getBoolean("done");}
 /** A living miner of the settlement with a workplace, or null. */
 public static Resident miner(SettlementData.Entry e){
  for(var r:e.settlement().residents())if(r.alive()&&r.profession()==Profession.MINER&&e.settlement().workplace(r.id())!=null)return r;
  return null;
 }
 public static String order(ServerLevel l,SettlementData.Entry e,List<BlockPos> cells){
  // AD-070: a besieged settlement orders no earthworks.
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  var village=e.settlement().id();if(pending(l,village))return "busy_miners";
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",Settlement.childId(village,"dig/"+UUID.randomUUID()));
  t.putLongArray("cells",cells.stream().mapToLong(BlockPos::asLong).toArray());t.putInt("taken",0);t.putInt("skipped",0);t.putBoolean("done",cells.isEmpty());
  save(l,village,t);return "";
 }
 /** Still the miner's work: a stone or ore block that nobody else has removed or replaced since the order. */
 private static boolean waiting(ServerLevel l,BlockPos pos){
  var state=l.getBlockState(pos);
  return !state.isAir()&&MapOrders.minersOnly(state)&&l.getBlockEntity(pos)==null&&state.getBlock().defaultDestroyTime()>=0;
 }
 /** A block a pick can get at: at least one side is open. */
 private static boolean open(ServerLevel l,BlockPos pos){
  for(var d:Direction.values()){var n=pos.relative(d);if(!l.getBlockState(n).isSolidRender(l,n))return true;}
  return false;
 }
 /** The next block the miner works out: the highest open one; the order is done when none of its blocks is left. */
 public static BlockPos next(ServerLevel l,SettlementData.Entry e){
  var t=record(l,e.settlement().id());if(t==null||t.getBoolean("done"))return null;
  boolean left=false;
  for(long raw:t.getLongArray("cells")){var pos=BlockPos.of(raw);
   if(!l.hasChunkAt(pos)){left=true;continue;}
   if(!waiting(l,pos))continue;
   left=true;
   if(open(l,pos))return pos;
  }
  if(!left){t.putBoolean("done",true);save(l,e.settlement().id(),t);}
  return null;
 }
 /** Blocks left in the order, open or not yet reachable. */
 public static int remaining(ServerLevel l,UUID village){
  var t=record(l,village);if(t==null||t.getBoolean("done"))return 0;int n=0;
  for(long raw:t.getLongArray("cells")){var pos=BlockPos.of(raw);if(!l.hasChunkAt(pos)||waiting(l,pos))n++;}
  return n;
 }
 /** Takes one ordered block out of the world exactly once and puts what a pick yields into the mine stock. */
 public static String dig(ServerLevel l,SettlementData.Entry e,Settlement.Building mine,BlockPos pos){
  var t=record(l,e.settlement().id());if(t==null||t.getBoolean("done"))return "no_order";
  boolean listed=false;for(long raw:t.getLongArray("cells"))if(raw==pos.asLong()){listed=true;break;}
  if(!listed)return "outside";
  if(!waiting(l,pos))return "not_diggable";
  var chest=LogisticsRoutes.chest(l,e,mine);if(chest==null)return "no_chest";
  var state=l.getBlockState(pos);
  var drops=Block.getDrops(state,l,pos,null,null,new ItemStack(Items.IRON_PICKAXE));
  var id=Settlement.childId(t.getUUID("id"),"block/"+pos.asLong());
  if(!WorldJournal.exists(l,id)&&!LogisticsRoutes.fits(chest,drops))return "stock_full";
  if(!WorldJournal.place(l,id,pos,state,Blocks.AIR.defaultBlockState()))return "taken";
  var stock=LogisticsRoutes.position(e,mine);int kept=0;
  for(var drop:drops){if(drop.isEmpty())continue;if(WorldJournal.deposit(l,Settlement.childId(id,"drop/"+kept),stock,drop))kept++;}
  t.putInt("taken",t.getInt("taken")+1);save(l,e.settlement().id(),t);
  return drops.isEmpty()||kept>0?"":"stock_full";
 }
 public static int taken(ServerLevel l,UUID village){var t=record(l,village);return t==null?0:t.getInt("taken");}
}
