package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.WarehouseFixture.*;
/** AD-147 §4 (CF-N): the store of VI sorts itself by category, page by page, with no courier and no wolf; the sum of its items never
 *  changes, a quest relic is never moved, a warehouse below VI does not sort, and a delivery to a sorting store goes to its category. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WarehouseSortGameTests {
 static final List<ItemStack> MIX=List.of(new ItemStack(Items.COBBLESTONE,64),new ItemStack(Items.OAK_LOG,30),new ItemStack(Items.BREAD,12),new ItemStack(Items.IRON_INGOT,9),
  new ItemStack(Items.IRON_PICKAXE),new ItemStack(Items.WHITE_WOOL,16),new ItemStack(Items.PAPER,20),new ItemStack(Items.COBBLESTONE,20),new ItemStack(Items.WHEAT,40),new ItemStack(Items.COAL,33),new ItemStack(Items.OAK_PLANKS,17),new ItemStack(Items.BREAD,7));
 static Map<String,Integer> sums(net.minecraft.world.Container c){var m=new TreeMap<String,Integer>();for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(!s.isEmpty())m.merge(s.getItem().toString(),s.getCount(),Integer::sum);}return m;}
 /** VI: every stack reaches the page of its category in a few turns; merged part stacks; the same items; the relic where it lay. */
 @GameTest(template="empty",timeoutTicks=400) public static void theStoreOfSixSortsItself(GameTestHelper h){
  var v=village(h,6);research(v,6);
  try{var c=v.stock();h.assertTrue(c.getContainerSize()==648&&WarehouseSort.sorting(v.l(),v.e(),v.kept()),"VI sorts: "+c.getContainerSize());
   // Out of place on purpose: the intake and a wrong page.
   int slot=0;for(var s:MIX)c.setItem(slot++,s.copy());c.setItem(9*54+5,new ItemStack(Items.STONE_BRICKS,11));
   var relic=new ItemStack(VillageAstra.RESEARCH_VOLUME.get());var tag=new CompoundTag();tag.putBoolean(QuestSites.RELIC,true);relic.setTag(tag);c.setItem(3*54+7,relic);
   var before=sums(c);
   for(int turn=0;turn<6&&WarehouseSort.unsorted(c)>0;turn++)WarehouseSort.step(v.l(),v.e(),v.kept(),v.l().getGameTime()*100+turn);
   h.assertTrue(sums(c).equals(before),"The same items: "+sums(c)+" vs "+before);
   h.assertTrue(WarehouseSort.unsorted(c)==0,"Everything but the relic is on its page: "+WarehouseSort.unsorted(c));
   h.assertTrue(c.getItem(3*54+7).getTag()!=null&&c.getItem(3*54+7).getTag().contains(QuestSites.RELIC),"The relic was not moved");
   int bread=0;for(int p:WarehouseSort.pages(WarehouseSort.FOOD))for(int i=0;i<54;i++)if(c.getItem(p*54+i).is(Items.BREAD)){bread++;h.assertTrue(c.getItem(p*54+i).getCount()==19,"The two bread stacks merged");}
   h.assertTrue(bread==1,"One bread stack on the food pages: "+bread);
   // A delivery goes straight to its category.
   int at=WarehouseSort.depositSlot(c,new ItemStack(Items.COBBLESTONE,5));
   h.assertTrue(at>=0&&WarehouseSort.categoryOfPage(at/54)==WarehouseSort.STONE,"Cobble goes onto the stone pages: "+at);
  }finally{done(v);}
  h.succeed();
 }
 /** Below VI the store keeps its order: nothing moves. */
 @GameTest(template="empty",timeoutTicks=200) public static void belowSixNothingSorts(GameTestHelper h){
  var v=village(h,5);research(v,5);
  try{var c=v.stock();int slot=0;for(var s:MIX)c.setItem(slot++,s.copy());var before=new ArrayList<ItemStack>();for(int i=0;i<c.getContainerSize();i++)before.add(c.getItem(i).copy());
   h.assertTrue(!WarehouseSort.sorting(v.l(),v.e(),v.kept())&&WarehouseSort.step(v.l(),v.e(),v.kept(),1)==0,"V does not sort");
   for(int i=0;i<c.getContainerSize();i++)h.assertTrue(ItemStack.matches(before.get(i),c.getItem(i)),"Slot "+i+" unchanged");
  }finally{done(v);}
  h.succeed();
 }
}
