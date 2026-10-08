package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkshopCaveReturnGameTests {
 @GameTest(template="empty",batch="workshop_cave_return",timeoutTicks=6000)
 public static void mayorReturnsFromDeepDryCaveAndFinishesTheSamePaidHallJob(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+393216,120,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-4,40,-8,8);
  for(int x=-4;x<=40;x++)for(int z=-8;z<=8;z++)for(int y=-10;y<=4;y++){
   boolean hollow=x>=8&&x<=24&&Math.abs(z)<=3;
   l.setBlock(base.offset(x,y,z),(y<=(hollow?-9:0)?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=HallSite.stock(e);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=(Container)l.getBlockEntity(stock);chest.setItem(0,new ItemStack(Items.OAK_PLANKS,2));
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MAYOR,hall.id());npc.bind(s.id(),r);
  long now=l.getGameTime();var wants=List.of(new Workshops.Want(Ingredient.of(Items.STICK),4,hall.id()));
  for(int turn=0;turn<4&&!Workshops.inspect(l,hall.id()).getString("stage").equals("work");turn++)Workshops.advance(l,e,hall,now+=20,wants);
  var paid=Workshops.inspect(l,hall.id());h.assertTrue(paid.getString("stage").equals("work")&&chest.countItem(Items.OAK_PLANKS)==0,"Real planks paid through ordinary funding before travel");var job=paid.getUUID("id");
  npc.moveTo(base.getX()+16.5,base.getY()-8,base.getZ()+.5);npc.onlyGoals(g->g instanceof PitEscapeGoal||g instanceof SafeDescentGoal||g instanceof ResidentDoorGoal,5,new WorkshopGoal(npc,true,l::getGameTime));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Cave entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  boolean[] done={false};Runnable clean=()->{done[0]=true;npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{if(done[0])return;h.assertTrue(npc.isAlive()&&npc.getHealth()==npc.getMaxHealth(),"Cave return preserves health");if(chest.countItem(Items.STICK)!=4)return;
   h.assertTrue(npc.getY()>=base.getY()&&npc.distanceToSqr(stock.getX()+1.5,stock.getY(),stock.getZ()+.5)<=6.25,"The mayor physically returned to the station");h.assertTrue(Workshops.inspect(l,hall.id()).getUUID("id").equals(job),"Same paid job, without replacement or refund");
   h.assertTrue(chest.countItem(Items.OAK_PLANKS)==0&&l.getBlockState(base.offset(7,-5,0)).is(Blocks.STONE),"Paid inputs and climbing wall are preserved");clean.run();h.succeed();});
  h.runAtTickTime(5800,()->{if(done[0])return;var why=npc.position()+" goals="+npc.runningGoals()+" stage="+Workshops.inspect(l,hall.id()).getString("stage");clean.run();throw new GameTestAssertException("Workshop cave return stalled: "+why);});
 }
}
