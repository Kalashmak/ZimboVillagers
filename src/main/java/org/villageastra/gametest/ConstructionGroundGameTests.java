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
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** A local paid construction operation over a fractional-height field, not a whole-building acceptance. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ConstructionGroundGameTests {
 @GameTest(template="empty",batch="construction_ground",timeoutTicks=3600)
 public static void builderFundsAndWorksFromFarmland(GameTestHelper h){run(h,true);}
 @GameTest(template="empty",batch="construction_path",timeoutTicks=3600)
 public static void builderFundsAndWorksFromDirtPath(GameTestHelper h){run(h,false);}
 @GameTest(template="empty",batch="construction_ground",timeoutTicks=3600)
 public static void builderRaisesPaidColumnFromFarmland(GameTestHelper h){run(h,true,true);}
 private static void run(GameTestHelper h,boolean field){run(h,field,false);}
 private static void run(GameTestHelper h,boolean field,boolean column){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(4,3,4));var surface=field?Blocks.FARMLAND:Blocks.DIRT_PATH;
  var forced=PhysicalFixtureChunks.force(l,base,-3,24,-3,24);
  for(int x=-3;x<=24;x++)for(int z=-3;z<=24;z++){
   l.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(base.offset(x,0,z),surface.defaultBlockState(),2);
   for(int y=1;y<=(column?29:9);y++)l.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
   if(column)l.setBlock(base.offset(x,30,z),Blocks.GLASS.defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var stockPos=LogisticsRoutes.position(e,hall);l.setBlock(stockPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.setItem(0,new ItemStack(Items.COBBLESTONE));
  var target=base.offset(12,column?24:6,12);var op=new CompoundTag();op.putLong("pos",target.asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(Blocks.COBBLESTONE.defaultBlockState()));op.putString("item","minecraft:cobblestone");
  var ops=new ListTag();
  if(column){
   stock.setItem(1,new ItemStack(VillageAstra.TIMBER_SCAFFOLD.get(),23));
   for(int y=1;y<=23;y++){var step=new CompoundTag();step.putLong("pos",base.offset(12,y,12).asLong());step.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));step.put("after",NbtUtils.writeBlockState(VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState()));step.putString("item","villageastra:timber_scaffold");step.putBoolean("barn",true);
    if(y>=3){step.putLong("stand",base.offset(12,y-2,12).asLong());step.putInt("standBase",base.getY()+1);}ops.add(step);}
   op.putLong("stand",base.offset(12,22,12).asLong());op.putInt("standBase",base.getY()+1);op.putBoolean("barn",true);
  }
  ops.add(op);var project=new CompoundTag();var id=UUID.randomUUID();project.putInt("schema",2);project.putString("kind","building");project.putUUID("id",id);project.putUUID("project",id);project.putString("design","home");project.putLong("origin",base.asLong());project.putLong("hatch",base.asLong());project.putBoolean("noHatch",true);project.put("ops",ops);project.put("cargo",new ListTag());var cost=new CompoundTag();cost.putInt("minecraft:cobblestone",1);if(column)cost.putInt("villageastra:timber_scaffold",23);project.put("cost",cost);HallUpgradeGoal.enqueue(l,e,project);
  var npc=VillageAstra.RESIDENT.get().create(l);var resident=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(resident,home);s.assign(resident.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),resident);npc.moveTo(base.getX()+2.5,base.getY()+1,base.getZ()+2.5,0,0);
  npc.onlyGoals(g->false,5,new HallUpgradeGoal(npc,true));l.addFreshEntity(npc);boolean[] ended={false};
  Runnable cleanup=()->{ended[0]=true;PhysicalFixtureChunks.release(l,forced);npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());};
  h.onEachTick(()->{if(ended[0]||!l.getBlockState(target).is(Blocks.COBBLESTONE))return;h.assertTrue(stock.countItem(Items.COBBLESTONE)==0,"Real stock paid for the operation");if(column)h.assertTrue(stock.countItem(VillageAstra.TIMBER_SCAFFOLD.get().asItem())==0,"Every scaffold came from real finite stock");cleanup.run();h.succeed();});
  h.runAtTickTime(3500,()->{if(ended[0])return;String detail=surface+" status="+npc.workStatus()+" at="+npc.position()+" ground="+npc.onGround();cleanup.run();throw new GameTestAssertException("Builder cannot work over partial-height soil: "+detail);});
 }
}
