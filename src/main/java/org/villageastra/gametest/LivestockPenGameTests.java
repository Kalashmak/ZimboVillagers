package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-138 (owner 2026-09-22): the livestock yard's pens — 1/2/4 at levels I/II/III, 16 head each, a feeder and a trough in every pen,
 *  gates the keeper opens, grass inside for the sheep's wool, and every level's plan agrees with the pens the code works. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LivestockPenGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void thePensFollowTheYardLevel(GameTestHelper h){
  int[] want={1,2,4,4,4,4};
  for(int level=1;level<=6;level++)h.assertTrue(LivestockPens.pens(level)==want[level-1]&&LivestockPens.built(level).size()==want[level-1],"Pens at level "+level);
  var cells=new HashSet<Long>();
  for(var p:LivestockPens.all()){
   for(int x=p.x()+1;x<p.x()+6;x++)for(int z=p.z()+1;z<p.z()+6;z++)h.assertTrue(cells.add(BlockPos.asLong(x,0,z)),"Pens do not overlap at "+x+","+z);
   h.assertTrue(p.outside().getX()>=7&&p.outside().getX()<=9,"Pen "+p.index()+" opens on the lane (x7..9)");
   h.assertTrue(p.walk().getZ()==8||p.walk().getZ()==17,"Pen "+p.index()+"'s feeder is filled from a walk");}
  // Every level's plan: the pens of that level stand with their feeder, trough and gate; the others are not built yet.
  for(int level=1;level<=6;level++){var plan=BuildingBlueprints.layout(BuildingTiers.layoutId("livestock",level),BlockPos.ZERO);
   for(var p:LivestockPens.all()){boolean built=p.index()<=LivestockPens.pens(level);var feeder=plan.getOrDefault(p.feeder(),Blocks.AIR.defaultBlockState());var gate=plan.getOrDefault(p.gate(),Blocks.AIR.defaultBlockState());
    h.assertTrue(built==(feeder.getBlock() instanceof FeederBlock),"Level "+level+" pen "+p.index()+": feeder "+feeder);
    h.assertTrue(built==(gate.getBlock() instanceof FenceGateBlock),"Level "+level+" pen "+p.index()+": gate "+gate);
    for(var t:p.trough())h.assertTrue(!built||plan.get(t)!=null&&plan.get(t).getFluidState().isSource(),"Level "+level+" pen "+p.index()+": water in the trough");}}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void everyPenKeepsSixteen(GameTestHelper h){
  for(int level=1;level<=6;level++)h.assertTrue(LivestockPens.capacity(level)==LivestockPens.PEN_CAP*LivestockPens.pens(level),"Level "+level+": "+LivestockPens.capacity(level)+" head in "+LivestockPens.pens(level)+" pens");
  h.assertTrue(LivestockPens.PEN_CAP==16&&LivestockGoal.max(1)==LivestockGoal.MAX,"Sixteen a pen (owner: culls above 16)");
  h.succeed();
 }
 /** Spec F10: sheep grow their wool back only by eating grass, so a pen's ground is grass (a trodden cell inside the gate aside). */
 @GameTest(template="empty",timeoutTicks=100) public static void woolGrowsBackInThePen(GameTestHelper h){
  var plan=BuildingBlueprints.layout(BuildingTiers.layoutId("livestock",3),BlockPos.ZERO);
  for(var p:LivestockPens.all()){int grass=0;for(int x=p.x()+1;x<p.x()+6;x++)for(int z=p.z()+1;z<p.z()+6;z++)if(plan.getOrDefault(new BlockPos(x,0,z),Blocks.AIR.defaultBlockState()).is(Blocks.GRASS_BLOCK))grass++;
   h.assertTrue(grass>=22,"Pen "+p.index()+" is grass inside (the sunk trough and a trodden cell aside): "+grass+" of 25");}
  h.succeed();
 }
 /** Spec R4: the keeper opens and closes a dark oak gate (nothing did before AD-138). */
 @GameTest(template="empty",timeoutTicks=100) public static void theKeeperOpensADarkOakGate(GameTestHelper h){
  var l=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));l.setBlock(pos,Blocks.DARK_OAK_FENCE_GATE.defaultBlockState(),2);
  var keeper=VillageAstra.RESIDENT.get().create(l);keeper.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5,0,0);l.addFreshEntity(keeper);
  h.assertTrue(Gates.reaches(keeper,pos)&&Gates.open(l,pos,keeper)&&Gates.isOpen(l.getBlockState(pos)),"Opened");
  h.assertTrue(Gates.close(l,pos,keeper)&&!Gates.isOpen(l.getBlockState(pos)),"Closed");
  h.assertTrue(!Gates.open(l,pos.above(),keeper),"No gate, nothing to open");
  keeper.discard();h.succeed();
 }
 /** The kit of a yard level never stands where any level's plan puts a block of its own (pens, fences, feeders grow into the lot). */
 @GameTest(template="empty",timeoutTicks=100) public static void theKitKeepsClearOfThePens(GameTestHelper h){
  // The finial of level V is set on the ridge's cap slab on purpose (LevelArchitecture.plan), as on every design.
  for(var placed:LevelArchitecture.equipment("livestock"))if(!placed.state().is(Blocks.STONE_BRICK_WALL))for(int level=1;level<=6;level++){var s=VillageStyle.plan(BuildingTiers.layoutId("livestock",level)).get(new VillageStyle.Cell(placed.local().getX(),placed.local().getY(),placed.local().getZ()));
   h.assertTrue(s==null||s.equals("air"),"Level "+level+" plan puts "+s+" on the kit cell "+placed.local()+" of level "+placed.level());}
  h.succeed();
 }
 /** An owned animal without a pen standing inside a pen (a newborn, a herd kept before the pens) is that pen's; a tagged one counts
  *  where its tag says; the siege sees the whole herd. */
 @GameTest(template="empty",timeoutTicks=100) public static void animalsBelongToTheirPen(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  var yard=new Settlement.Building(Settlement.childId(s.id(),"building/livestock"),"livestock",0,0,0,0,3);s.addBuilding(yard);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   var p1=LivestockPens.pen(1);var p2=LivestockPens.pen(2);
   var loose=EntityType.SHEEP.create(l);var in1=LivestockPens.at(e,yard,new BlockPos(3,1,11));loose.moveTo(in1.getX()+.5,in1.getY(),in1.getZ()+.5,0,0);loose.setNoAi(true);loose.getPersistentData().putUUID(LivestockGoal.OWNER,s.id());l.addFreshEntity(loose);
   var cow=EntityType.COW.create(l);cow.moveTo(in1.getX()+1.5,in1.getY(),in1.getZ()+.5,0,0);cow.setNoAi(true);LivestockPens.tag(cow,s.id(),yard,p2);l.addFreshEntity(cow);
   var h1=LivestockPens.herd(l,e,yard,p1);var h2=LivestockPens.herd(l,e,yard,p2);
   h.assertTrue(h1.contains(loose)&&loose.getPersistentData().getInt(LivestockPens.PEN_TAG)==1,"The untagged sheep inside pen 1 is pen 1's");
   h.assertTrue(h2.contains(cow)&&!h1.contains(cow),"The cow tagged for pen 2 counts there, wherever it stands");
   h.assertTrue(LivestockGoal.herd(l,e,yard,Animal.class).size()==2,"The yard's whole herd for a siege");
   loose.discard();cow.discard();
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 /** Spec F13: a pen culls at most CULLS_PER_DAY a village day; the count starts again the next day. */
 @GameTest(template="empty",timeoutTicks=100) public static void theCullRespectsTheDailyCap(GameTestHelper h){
  var yard=new Settlement.Building(UUID.randomUUID(),"livestock",0,0,0);var p=LivestockPens.pen(1);long day=24000L*7;
  for(int i=0;i<LivestockPens.CULLS_PER_DAY;i++){h.assertTrue(LivestockGoal.cullsLeft(yard,p,day+i)==LivestockPens.CULLS_PER_DAY-i,"Left "+i);LivestockGoal.culled(yard,p,day+i);}
  h.assertTrue(LivestockGoal.cullsLeft(yard,p,day+100)==0&&LivestockGoal.cullsLeft(yard,p,day+24000)==LivestockPens.CULLS_PER_DAY,"None left today, all again tomorrow");
  h.succeed();
 }
}
