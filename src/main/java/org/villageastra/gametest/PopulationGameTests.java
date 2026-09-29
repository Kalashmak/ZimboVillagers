package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-031: meals from real stock, hunger, births into free homes, growing up, school-only education and the labor office. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PopulationGameTests {
 private record Village(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building hall,Settlement.Home home){
  OwnedChestEntity chest(Settlement.Building b){return LogisticsRoutes.chest(l,e,b);}
 }
 private static Settlement.Building building(GameTestHelper h,Settlement s,BlockPos center,String type,int x,int z){
  var b=new Settlement.Building(Settlement.childId(s.id(),"building/"+type+"/"+x+"/"+z),type,x,0,z);s.addBuilding(b);
  var chest=center.offset(x+1,1,z+4);h.getLevel().setBlock(chest.below(),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  for(int dx=0;dx<5;dx++)for(int dz=0;dz<5;dz++)h.getLevel().setBlock(center.offset(x+dx,0,z+dz),Blocks.STONE.defaultBlockState(),2);
  return b;
 }
 private static Village village(GameTestHelper h,int capacity,int adults){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());
  var hall=building(h,s,center,"town_hall",0,0);var homeBuilding=building(h,s,center,"home",14,0);
  var home=new Settlement.Home(homeBuilding.id(),1,capacity,true);s.addHome(home);
  for(int i=0;i<adults;i++){var r=new Resident(Settlement.childId(s.id(),"adult/"+i),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home.id());}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Village(l,s,e,hall,home);
 }
 @GameTest(template="empty",timeoutTicks=100) public static void mealsUseRealStockAndHungerStopsOrdinaryWork(GameTestHelper h){
  var v=village(h,2,1);var r=v.s.residents().iterator().next();v.chest(v.hall).setItem(0,new ItemStack(Items.BREAD,2));
  long t=1000;Population.meal(v.l,v.e,r,t);h.assertTrue(r.lastMeal()==t,"First tick starts the meal clock");
  t+=Population.MEAL_INTERVAL;Population.meal(v.l,v.e,r,t);Population.meal(v.l,v.e,r,t);
  h.assertTrue(v.chest(v.hall).countItem(Items.BREAD)==1&&r.missedMeals()==0,"One bread per due meal, repeated call does not eat twice");
  t+=Population.MEAL_INTERVAL;Population.meal(v.l,v.e,r,t);h.assertTrue(v.chest(v.hall).countItem(Items.BREAD)==0,"Second meal eats the last bread");
  for(int i=0;i<Population.HUNGRY;i++){t+=Population.MEAL_INTERVAL;Population.meal(v.l,v.e,r,t);}
  h.assertTrue(r.missedMeals()==Population.HUNGRY&&!Population.mayWork(r),"Missed meals stop ordinary work");
  r.assign(Profession.FARMER);h.assertTrue(Population.mayWork(r),"Farmers keep working on food while hungry");
  v.chest(v.hall).setItem(0,new ItemStack(Items.BAKED_POTATO,4));t+=Population.MEAL_INTERVAL;Population.meal(v.l,v.e,r,t);
  h.assertTrue(r.missedMeals()==0&&v.chest(v.hall).countItem(Items.BAKED_POTATO)==3,"Any safe food ends hunger (baked potato: 5 nutrition)");
  v.chest(v.hall).setItem(1,new ItemStack(Items.ROTTEN_FLESH,8));v.chest(v.hall).setItem(0,ItemStack.EMPTY);t+=Population.MEAL_INTERVAL;Population.meal(v.l,v.e,r,t);
  h.assertTrue(r.missedMeals()==1&&v.chest(v.hall).countItem(Items.ROTTEN_FLESH)==8,"Harmful food is not eaten");
  var loaded=SettlementData.load(SettlementData.get(v.l.getServer()).save(new CompoundTag())).entry(v.s.id()).settlement().resident(r.id());
  h.assertTrue(loaded.missedMeals()==1&&loaded.lastMeal()==t,"Needs survive save/load");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void birthNeedsFreeHomeAdultsAndFood(GameTestHelper h){
  var v=village(h,3,2);long t=5000;
  h.assertTrue(Population.birth(v.l,v.e,t)==null,"No food reserve: no birth");
  v.chest(v.hall).setItem(0,new ItemStack(Items.BREAD,32));
  var child=Population.birth(v.l,v.e,t);
  h.assertTrue(child!=null&&child.life()==Resident.Life.CHILD&&v.home.id().equals(child.home())&&v.l.getEntity(child.id()) instanceof ResidentEntity npc&&npc.child(),"Child born into the free home place as a real entity");
  h.assertTrue(Population.birth(v.l,v.e,t+1)==null,"Births are rare (interval)");
  h.assertTrue(Population.birth(v.l,v.e,t+Population.BIRTH_INTERVAL)==null,"Home is full: capacity 3 with two adults and one child");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void childrenGrowUpAndOnlySchoolEducates(GameTestHelper h){
  var v=village(h,4,2);v.chest(v.hall).setItem(0,new ItemStack(Items.BREAD,40));long t=9000;
  var first=Population.birth(v.l,v.e,t);var second=Population.birth(v.l,v.e,t+Population.BIRTH_INTERVAL);
  h.assertTrue(first!=null&&second!=null,"Two children");
  var school=building(h,v.s,v.e.center(),"school",0,14);var teacher=new Resident(Settlement.childId(v.s.id(),"teacher"),Resident.Life.ADULT,false,null,null,-1);v.s.addHome(new Settlement.Home(Settlement.childId(v.s.id(),"teacher-home"),1,1,true));v.s.admit(teacher,Settlement.childId(v.s.id(),"teacher-home"));v.s.assign(teacher.id(),Profession.TEACHER,school.id());
  var station=LogisticsRoutes.position(v.e,school);var npc=VillageAstra.RESIDENT.get().create(v.l);npc.bind(v.s.id(),teacher);npc.moveTo(station.getX()+.5,station.getY(),station.getZ()+1.5,0,0);npc.setNoAi(true);v.l.addFreshEntity(npc);
  var pupil=(ResidentEntity)v.l.getEntity(second.id());pupil.setNoAi(true);pupil.teleportTo(station.getX()+1.5,station.getY(),station.getZ()+.5);
  var truant=(ResidentEntity)v.l.getEntity(first.id());truant.setNoAi(true);truant.teleportTo(v.e.center().getX()+30.5,v.e.center().getY()+1,v.e.center().getZ()+.5);
  h.assertTrue(Population.schoolStation(v.l,v.e)!=null,"School runs while its teacher is present");
  Population.grow(v.l,v.e,t+10);h.assertTrue(second.schoolTicks()==20&&first.schoolTicks()==0,"Only the child at school attends");
  second.attendSchool(Population.SCHOOL_REQUIRED);
  Population.grow(v.l,v.e,t+Population.BIRTH_INTERVAL+Population.GROW);
  h.assertTrue(first.life()==Resident.Life.ADULT&&!first.educated()&&!truant.child(),"Truant grows up uneducated");
  h.assertTrue(second.life()==Resident.Life.ADULT&&second.educated()&&!pupil.child(),"Pupil grows up educated");
  h.assertTrue(!second.educate(),"An adult can never be educated later");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void laborOfficeFillsImplementedJobsByPriority(GameTestHelper h){
  var v=village(h,6,5);building(h,v.s,v.e.center(),"farm",0,14);building(h,v.s,v.e.center(),"laboratory",14,14);building(h,v.s,v.e.center(),"restaurant",28,0);
  Population.assign(v.e);
  var roles=v.s.residents().stream().map(Resident::profession).filter(Objects::nonNull).toList();
  h.assertTrue(roles.contains(Profession.BUILDER)&&roles.contains(Profession.PORTER)&&roles.contains(Profession.FARMER)&&roles.contains(Profession.BAKER),"Builder, porter, farmer and baker assigned first: "+roles);
  h.assertTrue(!roles.contains(Profession.SCIENTIST),"Uneducated adults are never scientists");
  h.assertTrue(v.s.residents().stream().filter(r->r.profession()==Profession.BUILDER).count()==1,"One builder");
  h.succeed();
 }
}
