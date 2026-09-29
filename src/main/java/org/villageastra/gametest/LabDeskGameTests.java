package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.ScienceBalance;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-154 (roadmap 0.10 «Наука: столы по местам»): every scientist place of the laboratory at every level I–VI has a desk of its own in the
 *  design, and scientists who hold different places work at different desks. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LabDeskGameTests {
 @GameTest(template="empty",batch="labdesk",timeoutTicks=100) public static void scientistReachesOwnCellBeforeWriting(GameTestHelper h){
  var t=town(h,"laboratory");var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{var lab=raise(t,3);var r=scientist(t,lab,"precise");ScienceWorks.advance(t.l,t.e,0);npc.bind(t.s.id(),r);var desk=LabDesks.of(t.l,t.e,lab,r.id()).stand();
   npc.moveTo(desk.getX()+.5,desk.getY(),desk.getZ()-.5,0,0);t.l.addFreshEntity(npc);npc.setOnGround(true);npc.tickCount=20;var goal=new ResearchGoal(npc);goal.tick();
   h.assertTrue(!npc.getNavigation().isDone()&&!npc.workStatus().equals("science_writing"),"One cell short of an accessible desk: approach it instead of writing from the aisle");
   npc.moveTo(desk.getX()+.5,desk.getY(),desk.getZ()+.5,0,0);goal.tick();
   h.assertTrue(npc.getNavigation().isDone()&&npc.workStatus().equals("science_writing"),"At the assigned cell the scientist stops and writes");
  }finally{npc.discard();done(t);}h.succeed();
 }
 private static final BlockState AIR=Blocks.AIR.defaultBlockState();
 private static boolean full(BlockState s){return !s.isAir()&&s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);}
 /** 1. Desks >= places at every level; place i's desk stands from the level that opens it, beside a free cell of its own that a resident
  *  walks to from the door; no stand is a cell of the frozen equipment, and the desks cut no frozen cell off the door. */
 @GameTest(template="empty",batch="labdesk") public static void everyLabPlaceHasItsOwnDesk(GameTestHelper h){
  var frozen=new HashSet<BlockPos>();for(var p:LevelArchitecture.equipment("laboratory"))frozen.add(p.local());
  var stands=new HashSet<BlockPos>();var blocks=new HashSet<BlockPos>();
  for(var d:LabDesks.DESKS){h.assertTrue(stands.add(d.stand())&&blocks.add(d.block()),"Desks and stands are distinct: "+d);
   h.assertTrue(!frozen.contains(d.stand()),"A stand is never an equipment cell: "+d.stand().toShortString());
   h.assertTrue(Math.abs(d.stand().getX()-d.block().getX())<=1&&Math.abs(d.stand().getZ()-d.block().getZ())<=1&&d.stand().getY()==d.block().getY(),"A stand is beside its desk: "+d);}
  for(int level=1;level<=6;level++){int seats=ScienceBalance.seats(level),desks=LabDesks.designDesks(level);
   h.assertTrue(desks>=seats,"Level "+level+": "+desks+" desks for "+seats+" places");
   var layout=BuildingPlacement.layout(BuildingTiers.layoutId("laboratory",level),BlockPos.ZERO,0);
   for(int i=0;i<seats;i++){var d=LabDesks.DESKS.get(i);var s=layout.getOrDefault(d.block(),AIR);
    h.assertTrue(d.level()<=level&&LabDesks.isDesk(s),"Level "+level+" place "+i+" has its desk at "+d.block().toShortString()+": "+s);
    h.assertTrue(layout.getOrDefault(d.stand(),AIR).isAir()&&layout.getOrDefault(d.stand().above(),AIR).isAir()&&full(layout.getOrDefault(d.stand().below(),AIR)),"Level "+level+" place "+i+" stands free at "+d.stand().toShortString());}}
  // Reach (the frozen test's flat walk from the door) in the level-I design with every desk laid, against the design without level VI's lectern.
  var raw=BuildingBlueprints.raw("laboratory",BlockPos.ZERO);var design=BuildingBlueprints.design("laboratory");
  var before=EquipmentTableGameTests.reach(raw,design.width(),design.depth());
  var six=BuildingPlacement.layout(BuildingTiers.layoutId("laboratory",6),BlockPos.ZERO,0);var all=new HashMap<>(raw);
  for(var d:LabDesks.DESKS)all.put(d.block(),six.get(d.block()));
  var after=EquipmentTableGameTests.reach(all,design.width(),design.depth());
  for(var d:LabDesks.DESKS)h.assertTrue(after.contains(d.stand()),"The stand "+d.stand().toShortString()+" is walked to from the door");
  for(var p:frozen)h.assertTrue(EquipmentTableGameTests.near(before,p)==EquipmentTableGameTests.near(after,p),"The desks cut no equipment cell off the door: "+p.toShortString());
  h.succeed();
 }
 /** 2. Three scientists in a level-III laboratory take three places and work at three different desks standing in the world; a fourth,
  *  without a place, waits at the old desk cell. */
 @GameTest(template="empty",batch="labdesk",timeoutTicks=300) public static void scientistsWorkAtDifferentDesks(GameTestHelper h){
  var t=town(h,"laboratory");
  try{var lab=raise(t,3);var names=new String[]{"a","b","c","d"};var ids=new ArrayList<java.util.UUID>();
   for(var n:names)ids.add(scientist(t,lab,n).id());
   ScienceWorks.advance(t.l,t.e,0);
   var desks=new HashSet<BlockPos>();var stands=new HashSet<BlockPos>();int seated=0;
   for(var id:ids){int seat=ScienceWorks.seat(t.l,t.e,id);if(seat<0)continue;seated++;var d=LabDesks.of(t.l,t.e,lab,id);
    h.assertTrue(LabDesks.isDesk(t.l.getBlockState(d.block())),"Place "+seat+"'s desk stands in the world: "+t.l.getBlockState(d.block()));
    h.assertTrue(t.l.getBlockState(d.stand()).isAir(),"Place "+seat+"'s stand is free: "+t.l.getBlockState(d.stand()));
    h.assertTrue(desks.add(d.block())&&stands.add(d.stand()),"Two scientists never share a desk: "+d);}
   h.assertTrue(seated==3&&desks.size()==3,"Three places, three desks: seated="+seated+" desks="+desks.size());
   var waiting=ids.stream().filter(id->ScienceWorks.seat(t.l,t.e,id)<0).findFirst().orElseThrow();
   h.assertTrue(LabDesks.of(t.l,t.e,lab,waiting).stand().equals(BuildingPlacement.at(t.e,lab,2,1,4)),"The scientist without a place waits at the old desk cell");
   // A broken desk sends its scientist to the old desk cell rather than to a table that is not there.
   var first=ids.stream().filter(id->ScienceWorks.seat(t.l,t.e,id)==1).findFirst().orElseThrow();var broken=LabDesks.of(t.l,t.e,lab,first).block();
   t.l.setBlock(broken,AIR,2);
   h.assertTrue(LabDesks.of(t.l,t.e,lab,first).stand().equals(BuildingPlacement.at(t.e,lab,2,1,4)),"A broken desk: back to the old desk cell");
  }finally{done(t);}
  h.succeed();
 }
}
