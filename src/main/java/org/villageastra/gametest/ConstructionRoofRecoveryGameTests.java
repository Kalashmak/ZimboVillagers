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
public final class ConstructionRoofRecoveryGameTests {
 @GameTest(template="empty",batch="construction_roof_recovery",timeoutTicks=8400)
 public static void builderLeavesObservedRoofLoopAndPlacesFundedRoofBlock(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+208896,90,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-8,16,-8,18);
  for(var p:BlockPos.betweenClosed(base.offset(-8,-4,-8),base.offset(16,17,18)))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
  try(var in=ConstructionRoofRecoveryGameTests.class.getResourceAsStream("/data/villageastra/fixtures/fresh4_house_roof_20261008.json")){
   for(var raw:com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){
    var c=raw.getAsJsonArray();var state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),c.get(3).getAsString(),false).blockState();l.setBlock(base.offset(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),state,2);
   }
  }catch(Exception ex){throw new RuntimeException(ex);}
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",-40,0,0);s.addBuilding(hall);var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),r);
  var target=base.offset(4,10,1);h.assertTrue(l.getBlockState(target).isAir(),"Observed roof operation remains unbuilt");
  var after=Blocks.SMOOTH_SANDSTONE.defaultBlockState();
  var op=new CompoundTag();op.putLong("pos",target.asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(after));op.putString("item","minecraft:smooth_sandstone");op.putLong("stand",base.offset(5,4,0).asLong());op.putInt("standBase",base.getY()+1);
  var ops=new ListTag();ops.add(op);var t=new CompoundTag();var id=UUID.randomUUID();t.putInt("schema",2);t.putUUID("id",id);t.putUUID("project",id);t.putUUID("worker",r.id());t.putString("kind","building");t.putString("design","home");t.putInt("progress",352);t.putString("wood","birch");t.putLong("origin",base.asLong());t.putLong("hatch",base.offset(-100,0,-100).asLong());t.putBoolean("noHatch",true);t.putBoolean("funded",true);t.put("ops",ops);var cargo=new ListTag();cargo.add(new ItemStack(Items.SMOOTH_SANDSTONE).save(new CompoundTag()));t.put("cargo",cargo);HallUpgradeGoal.store(l,s.id(),t);
  npc.moveTo(base.getX()+3.49976,base.getY()+11,base.getZ()+3.35945);npc.setOnGround(true);h.assertTrue(l.noCollision(npc,npc.getBoundingBox()),"Observed starting roof body fits");var goal=new HallUpgradeGoal(npc,true);npc.onlyGoals(g->g instanceof SafeDescentGoal||g instanceof PitEscapeGoal||g instanceof ResidentDoorGoal||g instanceof DoorwayGoal,5,goal);npc.targetSelector.removeAllGoals(g->true);
  boolean[] done={false};Runnable cleanup=()->{done[0]=true;npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  h.startSequence().thenWaitUntil(()->h.assertTrue(TouchLoad.ticking(l,npc.blockPosition()),"Native fixture ticking ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Actual builder body added"));
  h.onEachTick(()->{if(done[0])return;h.assertTrue(npc.isAlive()&&npc.getHealth()==npc.getMaxHealth(),"Roof recovery is safe: "+npc.position()+" health="+npc.getHealth());if(!l.getBlockState(target).is(Blocks.SMOOTH_SANDSTONE))return;h.assertTrue(npc.getEyePosition().distanceToSqr(target.getCenter())<=BuildingOrders.REACH_SQ+.01,"Builder approached within ordinary eye/tool reach: "+npc.position()+" distanceSq="+npc.getEyePosition().distanceToSqr(target.getCenter()));var current=HallUpgradeGoal.inspect(l,s.id());h.assertTrue(current.getList("cargo",Tag.TAG_COMPOUND).stream().allMatch(v->ItemStack.of((CompoundTag)v).isEmpty()),"Exactly one funded sandstone block consumed");System.out.println("CONSTRUCTION_ROOF_RECOVERY bodyTicks="+npc.tickCount);cleanup.run();h.succeed();});
  h.runAtTickTime(8200,()->{if(done[0])return;var why=npc.position()+" goals="+npc.runningGoals()+" status="+npc.workStatus()+" op="+goal.opDiag+" stand="+goal.standDiag;cleanup.run();throw new GameTestAssertException("Observed roof recovery stalled: "+why);});
 }
}
