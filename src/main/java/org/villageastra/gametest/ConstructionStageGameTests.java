package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-066 (A07-VIS-006): the construction card tells the stages apart and explains the work — parts with progress, the site, materials and crew. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ConstructionStageGameTests {
 private static int first(CompoundTag state,String kind){
  var ops=state.getList("ops",Tag.TAG_COMPOUND);int y=BlockPos.of(state.getLong("origin")).getY();
  for(int i=0;i<ops.size();i++)if(ConstructionViews.kind(ops.getCompound(i),y).equals(kind))return i;
  return -1;
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theCardTellsTheStagesApartAndExplainsTheWork(GameTestHelper h){
  var l=h.getLevel();var site=h.absolutePos(new BlockPos(4,3,4));var center=h.absolutePos(new BlockPos(40,3,30));
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(int x=-1;x<16;x++)for(int z=-1;z<16;z++){
   for(int y=-3;y<0;y++)l.setBlock(site.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(site.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<=20;y++)l.setBlock(site.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  // A hollow under one corner of the floor and tall grass inside: there is foundation and clearing to do.
  for(int y=-3;y<=0;y++)l.setBlock(site.offset(2,y,2),Blocks.AIR.defaultBlockState(),2);l.setBlock(site.offset(2,-4,2),Blocks.STONE.defaultBlockState(),2);
  l.setBlock(site.offset(3,1,3),Blocks.TALL_GRASS.defaultBlockState(),2);
  try{
   var survey=BuildingOrders.survey(l,e,"home",0,site);
   h.assertTrue(survey.ok(),"The site can be built: "+survey.reason()+" "+survey.conflicts().size());
   var state=survey.state();
   var card=ConstructionViews.project(l,e,site,state);
   h.assertTrue(card.getString("stage").equals("ready"),"Before any work the site is ready: "+card.getString("stage"));
   var parts=card.getCompound("parts");
   h.assertTrue(parts.getCompound("clear").getInt("total")>=1&&parts.getCompound("foundation").getInt("total")>=1&&parts.getCompound("place").getInt("total")>0,"The parts of the work are counted: "+parts);
   h.assertTrue(card.getInt("width")==7&&card.getInt("depth")==7&&card.getInt("y")==site.getY()&&card.getInt("minutes")>0,"The site, its floor height and a rough time are shown");
   state.putBoolean("funded",true);
   for(var stage:List.of("clear","foundation","place")){
    int i=first(state,stage);h.assertTrue(i>=0,"The plan has a "+stage+" operation");
    state.putInt("index",i);var now=ConstructionViews.project(l,e,site,state);
    String expected=stage.equals("clear")?"clearing":stage.equals("foundation")?"foundation":"work";
    h.assertTrue(now.getString("stage").equals(expected),"At a "+stage+" operation the stage is "+expected+": "+now.getString("stage"));
    h.assertTrue(now.getCompound("parts").getCompound(stage).getInt("done")==0,"Nothing of that part is done before its first operation");
   }
   // Somebody changes a block the builder is about to take: the site is blocked.
   int i=first(state,"place");state.putInt("index",i);var at=HallConstructionPlan.step(state.getList("ops",Tag.TAG_COMPOUND).getCompound(i)).pos();
   var kept=l.getBlockState(at);l.setBlock(at,Blocks.GLASS.defaultBlockState(),2);
   h.assertTrue(ConstructionViews.project(l,e,site,state).getString("stage").equals("blocked"),"A changed site is blocked");
   l.setBlock(at,kept,2);
   var mayor=UUID.randomUUID();s.appointPlayerMayor(mayor);var g=s.governance();
   h.assertTrue(g.setPaused(mayor,g.epoch(),g.revision(),HallConstructionPlan.read(state).id(),true),"The mayor pauses the work");
   h.assertTrue(ConstructionViews.project(l,e,site,state).getString("stage").equals("pause"),"A paused site says so");
   state.putBoolean("complete",true);
   h.assertTrue(ConstructionViews.project(l,e,site,state).getString("stage").equals("complete"),"A finished site says so");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
