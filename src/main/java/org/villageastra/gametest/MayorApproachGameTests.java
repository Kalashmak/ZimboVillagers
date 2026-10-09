package org.villageastra.gametest;

import java.util.*;
import java.util.zip.GZIPInputStream;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** Prepared route regressions; neither supplies a village nor proves full natural development. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MayorApproachGameTests {
 @GameTest(template="empty",batch="mayor_safe_approach",timeoutTicks=6000)
 public static void mayorWalksTheExistingRampInsteadOfDiscardingTheSite(GameTestHelper h)throws Exception {
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+2162688,90,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-3,40,-3,15);
  for(int x=-2;x<=38;x++)for(int z=-2;z<=12;z++){
   int height=x<=4?3:z>=8&&z<=10&&x<=7?7-x:0;
   for(int y=0;y<=7;y++)l.setBlock(base.offset(x,y,z),y<=height?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
  run(h,base,base.offset(30,0,2),new Vec3(base.getX()+2.5,94,base.getZ()+2.5),held,true);
 }
 @GameTest(template="empty",batch="mayor_saved_approach",timeoutTicks=8000)
 public static void mayorReachesTheSurveyedFarmAcrossTheSavedVillageTerrain(GameTestHelper h)throws Exception {
  JsonObject fixture;
  try(var in=new GZIPInputStream(MayorApproachGameTests.class.getResourceAsStream("/data/villageastra/fixtures/fresh5_farm_route_20261009.json.gz"))){fixture=JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()-2162688,150,at.getZ());
  var bounds=fixture.getAsJsonArray("bounds");var min=bounds.get(0).getAsJsonArray();var max=bounds.get(1).getAsJsonArray();
  var held=PhysicalFixtureChunks.force(l,base,min.get(0).getAsInt()-1,max.get(0).getAsInt()+1,min.get(2).getAsInt()-1,max.get(2).getAsInt()+1);
  var palette=fixture.getAsJsonArray("palette");var states=new net.minecraft.world.level.block.state.BlockState[palette.size()];
  for(int i=0;i<states.length;i++)states[i]=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),palette.get(i).getAsString(),false).blockState();
  for(var raw:fixture.getAsJsonArray("runs")){var r=raw.getAsJsonArray();for(int y=0;y<r.get(3).getAsInt();y++)l.setBlock(base.offset(r.get(0).getAsInt(),r.get(1).getAsInt()+y,r.get(2).getAsInt()),states[r.get(4).getAsInt()],2);}
  var start=fixture.getAsJsonArray("start");var target=fixture.getAsJsonArray("target");
  run(h,base,base.offset(target.get(0).getAsInt(),target.get(1).getAsInt(),target.get(2).getAsInt()),new Vec3(base.getX()+start.get(0).getAsDouble(),base.getY()+start.get(1).getAsDouble(),base.getZ()+start.get(2).getAsDouble()),held,false);
 }
 private static void run(GameTestHelper h,BlockPos base,BlockPos site,Vec3 start,List<net.minecraft.world.level.ChunkPos> held,boolean cliff)throws Exception {
  var l=h.getLevel();var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var home=new Settlement.Home(UUID.randomUUID(),1,1,true);s.addHome(home);
  var npc=VillageAstra.RESIDENT.get().create(l);var resident=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(resident,home.id());s.assign(resident.id(),Profession.MAYOR,hall.id());
  var entry=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(entry);npc.bind(s.id(),resident);npc.moveTo(start.x,start.y,start.z);npc.setHealth(3);npc.setOnGround(true);
  h.assertTrue(l.noCollision(npc,npc.getBoundingBox()),"Prepared observed body fits the terrain");
  var ordinary=npc.routeTo(site.above(),0);var safe=npc.routeTo(site.above(),0,BuildingOrders.REACH);
  com.mojang.logging.LogUtils.getLogger().info("MAYOR_APPROACH_INITIAL cliff={} ordinaryReach={} ordinaryReversible={} safeReach={} safeReversible={} ordinaryNodes={} safeNodes={}",cliff,ordinary!=null&&ordinary.canReach(),HarvestAccess.reversible(ordinary),safe!=null&&safe.canReach(),HarvestAccess.reversible(safe),ordinary==null?0:ordinary.getNodeCount(),safe==null?0:safe.getNodeCount());
  if(cliff)h.assertTrue(ordinary!=null&&ordinary.canReach()&&!HarvestAccess.reversible(ordinary),"Ordinary shortest route drops from the cliff");
  h.assertTrue(HarvestAccess.reversible(safe),"The existing terrain has a complete safe native route");
  var f=MayorPlanner.class.getDeclaredField("PROPOSALS");f.setAccessible(true);@SuppressWarnings("unchecked") var proposals=(Map<UUID,MayorPlanner.Proposal>)f.get(null);
  var proposal=new MayorPlanner.Proposal("home",site,"prepared_route");proposals.put(s.id(),proposal);
  npc.onlyGoals(g->g instanceof ResidentDoorGoal||g instanceof DoorwayGoal,5,new MayorSiteGoal(npc,true));
  boolean[] done={false};Runnable clean=()->{done[0]=true;proposals.remove(s.id());npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Prepared body chunk is ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Real walking body registered"));
  h.onEachTick(()->{
   if(done[0]||!npc.isAddedToWorld())return;
   if(npc.workStatus().equals("mayor_unreachable_site")){var why=npc.position()+" ticks="+npc.tickCount;clean.run();throw new GameTestAssertException("Mayor discarded a site with a verified safe native route: "+why);}
   if(!npc.isAlive()||npc.getHealth()!=3){var why=npc.position()+" health="+npc.getHealth();clean.run();throw new GameTestAssertException("Approach damaged or healed its body: "+why);}
   if(npc.distanceToSqr(site.getX()+.5,site.getY()+1,site.getZ()+.5)>BuildingOrders.SITE_DISTANCE*BuildingOrders.SITE_DISTANCE)return;
   com.mojang.logging.LogUtils.getLogger().info("MAYOR_APPROACH_COMPLETE cliff={} bodyTicks={} relative={} health={} stockGrants=0",cliff,npc.tickCount,npc.position().subtract(Vec3.atLowerCornerOf(base)),npc.getHealth());clean.run();h.succeed();
  });
  h.runAtTickTime(cliff?5500:7500,()->{if(done[0])return;var why=npc.position().subtract(Vec3.atLowerCornerOf(base))+" bodyTicks="+npc.tickCount+" status="+npc.workStatus();clean.run();throw new GameTestAssertException("Mayor did not approach the site: "+why);});
 }
}
