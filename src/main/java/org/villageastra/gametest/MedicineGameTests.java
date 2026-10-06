package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-152: the sick do not work and stay sick through a save; about SICK_PERMILLE of the residents fall ill a day; a hospital of level I
 *  with its medic at work cures as many at once as it has beds — one — each after CURE_TICKS for one real bandage. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MedicineGameTests {
 @GameTest(template="empty",batch="patient_home_rest",timeoutTicks=100)
 public static void sickWorkerStaysHomeUntilRecoveredWhileMayorKeepsFoodOffice(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);ResidentEntity npc=null;
  try{var home=new Settlement.Building(Settlement.childId(t.s.id(),"patientHome"),"home",14,0,0);t.s.addBuilding(home);t.s.addHome(new Settlement.Home(home.id(),1,2,true));
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,home.id());t.s.assign(r.id(),Profession.BUILDER,t.hall().id());r.fallIll();npc=VillageAstra.RESIDENT.get().create(t.l);npc.bind(t.s.id(),r);npc.setNoAi(true);var size=BuildingPlacement.size("home",0);var at=BuildingPlacement.origin(t.e,home).offset(size[0]/2,1,size[1]/2);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);t.l.addFreshEntity(npc);
   var goal=new PatientGoal(npc);h.assertTrue(goal.canUse()&&goal.canContinueToUse(),"Sick worker's home rest remains active even after arrival");npc.tickCount=20;goal.tick();h.assertTrue(npc.getNavigation().isDone()&&npc.workStatus().equals("resting"),"At home patient rests rather than repeatedly strolling away");r.cure();h.assertTrue(!goal.canContinueToUse(),"Cure immediately releases work goals");r.fallIll();t.s.assign(r.id(),Profession.MAYOR,t.hall().id());h.assertTrue(!new PatientGoal(npc).canUse(),"Before a clinic the mayor keeps emergency food and planning work");
  }finally{if(npc!=null)npc.discard();Medicine.forget(t.s.id());ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="illness_daily_roll",timeoutTicks=100)
 public static void diseaseRollVariesByDayWithoutFortyConsecutiveSickDays(GameTestHelper h){
  var id=new UUID(31,17);int ill=0,run=0,longest=0;
  for(int day=0;day<10000;day++){boolean a=Medicine.fallsIll(id,day);h.assertTrue(a==Medicine.fallsIll(id,day),"Same resident/day is reproducible");if(a){ill++;run++;longest=Math.max(longest,run);}else run=0;}
  h.assertTrue(ill>250&&ill<550,"Longitudinal daily chance stays close to4%: "+ill);h.assertTrue(longest<6,"One person must not receive forty consecutive illness rolls from linear hashing: "+longest);h.succeed();
 }
 @GameTest(template="empty",batch="rest_recovery",timeoutTicks=100)
 public static void fedRestAtHomeSurvivesSaveWithoutOfflineOrHungryRecovery(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);ResidentEntity npc=null;
  try{
   var home=new Settlement.Building(Settlement.childId(t.s.id(),"restHome"),"home",14,0,0);t.s.addBuilding(home);t.s.addHome(new Settlement.Home(home.id(),1,2,true));
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,home.id());r.fallIll();r.ate(1000);r.restoreRecoveryRest(Medicine.REST_RECOVERY-40);
   npc=VillageAstra.RESIDENT.get().create(t.l);npc.bind(t.s.id(),r);npc.setNoAi(true);var at=BuildingPlacement.origin(t.e,home);var size=BuildingPlacement.size("home",0);npc.moveTo(at.getX()+size[0]/2+.5,at.getY()+1,at.getZ()+size[1]/2+.5);t.l.addFreshEntity(npc);
   Medicine.restRecovery(t.l,t.e,1000);Medicine.restRecovery(t.l,t.e,1000);h.assertTrue(r.recoveryRest()==Medicine.REST_RECOVERY-40,"First/repeated clock creates no rest");
   Medicine.restRecovery(t.l,t.e,1020);var copy=SettlementData.load(SettlementData.get(t.l.getServer()).save(new net.minecraft.nbt.CompoundTag())).entry(t.s.id()).settlement().resident(r.id());h.assertTrue(copy.sick()&&copy.recoveryRest()==Medicine.REST_RECOVERY-20,"Illness and earned physical rest survive a save");
   r.missedMeal(1030);Medicine.restRecovery(t.l,t.e,1040);h.assertTrue(r.sick()&&r.recoveryRest()==Medicine.REST_RECOVERY-20,"Missing meals stops recovery");
   r.ate(1060);npc.moveTo(at.getX()+60,at.getY()+1,at.getZ());Medicine.restRecovery(t.l,t.e,1060);h.assertTrue(r.sick(),"Being away from home does not recover");npc.moveTo(at.getX()+size[0]/2+.5,at.getY()+1,at.getZ()+size[1]/2+.5);
   Medicine.forget(t.s.id());Medicine.restRecovery(t.l,t.e,1060);h.assertTrue(r.sick(),"Restart creates no elapsed rest");Medicine.restRecovery(t.l,t.e,1080);h.assertTrue(!r.sick()&&r.recoveryRest()==0&&Population.mayWork(r),"Fed physical rest completes natural recovery and permits work again");
   r.fallIll();r.ate(1000000);Medicine.restRecovery(t.l,t.e,1000000);h.assertTrue(r.recoveryRest()==20,"A large unloaded time gap counts at most one actual pass");
  }finally{if(npc!=null)npc.discard();Medicine.forget(t.s.id());ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theSickDoNotWorkAndTheDayBringsIllness(GameTestHelper h){
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,Profession.FORESTER,null,-1);
  h.assertTrue(Population.mayWork(r)&&r.fallIll()&&r.sick()&&!Population.mayWork(r)&&!r.fallIll(),"Ill: no work");
  h.assertTrue(r.cure()&&!r.sick()&&Population.mayWork(r),"Cured: back to work");
  int ill=0;for(int i=0;i<10000;i++)if(Medicine.fallsIll(new UUID(i*31L,i*17L),i%97))ill++;
  h.assertTrue(Math.abs(ill-Medicine.SICK_PERMILLE*10)<Medicine.SICK_PERMILLE*10/2+50,"About "+Medicine.SICK_PERMILLE/10.0+"% a day: "+ill+" of 10000");
  h.assertTrue(Medicine.beds(1)==1&&Medicine.beds(2)==2&&Medicine.beds(3)==4&&Medicine.beds(4)==6&&Medicine.beds(5)==8&&Medicine.beds(6)==12,"Beds 1/2/4/6/8/12");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theHospitalCuresAsManyAsItHasBeds(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,2,4));var s=new Settlement(UUID.randomUUID());
  var clinic=new Settlement.Building(Settlement.childId(s.id(),"building/clinic"),"clinic",0,0,0);s.addBuilding(clinic);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var home=new Settlement.Home(UUID.randomUUID(),1,6,true);s.addHome(home);var bodies=new ArrayList<ResidentEntity>();
  try{
   var at=LogisticsRoutes.position(e,clinic);l.setBlock(at,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,clinic);
   chest.setItem(0,new ItemStack(VillageAstra.BANDAGE.get(),3));
   var doctor=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(doctor,home.id());s.assign(doctor.id(),Profession.DOCTOR,clinic.id());
   var sick=new ArrayList<Resident>();
   for(int i=0;i<3;i++){var r=i==0?doctor:new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);if(i>0){s.admit(r,home.id());r.fallIll();sick.add(r);}
    var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),r);npc.setNoAi(true);npc.moveTo(at.getX()+1.5+i,at.getY()+1,at.getZ()+.5,0,0);l.addFreshEntity(npc);bodies.add(npc);}
   long now=SettlementData.get(l.getServer()).clock().ticks();Medicine.tick(l,e,now);
   for(long t=200;t<=Medicine.CURE_TICKS+200;t+=200)Medicine.tick(l,e,now+t);
   long cured=sick.stream().filter(r->!r.sick()).count();
   h.assertTrue(cured==1,"One bed at level I: one of the two cured, the other waits: "+cured);
   h.assertTrue(chest.getItem(0).getCount()==2,"One bandage for the cure: "+chest.getItem(0));
   var loaded=SettlementData.load(SettlementData.get(l.getServer()).save(new net.minecraft.nbt.CompoundTag())).entry(s.id());
   var waiting=sick.stream().filter(Resident::sick).findFirst().orElseThrow();
   h.assertTrue(loaded!=null&&loaded.settlement().resident(waiting.id()).sick(),"The one still sick stays sick through a save");
  }finally{for(var b:bodies)b.discard();Medicine.forget(s.id());SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
