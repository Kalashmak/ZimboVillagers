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
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HarvestApproachGameTests {
 @GameTest(template="empty",batch="harvest_approach",timeoutTicks=4000)
 public static void finalHarvestApproachFollowsRouteAroundSolidCorner(GameTestHelper h){trip(h,false);}
 @GameTest(template="empty",batch="harvest_approach_gap",timeoutTicks=4000)
 public static void finalHarvestApproachAvoidsUnsupportedCorner(GameTestHelper h){trip(h,true);}
 private static void trip(GameTestHelper h,boolean gap){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(gap?61440:57344),90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+25)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+10)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-2;x<=25;x++)for(int z=-2;z<=10;z++)for(int y=-3;y<=4;y++)l.setBlock(base.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var target=base.offset(19,1,5);var stand=base.offset(20,1,5);l.setBlock(target,Blocks.SAND.defaultBlockState(),2);
  var obstacle=base.offset(20,1,6);if(gap){for(int y=0;y>=-2;y--)l.setBlock(base.offset(21,y,5),Blocks.AIR.defaultBlockState(),2);}else{l.setBlock(obstacle,Blocks.STONE.defaultBlockState(),2);l.setBlock(obstacle.above(),Blocks.STONE.defaultBlockState(),2);}
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(base.getX()+(gap?21.6:21.4),base.getY()+1,base.getZ()+(gap?6.3:6.4));npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  h.assertTrue(npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(stand))<2.25,"Starts inside the old direct-approach radius");h.assertTrue(HarvestAccess.standing(l,stand,target)&&HarvestAccess.reversible(npc.routeTo(stand,0,NaturalSupplyGoal.ROUTE_RANGE)),"A real reversible route goes around the corner");
  var t=new CompoundTag();t.putUUID("id",UUID.randomUUID());t.putLong("target",target.asLong());t.putLong("stand",stand.asLong());t.put("before",NbtUtils.writeBlockState(Blocks.SAND.defaultBlockState()));t.putString("stage","dig");NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),t);npc.goalSelector.addGoal(5,new NaturalSupplyGoal(npc,true));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Entity chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Resident added"));
  h.onEachTick(()->{h.assertTrue(npc.getY()>=base.getY()+1-.05,"Approach never falls into the gap");var state=NaturalSupplyGoal.inspect(l,npc.getUUID());if(!state.getBoolean("complete"))return;
   h.assertTrue(chest.countItem(Items.SAND)==1&&l.getBlockState(target).isAir(),"Resident really harvested and delivered, instead of timing out empty at "+npc.position());h.assertTrue(npc.tickCount>=200,"Harvest paid its labor");
   h.assertTrue(gap?l.getBlockState(base.offset(21,0,5)).isAir():l.getBlockState(obstacle).is(Blocks.STONE)&&l.getBlockState(obstacle.above()).is(Blocks.STONE),"No bridging, tunnelling or terrain replacement");npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(3800,()->h.assertTrue(false,"Approach stalled at "+npc.position()+" state="+NaturalSupplyGoal.inspect(l,npc.getUUID())));
 }
}
