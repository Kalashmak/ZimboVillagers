package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-053/AD-058: a quarry is a building — the miner comes to it, opens the chunk it stands in and really works it out, block by block, into the quarry's stock. */
final class QuarryProbe {
 static final int WANTED=4;
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready;
 private static volatile long chunk;private static volatile int stocked;private static volatile java.util.UUID quarryId;
 static boolean enabled(){return Boolean.getBoolean("villageastra.quarrySmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-quarry-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_QUARRY screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 /** Fixture: a mine with its stock, a miner living in the village, and a bare stone field two chunks away. */
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  settlement.appointPlayerMayor(p.getUUID());
  var mine=new Settlement.Building(Settlement.childId(settlement.id(),"building/mine-quarry"),"mine",18,0,6);settlement.addBuilding(mine);
  var chestPos=LogisticsRoutes.position(e,mine);l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  var quarters=new Settlement.Home(Settlement.childId(settlement.id(),"home/miner-quarry"),1,1,true);settlement.addHome(quarters);
  var r=new Resident(java.util.UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(r,quarters.id());
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(settlement.id(),settlement.resident(r.id()));
  npc.moveTo(chestPos.getX()+1.5,chestPos.getY(),chestPos.getZ()+1.5,0,0);l.addFreshEntity(npc);
  settlement.assign(r.id(),Profession.MINER,mine.id());
  // A bare stone field the quarry can work: two chunks from the hall, clear of roads and buildings.
  var field=new ChunkPos(e.center().offset(34,0,34));chunk=field.toLong();
  l.getChunk(field.x,field.z);
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){
   var top=new BlockPos(field.getMinBlockX()+dx,e.center().getY(),field.getMinBlockZ()+dz);
   l.setBlock(top,Blocks.STONE.defaultBlockState(),3);
   for(int y=1;y<5;y++)l.setBlock(top.above(y),Blocks.AIR.defaultBlockState(),3);}
  // The quarry building stands in one corner of the field, with its stock.
  var base=new BlockPos(field.getMinBlockX()+1,e.center().getY(),field.getMinBlockZ()+1);var offset=base.subtract(e.center());
  var quarry=new Settlement.Building(Settlement.childId(settlement.id(),"building/quarry-probe"),Quarry.BUILDING,offset.getX(),offset.getY(),offset.getZ());settlement.addBuilding(quarry);quarryId=quarry.id();
  for(var cell:BuildingBlueprints.layout(Quarry.BUILDING,base).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  Quarry.clear(l,settlement.id());SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_QUARRY fixture mine, miner and a stone field at chunk {}, {}",field.x,field.z);ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Quarry timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>40){phase=2;ticks=0;}
  else if(phase==2&&ticks%40==0){
   // Nobody orders anything: the miner, looking for work, opens the chunk of the quarry building.
   if(ticks>2400)throw new IllegalStateException("The miner never opened the quarry");
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var e=entry(s);
    var record=Quarry.record(s.overworld(),e.settlement().id());
    if(record==null||!record.hasUUID("building"))return;
    if(!record.getUUID("building").equals(quarryId)||record.getLong("chunk")!=chunk){failure="The quarry opened another chunk: "+record;return;}
    phase=3;
    LogUtils.getLogger().info("ASTRA_QUARRY claim {} refuse={} surface={}",record,Quarry.refuses(s.overworld(),e,new ChunkPos(chunk)),
      s.overworld().getBlockState(new BlockPos(new ChunkPos(chunk).getMinBlockX()+1,record.getInt("layer"),new ChunkPos(chunk).getMinBlockZ()+1)));});}
  else if(phase==3&&ticks%100==0){
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var e=entry(s);var l=s.overworld();
    int taken=Quarry.taken(l,e.settlement().id());
    var mine=e.settlement().buildings().stream().filter(b->b.id().equals(quarryId)).findFirst().orElse(null);
    var chest=mine==null?null:LogisticsRoutes.chest(l,e,mine);
    int count=0;if(chest!=null)for(int slot=0;slot<chest.getContainerSize();slot++)count+=chest.getItem(slot).getCount();
    stocked=count;
    var worker=e.settlement().residents().stream().filter(x->x.profession()==Profession.MINER).findFirst().map(x->l.getEntity(x.id())).orElse(null);
    progress="taken="+taken+" stocked="+stocked+" next="+(Quarry.next(l,e)==null?"none":Quarry.next(l,e).toShortString())
      +" miner="+(worker instanceof ResidentEntity npc?npc.workStatus()+" at "+npc.blockPosition().toShortString()+" goals="+npc.runningGoals()+" custody="+CargoCustody.pending(s,npc.getUUID()):"none");
    if(taken>=WANTED&&stocked>0)phase=4;});
   LogUtils.getLogger().info("ASTRA_QUARRY progress {}",progress);
   if(ticks>9000)throw new IllegalStateException("The miner never worked the quarry: "+progress);}
  else if(phase==4&&ticks>20){
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);
    var field=new ChunkPos(chunk);int air=0;
    for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)
     if(dx>=12||dz>=12)if(l.getBlockState(new BlockPos(field.getMinBlockX()+dx,e.center().getY(),field.getMinBlockZ()+dz)).isAir())air++;
    if(air<WANTED)failure="The quarry blocks did not leave the world: "+air;
    else progress+=" emptied="+air;});
   phase=5;ticks=0;}
  else if(phase==5&&ticks>20){
   capture(mc,"pit");
   LogUtils.getLogger().info("ASTRA_QUARRY VERIFIED the quarry building opened its chunk and the miner worked it out into the quarry stock: {}; reload=false",progress);
   mc.setScreen(null);mc.stop();phase=6;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_QUARRY FAILED",ex);mc.stop();}}
}
