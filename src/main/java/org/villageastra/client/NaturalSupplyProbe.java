package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.villageastra.domain.Profession;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Fixture supplies a real sand deposit and coal, never glass or artificial cooking ticks. */
final class NaturalSupplyProbe {
 private static int ticks,finishedTicks;private static volatile String failure,progress="";private static volatile boolean done,loaded,lit;private static boolean shot;private static BlockPos sand,furnacePos;private static UUID miner,mayor;
 static boolean enabled(){return Boolean.getBoolean("villageastra.naturalSupplySmoke");}
 static void setup(net.minecraft.server.MinecraftServer server)throws Exception{
  SupplyAudit.write(server);
  var l=server.overworld();server.getPlayerList().getPlayers().get(0).setGameMode(net.minecraft.world.level.GameType.SPECTATOR);var e=SettlementData.get(server).entries().iterator().next();var hall=Workshops.hall(e);var chest=LogisticsRoutes.chest(l,e,hall);chest.clearContent();chest.setItem(0,new ItemStack(Items.COAL,4));chest.setItem(1,new ItemStack(Items.BREAD,64));
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);
  for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc){if(r.profession()==Profession.MINER)miner=r.id();else if(r.profession()==Profession.MAYOR)mayor=r.id();else npc.setNoAi(true);}
  if(miner==null||mayor==null)throw new IllegalStateException("Fixture needs gatherer and workshop operator");
  sand=e.center().offset(-6,-1,-6);l.setBlock(sand,Blocks.SAND.defaultBlockState(),3);l.setBlock(sand.west(),Blocks.SAND.defaultBlockState(),3);
  var order=new CompoundTag();order.putUUID("id",UUID.randomUUID());order.putString("design","home");var cost=new CompoundTag();cost.putInt("minecraft:glass",2);order.put("cost",cost);HallUpgradeGoal.enqueue(l,e,order);
  LogUtils.getLogger().info("ASTRA_SUPPLY fixture two natural sand blocks at {}; four coal and bread in stock; no glass; normal NPC goals and furnace ticks",sand.toShortString());
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>18000)throw new IllegalStateException("Supply timeout "+progress);
  var server=mc.getSingleplayerServer();if(ticks%100==0)server.execute(()->{try{
   var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();var hall=Workshops.hall(e);var chest=LogisticsRoutes.chest(l,e,hall);var job=Workshops.inspect(l,hall.id());var m=(ResidentEntity)l.getEntity(miner);var operator=(ResidentEntity)l.getEntity(mayor);
   int glass=LogisticsRoutes.count(chest,s->s.is(Items.GLASS));int raw=LogisticsRoutes.count(chest,s->s.is(Items.SAND));int removed=(l.getBlockState(sand).isAir()?1:0)+(l.getBlockState(sand.west()).isAir()?1:0);
   if(job.contains("furnace")&&l.getBlockEntity(BlockPos.of(job.getLong("furnace"))) instanceof FurnaceBlockEntity f){furnacePos=BlockPos.of(job.getLong("furnace"));loaded|=f.getItem(0).is(Items.SAND);lit|=f.getBlockState().getValue(net.minecraft.world.level.block.FurnaceBlock.LIT);}
   progress="removed="+removed+" raw="+raw+" glass="+glass+" stage="+job.getString("stage")+" miner="+m.workStatus()+"@"+m.blockPosition().toShortString()+" operator="+operator.workStatus()+"@"+operator.blockPosition().toShortString();
   LogUtils.getLogger().info("ASTRA_SUPPLY progress {}",progress);
   if(glass==2){if(removed!=2||!loaded||!lit||LogisticsRoutes.count(chest,s->s.is(Items.COAL))>=4)throw new IllegalStateException("Glass lacks physical evidence "+progress);var at=furnacePos;var player=server.getPlayerList().getPlayers().get(0);player.teleportTo(l,at.getX()+2.6,at.getY()+.2,at.getZ()+.9,0,0);player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,net.minecraft.world.phys.Vec3.atCenterOf(at));done=true;}
  }catch(Exception ex){failure=ex.toString();}});
  if(done&&++finishedTicks>40&&!shot){shot=true;var path=java.nio.file.Path.of("../docs/runs/"+server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-supply.png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_SUPPLY screenshot {}",path);LogUtils.getLogger().info("ASTRA_SUPPLY VERIFIED removed=2 glass=2 physicalInput=true vanillaFire=true paidFuel=true reload=false");mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_SUPPLY FAILED",ex);mc.stop();}}
}
