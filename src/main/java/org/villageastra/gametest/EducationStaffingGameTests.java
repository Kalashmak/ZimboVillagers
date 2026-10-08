package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class EducationStaffingGameTests {
 @GameTest(template="empty",batch="education_staffing",timeoutTicks=100)
 public static void childGetsTeacherWithoutTakingProjectLeader(GameTestHelper h){check(h,true,false,true);}
 @GameTest(template="empty",batch="education_staffing",timeoutTicks=100)
 public static void noChildrenKeepAdditionalBuilder(GameTestHelper h){check(h,false,false,true);}
 @GameTest(template="empty",batch="education_staffing",timeoutTicks=100)
 public static void playerMayorKeepsChosenStaff(GameTestHelper h){check(h,true,true,true);}
 @GameTest(template="empty",batch="education_staffing",timeoutTicks=100)
 public static void onlyBuilderKeepsConstructionJob(GameTestHelper h){check(h,true,false,false);}
 @GameTest(template="empty",batch="education_staffing",timeoutTicks=100)
 public static void schoolCanRecruitBeforeAnyProjectExists(GameTestHelper h){check(h,true,false,true,false);}
 private static void check(GameTestHelper h,boolean child,boolean player,boolean extra){
  check(h,child,player,extra,true);
 }
 private static void check(GameTestHelper h,boolean child,boolean player,boolean extra,boolean queued){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  try{
   var hall=Workshops.hall(e);var lead=s.residents().stream().filter(r->r.profession()==Profession.BUILDER).findFirst().orElseThrow();
   var school=new Settlement.Building(UUID.randomUUID(),"school",48,0,48);s.addBuilding(school);
   var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));
   Resident helper=null;if(extra){helper=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(helper,home);s.assign(helper.id(),Profession.BUILDER,hall.id());}
   if(child){var pupil=new Resident(UUID.randomUUID(),Resident.Life.CHILD,false,null,null,-1);s.admit(pupil,home);}
   for(var r:s.residents()){var n=VillageAstra.RESIDENT.get().create(l);n.bind(s.id(),r);n.setNoAi(true);n.moveTo(e.center().getX()+.5,e.center().getY()+1,e.center().getZ()+.5);h.assertTrue(l.addFreshEntity(n),"Actual staffing body exists");}
   if(player)s.governance().appointPlayer(UUID.randomUUID());
   var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());project.putUUID("project",project.getUUID("id"));project.putUUID("worker",lead.id());project.putBoolean("funded",true);project.putInt("index",7);if(queued)HallUpgradeGoal.enqueue(l,e,project);
   Population.assign(l,e,2);
   boolean needed=child&&!player&&extra;
   h.assertTrue(s.residents().stream().filter(r->r.profession()==Profession.TEACHER).count()==(needed?1:0),"A new child needs a teacher even when all adults already have jobs");
   if(needed)h.assertTrue(s.residents().stream().anyMatch(r->r.profession()==Profession.TEACHER&&school.equals(s.workplace(r.id()))),"Teacher is posted at the school");
   if(queued){if(needed)h.assertTrue(helper.profession()==Profession.TEACHER,"Project lead is never the teacher candidate");h.assertTrue(lead.profession()==Profession.BUILDER&&HallUpgradeGoal.inspect(l,s.id()).equals(project),"Lead and exact paid construction state are preserved");}
   else h.assertTrue(!HallUpgradeGoal.exists(l,s.id())&&s.residents().stream().filter(r->r.profession()==Profession.BUILDER).count()==1,"No project invented and one builder retained");
   Population.assign(l,e,2);h.assertTrue(s.residents().stream().filter(r->r.profession()==Profession.TEACHER).count()==(needed?1:0),"Repeated staffing is stable");
  }finally{HallUpgradeGoal.drop(l,s.id());for(var r:s.residents()){var n=l.getEntity(r.id());if(n!=null)n.discard();}SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
}
