package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class AutonomyPlanningGameTests {
 private static SettlementData.Entry town(GameTestHelper h){
  var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,h.getLevel().dimension().location().toString(),h.absolutePos(new BlockPos(3,3,3)));SettlementData.get(h.getLevel().getServer()).add(e);
  h.getLevel().setBlock(LogisticsRoutes.position(e,Workshops.hall(e)),VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);LogisticsRoutes.chest(h.getLevel(),e,Workshops.hall(e)).clearContent();return e;
 }
 @GameTest(template="empty",batch="autonomy_planning",timeoutTicks=200) public static void npcResearchRequestsItsMissingInputs(GameTestHelper h){
  var e=town(h);var l=h.getLevel();try{
   BookResearch.autoSelect(l,e);var t=BookResearch.inspect(l,e);
   h.assertTrue(!t.getList("resourceOrders",Tag.TAG_STRING).isEmpty()&&!BookResearch.wants(l,e).isEmpty(),"NPC research publishes real input demand even when its stock starts empty");
   h.assertTrue(BookResearch.completed(e,t).isEmpty(),"An order is not free research");
   BookResearch.autoSelect(l,e);
   var after=BookResearch.inspect(l,e).getList("resourceOrders",Tag.TAG_STRING);
   h.assertTrue(new HashSet<>(after.stream().map(Tag::getAsString).toList()).size()==after.size()&&after.size()<=ScienceBalance.RESOURCE_ORDERS,"Repeated planning keeps a bounded distinct queue");
  }finally{SettlementData.get(l.getServer()).remove(e.settlement().id());}h.succeed();
 }
 @GameTest(template="empty",batch="autonomy_planning",timeoutTicks=200) public static void starterBedsUseTheExistingPaidStrawRecipe(GameTestHelper h){
  var e=town(h);var l=h.getLevel();var s=e.settlement();try{
   h.assertTrue(Workshops.makeable(l,e,"minecraft:white_bed"),"Starting farm and hall can supply bedding without a sheep yard");
   h.assertTrue("home".equals(MayorPlanner.need(s)),"The initial housing need remains the first house");
   // This small recipe fixture registers a farm but lays no physical field.
   // World-aware planning must still solve that food shortage before more housing.
   h.assertTrue(MayorPlanner.foodShortage(l,e)&&"farm".equals(MayorPlanner.wanted(l,e)),"An unlaid starter field is a real food shortage, not permission to skip food for housing");
   var chest=LogisticsRoutes.chest(l,e,Workshops.hall(e));var wants=List.of(new Workshops.Want(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.WHITE_BED),1,Workshops.hall(e).id()));
   h.assertTrue(Workshops.plan(l,Workshops.spec("town_hall"),chest,wants)==null,"An empty hall cannot create a free bed");
   chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT,12));chest.setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS,6));
   var job=Workshops.plan(l,Workshops.spec("town_hall"),chest,wants);
   h.assertTrue(job!=null&&job.recipe().equals("custom:straw_bedding")&&job.outputs().stream().anyMatch(out->out.is(net.minecraft.world.item.Items.WHITE_BED)),"Real wheat and planks choose the straw bedding recipe without wool");
   chest.setItem(2,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI,7));
   var withDye=Workshops.plan(l,Workshops.spec("town_hall"),chest,wants);
   h.assertTrue(withDye!=null&&withDye.recipe().equals("custom:straw_bedding"),"A fully supplied bed must precede dye intermediates for unavailable wool: "+(withDye==null?"none":withDye.recipe()));
  }finally{SettlementData.get(l.getServer()).remove(s.id());ResearchKnobs.forget(s.id());}h.succeed();
 }
 @GameTest(template="empty",batch="autonomy_planning",timeoutTicks=200) public static void npcMayorEventuallyPlansScienceAndServices(GameTestHelper h){
  var e=town(h);var l=h.getLevel();var s=e.settlement();try{
   var home=new Settlement.Home(UUID.randomUUID(),1,20,true);s.addHome(home);s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1),home.id());
   var t=BookResearch.inspect(l,e);var known=new ListTag();ResearchCatalog.NODES.keySet().forEach(id->known.add(StringTag.valueOf(id)));t.put("legacyDone",known);BookResearch.store(l,e,t);
   // Existing basic workshops must not make the planner run out of goals before it builds a laboratory.
   for(var type:List.of("school","mill","restaurant","carpentry","masonry","warehouse","smithy"))s.addBuilding(new Settlement.Building(UUID.randomUUID(),type,60,0,60));
   var seen=new HashSet<String>();for(int step=0;step<20;step++){var next=MayorPlanner.wanted(l,e);if(next==null)break;h.assertTrue(seen.add(next),"Planner must not order a duplicate workplace: "+next);s.addBuilding(new Settlement.Building(UUID.randomUUID(),next,80+step*20,0,80));}
   h.assertTrue(seen.containsAll(List.of("laboratory","livestock","clinic","cartographer","engineering","caravan","expedition")),"NPC plans the full civil development chain; got "+seen);
  }finally{SettlementData.get(l.getServer()).remove(s.id());ResearchKnobs.forget(s.id());}h.succeed();
 }
}
