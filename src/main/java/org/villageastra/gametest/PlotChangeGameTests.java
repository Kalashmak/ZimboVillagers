package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** OWNER_REQUEST 9.17: when somebody else changes the plot of a queued building, the crew does not build over it — the change stops that
 *  cell, a fresh survey shows it as a conflict, and only natural drift of the ground (grass dying to dirt) is simply taken into the plan. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PlotChangeGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void aChangedPlotIsSurveyedAgainNeverBuiltOver(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<30;x++)for(int z=-2;z<26;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<12;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   var site=center.offset(12,0,10);
   var survey=BuildingOrders.survey(l,e,"home",0,site);
   h.assertTrue(survey.ok(),"The plot plans: "+survey.reason());
   var ops=survey.state().getList("ops",Tag.TAG_COMPOUND);var origin=BlockPos.of(survey.state().getLong("origin"));
   CompoundTag intoAir=null,onGrass=null;
   for(var raw:ops){var op=(CompoundTag)raw;var step=HallConstructionPlan.step(op);
    if(intoAir==null&&step.before().isAir()&&!step.after().isAir()&&step.pos().getY()>site.getY())intoAir=op;
    if(onGrass==null&&step.before().is(Blocks.GRASS_BLOCK))onGrass=op;}
   h.assertTrue(intoAir!=null&&onGrass!=null,"The plan has a block to put into the air and a cell of grass to work");
   // Somebody puts a block of their own where the house wants its wall.
   var wall=HallConstructionPlan.step(intoAir).pos();l.setBlock(wall,Blocks.GOLD_BLOCK.defaultBlockState(),3);
   h.assertTrue(!BuildingOrders.reconcile(l,intoAir,origin),"The crew does not build over what somebody else put there");
   h.assertTrue(l.getBlockState(wall).is(Blocks.GOLD_BLOCK),"And the block stays as it is");
   // Grass dying to dirt is the ground's own life, not somebody's change.
   var ground=HallConstructionPlan.step(onGrass).pos();l.setBlock(ground,Blocks.DIRT.defaultBlockState(),3);
   h.assertTrue(BuildingOrders.reconcile(l,onGrass,origin)&&HallConstructionPlan.step(onGrass).before().is(Blocks.DIRT),"Natural drift is simply taken into the plan");
   // A fresh survey of the same plot shows the stranger's block as a conflict instead of planning through it.
   var again=BuildingOrders.survey(l,e,"home",0,site);
   h.assertTrue(!again.ok()&&again.conflicts().contains(wall),"The new survey names the changed cell as a conflict: "+again.reason()+" "+again.conflicts().size());
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
