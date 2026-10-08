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
 /** An eligible colleague can collect a sick worker's cooking furnace, or begin a
  * stale job whose stranded owner has not taken any goods. Paid custody stays exclusive. */
 public static boolean availableTo(ServerLevel l,Settlement.Building b,CompoundTag t,UUID worker){return availableTo(l,b,t,worker,SettlementData.get(l.getServer()).clock().ticks());}
 public static boolean availableTo(ServerLevel l,Settlement.Building b,CompoundTag t,UUID worker,long now){
  if(!t.getBoolean("physicalSmelt")||t.getString("stage").equals("idle")||!t.hasUUID("worker")||t.getUUID("worker").equals(worker))return true;
  boolean untouched=t.getString("stage").equals("smelt_raw")&&t.hasUUID("id")&&now-t.getLong("lastTick")>=2400
    &&t.getInt("withdrawals")==0&&t.getInt("fuels")==0&&t.getInt("output")==0&&t.getLong("labor")==0
    &&t.getList("paid",Tag.TAG_COMPOUND).isEmpty()&&!t.contains("furnace")&&!WorldJournal.exists(l,op(t,"smelt/raw/0"));
  if(!untouched&&(!t.getString("stage").equals("smelt_wait")||!t.hasUUID("id")||!t.contains("furnace"))
    ||!ItemStack.of(t.getCompound("carried")).isEmpty()||WorldJournal.exists(l,op(t,"smelt/output")))return false;
  if(!(l.getEntity(t.getUUID("worker")) instanceof ResidentEntity old)||!old.isAlive()||old.escortPlayer()!=null
    ||CargoCustody.pending(l.getServer(),old.getUUID())||old.settlementId()==null)return false;
  var e=SettlementData.get(l.getServer()).entry(old.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return false;
  var r=e.settlement().resident(old.getUUID());var replacement=e.settlement().resident(worker);
  var post=e.settlement().workplace(worker);if(post==null&&replacement!=null&&replacement.profession()==null)post=Workshops.hall(e);
  if(r==null||!r.alive()||(!untouched&&(!r.sick()||Population.mayWork(r)))||!Workshops.eligible(r,b)||!Workshops.eligible(replacement,b)||!Population.mayWork(replacement)
    ||post==null||!post.id().equals(b.id())||CargoCustody.pending(l.getServer(),worker))return false;
  if(!(l.getEntity(worker) instanceof ResidentEntity helper)||!helper.isAlive()||helper.escortPlayer()!=null||!old.settlementId().equals(helper.settlementId()))return false;
  var oldPost=e.settlement().workplace(old.getUUID());if(oldPost==null&&r.profession()==null)oldPost=Workshops.hall(e);
  if(oldPost==null||!oldPost.id().equals(b.id()))return false;
  if(untouched){
   var route=old.getNavigation().getPath();var pos=Workshops.station(e,b);
   if(old.distanceToSqr(pos.getCenter())<=64||route==null||route.canReach()||helper.isSleeping()||!helper.onGround()
     ||helper.distanceToSqr(pos.getX()+1.5,pos.getY(),pos.getZ()+.5)>6.25)return false;
   var sight=l.clip(new net.minecraft.world.level.ClipContext(helper.getEyePosition(),pos.getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,helper));
   return sight.getType()==net.minecraft.world.phys.HitResult.Type.MISS||sight.getBlockPos().equals(pos);
  }
  var at=BlockPos.of(t.getLong("furnace"));return l.hasChunkAt(at)&&l.getBlockEntity(at) instanceof FurnaceBlockEntity f
    &&f.getPersistentData().hasUUID("AstraSmeltJob")&&f.getPersistentData().getUUID("AstraSmeltJob").equals(t.getUUID("id"));
 }
 public static boolean claim(ServerLevel l,Settlement.Building b,CompoundTag t,UUID worker){return claim(l,b,t,worker,SettlementData.get(l.getServer()).clock().ticks());}
 public static boolean claim(ServerLevel l,Settlement.Building b,CompoundTag t,UUID worker,long now){
  if(!availableTo(l,b,t,worker,now))return false;
  if(!t.getBoolean("physicalSmelt")||t.getString("stage").equals("idle")||t.hasUUID("worker")&&t.getUUID("worker").equals(worker))return true;
  t.putUUID("worker",worker);save(l,b,t);return true;
 }
 /** Finish paid transfers before an emergency bread turn; a burning furnace
  * still leaves its carrier free to bake while it cooks. Observation only. */
 public static boolean finishing(ResidentEntity worker,SettlementData.Entry e){
  var l=(ServerLevel)worker.level();var b=e.settlement().workplace(worker.getUUID());if(b==null)b=Workshops.hall(e);if(b==null)return false;
  var t=Workshops.inspect(l,b.id());if(!t.getBoolean("physicalSmelt")||t.getString("stage").equals("idle")||!t.hasUUID("worker")||!t.getUUID("worker").equals(worker.getUUID()))return false;
  if(!ItemStack.of(t.getCompound("carried")).isEmpty())return true;
  String stage=t.getString("stage");if(!Set.of("smelt_wait","smelt_fuel").contains(stage)||!t.contains("furnace"))return false;
  var at=BlockPos.of(t.getLong("furnace"));if(!l.hasChunkAt(at)||!(l.getBlockEntity(at) instanceof FurnaceBlockEntity f))return false;
  if(stage.equals("smelt_wait")){
   var expected=ItemStack.of(t.getList("outputs",Tag.TAG_COMPOUND).getCompound(0));var output=f.getItem(2);
   if(!expected.isEmpty()&&output.is(expected.getItem())&&output.getCount()>=expected.getCount()||WorldJournal.exists(l,op(t,"smelt/output")))return true;
   if(f.getBlockState().getValue(net.minecraft.world.level.block.FurnaceBlock.LIT)||!f.getItem(1).isEmpty())return false;
  }
  var chest=LogisticsRoutes.chest(l,e,b);
  return !f.getItem(0).isEmpty()&&chest!=null&&fuelSlot(b.type().equals("town_hall")?HallReserve.view(l,e,chest):chest)>=0;
 }
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
 private static int fuelSlot(net.minecraft.world.Container chest){int fallback=-1;for(int i=0;i<chest.getContainerSize();i++){var s=chest.getItem(i);if(WorkshopFuel.ticks(s)<=0)continue;if(s.is(Items.COAL)||s.is(Items.CHARCOAL)||s.is(Items.COAL_BLOCK)||s.is(Items.DRIED_KELP_BLOCK))return i;if(fallback<0)fallback=i;}return fallback;}
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
   String key="smelt/fuel/"+t.getInt("fuels");var carried=WorldJournal.recoverAmount(l,op(t,key));if(carried.isEmpty()){int slot=fuelSlot(b.type().equals("town_hall")?HallReserve.view(l,e,chest):chest);if(slot>=0)carried=take(l,t,key,stock,chest,slot,1);}
   if(carried.isEmpty())return missing(l,b,t,WorkshopFuel.demand(),1);t.put("carried",carried.save(new CompoundTag()));t.putString("stage","smelt_put_fuel");
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
