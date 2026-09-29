package org.villageastra.gametest;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-136 (spec 4.2, CF2, R2): Engineering I and II teach the town hall its crafting lists - the first buildings' wood, cobblestone and
 *  torches with I (and the paper and sugar the cartographer and the clinic need without a mill), smelted stone, glass and bricks with II.
 *  A learned item is made at the builder's pace; anything else still at the slow pace of the fallback craft (AD-114), so that early
 *  building never stops while the mill, stoneworks and carpentry wait for their side nodes. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class EngineeringCraftGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void theHallLearnsItsCraftsFromEngineeringOneAndTwo(GameTestHelper h){
  var t=town(h,null);
  try{h.assertTrue(!Workshops.hallCrafts("engineering.1").isEmpty()&&!Workshops.hallCrafts("engineering.2").isEmpty(),"Two lists: "+Workshops.hallCrafts("engineering.1")+" / "+Workshops.hallCrafts("engineering.2"));
   h.assertTrue(Workshops.hallCrafts("engineering.4").isEmpty()&&Workshops.hallCrafts("research.2").isEmpty(),"No other node teaches the hall (III, the redstone mechanisms: EngineeringLadderGameTests)");
   h.assertTrue(Workshops.HALL_FAST<Workshops.HALL_SLOW,"The learned pace is faster than the fallback: "+Workshops.HALL_FAST+" < "+Workshops.HALL_SLOW);
   var fence=new ItemStack(Items.OAK_FENCE);var paper=new ItemStack(Items.PAPER);var sugar=new ItemStack(Items.SUGAR);var glass=new ItemStack(Items.GLASS);var bricks=new ItemStack(Items.STONE_BRICKS);
   h.assertTrue(!Workshops.hallFast(t.l,t.e,fence)&&!Workshops.hallFast(t.l,t.e,paper)&&!Workshops.hallFast(t.l,t.e,glass),"Without Engineering the hall makes everything at the fallback pace");
   learn(t,"engineering.1");
   h.assertTrue(Workshops.hallFast(t.l,t.e,fence)&&Workshops.hallFast(t.l,t.e,paper)&&Workshops.hallFast(t.l,t.e,sugar),"Engineering I: fences, paper and sugar at the builder's pace");
   h.assertTrue(!Workshops.hallFast(t.l,t.e,glass)&&!Workshops.hallFast(t.l,t.e,bricks),"Glass and stone bricks wait for Engineering II");
   learn(t,"engineering.2");
   h.assertTrue(Workshops.hallFast(t.l,t.e,glass)&&Workshops.hallFast(t.l,t.e,bricks),"Engineering II: glass and stone bricks too");
   h.assertTrue(!Workshops.hallFast(t.l,t.e,new ItemStack(Items.LANTERN)),"Lanterns are metallurgy's, not the hall's");
  }finally{done(t);}
  h.succeed();
 }
}
