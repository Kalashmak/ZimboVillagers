package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.tags.ItemTags;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkshopToolChainGameTests {
 @GameTest(template="empty",batch="workshop_tool_chain",timeoutTicks=100)
 public static void hallCraftsMissingAxeBeforeStrippingHouseTimber(GameTestHelper h){strip(h,false);}
 @GameTest(template="empty",batch="workshop_tool_existing",timeoutTicks=100)
 public static void existingAxeIsReusedInsteadOfCraftingAnother(GameTestHelper h){strip(h,true);}
 @GameTest(template="empty",batch="workshop_tool_inputs",timeoutTicks=100)
 public static void missingToolPublishesItsCraftableInputsInsteadOfUnharvestableAxes(GameTestHelper h){
  var l=h.getLevel();var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);SettlementData.get(l.getServer()).add(e);
  l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var c=LogisticsRoutes.chest(l,e,hall);
  s.addBuilding(new Settlement.Building(UUID.randomUUID(),"forester",24,0,0));
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.STRIPPED_BIRCH_LOG),2,hall.id()));var needs=Workshops.needs(l,e,Workshops.spec("town_hall"),c,wants);
  h.assertTrue(!needs.isEmpty()&&needs.stream().anyMatch(in->in.matches(new ItemStack(Items.BIRCH_LOG))),"Raw timber is still requested");
  h.assertTrue(needs.stream().noneMatch(in->Arrays.stream(in.ingredient().getItems()).anyMatch(st->st.is(ItemTags.AXES))),"Hall must request craftable tool inputs, not an axe no gatherer can harvest: "+needs.stream().map(in->in.ingredient().toJson()+" x"+in.count()).toList());
  h.assertTrue(c.isEmpty(),"Publishing demand cannot create goods");SettlementData.get(l.getServer()).remove(s.id());h.succeed();
 }
 private static void strip(GameTestHelper h,boolean supplied){
  var l=h.getLevel();var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);SettlementData.get(l.getServer()).add(e);
  l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var c=LogisticsRoutes.chest(l,e,hall);
  c.setItem(0,new ItemStack(Items.BIRCH_LOG,3));c.setItem(1,new ItemStack(Items.BIRCH_PLANKS,3));c.setItem(2,new ItemStack(Items.STICK,2));if(supplied)c.setItem(3,new ItemStack(Items.WOODEN_AXE));
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.STRIPPED_BIRCH_LOG),2,hall.id()));
  h.assertTrue(Workshops.plan(l,e,hall,c,wants)!=null,"House timber request must plan its missing tool or use the supplied axe");
  long now=1000;for(int step=0;step<1600&&c.countItem(Items.STRIPPED_BIRCH_LOG)<2;step++){Workshops.advance(l,e,hall,now,wants);now+=20;}
  // Let the final job return its worn tool before inspecting its exact balance.
  for(int step=0;step<10&&!Workshops.inspect(l,hall.id()).getString("stage").equals("idle");step++){Workshops.advance(l,e,hall,now,wants);now+=20;}
  h.assertTrue(c.countItem(Items.STRIPPED_BIRCH_LOG)==2&&c.countItem(Items.BIRCH_LOG)==1,"Two real logs become exactly two stripped logs");
  int axes=0,damage=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(ItemTags.AXES)){axes+=c.getItem(i).getCount();damage+=c.getItem(i).getDamageValue();}
  h.assertTrue(axes==1&&damage==2,"One axe survives both jobs with exactly two uses: count="+axes+" damage="+damage);
  h.assertTrue(c.countItem(Items.BIRCH_PLANKS)==(supplied?3:0)&&c.countItem(Items.STICK)==(supplied?2:0),"Missing axe is crafted from paid planks and sticks; an existing axe costs no extra materials");
  SettlementData.get(l.getServer()).remove(s.id());h.succeed();
 }
}
