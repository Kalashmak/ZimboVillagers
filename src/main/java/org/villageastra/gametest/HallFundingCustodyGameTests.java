package org.villageastra.gametest;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.world.*;

/** Real debits and return receipts around an unstarted construction handover. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HallFundingCustodyGameTests {
 private static ResidentEntity worker(ResearchV2Town.Town t){
  var n=VillageAstra.RESIDENT.get().create(t.l);
  var r=new Resident(n.getUUID(),Resident.Life.ADULT,true,null,null,-1);
  t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.BUILDER,t.hall().id());n.bind(t.s.id(),r);
  var p=HallSite.stock(t.e);n.moveTo(p.getX()+1.5,p.getY(),p.getZ()+.5);return n;
 }
 private static CompoundTag project(ResearchV2Town.Town t,ResidentEntity n){
  var id=UUID.randomUUID();var p=new CompoundTag();p.putInt("schema",1);p.putUUID("id",id);p.putUUID("project",id);p.putUUID("worker",n.getUUID());
  var ops=new ListTag();for(int i=0;i<4;i++){var op=new CompoundTag();op.putString("item","minecraft:cobblestone");ops.add(op);}p.put("ops",ops);
  var cost=new CompoundTag();cost.putInt("minecraft:cobblestone",4);p.put("cost",cost);
  var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.setItem(0,new ItemStack(Items.COBBLESTONE,4));
  var cargo=new ListTag();for(int i=0;i<2;i++){var paid=WorldJournal.takeAmount(t.l,Settlement.childId(id,"fund/"+i),HallSite.stock(t.e),0,chest.getItem(0).copy(),2);if(paid.getCount()!=2)throw new IllegalStateException("Fixture debit failed");cargo.add(paid.save(new CompoundTag()));}
  p.put("cargo",cargo);p.putInt("withdrawals",2);HallUpgradeGoal.store(t.l,t.s.id(),p);return p;
 }
 @GameTest(template="empty",timeoutTicks=200) public static void legacyFundingHandoverRefusesOtherOwnership(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);var n=worker(t);
  try{var p=project(t,n);var snap=JobCargo.snapshot(n,true);var c=new CompoundTag();c.putUUID("id",UUID.randomUUID());c.putUUID("owner",n.getUUID());c.putUUID("settlement",t.s.id());c.put("items",snap.items());c.put("jobs",snap.jobs());
   var rejoin=Class.forName("org.villageastra.world.HallFundingCustody").getDeclaredMethod("rejoin",net.minecraft.server.level.ServerLevel.class,CompoundTag.class);rejoin.setAccessible(true);
   for(String change:List.of("dead","funded","complete","started","done","differentProject","differentCargo","otherJob","unprovenRefund")){
    var changed=p.copy();var held=c.copy();
    switch(change){
     case "dead"->held.putBoolean("dead",true);case "funded"->changed.putBoolean("funded",true);case "complete"->changed.putBoolean("complete",true);
     case "started"->changed.putInt("index",1);case "done"->changed.getList("ops",Tag.TAG_COMPOUND).getCompound(2).putBoolean("done",true);
     case "differentProject"->changed.putUUID("project",UUID.randomUUID());case "differentCargo"->changed.put("cargo",new ListTag());
     case "otherJob"->held.getList("jobs",Tag.TAG_COMPOUND).add(held.getList("jobs",Tag.TAG_COMPOUND).getCompound(0).copy());case "unprovenRefund"->held.putInt("index",1);
    }
    HallUpgradeGoal.store(t.l,t.s.id(),changed);
    h.assertTrue(!(Boolean)rejoin.invoke(null,t.l,held),"Refuse ambiguous or ineligible ownership: "+change);
    h.assertTrue(HallUpgradeGoal.inspect(t.l,t.s.id()).equals(changed),"Rejected "+change+" leaves all project data intact");
   }
   HallUpgradeGoal.store(t.l,t.s.id(),p);var funded=p.copy();funded.putBoolean("funded",true);HallUpgradeGoal.store(t.l,t.s.id(),funded);t.s.unassign(n.getUUID());
   h.assertTrue(JobCargo.snapshot(n,false).items().size()==2,"A funded live construction still uses physical custody");
  }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void liveRoleChangeKeepsUnstartedFundingWithTheProject(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);var n=worker(t);
  try{var p=project(t,n);t.s.unassign(n.getUUID());
   var snap=JobCargo.snapshot(n,false);
   h.assertTrue(snap.jobs().isEmpty()&&snap.items().isEmpty(),"A live reassignment leaves the unstarted project's paid escrow with its replacement builder");
   h.assertTrue(HallUpgradeGoal.inspect(t.l,t.s.id()).equals(p),"Funding receipt identity and paid cargo stay intact");
   h.assertTrue(JobCargo.snapshot(n,true).items().size()==2,"Death still recovers the actual held cargo");
  }finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void partialLegacyRefundRejoinsOnlyUnreturnedCargo(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);var n=worker(t);
  try{var p=project(t,n);var snap=JobCargo.snapshot(n,true);var id=UUID.randomUUID();var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());
   for(int i=0;i<chest.getContainerSize();i++)chest.setItem(i,new ItemStack(Items.STONE,64));chest.setItem(0,new ItemStack(Items.COBBLESTONE,62));
   h.assertTrue(WorldJournal.deposit(t.l,Settlement.childId(id,"return/0"),HallSite.stock(t.e),ItemStack.of(snap.items().getCompound(0))),"The first legacy refund physically committed");
   h.assertTrue(!WorldJournal.deposit(t.l,Settlement.childId(id,"return/1"),HallSite.stock(t.e),ItemStack.of(snap.items().getCompound(1))),"The second refund is blocked by a full hall");
   var c=new CompoundTag();c.putInt("schema",1);c.putUUID("id",id);c.putUUID("owner",n.getUUID());c.putUUID("settlement",t.s.id());c.putString("dimension",t.l.dimension().location().toString());c.putString("sourceDimension",t.l.dimension().location().toString());c.put("items",snap.items());c.put("jobs",snap.jobs());c.putInt("index",1);
   var file=t.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-custody/"+n.getUUID()+".bin");NbtRecord.write(file,c);
   CargoCustody.returnStep(n,true);var now=HallUpgradeGoal.inspect(t.l,t.s.id());
   h.assertTrue(!CargoCustody.pending(n.getServer(),n.getUUID()),"A legacy unstarted refund no longer waits for empty hall slots");
   h.assertTrue(now.getList("cargo",Tag.TAG_COMPOUND).size()==1&&ItemStack.of(now.getList("cargo",Tag.TAG_COMPOUND).getCompound(0)).getCount()==2,"Only the two unreturned cobblestones remain in project escrow");
   h.assertTrue(chest.getItem(0).getCount()==64&&now.getInt("withdrawals")==2&&now.getUUID("id").equals(p.getUUID("id")),"The two returned items stay physical and old debit ids are preserved");
   var again=WorldJournal.takeAmount(t.l,Settlement.childId(p.getUUID("id"),"fund/2"),HallSite.stock(t.e),0,chest.getItem(0).copy(),2);
   h.assertTrue(again.getCount()==2&&chest.getItem(0).getCount()+2+again.getCount()==66,"A fresh debit, rather than replaying old funding, conserves all 66 real cobblestones");
   // Simulate the crash boundary: project committed, old custody checkpoint still awaiting its acknowledgement.
   var rejoin=Class.forName("org.villageastra.world.HallFundingCustody").getDeclaredMethod("rejoin",net.minecraft.server.level.ServerLevel.class,CompoundTag.class);rejoin.setAccessible(true);
   var before=HallUpgradeGoal.inspect(t.l,t.s.id());h.assertTrue((Boolean)rejoin.invoke(null,t.l,c),"The project's durable custody acknowledgement survives a retry");
   h.assertTrue(HallUpgradeGoal.inspect(t.l,t.s.id()).equals(before),"Replaying the handover cannot subtract or duplicate cargo again");
  }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();
 }
}
