package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import java.util.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmGameTests {
 private static int slot(Container c,Item item){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))return i;throw new IllegalStateException("Missing fixture item");}
 @GameTest(template="empty",timeoutTicks=200) public static void onlyRegisteredFieldsBelongToFarmer(GameTestHelper h){
  var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(h.getLevel(),origin);var entry=SettlementData.get(h.getLevel().getServer()).entry(s.id());
  // AD-104: a field is one 9x9 module of 80 plots (x -1..7, z 9..17 of the farm, water at its centre).
  h.assertTrue(FarmWorkArea.cells(entry).size()==80&&!FarmWorkArea.cells(entry).contains(origin.offset(36,1,28)),"Only actual starting field cells are selected");
  var second=new Settlement.Building(UUID.randomUUID(),"farm",42,2,12);s.addBuilding(second);var cells=FarmWorkArea.cells(entry);
  h.assertTrue(cells.size()==160&&cells.contains(origin.offset(43,3,22)),"Second registered field and its own elevation are included");
  h.assertTrue(OwnershipEvents.disallowedPlacement(h.getLevel(),origin.offset(-4,1,32)),"Field footprint has a three-block construction buffer");
  // AD-104: the kept level names the layout 'farm@2'; its field and buffer stay the settlement's, far beyond the farmhouse's own.
  // The second farm is raised rather than a third one added: a farm further east would lie in the next test's plot, and its farmer would work there.
  s.raiseBuildingLevel(second.id(),2);var kept=s.buildings().stream().filter(b->b.id().equals(second.id())).findFirst().orElseThrow();
  h.assertTrue(kept.level()==2&&OwnershipEvents.disallowedPlacement(h.getLevel(),FarmField.cells(entry,kept).get(40)),"A farm kept at level II still refuses a block on its field");h.succeed();
 }
 private static void deathWithStage(GameTestHelper h,boolean till){
  var level=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(level,origin);var r=s.residents().stream().filter(x->x.profession()==Profession.FARMER).findFirst().orElseThrow();var worker=(ResidentEntity)level.getEntity(r.id());
  var stock=(Container)level.getBlockEntity(origin.offset(1,1,4));var tool=WorldJournal.take(level,UUID.randomUUID(),origin.offset(1,1,4),slot(stock,Items.STONE_HOE),stock.getItem(slot(stock,Items.STONE_HOE)).copy());
  var id=UUID.randomUUID();var target=origin.offset(0,1,21);
  if(till){level.setBlock(target,Blocks.AIR.defaultBlockState(),3);level.setBlock(target.below(),Blocks.DIRT.defaultBlockState(),3);WorldJournal.place(level,id,target.below(),Blocks.DIRT.defaultBlockState(),Blocks.FARMLAND.defaultBlockState());}
  else WorldJournal.take(level,id,origin.offset(1,1,4),slot(stock,Items.WHEAT_SEEDS),stock.getItem(slot(stock,Items.WHEAT_SEEDS)).copy());
  var state=new CompoundTag();state.putInt("schema",1);state.putUUID("worker",worker.getUUID());state.putUUID("operation",id);state.putString("stage",till?"till":"plant");state.put("tool",tool.save(new CompoundTag()));state.putLong("target",target.asLong());
  var file=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+s.workplace(r.id()).id()+".bin");NbtRecord.write(file,state);
  worker.hurt(level.damageSources().genericKill(),1000);CargoCustody.tick(level.getServer());var custody=CargoCustody.inspect(level.getServer(),worker.getUUID());var pos=BlockPos.of(custody.getList("drops",Tag.TAG_COMPOUND).getCompound(0).getLong("pos"));var pile=(Container)level.getBlockEntity(pos);
  h.assertTrue(pile.countItem(Items.STONE_HOE)==1&&pile.getItem(slot(pile,Items.STONE_HOE)).getDamageValue()==(till?1:0),"Actual hoe and pending till durability retained once");
  h.assertTrue(pile.countItem(Items.WHEAT_SEEDS)==(till?0:1)&&pile.countItem(Items.OAK_SAPLING)==0,"Farmer cargo is wheat seed, not a forester sapling");
  h.assertTrue(stock.countItem(Items.WHEAT_SEEDS)==(till?16:15),"Existing seed withdrawal is neither refunded nor repeated");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void farmerDeathPreservesActualSeedKind(GameTestHelper h){deathWithStage(h,false);}
 @GameTest(template="empty",timeoutTicks=200) public static void tillingBeforeCheckpointConsumesHoeOnce(GameTestHelper h){deathWithStage(h,true);}
}
