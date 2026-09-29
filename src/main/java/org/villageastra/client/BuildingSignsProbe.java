package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.villageastra.domain.Settlement;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** A real client block-use packet must open the bound building, not the first list row. */
final class BuildingSignsProbe {
 private static int ticks,phase;private static volatile boolean ready;private static volatile String failure;private static UUID building;private static BlockPos sign;
 static boolean enabled(){return Boolean.getBoolean("villageastra.buildingSignsSmoke");}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>1200)throw new IllegalStateException("Building sign timeout phase="+phase);
  if(phase==0){phase=1;mc.getSingleplayerServer().execute(()->{try{
   var l=mc.getSingleplayerServer().overworld();var data=SettlementData.get(l.getServer());var center=data.entries().iterator().next().center().offset(200,0,0);
   var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);data.add(e);
   s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,20));building=UUID.randomUUID();var b=new Settlement.Building(building,"home",0,0,0);s.addBuilding(b);
   for(int x=-4;x<12;x++)for(int z=-6;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);for(int y=1;y<12;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
   var cells=BuildingPlacement.layout(e,b,"home");cells.forEach((p,state)->l.setBlock(p,state,3));sign=cells.entrySet().stream().filter(c->c.getValue().is(Blocks.OAK_WALL_SIGN)).findFirst().orElseThrow().getKey();BuildingSigns.refresh(l,e);
   var p=l.getServer().getPlayerList().getPlayers().get(0);s.appointPlayerMayor(p.getUUID());p.setGameMode(GameType.CREATIVE);p.teleportTo(l,sign.getX()+.5,sign.getY()-1,sign.getZ()-2.5,0,0);l.setDayTime(6000);ready=true;
  }catch(Exception ex){failure=ex.toString();}});return;}
  if(!ready||ticks%20!=0)return;
  if(phase==1){if(!mc.level.getBlockState(sign).is(Blocks.OAK_WALL_SIGN))return;
   mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(sign.getCenter(),Direction.NORTH,sign,false));phase=2;return;}
  if(!(mc.screen instanceof ConstructionScreen screen)||screen.section()!=ConstructionScreen.BUILDINGS||!building.equals(screen.selectedBuilding()))return;
  if(phase==2){phase=3;return;}
  var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-building-signs.png");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
  LogUtils.getLogger().info("ASTRA_BUILDING_SIGNS screenshot {}",path);LogUtils.getLogger().info("ASTRA_BUILDING_SIGNS VERIFIED blockUse=true selected=true menu=true");mc.stop();
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_BUILDING_SIGNS FAILED",ex);mc.stop();}}
}
