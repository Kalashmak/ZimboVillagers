package org.villageastra.client;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.phys.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Real client attack/use and palette clicks. Fixture grants office; gameplay still requires election. */
final class MayorToolProbe {
 private static int phase,ticks;private static volatile String failure;private static final BlockPos FIRST=new BlockPos(-15,-61,-15),SECOND=new BlockPos(-8,-61,-15);
 static boolean enabled(){return Boolean.getBoolean("villageastra.mayorToolSmoke");}
 private static void use(Minecraft mc,BlockPos pos){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));}
 private static void click(Minecraft mc,int x,int y){var screen=mc.screen;if(!screen.mouseClicked(x,y,0))throw new IllegalStateException("Palette click missed "+x+","+y);screen.mouseReleased(x,y,0);} // buttons may close the screen
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-mayor-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}com.mojang.logging.LogUtils.getLogger().info("ASTRA_MAYOR screenshot {}",path);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);var view=ConstructionOverlay.snapshot();
  if(++ticks>600)throw new IllegalStateException("Mayor tool timeout phase="+phase+" screen="+(mc.screen==null?"none":mc.screen.getClass().getSimpleName())+" survey="+view.getBoolean("survey")+" road="+view.getBoolean("road")+" twoPoints="+view.getBoolean("twoPoints")+" cells="+view.getInt("surveyCells")+" conflicts="+view.getInt("conflicts")+" canLay="+view.getBoolean("canLay"));int x=(mc.getWindow().getGuiScaledWidth()-MayorSurveyScreen.WIDTH)/2,y=(mc.getWindow().getGuiScaledHeight()-MayorSurveyScreen.HEIGHT)/2;
  if(phase==0){phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{var server=mc.getSingleplayerServer();var e=SettlementData.get(server).entries().iterator().next();var p=server.getPlayerList().getPlayers().get(0);e.settlement().appointPlayerMayor(p.getUUID());for(var r:e.settlement().residents())((ResidentEntity)server.overworld().getEntity(r.id())).setNoAi(true);p.teleportTo(server.overworld(),3.5,-59,3.5,0,10);}catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&ticks>30){use(mc,new BlockPos(2,-59,4));phase=2;ticks=0;}
  else if(phase==2&&mc.screen instanceof ConstructionScreen&&ticks>30){capture(mc,"hall");mc.setScreen(null);mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(org.villageastra.VillageAstra.MAYOR_SHOVEL.get()));p.teleportTo(p.serverLevel(),-15,-60,-13,180,45);});phase=3;ticks=0;}
  else if(phase==3&&ticks>30){mc.gameMode.startDestroyBlock(FIRST,Direction.UP);phase=4;ticks=0;}
  else if(phase==4&&mc.screen instanceof MayorSurveyScreen&&ticks>30){if(!view.getBoolean("survey")||ConstructionOverlay.geometry().isEmpty())throw new IllegalStateException("No building projection");capture(mc,"catalogue");click(mc,x+180,y+58);phase=5;ticks=0;}
  else if(phase==5&&view.getString("design").equals("home_2")&&ticks>20){click(mc,x+180,y+162);phase=6;ticks=0;}
  else if(phase==6&&view.getInt("variant")==1&&ticks>20){mc.setScreen(null);mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);if(!p.serverLevel().getBlockState(FIRST).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK))failure="Left click changed ground";p.teleportTo(p.serverLevel(),-30,-47,-26,-40,30);});phase=7;ticks=0;}
  else if(phase==7&&ticks>35){capture(mc,"projection");mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.teleportTo(p.serverLevel(),-15,-60,-13,180,45);});phase=8;ticks=0;}
  else if(phase==8&&ticks>30){use(mc,FIRST);phase=9;ticks=0;}
  else if(phase==9&&view.getBoolean("road")&&!view.getBoolean("twoPoints")&&ticks>20){if(mc.screen!=null)throw new IllegalStateException("First point opened palette prematurely");mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.teleportTo(p.serverLevel(),-8,-60,-13,180,45);});phase=10;ticks=0;}
  else if(phase==10&&ticks>30){use(mc,SECOND);phase=11;ticks=0;}
  else if(phase==11&&mc.screen instanceof MayorSurveyScreen&&view.getBoolean("canLay")&&ticks>30){capture(mc,"road-palette");click(mc,x+30,y+147);phase=12;ticks=0;}
  else if(phase==12&&!view.getBoolean("survey")&&ticks>30){mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);int count=0;for(int xx=-16;xx<=-7;xx++)for(int zz=-16;zz<=-14;zz++){if(!p.serverLevel().getBlockState(new BlockPos(xx,-61,zz)).is(net.minecraft.world.level.block.Blocks.DIRT_PATH))throw new IllegalStateException("Incomplete 3-wide road");count++;}if(p.getMainHandItem().getDamageValue()!=count)throw new IllegalStateException("Shovel wear mismatch");com.mojang.logging.LogUtils.getLogger().info("ASTRA_MAYOR VERIFIED actual LMB/RMB; catalogue selection and rotation; world projection; 30 path cells; survival durability=30; hall palette opened from lectern");mc.execute(mc::stop);}catch(Exception ex){failure=ex.toString();}});phase=13;}
 }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().error("ASTRA_MAYOR FAILED",ex);mc.stop();}}
}
