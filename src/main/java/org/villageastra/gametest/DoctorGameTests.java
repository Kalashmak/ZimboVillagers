package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-034: bandages are made from real inputs; each treatment spends one bandage once and heals without revival. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DoctorGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void clinicMakesBandagesAndDoctorHealsWithThem(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  var clinic=new Settlement.Building(Settlement.childId(s.id(),"building/clinic"),"clinic",0,0,0);s.addBuilding(clinic);
  for(int x=-6;x<12;x++)for(int z=-6;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var chest=LogisticsRoutes.chest(l,e,clinic);chest.setItem(0,new ItemStack(Items.PAPER,2));chest.setItem(1,new ItemStack(Items.SUGAR,1));
  h.assertTrue(Workshops.wants(l,e).stream().anyMatch(w->w.matches(new ItemStack(VillageAstra.BANDAGE.get()))),"Clinic wants a bandage reserve");
  long now=1000;String last="";for(int i=0;i<40&&!last.equals("workshop_complete");i++){last=Workshops.advance(l,e,clinic,now,Workshops.wants(l,e));now+=20;}
  h.assertTrue(chest.countItem(VillageAstra.BANDAGE.get())==2&&chest.countItem(Items.PAPER)==0&&chest.countItem(Items.SUGAR)==0,"Two paper and one sugar became two bandages");
  var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);var patientResident=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(patientResident,home.id());
  var patient=VillageAstra.RESIDENT.get().create(l);patient.bind(s.id(),patientResident);patient.setNoAi(true);patient.moveTo(center.getX()+3.5,center.getY()+1,center.getZ()+3.5,0,0);l.addFreshEntity(patient);patient.setHealth(6F);
  h.assertTrue(DoctorGoal.treat(l,e,clinic,patient,5000)&&DoctorGoal.treat(l,e,clinic,patient,5000),"Replaying the same treatment is idempotent");
  h.assertTrue(Math.abs(patient.getHealth()-(6F+DoctorGoal.HEAL))<.01F&&chest.countItem(VillageAstra.BANDAGE.get())==1,"One bandage spent, patient healed once: "+patient.getHealth());
  h.assertTrue(DoctorGoal.treat(l,e,clinic,patient,6000)&&chest.countItem(VillageAstra.BANDAGE.get())==0&&!DoctorGoal.treat(l,e,clinic,patient,7000),"No bandage, no treatment");
  h.succeed();
 }
}
