package org.villageastra.world;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
/** AD-098: requests with a face. A named resident asks for what their own workshop really lacks, and it is handed over there, at their chest;
 *  a request of one's own neighbour earns gratitude on top of the pay. Rescues are personal too: the captive, the survivors and the trapped
 *  prospectors are kin of somebody in the village, and a relative who comes home moves into that resident's house when it has room. */
public final class Pleas {
 public static final String PLEA="plea";
 public static final String KIN="AstraKin",NAME="AstraProfileName";
 public static final int EVERY=(int)Quests.setting(PLEA,"every");
 private Pleas(){}
 /** The living adult who works at this building, if anybody does. */
 static Resident worker(SettlementData.Entry e,UUID building){
  for(var r:e.settlement().residents()){if(!r.alive()||r.life()!=Resident.Life.ADULT)continue;var b=e.settlement().workplace(r.id());if(b!=null&&b.id().equals(building))return r;}
  return null;
 }
 private static Item item(String id){return BuiltInRegistries.ITEM.get(new ResourceLocation(id));}
 /** One request at a time: the first want of a staffed workshop the village's prices know. */
 public static CompoundTag post(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();var board=Quests.board(l,village);int open=0;
  for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var state=q.getString("state");
   if(!state.equals(Quests.OPEN)&&!state.equals(Quests.TAKEN))continue;open++;if(q.getString("template").equals(PLEA))return null;}
  if(open>=Quests.MAX_OPEN)return null;
  for(var want:Workshops.wants(l,e)){
   var giver=worker(e,want.destination());if(giver==null)continue;
   for(var stack:want.ingredient().getItems()){var kind=stack.getItem();if(Trade.price(kind)==null)continue;
    // A thing the board already asks for is not asked twice: the supply errand and the request share one item.
    var key=BuiltInRegistries.ITEM.getKey(kind).toString();
    if(board.getList("quests",Tag.TAG_COMPOUND).stream().anyMatch(x->{var o=(CompoundTag)x;var st=o.getString("state");return (st.equals(Quests.OPEN)||st.equals(Quests.TAKEN))&&o.getString("item").equals(key);}))continue;
    int count=Math.max(1,Math.min((int)Quests.setting(PLEA,"most"),want.count()));
    var q=Quests.blank(village,PLEA,now,Quests.deadline(PLEA));
    q.putString("item",key);q.putInt("target",count);
    q.putUUID("giver",giver.id());q.putString("giver_name",giver.profile().name());q.putUUID("building",want.destination());
    q.putLong("coins",Quests.coins(PLEA)+Quests.value(kind,count));
    q.putLong("reputation",Quests.reputation(PLEA)*(100+(long)Quests.setting(PLEA,"gratitude"))/100);
    Quests.store(l,village,q);
    for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
     player.sendSystemMessage(Component.translatable("quest.villageastra.plea_posted",giver.profile().name(),new ItemStack(kind).getHoverName(),count));
    return q;}
  }
  return null;
 }
 /** A request belongs to the workshop's need, spoken by whoever works there. When its speaker is gone the next worker of that workshop takes
  *  it over; when nobody works there any more, a request nothing was brought for yet is called off without blame (AD-105). Each record is
  *  read fresh and written on its own. */
 static void tick(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();var ids=new ArrayList<UUID>();
  for(var raw:Quests.board(l,village).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var state=q.getString("state");
   if(q.getString("template").equals(PLEA)&&(state.equals(Quests.OPEN)||state.equals(Quests.TAKEN)))ids.add(q.getUUID("id"));}
  for(var id:ids){var q=Quests.quest(l,village,id);if(q==null||!q.hasUUID("giver")||!q.hasUUID("building"))continue;
   var building=q.getUUID("building");var giver=e.settlement().resident(q.getUUID("giver"));var at=giver==null?null:e.settlement().workplace(giver.id());
   if(giver!=null&&giver.alive()&&at!=null&&at.id().equals(building))continue;
   var next=worker(e,building);
   if(next!=null){q.putUUID("giver",next.id());q.putString("giver_name",next.profile().name());Quests.store(l,village,q);continue;}
   if(q.getString("state").equals(Quests.OPEN)||q.getInt("progress")==0){
    var owner=q.hasUUID("owner")?l.getServer().getPlayerList().getPlayer(q.getUUID("owner")):null;
    Quests.settle(l,village,q,Quests.CANCELLED,"giver_gone",owner);}
   // What was already brought lies in the workshop: the rest can still be brought, whoever works there next.
  }
 }
 /** Handed over at the giver's own workshop, one item at a time through the journal. */
 public static String handOver(ServerPlayer p,UUID village,UUID id){
  var l=p.serverLevel();var q=Quests.quest(l,village,id);var e=SettlementData.get(p.server).entry(village);
  if(q==null||e==null||!q.getString("state").equals(Quests.TAKEN)||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))return "unknown";
  var building=e.settlement().buildings().stream().filter(b->b.id().equals(q.getUUID("building"))).findFirst().orElse(null);
  var chest=building==null?null:LogisticsRoutes.position(e,building);if(chest==null)return "unknown";
  if(p.distanceToSqr(chest.getX()+.5,chest.getY()+.5,chest.getZ()+.5)>64)return "far_workshop";
  var kind=item(q.getString("item"));int carried=0;
  for(var s:p.getInventory().items)if(s.is(kind)&&Trade.plain(s))carried+=s.getCount();
  if(carried<=0)return "empty";int moved=0;
  for(int i=0;i<carried&&q.getInt("progress")+moved<q.getInt("target");i++){
   if(!WorldJournal.deposit(l,Settlement.childId(id,"plea/"+(q.getInt("progress")+moved)),chest,new ItemStack(kind,1)))break;
   for(var s:p.getInventory().items)if(s.is(kind)&&Trade.plain(s)){s.shrink(1);break;}
   moved++;}
  if(moved==0)return "full";
  q.putInt("progress",q.getInt("progress")+moved);Quests.store(l,village,q);
  if(Quests.complete(p,village,id))p.displayClientMessage(Component.translatable("quest.villageastra.plea_thanks",q.getString("giver_name")),false);
  return "ok";
 }
 /** The resident whose kin a rescue brings home: a living adult, chosen by the quest so it stays the same. */
 static Resident kinFor(SettlementData.Entry e,UUID quest){
  var adults=e.settlement().residents().stream().filter(r->r.alive()&&r.life()==Resident.Life.ADULT).sorted(Comparator.comparing(Resident::id)).toList();
  return adults.isEmpty()?null:adults.get((int)Math.floorMod(quest.getLeastSignificantBits(),adults.size()));
 }
 /** Gives the people of a rescue the family name of their relative in the village and remembers whose kin they are. */
 static void kin(ServerLevel l,SettlementData.Entry e,CompoundTag q,CompoundTag site){
  var giver=kinFor(e,q.getUUID("id"));if(giver==null)return;
  var family=giver.profile().name().substring(giver.profile().name().lastIndexOf(' ')+1);
  for(var id:QuestSites.people(site)){
   if(!(l.getEntity(id) instanceof ResidentEntity npc))continue;
   var own=ResidentProfile.generate(id);var first=own.name().substring(0,own.name().indexOf(' '));
   var profile=new ResidentProfile(first+" "+family,own.skin());
   var resident=new Resident(id,Resident.Life.ADULT,npc.getPersistentData().getBoolean("AstraEducated"),null,null,-1,profile);
   npc.bind(null,resident);npc.getPersistentData().putUUID(KIN,giver.id());npc.getPersistentData().putString(NAME,profile.name());
  }
  q.putUUID("giver",giver.id());q.putString("giver_name",giver.profile().name());
 }
 /** The home a kin of this resident moves into first: the relative's own, while it has a free bed. */
 static Settlement.Home kinHome(SettlementData.Entry e,ResidentEntity npc){
  if(!npc.getPersistentData().hasUUID(KIN))return null;var relative=e.settlement().resident(npc.getPersistentData().getUUID(KIN));
  if(relative==null||!relative.alive()||relative.home()==null)return null;
  return e.settlement().homes().stream().filter(h->h.id().equals(relative.home())&&h.usable()&&e.settlement().occupancy(h.id())<h.capacity()).findFirst().orElse(null);
 }
 /** The profile a guest keeps when they are admitted: the family name the rescue gave them. */
 static ResidentProfile profile(ResidentEntity npc){
  var own=ResidentProfile.generate(npc.getUUID());var name=npc.getPersistentData().getString(NAME);
  return name.isEmpty()?own:new ResidentProfile(name,own.skin());
 }
}
