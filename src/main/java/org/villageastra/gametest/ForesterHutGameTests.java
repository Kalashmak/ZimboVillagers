package org.villageastra.gametest;
import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.OrganicLots;
import org.villageastra.world.*;
/** AD-131 §2: the forester's hut I..VI — six plans on one 15x21 lot, the door, the stock chest and the core in the same cells, every level's
 *  stations free in every plan from that level on, the built area and the cost growing with the level, the courtyard of VI open to the sky over
 *  its six grove cells, and no deepslate before IV. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ForesterHutGameTests {
 private static Map<BlockPos,BlockState> plan(int level){return BuildingBlueprints.layout(BuildingTiers.layoutId(ForesterHut.TYPE,level),BlockPos.ZERO);}
 private static BlockState at(Map<BlockPos,BlockState> m,BlockPos p){return m.getOrDefault(p,Blocks.AIR.defaultBlockState());}
 @GameTest(template="empty",batch="forester_hut") public static void everyPlanKeepsItsLotAndCells(GameTestHelper h){
  var d=BuildingBlueprints.design(ForesterHut.TYPE);
  h.assertTrue(d.width()==15&&d.depth()==21&&OrganicLots.width(ForesterHut.TYPE,OrganicLots.BARN_LOTS)==15&&OrganicLots.depth(ForesterHut.TYPE,OrganicLots.BARN_LOTS)==21,"The hut's lot is 15x21 from layout 6");
  for(int level=1;level<=6;level++){var m=plan(level);
   for(var p:m.keySet())h.assertTrue(p.getX()>=0&&p.getX()<15&&p.getZ()>=0&&p.getZ()<21,"Level "+level+" stays in its lot: "+p);
   h.assertTrue(at(m,ForesterHut.DOOR).getBlock() instanceof DoorBlock&&at(m,ForesterHut.DOOR.above()).getBlock() instanceof DoorBlock,"Level "+level+": the door at (4,1,0)");
   h.assertTrue(at(m,ForesterHut.CHEST).is(VillageAstra.OWNED_CHEST.get()),"Level "+level+": the stock chest at (1,1,4)");
   if(level>=2){var core=at(m,ForesterHut.CORE);h.assertTrue(core.getBlock() instanceof BuildingCoreBlock&&core.getValue(BuildingCoreBlock.GRADE)==level,"Level "+level+": the core of grade "+level+" at (6,1,4): "+core);}
   for(var k:ForesterHut.kits())if(k.level()<=level)h.assertTrue(at(m,k.local()).getBlock()==k.state().getBlock(),"Level "+level+": the station of level "+k.level()+" at "+k.local()+" stands: "+at(m,k.local()));
  }
  h.succeed();
 }
 @GameTest(template="empty",batch="forester_hut") public static void theHutGrowsAndCostsMoreWithEveryLevel(GameTestHelper h){
  int last=-1;long worth=-1;
  for(int level=1;level<=6;level++){var m=plan(level);var built=new HashSet<Long>();for(var e:m.entrySet())if(e.getKey().getY()>=1&&!e.getValue().isAir())built.add(((long)e.getKey().getX()<<32)|e.getKey().getZ());
   h.assertTrue(built.size()>last,"Level "+level+" covers more ground than the one before: "+built.size()+" > "+last);last=built.size();
   if(level>=2){long w=BuildingTiers.worth(BuildingTiers.cost(ForesterHut.TYPE,level));h.assertTrue(w>worth,"Level "+level+" costs more: "+w+" > "+worth);worth=w;}}
  h.succeed();
 }
 @GameTest(template="empty",batch="forester_hut") public static void theCourtyardOfSixIsOpenToTheSky(GameTestHelper h){
  var m=plan(6);
  for(var p:m.keySet())if(p.getY()>=1&&ForesterHut.courtyard(p))h.assertTrue(false,"Nothing of the plan stands in the courtyard above its ground, not even air: "+p+" "+m.get(p));
  for(var g:ForesterHut.grove()){h.assertTrue(ForesterHut.courtyard(g),"Grove cell "+g+" lies in the courtyard");h.assertTrue(at(m,g.below()).is(Blocks.DIRT),"Grove cell "+g+" stands on dirt");}
  for(var e:m.entrySet()){var p=e.getKey();boolean ring=p.getZ()>=10&&(p.getX()<=1||p.getX()>=13||p.getZ()>=19);if(ring&&!e.getValue().isAir())h.assertTrue(p.getY()<=6,"The courtyard's ring stays under y6 so the grove's crowns read over it: "+p);}
  h.succeed();
 }
 @GameTest(template="empty",batch="forester_hut") public static void theKitsAreTheLevelsBalance(GameTestHelper h){
  try(var s=ForesterHutGameTests.class.getResourceAsStream("/data/villageastra/balance/levels.json")){var kits=JsonParser.parseReader(new java.io.InputStreamReader(s,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("kits").getAsJsonObject("forester");
   for(int level=2;level<=6;level++){var want=new ArrayList<String>();for(var raw:kits.getAsJsonArray(String.valueOf(level)))want.add(raw.getAsString());Collections.sort(want);
    h.assertTrue(want.equals(ForesterHut.kitItems(level)),"Level "+level+": levels.json "+want+" is the hut's kit "+ForesterHut.kitItems(level));}
  }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  h.succeed();
 }
 @GameTest(template="empty",batch="forester_hut") public static void noDeepslateBeforeFour(GameTestHelper h){
  for(int level=1;level<=3;level++)for(var e:plan(level).entrySet()){var id=BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).getPath();h.assertTrue(!id.contains("deepslate"),"Level "+level+" takes only what the village has by then: "+id+" at "+e.getKey());}
  boolean slate=plan(4).values().stream().anyMatch(s->BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("deepslate"));
  h.assertTrue(slate,"From IV the roof is slate");
  h.succeed();
 }
}
