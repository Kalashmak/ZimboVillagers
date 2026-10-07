package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RemoteParcelRecoveryGameTests {
 @GameTest(template="empty",batch="porter_remote_receipt",timeoutTicks=400)
 public static void paidParcelSurvivesAnUnloadedSourceChunk(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,5,2));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var farm=new Settlement.Building(UUID.randomUUID(),"farm",1048576,0,4096);s.addBuilding(hall);s.addBuilding(farm);
  var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home.id());s.assign(person.id(),Profession.PORTER,hall.id());npc.bind(s.id(),person);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var source=LogisticsRoutes.position(e,farm);var dest=LogisticsRoutes.position(e,hall);
  for(var p:List.of(source,dest)){if(l.getBlockEntity(p) instanceof net.minecraft.world.Container c)c.clearContent();l.setBlock(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);l.setBlock(p,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);}
  var from=LogisticsRoutes.chest(l,e,farm);from.setItem(0,new ItemStack(Items.WHEAT,64));var job=UUID.randomUUID();var paid=WorldJournal.takeAmount(l,Settlement.childId(job,"take"),source,0,from.getItem(0).copy(),7);h.assertTrue(paid.getCount()==7&&from.countItem(Items.WHEAT)==57,"Real parcel is debited once before source unloads");
  var state=new CompoundTag();state.putInt("schema",1);state.putUUID("id",job);state.putUUID("worker",npc.getUUID());state.putUUID("settlement",s.id());state.putUUID("assignment",hall.id());state.putUUID("source",farm.id());state.putUUID("destination",hall.id());state.put("item",paid.save(new CompoundTag()));state.putString("stage","deliver");NbtRecord.write(PorterWork.path(l,npc.getUUID()),state);
  npc.moveTo(dest.getX()+1.5,dest.getY(),dest.getZ()+.5);
  waitForUnload(h,source,0,()->{try{
   PorterWork.step(npc);h.assertTrue(!PorterWork.active(PorterWork.inspect(l,npc.getUUID()))&&LogisticsRoutes.chest(l,e,hall).countItem(Items.WHEAT)==7,"Paid parcel resumes and deposits after real source unload");
   h.assertTrue(LogisticsRoutes.chest(l,e,farm).countItem(Items.WHEAT)==57,"Loading receipt source does not repeat the debit");
   state.putString("stage","fetch");NbtRecord.write(PorterWork.path(l,npc.getUUID()),state);PorterWork.step(npc);
   h.assertTrue(LogisticsRoutes.chest(l,e,hall).countItem(Items.WHEAT)==7&&LogisticsRoutes.chest(l,e,farm).countItem(Items.WHEAT)==57&&!PorterWork.active(PorterWork.inspect(l,npc.getUUID())),"Old job checkpoint cannot repeat either inventory transition");h.succeed();
  }finally{SettlementData.get(l.getServer()).remove(s.id());}});
 }
 private static void waitForUnload(GameTestHelper h,BlockPos source,int ticks,Runnable action){if(!h.getLevel().hasChunkAt(source)){action.run();return;}h.assertTrue(ticks<250,"Source must really unload before recovery is exercised");h.runAfterDelay(1,()->waitForUnload(h,source,ticks+1,action));}
}
