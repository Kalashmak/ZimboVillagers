package org.villageastra.gametest;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-136 (spec 2.2, 6.2, owner answer 3): a seated scientist writes one scientific work per 36 000 ticks of the village clock; a laboratory
 *  seats as many as its working level; a full chest stops the place; a replay never writes twice; the place keeps its progress when its
 *  scientist changes. The clock is passed explicitly (the server passes SettlementData.clock() every 40 ticks). */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ScienceWorksGameTests {
 private static final long W=ScienceBalance.WORK_TICKS;
 @GameTest(template="empty",timeoutTicks=200) public static void oneWorkPerThirtyMinutesOfTheVillageClock(GameTestHelper h){
  var t=town(h,"laboratory");
  try{var lab=t.kept();scientist(t,lab,"a");
   h.assertTrue(W==36000,"The owner's pace: 36 000 ticks (30 minutes at 20 TPS)");
   h.assertTrue(ScienceWorks.advance(t.l,t.e,1000)==0&&ScienceWorks.seated(t.l,t.e)==1,"The scientist takes the one place of a level-I laboratory");
   h.assertTrue(ScienceWorks.advance(t.l,t.e,1000+W-1)==0&&works(t,lab)==0,"No work a tick early");
   h.assertTrue(ScienceWorks.advance(t.l,t.e,1000+W)==1&&works(t,lab)==1,"One work at 36 000 clock ticks");
   h.assertTrue(ScienceWorks.advance(t.l,t.e,1000+W)==0&&works(t,lab)==1,"The same moment again writes nothing (no duplicate)");
   h.assertTrue(ScienceWorks.advance(t.l,t.e,1000+3*W)==2&&works(t,lab)==3,"A laboratory unvisited for an hour catches up: two more works");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void noScientistNoWork(GameTestHelper h){
  var t=town(h,"laboratory");
  try{var lab=t.kept();
   h.assertTrue(ScienceWorks.advance(t.l,t.e,0)==0&&ScienceWorks.advance(t.l,t.e,5*W)==0&&works(t,lab)==0,"An empty laboratory writes nothing");
   var r=scientist(t,lab,"late");
   h.assertTrue(ScienceWorks.advance(t.l,t.e,5*W)==0,"Time before the scientist came counts for nothing");
   h.assertTrue(ScienceWorks.advance(t.l,t.e,6*W)==1,"From his arrival: one work after 36 000 ticks");
   t.s.resident(r.id()).die();
   h.assertTrue(ScienceWorks.advance(t.l,t.e,6*W+10)==0&&ScienceWorks.advance(t.l,t.e,9*W)==0&&ScienceWorks.seated(t.l,t.e)==0,"A dead scientist writes nothing");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void aLevelThreeLaboratorySeatsThreeAndWritesThree(GameTestHelper h){
  var t=town(h,"laboratory");
  try{var lab=raise(t,3);
   h.assertTrue(BuildingLevels.level(t.l,t.e,lab)==3,"The laboratory works at level III: "+BuildingLevels.level(t.l,t.e,lab));
   for(var n:new String[]{"a","b","c","d"})scientist(t,lab,n);
   ScienceWorks.advance(t.l,t.e,0);
   h.assertTrue(ScienceWorks.places(t.l,t.e)==3&&ScienceWorks.seated(t.l,t.e)==3,"Three places at level III, the fourth scientist waits: "+ScienceWorks.seated(t.l,t.e));
   h.assertTrue(ScienceWorks.advance(t.l,t.e,W)==3&&works(t,lab)==3,"Three seated scientists write three works together");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aFullChestStopsThePlace(GameTestHelper h){
  var t=town(h,"laboratory");
  try{var lab=t.kept();scientist(t,lab,"a");var c=org.villageastra.world.LogisticsRoutes.chest(t.l,t.e,lab);
   for(int i=0;i<c.getContainerSize();i++)c.setItem(i,new ItemStack(Items.COBBLESTONE,64));
   ScienceWorks.advance(t.l,t.e,0);
   h.assertTrue(ScienceWorks.advance(t.l,t.e,3*W)==0&&works(t,lab)==0,"A full chest takes no work");
   h.assertTrue(ScienceWorks.status(t.l,t.e,t.s.residents().stream().filter(r->r.profession()==Profession.SCIENTIST).findFirst().orElseThrow().id()).equals("science_chest_full"),"The scientist's card says the chest is full");
   c.setItem(0,ItemStack.EMPTY);
   h.assertTrue(ScienceWorks.advance(t.l,t.e,3*W+20)==1&&works(t,lab)==1,"With room again the one work that waited goes in, the rest of the time was lost");
   h.assertTrue(ScienceWorks.advance(t.l,t.e,3*W+20+W-1)==0,"And the next one takes its full time again");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void progressBelongsToThePlaceNotTheScientist(GameTestHelper h){
  var t=town(h,"laboratory");
  try{var lab=t.kept();var first=scientist(t,lab,"first");
   ScienceWorks.advance(t.l,t.e,0);ScienceWorks.advance(t.l,t.e,W/2);
   t.s.resident(first.id()).die();ScienceWorks.advance(t.l,t.e,W/2+100);
   scientist(t,lab,"second");ScienceWorks.advance(t.l,t.e,W);
   h.assertTrue(works(t,lab)==0,"Half a work done, then a gap: nothing yet");
   h.assertTrue(ScienceWorks.advance(t.l,t.e,W+W/2-100)==1&&works(t,lab)==1,"The new scientist finishes the half his predecessor wrote");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aSecondLaboratoryIsRefused(GameTestHelper h){
  var t=town(h,"laboratory");
  try{h.assertTrue(ResearchGate.designRefusal(t.l,t.e,"laboratory").equals("unique"),"One laboratory per village (owner answer 1, CF15)");}finally{done(t);}
  h.succeed();
 }
}
