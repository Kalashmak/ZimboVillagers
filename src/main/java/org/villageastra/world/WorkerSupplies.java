package org.villageastra.world;
import java.util.*;
import java.nio.file.Files;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
/** Tools are village demand too; workers use the shared hall tool chest and can carry their own output. */
public final class WorkerSupplies {
 private WorkerSupplies(){}
 private static CompoundTag resource(ServerLevel l,Settlement.Building b){var file=MineWork.path(l,b.id());return Files.exists(file)?NbtRecord.read(file):new CompoundTag();}
 public static boolean eligible(Resident r,Settlement.Building b){return r!=null&&r.alive()&&r.life()==Resident.Life.ADULT&&b!=null&&(Set.of("farm","forester","mine").contains(b.type())||Workshops.spec(b.type())!=null);}
 private static Ingredient tools(String type){return Ingredient.of(switch(type){case "mine"->net.minecraft.tags.ItemTags.PICKAXES;case "forester"->net.minecraft.tags.ItemTags.AXES;default->net.minecraft.tags.ItemTags.HOES;});}
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e){return wants(l,e,null);}
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e,UUID workplace){var result=new ArrayList<Workshops.Want>();var hall=Workshops.hall(e);if(hall==null)return result;boolean delivers=SmithyDelivery.delivers(l,e);
  for(var b:e.settlement().buildings())if((workplace==null||workplace.equals(b.id()))&&Set.of("mine","forester","farm").contains(b.type())&&e.settlement().residents().stream().anyMatch(r->r.alive()&&b.equals(e.settlement().workplace(r.id())))){
   var c=LogisticsRoutes.chest(l,e,hall);if(c==null)continue;var t=resource(l,b);var held=ItemStack.of(t.getCompound("tool"));var accepted=tools(b.type());var desired=b.type().equals("mine")?Items.STONE_PICKAXE:b.type().equals("forester")?Items.STONE_AXE:Items.STONE_HOE;
   if(b.type().equals("mine")&&t.contains("requiredToolState")){var block=net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),t.getCompound("requiredToolState"));for(var pick:List.of(Items.STONE_PICKAXE,Items.IRON_PICKAXE,Items.DIAMOND_PICKAXE))if(new ItemStack(pick).isCorrectToolForDrops(block)){desired=pick;break;}accepted=Ingredient.of(java.util.stream.Stream.of(Items.STONE_PICKAXE,Items.IRON_PICKAXE,Items.DIAMOND_PICKAXE,Items.NETHERITE_PICKAXE).map(ItemStack::new).filter(pick->pick.isCorrectToolForDrops(block)));}

   // AD-155 III: with a smithy that delivers, the tool is wanted in the workplace's own chest (none there yet), not at the hall.
   var ownChest=delivers?LogisticsRoutes.chest(l,e,b):null;
   // Broken tools must not stop raw production while the first house reserves the stone.
   // Wooden tools are paid crafting; a miner blocked by an ore still requests its required tier.
   var requested=b.type().equals("forester")?Ingredient.of(desired,Items.WOODEN_AXE):b.type().equals("farm")?Ingredient.of(desired,Items.WOODEN_HOE):!t.contains("requiredToolState")?Ingredient.of(desired,Items.WOODEN_PICKAXE):Ingredient.of(desired);
   var acceptedKind=accepted;java.util.function.Predicate<ItemStack> usable=s->acceptedKind.test(s)&&(!s.isDamageableItem()||s.getDamageValue()<s.getMaxDamage());
   if(!usable.test(held)&&HallReserve.count(l,e,hall,c,usable)==0&&(ownChest==null||LogisticsRoutes.count(ownChest,usable)==0))result.add(new Workshops.Want(requested,1,ownChest!=null?b.id():hall.id()));
   if(b.type().equals("forester")&&NurserySoil.active(t)&&!t.getBoolean("soilHeld")&&HallReserve.count(l,e,hall,c,x->x.is(Items.DIRT))==0)result.add(new Workshops.Want(Ingredient.of(Items.DIRT),1,hall.id()));
   if(b.type().equals("mine")&&t.getString("stage").equals("stair")){
    var mineChest=LogisticsRoutes.chest(l,e,b);var stone=MineStairWork.stone(t);
    int missing=MineStairWork.missing(l,e,b,t)-(mineChest==null?0:LogisticsRoutes.count(mineChest,s->s.is(stone)));
    if(missing>0)result.add(new Workshops.Want(Ingredient.of(stone),missing,b.id()));
   }
   if(b.type().equals("mine")&&t.getString("stage").equals("seal_fetch")&&HallReserve.count(l,e,hall,c,MineSealing::material)==0)result.add(new Workshops.Want(Ingredient.of(Items.DIRT),1,hall.id()));
   if(b.type().equals("mine")&&SurfaceQuarry.blocked(t)&&HallReserve.count(l,e,hall,c,s->s.is(net.minecraft.tags.ItemTags.PICKAXES))==0
    &&e.settlement().residents().stream().filter(r->b.equals(e.settlement().workplace(r.id()))).noneMatch(r->NaturalSupplyGoal.quarryActive(l,r.id()))
    &&result.stream().noneMatch(w->w.matches(new ItemStack(Items.STONE_PICKAXE))))result.add(new Workshops.Want(Ingredient.of(Items.STONE_PICKAXE,Items.WOODEN_PICKAXE),1,hall.id()));
   // AD-122: lights due in the drive and none in hand nor in the hall: a lantern (or torches) is wanted.
   if(b.type().equals("mine")&&t.getIntArray("lightsDue").length>=8&&t.getInt("lightsHeld")==0&&HallReserve.count(l,e,hall,c,s->s.is(Items.LANTERN)||s.is(Items.TORCH))==0)result.add(new Workshops.Want(Ingredient.of(Items.LANTERN,Items.TORCH),ResourceWorkGoal.LIGHTS_CARRIED,hall.id()));
   // AD-131: a hut of level III or more keeps SAPLING_STOCK saplings of every kind its village has opened (and the grove's four at VI).
   if(b.type().equals(ForesterHut.TYPE)){int lv=BuildingLevels.level(l,e,b);var own=LogisticsRoutes.chest(l,e,b);
    if(lv>=3&&own!=null)for(var id:ForestWork.PLANTED){if(!CropUnlocks.known(id)||!CropUnlocks.unlocked(l,e,id))continue;var kind=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(id));
     int missing=ForestBalance.SAPLING_STOCK-LogisticsRoutes.count(own,st->st.is(kind));if(missing>0)result.add(new Workshops.Want(Ingredient.of(kind),missing,b.id()));}
    if(lv>=6)result.addAll(ForestryMachines.wants(l,e,b).stream().filter(w->result.stream().noneMatch(r->r.destination().equals(w.destination())&&r.ingredient().getItems().length>0&&w.ingredient().getItems().length>0&&r.ingredient().getItems()[0].is(w.ingredient().getItems()[0].getItem()))).toList());}
   if(b.type().equals("mine")&&t.getString("stage").equals("support_fetch")){int logs=HallReserve.count(l,e,hall,c,MineTimber::material),need=Math.max(0,MineWork.beam(t).count()-t.getInt("support_placed")-t.getInt("support_fetched"));if(logs<need)result.add(new Workshops.Want(Ingredient.of(net.minecraft.tags.ItemTags.LOGS),need-logs,hall.id()));}
  }
  // AD-138: a yard with its keeper asks for its feed, a lead and shears into its own chest (not tools of the hall).
  for(var b:e.settlement().buildings())if((workplace==null||workplace.equals(b.id()))&&b.type().equals("livestock")&&e.settlement().residents().stream().anyMatch(r->r.alive()&&b.equals(e.settlement().workplace(r.id())))){result.addAll(LivestockPens.wants(l,e,b));
   // AD-138 IV: and the meat of its kennel, into the kennel's bin (nobody works at the kennel; the yard asks for it).
   result.addAll(VillageWolves.wants(l,e,b));}
  return result;
 }
 public static boolean available(ResidentEntity worker){return available(worker,false);}
 public static boolean available(ResidentEntity worker,boolean withoutPlayers){if(!(worker.level() instanceof ServerLevel l)||worker.settlementId()==null||worker.escortPlayer()!=null||!worker.isAlive()||worker.getServer().getPlayerCount()==0&&!withoutPlayers)return false;var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return false;var b=e.settlement().workplace(worker.getUUID());if(!eligible(e.settlement().resident(worker.getUUID()),b))return false;
  var trip=PorterWork.inspect(l,worker.getUUID());if(PorterWork.active(trip))return trip.getBoolean("selfSupply");
  // AD-147 (CF-G): a warehouse courier on a trip with a cart finishes it first.
  if(WarehouseTrips.active(WarehouseTrips.inspect(l,worker.getUUID())))return false;
  // AD-139: the restaurant's courier carries portions (CourierGoal), not the kitchen's goods to the stock; a haul it had begun is finished above.
  if(Couriers.courier(e,worker.getUUID()))return false;
  if(NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,worker.getUUID())))return false;var job=Workshops.inspect(l,b.id());if(!job.isEmpty()&&!job.getString("stage").equals("idle"))return false;var state=resource(l,b);if(!state.isEmpty()&&!Set.of("tool","choose","support_fetch","stair").contains(state.getString("stage")))return false;
  return LogisticsRoutes.workerRoute(l,e,b)!=null;
 }
}
