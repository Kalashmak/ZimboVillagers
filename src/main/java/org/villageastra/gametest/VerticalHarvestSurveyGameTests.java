package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;

/** Actual native sensing, durable continuation and changed terrain; no physical trip claimed. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class VerticalHarvestSurveyGameTests {
 @GameTest(template="empty",batch="vertical_harvest_survey",timeoutTicks=200)
 public static void costlyVerticalSurveyYieldsAndReloadsWithoutSkippingTheLowerOre(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+491520,120,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-18)>>4;x<=(base.getX()+53)>>4;x++)for(int z=(base.getZ()-18)>>4;z<=(base.getZ()+20)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-6;x<=36;x++)for(int z=-6;z<=6;z++)for(int y=-30;y<=20;y++)l.setBlock(base.offset(x,y,z),(y==0&&(x<=12||x>=21)||y==8&&x>=21)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var upper=base.offset(25,9,0);var lower=base.offset(25,1,0);l.setBlock(upper,Blocks.IRON_ORE.defaultBlockState(),2);l.setBlock(lower,Blocks.IRON_ORE.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var mine=new Settlement.Building(UUID.randomUUID(),"mine",-40,0,-40);s.addBuilding(mine);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.STONE_PICKAXE));
  var work=MineWork.read(l,mine);int limit=MineWork.floorStep(l,e,mine,work);s.noteMine(mine.id(),limit,3,5,7);work.putInt("step",limit+1);work.putInt("side",MineDrive.DONE);work.putInt("floorStep",limit);work.putString("stage","choose");work.putString("status","mine_floor");work.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));work.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,limit).toArray());MineWork.write(l,mine,work);
  var project=new CompoundTag();var id=UUID.randomUUID();project.putUUID("id",id);project.putUUID("project",id);project.putString("kind","building");project.putString("design","home");project.putLong("origin",base.offset(40,0,20).asLong());var cost=new CompoundTag();cost.putInt("minecraft:raw_iron",2);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.setNoAi(true);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);s.assign(person.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(person.id()));npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);l.addFreshEntity(npc);
  int cursor=0;for(int x=-192;x<=192;x++)for(int z=-192;z<=192;z++){int d=x*x+z*z;if(d<625||d==625&&(x<25||x==25&&z<0))cursor++;}
  var scan=new CompoundTag();scan.putInt("surveyCursor",cursor);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),scan);var supply=new NaturalSupplyGoal(npc,true);
  h.assertTrue(HarvestAccess.find(npc,upper,320)==null&&HarvestAccess.find(npc,lower,320)==null,"Both real ore platforms are unreachable across the unsupported gap");
  h.assertTrue(SurfaceQuarry.safe(l,upper)&&SurfaceQuarry.safe(l,lower),"Test ores are outside building protection and dry");
  com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_VERTICAL_SURVEY INITIAL demand={} quarry={} tool={} height={} floor={}",NaturalSupplyGoal.demand(l,e),SurfaceQuarry.mayStart(l,e,npc),SurfaceQuarry.tool(l,e,l.getBlockState(upper)),l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,upper.getX(),upper.getZ()),MineWork.floorY(l,e,mine,BuildingTiers.level(l,e,mine)));
  long before=HarvestRouteCache.stats(npc).plans();boolean selected=supply.canUse();var state=NaturalSupplyGoal.inspect(l,npc.getUUID());long plans=HarvestRouteCache.stats(npc).plans()-before;
  com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_VERTICAL_SURVEY firstPlans={} cursorBefore={} state={}",plans,cursor,state);
  h.assertTrue(!selected&&plans>0&&plans<=4,"One sensing turn must not plan both ore floors: nativePlans="+plans);
  h.assertTrue(state.getInt("surveyCursor")==cursor&&state.contains("surveyY")&&state.getInt("surveyY")<upper.getY()&&state.getInt("surveyY")>=lower.getY(),"The exact unexamined lower depth is saved on the same column");
  for(int retry=0;retry<4;retry++)h.assertTrue(!supply.canUse(),"No new job is invented by immediate sensing retries");
  long retried=HarvestRouteCache.stats(npc).plans()-before;var paused=NaturalSupplyGoal.inspect(l,npc.getUUID());
  com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_VERTICAL_SURVEY RETRIES firstPlans={} windowPlans={} sameBodyTick={} cursor={} depth={}",plans,retried,npc.tickCount,paused.getInt("surveyCursor"),paused.getInt("surveyY"));
  h.assertTrue(retried==plans&&paused.getInt("surveyCursor")==cursor&&paused.getInt("surveyY")==state.getInt("surveyY"),"Repeated calls within the same body tick do not reset the spent native-search budget or skip depth: plans="+retried);
  for(int x=13;x<=20;x++)for(int z=-6;z<=6;z++)l.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
  h.runAtTickTime(8,()->{
   h.assertTrue(npc.tickCount>0&&npc.tickCount<20,"Several ordinary body ticks passed within the same sensing window");
   h.assertTrue(!supply.canUse()&&HarvestRouteCache.stats(npc).plans()-before==plans,"The spent allowance remains spent after real body ticks, even with a newly reachable bridge");
   var waiting=NaturalSupplyGoal.inspect(l,npc.getUUID());
   h.assertTrue(waiting.getInt("surveyCursor")==state.getInt("surveyCursor")&&waiting.getInt("surveyY")==state.getInt("surveyY"),"Waiting for the next window preserves the exact unexamined depth");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_VERTICAL_SURVEY WAIT bodyTicks={} plans={} depth={}",npc.tickCount,plans,waiting.getInt("surveyY"));
  });
  h.runAtTickTime(24,()->{
   var reloaded=new NaturalSupplyGoal(npc,true);h.assertTrue(reloaded.canUse(),"Reloaded sensing resumes the saved lower depth after real ticks");var resumed=NaturalSupplyGoal.inspect(l,npc.getUUID());
   h.assertTrue(resumed.hasUUID("id")&&BlockPos.of(resumed.getLong("target")).equals(lower)&&resumed.getString("stage").equals("tool"),"The lower ore was not skipped or replaced by a fabricated harvest");
   h.assertTrue(l.getBlockState(upper).is(Blocks.IRON_ORE)&&l.getBlockState(lower).is(Blocks.IRON_ORE)&&chest.countItem(Items.STONE_PICKAXE)==1,"Sensing does not mine ore or borrow a tool");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_VERTICAL_SURVEY VERIFIED firstPlans={} resumedTarget={} savedDepth={}",plans,lower,state.getInt("surveyY"));
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
}
