package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ColumnFullQueueGameTests {
 @GameTest(template="empty",batch="column_full_queue",timeoutTicks=5000)
 public static void builderAdvancesTheActualSavedGuardhouseQueue(GameTestHelper h) throws Exception{
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(-2485,68,-2457);var held=PhysicalFixtureChunks.force(l,base,-15,25,-15,25);
  for(int x=-15;x<=25;x++)for(int z=-15;z<=25;z++)for(int y=-8;y<=15;y++)l.setBlock(base.offset(x,y,z),(y<=-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  com.google.gson.JsonObject fixture;
  try(var input=ColumnFullQueueGameTests.class.getResourceAsStream("/data/villageastra/gametest/guard_house_wider_fixture.json")){h.assertTrue(input!=null,"Exact saved guardhouse fixture exists");fixture=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
  var palette=new java.util.ArrayList<net.minecraft.world.level.block.state.BlockState>();
  for(var raw:fixture.getAsJsonArray("palette"))palette.add(NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),TagParser.parseTag(raw.toString())));
  for(var raw:fixture.getAsJsonArray("cells")){var cell=raw.getAsJsonArray();l.setBlock(base.offset(cell.get(0).getAsInt(),cell.get(1).getAsInt(),cell.get(2).getAsInt()),palette.get(cell.get(3).getAsInt()),2);}
  var column=base;
  var target=column.above(8);var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));s.addBuilding(new Settlement.Building(home,"home",2,-2,27));var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base.offset(69,-2,57));SettlementData.get(l.getServer()).add(e);
  var research=BookResearch.inspect(l,e);var known=new ListTag();for(var branch:List.of("military","defense","livestock","engineering","medicine","construction","agriculture","caravans","education","housing","mining","forestry","baking","roads","logistics","research","cartography","metallurgy"))known.add(StringTag.valueOf(branch+".1"));research.put("resourceDone",known);BookResearch.store(l,e,research);ResearchKnobs.forget(s.id());h.assertTrue(BookResearch.completed(e,BookResearch.inspect(l,e)).size()==18,"Exactly the eighteen saved research rungs are present");
  CompoundTag state;try(var input=ColumnFullQueueGameTests.class.getResourceAsStream("/data/villageastra/gametest/guard_house_full_queue_fixture.nbt")){h.assertTrue(input!=null,"Original signed project payload fixture exists");state=NbtIo.read(new java.io.DataInputStream(input));}
  var shift=base.subtract(new BlockPos(-2485,68,-2457));
  for(var key:List.of("origin","hatch"))state.putLong(key,BlockPos.of(state.getLong(key)).offset(shift).asLong());
  for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var op=(CompoundTag)raw;for(var key:List.of("pos","stand","site"))if(op.contains(key))op.putLong(key,BlockPos.of(op.getLong(key)).offset(shift).asLong());if(op.contains("standBase"))op.putInt("standBase",op.getInt("standBase")+shift.getY());if(op.contains("retry"))op.putLong("retry",l.getGameTime()+Math.max(0,op.getLong("retry")-10740398L));}
  var id=UUID.randomUUID();state.putUUID("id",id);state.putUUID("project",id);state.putUUID("worker",npc.getUUID());HallUpgradeGoal.store(l,s.id(),state);
  npc.bind(s.id(),r);npc.setHealth(12);npc.moveTo(base.getX()-1.5,base.getY(),base.getZ()-.117091209171,0,0);npc.setOnGround(true);var goal=new HallUpgradeGoal(npc,true);npc.onlyGoals(g->false,5,goal);HomeNeighborhood.restrict(npc);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Builder chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Builder added"));
  Runnable clean=()->{npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};boolean[] entered={false};
  h.onEachTick(()->{
   if(npc.tickCount>0&&npc.tickCount%200==0){var path=npc.getNavigation().getPath();com.mojang.logging.LogUtils.getLogger().info("SAVED_GUARD_ENTRY ticks={} pos={} status={} navDone={} target={} next={} trunkLow={} trunkMid={} trunkHigh={} stand={}",npc.tickCount,npc.position(),npc.workStatus(),npc.getNavigation().isDone(),path==null?null:path.getTarget(),path==null||path.isDone()?null:path.getNextNodePos(),l.getBlockState(base.offset(3,-1,-3)),l.getBlockState(base.offset(3,0,-3)),l.getBlockState(base.offset(3,1,-3)),goal.standDiag);}

   if(npc.getX()>base.getX()-.3)entered[0]=true;
   if(npc.getHealth()!=12){clean.run();h.assertTrue(false,"Builder lost health on physical column approach");return;}
   if(HallUpgradeGoal.inspect(l,s.id()).getInt("progress")<=505||!l.getBlockState(target).is(VillageAstra.TIMBER_SCAFFOLD.get()))return;
   h.assertTrue(entered[0]&&npc.getEyePosition().distanceToSqr(target.getCenter())<=BuildingOrders.REACH_SQ,"Builder entered and climbed within unchanged physical work reach");
   for(int y=-1;y<=1;y++){var cleared=base.offset(3,y,-3);h.assertTrue(l.getBlockState(cleared).isAir()&&org.villageastra.persistence.WorldJournal.exists(l,Settlement.childId(id,"doorway-trunk/"+cleared.asLong())),"Real natural door trunk cleared with stable durable receipt: "+cleared);}
   h.assertTrue(org.villageastra.persistence.WorldJournal.exists(l,Settlement.childId(id,"block/484")),"Original top scaffold operation retains its stable payment/placement identity");
   var current=HallUpgradeGoal.inspect(l,s.id());int booked=6;var original=state.getList("ops",Tag.TAG_COMPOUND);var actual=current.getList("ops",Tag.TAG_COMPOUND);for(int i=0;i<original.size();i++){var before=original.getCompound(i);var after=actual.getCompound(i);if(before.getBoolean("done")||!after.getBoolean("done"))continue;if(after.getString("item").equals("villageastra:timber_scaffold"))booked--;if(after.getString("return").equals("villageastra:timber_scaffold"))booked++;}

   h.assertTrue(l.getBlockState(base.offset(-1,1,0)).is(Blocks.IRON_BARS),"Wall was not removed to get through");
   int heldItems=0;for(var raw:HallUpgradeGoal.inspect(l,s.id()).getList("cargo",Tag.TAG_COMPOUND)){var item=ItemStack.of((CompoundTag)raw);if(item.is(VillageAstra.TIMBER_SCAFFOLD.get().asItem()))heldItems+=item.getCount();}h.assertTrue(heldItems==booked&&heldItems<6,"Only newly committed original scaffold operations consume the saved paid stock");clean.run();h.succeed();
  });
  h.runAtTickTime(4950,()->{String why="Builder stuck across wall: "+npc.position()+" "+npc.workStatus()+" stand="+goal.standDiag+" walk="+goal.walkDiag;clean.run();h.assertTrue(false,why);});
 }
}
