package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;

/** Prepared school, actual walking child and changed terrain; not a natural-growth proof. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SchoolCommuteGameTests {
 @GameTest(template="empty",batch="school_commute",timeoutTicks=9000)
 public static void childKeepsItsRouteAndDetoursToThePresentTeacher(GameTestHelper h){
  run(h,false);
 }
 @GameTest(template="empty",batch="school_home_duty",timeoutTicks=3600)
 public static void childKeepsSchoolDutyOutsideItsHomeNeighborhood(GameTestHelper h){run(h,true);}
 private static void run(GameTestHelper h,boolean neighborhood){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+524288,90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+172)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+12)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-2;x<=172;x++)for(int z=-2;z<=12;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var school=new Settlement.Building(UUID.randomUUID(),"school",160,0,0);s.addBuilding(school);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,4,true));if(neighborhood)s.addBuilding(new Settlement.Building(home,"home",0,0,0));SettlementData.get(l.getServer()).add(e);var station=LogisticsRoutes.position(e,school);
  var teacher=VillageAstra.RESIDENT.get().create(l);var adult=new Resident(teacher.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(adult,home);s.assign(adult.id(),Profession.TEACHER,school.id());teacher.bind(s.id(),adult);teacher.setNoAi(true);teacher.moveTo(station.getX()+.5,station.getY(),station.getZ()+.5);l.addFreshEntity(teacher);
  var child=VillageAstra.RESIDENT.get().create(l);var pupil=new Resident(child.getUUID(),Resident.Life.CHILD,false,null,null,-1);s.admit(pupil,home);child.bind(s.id(),pupil);child.moveTo(base.getX()+.5,91,base.getZ()+2.5);child.setOnGround(true);child.goalSelector.removeAllGoals(g->true);child.targetSelector.removeAllGoals(g->true);var commute=new SchoolGoal(child,true);child.goalSelector.addGoal(6,commute);if(neighborhood){HomeNeighborhood.restrict(child);child.goalSelector.addGoal(5,new HomeNeighborhood(child));}boolean[] wall={false};l.setDayTime(1000);
  int[] nativePlans={0};
  try{
   var nav=child.getNavigation();var field=net.minecraft.world.entity.ai.navigation.PathNavigation.class.getDeclaredField("pathFinder");field.setAccessible(true);var original=(net.minecraft.world.level.pathfinder.PathFinder)field.get(nav);
   field.set(nav,new net.minecraft.world.level.pathfinder.PathFinder(nav.getNodeEvaluator(),1){
    @Override public net.minecraft.world.level.pathfinder.Path findPath(net.minecraft.world.level.PathNavigationRegion region,net.minecraft.world.entity.Mob body,Set<BlockPos> goals,float range,int accuracy,float budget){nativePlans[0]++;return original.findPath(region,body,goals,range,accuracy,budget);}
   });
  }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(child.blockPosition()),"Child chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(child),"Actual child added"));
  h.onEachTick(()->{
   if(!wall[0]&&child.getX()>base.getX()+40){wall[0]=true;for(int z=-2;z<=8;z++)for(int y=1;y<=3;y++)l.setBlock(base.offset(80,y,z),Blocks.STONE.defaultBlockState(),3);}
   if(child.distanceToSqr(station.getX()+.5,station.getY(),station.getZ()+.5)>4)return;
   h.assertTrue(wall[0]&&child.getHealth()==child.getMaxHealth(),"Child physically detours without injury");
   h.assertTrue(Population.classroom(l,e,station,1).contains(pupil.id()),"Teacher and child are actually present for attendance");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_SCHOOL_COMMUTE requests={} nativePlans={} bodyTicks={} wall={} child={} station={}",commute.routeRequests(),nativePlans[0],child.tickCount,wall[0],child.position(),station);
   h.assertTrue(nativePlans[0]<commute.routeRequests()/2,"Vanilla navigation actually reuses most requests instead of searching on each: plans="+nativePlans[0]+" requests="+commute.routeRequests());
   if(neighborhood){
    h.assertTrue(SchoolGoal.childStation(l,e,pupil)!=null,"Present teacher establishes real school duty");
    long day=l.getDayTime();l.setDayTime(12000);h.assertTrue(SchoolGoal.childStation(l,e,pupil)==null,"Night restores the home policy");l.setDayTime(day);
    teacher.discard();h.assertTrue(SchoolGoal.childStation(l,e,pupil)==null,"Absent teacher restores the home policy");
   }
   child.discard();teacher.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(neighborhood?3500:8800,()->h.assertTrue(false,"Child failed to reach the school: pos="+child.position()+" requests="+commute.routeRequests()));
 }
}
