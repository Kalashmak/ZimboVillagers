package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ReedRenewalGameTests {
 @GameTest(template="empty",batch="reed_renewal",timeoutTicks=4000)
 public static void matureRememberedPlotSuppliesStockWithoutAnotherFullWildernessSweep(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+106496,90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+38)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+10)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-2;x<=38;x++)for(int z=-2;z<=10;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);var roots=new ArrayList<BlockPos>();
  for(int n=0;n<ReedNursery.CAPACITY;n++){var root=base.offset(24+n,1,2);roots.add(root);l.setBlock(root.below().south(),Blocks.WATER.defaultBlockState(),3);l.setBlock(root,Blocks.SUGAR_CANE.defaultBlockState(),3);ReedNursery.record(l,s.id(),root);}
  var target=roots.get(0).above();l.setBlock(target,Blocks.SUGAR_CANE.defaultBlockState(),3);
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:sugar_cane",1);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(base.getX()+3.5,91,base.getZ()+4.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var prior=new CompoundTag();prior.putInt("surveyCursor",100000);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),prior);npc.goalSelector.addGoal(5,new NaturalSupplyGoal(npc,true));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{var t=NaturalSupplyGoal.inspect(l,npc.getUUID());if(!t.hasUUID("id"))return;
   h.assertTrue(BlockPos.of(t.getLong("target")).equals(target)&&t.getInt("surveyCursor")==100000,"Known mature plant is selected without advancing the remote survey");
   if(!t.getBoolean("complete"))return;
   h.assertTrue(chest.countItem(Items.SUGAR_CANE)==1&&l.getBlockState(target).isAir(),"One actual upper segment reaches stock");
   h.assertTrue(roots.stream().allMatch(p->l.getBlockState(p).is(Blocks.SUGAR_CANE))&&ReedNursery.planted(l,s.id()).size()==8,"All growing bases survive, including immature plants");
   h.assertTrue(npc.tickCount>=200,"Ordinary harvesting labor is paid");npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(3800,()->h.assertTrue(false,"Remembered renewable source was not delivered: "+NaturalSupplyGoal.inspect(l,npc.getUUID())));
 }
}
