package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-038: surfaces speed up every living user and wear with real traffic; the builder paves and repairs with real hall materials exactly once; lamps are placed where the road is dark. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RoadGameTests {
 private record Town(net.minecraft.server.level.ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building hall,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  for(int x=-4;x<36;x++)for(int z=-4;z<16;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Town(l,s,e,hall,center);
 }
 private static Map<BlockPos,BlockState> strip(Town t,int from,int length){var out=new LinkedHashMap<BlockPos,BlockState>();for(int x=from;x<from+length;x++)out.put(t.center.offset(x,0,13),Blocks.DIRT_PATH.defaultBlockState());return out;}
 @GameTest(template="empty",timeoutTicks=100) public static void surfaceSpeedsUpAnyLivingUserAndFadesWithWear(GameTestHelper h){
  var t=town(h);var pos=t.center.offset(6,0,10);t.l.setBlock(pos,Blocks.COBBLESTONE.defaultBlockState(),3);h.assertTrue(Roads.register(t.l,t.s.id(),pos)&&Roads.cell(t.l,pos).tier==2,"Cobble cell registered as tier 2");
  h.assertTrue(!Roads.register(t.l,t.s.id(),t.center.offset(7,-1,10)),"Plain stone is not a road");
  var zombie=EntityType.ZOMBIE.create(t.l);zombie.setNoAi(true);zombie.moveTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,0,0);zombie.setOnGround(true);t.l.addFreshEntity(zombie);
  double base=zombie.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);Roads.living(zombie);
  // AD-123: cobblestone gives ×1.5 now, but a hostile mob takes at most the pre-rework best bonus (roads.json hostile_bonus_cap 0.35).
  h.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED)-base*(1+Math.min(Roads.TIERS.get(2).bonus(),Roads.HOSTILE_CAP)))<1e-6&&Math.abs(Roads.HOSTILE_CAP-0.35)<1e-9,"Hostile on cobble moves at most 35% faster: "+zombie.getAttributeValue(Attributes.MOVEMENT_SPEED)/base);
  Roads.cell(t.l,pos).wear=100;Roads.living(zombie);h.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED)-base*1.25)<1e-6,"Worn surface keeps half the bonus (cobble ×1.25)");
  zombie.moveTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+4.5,0,0);Roads.living(zombie);h.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED)-base)<1e-9,"Off the road the bonus is removed");
  zombie.discard();h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void builderPavesWithRealMaterialsExactlyOnce(GameTestHelper h){
  var t=town(h);var cells=strip(t,4,3);var line=List.copyOf(cells.keySet());
  var project=Roads.plan(t.l,t.e,line,cells,2,false);h.assertTrue(project.getList("ops",10).size()==3&&project.getCompound("cost").getInt("minecraft:cobblestone")==3&&t.l.getBlockState(line.get(0)).is(Blocks.GRASS_BLOCK),"Plan is an estimate only");
  h.assertTrue(Roads.order(t.l,t.e,project)&&!Roads.order(t.l,t.e,Roads.plan(t.l,t.e,line,cells,1,false)),"One active road project");
  h.assertTrue(Roads.wants(t.l,t.e,t.hall).stream().anyMatch(w->w.matches(new ItemStack(Items.COBBLESTONE))&&w.count()==3),"Missing paving becomes settlement demand");
  h.assertTrue(Roads.apply(t.l,t.e,project).equals("missing")&&t.l.getBlockState(line.get(0)).is(Blocks.GRASS_BLOCK),"Nothing is paved without carried material");
  var chest=LogisticsRoutes.chest(t.l,t.e,t.hall);chest.setItem(0,new ItemStack(Items.COBBLESTONE,2));
  h.assertTrue(Roads.load(t.l,t.e,t.hall,project)==2&&chest.countItem(Items.COBBLESTONE)==0,"Builder carries the two real cobblestones");
  var stale=project.copy();
  h.assertTrue(Roads.apply(t.l,t.e,project).equals("done")&&t.l.getBlockState(line.get(0)).is(Blocks.COBBLESTONE)&&Roads.cell(t.l,line.get(0)).tier==2,"First cell paved and registered");
  h.assertTrue(Roads.apply(t.l,t.e,stale).equals("done")&&stale.getList("cargo",10).equals(project.getList("cargo",10))&&stale.getList("returns",10).equals(project.getList("returns",10)),"Replaying the uncommitted record neither places nor spends twice");
  h.assertTrue(Roads.apply(t.l,t.e,project).equals("done")&&Roads.apply(t.l,t.e,project).equals("missing"),"Third cell waits for material");
  chest.setItem(0,new ItemStack(Items.COBBLESTONE,1));Roads.load(t.l,t.e,t.hall,project);
  h.assertTrue(chest.countItem(Items.DIRT)==2,"Removed soil of two cells is returned to the hall: "+chest.countItem(Items.DIRT));
  h.assertTrue(Roads.apply(t.l,t.e,project).equals("done")&&Roads.apply(t.l,t.e,project).equals("unload"),"Last cell paved, soil still carried");
  Roads.load(t.l,t.e,t.hall,project);h.assertTrue(Roads.apply(t.l,t.e,project).equals("complete")&&chest.countItem(Items.DIRT)==3&&!Roads.active(t.l,t.s.id()),"Project completes after unloading");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void trafficWearsCheapSurfacesFasterAndRepairSpendsMaterial(GameTestHelper h){
  var t=town(h);var dirt=t.center.offset(3,0,12);var paved=t.center.offset(5,0,12);t.l.setBlock(dirt,Blocks.DIRT_PATH.defaultBlockState(),3);t.l.setBlock(paved,Blocks.STONE_BRICKS.defaultBlockState(),3);
  Roads.register(t.l,t.s.id(),dirt);Roads.register(t.l,t.s.id(),paved);Roads.traffic(Roads.cell(t.l,dirt),600);Roads.traffic(Roads.cell(t.l,paved),600);
  h.assertTrue(Roads.cell(t.l,dirt).wear==10&&Roads.cell(t.l,paved).wear==1,"Same traffic wears dirt 10, paving 1: "+Roads.cell(t.l,dirt).wear+"/"+Roads.cell(t.l,paved).wear);
  h.assertTrue(Roads.maintenance(t.l,t.e)==null,"No repair below the threshold");
  Roads.cell(t.l,dirt).wear=Roads.REPAIR_WEAR+5;var repair=Roads.maintenance(t.l,t.e);h.assertTrue(repair!=null&&repair.getList("ops",10).size()==1,"Worn cell gets a repair round");
  Roads.order(t.l,t.e,repair);h.assertTrue(Roads.apply(t.l,t.e,repair).equals("missing")&&Roads.cell(t.l,dirt).wear>=Roads.REPAIR_WEAR,"No repair without material");
  var chest=LogisticsRoutes.chest(t.l,t.e,t.hall);chest.setItem(0,new ItemStack(Items.DIRT,1));Roads.load(t.l,t.e,t.hall,repair);
  h.assertTrue(Roads.apply(t.l,t.e,repair).equals("done")&&Roads.cell(t.l,dirt).wear==0&&t.l.getBlockState(dirt).is(Blocks.DIRT_PATH)&&chest.countItem(Items.DIRT)==0,"Repair spends one dirt, resets wear and keeps the block");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void lampsArePlannedBesideDarkStretchesAndLightIsMeasured(GameTestHelper h){
  var t=town(h);var cells=strip(t,12,17);var line=List.copyOf(cells.keySet());
  var project=Roads.plan(t.l,t.e,line,cells,0,true);var ops=project.getList("ops",10);long posts=ops.stream().filter(o->((net.minecraft.nbt.CompoundTag)o).getString("kind").equals("post")).count(),lamps=ops.stream().filter(o->((net.minecraft.nbt.CompoundTag)o).getString("kind").equals("lamp")).count();
  h.assertTrue(posts==3&&lamps==3&&project.getCompound("cost").getInt("minecraft:lantern")==3&&project.getCompound("cost").getInt("minecraft:oak_fence")==3,"Lamp posts every 8 cells: "+posts+"/"+lamps+" skipped="+project.getList("lampSkips",8));
  for(var o:ops){var op=(net.minecraft.nbt.CompoundTag)o;if(op.getString("kind").equals("post")||op.getString("kind").equals("surface"))continue;var lamp=BlockPos.of(op.getLong("pos"));t.l.setBlock(lamp.below(),Blocks.OAK_FENCE.defaultBlockState(),3);t.l.setBlock(lamp,Blocks.LANTERN.defaultBlockState(),3);break;}
  for(var cell:cells.keySet()){t.l.setBlock(cell,Blocks.DIRT_PATH.defaultBlockState(),3);Roads.register(t.l,t.s.id(),cell);}
  // The light engine applies updates during the level tick, so the measurement waits for it.
  h.runAfterDelay(20,()->{
   h.assertTrue(Roads.lit(t.l,line.get(0))&&!Roads.lit(t.l,line.get(16)),"Block light near the first lamp, darkness far away");
   int[] light=Roads.light(t.l,t.s.id());h.assertTrue(light[0]>0&&light[1]>0,"Report counts lit and dark cells: "+light[0]+"/"+light[1]);
   var relit=Roads.plan(t.l,t.e,line,cells,0,true);h.assertTrue(relit.getList("ops",10).stream().filter(o->((net.minecraft.nbt.CompoundTag)o).getString("kind").equals("lamp")).count()==2,"An already lit stretch gets no new lamp");
   h.succeed();});
 }
 /** AD-042: a fenced corridor is planned with posts on both sides, gates for passage and a lit guard post; broken fences come back through maintenance. */
 @GameTest(template="empty",timeoutTicks=200) public static void fencedCorridorBuildsPostsGatesAndIsRepaired(GameTestHelper h){
  var t=town(h);var cells=strip(t,2,33);var line=List.copyOf(cells.keySet());
  var project=Roads.plan(t.l,t.e,line,cells,0,false,true);var ops=project.getList("ops",10);
  long gates=ops.stream().filter(o->((net.minecraft.nbt.CompoundTag)o).getString("kind").equals("gate")).count();
  long fences=ops.stream().filter(o->((net.minecraft.nbt.CompoundTag)o).getString("kind").equals("fence")).count();
  long posts=ops.stream().filter(o->((net.minecraft.nbt.CompoundTag)o).getString("kind").equals("post")).count();
  h.assertTrue(gates==6&&fences==60&&posts==1,"Both sides fenced with gates and one guard post: fences="+fences+" gates="+gates+" posts="+posts);
  h.assertTrue(Roads.order(t.l,t.e,project),"Ordered");
  var chest=LogisticsRoutes.chest(t.l,t.e,t.hall);
  chest.setItem(0,new ItemStack(Items.OAK_FENCE,64));chest.setItem(1,new ItemStack(Items.OAK_FENCE_GATE,8));chest.setItem(2,new ItemStack(Items.LANTERN,2));chest.setItem(3,new ItemStack(Items.DIRT,64));
  for(int i=0;i<400&&!project.getBoolean("complete");i++){
   var result=Roads.apply(t.l,t.e,project);
   if(result.equals("missing")||result.equals("unload"))if(Roads.load(t.l,t.e,t.hall,project)==0&&result.equals("missing"))break;
  }
  h.assertTrue(project.getBoolean("complete"),"The corridor is finished: index="+project.getInt("index")+"/"+ops.size());
  var built=Roads.fences(t.l.getServer(),t.s.id());var guardPosts=Roads.posts(t.l.getServer(),t.s.id());
  h.assertTrue(built.length==66&&guardPosts.length==1,"Every fence, gate and post is registered: "+built.length+"/"+guardPosts.length);
  var sample=BlockPos.of(built[0]);h.assertTrue(t.l.getBlockState(sample).is(Blocks.OAK_FENCE)||t.l.getBlockState(sample).is(Blocks.OAK_FENCE_GATE),"The fence really stands at "+sample.toShortString());
  h.assertTrue(t.l.getBlockState(BlockPos.of(guardPosts[0])).is(Blocks.LANTERN),"The guard post is lit");
  t.l.setBlock(sample,Blocks.AIR.defaultBlockState(),3);
  var repair=Roads.maintenance(t.l,t.e);
  h.assertTrue(repair!=null&&repair.getList("ops",10).stream().anyMatch(o->((net.minecraft.nbt.CompoundTag)o).getString("kind").equals("fence")),"A broken fence returns as maintenance work");
  h.succeed();
 }
}
