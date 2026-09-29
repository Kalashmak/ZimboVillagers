package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** OWNER_REQUEST 9.19: an empty server advances nothing — no clock, no queued construction, no machine of a high level, no research,
 *  no road project. Whatever the village has going on waits for somebody to be in the world. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class EmptyServerGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void anEmptyServerAdvancesNothingAtAll(GameTestHelper h){
  var l=h.getLevel();var server=l.getServer();var center=h.absolutePos(new BlockPos(6,3,6));
  var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<26;x++)for(int z=-2;z<22;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<6;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);var data=SettlementData.get(server);data.add(e);
  try{
   // A farm that really works at level five, with the research its machinery takes.
   var farm=new Settlement.Building(Settlement.childId(s.id(),"building/farm"),"farm",8,0,0);s.addBuilding(farm);
   for(int i=2;i<=5;i++)s.raiseBuildingLevel(farm.id(),i);
   var kept=s.buildings().stream().filter(x->x.id().equals(farm.id())).findFirst().orElseThrow();
   var chestPos=LogisticsRoutes.position(e,kept);
   l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
   for(int i=2;i<=5;i++)for(var placed:BuildingLevels.equipment("farm",i))
    l.setBlock(BuildingPlacement.at(e,kept,placed.local().getX(),placed.local().getY(),placed.local().getZ()),placed.state(),3);
   var record=BookResearch.inspect(l,e);var done=record.getList("legacyDone",net.minecraft.nbt.Tag.TAG_STRING);
   // AD-136: no mechanics branch; an older village's farm at V runs its machine by its own ladder.
   record.put("legacyDone",done);BookResearch.store(l,e,record);
   var farmChest=LogisticsRoutes.chest(l,e,kept);farmChest.setItem(0,new ItemStack(Items.WHEAT_SEEDS,16));
   for(var cell:FarmWorkArea.cells(e)){l.setBlock(cell.below(),Blocks.FARMLAND.defaultBlockState(),3);l.setBlock(cell,Blocks.AIR.defaultBlockState(),3);}
   // A construction project of the village, queued and waiting for the crew.
   var survey=BuildingTiers.survey(l,e,kept);
   if(!survey.state().isEmpty())HallUpgradeGoal.enqueue(l,e,survey.state());
   var knowledge=BookResearch.completed(e,BookResearch.inspect(l,e));
   long clock=data.clock().ticks();int index=HallUpgradeGoal.pending(l,s.id())?HallUpgradeGoal.inspect(l,s.id()).getInt("index"):-1;
   int sown=0;for(var cell:FarmWorkArea.cells(e))if(!l.getBlockState(cell).isAir())sown++;
   h.assertTrue(server.getPlayerCount()==0,"This server really has nobody in it");
   var event=new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,server);
   for(int i=0;i<200;i++)ServerEvents.tick(event);
   h.assertTrue(data.clock().ticks()==clock,"The village clock does not run: "+data.clock().ticks()+" was "+clock);
   int after=0;for(var cell:FarmWorkArea.cells(e))if(!l.getBlockState(cell).isAir())after++;
   h.assertTrue(after==sown,"No machine sows or reaps a field: "+after+" was "+sown);
   h.assertTrue(farmChest.countItem(Items.WHEAT_SEEDS)==16,"No stock is spent or made: "+farmChest.countItem(Items.WHEAT_SEEDS));
   int now=HallUpgradeGoal.pending(l,s.id())?HallUpgradeGoal.inspect(l,s.id()).getInt("index"):-1;
   h.assertTrue(now==index,"No queued project advances by itself: "+now+" was "+index);
   h.assertTrue(BookResearch.completed(e,BookResearch.inspect(l,e)).equals(knowledge),"No research is completed without anybody in the world");
  }finally{HallUpgradeGoal.drop(l,s.id());data.remove(s.id());}
  h.succeed();
 }
}
