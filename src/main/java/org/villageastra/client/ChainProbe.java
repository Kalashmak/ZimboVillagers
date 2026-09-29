package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-099: a stage of a chain is built out in real generated ground by the village's own tick once its player walks there: the war camp of
 *  the band is broken and quiets the raids, and the king's crypt goes down into real rock with its guard in the vault. */
final class ChainProbe {
 private static int phase,ticks,waited;
 private static volatile String failure,progress="";
 private static volatile UUID camp,crypt;
 private static volatile boolean ready,campBuilt,campBroken,cryptBuilt,shot;
 static boolean enabled(){return Boolean.getBoolean("villageastra.chainSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{
  var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-chain-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}
  LogUtils.getLogger().info("ASTRA_CHAIN screenshot {}",path);
 }
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static net.minecraft.server.level.ServerPlayer player(net.minecraft.server.MinecraftServer s){return s.getPlayerList().getPlayers().get(0);}
 private static int count(net.minecraft.server.level.ServerLevel l,BlockPos o,int half,int from,int to,net.minecraft.world.level.block.Block block){
  int found=0;for(int dx=-half;dx<=half;dx++)for(int dz=-half;dz<=half;dz++)for(int dy=from;dy<=to;dy++)if(l.getBlockState(o.offset(dx,dy,dz)).is(block))found++;return found;
 }
 /** A finished stage as the board keeps it: the template and where its place stood. The chain goes on from there. */
 private static CompoundTag finished(String template,BlockPos site){var t=new CompoundTag();t.putUUID("id",UUID.randomUUID());t.putString("template",template);t.putLong("site",site.asLong());return t;}
 private static void walkTo(net.minecraft.server.level.ServerLevel l,net.minecraft.server.level.ServerPlayer p,BlockPos at){
  p.teleportTo(l,at.getX()+.5,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ())+1,at.getZ()+.5,0,0);
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);
  if(++ticks>24000)throw new IllegalStateException("Chain timeout phase="+phase+" "+progress);
  var server=mc.getSingleplayerServer();
  if(phase==0&&ticks>60){phase=1;ticks=0;server.execute(()->{try{
    var l=server.overworld();var e=entry(server);var p=player(server);
    server.setDifficulty(net.minecraft.world.Difficulty.EASY,true);
    l.setDayTime(1000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);
    for(var x:e.settlement().residents())if(l.getEntity(x.id()) instanceof ResidentEntity other)other.setNoAi(true);
    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();
    p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
    p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
    // The lair was broken by this player: the trail it names is theirs.
    var lair=finished(Adventures.LAIR,Expeditions.target(l,e,0));
    var q=Chains.advance(p,e.settlement().id(),lair);if(q==null){failure="The lair named no war camp";return;}
    camp=q.getUUID("id");
    if(!Quests.take(p,e.settlement().id(),camp).equals("ok")){failure="The kept war camp could not be taken by its finder";return;}
    LogUtils.getLogger().info("ASTRA_CHAIN fixture: easy difficulty held in daylight, armour; the war camp waits {} blocks out, pending={}",Adventures.away(e.center(),BlockPos.of(q.getLong("site"))),q.getBoolean("pending"));
    ready=true;}catch(Exception ex){failure=ex.toString();}});}
  // The player walks out to where the trail points; the village's own tick builds the camp once that land is in the world.
  else if(phase==1&&ready&&ticks%20==0){server.execute(()->{try{
    if(campBuilt)return;
    var l=server.overworld();var e=entry(server);var p=player(server);var q=Quests.quest(l,e.settlement().id(),camp);
    if(q.getBoolean("pending")){walkTo(l,p,BlockPos.of(q.getLong("site")));waited++;progress="pending tries="+q.getInt("tries");return;}
    var site=QuestSites.site(l,camp);var o=QuestSites.origin(site);
    var chief=l.getEntity(site.getUUID("chief"));
    String checked="logs="+count(l,o,6,0,2,Blocks.SPRUCE_LOG)+" banners="+count(l,o,6,0,0,Blocks.RED_BANNER)+" band="+site.getList("mobs",11).size()+" warlord="+(chief==null?"none":chief.getType().toShortString());
    if(count(l,o,6,0,2,Blocks.SPRUCE_LOG)<60||site.getList("mobs",11).size()!=7||chief==null||chief.getType()!=EntityType.EVOKER){failure="The war camp did not come out whole: "+checked;return;}
    var view=QuestSites.origin(site).offset(0,7,0).relative(net.minecraft.core.Direction.byName(site.getString("facing")),13);
    p.teleportTo(l,view.getX()+.5,view.getY(),view.getZ()+.5,0,0);
    p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,net.minecraft.world.phys.Vec3.atCenterOf(o));
    LogUtils.getLogger().info("ASTRA_CHAIN the village's tick built the war camp in real ground at {} after {} walks: {}",o.toShortString(),waited,checked);campBuilt=true;
   }catch(Exception ex){failure=ex.toString();}});
   if(campBuilt){phase=2;ticks=0;}else if(ticks>3000)throw new IllegalStateException("The war camp was never built: "+progress);}
  // The chart goes back into the bag for the pictures: an empty hand leaves the view to the place itself.
  else if(phase==2&&ticks==40){mc.player.getInventory().selected=8;}
  else if(phase==2&&ticks==60&&!shot){shot=true;capture(mc,"warcamp");}
  // The band falls with its player standing by; the board then closes the camp on its own and pushes the next wave far back.
  else if(phase==2&&ticks>80&&ticks%20==0){server.execute(()->{try{
    if(campBroken)return;
    var l=server.overworld();var e=entry(server);var p=player(server);var site=QuestSites.site(l,camp);var o=QuestSites.origin(site);
    p.teleportTo(l,o.getX()+.5,o.getY()+1,o.getZ()+6.5,180,0);
    for(var raw:site.getList("mobs",11))if(l.getEntity(NbtUtils.loadUUID(raw)) instanceof LivingEntity raider&&raider.isAlive())raider.kill();
    var q=Quests.quest(l,e.settlement().id(),camp);progress="camp="+q.getString("state")+" "+q.getInt("progress")+"/"+q.getInt("target");
    if(q.getString("state").equals(Quests.DONE)){
     long next=Raids.record(l,e.settlement().id()).getLong("nextAt"),now=SettlementData.get(server).clock().ticks();
     if(next<now+(long)Quests.setting(Chains.WARCAMP,"calm")-200){failure="The broken camp did not quiet the raids: next="+next+" now="+now;return;}
     LogUtils.getLogger().info("ASTRA_CHAIN the war camp was broken and paid; the next wave waits until {} (now {})",next,now);campBroken=true;}
   }catch(Exception ex){failure=ex.toString();}});
   if(campBroken){phase=3;ticks=0;shot=false;}else if(ticks>2400)throw new IllegalStateException("The war camp was not closed: "+progress);}
  // The sanctuary gave up its tablet: the trail goes on to the crypt, and the crypt goes down into the real rock of that place.
  else if(phase==3&&ticks==20){server.execute(()->{try{
    var l=server.overworld();var e=entry(server);var p=player(server);
    var q=Chains.advance(p,e.settlement().id(),finished(Chains.SANCTUARY,Expeditions.target(l,e,3)));if(q==null){failure="The sanctuary named no crypt";return;}
    crypt=q.getUUID("id");waited=0;
    if(!Quests.take(p,e.settlement().id(),crypt).equals("ok")){failure="The kept crypt could not be taken";return;}
   }catch(Exception ex){failure=ex.toString();}});}
  else if(phase==3&&crypt!=null&&ticks%20==0){server.execute(()->{try{
    if(cryptBuilt)return;
    var l=server.overworld();var e=entry(server);var p=player(server);var q=Quests.quest(l,e.settlement().id(),crypt);
    if(q.getBoolean("pending")){walkTo(l,p,BlockPos.of(q.getLong("site")));waited++;progress="pending tries="+q.getInt("tries");return;}
    var site=QuestSites.site(l,crypt);var o=QuestSites.origin(site);var guard=l.getEntity(site.getUUID("chief"));
    String checked="bricks="+count(l,o,5,0,3,Blocks.STONE_BRICKS)+" ladders="+count(l,o,5,-5,-1,Blocks.LADDER)+" vault_chest="+count(l,o,5,-5,-5,Blocks.CHEST)
     +" guard="+(guard instanceof LivingEntity g?guard.getType().toShortString()+"/"+g.getMaxHealth():"none");
    if(count(l,o,5,0,3,Blocks.STONE_BRICKS)<20||count(l,o,5,-5,-1,Blocks.LADDER)<4||count(l,o,5,-5,-5,Blocks.CHEST)!=1||!(guard instanceof LivingEntity g2)||g2.getMaxHealth()!=(float)Quests.setting(Chains.CRYPT,"health")){
     failure="The crypt did not come out whole: "+checked;return;}
    var vault=BlockPos.of(site.getLong("vault"));p.teleportTo(l,vault.getX()+.5,vault.getY(),vault.getZ()+.5,0,20);
    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,1200,0,false,false));
    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,1200,4,false,false));
    p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,net.minecraft.world.phys.Vec3.atCenterOf(BlockPos.of(site.getLong("chest"))));
    LogUtils.getLogger().info("ASTRA_CHAIN the village's tick built the crypt in real ground at {} after {} walks: {}",o.toShortString(),waited,checked);cryptBuilt=true;
   }catch(Exception ex){failure=ex.toString();}});
   if(cryptBuilt){phase=4;ticks=0;}else if(ticks>3000)throw new IllegalStateException("The crypt was never built: "+progress);}
  else if(phase==4&&ticks==20){mc.player.getInventory().selected=8;}
  else if(phase==4&&ticks==40){capture(mc,"crypt");
   LogUtils.getLogger().info("ASTRA_CHAIN VERIFIED stages built by the village tick in real ground: the war camp broken and the raids quieted, the crypt dug into rock with its guard; reload=false");
   mc.stop();phase=5;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_CHAIN FAILED",ex);Minecraft.getInstance().stop();}}
}
