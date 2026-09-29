package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-151 (owner's Education ladder): a class holds the school's seats — 1, 2, 2, 4, 8, 8 — the eldest children in the room first; from III the
 *  eldest of them sit in the military class and grow up trained for arms; the library of VI teaches twice as fast as V. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SchoolClassGameTests {
 private record Town(net.minecraft.server.level.ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos station,List<Resident> kids,List<ResidentEntity> bodies){}
 private static Town town(GameTestHelper h,int children){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,2,4));var s=new Settlement(UUID.randomUUID());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var home=new Settlement.Home(UUID.randomUUID(),1,20,true);s.addHome(home);var kids=new ArrayList<Resident>();var bodies=new ArrayList<ResidentEntity>();
  for(int i=0;i<children;i++){var r=new Resident(UUID.randomUUID(),Resident.Life.CHILD,false,null,null,-1);r.born(100+i);s.admit(r,home.id());kids.add(r);
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),r);npc.setNoAi(true);npc.moveTo(center.getX()+.5+i%3,center.getY(),center.getZ()+.5+i/3,0,0);l.addFreshEntity(npc);bodies.add(npc);}
  return new Town(l,s,e,center,kids,bodies);
 }
 private static void done(Town t){for(var b:t.bodies)b.discard();SettlementData.get(t.l.getServer()).remove(t.s.id());}
 @GameTest(template="empty",timeoutTicks=100) public static void aSchoolTeachesItsSeatsEldestFirst(GameTestHelper h){
  var t=town(h,9);
  try{
   var seats=new int[7];for(int level=1;level<=6;level++)seats[level]=Population.classroom(t.l,t.e,t.station,level).size();
   h.assertTrue(seats[1]==1&&seats[2]==2&&seats[3]==2&&seats[4]==4&&seats[5]==8&&seats[6]==8,"Seats I..VI 1/2/2/4/8/8: "+Arrays.toString(seats));
   h.assertTrue(Population.classroom(t.l,t.e,t.station,1).contains(t.kids.get(0).id()),"The eldest child takes the one seat of level I");
   h.assertTrue(Population.lessonCredit(6)==2*Population.lessonCredit(5),"The library of VI teaches twice as fast as V");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theMilitaryClassGrowsSoldiers(GameTestHelper h){
  var t=town(h,3);
  try{
   Population.classroom(t.l,t.e,t.station,2);h.assertTrue(t.kids.stream().noneMatch(Resident::cadet),"No military class below III");
   Population.classroom(t.l,t.e,t.station,3);h.assertTrue(t.kids.get(0).cadet()&&!t.kids.get(1).cadet(),"At III the eldest pupil is the cadet");
   long now=SettlementData.get(t.l.getServer()).clock().ticks()+Population.GROW+1000;
   for(var r:t.kids.subList(0,2))r.attendSchool(Population.SCHOOL_REQUIRED);
   Population.grow(t.l,t.e,now);
   var cadet=t.kids.get(0);var pupil=t.kids.get(1);
   h.assertTrue(cadet.life()==Resident.Life.ADULT&&cadet.educated()&&cadet.military(),"The cadet grows up educated and trained for arms");
   h.assertTrue(pupil.life()==Resident.Life.ADULT&&pupil.educated()&&!pupil.military(),"The other pupil grows up educated, a civilian");
   var loaded=SettlementData.load(SettlementData.get(t.l.getServer()).save(new net.minecraft.nbt.CompoundTag())).entry(t.s.id());
   h.assertTrue(loaded!=null&&loaded.settlement().resident(cadet.id()).military(),"It stays so after a save");
  }finally{done(t);}
  h.succeed();
 }
}
