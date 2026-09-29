package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
/** AD-159 (owner's Military ladder): a soldier's gear by the barracks' level - I stone swords and no armour, II iron weapons and iron
 *  armour, III and on diamond (netherite is never issued). A soldier takes the best allowed weapon from the barracks chest and, from II,
 *  a piece of armour for every slot he lacks, each through the journal into his guard record (GuardGoal.equip); his body wears the
 *  record's armour on campaign (it never drops: the record owns it). */
public final class Army {
 private Army(){}
 public static final String BARRACKS="barracks";
 public static final List<EquipmentSlot> ARMOUR=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
 /** The best tier a soldier of a barracks at this level may take: 1 stone (wood, gold, leather, chain), 2 iron, 3 diamond. */
 public static int cap(int level){return Math.max(1,Math.min(3,level));}
 public static int tier(ItemStack s){var i=s.getItem();
  if(i instanceof TieredItem t){var tr=t.getTier();return tr==Tiers.IRON?2:tr==Tiers.DIAMOND?3:tr==Tiers.NETHERITE?4:1;}
  if(i instanceof ArmorItem a){var m=a.getMaterial();return m==ArmorMaterials.IRON?2:m==ArmorMaterials.DIAMOND?3:m==ArmorMaterials.NETHERITE?4:1;}
  return 1;}
 /** The slot of the best weapon of at most this tier in the chest (a sword or an axe, a bow for an archer), or -1. */
 public static int bestWeapon(Container c,boolean archer,int cap){int best=-1,top=0;
  for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(!GuardGoal.weapon(s,archer))continue;int t=tier(s);if(t<=cap&&t>top){top=t;best=i;}}return best;}
 /** From II: a piece of armour for every slot the record lacks, the best of at most the cap. True when something was taken. */
 public static boolean armour(ServerLevel l,UUID resident,CompoundTag t,Container chest,BlockPos pos,int cap){
  if(cap<2)return false;boolean changed=false;var worn=t.getCompound("armour");
  for(var slot:ARMOUR){if(worn.contains(slot.getName()))continue;int best=-1,top=0;
   for(int i=0;i<chest.getContainerSize();i++){var s=chest.getItem(i);if(s.getItem() instanceof ArmorItem a&&a.getEquipmentSlot()==slot){int tr=tier(s);if(tr<=cap&&tr>top){top=tr;best=i;}}}
   var id=Settlement.childId(resident,"equip/"+t.getInt("takes"));var got=WorldJournal.recoverAmount(l,id);
   if(got.isEmpty()&&!WorldJournal.exists(l,id)&&best>=0)got=WorldJournal.takeAmount(l,id,pos,best,chest.getItem(best).copy(),1);
   if(!got.isEmpty()){worn.put(slot.getName(),got.save(new CompoundTag()));t.putInt("takes",t.getInt("takes")+1);changed=true;}}
  t.put("armour",worn);return changed;}
 /** The soldier's body wears what his record holds (a copy that never drops). */
 public static void wear(ResidentEntity npc,CompoundTag t){var worn=t.getCompound("armour");
  for(var slot:ARMOUR){var want=worn.contains(slot.getName())?ItemStack.of(worn.getCompound(slot.getName())):ItemStack.EMPTY;
   if(!ItemStack.matches(npc.getItemBySlot(slot),want)){npc.setItemSlot(slot,want.copy());npc.setDropChance(slot,0F);}}
  var shield=t.contains("shield")?ItemStack.of(t.getCompound("shield")):ItemStack.EMPTY;
  if(!ItemStack.matches(npc.getItemBySlot(EquipmentSlot.OFFHAND),shield)){npc.setItemSlot(EquipmentSlot.OFFHAND,shield.copy());npc.setDropChance(EquipmentSlot.OFFHAND,0F);}}
 // ---------------------------------------------------------------- IV: the specialities
 public enum Speciality{NONE,ARCHER,DEFENDER,BERSERKER}
 public static final int SPECIALITY=4;public static final float BERSERK=1.5F,SHIELDED=.6F;public static final int SHOT_REACH=24;
 /** From IV every soldier has a speciality, a third each by his id: an archer (a bow), a defender (a shield: SHIELDED of the damage) and a
  *  berserker (an axe: BERSERK times the damage). */
 public static Speciality speciality(UUID resident,int barracksLevel){if(barracksLevel<SPECIALITY)return Speciality.NONE;return Speciality.values()[1+Math.floorMod(resident.hashCode(),3)];}
 /** The speciality of a soldier in a village (by its best barracks). */
 public static Speciality speciality(ServerLevel l,org.villageastra.server.SettlementData.Entry e,UUID resident){return speciality(resident,BuildingLevels.best(l,e,BARRACKS));}
 /** A berserker's axe: the slot of the best axe of at most the cap, or -1. */
 public static int bestAxe(Container c,int cap){int best=-1,top=0;for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(!(s.getItem() instanceof AxeItem))continue;int t=tier(s);if(t<=cap&&t>top){top=t;best=i;}}return best;}
 /** A defender's shield into the record, through the journal. */
 public static boolean shield(ServerLevel l,UUID resident,CompoundTag t,Container chest,BlockPos pos){
  if(t.contains("shield"))return false;int best=-1;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).getItem() instanceof ShieldItem){best=i;break;}
  var id=Settlement.childId(resident,"equip/"+t.getInt("takes"));var got=WorldJournal.recoverAmount(l,id);
  if(got.isEmpty()&&!WorldJournal.exists(l,id)&&best>=0)got=WorldJournal.takeAmount(l,id,pos,best,chest.getItem(best).copy(),1);
  if(got.isEmpty())return false;t.put("shield",got.save(new CompoundTag()));t.putInt("takes",t.getInt("takes")+1);return true;}
 /** LivingHurtEvent: what a soldier takes (a defender holding his shield SHIELDED of it). */
 public static float taken(ResidentEntity npc,float amount){
  if(!(npc.level() instanceof ServerLevel l)||npc.settlementId()==null)return amount;var e=org.villageastra.server.SettlementData.get(l.getServer()).entry(npc.settlementId());if(e==null)return amount;
  var r=e.settlement().resident(npc.getUUID());if(r==null||r.profession()!=org.villageastra.domain.Profession.SOLDIER)return amount;
  return speciality(l,e,npc.getUUID())==Speciality.DEFENDER&&GuardGoal.inspect(l,npc.getUUID()).contains("shield")?amount*SHIELDED:amount;}
 // ---------------------------------------------------------------- V: the dogs
 public static final int DOGS=5;public static final String ACADEMY="dog_academy_annex";
 /** V: a village whose barracks is of level V or more and has its dog academy sends every soldier out with a dog of its kennel. */
 public static boolean dogs(ServerLevel l,org.villageastra.server.SettlementData.Entry e){
  return BuildingLevels.best(l,e,BARRACKS)>=DOGS&&e.settlement().buildings().stream().anyMatch(b->b.type().equals(ACADEMY));}
 /** SoldierGoal (on campaign, every 40 ticks): the soldier's dog - taken from the free dogs of the village once (VillageDogs.follow), set on
  *  the soldier's foe. Returns the dog or null. */
 public static UUID dog(ServerLevel l,org.villageastra.server.SettlementData.Entry e,ResidentEntity soldier,net.minecraft.world.entity.LivingEntity foe){
  var t=GuardGoal.inspect(l,soldier.getUUID());UUID dog=t.hasUUID("dog")?t.getUUID("dog"):null;
  if(dog!=null&&!(l.getEntity(dog) instanceof net.minecraft.world.entity.animal.Wolf w&&w.isAlive())){t.remove("dog");GuardGoal.write(l,soldier.getUUID(),t);dog=null;}
  if(dog==null){if(!dogs(l,e))return null;var free=VillageDogs.available(l,e);if(free.isEmpty())return null;dog=free.get(0);VillageDogs.follow(l,e,dog,soldier);t.putUUID("dog",dog);GuardGoal.write(l,soldier.getUUID(),t);}
  if(foe!=null&&foe.isAlive()&&l.getEntity(dog) instanceof net.minecraft.world.entity.animal.Wolf w&&w.getTarget()!=foe)w.setTarget(foe);
  return dog;}
 /** The campaign is over for this soldier: his dog goes back to the kennel. */
 public static void release(ServerLevel l,org.villageastra.server.SettlementData.Entry e,UUID soldier){
  var t=GuardGoal.inspect(l,soldier);if(!t.hasUUID("dog"))return;VillageDogs.release(l,e,t.getUUID("dog"));t.remove("dog");GuardGoal.write(l,soldier,t);}
 // ---------------------------------------------------------------- VI: a town taken on the army's own
 public static final String CAPTURE="military.6";
 /** One fighter's weight: 1, and his weapon's tier, and half a point a piece of armour (his guard record). */
 static double weight(ServerLevel l,UUID resident){var t=GuardGoal.inspect(l,resident);double w=1;
  if(t.contains("weapon")){var s=ItemStack.of(t.getCompound("weapon"));if(!s.isEmpty())w+=tier(s);}
  w+=.5*t.getCompound("armour").getAllKeys().size();return w;}
 /** The army's strength: its living soldiers' weights. */
 public static double strength(ServerLevel l,CompoundTag army){double n=0;
  for(var raw:army.getList("soldiers",net.minecraft.nbt.Tag.TAG_INT_ARRAY)){var id=net.minecraft.nbt.NbtUtils.loadUUID(raw);if(l.getEntity(id) instanceof ResidentEntity npc&&npc.isAlive())n+=weight(l,id);}return n;}
 /** A village's defence: its guards', archers' and soldiers' weights, and two a level of its Defence (its wall and towers). */
 public static double defence(ServerLevel l,org.villageastra.server.SettlementData.Entry e){double n=2*Walls.defence(l,e);
  for(var r:e.settlement().residents())if(r.alive()&&r.profession()!=null&&r.profession().military())n+=weight(l,r.id());return n;}
}
