package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Profession;
import org.villageastra.server.SettlementData;
/** AD-150 «Серые в клетках»: the wolves of the village kennel are not bought, they are taken back from the catchers.
 *  <p>A village with a kennel and room in it hears of a poachers' camp out in the wolf country: greys in cages of iron bars, the catchers
 *  round their fire. The band has to be put down, a cage broken open, and the wolf inside — starved and wary of everybody — coaxed with
 *  raw meat by the one who let it out. It follows that person home; at the kennel it is written into the village's own pack
 *  ({@link VillageWolves#enlist}) and stops being anybody's pet. Only a wolf that really reached the kennel counts. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class WolfRescue {
 private WolfRescue(){}
 /** The errand, and the mark a caged grey of it carries. */
 public static final String CAGES="cages";
 public static final String CAUGHT="AstraCaughtWolf";
 /** How near the camp the owner must be for its own doings to run, and how near the kennel a wolf is taken in. */
 public static final int NEAR=48,HOME=16;
 public static boolean is(String template){return CAGES.equals(template);}
 private static int number(String key){return (int)Quests.setting(CAGES,key);}

 // ---------------------------------------------------------------- posting
 /** What the site of a waiting card is made of. */
 public static QuestSites.Order order(int wolves){return new QuestSites.Order(WolfSites.KIND,Math.max(1,wolves),0,0,null,ItemStack.EMPTY,ItemStack.EMPTY);}
 /** A camp built without a single cage is scenery: the place is tried again a little farther on. */
 public static boolean sound(CompoundTag built){return !built.getList("cages",Tag.TAG_LONG).isEmpty();}
 /** The keeper of the yard asks for this errand themselves, so it is offered in their own words (AD-149). */
 private static UUID keeper(SettlementData.Entry e){
  for(var r:e.settlement().residents())if(r.alive()&&r.profession()==Profession.LIVESTOCK_FARMER)return r.id();
  return null;
 }
 /** Posts the rescue when the village has a kennel with room in it, one card at a time within Quests.MAX_OPEN. */
 public static CompoundTag post(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();
  var kennel=VillageWolves.kennel(e);if(kennel==null)return null;
  int room=VillageWolves.room(l,e);if(room<=0)return null;
  int open=0;
  for(var raw:Quests.board(l,village).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var state=q.getString("state");
   if(!state.equals(Quests.OPEN)&&!state.equals(Quests.TAKEN))continue;open++;if(is(q.getString("template")))return null;}
  if(open>=Quests.MAX_OPEN)return null;
  int target=Math.min(room,number("count"));if(target<=0)return null;
  var q=Quests.blank(village,CAGES,now,Quests.deadline(CAGES));
  q.putInt("target",target);q.putLong("coins",Quests.coins(CAGES)*target);q.putLong("reputation",Quests.reputation(CAGES));
  var anchor=where(l,e,now);
  q.putLong("site",anchor.asLong());q.putBoolean("pending",true);q.putString("kind",WolfSites.KIND);q.putString("status","pending");
  var asker=keeper(e);
  if(asker!=null){var r=e.settlement().resident(asker);q.putUUID("giver",asker);q.putString("giver_name",r.profile().name());}
  Quests.store(l,village,q);
  for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
   player.sendSystemMessage(Component.translatable("quest.villageastra.wolf.posted",target,
    Component.translatable("quest.villageastra.side."+Adventures.bearing(e.center(),anchor)),Adventures.away(e.center(),anchor)));
  return q;
 }
 /** Wolf country out along one of eight roads: the catchers camp where there are wolves to catch. */
 static BlockPos where(ServerLevel l,SettlementData.Entry e,long now){
  int distance=number("distance");double angle=Math.PI/4*Math.floorMod(now/Math.max(1,number("every")),8L);
  var from=e.center().offset((int)Math.round(Math.cos(angle)*distance),0,(int)Math.round(Math.sin(angle)*distance));
  var found=l.findClosestBiome3d(h->h.value().getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.CREATURE).unwrap().stream()
   .anyMatch(d->d.type==net.minecraft.world.entity.EntityType.WOLF),from,number("biome_radius"),32,64);
  if(found==null)return from;
  var at=found.getFirst();
  // The cross never falls on the village itself: a wood that begins at its gate is looked for farther out.
  return at.distSqr(e.center())<(long)(distance/2)*(distance/2)?from:at;
 }

 // ---------------------------------------------------------------- the pass
 public static void tick(ServerLevel l,SettlementData.Entry e,long now){
  if(Math.floorMod(now,20L)!=0)return;
  var village=e.settlement().id();
  for(var raw:Quests.board(l,village).getList("quests",Tag.TAG_COMPOUND)){var old=(CompoundTag)raw;
   if(!is(old.getString("template")))continue;var state=old.getString("state");
   if(!state.equals(Quests.OPEN)&&!state.equals(Quests.TAKEN))continue;
   var q=Quests.quest(l,village,old.getUUID("id"));if(q==null)continue;
   var owner=q.hasUUID("owner")?l.getServer().getPlayerList().getPlayer(q.getUUID("owner")):null;
   step(l,e,q,owner,now);}
 }
 /** One pass of one card with its owner given: the tick passes the online player, a test passes its own. */
 public static void step(ServerLevel l,SettlementData.Entry e,CompoundTag q,ServerPlayer owner,long now){
  var village=e.settlement().id();var root=Quests.root(q);
  if(q.getBoolean("pending"))return;
  var site=QuestSites.site(l,root);
  if(site==null){Quests.settle(l,village,Quests.quest(l,village,q.getUUID("id")),Quests.BROKEN,"site_missing",owner);return;}
  if(site.getString("state").equals("closed"))return;
  var was=q.copy();
  var camp=QuestSites.origin(site);
  var band=l.hasChunkAt(camp)?Adventures.band(l,site,q):List.<net.minecraft.world.entity.Mob>of();
  var caged=l.hasChunkAt(camp)?WolfSites.caged(l,site):List.<Wolf>of();
  // What the village hears of the errand: the catchers first, then the cages, then the road home with the freed greys.
  String status;int left;
  if(!band.isEmpty()){status="catchers";left=band.size();}
  else if(!caged.isEmpty()){status="cages";left=caged.size();}
  else{var following=following(l,q,owner);status=following>0?"home":"wary";left=following>0?following:free(l,q).size();}
  // Taken in at the kennel: a wolf that really walked home is the village's from then on, and nobody's pet.
  int counted=q.getInt("progress");
  var kennel=VillageWolves.kennel(e);
  if(kennel!=null&&owner!=null&&counted<q.getInt("target")){
   var at=LogisticsRoutes.position(e,kennel);
   for(var wolf:free(l,q)){
    if(!wolf.isAlive()||wolf.blockPosition().distSqr(at)>(long)HOME*HOME)continue;
    if(!VillageWolves.enlist(l,e,wolf,kennel))continue;
    wolf.getPersistentData().remove(CAUGHT);wolf.setTame(false);wolf.setOwnerUUID(null);wolf.setOrderedToSit(false);
    wolf.setHealth(wolf.getMaxHealth());
    l.sendParticles(ParticleTypes.HAPPY_VILLAGER,wolf.getX(),wolf.getY()+.8,wolf.getZ(),10,.5,.5,.5,0);
    l.playSound(null,wolf.blockPosition(),SoundEvents.WOLF_HOWL,SoundSource.NEUTRAL,1f,1.1f);
    counted++;owner.displayClientMessage(Component.translatable("quest.villageastra.wolf.enlisted",counted,q.getInt("target")),false);
    if(counted>=q.getInt("target"))break;}}
  if(counted!=q.getInt("progress")){q.putInt("progress",counted);status=counted>=q.getInt("target")?"done":status;}
  if(!status.equals(q.getString("status"))||left!=q.getInt("status_left")){q.putString("status",status);q.putInt("status_left",left);}
  if(!q.equals(was))Quests.store(l,village,q);
  if(counted>=q.getInt("target")&&owner!=null){Quests.complete(owner,village,q.getUUID("id"));return;}
  // Nothing left to rescue and nothing rescued: the errand cannot be finished any more (AD-105 — a taken one fails, an open one is dropped).
  var lost=lost(l,q);
  if(!lost.isEmpty()&&counted<q.getInt("target")){var fresh=Quests.quest(l,village,q.getUUID("id"));
   Quests.settle(l,village,fresh,q.getString("state").equals(Quests.TAKEN)?Quests.FAILED:Quests.CANCELLED,lost,owner);}
 }
 /** The greys of this card that are out of their cages and alive. */
 public static List<Wolf> free(ServerLevel l,CompoundTag q){
  var out=new ArrayList<Wolf>();var root=Quests.root(q);
  var site=QuestSites.site(l,root);if(site==null)return out;
  var camp=QuestSites.origin(site);if(!l.hasChunkAt(camp))return out;
  for(var raw:site.getList("animals",Tag.TAG_INT_ARRAY)){var id=NbtUtils.loadUUID(raw);
   if(l.getEntity(id) instanceof Wolf wolf&&wolf.isAlive()&&wolf.getPersistentData().hasUUID(CAUGHT)
    &&VillageWolves.village(wolf)==null&&!inCage(l,site,wolf))out.add(wolf);}
  return out;
 }
 private static boolean inCage(ServerLevel l,CompoundTag site,Wolf wolf){
  for(var middle:WolfSites.cages(site))if(wolf.blockPosition().distSqr(middle)<=2&&!WolfSites.opened(l,middle))return true;
  return false;
 }
 /** The freed greys already walking home behind the one who coaxed them. */
 private static int following(ServerLevel l,CompoundTag q,ServerPlayer owner){
  if(owner==null)return 0;int n=0;
  for(var wolf:free(l,q))if(owner.getUUID().equals(wolf.getOwnerUUID()))n++;
  return n;
 }
 /** Why this card can no longer be finished, or "": no grey left alive to bring home, or the kennel is gone. */
 static String lost(ServerLevel l,CompoundTag q){
  int need=q.getInt("target")-q.getInt("progress");if(need<=0)return "";
  var site=QuestSites.site(l,Quests.root(q));if(site==null)return "";
  var camp=QuestSites.origin(site);if(!l.hasChunkAt(camp))return "";
  int alive=0;
  for(var raw:site.getList("animals",Tag.TAG_INT_ARRAY)){var id=NbtUtils.loadUUID(raw);
   if(l.getEntity(id) instanceof Wolf wolf&&wolf.isAlive()&&VillageWolves.village(wolf)==null)alive++;}
  return alive<need?"wolves_lost":"";
 }
 /** For the board: why nobody can take this card any more, or "". */
 static String impossible(ServerLevel l,CompoundTag q){
  if(!is(q.getString("template"))||q.getBoolean("pending"))return "";
  var e=SettlementData.get(l.getServer()).entry(q.getUUID("village"));
  if(e!=null&&VillageWolves.kennel(e)==null)return "no_kennel";
  var site=QuestSites.site(l,Quests.root(q));
  return site==null||site.getString("state").equals("closed")?"":lost(l,q);
 }
 /** A closed card: the catchers of a place nobody finished are cleared away with it; the greys are left where they are. */
 static void closed(ServerLevel l,UUID village,CompoundTag q,String state,String reason){
  if(!is(q.getString("template"))||state.equals(Quests.ABANDONED))return;
  var site=QuestSites.site(l,Quests.root(q));if(site==null||site.getString("state").equals("closed"))return;
  long now=SettlementData.get(l.getServer()).clock().ticks();
  if(l.hasChunkAt(QuestSites.origin(site)))for(var mob:Adventures.band(l,site,q))mob.discard();
  site.putString("state","closed");site.putLong("closed",now);QuestSites.save(l,Quests.root(q),site);
 }

 // ---------------------------------------------------------------- the world's side
 /** Raw meat from the hand of the one who broke the cage: a starved grey takes it, and then follows that person. */
 @SubscribeEvent public static void fed(PlayerInteractEvent.EntityInteract event){
  if(!(event.getLevel() instanceof ServerLevel l)||!(event.getTarget() instanceof Wolf wolf))return;
  var data=wolf.getPersistentData();if(!data.hasUUID(CAUGHT))return;
  if(!(event.getEntity() instanceof ServerPlayer p))return;
  var stack=p.getItemInHand(event.getHand());
  event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
  if(wolf.isTame()){if(!p.getUUID().equals(wolf.getOwnerUUID()))p.displayClientMessage(Component.translatable("quest.villageastra.wolf.another"),true);return;}
  var card=Quests.find(l,data.getUUID(CAUGHT));
  if(card==null){p.displayClientMessage(Component.translatable("quest.villageastra.wolf.no_card"),true);return;}
  var q=(CompoundTag)card[1];
  if(!q.getString("state").equals(Quests.TAKEN)||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID())){
   p.displayClientMessage(Component.translatable("quest.villageastra.wolf.not_yours"),true);return;}
  var site=QuestSites.site(l,Quests.root(q));
  if(site!=null&&inCage(l,site,wolf)){p.displayClientMessage(Component.translatable("quest.villageastra.wolf.still_caged"),true);return;}
  if(!VillageWolves.MEAT.contains(stack.getItem())){p.displayClientMessage(Component.translatable("quest.villageastra.wolf.wants_meat"),true);return;}
  if(!p.getAbilities().instabuild)stack.shrink(1);
  wolf.tame(p);wolf.setOrderedToSit(false);wolf.setHealth(wolf.getMaxHealth());
  l.sendParticles(ParticleTypes.HEART,wolf.getX(),wolf.getY()+.8,wolf.getZ(),8,.4,.4,.4,0);
  l.playSound(null,wolf.blockPosition(),SoundEvents.WOLF_WHINE,SoundSource.NEUTRAL,1f,1.2f);
  p.displayClientMessage(Component.translatable("quest.villageastra.wolf.coaxed"),false);
 }
 /** A grey killed on the way is one the village will not get: the card is judged on what is left (step). */
 @SubscribeEvent public static void died(LivingDeathEvent event){
  if(!(event.getEntity() instanceof Wolf wolf)||!(wolf.level() instanceof ServerLevel l))return;
  var data=wolf.getPersistentData();if(!data.hasUUID(CAUGHT))return;
  var card=Quests.find(l,data.getUUID(CAUGHT));if(card==null)return;
  var q=(CompoundTag)card[1];
  if(event.getSource().getEntity() instanceof ServerPlayer p&&q.hasUUID("owner")&&q.getUUID("owner").equals(p.getUUID()))
   p.displayClientMessage(Component.translatable("quest.villageastra.wolf.killed"),false);
 }
}
