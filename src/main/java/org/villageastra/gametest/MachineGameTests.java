package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-076: what the upper levels really do — a level-IV bench works without hands, a level-V farm, quarry and mine run their own
 *  cycle out of real blocks and real stock, a level-VI building hauls for the village, and a worker at the bench always comes first. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MachineGameTests {
 private record Yard(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s){}
 private static Yard yard(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<40;x++)for(int z=-2;z<34;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<6;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  // AD-136: no mechanics branch any more; a building's machinery is the top of its own ladder (balance/automation.json).
  return new Yard(l,e,s);
 }
 private static Settlement.Building building(Yard t,String type,int dx,int dz,int level){
  var b=new Settlement.Building(Settlement.childId(t.s.id(),"building/"+type),type,dx,0,dz);t.s.addBuilding(b);
  for(int i=2;i<=level;i++)t.s.raiseBuildingLevel(b.id(),i);
  var kept=t.s.buildings().stream().filter(x->x.id().equals(b.id())).findFirst().orElseThrow();
  var chest=LogisticsRoutes.position(t.e,kept);
  t.l.setBlock(chest.below(),Blocks.COBBLESTONE.defaultBlockState(),3);t.l.setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  // The equipment of every level it is kept at really stands in it, or it would work at a lower level.
  for(int i=2;i<=level;i++)for(var placed:BuildingLevels.equipment(kept.type(),i))
   t.l.setBlock(BuildingPlacement.at(t.e,kept,placed.local().getX(),placed.local().getY(),placed.local().getZ()),placed.state(),3);
  return kept;
 }
 /** Crops need light and the test world lies deep underground: the field gets its own lamps, and the light engine a moment to catch up. */
 private static void field(Yard t){
  for(var cell:FarmWorkArea.cells(t.e)){t.l.setBlock(cell.below(),Blocks.FARMLAND.defaultBlockState(),3);t.l.setBlock(cell,Blocks.AIR.defaultBlockState(),3);
   if((cell.getX()+cell.getZ())%3==0)t.l.setBlock(cell.above(2),Blocks.GLOWSTONE.defaultBlockState(),3);}
 }
 private static int tick(Yard t,long now){return Machines.tick(t.l,t.e,now,Workshops.wants(t.l,t.e));}
 private static void done(Yard t){SettlementData.get(t.l.getServer()).remove(t.s.id());}
 @GameTest(template="empty",timeoutTicks=200) public static void theFieldOfALevelFiveFarmSowsAndReapsItself(GameTestHelper h){
  var t=yard(h);
  var farm=building(t,"farm",8,0,5);
  h.assertTrue(BuildingLevels.level(t.l,t.e,farm)==5,"The farm is kept and works at level five: "+BuildingLevels.level(t.l,t.e,farm));
  var chest=LogisticsRoutes.chest(t.l,t.e,farm);chest.setItem(0,new ItemStack(Items.WHEAT_SEEDS,16));
  field(t);
  var first=FarmWorkArea.cells(t.e).get(0);
  h.startSequence().thenIdle(30).thenExecute(()->{
   int worked=tick(t,40);
   h.assertTrue(worked>0,"The machine takes its turn: worked="+worked+" why=["+Machines.lastReason+"]");
   int sown=0;for(var cell:FarmWorkArea.cells(t.e))if(t.l.getBlockState(cell).getBlock() instanceof CropBlock)sown++;
   h.assertTrue(sown==1&&chest.countItem(Items.WHEAT_SEEDS)==15,"One cell is sown from the farm's own seed: sown="+sown+" seeds="+chest.countItem(Items.WHEAT_SEEDS));
   t.l.setBlock(first,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);
   int reaped=tick(t,80);
   h.assertTrue(reaped>0,"The machine takes another turn: why=["+Machines.lastReason+"]");
   h.assertTrue(chest.countItem(Items.WHEAT)>0,"The harvest lands in the farm's own chest");
   // AD-104: every sowing has its own id, so a reaped cell is sown again and the field never stalls after its first round.
   int seeds=chest.countItem(Items.WHEAT_SEEDS);
   int again=tick(t,120);
   h.assertTrue(again>0&&t.l.getBlockState(first).getBlock() instanceof CropBlock&&chest.countItem(Items.WHEAT_SEEDS)==seeds-1,
    "The reaped cell is sown again from one more seed: "+t.l.getBlockState(first)+" seeds="+chest.countItem(Items.WHEAT_SEEDS)+" of "+seeds+" why=["+Machines.lastReason+"]");
   done(t);
  }).thenSucceed();
 }
 /** AD-104: a sowing cut short after its seed left the chest (a stop between the withdrawal and the planting) is finished under its own
  *  id on the next turn: the recorded cell gets its crop and no second seed is taken. */
 @GameTest(template="empty",timeoutTicks=200) public static void aSowingCutShortIsFinishedNotRepeated(GameTestHelper h){
  var t=yard(h);
  var farm=building(t,"farm",8,0,5);
  var chest=LogisticsRoutes.chest(t.l,t.e,farm);chest.setItem(0,new ItemStack(Items.WHEAT_SEEDS,16));
  field(t);
  var cell=FarmWorkArea.cells(t.e).get(5);var id=java.util.UUID.randomUUID();
  var record=new net.minecraft.nbt.CompoundTag();record.putUUID("sow",id);record.putLong("cell",cell.asLong());
  org.villageastra.persistence.NbtRecord.write(t.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-machines/"+farm.id()+".bin"),record);
  var taken=org.villageastra.persistence.WorldJournal.takeAmount(t.l,id,LogisticsRoutes.position(t.e,farm),0,chest.getItem(0).copy(),1);
  h.assertTrue(taken.getCount()==1&&chest.countItem(Items.WHEAT_SEEDS)==15,"The seed has left the chest before the stop");
  h.startSequence().thenIdle(30).thenExecute(()->{
   int worked=tick(t,40);
   int sown=0;for(var c:FarmWorkArea.cells(t.e))if(t.l.getBlockState(c).getBlock() instanceof CropBlock)sown++;
   h.assertTrue(worked>0&&t.l.getBlockState(cell).getBlock() instanceof CropBlock&&sown==1&&chest.countItem(Items.WHEAT_SEEDS)==15,
    "The recorded cell is sown with the seed already taken, and only it: sown="+sown+" seeds="+chest.countItem(Items.WHEAT_SEEDS)+" why=["+Machines.lastReason+"]");
   done(t);
  }).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theMineOfLevelFiveDrivesItsOwnAdit(GameTestHelper h){
  var t=yard(h);
  try{
   var mine=building(t,"mine",20,0,5);
   // AD-122: a drive opened now is MineWork.HEIGHT (5) high.
   for(int step=0;step<2;step++)for(int cell=0;cell<MineWork.HEIGHT;cell++)for(int x=2;x<=4;x++)
    t.l.setBlock(BuildingPlacement.at(t.e,mine,x,MineWork.HEIGHT-1-step-cell-MineWork.DRIVE_DESCENT,7+step),Blocks.STONE.defaultBlockState(),3);
   var chest=LogisticsRoutes.chest(t.l,t.e,mine);
   h.assertTrue(tick(t,40)>0,"The adit machine takes its turn: why=["+Machines.lastReason+"]");
   h.assertTrue(chest.countItem(Items.COBBLESTONE)==1,"Exactly the one block it dug is in the mine's chest: "+chest.countItem(Items.COBBLESTONE));
   var area=t.s.mineAreas().get(mine.id());
   h.assertTrue(area!=null&&area.descent()==MineWork.DRIVE_DESCENT,"What the machine dug is claimed at the depth it dug: "+area);
   h.assertTrue(tick(t,80)>0&&chest.countItem(Items.COBBLESTONE)==2,"Every turn takes exactly one more block: "+chest.countItem(Items.COBBLESTONE));
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aWorkerAtTheBenchComesBeforeTheMachineAndLowerLevelsDoNothing(GameTestHelper h){
  var t=yard(h);
  var farm=building(t,"farm",8,0,5);
  var low=building(t,"restaurant",24,0,3);
  h.assertTrue(!Machines.mechanized(t.l,t.e,low),"Below the fourth level nothing works without hands");
  h.assertTrue(Machines.mechanized(t.l,t.e,farm),"From the fifth level the farm runs itself");
  var chest=LogisticsRoutes.chest(t.l,t.e,farm);chest.setItem(0,new ItemStack(Items.WHEAT_SEEDS,16));
  field(t);
  t.s.addHome(new Settlement.Home(Settlement.childId(t.s.id(),"home"),1,4,true));
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,t.s.homes().iterator().next().id());t.s.assign(r.id(),Profession.FARMER,farm.id());
  var npc=VillageAstra.RESIDENT.get().create(t.l);npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);
  var at=BuildingPlacement.origin(t.e,farm);npc.moveTo(at.getX()+1.5,at.getY()+1,at.getZ()+1.5,0,0);t.l.addFreshEntity(npc);
  h.startSequence().thenIdle(30).thenExecute(()->{
   h.assertTrue(tick(t,40)==0,"With its own worker there the machine stands still");
   npc.discard();
  }).thenIdle(5).thenExecute(()->{
   h.assertTrue(tick(t,80)>0,"Without hands the machine works again: why=["+Machines.lastReason+"]");
   done(t);
  }).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aLevelSixBuildingHaulsWhatTheVillageAsksFor(GameTestHelper h){
  var t=yard(h);
  try{
   // A farm of the sixth level carries its own harvest to the village stock, the delivery the village plans anyway.
   var farm=building(t,"farm",8,0,6);
   var farmChest=LogisticsRoutes.chest(t.l,t.e,farm);farmChest.setItem(0,new ItemStack(Items.WHEAT,32));
   var hall=t.s.buildings().stream().filter(b->b.type().equals("town_hall")).findFirst().orElseThrow();
   var hallChest=LogisticsRoutes.chest(t.l,t.e,hall);
   int before=hallChest.countItem(Items.WHEAT);
   boolean hauled=false;for(int i=1;i<=8&&!hauled;i++){tick(t,30L*i);hauled=hallChest.countItem(Items.WHEAT)>before;}
   h.assertTrue(hauled,"The farm of the sixth level carries its harvest to the stock: "+hallChest.countItem(Items.WHEAT)+" why=["+Machines.lastReason+"]");
   h.assertTrue(hallChest.countItem(Items.WHEAT)+farmChest.countItem(Items.WHEAT)==32,"Nothing is created or lost on the way: "+hallChest.countItem(Items.WHEAT)+"+"+farmChest.countItem(Items.WHEAT));
  }finally{done(t);}
  h.succeed();
 }
}
