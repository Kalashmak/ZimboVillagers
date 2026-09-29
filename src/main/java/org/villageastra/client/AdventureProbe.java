package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-075: the adventure sites really stand in a generated world, and two of them are played through the notice board — a wounded
 *  survivor is treated by hand, and the bandit stockade gives up its captive only after the bars fall and the chief is cut down. */
final class AdventureProbe {
 private static final String[] KINDS={QuestSites.ADIT,QuestSites.BARROW};
 private static int phase,ticks,built,sector,attacks,settle;
 private static volatile String failure,progress="",fit="";
 private static volatile UUID quest,captive,chief,rescue,wounded;
 private static final java.util.List<UUID> FOES=java.util.Collections.synchronizedList(new ArrayList<>());
 private static volatile boolean ready,posted,cageOpen,chiefDown,following,paid,treated,shotWreck,shotStockade,shotChart;
 private static volatile EscortLeader leader;
 static boolean enabled(){return Boolean.getBoolean("villageastra.adventureSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{
  var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-adventure-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}
  LogUtils.getLogger().info("ASTRA_ADVENTURE screenshot {}",path);
 }
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static QuestSites.Order order(String kind,UUID id,int y){
  return kind.equals(QuestSites.ADIT)
   ?new QuestSites.Order(kind,0,0,14,y-7<0?Blocks.DEEPSLATE_IRON_ORE:Blocks.IRON_ORE,ItemStack.EMPTY,ItemStack.EMPTY)
   :new QuestSites.Order(kind,0,3,0,Blocks.AIR,ItemStack.EMPTY,Adventures.relic(id));
 }
 private static int count(net.minecraft.server.level.ServerLevel l,BlockPos o,int half,int from,int to,net.minecraft.world.level.block.Block block){
  int found=0;
  for(int dx=-half;dx<=half;dx++)for(int dz=-half;dz<=half;dz++)for(int dy=from;dy<=to;dy++)
   if(l.getBlockState(o.offset(dx,dy,dz)).is(block))found++;
  return found;
 }
 /** Walks the player out to a sector and waits for its chunks; true once the ground there is real. */
 private static boolean atSector(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,net.minecraft.server.level.ServerPlayer p,int at){
  var target=Expeditions.target(l,e,at%Expeditions.SECTORS);
  if(p.distanceToSqr(target.getX(),target.getY(),target.getZ())>256){
   p.teleportTo(l,target.getX()+.5,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,target.getX(),target.getZ()),target.getZ()+.5,0,0);
   settle=0;progress="walking out to sector "+at%Expeditions.SECTORS;return false;}
  if(++settle<3){progress="waiting for the chunks of sector "+at%Expeditions.SECTORS;return false;}
  settle=0;return true;
 }
 /** Posts one adventure the way the village does it: the player looks around the sector for ground worth reporting, as a scout would. */
 private static net.minecraft.nbt.CompoundTag postFrom(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,net.minecraft.server.level.ServerPlayer p,String template,int at){
  var office=e.settlement().buildings().stream().filter(b->b.type().equals("expedition")).findFirst().orElseThrow();
  var now=SettlementData.get(l.getServer()).clock().ticks();
  BlockPos spot=null;
  for(int radius=0;radius<=16&&spot==null;radius+=8)for(int step=0;step<8&&spot==null;step++){
   if(radius==0&&step>0)break;
   double angle=Math.PI*2*step/8;
   int x=p.blockPosition().getX()+(int)Math.round(Math.cos(angle)*radius),z=p.blockPosition().getZ()+(int)Math.round(Math.sin(angle)*radius);
   if(!l.hasChunkAt(new BlockPos(x,0,z)))continue;
   var ground=Expeditions.surface(l,x,z,p.blockPosition().getY(),4,6);
   if(ground!=null&&!Expeditions.inspect(l,ground.above()).isEmpty())spot=ground.above();
  }
  if(spot==null){progress="no ground worth reporting near sector "+at%Expeditions.SECTORS;return null;}
  p.teleportTo(l,spot.getX()+.5,spot.getY(),spot.getZ()+.5,0,0);
  if(Expeditions.report(l,e,office,spot,at%Expeditions.SECTORS,now)==null){progress="nothing to report in sector "+at%Expeditions.SECTORS;return null;}
  var made=Adventures.post(l,e,template,now);
  if(made==null)progress="no "+template+" at that lead";
  return made;
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);
  if(++ticks>24000)throw new IllegalStateException("Adventure timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{
    var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);
    // The adventures stand on fighting, so the smoke world is taken off peaceful, as the quest probe does.
    s.setDifficulty(net.minecraft.world.Difficulty.EASY,true);
    // Daylight for the whole run: the bandits of the stockade are the danger under test, not the night.
    l.setDayTime(1000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);
    var office=new Settlement.Building(Settlement.childId(e.settlement().id(),"building/expedition-adventure"),"expedition",-30,0,12);e.settlement().addBuilding(office);
    var chestPos=LogisticsRoutes.position(e,office);l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
    var chest=(Container)l.getBlockEntity(chestPos);chest.setItem(0,new ItemStack(Items.PAPER,16));chest.setItem(1,new ItemStack(Items.BREAD,16));
    for(var x:e.settlement().residents())if(l.getEntity(x.id()) instanceof ResidentEntity other)other.setNoAi(true);
    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();
    p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
    p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
    p.getInventory().add(new ItemStack(Items.DIAMOND_PICKAXE));p.getInventory().add(new ItemStack(VillageAstra.BANDAGE.get(),4));
    p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));
    SettlementData.get(s).setDirty();
    LogUtils.getLogger().info("ASTRA_ADVENTURE fixture: expedition office with supplies, easy difficulty, armour, pickaxe, sword and four bandages");
    ready=true;}catch(Exception ex){failure=ex.toString();}});}
  // The mine and the barrow are put up at scouted sectors to see the designs stand in real terrain.
  else if(phase==1&&ready&&ticks%20==0){
   final String kind=KINDS[Math.min(built,KINDS.length-1)];final int at=sector;
   mc.getSingleplayerServer().execute(()->{try{
    var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);
    if(!atSector(l,e,p,at))return;
    var id=UUID.randomUUID();var site=QuestSites.build(l,e,id,order(kind,id,p.blockPosition().getY()),p.blockPosition(),SettlementData.get(s).clock().ticks());
    if(site==null){fit+=kind+"@"+at+"=no-room ";sector=at+1;progress="no room for the "+kind+" in sector "+at;return;}
    var o=QuestSites.origin(site);int half=QuestSites.half(kind);
    String checked=kind.equals(QuestSites.ADIT)
     ?"logs="+count(l,o,half,0,4,Blocks.SPRUCE_LOG)+" ladders="+count(l,o,half,-7,0,Blocks.LADDER)+" seam="+QuestSites.seamLeft(l,site)
     :"mound="+count(l,o,half,0,1,Blocks.MOSSY_COBBLESTONE)+" seal="+count(l,o,half,0,1,Blocks.CRACKED_STONE_BRICKS)+" sealed="+!QuestSites.sealBroken(l,site);
    if(kind.equals(QuestSites.ADIT)&&(QuestSites.seamLeft(l,site)<10||count(l,o,half,-7,0,Blocks.LADDER)<5)){failure="The adit came out without its seam or its ladder: "+checked;return;}
    if(kind.equals(QuestSites.BARROW)&&(QuestSites.sealBroken(l,site)||count(l,o,half,0,1,Blocks.CRACKED_STONE_BRICKS)!=2)){failure="The barrow is not sealed: "+checked;return;}
    fit+=kind+"@"+at+"=ok ";progress=kind+" at "+o.toShortString()+" "+checked;built++;sector=at+1;
    LogUtils.getLogger().info("ASTRA_ADVENTURE built the {} in real ground: {}",kind,checked);
   }catch(Exception ex){failure=ex.toString();}});
   if(ticks%200==0)LogUtils.getLogger().info("ASTRA_ADVENTURE sites {} | {}",fit,progress);
   if(sector>16)throw new IllegalStateException("No room for the adventure sites: "+fit);
   if(built>=KINDS.length){phase=2;ticks=0;}}
  // The wrecked expedition comes off the board: the village posts it and the player takes it on.
  else if(phase==2&&ticks%20==0){mc.getSingleplayerServer().execute(()->{try{
    if(posted)return;
    var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);
    if(!atSector(l,e,p,sector))return;
    var made=postFrom(l,e,p,Adventures.LOST,sector);
    if(made==null){sector++;return;}
    rescue=made.getUUID("id");var site=QuestSites.site(l,rescue);var o=QuestSites.origin(site);
    var people=QuestSites.people(site);
    if(people.isEmpty()){failure="The wreck came out without survivors";return;}
    wounded=people.get(0);
    // AD-097: the probe's adventurer has already earned the trust a rescue and a stockade ask for.
    PropertyLedger.get(s).gift(e.settlement().id(),p.getUUID(),Quests.trust(Adventures.CAPTIVE));
    if(!Quests.take(p,e.settlement().id(),rescue).equals("ok")){failure="The rescue could not be taken";return;}
    if(l.getEntity(wounded) instanceof ResidentEntity hurt){
     if(!Adventures.hold(hurt).equals("injured")){failure="A survivor should be held by their wound: "+Adventures.hold(hurt);return;}
     p.teleportTo(l,hurt.getX()+2,hurt.getY(),hurt.getZ(),0,0);p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,hurt.position().add(0,1,0));
     p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VillageAstra.BANDAGE.get(),4));
     LogUtils.getLogger().info("ASTRA_ADVENTURE the village posted the wrecked expedition at {}: survivors={} wool={}",o.toShortString(),people.size(),count(l,o,4,0,2,Blocks.WHITE_WOOL));
     posted=true;}
   }catch(Exception ex){failure=ex.toString();}});
   if(ticks%200==0)LogUtils.getLogger().info("ASTRA_ADVENTURE rescue {}",progress);
   if(posted){posted=false;phase=3;ticks=0;}
   else if(ticks>4000)throw new IllegalStateException("No wrecked expedition was posted: "+progress);}
  // One real right click with a bandage puts a survivor back on their feet.
  else if(phase==3&&ticks>20){
   var npc=mc.level==null?null:java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(x->x.getUUID().equals(wounded)).findFirst().orElse(null);
   if(npc==null){if(ticks>600)throw new IllegalStateException("The survivor is not visible");return;}
   if(!shotWreck){capture(mc,"wreck");shotWreck=true;}
   if(ticks%10==0)mc.gameMode.interact(mc.player,npc,InteractionHand.MAIN_HAND);
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);
    if(s.overworld().getEntity(wounded) instanceof ResidentEntity hurt){
     if(!hurt.getPersistentData().getBoolean(QuestSites.INJURED)&&!hurt.isNoAi())treated=true;
     progress="hand="+p.getMainHandItem()+" hold="+Adventures.hold(hurt)+" care="+hurt.getPersistentData().getInt(QuestSites.CARE)
      +" owned="+Quests.owned(p,rescue)+" near="+String.format(Locale.ROOT,"%.1f",Math.sqrt(p.distanceToSqr(hurt)));
     if(p.distanceToSqr(hurt)>9){p.teleportTo(p.serverLevel(),hurt.getX()+1.5,hurt.getY(),hurt.getZ(),0,0);
      p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,hurt.position().add(0,1,0));
      p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VillageAstra.BANDAGE.get(),4));}}});
   if(ticks%40==0)LogUtils.getLogger().info("ASTRA_ADVENTURE treating {}",progress);
   if(treated){LogUtils.getLogger().info("ASTRA_ADVENTURE a real bandage put a survivor back on their feet");phase=4;ticks=0;}
   else if(ticks>400)throw new IllegalStateException("The bandage did not treat the survivor");}
  // The stockade also comes off the board, at the next scouted sector.
  else if(phase==4&&ticks%20==0){mc.getSingleplayerServer().execute(()->{try{
    if(posted)return;
    var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);
    if(!atSector(l,e,p,sector+1))return;
    var made=postFrom(l,e,p,Adventures.CAPTIVE,sector+1);
    if(made==null){sector++;return;}
    quest=made.getUUID("id");var site=QuestSites.site(l,quest);var o=QuestSites.origin(site);
    chief=site.hasUUID("chief")?site.getUUID("chief"):null;
    captive=QuestSites.people(site).isEmpty()?null:QuestSites.people(site).get(0);
    if(captive==null||chief==null){failure="The stockade came out without its captive or its chief";return;}
    if(!Quests.take(p,e.settlement().id(),quest).equals("ok")){failure="The stockade quest could not be taken";return;}
    var chart=p.getInventory().items.stream().filter(x->x.is(Items.FILLED_MAP)&&x.hasTag()&&x.getTag().contains("Decorations")).findFirst().orElse(ItemStack.EMPTY);
    if(chart.isEmpty()){failure="Taking the stockade handed out no chart";return;}
    LogUtils.getLogger().info("ASTRA_ADVENTURE the board drew a chart with a cross: {} {}",chart.getHoverName().getString(),chart.getTag().getList("Decorations",net.minecraft.nbt.Tag.TAG_COMPOUND));
    // The chart of this very quest is held up before the stockade: the cross on it is the place the player stands at.
    var own=p.getInventory().items.stream().filter(x->x.is(Items.FILLED_MAP)&&x.hasTag()&&x.getTag().getList("Decorations",net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
     .anyMatch(d->(int)((net.minecraft.nbt.CompoundTag)d).getDouble("x")==o.getX()&&(int)((net.minecraft.nbt.CompoundTag)d).getDouble("z")==o.getZ())).findFirst().orElse(ItemStack.EMPTY);
    if(own.isEmpty()){failure="No chart carries a cross on the stockade";return;}
    var held=own.copy();own.setCount(0);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
    p.teleportTo(l,o.getX()+7.5,o.getY(),o.getZ()+.5,90,55);p.setItemInHand(InteractionHand.MAIN_HAND,held);
    LogUtils.getLogger().info("ASTRA_ADVENTURE the chart of the stockade carries its cross at {}",o.toShortString());
    LogUtils.getLogger().info("ASTRA_ADVENTURE the village posted the stockade at {}: bars={} bandits={}",o.toShortString(),
     count(l,o,5,-1,2,Blocks.IRON_BARS),site.getList("mobs",net.minecraft.nbt.Tag.TAG_INT_ARRAY).size());
    posted=true;
   }catch(Exception ex){failure=ex.toString();}});
   if(ticks%200==0)LogUtils.getLogger().info("ASTRA_ADVENTURE stockade {}",progress);
   if(posted){phase=5;ticks=0;}
   else if(ticks>4000)throw new IllegalStateException("No stockade was posted: "+progress);}
  // The bars come down under a real pickaxe; while the chief stands, the captive still refuses to leave.
  else if(phase==5&&ticks>40){
   if(!shotChart){capture(mc,"chart");shotChart=true;
    mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
     p.getInventory().add(p.getMainHandItem().copy());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));
     p.teleportTo(p.serverLevel(),p.getX(),p.getY(),p.getZ(),90,0);});
    return;}
   if(ticks<80)return;
   if(!shotStockade){capture(mc,"stockade");shotStockade=true;}
   mc.getSingleplayerServer().execute(()->{try{
    var s=mc.getSingleplayerServer();var l=s.overworld();var p=s.getPlayerList().getPlayers().get(0);
    var site=QuestSites.site(l,quest);var bars=site.getList("cage",net.minecraft.nbt.Tag.TAG_LONG);
    if(!(l.getEntity(captive) instanceof ResidentEntity person)){failure="The captive is gone";return;}
    var column=BlockPos.of(((net.minecraft.nbt.LongTag)bars.get(0)).getAsLong());
    progress="hold="+Adventures.hold(person)+" bars="+count(l,QuestSites.origin(site),5,-1,2,Blocks.IRON_BARS);
    if(!QuestSites.cageOpened(l,site)){
     p.teleportTo(l,column.getX()+1.5,column.getY(),column.getZ()+.5,0,0);
     p.gameMode.destroyBlock(column);p.gameMode.destroyBlock(column.above());return;}
    if(!Adventures.hold(person).equals("chief")){failure="An open cage should leave only the chief in the way: "+Adventures.hold(person);return;}
    cageOpen=true;
   }catch(Exception ex){failure=ex.toString();}});
   if(cageOpen){LogUtils.getLogger().info("ASTRA_ADVENTURE the cage is broken open with a pickaxe, the chief still holds the captive: {}",progress);phase=6;ticks=0;}
   else if(ticks>800)throw new IllegalStateException("The cage did not open: "+progress);}
  // The whole band is cut down by real attacks: a captive walked out under crossbow fire never gets home.
  else if(phase==6&&ticks%4==0){
   var victim=mc.level==null?null:java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
    .filter(x->x.isAlive()&&(x.getUUID().equals(chief)||FOES.contains(x.getUUID()))).findFirst().orElse(null);
   if(victim!=null){
    if(mc.player.distanceToSqr(victim)>9){mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
      p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));p.setHealth(p.getMaxHealth());
      p.teleportTo(p.serverLevel(),victim.getX()+1.5,victim.getY(),victim.getZ(),0,0);
      p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,victim.position().add(0,1,0));});return;}
    mc.gameMode.attack(mc.player,victim);mc.player.swing(InteractionHand.MAIN_HAND);attacks++;}
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var l=s.overworld();var site=QuestSites.site(l,quest);
    if(site==null)return;
    FOES.clear();
    for(var raw:site.getList("mobs",net.minecraft.nbt.Tag.TAG_INT_ARRAY)){var id=net.minecraft.nbt.NbtUtils.loadUUID(raw);
     if(l.getEntity(id)!=null&&l.getEntity(id).isAlive())FOES.add(id);}
    if(FOES.isEmpty()&&QuestSites.chiefDown(l,site)&&l.getEntity(captive) instanceof ResidentEntity person&&Adventures.hold(person).isEmpty())chiefDown=true;});
   if(ticks%100==0)LogUtils.getLogger().info("ASTRA_ADVENTURE fight attacks={} {}",attacks,progress);
   if(chiefDown){LogUtils.getLogger().info("ASTRA_ADVENTURE the chief is down after {} real attacks and the band with him; the captive is free to walk",attacks);phase=7;ticks=0;}
   else if(ticks>4000)throw new IllegalStateException("The band did not fall: attacks="+attacks+" left="+FOES.size());}
  // A real right click and the walk home; arrival pays coin and reputation.
  else if(phase==7&&ticks>20){
   var npc=mc.level==null?null:java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(x->x.getUUID().equals(captive)).findFirst().orElse(null);
   // Between hops the captive slips out of the client view; that is a tick with nothing to click, not a failure.
   if(npc==null&&!following){if(ticks>600)throw new IllegalStateException("The captive is not visible");return;}
   if(npc!=null&&!following){
    if(ticks%10==0)mc.gameMode.interact(mc.player,npc,InteractionHand.MAIN_HAND);
    mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);
     if(s.overworld().getEntity(captive) instanceof ResidentEntity person&&p.distanceToSqr(person)>9)
      p.teleportTo(p.serverLevel(),person.getX()+1.5,person.getY(),person.getZ(),0,0);});
    final int sampleNow=ticks;
    mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();
     if(s.overworld().getEntity(captive) instanceof ResidentEntity person&&person.escortPlayer()!=null){if(leader==null)leader=new EscortLeader(sampleNow);following=true;}});
    if(ticks>400)throw new IllegalStateException("The freed captive did not join");
    return;}
   final int sample=ticks;
   if(ticks%20==0)mc.getSingleplayerServer().execute(()->{try{
    var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);
    if(!(l.getEntity(captive) instanceof ResidentEntity person)||!person.isAlive()){failure="The captive did not survive the way home";return;}
    double distance=Math.sqrt(p.distanceToSqr(e.center().getX()+.5,p.getY(),e.center().getZ()+.5));
    // AD-106: the player leads along the captive's own route home, a few steps ahead, and never fetches it.
    var why=leader.step(l,p,person,e.center(),sample);if(why!=null){failure=why;return;}
    var now=SettlementData.get(s).clock().ticks();Quests.companions(l,e,now);
    var q=Quests.quest(l,e.settlement().id(),quest);
    if(q!=null&&q.getInt("progress")>0)Quests.complete(p,e.settlement().id(),quest);
    q=Quests.quest(l,e.settlement().id(),quest);
    long coins=p.getInventory().countItem(VillageAstra.ZINDBO.get()),score=PropertyLedger.get(s).roll(e.settlement().id()).account(p.getUUID()).score();
    progress="quest="+(q==null?"none":q.getString("state")+" "+q.getInt("progress")+"/"+q.getInt("target"))+" state="+person.escortState()
     +" gap="+String.format(Locale.ROOT,"%.1f",Math.sqrt(p.distanceToSqr(person)))+" at="+person.blockPosition().toShortString()
     +" goals="+person.runningGoals()
     +" distance="+String.format(Locale.ROOT,"%.1f",distance)+" coins="+coins+" reputation="+score+" guest="+(Camps.guest(l,captive)==null?"none":Camps.guest(l,captive).getString("state"))
     +" "+leader.summary();
    if(q!=null&&q.getString("state").equals(Quests.DONE)){
     if(coins<q.getLong("coins")||score<q.getLong("reputation")){failure="Reward mismatch "+progress;return;}
     paid=true;}
   }catch(Exception ex){failure=ex.toString();}});
   if(ticks%200==0)LogUtils.getLogger().info("ASTRA_ADVENTURE escort {}",progress);
   if(ticks>12000&&!paid)throw new IllegalStateException("The walk home took over ten minutes: "+progress);
   if(paid){capture(mc,"paid");
    LogUtils.getLogger().info("ASTRA_ADVENTURE VERIFIED adventure sites in real ground: {}; a survivor treated by hand, the cage broken with a pickaxe, the chief cut down in {} attacks, the captive walked home and the village paid; {}; reload=false",fit,attacks,progress);
    mc.stop();phase=8;}}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_ADVENTURE FAILED",ex);mc.stop();}}
}
