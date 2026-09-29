package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MedicineDeliveryGameTests {
 private record Town(ServerLevel l,SettlementData.Entry e,Settlement.Building clinic,Resident person,ResidentEntity npc) implements AutoCloseable{
  public void close(){npc.discard();Medicine.forget(e.settlement().id());SettlementData.get(l.getServer()).remove(e.settlement().id());VillageDogs.provide(VillageWolves.DOGS);}
 }
 private static Town town(GameTestHelper h){return town(h,5);}
 private static Town town(GameTestHelper h,int level){var l=h.getLevel();var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  var id=UUID.randomUUID();s.addBuilding(new Settlement.Building(id,"clinic",0,0,0));for(int n=2;n<=level;n++)s.raiseBuildingLevel(id,n);var b=s.buildings().iterator().next();for(var cell:BuildingPlacement.layout(e,b,"clinic@"+level).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  var pos=LogisticsRoutes.position(e,b);l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var c=LogisticsRoutes.chest(l,e,b);c.clearContent();c.setItem(0,new ItemStack(VillageAstra.BANDAGE.get()));
  var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());r.fallIll();var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),r);npc.setNoAi(true);var patientAt=h.absolutePos(new BlockPos(1,10,1));npc.moveTo(patientAt.getX()+.5,patientAt.getY(),patientAt.getZ()+.5,0,0);h.assertTrue(l.addFreshEntity(npc)&&l.getEntity(npc.getUUID())==npc,"Patient must be loaded before scheduling delivery");
  h.assertTrue(BuildingLevels.level(l,e,b)==level,"Clinic equipment");return new Town(l,e,b,r,npc);
 }
 private static final class Dog implements VillageDogs.Source {
  final UUID id=UUID.randomUUID();BlockPos at;boolean busy,released;
  public List<UUID> free(ServerLevel l,SettlementData.Entry e){return busy?List.of():List.of(id);}
  public boolean pulls(){return true;}
  public boolean send(ServerLevel l,SettlementData.Entry e,UUID dog,BlockPos target){busy=true;return true;}
  public boolean near(ServerLevel l,SettlementData.Entry e,UUID dog,BlockPos target,double radius){return target.equals(at);}
  public void release(ServerLevel l,SettlementData.Entry e,UUID dog){busy=false;released=true;}
 }
 @GameTest(template="empty",timeoutTicks=100,batch="medicinedeliveryneedsphysicalmedicine") public static void medicineDeliveryNeedsPhysicalMedicine(GameTestHelper h){try(var t=town(h)){VillageDogs.provide(null);Medicine.tick(t.l,t.e,100);h.assertTrue(t.person.sick()&&LogisticsRoutes.chest(t.l,t.e,t.clinic).countItem(VillageAstra.BANDAGE.get())==1,"No remote cure without a delivery or carried medicine");}h.succeed();}
 @GameTest(template="empty",timeoutTicks=100,batch="medicinedeliverywaitsforbothstopsandreceiptsurvivesuse") public static void medicineDeliveryWaitsForBothStopsAndReceiptSurvivesUse(GameTestHelper h){try(var t=town(h)){var dog=new Dog();VillageDogs.provide(dog);MedicineDelivery.tick(t.l,t.e);MedicineDelivery.tick(t.l,t.e);
  var chest=LogisticsRoutes.chest(t.l,t.e,t.clinic);h.assertTrue(chest.countItem(VillageAstra.BANDAGE.get())==1&&t.person.sick(),"Not at clinic: no debit");
  dog.at=LogisticsRoutes.position(t.e,t.clinic);MedicineDelivery.tick(t.l,t.e);var trip=MedicineDelivery.inspect(t.l,t.e.settlement().id());h.assertTrue(chest.countItem(VillageAstra.BANDAGE.get())==0&&trip.getString("stage").equals("go"),"Loaded once");MedicineDelivery.tick(t.l,t.e);h.assertTrue(t.person.sick()&&!MedicinePacks.carried(t.l,t.person.id()),"Not at patient: no cure");
  dog.at=t.npc.blockPosition();MedicineDelivery.tick(t.l,t.e);h.assertTrue(dog.released&&MedicinePacks.carried(t.l,t.person.id()),"Delivered and released");h.assertTrue(MedicinePacks.use(t.l,t.person)&&!t.person.sick(),"One medicine cures");
  t.person.fallIll();h.assertTrue(!MedicinePacks.use(t.l,t.person),"New illness needs a new paid dose");h.assertTrue(MedicinePacks.give(t.l,t.person.id(),trip.getUUID("id"))&&!MedicinePacks.carried(t.l,t.person.id()),"Replayed receipt does not refill consumed medicine");
 }h.succeed();}
 @GameTest(template="empty",timeoutTicks=100,batch="medicinedeliveryreturnsunreachablecargoonce") public static void medicineDeliveryReturnsUnreachableCargoOnce(GameTestHelper h){try(var t=town(h)){var dog=new Dog();VillageDogs.provide(dog);MedicineDelivery.tick(t.l,t.e);dog.at=LogisticsRoutes.position(t.e,t.clinic);MedicineDelivery.tick(t.l,t.e);
  for(int n=0;n<=MedicineDelivery.LOST_TICKS/20;n++)MedicineDelivery.tick(t.l,t.e);
  var chest=LogisticsRoutes.chest(t.l,t.e,t.clinic);h.assertTrue(chest.countItem(VillageAstra.BANDAGE.get())==1&&dog.released&&t.person.sick(),"Unreachable patient: stock="+chest.countItem(VillageAstra.BANDAGE.get())+" released="+dog.released+" trip="+MedicineDelivery.inspect(t.l,t.e.settlement().id()));VillageDogs.provide(null);MedicineDelivery.tick(t.l,t.e);h.assertTrue(chest.countItem(VillageAstra.BANDAGE.get())==1,"No duplicate refund");
 }h.succeed();}
 @GameTest(template="empty",timeoutTicks=100,batch="medicinedeliverycounterkeepsdoseforlater") public static void medicineDeliveryCounterKeepsDoseForLater(GameTestHelper h){try(var t=town(h)){VillageDogs.provide(null);var at=LogisticsRoutes.position(t.e,t.clinic);t.npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5,0,0);t.person.cure();for(int i=0;i<3;i++)MedicineDelivery.tick(t.l,t.e);
  h.assertTrue(MedicinePacks.carried(t.l,t.person.id()),"Healthy resident keeps the paid dose");t.person.fallIll();Medicine.tick(t.l,t.e,200);h.assertTrue(!t.person.sick()&&!MedicinePacks.carried(t.l,t.person.id())&&LogisticsRoutes.chest(t.l,t.e,t.clinic).countItem(VillageAstra.BANDAGE.get())==0,"Dose consumed for later illness, no second debit");
 }h.succeed();}
 @GameTest(template="empty",timeoutTicks=100,batch="medicinedeliveryobeyslevelsandautomaticcurekeepsdose") public static void medicineDeliveryObeysLevelsAndAutomaticCureKeepsDose(GameTestHelper h){
  try(var t=town(h,4)){VillageDogs.provide(new Dog());MedicineDelivery.tick(t.l,t.e);h.assertTrue(!MedicineDelivery.active(MedicineDelivery.inspect(t.l,t.e.settlement().id())),"No medicine delivery before V");}
  try(var t=town(h,6)){VillageDogs.provide(null);MedicinePacks.give(t.l,t.person.id(),UUID.randomUUID());Medicine.tick(t.l,t.e,100);h.assertTrue(!t.person.sick()&&MedicinePacks.carried(t.l,t.person.id()),"Automatic cure preserves carried medicine");}h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100,batch="medicinedeliveryreservesacrosspersistentreads") public static void medicineDeliveryReservesAcrossPersistentReads(GameTestHelper h){try(var t=town(h)){var dog=new Dog();VillageDogs.provide(dog);MedicineDelivery.tick(t.l,t.e);
  h.assertTrue(MedicineDelivery.reservedWolf(t.l,t.e,dog.id),"Durable errand missing: trip="+MedicineDelivery.inspect(t.l,t.e.settlement().id())+" patient="+t.l.getEntity(t.person.id())+" stock="+LogisticsRoutes.chest(t.l,t.e,t.clinic).countItem(VillageAstra.BANDAGE.get())+" free="+VillageDogs.available(t.l,t.e));
  h.assertTrue(PorterWork.reserved(t.l,t.e,t.clinic.id(),stack->stack.is(VillageAstra.BANDAGE.get()),false)==1,"Source dose reserved before pickup");dog.at=LogisticsRoutes.position(t.e,t.clinic);MedicineDelivery.tick(t.l,t.e);
  h.assertTrue(PorterWork.reserved(t.l,t.e,t.clinic.id(),stack->stack.is(VillageAstra.BANDAGE.get()),false)==0,"Debited dose no longer reserves stock");dog.at=t.npc.blockPosition();MedicineDelivery.tick(t.l,t.e);h.assertTrue(!MedicineDelivery.reservedWolf(t.l,t.e,dog.id),"Completed trip releases durable reservation");
 }h.succeed();}

 @GameTest(template="empty",timeoutTicks=100,batch="medicinedeliverydosetreatsinjuryonce") public static void medicineDeliveryDoseTreatsInjuryOnce(GameTestHelper h){try(var t=town(h)){t.person.cure();t.npc.setHealth(t.npc.getMaxHealth()-12);var receipt=UUID.randomUUID();MedicinePacks.give(t.l,t.person.id(),receipt);h.assertTrue(MedicinePacks.use(t.l,t.person)&&t.npc.getHealth()==t.npc.getMaxHealth()-4,"Carried medicine heals eight health");h.assertTrue(!MedicinePacks.use(t.l,t.person),"Remaining injury needs another paid dose");}h.succeed();}
 @GameTest(template="empty",timeoutTicks=100,batch="medicinedeliveryinjuredwalktoearlyhospital") public static void medicineDeliveryInjuredWalkToEarlyHospital(GameTestHelper h){try(var t=town(h,1)){t.person.cure();t.npc.setHealth(t.npc.getMaxHealth()-6);h.assertTrue(new PatientGoal(t.npc).canUse(),"An injured resident also seeks the early hospital");
  var doctor=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.e.settlement().admit(doctor,t.person.home());t.e.settlement().assign(doctor.id(),Profession.DOCTOR,t.clinic.id());var body=VillageAstra.RESIDENT.get().create(t.l);body.bind(t.e.settlement().id(),doctor);body.setNoAi(true);var at=LogisticsRoutes.position(t.e,t.clinic);body.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);t.l.addFreshEntity(body);
  try{h.assertTrue(!new DoctorGoal(body,true).canUse(),"Home visits remain locked before IV");t.npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5,0,0);h.assertTrue(new DoctorGoal(body,true).canUse(),"The early hospital treats injuries in its ward");}finally{body.discard();}
 }h.succeed();}

 @GameTest(template="empty",timeoutTicks=100,batch="medicinedeliverysicksolodoctor") public static void medicineDeliverySickSoloDoctorCanRecover(GameTestHelper h){try(var t=town(h,1)){t.e.settlement().assign(t.person.id(),Profession.DOCTOR,t.clinic.id());var at=LogisticsRoutes.position(t.e,t.clinic);t.npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5,0,0);
  for(long now=100;now<=Medicine.CURE_TICKS+300;now+=100)Medicine.tick(t.l,t.e,now);
  h.assertTrue(!t.person.sick()&&LogisticsRoutes.chest(t.l,t.e,t.clinic).countItem(VillageAstra.BANDAGE.get())==0,"The only medic recovers in the ward for one dose, without needing another medic");
 }h.succeed();}

}
