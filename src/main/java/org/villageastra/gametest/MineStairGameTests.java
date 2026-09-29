package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineStairGameTests {
 private static ResearchV2Town.Town town(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");var data=org.villageastra.server.SettlementData.get(t.l.getServer());data.remove(t.s.id());
  var e=new org.villageastra.server.SettlementData.Entry(t.s,t.e.dimension(),t.e.center().above(16));data.add(e);ResearchV2Town.lay(t.l,e,t.shop,"mine");
  t.l.setBlock(e.center().offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);return new ResearchV2Town.Town(t.l,e,t.s,t.shop);
 }
 @GameTest(template="empty",batch="mine_stairs",timeoutTicks=200)
 public static void partialReceiptsRecoverOnceAndKeepRemainingDemand(GameTestHelper h){
  var t=town(h);
  try{
   var s=MineWork.read(t.l,t.shop);s.putString("stage","stair");s.putInt("step",2);s.putInt("stairStep",1);s.putString("stairItem","minecraft:cobblestone");
   var cells=MineDrive.stairs(1,MineWork.shape(s));for(var cell:cells)t.l.setBlock(MineWork.at(t.e,t.shop,cell),Blocks.AIR.defaultBlockState(),2);
   var p=BuildingPlacement.at(t.e,t.shop,1,1,4);var chest=LogisticsRoutes.chest(t.l,t.e,t.shop);chest.clearContent();
   for(int i=0;i<cells.size();i++){
    chest.setItem(0,new ItemStack(Items.COBBLESTONE));var take=MineStairWork.takeId(s);
    WorldJournal.takeAmount(t.l,take,p,0,chest.getItem(0).copy(),1);
    MineStairWork.reconcile(t.l,s);MineStairWork.reconcile(t.l,s);
    h.assertTrue(s.getList("cargo",Tag.TAG_COMPOUND).size()==1&&MineStairWork.missing(t.l,t.e,t.shop,s)==cells.size()-i-1,"Only this delivery is credited, once");
    MineWork.write(t.l,t.shop,s);s=MineWork.read(t.l,t.shop);
    var at=MineWork.at(t.e,t.shop,cells.get(i));h.assertTrue(WorldJournal.place(t.l,Settlement.childId(s.getUUID("operation"),"stair/"+i),at,Blocks.AIR.defaultBlockState(),Blocks.COBBLESTONE_STAIRS.defaultBlockState()),"Paid stair placed");
    MineStairWork.reconcile(t.l,s);MineStairWork.reconcile(t.l,s);
    h.assertTrue(s.getInt("stairPlaced")==i+1&&MineStairWork.missing(t.l,t.e,t.shop,s)==cells.size()-i-1,"Crash after placement pays once and keeps remaining row");
    s.putInt("stairTakeRound",s.getInt("stairTakeRound")+1);s.remove("stairTaken");
   }
   h.assertTrue(chest.isEmpty()&&MineStairWork.missing(t.l,t.e,t.shop,s)==0,"Exactly three real stones finish a three-wide row");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_stairs",timeoutTicks=200)
 public static void oldIncompleteRowQueuesRepairWithoutLosingCargo(GameTestHelper h){
  var t=town(h);var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{
   var s=MineWork.read(t.l,t.shop);s.putInt("step",2);s.putInt("cell",9);s.putInt("stairAudit",1);s.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));
   var cargo=new ListTag();cargo.add(new ItemStack(Items.DIRT).save(new CompoundTag()));s.put("cargo",cargo);
   var cells=MineDrive.stairs(1,MineWork.shape(s));for(var cell:cells)t.l.setBlock(MineWork.at(t.e,t.shop,cell),Blocks.AIR.defaultBlockState(),2);
   t.l.setBlock(MineWork.at(t.e,t.shop,cells.get(0)),Blocks.COBBLESTONE_STAIRS.defaultBlockState(),2);MineWork.write(t.l,t.shop,s);
   var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);t.l.addFreshEntity(npc);
   var chest=LogisticsRoutes.chest(t.l,t.e,t.shop);chest.clearContent();var p=BuildingPlacement.at(t.e,t.shop,1,1,4);npc.moveTo(p.getX()+1.5,p.getY(),p.getZ()+.5);
   var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Miner resumes");for(int i=0;i<80;i++)goal.tick();
   var after=MineWork.read(t.l,t.shop);h.assertTrue(after.getString("stage").equals("stair")&&after.getInt("stairStep")==1&&after.getInt("step")==2&&after.getInt("cell")==9,"Empty stock cannot discard the old row or advance excavation");
   h.assertTrue(after.getList("cargo",Tag.TAG_COMPOUND).equals(cargo),"Other paid cargo is preserved");
   h.assertTrue(WorkerSupplies.wants(t.l,t.e).stream().anyMatch(w->w.destination().equals(t.shop.id())&&w.matches(new ItemStack(Items.COBBLESTONE))&&w.count()==2),"Missing two stones are requested at the mine");
   chest.setItem(0,new ItemStack(Items.COBBLESTONE,2));h.assertTrue(LogisticsRoutes.constructionReserve(t.l,t.e,t.shop,chest.getItem(0))==2,"Delivered stair stones cannot be exported");
   chest.clearContent();
   for(int part=1;part<3;part++){
    npc.moveTo(p.getX()+1.5,p.getY(),p.getZ()+.5);chest.setItem(0,new ItemStack(Items.COBBLESTONE));
    goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Real goal reloads a partial order");goal.tick();
    var stand=MineDrive.next(new MineDrive.Drive(1,0,MineDrive.EAST,0),Integer.MAX_VALUE/2,MineWork.shape(after)).stand();var at=MineWork.at(t.e,t.shop,stand);
    npc.moveTo(at.getX()+.5,at.getY()+1,at.getZ()+.5);npc.setOnGround(true);
    for(int n=0;n<5;n++)goal.tick();
    h.assertTrue(t.l.getBlockState(MineWork.at(t.e,t.shop,cells.get(part))).is(Blocks.COBBLESTONE_STAIRS),"Real goal installs each separate one-stone delivery");
   }
   h.assertTrue(!MineWork.read(t.l,t.shop).contains("stairStep"),"Order ends only after all stairs exist");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
}
