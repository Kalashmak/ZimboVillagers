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
public final class MineReturnWaypointGameTests {
 @GameTest(template="empty",batch="mine_return_waypoint",timeoutTicks=9000)
 public static void reassignedMinerReturnsThroughKnownGalleryAroundLongCaveDetour(GameTestHelper h){run(h,false);}
 @GameTest(template="empty",batch="mine_return_water",timeoutTicks=9000)
 public static void knownGalleryReturnCanCrossVerifiedOpenWater(GameTestHelper h){run(h,true);}
 private static void run(GameTestHelper h,boolean wet){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+151552,120,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-8,20,-8,190);
  for(int x=-8;x<=20;x++)for(int z=-8;z<=190;z++)for(int y=-1;y<=10;y++){
   boolean corridor=z>=0&&z<=182&&(x>=0&&x<=2||x>=10&&x<=12||z>=180&&x>=0&&x<=12);
   l.setBlock(base.offset(x,y,z),(!corridor||y<1||y>3?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  if(wet)for(int x=0;x<=2;x++)for(int z=10;z<=12;z++)for(int y=-2;y<=0;y++)l.setBlock(base.offset(x,y,z),Blocks.WATER.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base.offset(10,0,-4));
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var mine=new Settlement.Building(UUID.randomUUID(),"mine",-13,8,178);
  s.addBuilding(hall);s.addBuilding(mine);s.noteMine(mine.id(),0,3,5,7);s.noteMine(mine.id(),new MineArea.Gallery(0,MineDrive.EAST,10));var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=HallSite.stock(e);l.setBlock(stock,Blocks.CHEST.defaultBlockState(),2);var chest=(Container)l.getBlockEntity(stock);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());npc.bind(s.id(),r);
  var work=MineWork.read(l,mine);work.putUUID("worker",r.id());work.putString("stage","deliver");work.putBoolean("advanced",true);
  var cargo=new ListTag();cargo.add(new ItemStack(Items.COBBLESTONE,10).save(new CompoundTag()));work.put("cargo",cargo);
  var pick=new ItemStack(Items.STONE_PICKAXE);pick.setDamageValue(12);work.put("tool",pick.save(new CompoundTag()));MineWork.write(l,mine,work);
  s.assign(r.id(),Profession.PORTER,hall.id());npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);
  var direct=ResourceReturnRoute.plan(npc,stock.east());h.assertTrue(direct!=null&&!direct.canReach(),"Direct search cannot complete the long underground detour");
  h.assertTrue(CargoCustody.beginReturn(npc),"Existing tool and cargo enter custody after reassignment");
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(0,new net.minecraft.world.entity.ai.goal.FloatGoal(npc));npc.goalSelector.addGoal(1,new ShoreEscapeGoal(npc));
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}public boolean canUse(){return CargoCustody.pending(l.getServer(),npc.getUUID());}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0)CargoCustody.returnStep(npc,true);}
   public void stop(){npc.getNavigation().stop();}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Cave entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  boolean[] resumed={false};
  h.onEachTick(()->{
   if(!resumed[0]&&npc.onGround()&&npc.getZ()>base.getZ()+20){
    var saved=CargoCustody.inspect(l.getServer(),npc.getUUID());h.assertTrue(saved.contains("returnWaypoint"),"Long detour retains its known gallery");
    var disk=org.villageastra.persistence.NbtRecord.read(l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-custody/"+npc.getUUID()+".bin"));
    h.assertTrue(disk.getLong("returnWaypoint")==saved.getLong("returnWaypoint"),"Selected gallery is durably saved with custody");
    npc.getNavigation().stop();CargoCustody.returnStep(npc,true);npc.getNavigation().recomputePath();
    var path=npc.getNavigation().getPath();h.assertTrue(path!=null&&path.canReach()&&path.getTarget().asLong()==saved.getLong("returnWaypoint"),"Goal interruption and native repath retain the same reachable leg");resumed[0]=true;
   }
   if(!CargoCustody.pending(l.getServer(),npc.getUUID())){
   h.assertTrue(chest.countItem(Items.COBBLESTONE)==10&&chest.countItem(Items.STONE_PICKAXE)==1&&npc.getY()>=base.getY(),"Physical return deposits exact held items at surface");
   h.assertTrue(resumed[0]&&npc.getHealth()==npc.getMaxHealth()&&!MineWork.read(l,mine).hasUUID("worker"),"Safe return releases old workplace only after delivery");clean.run();h.succeed();
  }});
  h.runAtTickTime(8500,()->{String why="Custody carrier did not leave cave: "+npc.position()+" ticks="+npc.tickCount+" goals="+npc.runningGoals();clean.run();h.assertTrue(false,why);});
 }
}
