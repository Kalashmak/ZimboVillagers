package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import java.util.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MayorSurfaceGroundGameTests {
 @GameTest(template="empty",batch="mayor_surface_ground",timeoutTicks=100)
 public static void leafAboveSurfaceNeverTurnsTheCaveBelowIntoTheBuildingGround(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);var floor=new BlockPos(origin.getX()+196608,120,origin.getZ());
  for(int y=0;y<=24;y++)l.setBlock(floor.above(y),y==0?Blocks.STONE.defaultBlockState():y>=12&&y<20?Blocks.STONE.defaultBlockState():y==20?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  l.setBlock(floor.above(22),Blocks.BIRCH_LEAVES.defaultBlockState(),2);
  var chosen=MayorPlanner.siteGround(l,floor);h.assertTrue(chosen==null,"A canopy-rejected surface must not make the underground cave floor eligible: chosen="+chosen+" top="+l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,floor.getX(),floor.getZ()));
  l.setBlock(floor.above(22),Blocks.AIR.defaultBlockState(),2);h.assertTrue(floor.above(20).equals(MayorPlanner.siteGround(l,floor)),"The exposed actual surface remains valid");h.succeed();
 }
 @GameTest(template="empty",batch="mayor_site_routes",timeoutTicks=100)
 public static void unsafeDownwardSiteIsDiscardedBeforeTheMayorWalksOffTheLedge(GameTestHelper h){route(h,true);}
 @GameTest(template="empty",batch="mayor_site_routes",timeoutTicks=100)
 public static void flatReachableSiteKeepsItsProposalAndRequiresPhysicalArrival(GameTestHelper h){route(h,false);}
 @SuppressWarnings("unchecked") private static void route(GameTestHelper h,boolean drop){
  var l=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(origin.getX()+262144+(drop?0:65536),120,origin.getZ());var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  try{
   var field=MayorPlanner.class.getDeclaredField("PROPOSALS");field.setAccessible(true);var proposals=(Map<UUID,MayorPlanner.Proposal>)field.get(null);
   var cursorField=MayorPlanner.class.getDeclaredField("CURSOR");cursorField.setAccessible(true);var cursors=(Map<UUID,Integer>)cursorField.get(null);cursors.put(s.id(),100);
   for(int x=0;x<=65;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=16;y++)l.setBlock(base.offset(x,y,z),y==(drop&&x>=35?0:12)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
   var site=base.offset(60,drop?0:12,0);var proposal=new MayorPlanner.Proposal("farm",site,"shortage");proposals.put(s.id(),proposal);
   var r=s.residents().stream().filter(v->v.profession()==Profession.MAYOR).findFirst().orElseThrow();var body=VillageAstra.RESIDENT.get().create(l);body.bind(s.id(),r);body.setNoAi(true);body.moveTo(base.getX()+2.5,base.getY()+13,base.getZ()+.5);body.setOnGround(true);h.assertTrue(l.addFreshEntity(body),"Mayor body registered");body.tickCount=20;
   try{
    var path=body.routeTo(site.above(),0);h.assertTrue(HarvestAccess.reversible(path)!=drop,"Fixture has the expected reversible native route");var goal=new MayorSiteGoal(body,true);h.assertTrue(goal.canUse(),"The mayor has a real proposal");goal.tick();
    if(drop){h.assertTrue(MayorPlanner.proposal(s.id())==null&&cursors.get(s.id())==101,"Unreachable site is discarded and the next candidate is considered");h.assertTrue(body.getNavigation().getPath()==null,"No unsafe partial path starts");}
    else h.assertTrue(proposal.equals(MayorPlanner.proposal(s.id()))&&body.getNavigation().getPath()!=null&&body.getNavigation().getPath().canReach(),"Flat site retains a complete physical walking route: status="+body.workStatus()+" ground="+MayorPlanner.siteGround(l,site));
    h.assertTrue(!HallUpgradeGoal.exists(l,s.id())&&body.getHealth()==body.getMaxHealth(),"No remote approval, stock grant or fall damage");
   }finally{body.discard();proposals.remove(s.id());cursors.remove(s.id());}
  }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}finally{SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }

}
