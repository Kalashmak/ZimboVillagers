package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ForestRenewalGameTests {
 @GameTest(template="empty",batch="forest_renewal_budget",timeoutTicks=1800)
 public static void renewalCapsNativeQueriesAndKeepsUnfinishedCandidate(GameTestHelper h){var f=ForestFixture.create(h,1,192);
  try{
   var cells=new ArrayList<net.minecraft.core.BlockPos>();int radius=ForestBalance.radius(1);
   for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)if(x*x+z*z<=radius*radius)cells.add(new net.minecraft.core.BlockPos(x,0,z));
   cells.sort(Comparator.comparingDouble(p->p.distSqr(net.minecraft.core.BlockPos.ZERO)));
   net.minecraft.core.BlockPos foot=null;
   for(int x=-4;x<=20&&foot==null;x++)for(int z=3;z<=6&&foot==null;z++){var p=f.wood(x,z);if(cells.contains(p.subtract(f.door()))&&ForestRenewal.safe(f.l,p))foot=p;}
   h.assertTrue(foot!=null,"The candidate is ordinary unprotected fertile soil");
   var t=new CompoundTag();int first=cells.indexOf(foot.subtract(f.door()));t.putInt("renewalScan",first);
   var cage=f.wood(10,0);f.forester.moveTo(cage.getX()+.5,cage.getY(),cage.getZ()+.5,0,0);f.forester.setOnGround(true);
   for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<=2;y++)if(x!=0||z!=0||y==2)f.l.setBlock(cage.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
   long before=HarvestRouteCache.stats(f.forester).plans();
   try(var survey=HarvestRouteCache.survey(f.forester)){ForestRenewal.plan(f.l,f.e,f.hut(),f.forester,t,Set.of(Items.BIRCH_SAPLING));}
   long plans=HarvestRouteCache.stats(f.forester).plans()-before;
   h.assertTrue(plans<=4,"A renewal turn must cap native path queries at four, actual="+plans);
   h.assertTrue(t.getBoolean("renewalPending"),"An unfinished reachable-platform search remains pending");
   h.assertTrue(t.getInt("renewalScan")==first,"The candidate is retained rather than skipped");
   h.assertTrue(t.contains("renewalPlatform"),"The next platform survives in the work record");
   for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<=2;y++)if(x!=0||z!=0||y==2)f.l.setBlock(cage.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
   var saved=t.copy();
   h.runAfterDelay(21,new Runnable(){int attempts;CompoundTag current=saved;
    public void run(){try{
     current=current.copy();long start=HarvestRouteCache.stats(f.forester).plans();
     boolean found=ForestRenewal.plan(f.l,f.e,f.hut(),f.forester,current,Set.of(Items.BIRCH_SAPLING));
     h.assertTrue(HarvestRouteCache.stats(f.forester).plans()-start<=4,"Every resumed turn retains the native-query cap");
     if(found){h.assertTrue(current.getString("stage").equals("sapling"),"Finding soil still requires a paid sapling");h.assertTrue(ForestRenewal.safe(f.l,net.minecraft.core.BlockPos.of(current.getLong("target"))),"The resumed candidate remains physically safe");f.done();h.succeed();return;}
     h.assertTrue(++attempts<75,"Resuming serialized platform progress must eventually find the opened route");h.runAfterDelay(20,this);
    }catch(Throwable ex){f.done();throw ex;}}
   });return;
  }catch(Throwable ex){f.done();throw ex;}
 }
 @GameTest(template="empty",batch="forest_renewal_grass",timeoutTicks=100)
 public static void naturalGroundCoverDoesNotPreventPaidPlanting(GameTestHelper h){var f=ForestFixture.create(h,1,192);
  try{
   net.minecraft.core.BlockPos foot=null;
   for(int x=-4;x<=20&&foot==null;x++)for(int z=3;z<=10&&foot==null;z++){var candidate=f.wood(x,z);if(ForestRenewal.safe(f.l,candidate))foot=candidate;}
   h.assertTrue(foot!=null,"Fixture contains an unprotected empty planting site before adding grass");var neighbor=foot.east();
   f.l.setBlock(neighbor,Blocks.GRASS.defaultBlockState(),2);
   h.assertTrue(ForestRenewal.safe(f.l,foot),"An empty planting cell beside natural grass still has room for a tree");
   h.assertTrue(f.l.getBlockState(neighbor).is(Blocks.GRASS),"Survey leaves the existing grass alone");
   f.l.setBlock(neighbor,Blocks.OAK_PLANKS.defaultBlockState(),2);
   h.assertTrue(!ForestRenewal.safe(f.l,foot),"An adjacent structure still blocks renewal");
  }finally{f.done();}h.succeed();
 }
 @GameTest(template="empty",batch="forest_renewal",timeoutTicks=300)
 public static void exhaustedLevelOnePlantsPaidSaplingAndRecoversPlacement(GameTestHelper h){var f=ForestFixture.create(h,1,192);
  try{
   f.chestBlock().clearContent();f.chestBlock().setItem(0,new ItemStack(Items.BIRCH_SAPLING,2));
   var t=new CompoundTag();t.putInt("schema",2);t.putInt("width",1);t.putInt("height",3);t.putInt("descent",0);t.putUUID("operation",UUID.randomUUID());t.putString("stage","choose");t.putString("felledKind","minecraft:birch_sapling");t.put("tool",new ItemStack(Items.IRON_AXE).save(new CompoundTag()));f.write(t);
   var goal=new ResourceWorkGoal(f.forester,true,()->6000L);h.assertTrue(goal.canUse(),"Level-I forester resumes");var prepared=f.drive(goal,350,r->r.getString("stage").equals("replant"));
   h.assertTrue(prepared.getString("stage").equals("replant"),"No planting: "+prepared+" sample="+f.wood(0,0)+" safe="+ForestRenewal.safe(f.l,f.wood(0,0))+" sky="+f.l.canSeeSky(f.wood(0,0))+" height="+f.l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,f.wood(0,0).getX(),f.wood(0,0).getZ())+" route="+HarvestAccess.find(f.forester,f.wood(0,0)));
   var p=net.minecraft.core.BlockPos.of(prepared.getLong("target"));h.assertTrue(ForestRenewal.safe(f.l,p),"Planting leaves the protected lots and existing blocks alone");
   h.assertTrue(f.chestBlock().countItem(Items.BIRCH_SAPLING)==1&&ForestFixture.count(prepared.getList("cargo",Tag.TAG_COMPOUND),Items.BIRCH_SAPLING)==1,"Exactly one sapling leaves the chest");
   f.drive(goal,1,r->false);h.assertTrue(f.l.getBlockState(p).is(Blocks.BIRCH_SAPLING),"The real goal plants a sapling, not a free tree");
   f.write(prepared);goal=new ResourceWorkGoal(f.forester,true,()->6000L);h.assertTrue(goal.canUse(),"Placement before record save recovers");var after=f.drive(goal,1,r->false);
   h.assertTrue(ForestFixture.count(after.getList("cargo",Tag.TAG_COMPOUND),Items.BIRCH_SAPLING)==0&&f.chestBlock().countItem(Items.BIRCH_SAPLING)==1,"Replay charges the carried sapling once without another withdrawal");
   h.assertTrue(org.villageastra.server.ForestPlantings.get(f.l.getServer()).forester(f.l,p)!=null,"Growth remains marked as village forestry");
  }finally{f.done();}h.succeed();
 }
}
