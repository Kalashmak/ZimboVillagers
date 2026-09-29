package org.villageastra.world;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.villageastra.server.SettlementData;
/** A real owner lends a tame horse to a trade yard; no animal is created by research. */
public final class VillageHorses {
 private VillageHorses(){}
 public static final String VILLAGE="AstraTradeHorse";
 public static UUID village(Horse h){return h.getPersistentData().hasUUID(VILLAGE)?h.getPersistentData().getUUID(VILLAGE):null;}
 public static boolean ready(Horse h){return h.isAlive()&&!CaravanHorses.stale(h)&&h.isTamed()&&!h.isBaby()&&!h.isVehicle()&&!h.isPassenger()&&!h.isLeashed()&&!h.isInLove()&&h.getHealth()>=h.getMaxHealth()*.5F;}
 public static boolean enlist(ServerLevel l,SettlementData.Entry e,Horse h,UUID owner){
  var yard=Caravans.yard(e);if(yard==null||TradeLadder.level(l,e)<6||!ready(h)||!owner.equals(h.getOwnerUUID())||village(h)!=null||CaravanHorses.reserved(l.getServer(),h.getUUID()))return false;
  if(!h.level().equals(l)||h.blockPosition().distSqr(BuildingPlacement.origin(e,yard))>24*24)return false;
  h.getPersistentData().putUUID(VILLAGE,e.settlement().id());h.setPersistenceRequired();attach(h);return true;
 }
 public static boolean release(Horse h,UUID owner){if(!owner.equals(h.getOwnerUUID())||village(h)==null||CaravanHorses.reserved(h.getServer(),h.getUUID()))return false;h.getPersistentData().remove(VILLAGE);return true;}
 public static Horse free(ServerLevel l,SettlementData.Entry e){
  var yard=Caravans.yard(e);if(yard==null||TradeLadder.level(l,e)<6)return null;
  return l.getEntitiesOfClass(Horse.class,new AABB(BuildingPlacement.origin(e,yard)).inflate(24),h->e.settlement().id().equals(village(h))&&ready(h)&&!CaravanHorses.reserved(l.getServer(),h.getUUID())).stream().findFirst().orElse(null);
 }
 public static void attach(Horse h){if(village(h)!=null&&h.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof CaravanHorseGoal))h.goalSelector.addGoal(0,new CaravanHorseGoal(h));}
 /** Sneak-use a lead on one's own horse beside a level VI yard to lend it, or take it back when idle. */
 public static String interact(ServerLevel l,Player player,Horse h){
  if(!player.getUUID().equals(h.getOwnerUUID()))return "owner";
  if(village(h)!=null)return release(h,player.getUUID())?"released":"busy";
  for(var e:SettlementData.get(l.getServer()).entries())if(e.dimension().equals(l.dimension().location().toString())&&enlist(l,e,h,player.getUUID()))return "enlisted";
  return "yard";
 }
}
