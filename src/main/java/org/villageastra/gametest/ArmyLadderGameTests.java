package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-159 (owner's Military ladder): the barracks has 8/12/16 places by its level, filled only by adults nobody else needs; its soldiers
 *  take stone at I, iron weapons and armour at II, diamond at III. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ArmyLadderGameTests {
 private static int soldiers(Settlement s){return (int)s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.SOLDIER).count();}
 private static void trained(Settlement s,UUID home,int n){for(int i=0;i<n;i++){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);r.restoreTraining(true,false,0);s.admit(r,home);}}
 @GameTest(template="empty",timeoutTicks=100) public static void theBarracksArmyTakesItsPlacesByLevel(GameTestHelper h){
  var l=h.getLevel();var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var barracks=new Settlement.Building(Settlement.childId(s.id(),"building/barracks"),"barracks",20,0,0);s.addBuilding(barracks);
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,40,true);s.addHome(home);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,2,2)));SettlementData.get(l.getServer()).add(e);
  try{
   h.assertTrue(Staff.slots("barracks",1)==8&&Staff.slots("barracks",2)==12&&Staff.slots("barracks",3)==16&&Staff.slots("barracks",6)==16,"Places 8/12/16");
   trained(s,home.id(),12);for(int i=0;i<30&&Population.assign(e);i++){}
   h.assertTrue(soldiers(s)==8,"Level I: eight soldiers of twelve trained adults: "+soldiers(s));
   s.raiseBuildingLevel(barracks.id(),2);for(int i=0;i<30&&Population.assign(e);i++){}
   int two=soldiers(s);h.assertTrue(two>8&&two<=12,"Level II: more places, up to twelve: "+two);
   var farm=new Settlement.Building(Settlement.childId(s.id(),"building/farm"),"farm",-20,0,0);s.addBuilding(farm);
   var idle=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(idle,home.id());for(int i=0;i<30&&Population.assign(e);i++){}
   h.assertTrue(idle.profession()==Profession.FARMER||s.residents().stream().anyMatch(r->r.profession()==Profession.FARMER),"A workplace still gets its worker before the barracks' further places");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=200) public static void theArmyGearFollowsTheBarracksLevel(GameTestHelper h){
  var t=ResearchV2Town.town(h,"barracks");var b=t.kept();var at=LogisticsRoutes.position(t.e,b);
  if(!(t.l.getBlockEntity(at) instanceof OwnedChestEntity))t.l.setBlock(at,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  try{
   var chest=LogisticsRoutes.chest(t.l,t.e,b);
   java.util.function.IntFunction<net.minecraft.nbt.CompoundTag> soldier=level->{var kept=ResearchV2Town.raise(t,level);
    h.assertTrue(BuildingLevels.level(t.l,t.e,kept)==level,"The barracks works at "+level+": "+BuildingLevels.level(t.l,t.e,kept));
    chest.clearContent();int i=0;for(var item:List.of(net.minecraft.world.item.Items.STONE_SWORD,net.minecraft.world.item.Items.IRON_SWORD,net.minecraft.world.item.Items.DIAMOND_SWORD,
      net.minecraft.world.item.Items.IRON_CHESTPLATE,net.minecraft.world.item.Items.DIAMOND_CHESTPLATE))chest.setItem(i++,new net.minecraft.world.item.ItemStack(item));
    return GuardGoal.equip(t.l,t.e,kept,UUID.randomUUID(),false);};
   java.util.function.Function<net.minecraft.nbt.CompoundTag,String> weapon=r->net.minecraft.world.item.ItemStack.of(r.getCompound("weapon")).getItem().toString();
   java.util.function.Function<net.minecraft.nbt.CompoundTag,String> chestplate=r->r.getCompound("armour").contains("chest")?net.minecraft.world.item.ItemStack.of(r.getCompound("armour").getCompound("chest")).getItem().toString():"none";
   var one=soldier.apply(1);h.assertTrue(weapon.apply(one).equals("stone_sword")&&chestplate.apply(one).equals("none"),"I: a stone sword, no armour: "+weapon.apply(one)+" "+chestplate.apply(one));
   var two=soldier.apply(2);h.assertTrue(weapon.apply(two).equals("iron_sword")&&chestplate.apply(two).equals("iron_chestplate"),"II: iron sword and armour: "+weapon.apply(two)+" "+chestplate.apply(two));
   var three=soldier.apply(3);h.assertTrue(weapon.apply(three).equals("diamond_sword")&&chestplate.apply(three).equals("diamond_chestplate"),"III: diamond: "+weapon.apply(three)+" "+chestplate.apply(three));
  }finally{ResearchV2Town.done(t);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=200) public static void theArmySpecialitiesFromLevelFour(GameTestHelper h){
  var t=ResearchV2Town.town(h,"barracks");var kept=ResearchV2Town.raise(t,4);var at=LogisticsRoutes.position(t.e,kept);
  if(!(t.l.getBlockEntity(at) instanceof OwnedChestEntity))t.l.setBlock(at,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{
   h.assertTrue(BuildingLevels.level(t.l,t.e,kept)==4,"The barracks works at IV");
   h.assertTrue(Army.speciality(UUID.randomUUID(),3)==Army.Speciality.NONE,"No speciality before IV");
   var ids=new EnumMap<Army.Speciality,UUID>(Army.Speciality.class);while(ids.size()<3){var id=UUID.randomUUID();ids.putIfAbsent(Army.speciality(id,4),id);}
   var chest=LogisticsRoutes.chest(t.l,t.e,kept);
   java.util.function.Function<Army.Speciality,net.minecraft.nbt.CompoundTag> equip=sp->{chest.clearContent();int i=0;
    for(var item:List.of(net.minecraft.world.item.Items.IRON_SWORD,net.minecraft.world.item.Items.IRON_AXE,net.minecraft.world.item.Items.BOW,net.minecraft.world.item.Items.SHIELD))chest.setItem(i++,new net.minecraft.world.item.ItemStack(item));
    chest.setItem(i,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW,32));return GuardGoal.equip(t.l,t.e,kept,ids.get(sp),false);};
   java.util.function.Function<net.minecraft.nbt.CompoundTag,net.minecraft.world.item.Item> weapon=r->net.minecraft.world.item.ItemStack.of(r.getCompound("weapon")).getItem();
   var archer=equip.apply(Army.Speciality.ARCHER);var berserker=equip.apply(Army.Speciality.BERSERKER);var defender=equip.apply(Army.Speciality.DEFENDER);
   h.assertTrue(weapon.apply(archer)==net.minecraft.world.item.Items.BOW&&archer.getInt("arrows")>0,"An archer takes a bow and arrows");
   h.assertTrue(weapon.apply(berserker)==net.minecraft.world.item.Items.IRON_AXE,"A berserker takes the axe");
   h.assertTrue(weapon.apply(defender)==net.minecraft.world.item.Items.IRON_SWORD&&defender.contains("shield"),"A defender takes a sword and a shield");
   var r=new Resident(ids.get(Army.Speciality.DEFENDER),Resident.Life.ADULT,false,null,null,-1);r.restoreTraining(true,false,0);t.s.admit(r,t.s.homes().iterator().next().id());t.s.assign(r.id(),Profession.SOLDIER,kept.id());
   npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+1.5,0,0);t.l.addFreshEntity(npc);
   h.assertTrue(Math.abs(Army.taken(npc,10F)-10F*Army.SHIELDED)<.01F,"The defender's shield takes part of a blow: "+Army.taken(npc,10F));
  }finally{npc.discard();ResearchV2Town.done(t);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=200) public static void theArmyDogGoesWithItsSoldierFromTheAcademy(GameTestHelper h){
  var t=ResearchV2Town.town(h,"barracks");var kept=ResearchV2Town.raise(t,5);var at=LogisticsRoutes.position(t.e,kept);
  var wolf=net.minecraft.world.entity.EntityType.WOLF.create(t.l);wolf.setTame(true);wolf.moveTo(at.getX()+.5,at.getY()+1,at.getZ()+3.5,0,0);t.l.addFreshEntity(wolf);
  var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(t.l);zombie.setNoAi(true);zombie.moveTo(at.getX()+4.5,at.getY()+1,at.getZ()+3.5,0,0);t.l.addFreshEntity(zombie);
  var npc=VillageAstra.RESIDENT.get().create(t.l);var followed=new ArrayList<UUID>();var released=new ArrayList<UUID>();
  VillageDogs.provide(new VillageDogs.Source(){
   @Override public List<UUID> free(net.minecraft.server.level.ServerLevel x,SettlementData.Entry y){return followed.contains(wolf.getUUID())&&!released.contains(wolf.getUUID())?List.of():List.of(wolf.getUUID());}
   @Override public void follow(net.minecraft.server.level.ServerLevel x,SettlementData.Entry y,UUID d,ResidentEntity c){followed.add(d);}
   @Override public void release(net.minecraft.server.level.ServerLevel x,SettlementData.Entry y,UUID d){released.add(d);}});
  try{
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);r.restoreTraining(true,false,0);t.s.admit(r,t.s.homes().iterator().next().id());t.s.assign(r.id(),Profession.SOLDIER,kept.id());
   npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);npc.moveTo(at.getX()+1.5,at.getY()+1,at.getZ()+3.5,0,0);t.l.addFreshEntity(npc);
   h.assertTrue(BuildingLevels.level(t.l,t.e,kept)==5,"The barracks works at V");
   h.assertTrue(!Army.dogs(t.l,t.e)&&Army.dog(t.l,t.e,npc,zombie)==null,"Without the academy no soldier takes a dog");
   t.s.addBuilding(new Settlement.Building(Settlement.childId(t.s.id(),"building/academy"),Army.ACADEMY,-5,0,0));
   var dog=Army.dog(t.l,t.e,npc,zombie);
   h.assertTrue(Army.dogs(t.l,t.e)&&wolf.getUUID().equals(dog)&&followed.contains(dog)&&wolf.getTarget()==zombie,"With the academy the soldier takes the kennel's wolf and sets it on his foe");
   h.assertTrue(wolf.getUUID().equals(Army.dog(t.l,t.e,npc,zombie))&&followed.size()==1,"He keeps the same dog");
   Army.release(t.l,t.e,npc.getUUID());h.assertTrue(released.contains(wolf.getUUID())&&!GuardGoal.inspect(t.l,npc.getUUID()).hasUUID("dog"),"After the campaign the dog goes back");
  }finally{VillageDogs.provide(VillageWolves.DOGS);npc.discard();wolf.discard();zombie.discard();ResearchV2Town.done(t);}
  h.succeed();
 }
}
