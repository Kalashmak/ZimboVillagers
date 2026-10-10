package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;
/** AD478: normal entity admission selects both ordinary goal scheduling parities without changing body ticks. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WolfRepathCadenceGameTests {
 @GameTest(template="empty",batch="wolf_repath_cadence",timeoutTicks=1400)
 public static void anEvenBodyCadencePhysicallyPaysAndReturnsItsOriginalParcel(GameTestHelper h){run(h,0);}
 @GameTest(template="empty",batch="wolf_repath_cadence",timeoutTicks=1400)
 public static void anOddBodyCadencePhysicallyPaysAndReturnsItsOriginalParcel(GameTestHelper h){run(h,1);}
 private static void run(GameTestHelper h,int parity){
  var t=WarehouseReliefGameTests.town(h,true,true);int[] ticks={0},mask={0},observed={0};boolean[] finished={false};CompoundTag[] paid={null};float hp=t.wolf().getHealth();
  WarehouseReliefGameTests.admitted(h,t,parity).thenExecute(()->{t.wolf().goalSelector.removeAllGoals(g->true);t.wolf().targetSelector.removeAllGoals(g->true);t.wolf().setNoAi(false);}).thenWaitUntil(()->h.assertTrue(t.wolf().onGround(),"Canonical actual wolf naturally grounds before route query")).thenExecute(()->{
   WarehouseReliefGameTests.nativeRouteReady(h,t);LogisticsRoutes.chest(t.l(),t.e(),t.store()).setItem(0,new ItemStack(VillageAstra.CART_ITEM.get(),1));WarehouseReliefGameTests.cart(h,t);LogisticsRoutes.chest(t.l(),t.e(),t.mine()).setItem(0,new ItemStack(Items.COBBLESTONE,32));Warehouses.tick(t.l(),t.e(),20);Warehouses.tick(t.l(),t.e(),20);
   var actual=new WolfKennelGoal(t.wolf());h.assertTrue(!actual.requiresUpdateEveryTick(),"Actual kennel goal retains ordinary cadence");t.wolf().goalSelector.addGoal(0,actual);t.wolf().goalSelector.addGoal(10,new Goal(){@Override public boolean canUse(){return true;}@Override public void tick(){mask[0]|=1<<(t.wolf().tickCount&1);if(observed[0]++<12)com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_WOLF_REPATH requestedParity={} serverTick={} entityId={} ordinaryBodyTick={} mod10={}",parity,t.l().getServer().getTickCount(),t.wolf().getId(),t.wolf().tickCount,t.wolf().tickCount%10);}});
  }).thenWaitUntil(()->{
   if(ticks[0]++%20==0){Warehouses.tick(t.l(),t.e(),20);var x=WarehouseTrips.inspect(t.l(),t.wolf().getUUID());if(paid[0]==null&&!WarehouseTrips.custody(t.l(),x).isEmpty()){paid[0]=x.copy();WarehouseTrips.forget(t.l().getServer());h.assertTrue(WarehouseTrips.inspect(t.l(),t.wolf().getUUID()).equals(paid[0]),"Fresh signed reader preserves the actual paid checkpoint");}if(paid[0]!=null&&!WarehouseTrips.active(x))finished[0]=true;}
   h.assertTrue(finished[0],"Both real ordinary scheduling parities must physically take and return32; requested="+parity+" observedMask="+mask[0]+" paid="+(paid[0]!=null)+" stage="+WarehouseTrips.inspect(t.l(),t.wolf().getUUID()).getString("stage")+" pos="+t.wolf().position()+" sent="+VillageWolves.sentTo(t.wolf())+" nav="+t.wolf().getNavigation().getPath());
  }).thenExecute(()->{try{
   h.assertTrue(mask[0]==1<<parity&&observed[0]>30,"Actual ordinary goal ticks used exactly the requested body parity");h.assertTrue(t.wolf().tickCount>100&&t.wolf().getHealth()==hp&&Arrays.equals(WarehouseTrips.audit(t.l(),paid[0]),new int[]{32,32,0}),"Real paid walking preserves health and all32 units");var id=paid[0].getUUID("id");var put=WorldJournal.inspectCommitted(t.l(),Settlement.childId(id,"put/0"));var back=WorldJournal.inspectCommitted(t.l(),Settlement.childId(id,"back/0"));var receipt=put!=null?put:back;h.assertTrue((put==null)!=(back==null)&&receipt.getLong("pos")==LogisticsRoutes.position(t.e(),t.store()).asLong()&&LogisticsRoutes.chest(t.l(),t.e(),t.store()).countItem(Items.COBBLESTONE)==32&&LogisticsRoutes.chest(t.l(),t.e(),t.mine()).countItem(Items.COBBLESTONE)==0,"Exactly one original deposit puts all32 at the intended warehouse");
   for(int i=0;i<5;i++)Warehouses.tick(t.l(),t.e(),20);h.assertTrue(LogisticsRoutes.chest(t.l(),t.e(),t.store()).countItem(Items.COBBLESTONE)==32&&LogisticsRoutes.chest(t.l(),t.e(),t.mine()).countItem(Items.COBBLESTONE)==0,"Five repeats never duplicate or lose the paid parcel");com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_WOLF_REPATH physical=true requestedParity={} observedMask={} bodyTicks={} receiptKind={} exactWarehouse=32 replay=5 audit={}",parity,mask[0],t.wolf().tickCount,put!=null?"put/0":"back/0",Arrays.toString(WarehouseTrips.audit(t.l(),paid[0])));
  }finally{t.close();}}).thenSucceed();
 }
}
