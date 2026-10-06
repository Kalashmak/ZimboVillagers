package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** Actual supported school surveys on local terrain, beyond the original hall search. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MayorExpansionGameTests {
 private static void check(GameTestHelper h,boolean expansion){
  var l=h.getLevel();var column=h.absolutePos(new BlockPos(4,0,4));
  var pad=new BlockPos(column.getX(),Math.max(100,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ())+12),column.getZ());
  var center=pad.offset(expansion?-10000:-44,expansion?0:-10,0);
  var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0));
  if(expansion)s.addBuilding(new Settlement.Building(UUID.randomUUID(),"home",9956,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);
  var data=SettlementData.get(l.getServer());data.add(e);
  try{
   for(int x=-2;x<22;x++)for(int z=-2;z<22;z++){
    for(int y=-5;y<0;y++)l.setBlock(pad.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
    l.setBlock(pad.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
    for(int y=1;y<17;y++)l.setBlock(pad.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
   }
   MayorPlanner.clear();BlockPos chosen=null;
   for(int pass=0;pass<80&&chosen==null;pass++)chosen=MayorPlanner.site(l,e,"school");
   var explicit=BuildingOrders.survey(l,e,"school",0,pad);
   String details=" pad="+pad+" top="+l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pad.getX(),pad.getZ())+" free="+GrowthPlots.available(e,"school",pad,0)+" explicit="+explicit.reason()+" conflicts="+explicit.conflicts().stream().limit(8).toList();
   h.assertTrue(chosen!=null,(expansion?"School must use a safe site near an existing outlying home beyond the original 120-block hall circle":"School must follow supported terrain ten blocks above its hall")+details);
   h.assertTrue(BuildingOrders.survey(l,e,"school",0,chosen).ok(),"Chosen local terrain is genuinely orderable with paid foundation and safe scaffolds: "+chosen);
   if(expansion)h.assertTrue(chosen.distSqr(center)>120*120&&Math.abs(chosen.getX()-pad.getX()+44)<=150,"The chosen school expands from the outlying home, beyond the original hall circle");
   h.assertTrue(!HallUpgradeGoal.exists(l,s.id())&&l.getBlockState(pad).is(Blocks.GRASS_BLOCK),"Survey creates no project, supplies no resources and changes no ground");
  }finally{data.remove(s.id());MayorPlanner.clear();}
  h.succeed();
 }
 @GameTest(template="empty",batch="mayor_expansion",timeoutTicks=200)
 public static void schoolExpandsFromAnExistingOutlyingHome(GameTestHelper h){check(h,true);}
 @GameTest(template="empty",batch="mayor_expansion",timeoutTicks=200)
 public static void localSoilHeightExcludesAnOverheadTimberRoof(GameTestHelper h){
  var l=h.getLevel();var column=h.absolutePos(new BlockPos(4,0,4));
  var pad=new BlockPos(column.getX(),Math.max(100,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ())+12),column.getZ());
  l.setBlock(pad,Blocks.GRASS_BLOCK.defaultBlockState(),2);
  for(int y=1;y<=14;y++)l.setBlock(pad.above(y),Blocks.AIR.defaultBlockState(),2);
  var roof=pad.above(14);l.setBlock(roof,Blocks.BIRCH_LOG.defaultBlockState(),2);
  h.assertTrue(MayorPlanner.siteGround(l,column).equals(pad),"Local supported soil determines height; an overhead log roof cannot become building ground");
  h.assertTrue(l.getBlockState(pad).is(Blocks.GRASS_BLOCK)&&l.getBlockState(roof).is(Blocks.BIRCH_LOG),"Height search clears no terrain or timber");h.succeed();
 }
}
