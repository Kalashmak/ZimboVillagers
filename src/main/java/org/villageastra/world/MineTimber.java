package org.villageastra.world;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.RotatedPillarBlock;
import org.villageastra.persistence.WorldJournal;
/** Actual paid logs for a mine beam, in installation order. Legacy beams were paid exclusively in oak. */
public final class MineTimber {
 private MineTimber(){}
 public static boolean material(ItemStack stack){return stack.is(ItemTags.LOGS)&&stack.getItem() instanceof BlockItem b&&b.getBlock().defaultBlockState().hasProperty(RotatedPillarBlock.AXIS);}
 public static ListTag stored(CompoundTag t){
  if(t.contains("supportTimber",Tag.TAG_LIST))return t.getList("supportTimber",Tag.TAG_COMPOUND).copy();
  var result=new ListTag();int count=t.getString("stage").equals("support_place")?MineWork.beam(t).count()-t.getInt("support_placed"):t.getInt("support_fetched");
  if(count>0)result.add(new ItemStack(Items.OAK_LOG,count).save(new CompoundTag()));return result;
 }
 public static void add(CompoundTag t,ItemStack stack){var held=stored(t);held.add(stack.save(new CompoundTag()));t.put("supportTimber",held);}
 public static ItemStack first(CompoundTag t){var held=stored(t);return held.isEmpty()?ItemStack.EMPTY:ItemStack.of(held.getCompound(0));}
 private static void spend(ListTag held){if(held.isEmpty())return;var first=ItemStack.of(held.getCompound(0));first.shrink(1);if(first.isEmpty())held.remove(0);else held.set(0,first.save(new CompoundTag()));}
 public static void spend(CompoundTag t){var held=stored(t);spend(held);t.put("supportTimber",held);}
 /** Journal-ahead recovery for custody: a committed take is carried, a committed placement is not. */
 public static ListTag carried(ServerLevel l,CompoundTag t){
  var held=stored(t);var op=t.getUUID("operation");int placed=t.getInt("support_placed"),fetched=t.getInt("support_fetched"),width=MineWork.beam(t).count();
  if(t.getString("stage").equals("support_fetch")&&fetched<width-placed){var got=WorldJournal.recoverAmount(l,MineWork.timberId(t,op,fetched));if(!got.isEmpty())held.add(got.save(new CompoundTag()));}
  if(t.getString("stage").equals("support_place")&&placed<width&&WorldJournal.recoverExisting(l,MineWork.beamId(t,op,placed))!=null)spend(held);
  return held;
 }
 /** Abandon an unapproachable old beam without losing, duplicating or converting its paid logs. */
 public static void cancel(ServerLevel l,CompoundTag t){
  var held=carried(l,t);t.put("cargo",ResourceWorkGoal.carry(t.getList("cargo",Tag.TAG_COMPOUND),held.stream().map(raw->ItemStack.of((CompoundTag)raw)).toList(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),-1));
  for(var key:new String[]{"support_fetched","support_placed","supportTimber","beam"})t.remove(key);
  t.putString("stage",ItemStack.of(t.getCompound("tool")).isEmpty()?"tool":"choose");t.putUUID("operation",java.util.UUID.randomUUID());
 }
}
