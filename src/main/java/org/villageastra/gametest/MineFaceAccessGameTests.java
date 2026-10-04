package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineFaceAccessGameTests {
 @GameTest(template="empty",batch="mine_face_access",timeoutTicks=600)
 public static void minerWalksDownActualStairsUntilTheLowerFaceIsWithinReach(GameTestHelper h){
  var old=ResearchV2Town.town(h,"mine");var data=SettlementData.get(old.l.getServer());data.remove(old.s.id());
  int y=96;for(int x=8;x<=20;x++)for(int z=0;z<=20;z++)y=Math.max(y,old.l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,old.e.center().getX()+x,old.e.center().getZ()+z)+20);
  var e=new SettlementData.Entry(old.s,old.e.dimension(),new net.minecraft.core.BlockPos(old.e.center().getX(),y,old.e.center().getZ()));data.add(e);ResearchV2Town.lay(old.l,e,old.shop,"mine");
  var t=new ResearchV2Town.Town(old.l,e,old.s,old.shop);var npc=VillageAstra.RESIDENT.get().create(t.l);
  var s=MineWork.read(t.l,t.shop);s.putInt("step",5);s.putInt("cell",9);s.putInt("extentStep",4);s.putInt("width",3);s.putInt("height",5);s.putInt("descent",7);s.putInt("floorStep",27);
  for(int row=0;row<=5;row++)for(int x=1;x<=5;x++)for(int dy=-1;dy<=6;dy++){
   var p=BuildingPlacement.at(e,t.shop,x,-row-7+dy,7+row);var block=(x==1||x==5||dy<0||dy>=5||row==5&&dy<=1)?Blocks.SANDSTONE.defaultBlockState():row==3&&dy==4?Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X):dy==0?Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH):Blocks.AIR.defaultBlockState();t.l.setBlock(p,block,2);
  }
  var target=BuildingPlacement.at(e,t.shop,2,-11,12);t.l.setBlock(target,Blocks.SANDSTONE.defaultBlockState(),2);
  var operation=UUID.randomUUID();s.putUUID("operation",operation);s.putString("stage","dig");s.putLong("target",target.asLong());s.put("before",NbtUtils.writeBlockState(Blocks.SANDSTONE.defaultBlockState()));s.putIntArray("access",new int[]{3,-11,11});s.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));MineWork.write(t.l,t.shop,s);
  var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));
  var start=BuildingPlacement.at(e,t.shop,3,-9,10);npc.moveTo(start.getX()+.52,start.getY()+.5,start.getZ()+.148);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  h.assertTrue(npc.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))>16,"Face starts outside the unchanged physical reach");
  // Production cadence matters here: withoutPlayers also retries blocked navigation every tick.
  // Scope an observer to this goal call only, so other test settlements are not simulated as player-loaded.
  var observer=net.minecraftforge.common.util.FakePlayerFactory.get(t.l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"MineObserver"));
  var duty=new ResourceWorkGoal(npc,false,()->6000L);
  npc.goalSelector.addGoal(1,new net.minecraft.world.entity.ai.goal.Goal(){
   {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
   @SuppressWarnings("unchecked") private boolean online(java.util.function.BooleanSupplier body){
    try{var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");field.setAccessible(true);var players=(List<net.minecraft.server.level.ServerPlayer>)field.get(t.l.getServer().getPlayerList());players.add(observer);try{return body.getAsBoolean();}finally{players.remove(observer);}}
    catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot scope the test observer",ex);}
   }
   @Override public boolean canUse(){return online(duty::canUse);}
   @Override public boolean canContinueToUse(){return online(duty::canContinueToUse);}
   @Override public boolean requiresUpdateEveryTick(){return true;}
   @Override public void tick(){online(()->{duty.tick();return true;});}
   @Override public void stop(){duty.stop();}
  });t.l.addFreshEntity(npc);
  h.runAtTickTime(580,()->h.assertTrue(false,"No physical descent: "+npc.position()+" status="+npc.workStatus()+" navigation="+npc.getNavigation().getTargetPos()+" state="+MineWork.read(t.l,t.shop)));
  h.startSequence().thenWaitUntil(()->h.assertTrue(WorldJournal.inspectCommitted(t.l,operation)!=null,"Miner must physically approach and mine the lower face"))
   .thenExecute(()->{
    h.assertTrue(npc.getZ()>start.getZ()+.5,"Real movement along the descending passage");
    h.assertTrue(t.l.getBlockState(target).isAir()&&ForestFixture.count(MineWork.read(t.l,t.shop).getList("cargo",Tag.TAG_COMPOUND),Items.SANDSTONE)>=1,"Actual target becomes carried stone");
    npc.discard();ResearchV2Town.done(t);
   }).thenSucceed();
 }
}
