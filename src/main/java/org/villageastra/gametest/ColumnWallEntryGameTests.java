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
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ColumnWallEntryGameTests {
 @GameTest(template="empty",batch="column_wall_entry",timeoutTicks=1600)
 public static void builderEntersRealSavedGuardhouseInsteadOfChoosingItsOwnStandAcrossWall(GameTestHelper h) throws Exception{
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+688128,120,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-8,20,-8,20);
  for(int x=-8;x<=20;x++)for(int z=-8;z<=20;z++)for(int y=-4;y<=15;y++)l.setBlock(base.offset(x,y,z),(y<=-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  com.google.gson.JsonObject fixture;
  try(var input=ColumnWallEntryGameTests.class.getResourceAsStream("/data/villageastra/gametest/guard_house_column_fixture.json")){h.assertTrue(input!=null,"Exact saved guardhouse fixture exists");fixture=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
  var palette=new java.util.ArrayList<net.minecraft.world.level.block.state.BlockState>();
  for(var raw:fixture.getAsJsonArray("palette"))palette.add(NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),TagParser.parseTag(raw.toString())));
  for(var raw:fixture.getAsJsonArray("cells")){var cell=raw.getAsJsonArray();l.setBlock(base.offset(cell.get(0).getAsInt(),cell.get(1).getAsInt(),cell.get(2).getAsInt()),palette.get(cell.get(3).getAsInt()),2);}
  var column=base;
  var target=column.above(8);var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);RestaurantFixture.hold(l,s.id(),base.offset(-8,0,-8),base.offset(20,0,20));
  var state=new CompoundTag();var id=UUID.randomUUID();state.putInt("schema",2);state.putUUID("id",id);state.putUUID("project",id);state.putString("kind","building");state.putString("design","guard_house");state.putLong("origin",column.offset(-1,-1,-2).asLong());state.putLong("hatch",column.asLong());state.putBoolean("noHatch",true);state.putBoolean("funded",true);
  var op=new CompoundTag();op.putLong("pos",target.asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState()));op.putString("item","villageastra:timber_scaffold");op.putLong("stand",column.above(6).asLong());op.putInt("standBase",column.getY());var ops=new ListTag();ops.add(op);state.put("ops",ops);var cargo=new ListTag();cargo.add(new ItemStack(VillageAstra.TIMBER_SCAFFOLD.get()).save(new CompoundTag()));state.put("cargo",cargo);HallUpgradeGoal.store(l,s.id(),state);
  npc.bind(s.id(),r);npc.setHealth(12);npc.moveTo(base.getX()-1.5,base.getY(),base.getZ()-.117091209171,0,0);npc.setOnGround(true);var goal=new HallUpgradeGoal(npc,true);npc.onlyGoals(g->false,5,goal);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Builder chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Builder added"));
  Runnable clean=()->{npc.discard();SettlementData.get(l.getServer()).remove(s.id());RestaurantFixture.release(l,s.id());PhysicalFixtureChunks.release(l,held);};boolean[] entered={false};
  h.onEachTick(()->{
   if(npc.getX()>base.getX()-.3)entered[0]=true;
   if(npc.getHealth()!=12){clean.run();h.assertTrue(false,"Builder lost health on physical column approach");return;}
   if(!l.getBlockState(target).is(VillageAstra.TIMBER_SCAFFOLD.get()))return;
   h.assertTrue(entered[0]&&npc.getEyePosition().distanceToSqr(target.getCenter())<=BuildingOrders.REACH_SQ,"Builder entered and climbed within unchanged physical work reach");
   h.assertTrue(l.getBlockState(base.offset(-1,1,0)).is(Blocks.IRON_BARS),"Wall was not removed to get through");
   int heldItems=0;for(var raw:HallUpgradeGoal.inspect(l,s.id()).getList("cargo",Tag.TAG_COMPOUND))heldItems+=ItemStack.of((CompoundTag)raw).getCount();h.assertTrue(heldItems==0,"The original one scaffold is paid once");clean.run();h.succeed();
  });
  h.runAtTickTime(1550,()->{String why="Builder stuck across wall: "+npc.position()+" "+npc.workStatus()+" ticks="+npc.tickCount+" attached="+(l.getEntity(npc.getUUID())==npc)+" ticking="+l.isPositionEntityTicking(npc.blockPosition())+" goals="+npc.runningGoals()+" stand="+goal.standDiag+" walk="+goal.walkDiag;clean.run();h.assertTrue(false,why);});
 }
}
