package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Profession;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-038: real shovel marks and palette clicks order a cobbled road with lamps; the ordinary builder carries hall materials and paves it; speed and real light are measured. Fixture: office and hall stock. */
final class RoadProbe {
 private static final BlockPos FIRST=new BlockPos(-15,-61,-15),SECOND=new BlockPos(-8,-61,-15);
 private static int phase,ticks,lastIndex=-1,stalled;private static volatile String failure,progress="";private static volatile boolean ordered,complete,verified;private static volatile int cobbleBefore;
 static boolean enabled(){return Boolean.getBoolean("villageastra.roadSmoke");}
 private static void use(Minecraft mc,BlockPos pos){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));}
 private static void click(Minecraft mc,int x,int y){var screen=mc.screen;if(!screen.mouseClicked(x,y,0))throw new IllegalStateException("Palette click missed "+x+","+y);screen.mouseReleased(x,y,0);}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-road-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_ROAD screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);var view=ConstructionOverlay.snapshot();
  if(++ticks>9000)throw new IllegalStateException("Road timeout phase="+phase+" "+progress);int x=(mc.getWindow().getGuiScaledWidth()-MayorSurveyScreen.WIDTH)/2,y=(mc.getWindow().getGuiScaledHeight()-MayorSurveyScreen.HEIGHT)/2;
  if(phase==0){phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);e.settlement().appointPlayerMayor(p.getUUID());
    int parked=0;for(var r:e.settlement().residents())if(r.profession()!=Profession.BUILDER){var npc=(ResidentEntity)l.getEntity(r.id());npc.setNoAi(true);npc.teleportTo(30.5+2*parked++,-60,20.5);}
    var chest=(Container)l.getBlockEntity(e.center().offset(1,1,4));int slot=chest.getContainerSize()-3;chest.setItem(slot,new ItemStack(Items.COBBLESTONE,64));chest.setItem(slot+1,new ItemStack(Items.OAK_FENCE,4));chest.setItem(slot+2,new ItemStack(Items.LANTERN,4));for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(Items.COBBLESTONE))cobbleBefore+=chest.getItem(i).getCount();
    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VillageAstra.MAYOR_SHOVEL.get()));p.teleportTo(l,-15,-60,-13,180,45);
    LogUtils.getLogger().info("ASTRA_ROAD fixture: mayor office, 64 cobblestone, 4 fences and 4 lanterns in the hall chest");}catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&ticks>40){use(mc,FIRST);phase=2;ticks=0;}
  else if(phase==2&&view.getBoolean("road")&&!view.getBoolean("twoPoints")&&ticks>20){mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.teleportTo(p.serverLevel(),-8,-60,-13,180,45);});phase=3;ticks=0;}
  else if(phase==3&&ticks>30){use(mc,SECOND);phase=4;ticks=0;}
  else if(phase==4&&mc.screen instanceof MayorSurveyScreen&&view.getBoolean("canLay")&&ticks>30){int surface=(view.getInt("variant")>>2)&3;
   if(surface<2){click(mc,x+26,y+83);ticks=0;return;}
   if(((view.getInt("variant")>>4)&1)==0){click(mc,x+179,y+83);ticks=0;return;}
   if(view.getInt("roadItems")<=0)return;capture(mc,"palette");LogUtils.getLogger().info("ASTRA_ROAD palette cobbled road with lamps: {} items",view.getInt("roadItems"));click(mc,x+30,y+147);phase=5;ticks=0;}
  else if(phase==5&&ticks%20==0){mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();if(Roads.active(s.overworld(),entry(s).settlement().id()))ordered=true;});
   if(ordered){mc.setScreen(null);mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.teleportTo(p.serverLevel(),-11.5,-55,-24.5,0,35);});phase=6;ticks=0;}
   else if(ticks>400)throw new IllegalStateException("Road project was not ordered");}
  else if(phase==6&&ticks%100==0){mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var t=Roads.project(l,e.settlement().id());
    var builder=e.settlement().residents().stream().filter(r->r.profession()==Profession.BUILDER).findFirst().map(r->l.getEntity(r.id())).orElse(null);
    progress="index="+t.getInt("index")+"/"+t.getList("ops",10).size()+" cargo="+t.getList("cargo",10)+" status="+(builder instanceof ResidentEntity b?b.workStatus()+" at "+b.blockPosition().toShortString()+" goals="+b.runningGoals():"none");LogUtils.getLogger().info("ASTRA_ROAD progress {}",progress);
    int marker=t.getInt("index")*100+t.getInt("withdrawals")+t.getInt("deposits");if(marker==lastIndex)stalled+=100;else{lastIndex=marker;stalled=0;}if(stalled>=1600)failure="Builder stalled: "+progress;
    if(t.getBoolean("complete"))complete=true;}catch(Exception ex){failure=ex.toString();}});
   if(ticks==1000)capture(mc,"progress");
   if(complete){phase=7;ticks=0;mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);
     int paved=0;for(int xx=-16;xx<=-7;xx++)for(int zz=-16;zz<=-14;zz++){var pos=new BlockPos(xx,-61,zz);if(!l.getBlockState(pos).is(Blocks.COBBLESTONE)||Roads.cell(l,pos)==null||Roads.cell(l,pos).tier!=2)throw new IllegalStateException("Cell not paved and registered "+pos.toShortString());paved++;}
     int lamps=0;for(int xx=-20;xx<=-3;xx++)for(int zz=-20;zz<=-10;zz++)for(int yy=-60;yy<=-58;yy++)if(l.getBlockState(new BlockPos(xx,yy,zz)).is(Blocks.LANTERN)&&l.getBlockState(new BlockPos(xx,yy-1,zz)).is(Blocks.OAK_FENCE))lamps++;
     if(lamps<1)throw new IllegalStateException("No lamp post built");if(!Roads.lit(l,FIRST))throw new IllegalStateException("Road start is not lit by block light");
     var chest=(Container)l.getBlockEntity(e.center().offset(1,1,4));int dirt=0,cobble=0;for(int i=0;i<chest.getContainerSize();i++){if(chest.getItem(i).is(Items.DIRT))dirt+=chest.getItem(i).getCount();if(chest.getItem(i).is(Items.COBBLESTONE))cobble+=chest.getItem(i).getCount();}
     if(cobble!=cobbleBefore-paved)throw new IllegalStateException("Cobblestone spent "+(cobbleBefore-cobble)+" for "+paved+" cells");if(dirt<paved)throw new IllegalStateException("Removed soil not returned: "+dirt);
     p.teleportTo(l,-11.5,-60,-14.5,0,30);p.setOnGround(true);Roads.living(p);var modifier=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifier(Roads.SPEED_ID);
     if(modifier==null||Math.abs(modifier.getAmount()-0.30)>1e-6)throw new IllegalStateException("Player speed bonus on cobble missing: "+modifier);
     progress="paved="+paved+" lamps="+lamps+" dirt="+dirt;verified=true;p.teleportTo(l,-11.5,-55,-24.5,0,35);}catch(Exception ex){failure=ex.toString();}});}}
  else if(phase==7&&verified&&ticks>40){capture(mc,"road");LogUtils.getLogger().info("ASTRA_ROAD VERIFIED shovel palette ordered a cobbled lit road; builder carried hall materials, paved and registered cells, built lamp posts, returned soil; player speed +30% on cobble; {}; reload=false",progress);mc.stop();phase=8;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_ROAD FAILED",ex);mc.stop();}}
}
