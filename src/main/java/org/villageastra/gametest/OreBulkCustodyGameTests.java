package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;

/** Transaction boundaries for the same borrowed tool; these fixtures do not simulate navigation. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class OreBulkCustodyGameTests {
 @GameTest(template="empty",batch="ore_bulk_custody",timeoutTicks=200)
 public static void deathAfterVeinReceiptKeepsTwoOreAndOneTwiceUsedPick(GameTestHelper h){check(h,true);}
 @GameTest(template="empty",batch="ore_bulk_custody",timeoutTicks=200)
 public static void deathAfterVeinCheckpointKeepsTheSameFiniteCargo(GameTestHelper h){check(h,false);}
 private static void check(GameTestHelper h,boolean receiptAhead){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var mine=new Settlement.Building(UUID.randomUUID(),"mine",20,0,0);s.addBuilding(mine);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.STONE_PICKAXE));
  var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);s.assign(person.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(person.id()));npc.moveTo(base.getX()+2.5,base.getY()+1,base.getZ()+2.5);npc.setNoAi(true);l.addFreshEntity(npc);
  var first=UUID.randomUUID();var second=UUID.randomUUID();var third=UUID.randomUUID();var pick=WorldJournal.takeAmount(l,Settlement.childId(first,"tool"),stock,0,chest.getItem(0).copy(),1);
  var ore=base.offset(8,3,0);for(int i=0;i<3;i++){l.getChunkAt(ore.south(i));l.setBlock(ore.south(i),Blocks.IRON_ORE.defaultBlockState(),2);}
  var bag=new ListTag();for(var item:WorldJournal.harvest(l,first,ore,Blocks.IRON_ORE.defaultBlockState(),pick))bag.add(item.save(new CompoundTag()));
  pick.setDamageValue(1);var secondLoot=WorldJournal.harvest(l,second,ore.south(),Blocks.IRON_ORE.defaultBlockState(),pick);
  var state=new CompoundTag();state.putUUID("id",receiptAhead?second:third);state.putUUID("toolLoan",first);state.putInt("toolDamage",receiptAhead?1:2);state.putBoolean("quarry",true);state.putString("stage","dig");state.putLong("target",ore.south(receiptAhead?1:2).asLong());state.put("before",NbtUtils.writeBlockState(Blocks.IRON_ORE.defaultBlockState()));
  if(!receiptAhead)for(var item:secondLoot)bag.add(item.save(new CompoundTag()));state.put("bag",bag);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),state);
  npc.hurt(l.damageSources().genericKill(),1000);CargoCustody.tick(l.getServer());var custody=CargoCustody.inspect(l.getServer(),npc.getUUID());var drop=BlockPos.of(custody.getList("drops",Tag.TAG_COMPOUND).getCompound(0).getLong("pos"));var pile=(Container)l.getBlockEntity(drop);
  h.assertTrue(pile!=null&&pile.countItem(Items.RAW_IRON)==2&&pile.countItem(Items.STONE_PICKAXE)==1,"Two harvested blocks and one original loan have one physical owner");
  for(int slot=0;slot<pile.getContainerSize();slot++)if(pile.getItem(slot).is(Items.STONE_PICKAXE))h.assertTrue(pile.getItem(slot).getDamageValue()==2,"Only two confirmed extractions wear the original tool");
  h.assertTrue(chest.countItem(Items.STONE_PICKAXE)==0&&chest.countItem(Items.RAW_IRON)==0&&l.getBlockState(ore.south(2)).is(Blocks.IRON_ORE),"No refund or unmined third ore enters stock");
  h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean("complete"),"Custody retires the entire natural job");
  pile.clearContent();CargoCustody.onDeath(npc);CargoCustody.recover(l.getServer());h.assertTrue(pile.isEmpty(),"Repeated death recovery cannot refill looted cargo");npc.discard();SettlementData.get(l.getServer()).remove(s.id());h.succeed();
 }
}
