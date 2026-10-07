package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CargoCaveReturnGameTests {
 @GameTest(template="empty",batch="cargo_cave_return",timeoutTicks=3000)
 public static void reassignedMinerPhysicallyReturnsCargoFromDeepDryCave(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+110592,120,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-4,40,-8,8);
  for(int x=-4;x<=40;x++)for(int z=-8;z<=8;z++)for(int y=-10;y<=4;y++){
   boolean hollow=x>=8&&x<=24&&Math.abs(z)<=3;
   l.setBlock(base.offset(x,y,z),(y<=(hollow?-9:0)?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var mine=new Settlement.Building(UUID.randomUUID(),"mine",35,0,0);
  s.addBuilding(hall);s.addBuilding(mine);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=HallSite.stock(e);l.setBlock(stock,Blocks.CHEST.defaultBlockState(),2);var chest=(Container)l.getBlockEntity(stock);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());npc.bind(s.id(),r);
  var work=MineWork.read(l,mine);work.putUUID("worker",r.id());work.putString("stage","deliver");work.putBoolean("advanced",true);
  var cargo=new ListTag();cargo.add(new ItemStack(Items.COBBLESTONE,10).save(new CompoundTag()));work.put("cargo",cargo);
  var pick=new ItemStack(Items.STONE_PICKAXE);pick.setDamageValue(12);work.put("tool",pick.save(new CompoundTag()));MineWork.write(l,mine,work);
  s.assign(r.id(),Profession.PORTER,hall.id());npc.moveTo(base.getX()+16.5,base.getY()-8,base.getZ()+.5);
  h.assertTrue(CargoCustody.beginReturn(npc),"Existing tool and cargo enter custody after reassignment");
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(0,new PitEscapeGoal(npc));
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}public boolean canUse(){return CargoCustody.pending(l.getServer(),npc.getUUID());}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0)CargoCustody.returnStep(npc,true);}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Cave entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{if(!CargoCustody.pending(l.getServer(),npc.getUUID())){
   h.assertTrue(chest.countItem(Items.COBBLESTONE)==10&&chest.countItem(Items.STONE_PICKAXE)==1&&npc.getY()>=base.getY(),"Physical return deposits exact held items at surface");
   h.assertTrue(npc.getHealth()==npc.getMaxHealth()&&!MineWork.read(l,mine).hasUUID("worker"),"Safe return releases old workplace only after delivery");clean.run();h.succeed();
  }});
  h.runAtTickTime(2800,()->{String why="Custody carrier did not leave cave: "+npc.position()+" ticks="+npc.tickCount+" goals="+npc.runningGoals();clean.run();h.assertTrue(false,why);});
 }
}
