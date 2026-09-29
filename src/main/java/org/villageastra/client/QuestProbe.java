package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import org.villageastra.VillageAstra;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-040: the village posts a clearing quest by itself; the player takes it on the hall board and completes it with real attacks. Fixture: food in the hall chest, monsters and a sword. */
final class QuestProbe {
 private static int phase,ticks,attacks;private static volatile String failure,progress="";private static volatile UUID quest;private static volatile boolean taken,done;private static volatile BlockPos lectern;private static final List<UUID> MONSTERS=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.questSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-quest-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_QUEST screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 /** The hall's office lectern wherever the village stands (a flat or a natural world): the lectern the station map calls section 0. */
 private static BlockPos lectern(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e){
  for(var pos:BlockPos.betweenClosed(e.center().offset(-16,-4,-16),e.center().offset(16,12,16)))
   if(l.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LECTERN)&&BuildingInteractions.station(l,pos)==0)return pos.immutable();
  return null;
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>9000)throw new IllegalStateException("Quest timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{
    var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);
    // A guest really brought home and waiting for a bed, so the office's guest strip has somebody to show.
    var guest=VillageAstra.RESIDENT.get().create(l);guest.setCustomName(net.minecraft.network.chat.Component.literal("Гостья"));
    guest.moveTo(e.center().getX()+3.5,e.center().getY()+1,e.center().getZ()+3.5,0,0);guest.setNoAi(true);l.addFreshEntity(guest);
    Camps.arrived(l,e,guest,SettlementData.get(s).clock().ticks());
    // The smoke world is created peaceful; hostile quests need real monsters, so the fixture raises the difficulty.
    s.setDifficulty(net.minecraft.world.Difficulty.EASY,true);
    var chest=(Container)l.getBlockEntity(LogisticsRoutes.position(e,Workshops.hall(e)));for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).isEmpty()){chest.setItem(i,new ItemStack(Items.BREAD,64));break;}
    for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc)npc.setNoAi(true);
    for(int i=0;i<9;i++){var z=EntityType.ZOMBIE.create(l);z.setNoAi(true);z.setPersistenceRequired();z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));z.moveTo(e.center().getX()+6+i*2,e.center().getY()+1,e.center().getZ()+14,0,0);l.addFreshEntity(z);MONSTERS.add(z.getUUID());}
    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));
    p.teleportTo(l,e.center().getX()+2.5,e.center().getY()+1,e.center().getZ()+4.5,0,0);
    LogUtils.getLogger().info("ASTRA_QUEST fixture: hall food topped up, six monsters near the village, sword in hand");}catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&ticks%40==0){mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var e=entry(s);
    for(var raw:Quests.board(s.overworld(),e.settlement().id()).getList("quests",10)){var q=(net.minecraft.nbt.CompoundTag)raw;if(q.getString("state").equals(Quests.OPEN)&&q.getString("template").equals(Quests.CLEARING))quest=q.getUUID("id");}
    int alive=(int)MONSTERS.stream().filter(id->s.overworld().getEntity(id)!=null&&s.overworld().getEntity(id).isAlive()).count();
    long near=s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(e.center()).inflate(48),m->m instanceof net.minecraft.world.entity.monster.Enemy&&m.isAlive()).size();
    var board=Quests.board(s.overworld(),e.settlement().id()).getList("quests",10);var states=new StringBuilder();for(var raw:board){var q=(net.minecraft.nbt.CompoundTag)raw;states.append(q.getString("template")).append(':').append(q.getString("state")).append(' ');}
    progress="clock="+SettlementData.get(s).clock().ticks()+" quest="+quest+" monsters="+alive+"/"+near+" board=["+states+"]";});
   if(ticks%200==0)LogUtils.getLogger().info("ASTRA_QUEST waiting for the board: {}",progress);
   if(quest!=null){LogUtils.getLogger().info("ASTRA_QUEST village posted a clearing quest by itself: {}",quest);mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);var e=entry(s);lectern=lectern(s.overworld(),e);p.teleportTo(s.overworld(),e.center().getX()+2.5,e.center().getY()+1,e.center().getZ()+4.5,0,10);});phase=2;ticks=0;}
   else if(ticks>6000)throw new IllegalStateException("No quest was posted: "+progress);}
  else if(phase==2&&ticks>20){if(lectern==null)throw new IllegalStateException("No office lectern in the town hall");mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(lectern),net.minecraft.core.Direction.UP,lectern,false));phase=3;ticks=0;}
  else if(phase==3&&mc.screen instanceof ConstructionScreen screen&&ticks>30){// The tab is found by its own label, so the probe keeps working however many tabs the office grows.
   var label=Component.translatable("hall.villageastra.tab_4");
   var tabButton=screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().equals(label)).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElse(null);
   if(tabButton==null)throw new IllegalStateException("No quest tab on the office screen");
   int tab=tabButton.getX()+tabButton.getWidth()/2,row=tabButton.getY()+tabButton.getHeight()/2;
   if(!screen.mouseClicked(tab,row,0))throw new IllegalStateException("Quest tab missed");screen.mouseReleased(tab,row,0);phase=4;ticks=0;}
  // The office did not open: say what was clicked and what is on screen instead of waiting for the global timeout.
  else if(phase==3&&ticks>400){var at=lectern;
   throw new IllegalStateException("The hall station did not open the office: screen="+(mc.screen==null?"none":mc.screen.getClass().getSimpleName())+" block="+mc.level.getBlockState(at)+" player="+mc.player.blockPosition().toShortString()+" dist="+String.format(java.util.Locale.ROOT,"%.1f",Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(at)))));}
  else if(phase==4&&mc.screen instanceof ConstructionScreen screen&&ticks>30){
   var list=ConstructionOverlay.snapshot().getList("quests",10);if(list.isEmpty())throw new IllegalStateException("Board is empty on the client");
   // The row's own Take button, found by the quest's id and scrolled into view, clicked at its centre as the player would.
   var take=((QuestPanel)screen.panel(ConstructionScreen.QUESTS)).takeButton(quest);if(take==null||!take.active)throw new IllegalStateException("No active Take button for the quest: "+take);
   // QUEST-005: a guest waiting for a bed is on the tab too, with the hour that is left.
   var waiting=ConstructionOverlay.snapshot().getCompound("guests").getList("list",net.minecraft.nbt.Tag.TAG_COMPOUND);
   LogUtils.getLogger().info("ASTRA_QUEST guests waiting on the tab: {}",waiting);
   capture(mc,"board");double bx=take.getX()+take.getWidth()/2.0,by=take.getY()+take.getHeight()/2.0;if(!screen.mouseClicked(bx,by,0))throw new IllegalStateException("Take button missed");screen.mouseReleased(bx,by,0);phase=5;ticks=0;}
  // The quests tab of the inventory: opened where the player stands, far from any board, and showing the errand they just took.
  else if(phase==5&&ticks==5){mc.getSingleplayerServer().execute(()->{var s2=mc.getSingleplayerServer();var p=s2.getPlayerList().getPlayers().get(0);var e=entry(s2);
    p.teleportTo(s2.overworld(),e.center().getX()+120.5,e.center().getY()+1,e.center().getZ()+120.5,0,0);});}
  else if(phase==5&&ticks==25){mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));}
  else if(phase==5&&ticks==45){
   var tab=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().equals(Component.translatable("quest.villageastra.log_tab"))).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElse(null);
   if(tab==null)throw new IllegalStateException("No quests tab on the inventory");
   tab.onPress();
   if(!(mc.screen instanceof QuestLogScreen))throw new IllegalStateException("The tab did not open the quest log: "+mc.screen);}
  else if(phase==5&&ticks==75){
   var log=QuestLogScreen.snapshot();var villages=log.getList("villages",net.minecraft.nbt.Tag.TAG_COMPOUND);
   if(QuestLogScreen.count()<1)throw new IllegalStateException("The tab shows no errand of the player: "+log);
   capture(mc,"log");
   LogUtils.getLogger().info("ASTRA_QUEST the quests tab away from the village shows {} errand(s) under {} village(s): {}",QuestLogScreen.count(),villages.size(),villages);
   mc.setScreen(null);mc.getSingleplayerServer().execute(()->{var s2=mc.getSingleplayerServer();var p=s2.getPlayerList().getPlayers().get(0);var e=entry(s2);
    p.teleportTo(s2.overworld(),e.center().getX()+2.5,e.center().getY()+1,e.center().getZ()+4.5,0,0);});}
  else if(phase==5&&ticks>90){mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var q=Quests.quest(s.overworld(),entry(s).settlement().id(),quest);
    if(q!=null&&q.getString("state").equals(Quests.TAKEN)&&q.hasUUID("owner")&&q.getUUID("owner").equals(s.getPlayerList().getPlayers().get(0).getUUID()))taken=true;});
   if(taken){mc.setScreen(null);phase=6;ticks=0;}
   else if(ticks>200)throw new IllegalStateException("Quest was not taken by the client button");}
  else if(phase==6&&ticks%4==0){
   var victim=mc.level==null?null:java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(x->MONSTERS.contains(x.getUUID())&&x.isAlive()).findFirst().orElse(null);
   if(victim==null){if(ticks>2400)throw new IllegalStateException("No monsters left but the quest is not done: "+progress);return;}
   if(mc.player.distanceToSqr(victim)>9){mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.teleportTo(p.serverLevel(),victim.getX()+1.5,victim.getY(),victim.getZ(),0,0);p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,victim.position().add(0,1,0));});return;}
   mc.gameMode.attack(mc.player,victim);mc.player.swing(InteractionHand.MAIN_HAND);attacks++;
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var q=Quests.quest(s.overworld(),entry(s).settlement().id(),quest);progress="attacks="+attacks+" quest="+(q==null?"none":q.getString("state")+" "+q.getInt("progress")+"/"+q.getInt("target"));
     if(q!=null&&q.getString("state").equals(Quests.DONE)){var p=s.getPlayerList().getPlayers().get(0);int coins=p.getInventory().countItem(VillageAstra.ZINDBO.get());long rep=PropertyLedger.get(s).roll(entry(s).settlement().id()).account(p.getUUID()).score();
      if(coins!=q.getLong("coins")||rep<q.getLong("reputation"))failure="Reward mismatch coins="+coins+" rep="+rep+" quest="+q;else done=true;}});
   if(ticks%100==0)LogUtils.getLogger().info("ASTRA_QUEST progress {}",progress);
   if(done){capture(mc,"done");LogUtils.getLogger().info("ASTRA_QUEST VERIFIED the village posted a clearing quest, the board button took it and {} real attacks completed it with the exact reward; {}; reload=false",attacks,progress);mc.stop();phase=7;}}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_QUEST FAILED",ex);mc.stop();}}
}
