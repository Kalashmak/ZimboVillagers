package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class QuarryGeometryBudgetGameTests {
 @GameTest(template="empty",batch="quarry_geometry_budget",timeoutTicks=200)
 public static void enclosedOreAdvancesWithoutLeasingImpossibleCorridor(GameTestHelper h)throws Exception{
  var l=h.getLevel();var base=h.absolutePos(BlockPos.ZERO).offset(2752512,120,0);var ore=base.offset(220,-3,220);
  var startChunks=PhysicalFixtureChunks.force(l,base,-3,28,-3,10);var oreChunks=PhysicalFixtureChunks.force(l,ore,-5,5,-5,5);
  for(var pos:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(28,4,10)))l.setBlock(pos,pos.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  for(var pos:BlockPos.betweenClosed(ore.offset(-4,-6,-4),ore.offset(4,4,4)))l.setBlock(pos,Blocks.STONE.defaultBlockState(),2);l.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var mine=new Settlement.Building(UUID.randomUUID(),"mine",20,0,0);s.addBuilding(hall);s.addBuilding(mine);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.STONE_PICKAXE));chest.setItem(1,new ItemStack(Items.BREAD,64));
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:raw_iron",1);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(r.id()));npc.moveTo(base.getX()+2.5,base.getY(),base.getZ()+2.5);
  var work=new CompoundTag();work.putInt("schema",1);work.putUUID("worker",npc.getUUID());work.putString("stage","choose");work.putString("status","mine_floor");MineWork.write(l,mine,work);
  var searchField=NaturalSupplyGoal.class.getDeclaredField("SEARCH_AREAS");searchField.setAccessible(true);
  @SuppressWarnings("unchecked") var areas=(Map<Integer,List<BlockPos>>)searchField.get(null);int original=areas.get(320).indexOf(new BlockPos(220,0,220));h.assertTrue(original>=0,"Fixture uses the exact retained production column order");
  var seed=new CompoundTag();seed.putInt("surveyRadius",320);seed.putInt("surveyCursor",original);seed.putBoolean("discoveryFirst",false);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),seed);
  try{
   h.assertTrue(SurfaceQuarry.safe(l,ore),"Real dry ore itself passes existing extraction safety");
   h.assertTrue(HarvestAccess.platformOffsets().stream().noneMatch(offset->HarvestAccess.standing(l,ore.offset(offset),ore)),"Enclosed ore has no possible local working platform");
   boolean missing=false;for(int x=base.getX()>>4;x<=ore.getX()>>4;x++)for(int z=base.getZ()>>4;z<=ore.getZ()>>4;z++)if(!l.hasChunkAt(new BlockPos(x<<4,base.getY(),z<<4)))missing=true;h.assertTrue(missing,"Real route corridor contains unloaded chunks");
   TouchLoad.exhaust(l.getServer());long loads=TouchLoad.stats().loads();var goal=new NaturalSupplyGoal(npc,true);h.assertTrue(!goal.canUse(),"Fully enclosed ore cannot start a trip");var observed=NaturalSupplyGoal.inspect(l,npc.getUUID());
   h.assertTrue(observed.getInt("surveyCursor")>original,"Reject impossible local geometry before a deferred corridor lease pins its exact ore depth: original="+original+" state="+observed);
   h.assertTrue(TouchLoad.stats().loads()==loads&&chest.countItem(Items.STONE_PICKAXE)==1&&chest.countItem(Items.RAW_IRON)==0&&l.getBlockState(ore).is(Blocks.IRON_ORE),"Early geometry sensing loads no corridor and preserves tool, stock and ore");
  }finally{npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,startChunks);PhysicalFixtureChunks.release(l,oreChunks);}h.succeed();
 }
 @GameTest(template="empty",batch="quarry_geometry_guards",timeoutTicks=200)
 public static void geometryKeepsSupportedFacesAndUnknownTerrainEligible(GameTestHelper h){
  var l=h.getLevel();var ore=h.absolutePos(BlockPos.ZERO).offset(2883584,120,0);var chunks=PhysicalFixtureChunks.force(l,ore,-5,5,-5,5);
  try{
   for(var pos:BlockPos.betweenClosed(ore.offset(-4,-6,-4),ore.offset(4,4,4)))l.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
   l.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),2);h.assertTrue(!HarvestAccess.possiblePlatform(l,ore),"Completely inspected encased ore has no usable local platform");
   var feet=ore.east();l.setBlock(feet,Blocks.AIR.defaultBlockState(),2);l.setBlock(feet.above(),Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(HarvestAccess.standing(l,feet,ore)&&HarvestAccess.possiblePlatform(l,ore),"Dry supported direct or covered working platforms remain eligible");
   var unknown=ore.offset(512,0,-512);h.assertTrue(!l.hasChunkAt(unknown),"Unknown fixture territory is genuinely unloaded");
   h.assertTrue(HarvestAccess.possiblePlatform(l,unknown)&&!l.hasChunkAt(unknown),"Unknown neighbourhood is not rejected or synchronously loaded by local sensing");
  }finally{PhysicalFixtureChunks.release(l,chunks);}h.succeed();
 }

}
