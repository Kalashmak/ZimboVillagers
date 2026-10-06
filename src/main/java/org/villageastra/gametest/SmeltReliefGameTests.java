package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** A healthy colleague physically finishes the same paid furnace job during illness. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SmeltReliefGameTests {
 @GameTest(template="empty",batch="smelt_relief",timeoutTicks=2400)
 public static void healthyWorkerFinishesIllColleaguesPaidFurnaceWithoutNewMaterials(GameTestHelper h){
  check(h,false);
 }
 @GameTest(template="empty",batch="smelt_relief",timeoutTicks=800)
 public static void reliefRefusesHealthyOwnerCarriedFuelAndAlreadyTakenOutput(GameTestHelper h){check(h,true);}
 private static void check(GameTestHelper h,boolean guards){
  var l=h.getLevel();var p=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(p.getX()+1179648+(guards?32768:0),120,p.getZ());var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-3)>>4;x<=(base.getX()+18)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+12)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);chunks.add(cp);}l.getChunk(x,z);
  }
  for(var cell:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(18,6,12)))l.setBlock(cell,cell.getY()<=120?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  l.setBlock(Workshops.station(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.setItem(0,new ItemStack(Items.SAND));stock.setItem(1,new ItemStack(Items.COAL));l.setBlock(base.offset(4,1,4),Blocks.FURNACE.defaultBlockState(),2);
  var bodies=new ArrayList<ResidentEntity>();
  for(int i=0;i<2;i++){
   var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),s.resident(r.id()));npc.moveTo(base.getX()+1.5+i*12,121,base.getZ()+2.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);l.addFreshEntity(npc);bodies.add(npc);
  }
  var old=bodies.get(0);var helper=bodies.get(1);var wants=List.of(new Workshops.Want(Ingredient.of(Items.GLASS),1,hall.id()));
  for(int i=0;i<14;i++){
   var current=Workshops.inspect(l,hall.id());if(current.getString("stage").equals("smelt_wait")&&current.getInt("fuels")==1)break;
   Workshops.advance(l,e,hall,1000+i*20,wants,old.getUUID());current=Workshops.inspect(l,hall.id());NaturalFurnace.claim(l,hall,current,old.getUUID());
  }
  var job=Workshops.inspect(l,hall.id());var id=job.getUUID("id");
  h.assertTrue(job.getString("stage").equals("smelt_wait")&&job.getUUID("worker").equals(old.getUUID())&&!job.contains("carried")&&stock.countItem(Items.SAND)==0&&stock.countItem(Items.COAL)==0,"Real input and fuel are in the furnace, not in either colleague's hands");
  h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Healthy owner keeps the job");
  s.resident(old.getUUID()).fallIll();
  h.assertTrue(NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"A healthy colleague may collect from a sick owner's actual furnace");
  if(guards){
   var carried=job.copy();carried.put("carried",new ItemStack(Items.COAL).save(new net.minecraft.nbt.CompoundTag()));h.assertTrue(!NaturalFurnace.availableTo(l,hall,carried,helper.getUUID()),"No transfer while paid fuel remains in hands");
   for(var stage:List.of("smelt_raw","smelt_put_raw","smelt_fuel","smelt_put_fuel","smelt_deliver","equip_place")){var other=job.copy();other.putString("stage",stage);h.assertTrue(!NaturalFurnace.availableTo(l,hall,other,helper.getUUID()),"No transfer at carried or equipment boundary: "+stage);}
   s.resident(helper.getUUID()).fallIll();h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"A sick replacement cannot take over");s.resident(helper.getUUID()).cure();
   h.runAfterDelay(260,()->{
    var furnace=(net.minecraft.world.level.block.entity.FurnaceBlockEntity)l.getBlockEntity(base.offset(4,1,4));h.assertTrue(furnace.getItem(2).is(Items.GLASS),"Actual vanilla cooking finished before receipt guard");
    var output=org.villageastra.persistence.WorldJournal.takeAmount(l,Settlement.childId(id,"smelt/output"),base.offset(4,1,4),2,furnace.getItem(2).copy(),1);h.assertTrue(output.is(Items.GLASS)&&furnace.getItem(2).isEmpty(),"Old owner really took the output before the checkpoint");
    h.assertTrue(!NaturalFurnace.claim(l,hall,Workshops.inspect(l,hall.id()),helper.getUUID()),"An existing output receipt cannot change owner");
    var held=new net.minecraft.nbt.ListTag();var custody=NaturalFurnace.custody(l,Workshops.inspect(l,hall.id()),held);h.assertTrue(custody.getString("stage").equals("idle")&&stock.countItem(Items.GLASS)==0&&held.size()==1&&ItemStack.of(held.getCompound(0)).is(Items.GLASS)&&ItemStack.of(held.getCompound(0)).getCount()==1,"The original owner retains exactly the paid output, without duplicate deposit");
    h.assertTrue(Workshops.inspect(l,hall.id()).getUUID("worker").equals(old.getUUID()),"Observation and refused claims leave original assignment unchanged");
    for(var npc:bodies)npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:chunks)l.setChunkForced(cp.x,cp.z,false);h.succeed();
   });return;
  }
  helper.goalSelector.addGoal(6,new WorkshopGoal(helper,true,l::getGameTime));
  h.succeedWhen(()->{
   var current=Workshops.inspect(l,hall.id());h.assertTrue(stock.countItem(Items.GLASS)==1,"Healthy body must collect the same paid furnace output: stage="+current.getString("stage")+" goals="+helper.runningGoals()+" ticks="+helper.tickCount);
   h.assertTrue(current.getUUID("id").equals(id)&&current.getString("stage").equals("idle")&&!current.contains("carried"),"Original paid job finishes exactly once");
   h.assertTrue(s.resident(old.getUUID()).sick()&&stock.countItem(Items.SAND)==0&&stock.countItem(Items.COAL)==0&&helper.getHealth()==helper.getMaxHealth(),"Illness persists; no new input, fuel or damage");
   h.assertTrue(org.villageastra.persistence.WorldJournal.recoverExisting(l,Settlement.childId(id,"smelt/output"))!=null&&org.villageastra.persistence.WorldJournal.recoverExisting(l,Settlement.childId(id,"smelt/deliver"))!=null,"Original output and deposit receipts retained");
   var oldCustody=JobCargo.snapshot(old,true);h.assertTrue(oldCustody.items().isEmpty()&&oldCustody.jobs().isEmpty(),"Former owner cannot replay the successor's paid output into its custody");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_SMELT_RELIEF VERIFIED bodyTicks={} glass=1 sameJob={} oldSick=true",helper.tickCount,id);
   for(var npc:bodies)npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:chunks)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
}
