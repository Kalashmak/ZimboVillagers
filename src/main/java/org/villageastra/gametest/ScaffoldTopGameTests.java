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
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ScaffoldTopGameTests {
 @GameTest(template="empty",batch="scaffold_top",timeoutTicks=2000)
 public static void builderDescendsFromTheTopForLowerReachableWork(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+65536,90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+46)>>4;x++)for(int z=(base.getZ()-12)>>4;z<=(base.getZ()+8)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-2;x<=46;x++)for(int z=-12;z<=8;z++)for(int y=0;y<=16;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var column=base.offset(4,1,0);for(int y=0;y<=9;y++)l.setBlock(column.above(y),VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState(),2);
  // The body stands in air on the top platform, above the height that reaches the target.
  var target=column.offset(5,7,2);var settlement=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);settlement.addBuilding(hall);
  var home=new Settlement.Home(UUID.randomUUID(),1,2,true);settlement.addHome(home);var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(r,home.id());settlement.assign(r.id(),Profession.BUILDER,hall.id());
  var entry=new SettlementData.Entry(settlement,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(entry);
  var state=new CompoundTag();var id=UUID.randomUUID();state.putInt("schema",2);state.putUUID("id",id);state.putUUID("project",id);state.putString("kind","building");state.putString("design","home");state.putLong("origin",column.below().asLong());state.putLong("hatch",column.asLong());state.putBoolean("noHatch",true);state.putBoolean("funded",true);
  var op=new CompoundTag();op.putLong("pos",target.asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(Blocks.OAK_PLANKS.defaultBlockState()));op.putString("item","minecraft:oak_planks");op.putLong("stand",column.above(5).asLong());op.putInt("standBase",column.getY());var ops=new ListTag();ops.add(op);state.put("ops",ops);
  var cargo=new ListTag();cargo.add(new ItemStack(Items.OAK_PLANKS).save(new CompoundTag()));state.put("cargo",cargo);HallUpgradeGoal.store(l,settlement.id(),state);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(settlement.id(),settlement.resident(r.id()));npc.moveTo(base.getX()+4.5,base.getY()+11,base.getZ()+.5);npc.setOnGround(true);var goal=new HallUpgradeGoal(npc,true);npc.onlyGoals(g->false,5,goal);
  double startHeight=npc.getY();boolean[] descended={false};h.assertTrue(npc.getEyePosition().distanceToSqr(target.getCenter())>BuildingOrders.REACH_SQ,"Initial top position cannot remotely reach the target");
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Entity chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Builder added"));
  h.onEachTick(()->{
   if(npc.getY()<startHeight-1)descended[0]=true;
   if(!l.getBlockState(target).is(Blocks.OAK_PLANKS))return;
   h.assertTrue(descended[0],"The builder physically descended instead of gaining reach");h.assertTrue(l.getBlockState(column.above(9)).is(VillageAstra.TIMBER_SCAFFOLD.get()),"Upper platform is preserved");
   h.assertTrue(npc.getEyePosition().distanceToSqr(target.getCenter())<=BuildingOrders.REACH_SQ,"No extra reach was granted");
   int held=0;for(var raw:HallUpgradeGoal.inspect(l,settlement.id()).getList("cargo",Tag.TAG_COMPOUND))held+=ItemStack.of((CompoundTag)raw).getCount();
   h.assertTrue(held==0,"The placed plank was paid exactly once");
   npc.discard();SettlementData.get(l.getServer()).remove(settlement.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(1800,()->h.assertTrue(false,"Scaffold top prevented actual descent: "+npc.position()+" "+npc.workStatus()));
 }
}
