package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** A workshop's vanilla smelting job uses real furnace slots, real burn time and vanilla server ticks. */
public final class NaturalFurnace {
 private NaturalFurnace(){}
 public static boolean recipe(ServerLevel l,Workshops.Job job){return !job.recipe().startsWith("custom:")&&l.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(job.recipe())).orElse(null) instanceof SmeltingRecipe;}
 public static BlockPos position(ServerLevel l,SettlementData.Entry e,CompoundTag job){if(job.contains("furnace"))return BlockPos.of(job.getLong("furnace"));for(var b:e.settlement().buildings()){var design=BuildingBlueprints.design(b.type());if(design==null||Relocations.moving(l,e,b.id()))continue;for(int x=0;x<design.width();x++)for(int z=0;z<design.depth();z++){var pos=BuildingPlacement.at(e,b,x,1,z);if(l.hasChunkAt(pos)&&l.getBlockEntity(pos) instanceof FurnaceBlockEntity f&&(!f.getPersistentData().hasUUID("AstraSmeltJob")||job.hasUUID("id")&&f.getPersistentData().getUUID("AstraSmeltJob").equals(job.getUUID("id")))&&f.getItem(0).isEmpty()&&f.getItem(2).isEmpty())return pos;}}return null;}
 public static int availableBurn(ServerLevel l,SettlementData.Entry e){var pos=position(l,e,new CompoundTag());if(pos==null||!(l.getBlockEntity(pos) instanceof FurnaceBlockEntity f))return 0;return Math.max(0,f.saveWithoutMetadata().getShort("BurnTime")-1)+net.minecraftforge.common.ForgeHooks.getBurnTime(f.getItem(1),RecipeType.SMELTING)*f.getItem(1).getCount();}
 public static Workshops.Job fit(Workshops.Job job,net.minecraft.world.Container c){ItemStack best=ItemStack.EMPTY;int count=0;for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(!job.inputs().get(0).matches(s))continue;int n=0;for(int j=0;j<c.getContainerSize();j++)if(ItemStack.isSameItemSameTags(s,c.getItem(j)))n+=c.getItem(j).getCount();if(n>count){count=n;best=s;}}int units=Math.min(job.units(),count);if(units<1)return job;var out=new ArrayList<ItemStack>();for(var s:job.outputs())out.add(s.copyWithCount(s.getCount()/job.units()*units));return new Workshops.Job(job.recipe(),List.of(new Workshops.Input(Ingredient.of(best),units)),job.fuelTicks()/job.units()*units,null,0,out,job.labor()/job.units()*units,units,job.batch());}
 public static BlockPos workPosition(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){String stage=t.getString("stage");if(stage.equals("equip_place"))return BlockPos.of(t.getLong("installAt"));if(stage.startsWith("equip_"))return Workshops.station(e,b);if(!t.getBoolean("physicalSmelt")||stage.equals("idle")||Set.of("smelt_raw","smelt_fuel","smelt_deliver").contains(stage))return Workshops.station(e,b);var f=position(l,e,t);return f==null?Workshops.station(e,b):f;}
 private static void save(ServerLevel l,Settlement.Building b,CompoundTag t){NbtRecord.write(Workshops.path(l,b.id()),t);}
 private static String missing(ServerLevel l,Settlement.Building b,CompoundTag t,Ingredient ingredient,int count){var list=new ListTag();var in=new CompoundTag();in.putString("ingredient",ingredient.toJson().toString());in.putInt("count",count);list.add(in);t.put("needs",list);save(l,b,t);return "workshop_missing_inputs";}
 private static UUID op(CompoundTag t,String suffix){return Settlement.childId(t.getUUID("id"),suffix);}
 public static boolean claim(ServerLevel l,Settlement.Building b,CompoundTag t,UUID worker){if(!t.getBoolean("physicalSmelt")||t.getString("stage").equals("idle"))return true;if(t.hasUUID("worker"))return t.getUUID("worker").equals(worker);t.putUUID("worker",worker);save(l,b,t);return true;}
 /** Recover the exact boundary between a resident's hands and the furnace/chest receipts. */
 public static CompoundTag custody(ServerLevel l,CompoundTag original,ListTag held){if(original.getString("stage").startsWith("equip_"))return FurnaceEquipment.custody(l,original,held);var t=original.copy();String stage=t.getString("stage");var carried=ItemStack.of(t.getCompound("carried"));
  if(stage.equals("smelt_raw")){var pending=WorldJournal.recoverAmount(l,op(t,"smelt/raw/"+t.getInt("withdrawals")));if(!pending.isEmpty()){if(carried.isEmpty())carried=pending;else carried.grow(pending.getCount());t.putInt("withdrawals",t.getInt("withdrawals")+1);}}
  else if(stage.equals("smelt_put_raw")){if(WorldJournal.recoverExisting(l,op(t,"smelt/put_raw"))!=null){carried=ItemStack.EMPTY;t.putString("stage","smelt_wait");}else t.putString("stage","smelt_raw");}
  else if(stage.equals("smelt_fuel")){carried=WorldJournal.recoverAmount(l,op(t,"smelt/fuel/"+t.getInt("fuels")));if(!carried.isEmpty())t.putInt("fuels",t.getInt("fuels")+1);}
  else if(stage.equals("smelt_put_fuel")){if(WorldJournal.recoverExisting(l,op(t,"smelt/put_fuel/"+t.getInt("fuels")))!=null){carried=ItemStack.EMPTY;t.putString("stage","smelt_wait");}else t.putString("stage","smelt_fuel");t.putInt("fuels",t.getInt("fuels")+1);}
  else if(stage.equals("smelt_wait")){carried=WorldJournal.recoverAmount(l,op(t,"smelt/output"));if(!carried.isEmpty())t.putString("stage","idle");}
  else if(stage.equals("smelt_deliver")){if(WorldJournal.recoverExisting(l,op(t,"smelt/deliver"))!=null)carried=ItemStack.EMPTY;t.putString("stage","idle");}
  if(!carried.isEmpty())held.add(carried.save(new CompoundTag()));t.remove("carried");t.remove("worker");return t;
 }
 public static void release(ServerLevel l,UUID building,CompoundTag reset){if(reset.getString("stage").equals("idle")&&reset.contains("furnace")&&l.getBlockEntity(BlockPos.of(reset.getLong("furnace"))) instanceof FurnaceBlockEntity f){f.getPersistentData().remove("AstraSmeltJob");f.setChanged();}NbtRecord.write(Workshops.path(l,building),reset);}
 private static boolean legacyFuel(ServerLevel l,CompoundTag t,BlockPos at){int bank=t.getInt("fuelBank");if(bank<=0)return false;int amount=Math.min(32766,bank),credit=t.getInt("fuelCredits");if(!WorldJournal.furnaceCredit(l,op(t,"smelt/legacy_fuel/"+credit),at,amount))return false;t.putInt("fuelBank",bank-amount);t.putInt("fuelCredits",credit+1);return true;}
 private static ItemStack take(ServerLevel l,CompoundTag t,String key,BlockPos at,net.minecraft.world.Container c,int slot,int count){var id=op(t,key);var old=WorldJournal.recoverAmount(l,id);if(!old.isEmpty()||WorldJournal.exists(l,id))return old;var stack=c.getItem(slot);return stack.isEmpty()?ItemStack.EMPTY:WorldJournal.takeAmount(l,id,at,slot,stack.copy(),Math.min(count,stack.getCount()));}
 /** Keep sticks/planks for crafting when dedicated fuel is available, irrespective of chest slot order. */
 private static int fuelSlot(net.minecraft.world.Container chest){int fallback=-1;for(int i=0;i<chest.getContainerSize();i++){var s=chest.getItem(i);if(net.minecraftforge.common.ForgeHooks.getBurnTime(s,RecipeType.SMELTING)<=0||s.hasCraftingRemainingItem())continue;if(s.is(Items.COAL)||s.is(Items.CHARCOAL))return i;if(fallback<0)fallback=i;}return fallback;}
 public static String advance(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t,long now){
  var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)return "workshop_missing_chest";
  if(t.getString("stage").equals("smelt_deliver")){
   if(!WorldJournal.deposit(l,op(t,"smelt/deliver"),Workshops.station(e,b),ItemStack.of(t.getCompound("carried"))))return "output_full";
   t.remove("carried");t.remove("worker");t.putString("stage","idle");release(l,b.id(),t);return "workshop_complete";
  }
  if(t.getString("stage").equals("smelt_wait")){var recovered=WorldJournal.recoverAmount(l,op(t,"smelt/output"));if(!recovered.isEmpty()){t.put("carried",recovered.save(new CompoundTag()));t.putString("stage","smelt_deliver");save(l,b,t);return "workshop_smelting";}}
  if(t.getString("stage").startsWith("equip_"))return FurnaceEquipment.advance(l,e,b,t);
  var at=position(l,e,t);if(at==null)return t.getString("stage").equals("smelt_raw")&&!t.contains("carried")?FurnaceEquipment.advance(l,e,b,t):"workshop_missing_furnace";
  if(!(l.getBlockEntity(at) instanceof FurnaceBlockEntity furnace))return "workshop_missing_furnace";var owner=furnace.getPersistentData();if(owner.hasUUID("AstraSmeltJob")&&!owner.getUUID("AstraSmeltJob").equals(t.getUUID("id")))return "workshop_missing_furnace";
  owner.putUUID("AstraSmeltJob",t.getUUID("id"));furnace.setChanged();t.putLong("furnace",at.asLong());String stage=t.getString("stage");var stock=Workshops.station(e,b);
  if(stage.equals("smelt_raw")){
   var raw=t.getList("inputs",Tag.TAG_COMPOUND).getCompound(0);var input=Ingredient.fromJson(com.google.gson.JsonParser.parseString(raw.getString("ingredient")));int need=raw.getInt("count");var carried=ItemStack.of(t.getCompound("carried"));String key="smelt/raw/"+t.getInt("withdrawals");ItemStack taken=WorldJournal.recoverAmount(l,op(t,key));
   if(taken.isEmpty())for(int i=0;i<chest.getContainerSize();i++)if(input.test(chest.getItem(i))&&(carried.isEmpty()||ItemStack.isSameItemSameTags(carried,chest.getItem(i)))){taken=take(l,t,key,stock,chest,i,need-carried.getCount());break;}
   if(taken.isEmpty())return missing(l,b,t,input,need-carried.getCount());if(carried.isEmpty())carried=taken.copy();else carried.grow(taken.getCount());t.putInt("withdrawals",t.getInt("withdrawals")+1);t.put("carried",carried.save(new CompoundTag()));if(carried.getCount()>=need)t.putString("stage","smelt_put_raw");
  }else if(stage.equals("smelt_put_raw")){
   if(!WorldJournal.putSlot(l,op(t,"smelt/put_raw"),at,0,ItemStack.of(t.getCompound("carried"))))return "output_full";t.remove("carried");t.putString("stage","smelt_wait");
   legacyFuel(l,t,at);
  }else if(stage.equals("smelt_fuel")){
   String key="smelt/fuel/"+t.getInt("fuels");var carried=WorldJournal.recoverAmount(l,op(t,key));if(carried.isEmpty()){int slot=fuelSlot(chest);if(slot>=0)carried=take(l,t,key,stock,chest,slot,1);}
   if(carried.isEmpty())return missing(l,b,t,Ingredient.of(Items.COAL,Items.CHARCOAL),1);t.put("carried",carried.save(new CompoundTag()));t.putString("stage","smelt_put_fuel");
  }else if(stage.equals("smelt_put_fuel")){
   if(!WorldJournal.putSlot(l,op(t,"smelt/put_fuel/"+t.getInt("fuels")),at,1,ItemStack.of(t.getCompound("carried"))))return "output_full";t.remove("carried");t.putInt("fuels",t.getInt("fuels")+1);t.putString("stage","smelt_wait");
  }else if(stage.equals("smelt_wait")){
   var expected=ItemStack.of(t.getList("outputs",Tag.TAG_COMPOUND).getCompound(0));var output=furnace.getItem(2);
   if(output.is(expected.getItem())&&output.getCount()>=expected.getCount()){var got=take(l,t,"smelt/output",at,furnace,2,expected.getCount());if(got.isEmpty())return "workshop_working";t.put("carried",got.save(new CompoundTag()));t.putString("stage","smelt_deliver");}
   else if(!furnace.getBlockState().getValue(net.minecraft.world.level.block.FurnaceBlock.LIT)&&furnace.getItem(1).isEmpty()&&!legacyFuel(l,t,at))t.putString("stage","smelt_fuel");
  }else throw new IllegalStateException("Unknown physical smelting stage "+stage);
  t.remove("needs");t.putLong("lastTick",now);save(l,b,t);return "workshop_smelting";
 }
}
