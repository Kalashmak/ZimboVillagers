package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Physical patrol acceptance: no direct patrol/goal ticks and no repositioning after spawn. */
final class PatrolProbe {
 private static int ticks,phase;private static boolean busy;private static volatile boolean done;private static volatile String failure;private static UUID village;private static Wolf wolf;private static BlockPos start;private static double walked;private static int spent;private static final Set<BlockPos> TORCHES=new HashSet<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.patrolSmoke");}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>3600)throw new IllegalStateException("Patrol timeout phase="+phase+" spent="+spent+" walked="+walked);
  if(done){var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-patrol.png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_PATROL screenshot {}",path);LogUtils.getLogger().info("ASTRA_PATROL VERIFIED naturalTicks=true walked=true torches=2 paid=2 reserved=true");mc.stop();return;}
  if(ticks%20!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_PATROL FAILED",ex);mc.stop();}}
 private static void step(ServerLevel l){var data=SettlementData.get(l.getServer());
  if(phase==0){var e=data.entries().iterator().next();village=e.settlement().id();var s=e.settlement();for(var r:s.residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc)npc.setNoAi(true);
   l.setDayTime(18000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   var officeId=Settlement.childId(village,"patrol-probe/office");s.addBuilding(new Settlement.Building(officeId,"cartographer",64,0,-24));for(int n=2;n<=5;n++)s.raiseBuildingLevel(officeId,n);var office=s.buildings().stream().filter(b->b.id().equals(officeId)).findFirst().orElseThrow();
   var farm=new Settlement.Building(Settlement.childId(village,"patrol-probe/farm"),"livestock",64,0,30);s.addBuilding(farm);var kennel=new Settlement.Building(Settlement.childId(village,"patrol-probe/kennel"),VillageWolves.TYPE,84,0,30);s.addBuilding(kennel);s.linkAnnex(kennel.id(),farm.id());
   for(int x=58;x<=108;x++)for(int z=-8;z<=26;z++){var at=e.center().offset(x,0,z);l.setBlock(at,Blocks.STONE.defaultBlockState(),3);for(int y=1;y<=4;y++)l.setBlock(at.above(y),Blocks.AIR.defaultBlockState(),3);}
   for(var b:List.of(office,kennel))for(var cell:BuildingPlacement.layout(e,b,b==office?BuildingTiers.layoutId("cartographer",5):VillageWolves.TYPE).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   var chestAt=LogisticsRoutes.position(e,office);l.setBlock(chestAt,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);BuildingLevels.forgetBest(village);Atlas.forgetMargins();var chest=LogisticsRoutes.chest(l,e,office);chest.clearContent();chest.setItem(0,new ItemStack(Items.TORCH,2));
   start=e.center().offset(80,1,8);l.setBlock(start.west(2),Blocks.LANTERN.defaultBlockState(),3);
   wolf=EntityType.WOLF.create(l);wolf.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5,0,0);l.addFreshEntity(wolf);wolf.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l));require(VillageWolves.enlist(l,e,wolf,kennel),"Wolf enlisted");
   var player=l.getServer().getPlayerList().getPlayers().get(0);player.setGameMode(GameType.SPECTATOR);player.teleportTo(l,start.getX()+.5,start.getY()+12,start.getZ()-17,0,40);phase=1;LogUtils.getLogger().info("ASTRA_PATROL setup wolf={} start={}",wolf.getUUID(),start);return;
  }
  var e=data.entry(village);var chest=LogisticsRoutes.chest(l,e,CartographyLadder.house(l,e));spent=2-chest.countItem(Items.TORCH);walked=Math.max(walked,Math.sqrt(wolf.distanceToSqr(start.getX()+.5,start.getY(),start.getZ()+.5)));
  if(ticks%200==0)LogUtils.getLogger().info("ASTRA_PATROL progress at={} trained={} paid={} walked={} goal={}",wolf.blockPosition(),wolf.getPersistentData().hasUUID(CartographyLadder.PATROL),spent,walked,wolf.goalSelector.getRunningGoals().map(g->g.getGoal().getClass().getSimpleName()).toList());
  for(var at:BlockPos.betweenClosed(wolf.blockPosition().offset(-5,-2,-5),wolf.blockPosition().offset(5,2,5)))if(l.getBlockState(at).is(Blocks.TORCH))TORCHES.add(at.immutable());
  if(spent==2){require(walked>=6,"Wolf must physically walk away from the lantern");require(TORCHES.size()==2,"Exactly two real torches observed: "+TORCHES);require(!VillageWolves.DOGS.free(l,e).contains(wolf.getUUID()),"Patrol excluded from delivery pool");
   wolf.setNoAi(true);if(phase==1){phase=2;LogUtils.getLogger().info("ASTRA_PATROL completed positions={} walked={}",TORCHES,walked);return;}done=true;}
 }
}
