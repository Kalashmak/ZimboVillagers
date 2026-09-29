package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.Items;
import org.villageastra.server.SettlementData;
/** AD-099: adventures that lead on. A finished stage names the next place — farther out along the same road — and that stage is kept for
 *  the player who finished the last one. Its site is built when that land is in the world; until then the chart already shows the way. */
public final class Chains {
 public static final String SANCTUARY="sanctuary",CRYPT="crypt",WARCAMP="warcamp",RICHLODE="richlode";
 /** Stages that only a chain posts: the board's windows never offer them on their own. */
 public static final List<String> STAGES=List.of(SANCTUARY,CRYPT,WARCAMP,RICHLODE);
 private static final int SHIFT=16,TRIES=8;
 private Chains(){}
 /** The chain a template belongs to and its place in it, or null. */
 static String[] chainOf(String template){
  var chains=Quests.chains();
  for(var name:chains.keySet()){var stages=chains.get(name);int i=stages.indexOf(template);if(i>=0)return new String[]{name,String.valueOf(i),String.valueOf(stages.size())};}
  return null;
 }
 static String next(String template){
  var of=chainOf(template);if(of==null)return null;var stages=Quests.chains().get(of[0]);int i=Integer.parseInt(of[1]);
  return i+1<stages.size()?stages.get(i+1):null;
 }
 /** Farther out along the road from the village through the last site. */
 static BlockPos onward(SettlementData.Entry e,BlockPos from,int step){
  double dx=from.getX()-e.center().getX(),dz=from.getZ()-e.center().getZ(),len=Math.max(1,Math.sqrt(dx*dx+dz*dz));
  return new BlockPos((int)Math.round(from.getX()+dx/len*step),from.getY(),(int)Math.round(from.getZ()+dz/len*step));
 }
 /** Called once a stage is paid: the next stage is posted, kept for the same player. A world without monsters gets no stage that stands on fighting. */
 public static CompoundTag advance(ServerPlayer p,UUID village,CompoundTag done){return advance(p.serverLevel(),village,p.getUUID(),done);}
 /** The same for a finder who may be away: the stage is kept for them all the same and they are told when they are here. */
 static CompoundTag advance(ServerLevel l,UUID village,UUID finder,CompoundTag done){
  var template=done.getString("template");var following=next(template);if(following==null||!done.contains("site"))return null;
  var e=SettlementData.get(l.getServer()).entry(village);if(e==null)return null;
  if(Adventures.needsFighters(following)&&l.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return null;
  var board=Quests.board(l,village);var id=done.getUUID("id");
  if(board.getList("quests",Tag.TAG_COMPOUND).stream().anyMatch(x->((CompoundTag)x).hasUUID("after")&&((CompoundTag)x).getUUID("after").equals(id)))return null;
  var of=chainOf(following);long now=SettlementData.get(l.getServer()).clock().ticks();
  var q=Quests.blank(village,following,now,Quests.deadline(following));
  q.putInt("target",(int)Quests.setting(following,"count"));
  q.putLong("coins",Quests.coins(following));q.putLong("reputation",Quests.reputation(following));
  if(following.equals(RICHLODE)){q.putString("item",BuiltInRegistries.ITEM.getKey(Items.RAW_GOLD).toString());q.putLong("coins",q.getLong("coins")+Quests.value(Items.RAW_GOLD,q.getInt("target")));}
  if(following.equals(SANCTUARY)||following.equals(CRYPT))q.putString("item",BuiltInRegistries.ITEM.getKey(org.villageastra.VillageAstra.RESEARCH_VOLUME.get()).toString());
  var anchor=onward(e,BlockPos.of(done.getLong("site")),(int)Quests.number("chain_step"));
  q.putLong("site",anchor.asLong());q.putBoolean("pending",true);q.putString("kind",Adventures.site(following));q.putString("status","pending");
  q.putString("chain",of[0]);q.putInt("stage",Integer.parseInt(of[1])+1);q.putInt("stages",Integer.parseInt(of[2]));
  q.putUUID("reserved",finder);q.putUUID("after",id);
  if(done.hasUUID("giver")){q.putUUID("giver",done.getUUID("giver"));q.putString("giver_name",done.getString("giver_name"));}
  Quests.store(l,village,q);
  var p=l.getServer().getPlayerList().getPlayer(finder);
  if(p!=null)p.displayClientMessage(Component.translatable("quest.villageastra.chain_next",Component.translatable("quest.villageastra.chain."+of[0]),
   Component.translatable("quest.villageastra.place."+Adventures.site(following)),
   Component.translatable("quest.villageastra.side."+Adventures.bearing(e.center(),anchor)),Adventures.away(e.center(),anchor)),false);
  return q;
 }
 /** Builds the site of a waiting stage at this anchor. The tick passes the stored anchor; a test may pass its own ground. */
 public static boolean materialize(ServerLevel l,SettlementData.Entry e,UUID id,BlockPos anchor,long now){
  var village=e.settlement().id();var q=Quests.quest(l,village,id);if(q==null||!q.getBoolean("pending"))return false;
  // The place, its tablet and its band carry the id the world knows the stage by: the first card's, when this one came back to the board.
  var template=q.getString("template");var root=Quests.root(q);
  // AD-140: an animal quest's place is built the same way, from its own order.
  boolean animal=AnimalQuests.is(template);
  // AD-150: the poachers' camp is built the same way, and asks its pad for as many cages as the card asks for greys.
  boolean wolves=WolfRescue.is(template),far=Far.freight(template);
  var order=animal?AnimalQuests.order(template):wolves?WolfRescue.order(q.getInt("target")):far?Far.order(q)
   :Adventures.order(l,e,template,Quests.spec(template),root,null,anchor);if(order==null)return false;
  var built=QuestSites.build(l,e,root,order,anchor,now);if(built==null)return false;
  // A place that came out without its tablet or its band is scenery: the stage is tried again a little farther on.
  if(!(animal?AnimalQuests.sound(l,template,built):wolves?WolfRescue.sound(built):far?Far.sound(built):Adventures.sound(l,template,built,root))){built.putString("state","closed");QuestSites.save(l,root,built);return false;}
  var origin=QuestSites.origin(built);
  q.putLong("site",origin.asLong());q.putBoolean("pending",false);q.remove("tries");q.putString("status","");
  // The chart shows where the trail pointed when the player took it; a place found well away from that cross gets a new chart.
  var charted=q.contains("charted")?BlockPos.of(q.getLong("charted")):null;
  var owner=q.getString("state").equals(Quests.TAKEN)&&q.hasUUID("owner")?l.getServer().getPlayerList().getPlayer(q.getUUID("owner")):null;
  boolean redraw=owner!=null&&charted!=null&&Math.pow(charted.getX()-origin.getX(),2)+Math.pow(charted.getZ()-origin.getZ(),2)>24*24;
  if(redraw)q.putLong("charted",origin.asLong());
  Quests.store(l,village,q);
  if(redraw)Adventures.giveChart(owner,village,q);
  return true;
 }
 /** A waiting stage is built as soon as its land is in the world; ground with no room moves the anchor on a little. */
 public static void tick(ServerLevel l,SettlementData.Entry e,long now){
  if(Math.floorMod(now,20L)!=0)return;
  var village=e.settlement().id();
  for(var raw:Quests.board(l,village).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
   if(!q.getBoolean("pending"))continue;var state=q.getString("state");if(!state.equals(Quests.OPEN)&&!state.equals(Quests.TAKEN))continue;
   var anchor=BlockPos.of(q.getLong("site"));int half=QuestSites.half(q.getString("kind"))+1;
   boolean loaded=true;
   for(int dx=-half;dx<=half&&loaded;dx+=half)for(int dz=-half;dz<=half&&loaded;dz+=half)if(!l.hasChunkAt(anchor.offset(dx,0,dz)))loaded=false;
   if(!loaded)continue;
   var ground=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,anchor.getX(),anchor.getZ());
   var standing=new BlockPos(anchor.getX(),ground,anchor.getZ());
   if(materialize(l,e,q.getUUID("id"),standing,now))continue;
   var again=Quests.quest(l,village,q.getUUID("id"));int tries=again.getInt("tries")+1;
   // A trail that finds no ground at all is called off without blaming its player: the world, not the deed, made it impossible (AD-105).
   if(tries>TRIES){var who=again.hasUUID("owner")?again.getUUID("owner"):again.hasUUID("reserved")?again.getUUID("reserved"):null;
    var player=who==null?null:l.getServer().getPlayerList().getPlayer(who);
    if(!again.hasUUID("owner")&&player!=null)player.displayClientMessage(Component.translatable("quest.villageastra.chain_lost",Component.translatable("quest.villageastra.template."+again.getString("template"))),false);
    Quests.settle(l,village,again,Quests.CANCELLED,"no_ground",player);continue;}
   again.putInt("tries",tries);again.putLong("site",onward(e,anchor,SHIFT).asLong());Quests.store(l,village,again);
  }
 }
}
