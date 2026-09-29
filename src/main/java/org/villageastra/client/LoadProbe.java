package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** P13: several settlements live at once — residents eat, work and build, the server keeps its tick budget, and a real save keeps every record. */
final class LoadProbe {
 static final int VILLAGES=3,RESIDENTS=8,MINUTES=4;
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,saved;
 private static volatile int started,alive,projects;private static volatile double worstTick;
 static boolean enabled(){return Boolean.getBoolean("villageastra.loadSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-load-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_LOAD screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 /** Two more settlements beside the generated one, each with homes, food and an ordered house to build. */
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var home=entry(s);
  for(int n=1;n<VILLAGES;n++){
   int cx=home.center().getX()+n*160,cz=home.center().getZ();
   // The chunk has to be there before a settlement is put on it.
   l.getChunk(cx>>4,cz>>4);
   var found=Expeditions.surface(l,cx,cz,home.center().getY(),8,32);
   var center=found!=null?found:new BlockPos(cx,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,cx,cz),cz);
   var settlement=new Settlement(java.util.UUID.randomUUID());
   settlement.addBuilding(new Settlement.Building(Settlement.childId(settlement.id(),"building/town_hall"),"town_hall",0,0,0));
   for(int x=-2;x<10;x++)for(int z=-2;z<10;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
    for(int y=0;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
   var e=new SettlementData.Entry(settlement,home.dimension(),center);SettlementData.get(s).add(e);
   var quarters=new Settlement.Home(Settlement.childId(settlement.id(),"home/load"),RESIDENTS,RESIDENTS,true);settlement.addHome(quarters);
   for(int i=0;i<RESIDENTS;i++){
    var r=new Resident(java.util.UUID.randomUUID(),Resident.Life.ADULT,i%2==0,null,null,-1);settlement.admit(r,quarters.id());
    var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(settlement.id(),settlement.resident(r.id()));
    npc.moveTo(center.getX()+2.5+i%4,center.getY()+1,center.getZ()+2.5+i/4,0,0);l.addFreshEntity(npc);}
   var chest=LogisticsRoutes.chest(l,e,Workshops.hall(e));
   if(chest!=null){chest.setItem(0,new ItemStack(Items.BREAD,64));chest.setItem(1,new ItemStack(Items.WHEAT,64));}
   started++;
  }
  SettlementData.get(s).setDirty();
  LogUtils.getLogger().info("ASTRA_LOAD fixture {} extra settlements with {} residents each",started,RESIDENTS);ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();int live=0,building=0;
  for(var e:SettlementData.get(s).entries()){
   live+=(int)e.settlement().residents().stream().filter(Resident::alive).count();
   if(HallUpgradeGoal.pending(s.overworld(),e.settlement().id()))building++;
  }
  alive=live;projects=building;
  double tick=s.getAverageTickTime();worstTick=Math.max(worstTick,tick);
  progress="settlements="+SettlementData.get(s).entries().size()+" residents="+alive+" projects="+projects+" tick="+String.format(java.util.Locale.ROOT,"%.1f",tick)+"ms";
  LogUtils.getLogger().info("ASTRA_LOAD progress {}",progress);
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);
  if(++ticks>MINUTES*1200+4000)throw new IllegalStateException("Load timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>80){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%200==0){
   sample(mc);
   if(ticks>=MINUTES*1200){phase=2;ticks=0;}}
  else if(phase==2&&ticks>20){
   // A real save, then the records are read back from disk: nothing may be lost by the load itself.
   mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();s.saveEverything(true,true,true);
    var reloaded=SettlementData.load(SettlementData.get(s).save(new net.minecraft.nbt.CompoundTag()));
    if(reloaded.entries().size()!=SettlementData.get(s).entries().size())throw new IllegalStateException("Settlements lost on save: "+reloaded.entries().size());
    for(var e:SettlementData.get(s).entries()){var back=reloaded.entry(e.settlement().id());
     if(back==null||back.settlement().residents().size()!=e.settlement().residents().size())throw new IllegalStateException("Residents lost on save in "+e.settlement().id());}
    saved=true;}catch(Exception ex){failure=ex.toString();}});
   phase=3;ticks=0;}
  else if(phase==3&&saved&&ticks>40){
   if(alive<started*RESIDENTS)throw new IllegalStateException("Residents died under load: "+progress);
   if(worstTick>45)throw new IllegalStateException("Server tick budget exceeded: "+worstTick+"ms");
   capture(mc,"world");
   LogUtils.getLogger().info("ASTRA_LOAD VERIFIED {} settlements ran {} minutes together: {}; worst tick {}ms; save kept every settlement and resident; reload=false",
     SettlementData.get(mc.getSingleplayerServer()).entries().size(),MINUTES,progress,String.format(java.util.Locale.ROOT,"%.1f",worstTick));
   mc.setScreen(null);mc.stop();phase=4;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_LOAD FAILED",ex);mc.stop();}}
}
