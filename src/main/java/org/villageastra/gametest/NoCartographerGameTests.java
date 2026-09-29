package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** OWNER_REQUEST 9.12: without a cartographer the mayor still builds where he stands, but the map — the surface and what lies under it —
 *  stays closed: nothing is surveyed, no column and no building card reaches the map. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class NoCartographerGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void withoutACartographerTheMapStaysClosedButBuildingGoesOn(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/cartographer"),"cartographer",-16,0,0));
  for(int x=-2;x<30;x++)for(int z=-2;z<26;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<16;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  // Ore lies under the village, where a surveyed map would show it.
  l.setBlock(center.offset(4,-1,4),Blocks.IRON_ORE.defaultBlockState(),2);
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   h.assertTrue(s.residents().stream().noneMatch(r->r.alive()&&r.profession()==Profession.CARTOGRAPHER),"The village really has no cartographer");
   for(int i=0;i<5;i++)Atlas.tick(l,e);
   var view=Atlas.view(l,e,0,0,64);
   h.assertTrue(Atlas.surveyed(Atlas.inspect(l,s.id())).isEmpty(),"Nothing is surveyed without a cartographer");
   h.assertTrue(view.getList("chunks",Tag.TAG_COMPOUND).isEmpty()&&view.getInt("total")==0,"No column, surface or underground, reaches the map: "+view.getInt("total"));
   h.assertTrue(view.getList("buildings",Tag.TAG_COMPOUND).isEmpty(),"Not even the village's own buildings are drawn on a map nobody made");
   // Building by the mayor's own eyes goes on: a house is surveyed and ordered at the spot he marks.
   var site=center.offset(12,0,10);
   var reason=BuildingOrders.approve(l,e,"home",0,site);
   h.assertTrue(reason.isEmpty(),"A house is still ordered where the mayor stands: "+reason+" "+BuildingOrders.survey(l,e,"home",0,site).conflicts().stream().limit(4).map(BlockPos::toShortString).toList());
   h.assertTrue(HallUpgradeGoal.pending(l,s.id()),"And the crew has it queued");
  }finally{HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
