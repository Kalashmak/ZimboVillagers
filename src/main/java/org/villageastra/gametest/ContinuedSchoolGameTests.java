package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ContinuedSchoolGameTests {
 private static final Map<ResearchV2Town.Town,List<net.minecraft.world.level.ChunkPos>> chunks=new IdentityHashMap<>();
 private static final Map<net.minecraft.world.level.ChunkPos,Integer> users=new HashMap<>();
 private static final Set<net.minecraft.world.level.ChunkPos> forced=new HashSet<>();
 private static void ready(GameTestHelper h,java.util.function.Consumer<ResearchV2Town.Town> exercise){
  var t=ResearchV2Town.town(h,"school");var room=LogisticsRoutes.position(t.e,t.shop);var owned=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(room.getX()-30)>>4;x<=(room.getX()+24)>>4;x++)for(int z=(room.getZ()-6)>>4;z<=(room.getZ()+16)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);owned.add(cp);users.merge(cp,1,Integer::sum);
   if(!t.l.getForcedChunks().contains(cp.toLong())){t.l.setChunkForced(x,z,true);forced.add(cp);}t.l.getChunk(x,z);
  }
  chunks.put(t,owned);
  h.startSequence().thenWaitUntil(()->h.assertTrue(owned.stream().allMatch(cp->t.l.isPositionEntityTicking(new net.minecraft.core.BlockPos(cp.getMinBlockX(),room.getY(),cp.getMinBlockZ()))),"Classroom and walking corridor must tick before fixture bodies are admitted")).thenExecute(()->exercise.accept(t));
 }
 private static void done(ResearchV2Town.Town t){
  ResearchV2Town.done(t);for(var cp:chunks.remove(t))if(users.merge(cp,-1,Integer::sum)==0){users.remove(cp);if(forced.remove(cp))t.l.setChunkForced(cp.x,cp.z,false);}
 }
 private static ResidentEntity body(ResearchV2Town.Town t,Resident r){
  var n=VillageAstra.RESIDENT.get().create(t.l);n.bind(t.s.id(),r);n.setNoAi(true);
  var p=LogisticsRoutes.position(t.e,t.shop);n.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
  if(!t.l.addFreshEntity(n)||t.l.getEntity(r.id())!=n)throw new GameTestAssertException("Canonical school body must be admitted before the exercise");return n;
 }
 private static Resident adult(ResearchV2Town.Town t,long credit){
  var r=new Resident(UUID.randomUUID(),Resident.Life.CHILD,false,null,null,-1);r.born(100);r.attendSchool(credit);r.growUp();
  t.s.admit(r,Settlement.childId(t.s.id(),"home"));return r;
 }
 @GameTest(template="empty",batch="continued_school",timeoutTicks=400)
 public static void grownPupilFinishesOnlyByAttendingTeacher(GameTestHelper h){
  ready(h,t->{var bodies=new ArrayList<ResidentEntity>();
  try{
   var pupil=adult(t,65320);var student=body(t,pupil);bodies.add(student);
   var initial=adult(t,0);bodies.add(body(t,initial));
   var teacher=adult(t,0);t.s.assign(teacher.id(),Profession.TEACHER,t.shop.id());var tutor=body(t,teacher);bodies.add(tutor);
   tutor.moveTo(t.e.center().getX()-30,t.e.center().getY(),t.e.center().getZ(),0,0);
   Population.grow(t.l,t.e,Population.GROW+1000);
   h.assertTrue(pupil.schoolTicks()==65320&&!pupil.educated(),"No credit without physically present teacher");
   var p=LogisticsRoutes.position(t.e,t.shop);tutor.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
   for(int i=0;i<333;i++)Population.grow(t.l,t.e,Population.GROW+1000+i*20);
   h.assertTrue(pupil.schoolTicks()==71980&&!pupil.educated(),"333 real lesson passes leave 20 ticks to earn");
   Population.grow(t.l,t.e,Population.GROW+1000+333*20);
   h.assertTrue(pupil.schoolTicks()==72000&&pupil.educated(),"Grown pupil physically completes original 72000-tick course");
   h.assertTrue(initial.schoolTicks()==0&&!initial.educated(),"Initial adults do not receive a free course");
  }finally{for(var n:bodies)n.discard();done(t);}
  h.succeed();});
 }
 @GameTest(template="empty",batch="continued_school",timeoutTicks=400)
 public static void unfinishedAdultUsesOneSeatAndYieldsToHealth(GameTestHelper h){
  ready(h,t->{var bodies=new ArrayList<ResidentEntity>();
  try{
   var pupil=adult(t,65320);var n=body(t,pupil);bodies.add(n);
   var teacher=adult(t,0);t.s.assign(teacher.id(),Profession.TEACHER,t.shop.id());bodies.add(body(t,teacher));
   var kid=new Resident(UUID.randomUUID(),Resident.Life.CHILD,false,null,null,-1);kid.born(200);
   t.s.admit(kid,Settlement.childId(t.s.id(),"home"));bodies.add(body(t,kid));
   t.l.setDayTime(1000);var goal=new SchoolGoal(n,true);
   h.assertTrue(goal.canUse()&&goal.canContinueToUse(),"Adult holds classroom duty even after arriving");
   var room=LogisticsRoutes.position(t.e,t.shop);
   h.assertTrue(Population.classroom(t.l,t.e,room,1).equals(Set.of(pupil.id())),"Oldest unfinished course uses only one seat");
   pupil.fallIll();h.assertTrue(!goal.canUse(),"Ill pupil yields to treatment");
   Population.grow(t.l,t.e,1000);h.assertTrue(pupil.schoolTicks()==65320,"Ill pupil cannot earn credit");pupil.cure();
   teacher.fallIll();Population.grow(t.l,t.e,1020);h.assertTrue(pupil.schoolTicks()==65320,"Ill teacher cannot teach");teacher.cure();
   long daytime=t.l.getDayTime();t.l.setDayTime(12000);h.assertTrue(!goal.canUse(),"Night releases classroom duty");t.l.setDayTime(daytime);
   for(int i=0;i<334;i++)Population.grow(t.l,t.e,1040+i*20);
   h.assertTrue(pupil.educated()&&!goal.canUse(),"Graduation releases adult duty");
   h.assertTrue(Population.classroom(t.l,t.e,room,1).equals(Set.of(kid.id())),"Graduation frees the seat for the younger child");
  }finally{for(var n:bodies)n.discard();done(t);}
  h.succeed();});
 }
 @GameTest(template="empty",batch="continued_school_walk",timeoutTicks=1200)
 public static void employedAdultWalksBackFinishesAndKeepsItsTrade(GameTestHelper h){
  ready(h,t->{var room=LogisticsRoutes.position(t.e,t.shop);
  for(int x=-28;x<0;x++)for(int z=-2;z<=2;z++)for(int y=-1;y<=3;y++)
   t.l.setBlock(room.offset(x,y,z),y==-1?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
  var pupil=adult(t,71940);var student=body(t,pupil);student.setNoAi(false);student.moveTo(room.getX()-24.5,room.getY(),room.getZ()+.5,0,0);student.setOnGround(true);
  var workplace=new Settlement.Building(UUID.randomUUID(),"forester",30,0,0);t.s.addBuilding(workplace);t.s.assign(pupil.id(),Profession.FORESTER,workplace.id());
  var teacher=adult(t,0);t.s.assign(teacher.id(),Profession.TEACHER,t.shop.id());var tutor=body(t,teacher);
  student.goalSelector.removeAllGoals(g->true);student.targetSelector.removeAllGoals(g->true);var commute=new SchoolGoal(student,true);student.goalSelector.addGoal(5,commute);
  int[] worked={0};student.goalSelector.addGoal(6,new net.minecraft.world.entity.ai.goal.Goal(){
   {setFlags(java.util.EnumSet.of(Flag.MOVE));}
   @Override public boolean canUse(){return true;}
   @Override public void tick(){worked[0]++;student.getNavigation().moveTo(room.getX()-24.5,room.getY(),room.getZ()+.5,.7);}
  });
  t.l.setDayTime(1000);int[] passes={0};
  h.onEachTick(()->{
   if(h.getTick()%20==0){Population.grow(t.l,t.e,1000+h.getTick());passes[0]++;}
   if(!pupil.educated())return;
   h.assertTrue(student.distanceToSqr(room.getX()+.5,room.getY(),room.getZ()+.5)<=Population.SCHOOL_RADIUS*Population.SCHOOL_RADIUS,"Adult reached the actual teacher");
   h.assertTrue(pupil.schoolTicks()==72000&&worked[0]==0&&passes[0]>=3,"Earns the remaining three lessons without routine work pulling it away: credit="+pupil.schoolTicks()+" worked="+worked[0]+" passes="+passes[0]);
   h.assertTrue(pupil.profession()==Profession.FORESTER&&t.s.workplace(pupil.id()).id().equals(workplace.id()),"Course preserves assigned profession");
   h.assertTrue(!commute.canUse()&&student.getHealth()==student.getMaxHealth(),"Graduation releases movement duty without injury");
   student.discard();tutor.discard();done(t);h.succeed();
  });
  });
 }
}
