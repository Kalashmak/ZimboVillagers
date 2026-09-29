package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-037: an ordinary educated resident becomes cartographer and personally surveys chunks for paper; the hall screen's Atlas button renders the opened 3D map. Fixture: office record, chest, paper and one educated adult arrival. */
final class AtlasProbe {
 static final int NEEDED=4;
 private static int phase,ticks;private static int whole;private static volatile String failure,progress="";private static volatile int surveyed,paper=-1;private static volatile boolean ready;
 static boolean enabled(){return Boolean.getBoolean("villageastra.atlasSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-atlas-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_ATLAS screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Atlas timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{
   var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var settlement=e.settlement();
   var office=new Settlement.Building(Settlement.childId(settlement.id(),"building/cartographer-probe"),"cartographer",-44,0,-4);settlement.addBuilding(office);
   var chestPos=LogisticsRoutes.position(e,office);
   // The office stands where the natural ground may rise: the fixture clears a small yard around its chest, or the cartographer is born inside a hill.
   for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){l.setBlock(chestPos.offset(dx,-1,dz),net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(),3);
    for(int dy=0;dy<=5;dy++)l.setBlock(chestPos.offset(dx,dy,dz),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);}
   l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
   var chest=LogisticsRoutes.chest(l,e,office);if(chest==null)throw new IllegalStateException("Office chest missing");chest.setItem(0,new ItemStack(Items.PAPER,32));
   // Fixture: the starter village has no adult graduates (education is covered by PopulationGameTests), so one educated adult arrives with a one-bed home record.
   var home=new Settlement.Home(Settlement.childId(settlement.id(),"home/cartographer-probe"),1,1,true);settlement.addHome(home);
   var r=new Resident(java.util.UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(r,home.id());
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(settlement.id(),settlement.resident(r.id()));npc.moveTo(chestPos.getX()+1.5,chestPos.getY(),chestPos.getZ()+1.5,0,0);l.addFreshEntity(npc);
   settlement.assign(r.id(),Profession.CARTOGRAPHER,office.id());SettlementData.get(s).setDirty();
   LogUtils.getLogger().info("ASTRA_ATLAS fixture resident {} assigned as cartographer; office chest {} with 32 paper; area {} chunks",r.id(),chestPos.toShortString(),Atlas.area(e).size());ready=true;
  }catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&ready&&ticks%100==0){mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);surveyed=Atlas.surveyed(Atlas.inspect(l,e.settlement().id())).size();
    var office=e.settlement().buildings().stream().filter(b->b.type().equals("cartographer")).findFirst().orElseThrow();var chest=LogisticsRoutes.chest(l,e,office);paper=chest==null?-1:chest.countItem(Items.PAPER);
    var worker=e.settlement().residents().stream().filter(x->x.profession()==Profession.CARTOGRAPHER).findFirst().map(x->l.getEntity(x.id())).orElse(null);
    progress="surveyed="+surveyed+" paper="+paper+" status="+(worker instanceof ResidentEntity npc?npc.workStatus()+" at "+npc.blockPosition().toShortString():"none");LogUtils.getLogger().info("ASTRA_ATLAS progress {}",progress);});
   if(surveyed>=NEEDED){if(paper!=32-surveyed)throw new IllegalStateException("Paper spent does not match opened chunks: "+progress);
    mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var c=entry(mc.getSingleplayerServer()).center();p.teleportTo(p.serverLevel(),c.getX()+.5,c.getY()+2,c.getZ()+.5,0,0);});
    OfficeProbes.open(mc,ConstructionScreen.OVERVIEW);phase=2;ticks=0;}}
  else if(phase==2&&ticks>40&&mc.screen instanceof ConstructionScreen screen){OfficeProbes.clickOrFail(screen,screen.atlasButton(),"Atlas button");phase=3;ticks=0;}
  else if(phase==3&&mc.screen instanceof AtlasScreen&&ticks>60){
   if(AtlasScreen.live().getBoolean("missing"))throw new IllegalStateException("Atlas reports no settlement");
   if(AtlasScreen.opened()<NEEDED){if(ticks>600)throw new IllegalStateException("Client received "+AtlasScreen.opened()+" chunks");return;}
   capture(mc,"scene");whole=AtlasScreen.TERRAIN.blocks;
   // ISO-003: the side section — everything past a plane through the settlement is left out and the cut face shows its layers.
   ((AtlasScreen)mc.screen).section(1);phase=4;ticks=0;}
  else if(phase==4&&ticks>60&&mc.screen instanceof AtlasScreen screen){
   int cutBlocks=AtlasScreen.TERRAIN.blocks;
   if(cutBlocks<=0||cutBlocks==whole)throw new IllegalStateException("The side section did not change the scene: "+whole+" -> "+cutBlocks);
   capture(mc,"section");screen.section(0);
   LogUtils.getLogger().info("ASTRA_ATLAS VERIFIED cartographer surveyed {} chunks for {} paper; hall Atlas button rendered {} opened chunks in the isometric scene; side section drew {} of {} blocks; reload=false",surveyed,32-paper,AtlasScreen.opened(),cutBlocks,whole);mc.setScreen(null);mc.stop();phase=5;}
  else if(phase==3&&ticks>400&&!(mc.screen instanceof AtlasScreen))throw new IllegalStateException("Atlas screen did not open");
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_ATLAS FAILED",ex);mc.stop();}}
}
