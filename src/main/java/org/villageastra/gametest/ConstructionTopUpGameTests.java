package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ConstructionTopUpGameTests {
 @GameTest(template="empty",batch="construction_top_up",timeoutTicks=6000)
 public static void appendedWorkFetchesTheMissingPaidBlockBeforePlacingIt(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+131072,120,at.getZ());var forced=PhysicalFixtureChunks.force(l,base,-4,28,-4,12);
  for(int x=-4;x<=28;x++)for(int z=-4;z<=12;z++)for(int y=-1;y<=5;y++)l.setBlock(base.offset(x,y,z),(y<=0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var chestPos=LogisticsRoutes.position(e,hall);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.COBBLESTONE));
  var id=UUID.randomUUID();var project=new CompoundTag();project.putUUID("id",id);project.putUUID("project",id);project.putInt("schema",2);project.putString("kind","building");project.putString("design","home");project.putLong("origin",base.asLong());project.putLong("hatch",base.asLong());project.putBoolean("noHatch",true);project.putBoolean("funded",true);
  var cost=new CompoundTag();cost.putInt("minecraft:cobblestone",1);project.put("cost",cost);var cargo=new ListTag();cargo.add(new ItemStack(Items.COBBLESTONE).save(new CompoundTag()));project.put("cargo",cargo);
  var ops=new ListTag();for(int i=0;i<2;i++){var op=new CompoundTag();op.putLong("pos",base.offset(20,1,5+i).asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(Blocks.COBBLESTONE.defaultBlockState()));op.putString("item","minecraft:cobblestone");ops.add(op);}project.put("ops",ops);HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),r);npc.moveTo(base.getX()+18.5,base.getY()+1,base.getZ()+5.5);npc.onlyGoals(g->false,5,new HallUpgradeGoal(npc,true));boolean[] needed={false},started={false};
  h.onEachTick(()->{
   if(!started[0]){if(!TouchLoad.ticking(l,npc.blockPosition()))return;started[0]=l.addFreshEntity(npc);return;}
   var t=HallUpgradeGoal.inspect(l,s.id());
   if(!t.getBoolean("funded")){
    needed[0]=true;h.assertTrue(t.getInt("progress")==1,"Exactly one block was paid before the top-up");
    for(var raw:t.getList("ops",Tag.TAG_COMPOUND)){var op=(CompoundTag)raw;if(!op.getBoolean("done"))h.assertTrue(l.getBlockState(BlockPos.of(op.getLong("pos"))).isAir(),"Unpaid block stays unchanged regardless of handy work order");}
    h.assertTrue(ConstructionFunding.missing(t).getOrDefault("minecraft:cobblestone",0)==1||chest.countItem(Items.COBBLESTONE)==0&&WorldJournal.recoverAmount(l,Settlement.childId(id,"fund/0")).is(Items.COBBLESTONE),"Only one additional block is requested or already physically withdrawn");
   }
   if(t.getInt("progress")<2)return;
   h.assertTrue(needed[0]&&chest.countItem(Items.COBBLESTONE)==0&&t.getInt("withdrawals")==1,"Builder physically fetches the one real top-up from hall stock");
   h.assertTrue(WorldJournal.recoverAmount(l,Settlement.childId(id,"fund/0")).is(Items.COBBLESTONE)&&WorldJournal.inspectCommitted(l,Settlement.childId(id,"block/0"))!=null&&WorldJournal.inspectCommitted(l,Settlement.childId(id,"block/1"))!=null,"Existing funding and placement journal IDs remain authoritative");
   h.assertTrue(t.getCompound("initialCost").getInt("minecraft:cobblestone")==1,"Original quote is retained for audit");npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,forced);h.succeed();
  });
  h.runAtTickTime(5800,()->h.assertTrue(false,"Top-up stalled: "+npc.position()+" "+npc.workStatus()+" "+HallUpgradeGoal.inspect(l,s.id())));
 }
}
