package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-057: the mayor gives orders on the map with real clicks — a house on a flat site, a clearing of a hill that splits soil to builders
 *  and stone to miners above the settlement floor, and a road whose dry run arrives before the order. */
final class MapToolsProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,checked;
 private static volatile BlockPos base,site,plotA,plotB,roadA,roadB;private static volatile java.util.Set<Long> needed=java.util.Set.of();private static volatile int surveyedNeeded,surveyedAll;
 private static volatile int opsSoft,opsStone,cellsStone,cellsSoft,floor;private static volatile boolean houseQueued;
 static boolean enabled(){return Boolean.getBoolean("villageastra.mapToolsSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-maptools-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_MAPTOOLS screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static void click(AtlasScreen screen,double x,double y){screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);}
 private static void press(AtlasScreen screen,String name){var at=screen.buttonCenter(name);if(at==null)throw new IllegalStateException("No button "+name);
  var b=screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button bt&&bt.isMouseOver(at[0],at[1])&&bt.visible).findFirst();
  if(b.isEmpty())throw new IllegalStateException("Button "+name+" is not shown");click(screen,at[0],at[1]);}
 private static void pick(AtlasScreen screen,BlockPos top){screen.lookAt(top);var at=screen.screenOf(top);click(screen,at[0],at[1]);}
 private static void layer(net.minecraft.server.level.ServerLevel l,int x0,int x1,int z0,int z1,int y,net.minecraft.world.level.block.state.BlockState state){
  for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)l.setBlock(new BlockPos(x,y,z),state,2);}
 /** The land is prepared before the cartographer walks it, so the map shows exactly what the orders will find. */
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);var c=e.center();
  settlement.appointPlayerMayor(p.getUUID());
  // AD-077: a gravel road takes its research. The village has done it, so the road's answer is about the busy crew, not the books.
  var research=org.villageastra.server.BookResearch.inspect(l,e);var done=research.getList("legacyDone",net.minecraft.nbt.Tag.TAG_STRING);
  for(var id:ResearchGate.forRoad(1<<2))done.add(net.minecraft.nbt.StringTag.valueOf(id));research.put("legacyDone",done);org.villageastra.server.BookResearch.store(l,e,research);
  base=Expeditions.surface(l,c.getX()-30,c.getZ()+8,c.getY()+8,4,16);int y=base.getY();
  var air=Blocks.AIR.defaultBlockState();var stone=Blocks.STONE.defaultBlockState();var grass=Blocks.GRASS_BLOCK.defaultBlockState();var dirt=Blocks.DIRT.defaultBlockState();
  // A flat house site, a small hill of soil over stone with one ore and one log, and a flat grass strip for a road.
  int hx=c.getX()-30,hz=c.getZ()+8;
  for(int yy=y-3;yy<y;yy++)layer(l,hx-2,hx+8,hz-2,hz+8,yy,stone);layer(l,hx-2,hx+8,hz-2,hz+8,y,grass);for(int yy=y+1;yy<=y+12;yy++)layer(l,hx-2,hx+8,hz-2,hz+8,yy,air);
  site=new BlockPos(hx,y,hz);
  int px=c.getX()-30,pz=c.getZ()-16;
  for(int yy=y-4;yy<y-1;yy++)layer(l,px-2,px+6,pz-2,pz+6,yy,stone);layer(l,px-2,px+6,pz-2,pz+6,y-1,dirt);layer(l,px-2,px+6,pz-2,pz+6,y,grass);
  for(int yy=y+1;yy<=y+12;yy++)layer(l,px-2,px+6,pz-2,pz+6,yy,air);
  layer(l,px,px+4,pz,pz+4,y,dirt);layer(l,px,px+4,pz,pz+4,y+1,dirt);layer(l,px,px+4,pz,pz+4,y+2,grass);
  l.setBlock(new BlockPos(px+1,y-2,pz+1),Blocks.IRON_ORE.defaultBlockState(),2);l.setBlock(new BlockPos(px+2,y+3,pz+2),Blocks.OAK_LOG.defaultBlockState(),2);
  plotA=new BlockPos(px,y+2,pz);plotB=new BlockPos(px+4,y+2,pz+4);
  int rx=c.getX()-31,rz=c.getZ()+20;
  for(int yy=y-3;yy<y;yy++)layer(l,rx-1,rx+13,rz-3,rz+3,yy,stone);layer(l,rx-1,rx+13,rz-3,rz+3,y,grass);for(int yy=y+1;yy<=y+6;yy++)layer(l,rx-1,rx+13,rz-3,rz+3,yy,air);
  roadA=new BlockPos(rx+1,y,rz);roadB=new BlockPos(rx+11,y,rz);
  needed=new java.util.HashSet<>(java.util.List.of(new ChunkPos(site).toLong(),new ChunkPos(plotA).toLong(),new ChunkPos(plotB).toLong(),new ChunkPos(roadA).toLong(),new ChunkPos(roadB).toLong()));
  // A cartographer with paper, and a miner with a mine, so stone has somebody to go to.
  var office=new Settlement.Building(Settlement.childId(settlement.id(),"building/cartographer-tools"),"cartographer",-44,0,-4);settlement.addBuilding(office);
  var chestPos=LogisticsRoutes.position(e,office);l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  LogisticsRoutes.chest(l,e,office).setItem(0,new ItemStack(Items.PAPER,48));
  var home=new Settlement.Home(Settlement.childId(settlement.id(),"home/cartographer-tools"),1,1,true);settlement.addHome(home);
  var r=new Resident(java.util.UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(r,home.id());
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(settlement.id(),settlement.resident(r.id()));npc.moveTo(chestPos.getX()+1.5,chestPos.getY(),chestPos.getZ()+1.5,0,0);l.addFreshEntity(npc);
  settlement.assign(r.id(),Profession.CARTOGRAPHER,office.id());
  var mine=new Settlement.Building(Settlement.childId(settlement.id(),"building/mine-tools"),"mine",-20,0,-40);settlement.addBuilding(mine);
  var minerHome=new Settlement.Home(Settlement.childId(settlement.id(),"home/miner-tools"),1,1,true);settlement.addHome(minerHome);
  var miner=new Resident(java.util.UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(miner,minerHome.id());settlement.assign(miner.id(),Profession.MINER,mine.id());
  SettlementData.get(s).setDirty();
  LogUtils.getLogger().info("ASTRA_MAPTOOLS fixture site {} hill {}..{} road {}..{}",site.toShortString(),plotA.toShortString(),plotB.toShortString(),roadA.toShortString(),roadB.toShortString());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>16000)throw new IllegalStateException("Map tools timeout phase="+phase+" "+progress);
  var server=mc.getSingleplayerServer();
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%100==0){
   server.execute(()->{var e=entry(server);var done=Atlas.surveyed(Atlas.inspect(server.overworld(),e.settlement().id()));surveyedNeeded=(int)needed.stream().filter(done::contains).count();surveyedAll=done.size();});
   progress="surveyed needed="+surveyedNeeded+"/"+needed.size()+" all="+surveyedAll;
   if(ticks%1000==0)LogUtils.getLogger().info("ASTRA_MAPTOOLS progress {}",progress);
   if(surveyedNeeded>=needed.size()){
    server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);var c=entry(server).center();p.teleportTo(p.serverLevel(),c.getX()+.5,c.getY()+2,c.getZ()+.5,0,0);});
    mc.setScreen(new AtlasScreen());phase=2;ticks=0;}}
  // House: the tool, a click on the flat site, the estimate, the order.
  else if(phase==2&&mc.screen instanceof AtlasScreen screen&&ticks>80){
   if(AtlasScreen.opened()<needed.size()){if(ticks>800)throw new IllegalStateException("Client received "+AtlasScreen.opened()+" chunks");return;}
   press(screen,"tool_house");pick(screen,site);phase=3;ticks=0;}
  else if(phase==3&&mc.screen instanceof AtlasScreen screen&&ticks>30){
   var plan=AtlasScreen.plan();
   if(!plan.contains("origin")){if(ticks>200)throw new IllegalStateException("No estimate for the clicked site");return;}
   if(!plan.getBoolean("ok"))throw new IllegalStateException("The flat site is not orderable: "+plan.getString("reason")+" conflicts="+plan.getInt("conflictCount")+" at "+BlockPos.of(plan.getLong("origin")).toShortString());
   capture(mc,"house");
   // A07-VIS-004: the floor goes up a block and back, then the plan is seen «after» in real blocks.
   press(screen,"floor_up");phase=31;ticks=0;}
  else if(phase==31&&mc.screen instanceof AtlasScreen screen&&ticks>20){
   var plan=AtlasScreen.plan();
   if(BlockPos.of(plan.getLong("origin")).getY()!=site.getY()+1){if(ticks>200)throw new IllegalStateException("Raising the floor did not move the plan: "+BlockPos.of(plan.getLong("origin")).toShortString());return;}
   progress+=" floor+1="+BlockPos.of(plan.getLong("origin")).getY();press(screen,"floor_down");phase=32;ticks=0;}
  else if(phase==32&&mc.screen instanceof AtlasScreen screen&&ticks>20){
   var plan=AtlasScreen.plan();
   if(BlockPos.of(plan.getLong("origin")).getY()!=site.getY()||!plan.getBoolean("ok")){if(ticks>200)throw new IllegalStateException("The floor did not come back to the ground");return;}
   press(screen,"after");screen.lookAt(site.offset(3,0,3));phase=33;ticks=0;}
  else if(phase==33&&mc.screen instanceof AtlasScreen screen&&ticks>20){
   if(!AtlasScreen.after()||AtlasScreen.futureQuads<=0)throw new IllegalStateException("The plan is not drawn after building: "+AtlasScreen.futureQuads);
   progress+=" after="+AtlasScreen.futureQuads;capture(mc,"house-after");press(screen,"after");press(screen,"order");phase=4;ticks=0;}
  else if(phase==4&&mc.screen instanceof AtlasScreen screen&&ticks>20){
   var outcome=AtlasScreen.outcome();
   if(outcome.isEmpty()){if(ticks>200)throw new IllegalStateException("No answer to the house order");return;}
   if(!outcome.getBoolean("ordered"))throw new IllegalStateException("The house order was refused: "+outcome.getString("reason"));
   server.execute(()->houseQueued=HallUpgradeGoal.pending(server.overworld(),entry(server).settlement().id()));
   progress+=" house=ordered";
   // Clearing: two corners on the hill, the kept level lowered to take the soil and the top stone, the dry run, the order.
   press(screen,"tool_demolish");pick(screen,plotA);pick(screen,plotB);
   if(AtlasScreen.corner(true)==null||AtlasScreen.corner(false)==null)throw new IllegalStateException("The clicks did not mark both corners");
   for(int i=0;i<5;i++)press(screen,"level_down");
   phase=5;ticks=0;}
  else if(phase==5&&mc.screen instanceof AtlasScreen screen&&ticks>30){
   if(!houseQueued)throw new IllegalStateException("The ordered house is not on the builders' queue");
   var preview=AtlasScreen.preview();
   if(preview.isEmpty()){if(ticks>300)throw new IllegalStateException("No dry run for the clearing");return;}
   if(!preview.getString("reason").isEmpty())throw new IllegalStateException("The clearing is refused: "+preview.getString("reason"));
   if(preview.getInt("builders")<=0||preview.getInt("miners")<=0)throw new IllegalStateException("The clearing does not split soil and stone: "+preview);
   screen.lookAt(plotA.offset(2,0,2));
   progress+=" clearing builders="+preview.getInt("builders")+" miners="+preview.getInt("miners")+" level="+AtlasScreen.level()+" floor="+preview.getInt("floor");
   phase=51;ticks=0;}
  else if(phase==51&&mc.screen instanceof AtlasScreen screen&&ticks>10){capture(mc,"demolish");press(screen,"order");phase=6;ticks=0;}
  else if(phase==6&&mc.screen instanceof AtlasScreen screen&&ticks>20){
   var outcome=AtlasScreen.outcome();
   if(outcome.isEmpty()||outcome.getInt("tool")!=MapOrders.DEMOLISH){if(ticks>200)throw new IllegalStateException("No answer to the clearing order");return;}
   if(!outcome.getBoolean("ordered"))throw new IllegalStateException("The clearing order was refused: "+outcome.getString("reason"));
   checked=false;
   server.execute(()->{try{var l=server.overworld();var e=entry(server);var id=e.settlement().id();
    var project=Roads.project(l,id);var dig=Excavation.record(l,id);
    if(project==null||!project.getString("kind").equals("clearing"))throw new IllegalStateException("No clearing project for the builders");
    if(dig==null)throw new IllegalStateException("No excavation for the miners");
    floor=MapOrders.floor(e);int soft=0,stone=0,deep=0;
    for(var raw:project.getList("ops",Tag.TAG_COMPOUND)){var op=(CompoundTag)raw;var before=NbtUtils.readBlockState(l.holderLookup(net.minecraft.core.registries.Registries.BLOCK),op.getCompound("before"));
     if(MapOrders.minersOnly(before))stone++;else soft++;if(BlockPos.of(op.getLong("pos")).getY()<floor)deep++;}
    int cellsSoftCount=0,cellsStoneCount=0;
    for(long raw:dig.getLongArray("cells")){var pos=BlockPos.of(raw);if(MapOrders.minersOnly(l.getBlockState(pos)))cellsStoneCount++;else cellsSoftCount++;if(pos.getY()<floor)deep++;}
    if(deep>0)throw new IllegalStateException("A cell under the settlement floor was ordered: "+deep);
    opsSoft=soft;opsStone=stone;cellsStone=cellsStoneCount;cellsSoft=cellsSoftCount;checked=true;
   }catch(Exception ex){failure=ex.toString();}});
   phase=7;ticks=0;}
  else if(phase==7&&checked&&mc.screen instanceof AtlasScreen screen){
   if(opsStone!=0||opsSoft<=0)throw new IllegalStateException("The builders were given stone: soft="+opsSoft+" stone="+opsStone);
   if(cellsSoft!=0||cellsStone<=0)throw new IllegalStateException("The miners were given soft blocks: stone="+cellsStone+" soft="+cellsSoft);
   progress+=" project soft="+opsSoft+" stone="+opsStone+" excavation stone="+cellsStone+" soft="+cellsSoft;
   // Road: two ends on the grass strip, gravel, the dry run with its cells; the builders are busy with the clearing, so the order is answered honestly.
   press(screen,"tool_road");pick(screen,roadA);pick(screen,roadB);press(screen,"surface");
   phase=8;ticks=0;}
  else if(phase==8&&mc.screen instanceof AtlasScreen screen&&ticks>30){
   var preview=AtlasScreen.preview();
   if(preview.isEmpty()){if(ticks>300)throw new IllegalStateException("No dry run for the road");return;}
   if(preview.getLongArray("roadCells").length==0)throw new IllegalStateException("The road dry run has no cells: "+preview.getString("reason"));
   screen.lookAt(roadA.offset(5,0,0));
   progress+=" road cells="+preview.getLongArray("roadCells").length+" blocked="+preview.getLongArray("blocked").length+" busy="+preview.getBoolean("busyBuilders");
   phase=81;ticks=0;}
  else if(phase==81&&mc.screen instanceof AtlasScreen screen&&ticks>10){capture(mc,"road");press(screen,"order");phase=9;ticks=0;}
  else if(phase==9&&mc.screen instanceof AtlasScreen screen&&ticks>20){
   var outcome=AtlasScreen.outcome();
   if(outcome.isEmpty()||outcome.getInt("tool")!=MapOrders.ROAD){if(ticks>200)throw new IllegalStateException("No answer to the road order");return;}
   String answer=outcome.getBoolean("ordered")?"ordered":outcome.getString("reason");
   if(!answer.equals("busy_builders"))throw new IllegalStateException("With the builders on the clearing the road must wait, got: "+answer);
   progress+=" road="+answer;
   // AD-094: the wall tool — its dry run for the default square, then the round one, drawn over the ground the map knows.
   press(screen,"tool_wall");phase=10;ticks=0;}
  else if(phase==10&&mc.screen instanceof AtlasScreen screen&&ticks>30){
   var preview=AtlasScreen.preview();
   if(preview.isEmpty()||preview.getInt("tool")!=MapOrders.WALL){if(ticks>300)throw new IllegalStateException("No dry run for the wall");return;}
   if(preview.getInt("shape")!=0||preview.getInt("radius")<Walls.MIN_RADIUS)throw new IllegalStateException("The wall tool opens on a square of its own radius: "+preview);
   progress+=" wall square r="+preview.getInt("radius")+" gates="+preview.getInt("gates")+" towers="+preview.getInt("towers")+" reason="+preview.getString("reason");
   press(screen,"wall_shape");phase=11;ticks=0;}
  else if(phase==11&&mc.screen instanceof AtlasScreen screen&&ticks>30){
   var preview=AtlasScreen.preview();
   if(preview.isEmpty()||preview.getInt("tool")!=MapOrders.WALL||preview.getInt("shape")!=1){if(ticks>300)throw new IllegalStateException("The round wall was never previewed");return;}
   if(preview.getLongArray("towerCells").length<4)throw new IllegalStateException("A round wall has at least four tower sites: "+preview);
   progress+=" wall=round towers="+preview.getLongArray("towerCells").length+" gates="+preview.getLongArray("gateCells").length+" reason="+preview.getString("reason");
   screen.overview(entry(server).center(),3F);phase=12;ticks=0;}
  else if(phase==12&&mc.screen instanceof AtlasScreen screen&&ticks>20){
   capture(mc,"wall");
   LogUtils.getLogger().info("ASTRA_MAPTOOLS VERIFIED map orders: {}; reload=false",progress);
   mc.setScreen(null);mc.stop();phase=13;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_MAPTOOLS FAILED",ex);mc.stop();}}
}
