package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-033: guards and archers take real equipment from their building chest, patrol the settlement and fight hostile mobs near residents. */
public final class GuardGoal extends Goal {
 public static final int DEFEND_RADIUS=40,SIGHT=24,MELEE_REACH_SQ=9,RANGED_MAX_SQ=256,MELEE_COOLDOWN=20,RANGED_COOLDOWN=30,ARROWS_TAKEN=16;
 private final ResidentEntity guard;private LivingEntity target;private int cooldown;private BlockPos patrol;private int patrolTicks;private int postLevel;
 /** AD-112: how far from the settlement a guard of a guard house at this working level defends it. */
 public static int defendRadius(int level){return CoreEffects.value("guard_house","defend",level);}
 /** AD-112: how far a guard sees from a post of this type at its working level (a guard house and a range by their cores; a wall tower keeps SIGHT). */
 public static int sight(String post,int level){return post.equals("guard_house")||post.equals("archery")?CoreEffects.value(post,"sight",level):post.equals(Walls.TOWER)?Math.max(SIGHT,towerReach(level)):SIGHT;}
 /** AD-157 (owner's Defence ladder): how far a wall tower's archers shoot by the village's Defence (a tower's "level" is the Defence):
  *  16 before II (the AD-094 tower), 24 at II-III (wood), 48 at IV (small stone), 64 from V (big stone). */
 public static int towerReach(int defence){return defence>=5?64:defence>=4?48:defence>=2?24:16;}
 /** AD-112: the square of an archer's reach from a range at its working level; a wall tower keeps RANGED_MAX_SQ. */
 public static int rangedMaxSq(String post,int level){if(post.equals(Walls.TOWER)){int r=towerReach(level);return r*r;}if(!post.equals("archery"))return RANGED_MAX_SQ;int reach=CoreEffects.value("archery","reach",level);return reach*reach;}
 /** The defend radius around the settlement for a guard of this post (only a guard house's core widens it). */
 public static int defendRadius(String post,int level){return post.equals("guard_house")?defendRadius(level):DEFEND_RADIUS;}
 private static int postLevel(ServerLevel l,SettlementData.Entry e,Settlement.Building post){return post==null?1:post.type().equals(Walls.TOWER)?Math.max(1,Walls.defence(l,e)):BuildingLevels.level(l,e,post);}
 /** The knobs of a post as it stands in the world (its working level read from its equipment and core). */
 public static int defendRadius(ServerLevel l,SettlementData.Entry e,Settlement.Building post){return defendRadius(post.type(),postLevel(l,e,post));}
 public static int sight(ServerLevel l,SettlementData.Entry e,Settlement.Building post){return sight(post.type(),postLevel(l,e,post));}
 public static int rangedMaxSq(ServerLevel l,SettlementData.Entry e,Settlement.Building post){return rangedMaxSq(post.type(),postLevel(l,e,post));}
 private final boolean withoutPlayers;
 public GuardGoal(ResidentEntity guard){this(guard,false);}
 /** GameTest servers have no players; the flag only lifts the active-time player requirement. */
 public GuardGoal(ResidentEntity guard,boolean withoutPlayers){this.guard=guard;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 /** AD-159: writes a guard record back (an archer soldier's arrows). */
 public static void write(ServerLevel l,UUID resident,CompoundTag t){NbtRecord.write(path(l,resident),t);}
 public static Path path(ServerLevel l,UUID resident){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-guard/"+resident+".bin");}
 public static CompoundTag inspect(ServerLevel l,UUID resident){var p=path(l,resident);return Files.exists(p)?NbtRecord.read(p):new CompoundTag();}
 private record Duty(SettlementData.Entry entry,Settlement.Building post,boolean archer){}
 private Duty duty(){
  if(!(guard.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0&&!withoutPlayers||guard.settlementId()==null||guard.escortPlayer()!=null||!guard.isAlive())return null;
  var e=SettlementData.get(l.getServer()).entry(guard.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(guard.getUUID());var b=e.settlement().workplace(guard.getUUID());
  if(r==null||!r.alive()||b==null)return null;
  if(r.profession()==Profession.GUARD&&b.type().equals("guard_house"))return new Duty(e,b,false);
  if(r.profession()==Profession.ARCHER_GUARD&&(b.type().equals("archery")||b.type().equals(Walls.TOWER)))return new Duty(e,b,true);
  return null;
 }
 public static boolean weapon(ItemStack s,boolean archer){return archer?s.getItem() instanceof BowItem:s.getItem() instanceof SwordItem||s.getItem() instanceof AxeItem;}
 /** Takes one weapon (and a quiver of arrows for archers) through the journal; returns the current record. */
 public static CompoundTag equip(ServerLevel l,SettlementData.Entry e,Settlement.Building post,UUID resident,boolean archer){
  var file=path(l,resident);var t=inspect(l,resident);if(t.isEmpty()){t.putInt("schema",1);t.putUUID("resident",resident);t.putInt("takes",0);}
  var chest=LogisticsRoutes.chest(l,e,post);if(chest==null)return t;var pos=LogisticsRoutes.position(e,post);boolean changed=false;
  boolean barracks=post.type().equals(Army.BARRACKS);int cap=barracks?Army.cap(BuildingLevels.level(l,e,post)):4;
  // AD-159 IV: a soldier's speciality decides his weapon: an archer a bow and arrows, a berserker an axe, a defender a sword and a shield.
  var speciality=barracks?Army.speciality(resident,BuildingLevels.level(l,e,post)):Army.Speciality.NONE;if(speciality==Army.Speciality.ARCHER)archer=true;
  if(!t.contains("weapon")||ItemStack.of(t.getCompound("weapon")).isEmpty()){
   var id=Settlement.childId(resident,"equip/"+t.getInt("takes"));var got=WorldJournal.recoverAmount(l,id);
   // AD-159: a soldier of the barracks takes the best weapon its level allows; a guard any weapon, as before.
   if(got.isEmpty()&&!WorldJournal.exists(l,id)){int slot=barracks?speciality==Army.Speciality.BERSERKER?Army.bestAxe(chest,cap):Army.bestWeapon(chest,archer,cap):-1;
    if(!barracks)for(int i=0;i<chest.getContainerSize();i++)if(weapon(chest.getItem(i),archer)){slot=i;break;}
    if(slot>=0)got=WorldJournal.takeAmount(l,id,pos,slot,chest.getItem(slot).copy(),1);}
   if(!got.isEmpty()){t.put("weapon",got.save(new CompoundTag()));t.putInt("takes",t.getInt("takes")+1);changed=true;}
  }
  if(barracks)changed|=Army.armour(l,resident,t,chest,pos,cap);
  if(speciality==Army.Speciality.DEFENDER)changed|=Army.shield(l,resident,t,chest,pos);
  if(archer&&t.getInt("arrows")<=0){
   var id=Settlement.childId(resident,"equip/"+t.getInt("takes"));var got=WorldJournal.recoverAmount(l,id);
   if(got.isEmpty()&&!WorldJournal.exists(l,id))for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(Items.ARROW)){got=WorldJournal.takeAmount(l,id,pos,slot,chest.getItem(slot).copy(),Math.min(ARROWS_TAKEN,chest.getItem(slot).getCount()));break;}
   if(!got.isEmpty()){t.putInt("arrows",got.getCount());t.putInt("takes",t.getInt("takes")+1);changed=true;}
  }
  if(changed)NbtRecord.write(file,t);
  return t;
 }
 /** Nearest hostile mob close to the settlement or to one of its residents. */
 public static LivingEntity threat(ServerLevel l,SettlementData.Entry e,ResidentEntity guard){
  // AD-094: an archer on a wall tower also watches the ground before its tower — a big wall stands beyond the watch's usual reach.
  var post=e.settlement().workplace(guard.getUUID());var stand=post!=null&&post.type().equals(Walls.TOWER)?platform(e,post):null;
  // AD-112: the post's core widens the guard's sight and the guard house's the ring it defends.
  int level=postLevel(l,e,post);String type=post==null?"":post.type();int sight=sight(type,level),defend=defendRadius(type,level);
  var box=guard.getBoundingBox().inflate(sight);LivingEntity best=null;double bestDistance=Double.MAX_VALUE;
  for(var mob:l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,box,m->m instanceof Enemy&&m.isAlive())){
   boolean nearVillage=mob.blockPosition().distSqr(e.center())<=(double)defend*defend
     ||stand!=null&&mob.distanceToSqr(stand.getX()+.5,stand.getY(),stand.getZ()+.5)<=sight*sight;
   if(!nearVillage)continue;double d=mob.distanceToSqr(guard);if(d<bestDistance){best=mob;bestDistance=d;}
  }
  // AD-065: soldiers of an army marching on this settlement are fought like any raider.
  var soldier=Battle.invader(l,e,guard,sight);
  if(soldier!=null&&soldier.distanceToSqr(guard)<bestDistance)best=soldier;
  return best;
 }
 @Override public boolean canUse(){var d=duty();return d!=null&&CargoCustody.mayStartWork(guard);}
 @Override public boolean canContinueToUse(){return duty()!=null;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var d=duty();if(d==null)return;var l=(ServerLevel)guard.level();if(cooldown>0)cooldown--;
  // Read at once on the first tick (a fresh guard of a level-VI post is not a level-I one for two seconds), then every two seconds.
  if(postLevel==0||guard.tickCount%40==0)postLevel=postLevel(l,d.entry(),d.post());String type=d.post().type();int sight=sight(type,postLevel),rangedMaxSq=rangedMaxSq(type,postLevel);
  var record=guard.tickCount%40==0?equip(l,d.entry(),d.post(),guard.getUUID(),d.archer()):inspect(l,guard.getUUID());
  var weapon=record.contains("weapon")?ItemStack.of(record.getCompound("weapon")):ItemStack.EMPTY;guard.displayWorkItem(weapon);
  if(weapon.isEmpty()||(d.archer()&&record.getInt("arrows")<=0)){
   var pos=LogisticsRoutes.position(d.entry(),d.post());if(guard.distanceToSqr(pos.getX()+1.5,pos.getY(),pos.getZ()+.5)>6.25)guard.getNavigation().moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5,.8);
   guard.workStatus("guard_missing_equipment");return;
  }
  if(target==null||!target.isAlive()||target.distanceToSqr(guard)>sight*sight||guard.tickCount%20==0)target=threat(l,d.entry(),guard);
  if(target!=null){
   guard.getLookControl().setLookAt(target,30,30);double dist=guard.distanceToSqr(target);
   boolean tower=d.post().type().equals(Walls.TOWER);
   if(d.archer()){
    // AD-094: an archer of a tower holds the tower — it shoots what it can see from up there and never runs out after it.
    if(tower&&(dist>rangedMaxSq||!guard.hasLineOfSight(target))){man(d);guard.workStatus("guard_on_tower");return;}
    if(dist>rangedMaxSq||!guard.hasLineOfSight(target))guard.getNavigation().moveTo(target,.9);else{guard.getNavigation().stop();if(cooldown==0){shoot(l,record,weapon,target);cooldown=RANGED_COOLDOWN;}}
   }else if(dist>MELEE_REACH_SQ)guard.getNavigation().moveTo(target,1.0);
   else if(cooldown==0){strike(l,record,weapon,target);cooldown=MELEE_COOLDOWN;}
   guard.workStatus("guard_fighting");return;
  }
  if(d.post().type().equals(Walls.TOWER)){man(d);guard.workStatus("guard_on_tower");return;}
  if(--patrolTicks<=0||patrol==null){
   // SAFE-004: existing guards walk the corridor between the village and its real road posts.
   var posts=Roads.posts(l.getServer(),d.entry().settlement().id());
   if(posts.length>0&&guard.getRandom().nextInt(3)>0){var post=BlockPos.of(posts[guard.getRandom().nextInt(posts.length)]);
    if(post.distSqr(d.entry().center())<=(long)Roads.PATROL_RADIUS*Roads.PATROL_RADIUS){patrol=post;patrolTicks=600;guard.getNavigation().moveTo(patrol.getX()+.5,patrol.getY(),patrol.getZ()+.5,.75);guard.workStatus("guard_patrol");return;}}
   var buildings=new ArrayList<>(d.entry().settlement().buildings());patrol=LogisticsRoutes.position(d.entry(),buildings.get(guard.getRandom().nextInt(buildings.size())));patrolTicks=400;guard.getNavigation().moveTo(patrol.getX()+2.5,patrol.getY(),patrol.getZ()+.5,.6);}
  guard.workStatus("guard_patrol");
 }
 private void wear(ServerLevel l,CompoundTag record,ItemStack weapon){
  var worn=weapon.copy();worn.setDamageValue(worn.getDamageValue()+1);
  if(worn.getDamageValue()>=worn.getMaxDamage())record.remove("weapon");else record.put("weapon",worn.save(new CompoundTag()));
  NbtRecord.write(path(l,guard.getUUID()),record);
 }
 private void strike(ServerLevel l,CompoundTag record,ItemStack weapon,LivingEntity target){
  float damage=1F;for(var modifier:weapon.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))damage+=(float)modifier.getAmount();
  guard.swing(net.minecraft.world.InteractionHand.MAIN_HAND);if(target.hurt(l.damageSources().mobAttack(guard),damage))wear(l,record,weapon);
 }
 /** AD-157: an archer's shot at a target dx,dy,dz away. A far shot flies faster (a full bow draw at 64) and is aimed over the arrow's
  *  drop; near shots (16 and less) as before. */
 public static void aim(net.minecraft.world.entity.projectile.AbstractArrow arrow,double dx,double dy,double dz){
  double far=Math.sqrt(dx*dx+dz*dz);float speed=(float)Math.min(3.0,1.6+Math.max(0,far-16)/34);double lift=far<=16?far*.2:.028*(far/speed)*(far/speed);
  arrow.shoot(dx,dy+lift,dz,speed,far<=16?4F:.25F);
 }
 private void shoot(ServerLevel l,CompoundTag record,ItemStack bow,LivingEntity target){
  var arrow=new Arrow(l,guard);double dx=target.getX()-guard.getX(),dy=target.getY(0.33)-arrow.getY(),dz=target.getZ()-guard.getZ();
  aim(arrow,dx,dy,dz);arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;l.addFreshEntity(arrow);
  record.putInt("arrows",record.getInt("arrows")-1);guard.swing(net.minecraft.world.InteractionHand.MAIN_HAND);wear(l,record,bow);
 }
 /** The platform of a wall tower, where its archer stands: reached up the tower's own stair. */
 public static net.minecraft.core.BlockPos platform(SettlementData.Entry e,org.villageastra.domain.Settlement.Building tower){return BuildingPlacement.at(e,tower,3,8,3);}
 /** Where this archer stands on the platform: the first of the tower's archers at the far corner, the second at the other one. */
 static net.minecraft.core.BlockPos stand(SettlementData.Entry e,org.villageastra.domain.Settlement.Building tower,UUID archer){
  var s=e.settlement();var mates=s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.ARCHER_GUARD&&s.workplace(r.id())!=null&&s.workplace(r.id()).id().equals(tower.id()))
   .map(org.villageastra.domain.Resident::id).sorted().toList();
  return mates.indexOf(archer)%2==1?BuildingPlacement.at(e,tower,3,8,1):platform(e,tower);}
 private void man(Duty d){var at=stand(d.entry(),d.post(),guard.getUUID());
  if(guard.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)>1.5)guard.getNavigation().moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,.8);else guard.getNavigation().stop();}
 @Override public void stop(){guard.getNavigation().stop();target=null;}
}
