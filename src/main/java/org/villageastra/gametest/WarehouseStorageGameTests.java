package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.WarehouseFixture.*;
/** AD-147 §3.3 (CF-B): the warehouse's store is real chests - the stock chest (1,1,4) is the master of 54 slots a page of its level
 *  (108 .. 648, core effect "storage"), every page a double chest of the plan that opens on its part; growing keeps every item, an old
 *  hall store's NBT still reads as 108, and breaking a page drops what it shows once. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WarehouseStorageGameTests {
 /** The store's slots follow the level: 108/162/324/432/486/648 (the core's "storage"), every page opens, an item stays in its slot. */
 @GameTest(template="empty",timeoutTicks=400) public static void storageFollowsTheLevel(GameTestHelper h){
  var v=village(h,1);
  try{v.stock().setItem(0,new ItemStack(Items.COBBLESTONE,33));
   for(int level=1;level<=6;level++){var b=raise(v,level);
    h.assertTrue(BuildingLevels.level(v.l(),v.e(),b)==level,"The plan of level "+level+" stands: "+BuildingLevels.level(v.l(),v.e(),b));
    int slots=v.stock().getContainerSize();
    h.assertTrue(slots==WarehouseStore.slots(level)&&slots==CoreEffects.value("warehouse","storage",level),"Level "+level+": "+slots+" slots, the core says "+CoreEffects.value("warehouse","storage",level));
    h.assertTrue(v.stock().getItem(0).is(Items.COBBLESTONE)&&v.stock().getItem(0).getCount()==33,"Growing keeps the items in their slots at "+level);
    var cells=WarehouseStore.chests(level);
    for(int i=0;i<cells.size();i++){var pos=WarehouseStore.at(v.e(),b,cells.get(i));
     h.assertTrue(i==0||HallStorage.partAt(v.l(),pos),"Level "+level+": chest "+i+" at "+cells.get(i)+" is a page of the store");
     h.assertTrue(i==0||HallStorage.menu(v.l(),pos)!=null,"Level "+level+": the page of chest "+i+" opens");}
   }
   // A page shows the master's slots: an item put through a page's part is in the master.
   var part=(OwnedChestEntity)v.l().getBlockEntity(WarehouseStore.at(v.e(),v.kept(),WarehouseStore.chests(6).get(23)));
   part.setItem(3,new ItemStack(Items.OAK_LOG,5));
   h.assertTrue(v.stock().getItem(23*27+3).is(Items.OAK_LOG),"The last page's part shows the master's last slots");
  }finally{done(v);}
  h.succeed();
 }
 /** An old save names the hall's store only by AstraHallStorage: it still reads as 108 slots, and saves its size from then on. */
 @GameTest(template="empty",timeoutTicks=100) public static void anOldHallStoreStillReads(GameTestHelper h){
  var pos=h.absolutePos(new BlockPos(1,2,1));h.getLevel().setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var chest=(OwnedChestEntity)h.getLevel().getBlockEntity(pos);var tag=new CompoundTag();tag.putBoolean("AstraHallStorage",true);
  chest.load(tag);h.assertTrue(chest.getContainerSize()==108,"AstraHallStorage reads as 108: "+chest.getContainerSize());
  var saved=chest.saveWithoutMetadata();h.assertTrue(saved.getInt("AstraStoreSlots")==108,"The size is saved: "+saved);
  chest.expand(54);h.assertTrue(chest.getContainerSize()==108,"A store never shrinks");
  h.succeed();
 }
 /** Breaking a page drops the stacks it shows once, and the master no longer holds them. */
 @GameTest(template="empty",timeoutTicks=200) public static void breakingAPageDoesNotDuplicate(GameTestHelper h){
  var v=village(h,2);
  try{var b=v.kept();for(int i=0;i<6;i++)v.stock().setItem(108+i,new ItemStack(Items.IRON_INGOT,10));
   int before=count(v.stock(),Items.IRON_INGOT);
   var page=WarehouseStore.at(v.e(),b,WarehouseStore.chests(2).get(4));
   h.assertTrue(HallStorage.partAt(v.l(),page),"Chest 4 is the first half of the third page");
   v.l().destroyBlock(page,true);
   int dropped=0;for(var it:v.l().getEntitiesOfClass(ItemEntity.class,new AABB(page).inflate(3)))if(it.getItem().is(Items.IRON_INGOT)){dropped+=it.getItem().getCount();it.discard();}
   int after=count(v.stock(),Items.IRON_INGOT);
   h.assertTrue(dropped+after==before&&dropped==60,"Nothing doubled: dropped "+dropped+", kept "+after+" of "+before);
  }finally{done(v);}
  h.succeed();
 }
}
