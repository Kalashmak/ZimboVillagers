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
