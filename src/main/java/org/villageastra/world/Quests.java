package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import net.minecraft.core.BlockPos;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
/** AD-040: the notice board of a settlement. Every quest comes from a real need, is taken by one player, is completed by real deeds and pays exactly once. */
public final class Quests {
 public static final String OPEN="open",TAKEN="taken",DONE="done",FAILED="failed",ABANDONED="abandoned";
 /** AD-105: an errand the world itself made impossible closes without blame; a record the mod lost closes as broken, logged, never punished. */
 public static final String CANCELLED="cancelled",BROKEN="broken";
 public static final String SUPPLY="supply",DONATION="donation",CLEARING="clearing",DEFENCE="defence",CARGO="cargo",BRING="bring",RESCUE="rescue";
 private static final JsonObject ROOT=root();
 public static final int MAX_OPEN=ROOT.get("max_open").getAsInt(),POST_EVERY=ROOT.get("post_every").getAsInt(),ABANDON_REPUTATION=ROOT.get("abandon_reputation").getAsInt(),FAIL_REPUTATION=ROOT.get("fail_reputation").getAsInt();
 /** QUEST-004: whoever kills a quest character pays for it as for killing a resident of that village. */
 public static final int KILL_REPUTATION=ROOT.get("kill_reputation").getAsInt();
 private Quests(){}
 private static JsonObject root(){try(var s=Quests.class.getResourceAsStream("/data/villageastra/balance/quests.json")){if(s==null)throw new IllegalStateException("Missing quest balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 static JsonObject spec(String template){return ROOT.getAsJsonObject(template);}
 public static int number(String key){return ROOT.get(key).getAsInt();}
 public static long deadline(String template){return spec(template).get("deadline").getAsLong();}
 /** One number of a template's balance, for the tests and the probes that check against it. */
 public static double setting(String template,String key){return spec(template).get(key).getAsDouble();}
 /** AD-097: the reputation a player needs here before the board gives them this errand: everyday work for anyone, lives and far places
  *  for the proven, sieges, the dead and the envoys for the trusted. */
 public static int trust(String template){var t=spec(template);if(t!=null&&t.has("trust"))return t.get("trust").getAsInt();
  // A stage of a chain asks for the trust its first adventure asked for, once nobody holds it any more.
  for(var stages:chains().values())if(stages.indexOf(template)>0)return trust(stages.get(0));
  return 0;}
 /** AD-099: the chains of adventures, each the templates it plays in turn. */
 static Map<String,List<String>> chains(){var out=new LinkedHashMap<String,List<String>>();var root=ROOT.getAsJsonObject("chains");
  for(var name:root.keySet()){var stages=new ArrayList<String>();for(var x:root.getAsJsonArray(name))stages.add(x.getAsString());out.put(name,List.copyOf(stages));}
  return out;}
 /** AD-088: a quest pays like the trade it stands beside — the reputation and the coins an hour of plain selling brings, for the minutes
  *  the quest really takes. It is another way to earn, not a better one. A template without minutes keeps its fixed numbers. */
 public static long reputation(String template){var t=spec(template);
  if(t.has("minutes"))return Math.max(1,Math.round(t.get("minutes").getAsDouble()*number("reputation_per_hour")/60.0));
  return t.has("reputation")?t.get("reputation").getAsLong():0;}
 public static long coins(String template){var t=spec(template);
  if(t.has("minutes"))return Math.max(1,Math.round(t.get("minutes").getAsDouble()*number("coins_per_hour")/60.0));
  return t.has("reward_coins")?t.get("reward_coins").getAsLong():0;}
 /** What goods are worth at the village's own prices: a quest that brings goods pays their value, like selling them would. */
 public static long value(Item item,int count){var price=Trade.price(item);return price==null?0:(long)count*price.coins()/Math.max(1,price.per());}
 /** A quest whose deed became impossible by the player's own doing fails at once, as at its deadline. */
 static void fail(ServerLevel l,UUID village,CompoundTag q){close(l,village,q,FAILED,"",null);}
 /** Templates whose deed is counted in parts: what was really brought, or who really arrived, is paid even when the whole is not done. */
 static boolean counted(String template){
  return template.equals(CARGO)||template.equals(RESCUE)||template.equals(Pleas.PLEA)||template.equals(CropQuests.CROP)
   ||template.equals(Adventures.LODE)||template.equals(Chains.RICHLODE)||Adventures.partial(template)||AnimalQuests.is(template)
   ||WolfRescue.is(template)||Far.freight(template);
 }
 /** Templates whose deed is people brought home: a person who arrived is a success that nothing later takes back (QUEST-005). */
 static boolean escort(String template){return template.equals(BRING)||template.equals(RESCUE)||Adventures.escorts(template);}
 /** The id the world knows a quest by. Its site, people, relic and mobs carry the id of the first card, which a card posted again keeps as its origin. */
 public static UUID root(CompoundTag q){return q.hasUUID("origin")?q.getUUID("origin"):q.getUUID("id");}
 /** Pays the owner for the part of the deed really done, once, by the completion id: into the inventory when they are here, as coins owed
  *  (claimed at any trade) when they are not; reputation needs nobody present. Returns {coins, reputation}, or null when it was paid before. */
 private static long[] pay(ServerLevel l,UUID village,CompoundTag q,ServerPlayer online){
  var server=l.getServer();var id=q.getUUID("id");var owner=q.getUUID("owner");long now=SettlementData.get(server).clock().ticks();
  var completion=Settlement.childId(id,"completion");var ledger=TradeLedger.get(server);if(ledger.deal(completion)!=null)return null;
  int done=Math.min(q.getInt("progress"),q.getInt("target")),of=Math.max(1,q.getInt("target"));
  long coins=q.getLong("coins")*done/of,reputation=q.getLong("reputation")*done/of,owed=coins;
  if(coins>0&&online!=null){long left=coins;while(left>0){int chunk=(int)Math.min(64,left);var stack=new ItemStack(VillageAstra.ZINDBO.get(),chunk);online.getInventory().add(stack);int paid=chunk-stack.getCount();left-=paid;if(paid<chunk)break;}owed=left;}
  if(owed>0)ledger.owe(owner,owed);
  if(reputation>0){var property=PropertyLedger.get(server);property.roll(village).decay(now);property.gift(village,owner,(int)Math.min(Integer.MAX_VALUE,reputation));}
  var deal=new CompoundTag();deal.putUUID("token",completion);deal.putUUID("village",village);deal.putUUID("player",owner);deal.putString("item",q.getString("item"));deal.putInt("count",q.getInt("progress"));deal.putLong("paid",coins-owed);deal.putLong("owed",owed);deal.putLong("reputation",reputation);deal.putString("quest",q.getString("template"));deal.putLong("tick",now);
  ledger.record(completion,deal);
  return new long[]{coins,reputation};
 }
 /** AD-105: closes a quest with its outcome and says so to its owner. The part really done is paid — a failure too pays what was delivered —
  *  a failure costs reputation for the part left undone, and a cancellation or a broken record costs nothing. Changes the record in memory:
  *  the caller writes it, or holds the board it walks. */
 static void close(ServerLevel l,UUID village,CompoundTag q,String state,String reason,ServerPlayer online){
  var server=l.getServer();long now=SettlementData.get(server).clock().ticks();var template=q.getString("template");
  boolean owned=q.getString("state").equals(TAKEN)&&q.hasUUID("owner");
  var player=online!=null?online:owned?server.getPlayerList().getPlayer(q.getUUID("owner")):null;
  long[] paid=null;int penalty=0;
  if(owned&&q.getInt("progress")>0&&(state.equals(DONE)||counted(template)))paid=pay(l,village,q,player);
  if(owned&&state.equals(FAILED)){int target=Math.max(1,q.getInt("target")),done=counted(template)?Math.min(q.getInt("progress"),target):0;
   penalty=FAIL_REPUTATION*(target-done)/target;if(penalty>0)penalty(server,village,q.getUUID("owner"),penalty);}
  q.putString("state",state);q.putLong("closed",now);if(!reason.isEmpty())q.putString("reason",reason);
  AnimalQuests.closed(l,village,q,state,reason);WolfRescue.closed(l,village,q,state,reason);
  if(state.equals(DONE))q.putLong("completed",now);if(state.equals(FAILED))q.putLong("failed",now);
  if(state.equals(BROKEN))com.mojang.logging.LogUtils.getLogger().warn("Quest {} ({}) of village {} is broken: {}",q.getUUID("id"),template,village,reason);
  if(player==null||!owned)return;
  var name=net.minecraft.network.chat.Component.translatable("quest.villageastra.template."+template);
  long coins=paid==null?0:paid[0],reputation=paid==null?0:paid[1];
  var why=net.minecraft.network.chat.Component.translatable("quest.villageastra.reason."+(reason.isEmpty()?"none":reason));
  player.displayClientMessage(switch(state){
   case DONE->net.minecraft.network.chat.Component.translatable("quest.villageastra.completed",name,coins,reputation);
   case FAILED->net.minecraft.network.chat.Component.translatable("quest.villageastra.failed",name,coins,reputation,penalty);
   case CANCELLED->net.minecraft.network.chat.Component.translatable("quest.villageastra.cancelled",name,why,coins,reputation);
   default->net.minecraft.network.chat.Component.translatable("quest.villageastra.broken",name,why);},false);
 }
 /** Closes one quest and writes it at once; a finished stage then names the next place (AD-099). Takes a record read fresh and never
  *  runs inside a walk that saves a board read before it. */
 static void settle(ServerLevel l,UUID village,CompoundTag q,String state,String reason,ServerPlayer online){
  close(l,village,q,state,reason,online);replace(l,village,q);
  if(state.equals(DONE)&&q.hasUUID("owner"))Chains.advance(l,village,q.getUUID("owner"),q);
 }
 /** The ids of the records in these states, for walks that read and write each record on its own. */
 private static List<UUID> ids(CompoundTag board,String... states){
  var out=new ArrayList<UUID>();var wanted=List.of(states);
  for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;if(wanted.contains(q.getString("state")))out.add(q.getUUID("id"));}
  return out;
 }
 /** Finds the open or taken quest the world knows by this id, in any village: {village, record}, or null. */
 static Object[] find(ServerLevel l,UUID root){
  for(var e:SettlementData.get(l.getServer()).entries())for(var raw:board(l,e.settlement().id()).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
   var state=q.getString("state");if((state.equals(OPEN)||state.equals(TAKEN))&&root(q).equals(root))return new Object[]{e.settlement().id(),q};}
  return null;
 }
 /** QUEST-004: the killer of a quest character loses what killing a resident of that village costs. */
 public static void murder(MinecraftServer server,UUID village,UUID killer){penalty(server,village,killer,KILL_REPUTATION);}
 /** AD-140: a smaller price, for killing an animal somebody is bringing to the village. */
 static void penalize(MinecraftServer server,UUID village,UUID player,int amount){penalty(server,village,player,amount);}
 /** AD-075: an adventure template writes its record on the same board, with the same rules for owner, deadline and payment. */
 public static CompoundTag blank(UUID village,String template,long now,long deadline){return quest(village,template,now,deadline);}
 public static void store(ServerLevel l,UUID village,CompoundTag quest){replace(l,village,quest);}
 static void saveBoard(ServerLevel l,UUID village,CompoundTag board){save(l,village,board);}
 public static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-quests/"+village+".bin");}
 public static CompoundTag board(ServerLevel l,UUID village){var p=path(l,village);if(Files.exists(p))return NbtRecord.read(p);var t=new CompoundTag();t.putInt("schema",1);t.put("quests",new ListTag());return t;}
 private static void save(ServerLevel l,UUID village,CompoundTag board){NbtRecord.write(path(l,village),board);}
 public static CompoundTag quest(ServerLevel l,UUID village,UUID id){for(var raw:board(l,village).getList("quests",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getUUID("id").equals(id))return (CompoundTag)raw;return null;}
 private static void replace(ServerLevel l,UUID village,CompoundTag quest){var board=board(l,village);var list=board.getList("quests",Tag.TAG_COMPOUND);
  for(int i=0;i<list.size();i++)if(list.getCompound(i).getUUID("id").equals(quest.getUUID("id"))){list.set(i,quest);board.put("quests",list);save(l,village,board);return;}
  list.add(quest);board.put("quests",list);save(l,village,board);}
 private static long open(CompoundTag board){return board.getList("quests",Tag.TAG_COMPOUND).stream().filter(t->{var s=((CompoundTag)t).getString("state");return s.equals(OPEN)||s.equals(TAKEN);}).count();}
 /** Posts one quest from a real need of the settlement: missing goods, a donation drive, monsters nearby or a defence call. */
 public static CompoundTag post(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();var board=board(l,village);if(open(board)>=MAX_OPEN)return null;
  var wants=Workshops.wants(l,e);
  // A village that actually has monsters around it asks for help with them before asking for goods.
  long nearby=l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(e.center()).inflate(spec(CLEARING).get("radius").getAsInt()),m->m instanceof Enemy&&m.isAlive()).size();
  if(nearby>=2&&board.getList("quests",Tag.TAG_COMPOUND).stream().noneMatch(t->{var q=(CompoundTag)t;return q.getString("template").equals(CLEARING)&&(q.getString("state").equals(OPEN)||q.getString("state").equals(TAKEN));})){
   var clearing=quest(village,CLEARING,now,spec(CLEARING).get("deadline").getAsLong());clearing.putInt("target",spec(CLEARING).get("count").getAsInt());clearing.putLong("coins",coins(CLEARING));clearing.putLong("reputation",reputation(CLEARING));
   replace(l,village,clearing);return clearing;}
  for(var want:wants){
   for(var stack:want.ingredient().getItems()){var item=stack.getItem();var price=Trade.price(item);if(price==null)continue;
    if(board.getList("quests",Tag.TAG_COMPOUND).stream().anyMatch(t->{var q=(CompoundTag)t;return (q.getString("state").equals(OPEN)||q.getString("state").equals(TAKEN))&&q.getString("item").equals(key(item));}))continue;
    int count=Math.min(64,want.count());long value=(long)count*price.coins()/price.per();if(value<spec(SUPPLY).get("min_coins").getAsInt())continue;
    var q=quest(village,SUPPLY,now,spec(SUPPLY).get("deadline").getAsLong());q.putString("item",key(item));q.putInt("target",count);
    q.putLong("coins",value*spec(SUPPLY).get("reward_percent").getAsInt()/100);q.putLong("reputation",value*spec(SUPPLY).get("reputation_per_coin").getAsInt());
    replace(l,village,q);return q;}
  }
  String template=wants.isEmpty()?DONATION:null;if(template==null)return null;
  if(board.getList("quests",Tag.TAG_COMPOUND).stream().anyMatch(t->{var q=(CompoundTag)t;return (q.getString("state").equals(OPEN)||q.getString("state").equals(TAKEN))&&q.getString("template").equals(template);}))return null;
  var q=quest(village,template,now,spec(template).get("deadline").getAsLong());
  {q.putInt("target",spec(DONATION).get("target_coins").getAsInt());q.putLong("coins",0);q.putLong("reputation",(long)spec(DONATION).get("target_coins").getAsInt()*spec(DONATION).get("reputation_per_coin").getAsInt());}
  replace(l,village,q);return q;
 }
 /** AD-041: a distant quest that points at a real scouted lead; the camp itself is built at that place when the quest is posted. */
 public static CompoundTag postDistant(ServerLevel l,SettlementData.Entry e,String template,long now){
  var village=e.settlement().id();var board=board(l,village);if(open(board)>=MAX_OPEN)return null;
  if(board.getList("quests",Tag.TAG_COMPOUND).stream().anyMatch(t->{var q=(CompoundTag)t;return q.getString("template").equals(template)&&(q.getString("state").equals(OPEN)||q.getString("state").equals(TAKEN));}))return null;
  var lead=Expeditions.lead(l,village,Expeditions.CAMP,true);if(lead==null)return null;var site=BlockPos.of(lead.getLong("pos"));
  var q=quest(village,template,now,spec(template).get("deadline").getAsLong());
  ItemStack cargo=ItemStack.EMPTY;int travellers=0;
  if(template.equals(CARGO)){
   var want=Workshops.wants(l,e).stream().flatMap(w->java.util.Arrays.stream(w.ingredient().getItems())).filter(s->Trade.price(s.getItem())!=null).findFirst().orElse(new ItemStack(Items.IRON_INGOT));
   int count=spec(CARGO).get("count").getAsInt();cargo=new ItemStack(want.getItem(),count);q.putString("item",Camps.key(cargo));q.putInt("target",count);}
  else if(template.equals(BRING)){travellers=1;q.putInt("target",1);}
  else if(template.equals(RESCUE)){travellers=spec(RESCUE).get("count").getAsInt();q.putInt("target",travellers);}
  else return null;
  var camp=Camps.build(l,village,q.getUUID("id"),site,cargo,travellers,now);if(camp==null)return null;
  Expeditions.useLead(l,village,site,true);
  q.putUUID("camp",camp.getUUID("id"));q.putLong("site",BlockPos.of(camp.getLong("pos")).asLong());q.putLong("coins",coins(template)+(template.equals(CARGO)?value(cargo.getItem(),cargo.getCount()):0));q.putLong("reputation",reputation(template));
  replace(l,village,q);return q;
 }
 /** AD-075: one hand-over button for everything carried back: camp cargo, ore from a far seam or the volume of a barrow. */
 /** Whether this errand ends by handing goods over — the stock chest, a workshop chest or the hands that asked for them. */
 public static boolean handsOver(CompoundTag q){
  var template=q.getString("template");
  return template.equals(CARGO)||template.equals(CropQuests.CROP)||template.equals(Pleas.PLEA)||Adventures.handsOver(template)||Far.freight(template);
 }
 public static String handOver(ServerPlayer p,UUID village,UUID id){
  var q=quest(p.serverLevel(),village,id);
  if(q!=null&&q.getString("template").equals(CropQuests.CROP))return CropQuests.handOver(p,village,id);
  if(q!=null&&q.getString("template").equals(Pleas.PLEA))return Pleas.handOver(p,village,id);
  return q!=null&&Adventures.adventure(q.getString("template"))?Adventures.handOver(p,village,id):deliver(p,village,id);
 }
 /** Cargo brought from a camp is handed over at the village stock chest, item by item through the journal. */
 public static String deliver(ServerPlayer p,UUID village,UUID id){
  var l=p.serverLevel();var q=quest(l,village,id);var e=SettlementData.get(p.server).entry(village);
  if(q==null||e==null||!q.getString("state").equals(TAKEN)||!(q.getString("template").equals(CARGO)||Far.freight(q.getString("template")))||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))return "unknown";
  var stock=Caravans.stock(e);var chestPos=stock==null?null:LogisticsRoutes.position(e,stock);
  if(chestPos==null||p.distanceToSqr(chestPos.getX()+.5,chestPos.getY()+.5,chestPos.getZ()+.5)>64)return "far";
  var item=Camps.stack(q.getString("item"),1).getItem();int carried=0;for(var s:p.getInventory().items)if(s.is(item)&&Trade.plain(s))carried+=s.getCount();
  if(carried<=0)return "empty";int moved=0;
  for(int i=0;i<carried&&q.getInt("progress")+moved<q.getInt("target");i++){
   var deposit=Settlement.childId(id,"cargo/"+(q.getInt("progress")+moved));
   if(!WorldJournal.deposit(l,deposit,chestPos,new ItemStack(item,1)))break;
   for(var s:p.getInventory().items)if(s.is(item)&&Trade.plain(s)){s.shrink(1);break;}
   moved++;}
  if(moved==0)return "full";
  q.putInt("progress",q.getInt("progress")+moved);replace(l,village,q);complete(p,village,id);return "ok";
 }
 /** Escorted travellers who really reached the village: each one counts once and becomes a guest waiting for a bed. */
 public static void companions(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();
  for(var id:ids(board(l,village),OPEN,TAKEN)){var q=quest(l,village,id);
   if(q==null||!escort(q.getString("template")))continue;
   // AD-105: an untaken errand whose people are all gone asks nobody for anything any more: it is called off, blaming nobody.
   if(q.getString("state").equals(OPEN)){if(outThere(l,q)==0)settle(l,village,q,CANCELLED,"people_lost",null);continue;}
   // AD-075: a freed captive and treated survivors come home the same way as the travellers of a camp.
   if(!q.getString("state").equals(TAKEN)||!q.hasUUID("owner"))continue;
   escorted(l,e,q,l.getServer().getPlayerList().getPlayer(q.getUUID("owner")),now);}
 }
 /** One escort quest's arrivals with its owner given, not looked up: the tick passes the online player, a test passes its own. */
 public static void escorted(ServerLevel l,SettlementData.Entry e,UUID id,ServerPlayer owner,long now){
  var q=quest(l,e.settlement().id(),id);if(q!=null&&q.getString("state").equals(TAKEN)&&escort(q.getString("template")))escorted(l,e,q,owner,now);
 }
 private static boolean listed(ListTag list,UUID id){return list.stream().anyMatch(x->NbtUtils.loadUUID(x).equals(id));}
 private static void escorted(ServerLevel l,SettlementData.Entry e,CompoundTag q,ServerPlayer owner,long now){
  var village=e.settlement().id();var root=root(q);var arrived=q.getList("arrived",Tag.TAG_INT_ARRAY);var before=q.getList("before",Tag.TAG_INT_ARRAY);
  boolean changed=false;
  for(var npc:l.getEntitiesOfClass(ResidentEntity.class,new net.minecraft.world.phys.AABB(e.center()).inflate(Camps.ARRIVE),x->x.isAlive()&&Camps.companion(x,root))){
   if(listed(arrived,npc.getUUID())||listed(before,npc.getUUID()))continue;
   arrived.add(NbtUtils.createUUID(npc.getUUID()));Camps.arrived(l,e,npc,now);changed=true;}
  // AD-106: the board keeps where each of its people was last seen and what they were doing, to speak of them while their ground sleeps.
  var roster=q.getCompound("roster");boolean seen=false;
  for(var id:members(l,q)){if(listed(arrived,id)||listed(before,id))continue;var npc=loaded(l.getServer(),id);if(npc==null)continue;
   var old=roster.getCompound(id.toString());var dim=npc.level().dimension().location().toString();
   boolean moved=!old.contains("pos")||BlockPos.of(old.getLong("pos")).distSqr(npc.blockPosition())>=32*32;
   if(moved||!old.getString("state").equals(npc.escortState())||!old.getString("reason").equals(npc.escortReason())||!old.getString("dim").equals(dim)||Math.abs(old.getFloat("hp")-npc.getHealth())>=2){
    var t=new CompoundTag();t.putString("name",npc.getDisplayName().getString());t.putString("state",npc.escortState());t.putString("reason",npc.escortReason());
    t.putFloat("hp",npc.getHealth());t.putFloat("max",npc.getMaxHealth());t.putLong("pos",npc.blockPosition().asLong());t.putString("dim",dim);t.putLong("seen",now);
    roster.put(id.toString(),t);seen=true;}}
  if(changed||seen){q.put("arrived",arrived);q.putInt("progress",arrived.size());if(seen)q.put("roster",roster);replace(l,village,q);}
  // QUEST-005: the quest closes once everybody is accounted for — home, or lost on the way. A guest waiting for a bed is already home,
  // so a rescue is never held back until the village has room for its people.
  int out=outThere(l,q);
  if(q.getInt("progress")>=q.getInt("target")||out==0){
   if(q.getInt("progress")==0){settle(l,village,q,FAILED,"people_lost",owner);return;}
   if(owner!=null)complete(owner,village,q.getUUID("id"));}
 }
 /** The people of an escort still out there: its own list of travellers or site people, less those who arrived and those who died on the way.
  *  A person whose chunk sleeps is still out there. Returns -1 when the quest knows nobody, which is never read as everybody lost. */
 static int outThere(ServerLevel l,CompoundTag q){
  var members=members(l,q);
  if(members.isEmpty())return -1;
  var arrived=q.getList("arrived",Tag.TAG_INT_ARRAY);var before=q.getList("before",Tag.TAG_INT_ARRAY);var lost=q.getList("lost",Tag.TAG_INT_ARRAY);
  int out=0;for(var id:members)if(!listed(arrived,id)&&!listed(before,id)&&!listed(lost,id))out++;
  return out;
 }
 /** The people of an escort quest: the travellers of its camp, or the people of its site. */
 static List<UUID> members(ServerLevel l,CompoundTag q){
  var members=new ArrayList<UUID>();
  if(q.hasUUID("camp")){var camp=Camps.camp(l,q.getUUID("camp"));if(camp!=null)for(var raw:camp.getList("travellers",Tag.TAG_COMPOUND))members.add(((CompoundTag)raw).getUUID("id"));}
  else{var site=QuestSites.site(l,root(q));if(site!=null)members.addAll(QuestSites.people(site));}
  return members;
 }
 /** A living resident with this id in any loaded world, or null. */
 static ResidentEntity loaded(MinecraftServer server,UUID id){
  for(var level:server.getAllLevels())if(level.getEntity(id) instanceof ResidentEntity npc&&npc.isAlive())return npc;
  return null;
 }
 /** AD-106: the player who holds the quest the world knows by this id, or null while nobody does. */
 static UUID ownerOf(ServerLevel l,UUID root){
  var found=find(l,root);if(found==null)return null;var q=(CompoundTag)found[1];
  return q.getString("state").equals(TAKEN)&&q.hasUUID("owner")?q.getUUID("owner"):null;
 }
 public static CompoundTag callDefence(ServerLevel l,SettlementData.Entry e,long now){
  var board=board(l,e.settlement().id());
  if(board.getList("quests",Tag.TAG_COMPOUND).stream().anyMatch(t->{var q=(CompoundTag)t;return q.getString("template").equals(DEFENCE)&&(q.getString("state").equals(OPEN)||q.getString("state").equals(TAKEN));}))return null;
  long inside=l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(e.center()).inflate(spec(DEFENCE).get("radius").getAsInt()),m->m instanceof Enemy&&m.isAlive()).size();
  if(inside<2||open(board)>=MAX_OPEN)return null;
  var q=quest(e.settlement().id(),DEFENCE,now,spec(DEFENCE).get("deadline").getAsLong());q.putInt("target",spec(DEFENCE).get("count").getAsInt());q.putLong("coins",coins(DEFENCE));q.putLong("reputation",reputation(DEFENCE));
  replace(l,e.settlement().id(),q);return q;
 }
 private static CompoundTag quest(UUID village,String template,long now,long deadline){
  var q=new CompoundTag();q.putUUID("id",UUID.randomUUID());q.putUUID("village",village);q.putString("template",template);q.putString("state",OPEN);q.putString("item","");q.putInt("target",0);q.putInt("progress",0);q.putLong("posted",now);q.putLong("deadline",now+deadline);q.putLong("coins",0);q.putLong("reputation",0);return q;}
 private static String key(Item item){return BuiltInRegistries.ITEM.getKey(item).toString();}
 /** True when this player is the owner of that quest in any settlement. */
 public static boolean owned(ServerPlayer p,UUID quest){
  // The people and things out there carry the id of the first card: the card posted again after an abandonment answers to it too.
  var l=p.serverLevel();for(var e:SettlementData.get(p.server).entries())for(var raw:board(l,e.settlement().id()).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
   if((q.getUUID("id").equals(quest)||root(q).equals(quest))&&q.getString("state").equals(TAKEN)&&q.hasUUID("owner")&&q.getUUID("owner").equals(p.getUUID()))return true;}
  return false;
 }
 /** One owner per quest: the first accepted request wins, later ones are refused. */
 /** Read-only authority shared by accepting a quest and per-player resident markers. */
 public static String takeRefusal(ServerPlayer p,UUID village,CompoundTag q){
  if(q==null)return "unknown";
  if(!q.getString("state").equals(OPEN))return q.hasUUID("owner")&&q.getUUID("owner").equals(p.getUUID())?"yours":"taken";
  long score=PropertyLedger.get(p.server).roll(village).account(p.getUUID()).score();
  if(score<0)return "distrust";
  // AD-099: the next stage of a chain is kept for the player who found the trail; AD-097: the rest waits for enough trust.
  if(q.hasUUID("reserved")&&!q.getUUID("reserved").equals(p.getUUID()))return "reserved";
  // AD-105: whoever gave this need up once does not take it back with a fresh deadline.
  if(listed(q.getList("given_up",Tag.TAG_INT_ARRAY),p.getUUID()))return "given_up";
  if(!q.hasUUID("reserved")&&score<trust(q.getString("template")))return "trust";
  return impossible(p.serverLevel(),q).isEmpty()?"":"gone";
 }
 public static String take(ServerPlayer p,UUID village,UUID id){
  var l=p.serverLevel();var q=quest(l,village,id);String refusal=takeRefusal(p,village,q);
  if(!refusal.isEmpty()){if(refusal.equals("gone"))settle(l,village,q,CANCELLED,impossible(l,q),null);return refusal;}
  q.putUUID("owner",p.getUUID());q.putString("state",TAKEN);q.putLong("taken",SettlementData.get(p.server).clock().ticks());
  // AD-080: a quest that points somewhere hands out a chart with a cross on the place, instead of coordinates in the chat. The record
  // remembers where that cross stands, so a place later built elsewhere knows whether its player needs a new chart.
  if(q.contains("site"))q.putLong("charted",q.getLong("site"));
  replace(l,village,q);
  if(q.contains("site"))Adventures.giveChart(p,village,q);
  Wilds.onTake(p,village,q);
  return "ok";
 }
 /** Why an errand nobody has taken is not worth asking for any more, or "": the goods are no longer wanted, or the village got what it asked
  *  for another way. A card somebody is already working on is left alone — their work is paid whether the need has passed or not. */
 public static String stale(ServerLevel l,SettlementData.Entry e,CompoundTag q){
  if(!q.getString("state").equals(OPEN))return "";
  var template=q.getString("template");
  if(template.equals(SUPPLY)){
   var item=BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(q.getString("item")));
   for(var want:Workshops.wants(l,e))if(want.ingredient().test(new ItemStack(item)))return "";
   return "need_gone";}
  if(template.equals(CropQuests.CROP))return CropUnlocks.unlocked(l,e,q.getString("item"))?"have_it":"";
  if(AnimalQuests.is(template))return AnimalUnlocks.unlocked(l,e,AnimalQuests.kind(template))?"have_it":"";
  return "";
 }
 /** Why nobody could do this errand any more, or "" when somebody still can: its people are all gone, or its caravan is off the road. */
 static String impossible(ServerLevel l,CompoundTag q){
  var template=q.getString("template");
  if(escort(template)&&outThere(l,q)==0)return "people_lost";
  if(template.equals(Wilds.ESCORT)){var contract=Caravans.contract(l.getServer(),q.getUUID("contract"));
   if(contract==null||!contract.getString("state").equals(Caravans.TRANSIT))return "caravan_gone";}
  var animals=AnimalQuests.impossible(l,q);if(!animals.isEmpty())return animals;
  var greys=WolfRescue.impossible(l,q);if(!greys.isEmpty())return greys;
  return "";
 }
 /** What belongs to one player's attempt and does not pass to the next taker of the same need. The embassy's answer is the village's:
  *  a letter answered once is not answered again for whoever takes the card next. */
 private static final List<String> ATTEMPT=List.of("owner","taken","reserved","closed","charted","joined","samples","escorted","ambushed","ambush","visited","reason","roster","penned","foxes_killed");
 /** Abandoning a taken quest costs reputation; it is not a program error and the need returns to the board as a new card (AD-040).
  *  AD-105: what was really delivered stays paid to the one who delivered it, and the new card asks only for the rest. A card bound to a
  *  place keeps the id the world knows it by as its origin, so its site, its people and its relic still answer the next taker. */
 public static String abandon(ServerPlayer p,UUID village,UUID id){
  var l=p.serverLevel();var q=quest(l,village,id);if(q==null||!q.getString("state").equals(TAKEN)||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))return "unknown";
  var template=q.getString("template");long now=SettlementData.get(p.server).clock().ticks();
  int target=Math.max(1,q.getInt("target")),done=counted(template)?Math.min(q.getInt("progress"),target):0;
  // A deed already done, or people all accounted for, is not given up: it is settled as the success it is.
  boolean finished=q.getInt("target")>0&&q.getInt("progress")>=q.getInt("target")||escort(template)&&q.getInt("progress")>0&&outThere(l,q)==0;
  if(finished&&TradeLedger.get(p.server).deal(Settlement.childId(id,"completion"))==null){settle(l,village,q,DONE,"",p);return "done";}
  if(done>0)pay(l,village,q,p);
  penalty(p.server,village,p.getUUID(),ABANDON_REPUTATION);q.putString("state",ABANDONED);q.putLong("closed",now);replace(l,village,q);
  // Companions who were following the player who gave up stop where they stand; the ones farther away stop once they notice (EscortGoal).
  var root=root(q);
  for(var npc:l.getEntitiesOfClass(ResidentEntity.class,p.getBoundingBox().inflate(128),x->p.getUUID().equals(x.escortPlayer())&&Camps.companion(x,root)))npc.escort(null);
  // The one written thing of a barrow, a sanctuary or a crypt goes back where it lay; if it is neither with the player nor there, nobody can bring it.
  if(Adventures.relicLike(template)&&!Adventures.returnRelic(l,p,q))return "ok";
  var fresh=q.copy();fresh.putUUID("id",UUID.randomUUID());fresh.putUUID("origin",root);fresh.putString("state",OPEN);
  for(var key:ATTEMPT)fresh.remove(key);
  var gaveUp=q.getList("given_up",Tag.TAG_INT_ARRAY).copy();gaveUp.add(NbtUtils.createUUID(p.getUUID()));fresh.put("given_up",gaveUp);
  // The need keeps its own time, with at least a quarter of a full window left for the next taker.
  fresh.putInt("progress",0);fresh.putLong("deadline",Math.max(q.getLong("deadline"),now+deadline(template)/4));
  int left=target-done;
  // People already brought home are not counted again, and the new card asks only for those still out there.
  if(escort(template)){var before=q.getList("before",Tag.TAG_INT_ARRAY).copy();before.addAll(q.getList("arrived",Tag.TAG_INT_ARRAY));fresh.put("before",before);fresh.put("arrived",new ListTag());
   int out=outThere(l,fresh);if(out>=0)left=Math.min(left,out);}
  if(left>0&&left<target){fresh.putInt("target",left);fresh.putLong("coins",q.getLong("coins")*left/target);fresh.putLong("reputation",q.getLong("reputation")*left/target);}
  // AD-140: judged on what it asks for now; an animal card that is not posted again leaves its place and waits like a failed one.
  if(left<=0||!impossible(l,fresh).isEmpty()){AnimalQuests.closed(l,village,q,CANCELLED,"");return "ok";}
  if(Wilds.handsOver(template)||template.equals(Wilds.SURVEY)||template.equals(Wilds.ESCORT))fresh.putString("status",q.getBoolean("answered")?"reply":"");
  replace(l,village,fresh);return "ok";
 }
 private static void penalty(MinecraftServer server,UUID village,UUID player,int amount){var ledger=PropertyLedger.get(server);ledger.roll(village).decay(SettlementData.get(server).clock().ticks());ledger.theft(village,player,amount);}
 /** Confirmed trade with the village advances supply and donation quests of that village. */
 public static void onDeal(ServerPlayer p,UUID village,String item,int count,long coins,boolean donation){
  var l=p.serverLevel();var board=board(l,village);
  for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
   if(!q.getString("state").equals(TAKEN)||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))continue;
   if(q.getString("template").equals(SUPPLY)&&q.getString("item").equals(item))q.putInt("progress",q.getInt("progress")+count);
   else if(q.getString("template").equals(DONATION)&&donation)q.putInt("progress",(int)Math.min(Integer.MAX_VALUE,q.getInt("progress")+coins));
   else continue;
   replace(l,village,q);complete(p,village,q.getUUID("id"));}
 }
 /** A hostile killed by the owner near the village advances clearing and defence quests. */
 public static void onKill(ServerPlayer p,LivingEntity victim){
  if(!(victim instanceof Enemy)||!(p.level() instanceof ServerLevel l))return;
  for(var e:SettlementData.get(p.server).entries()){
   if(!e.dimension().equals(l.dimension().location().toString()))continue;
   var board=board(l,e.settlement().id());boolean changed=false;
   for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
    if(!q.getString("state").equals(TAKEN)||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))continue;
    var template=q.getString("template");if(!template.equals(CLEARING)&&!template.equals(DEFENCE))continue;
    int radius=spec(template).get("radius").getAsInt();if(victim.blockPosition().distSqr(e.center())>(long)radius*radius)continue;
    q.putInt("progress",q.getInt("progress")+1);changed=true;}
   if(changed){save(l,e.settlement().id(),board);for(var raw:board.getList("quests",Tag.TAG_COMPOUND))complete(p,e.settlement().id(),((CompoundTag)raw).getUUID("id"));}
  }
 }
 /** Pays the reward exactly once, keyed by the completion id written into the quest record. */
 public static boolean complete(ServerPlayer p,UUID village,UUID id){
  var l=p.serverLevel();var q=quest(l,village,id);
  if(q==null||!q.getString("state").equals(TAKEN)||q.getInt("progress")<=0||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))return false;
  boolean partial=q.getString("template").equals(RESCUE)||Adventures.partial(q.getString("template"));if(q.getInt("progress")<q.getInt("target")&&!partial)return false;
  if(TradeLedger.get(p.server).deal(Settlement.childId(id,"completion"))!=null)return false;
  // QUEST-004: a partial rescue pays for the part that really arrived; AD-099: a finished stage of a chain names the next place.
  settle(l,village,q,DONE,"",p);
  return true;
 }
 /** Expired quests fail with a reputation penalty for their owner; the need itself may be posted again later.
  *  AD-105: every record is read fresh and written on its own, so nothing written during this pass is overwritten by an older copy. */
 public static void tick(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();
  payDone(l,village);
  if(now%POST_EVERY==0){
   // Distant templates rotate while a scouted camp lead is free; otherwise the village asks for what it lacks at home.
   var distant=new String[]{CARGO,BRING,RESCUE}[(int)((now/POST_EVERY)%3)];
   if(Expeditions.lead(l,e.settlement().id(),Expeditions.CAMP,true)==null||postDistant(l,e,distant,now)==null)post(l,e,now);}
  // AD-075: on its own, rarer window the village sends the player out on an adventure instead of asking for goods.
  if(now%Adventures.EVERY==0)Adventures.postNext(l,e,now);
  // AD-088: a crop or sapling the village cannot plant yet is asked for on its own, rarer window.
  if(now%CropQuests.EVERY==0)CropQuests.post(l,e,now);
  // AD-098: a resident asks in person for what their own workshop lacks.
  if(now%Pleas.EVERY==0)Pleas.post(l,e,now);
  Pleas.tick(l,e,now);
  Chains.tick(l,e,now);
  // QUEST-002: the village asks for word of a place thousands of blocks away, and the far errands take their step.
  if(now%Far.EVERY==0){Far.post(l,e,now);Far.postFreight(l,e,now);}
  Far.tick(l,e,now);
  // AD-140: the yard asks for the next kind of animal it keeps and has not got, and the animal quests take their step.
  if(now%AnimalQuests.EVERY==0)AnimalQuests.post(l,e,now);
  AnimalQuests.tick(l,e,now);
  // AD-150: the kennel with room in it asks for the greys of a poachers' camp, and that errand takes its step.
  if(now%AnimalQuests.EVERY==0)WolfRescue.post(l,e,now);
  WolfRescue.tick(l,e,now);
  Adventures.tick(l,e,now);
  Wilds.tick(l,e,now);
  companions(l,e,now);
  // The clock is read last, after this pass has recorded every deed the world saw, so a deed done in time is never lost to the order of the passes.
  payDone(l,village);
  boolean expired=false;
  for(var id:ids(board(l,village),OPEN,TAKEN)){var q=quest(l,village,id);
   if(q==null||now<=q.getLong("deadline"))continue;var state=q.getString("state");if(!state.equals(OPEN)&&!state.equals(TAKEN))continue;
   var owner=q.hasUUID("owner")?l.getServer().getPlayerList().getPlayer(q.getUUID("owner")):null;
   // QUEST-005: people who really arrived are a success the clock does not take back; goods really delivered are paid even on a failure.
   settle(l,village,q,state.equals(TAKEN)&&escort(q.getString("template"))&&q.getInt("progress")>0?DONE:FAILED,"deadline",owner);expired=true;}
  // AD-041: an errand nobody took that the village no longer needs is taken down, blaming nobody.
  for(var id:ids(board(l,village),OPEN)){var q=quest(l,village,id);if(q==null)continue;
   var why=stale(l,e,q);if(!why.isEmpty())settle(l,village,q,CANCELLED,why,null);}
  // A place whose errand just ran out is closed in the same pass, so its scouted lead is free again at once.
  if(expired)Adventures.tick(l,e,now);
 }
 /** A deed done while its owner was away is paid at once: into the inventory when they are here, as coins owed to claim at the stall when not. */
 private static void payDone(ServerLevel l,UUID village){
  for(var id:ids(board(l,village),TAKEN)){var q=quest(l,village,id);
   if(q==null||!q.getString("state").equals(TAKEN)||!q.hasUUID("owner")||q.getInt("target")<=0||q.getInt("progress")<q.getInt("target"))continue;
   if(TradeLedger.get(l.getServer()).deal(Settlement.childId(id,"completion"))!=null)continue;
   settle(l,village,q,DONE,"",l.getServer().getPlayerList().getPlayer(q.getUUID("owner")));}
 }
 /** QUEST-003 (AD-106): each of a player's people on the board — what they do, why they wait, where they are from the player, how hurt,
  *  whether danger or a boat is near. A person whose ground sleeps is shown as last seen. */
 private static ListTag people(ServerPlayer p,CompoundTag q){
  var l=p.serverLevel();var out=new ListTag();var arrived=q.getList("arrived",Tag.TAG_INT_ARRAY);var before=q.getList("before",Tag.TAG_INT_ARRAY);var lost=q.getList("lost",Tag.TAG_INT_ARRAY);
  var roster=q.getCompound("roster");
  for(var id:members(l,q)){if(listed(before,id)||listed(lost,id))continue;
   var t=new CompoundTag();t.putUUID("id",id);var npc=loaded(l.getServer(),id);var last=roster.getCompound(id.toString());
   t.putString("name",npc!=null?npc.getDisplayName().getString():last.getString("name"));t.putBoolean("live",npc!=null);
   if(listed(arrived,id)){t.putString("state","arrived");t.putString("reason","");out.add(t);continue;}
   if(npc!=null){
    t.putString("state",npc.escortState());t.putString("reason",npc.escortReason());t.putFloat("hp",npc.getHealth());t.putFloat("max",npc.getMaxHealth());
    t.putInt("danger",npc.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,npc.getBoundingBox().inflate(12),m->m.isAlive()&&m instanceof Enemy).size());
    t.putString("boat",npc.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat?"riding":EscortGoal.freeBoat(npc,16)!=null?"near":"none");
    if(npc.level()==p.level()){t.putString("dir",Adventures.bearing(p.blockPosition(),npc.blockPosition()));t.putInt("away",Adventures.away(p.blockPosition(),npc.blockPosition()));}
    else t.putString("dim",npc.level().dimension().location().getPath());
   }else if(!last.contains("state")){t.putString("state","unseen");t.putString("reason","");
   }else{
    t.putString("state",last.getString("state"));t.putString("reason",last.getString("reason"));t.putFloat("hp",last.getFloat("hp"));t.putFloat("max",last.getFloat("max"));t.putString("boat","none");
    if(last.contains("pos")){var at=BlockPos.of(last.getLong("pos"));var dim=last.getString("dim");
     if(dim.equals(p.level().dimension().location().toString())){t.putString("dir",Adventures.bearing(p.blockPosition(),at));t.putInt("away",Adventures.away(p.blockPosition(),at));}
     else t.putString("dim",dim.substring(dim.indexOf(':')+1));}}
   out.add(t);}
  return out;
 }
 /** The player's own errands, in every village that holds one: what to do, how far along it is, how long is left and which way its place lies.
  *  This is what the quests tab of the inventory shows, so it is answered wherever the player stands, not only at a board. */
 public static CompoundTag log(ServerPlayer p){
  var out=new CompoundTag();var villages=new ListTag();var l=p.serverLevel();long now=SettlementData.get(p.server).clock().ticks();
  for(var e:SettlementData.get(p.server).entries()){
   if(!e.dimension().equals(l.dimension().location().toString()))continue;
   var mine=new ListTag();
   for(var raw:board(l,e.settlement().id()).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
    if(!q.getString("state").equals(TAKEN)||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))continue;
    var row=new CompoundTag();row.putUUID("id",q.getUUID("id"));row.putString("template",q.getString("template"));row.putString("item",q.getString("item"));
    row.putInt("target",q.getInt("target"));row.putInt("progress",q.getInt("progress"));row.putLong("coins",q.getLong("coins"));row.putLong("reputation",q.getLong("reputation"));
    row.putLong("left",Math.max(0,q.getLong("deadline")-now));row.putString("status",q.getString("status"));row.putInt("status_left",q.getInt("status_left"));
    row.putString("giver",q.getString("giver_name"));
    if(q.getInt("stages")>0){row.putString("chain",q.getString("chain"));row.putInt("stage",q.getInt("stage"));row.putInt("stages",q.getInt("stages"));}
    row.putBoolean("handover",handsOver(q));
    if(q.contains("site")){var site=BlockPos.of(q.getLong("site"));row.putString("kind",q.getString("kind"));
     row.putString("dir",Adventures.bearing(p.blockPosition(),site));row.putInt("away",Adventures.away(p.blockPosition(),site));}
    if(escort(q.getString("template")))row.put("people",people(p,q));
    mine.add(row);}
   if(mine.isEmpty())continue;
   var village=new CompoundTag();village.putUUID("id",e.settlement().id());village.put("quests",mine);
   // AD-145: the village is told apart by its own name; where it lies from the player is said after it.
   village.putString("name",e.settlement().name());
   village.putString("dir",Adventures.bearing(p.blockPosition(),e.center()));village.putInt("away",Adventures.away(p.blockPosition(),e.center()));
   village.putLong("score",PropertyLedger.get(p.server).roll(e.settlement().id()).account(p.getUUID()).score());
   villages.add(village);}
  out.put("villages",villages);return out;
 }
 public static void addView(ServerPlayer p,CompoundTag tag){
  if(!tag.hasUUID("village"))return;var l=p.serverLevel();var village=tag.getUUID("village");var list=new ListTag();
  long score=PropertyLedger.get(p.server).roll(village).account(p.getUUID()).score();
  for(var raw:board(l,village).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var state=q.getString("state");if(!state.equals(OPEN)&&!state.equals(TAKEN))continue;
   var view=new CompoundTag();view.putUUID("id",q.getUUID("id"));view.putString("template",q.getString("template"));view.putString("item",q.getString("item"));view.putInt("target",q.getInt("target"));view.putInt("progress",q.getInt("progress"));
   if(q.getString("template").equals(Adventures.BEACON)){view.putInt("towerStone",spec(Adventures.BEACON).get("stone").getAsInt());view.putInt("towerCoal",spec(Adventures.BEACON).get("coal").getAsInt());}
   view.putLong("coins",q.getLong("coins"));view.putLong("reputation",q.getLong("reputation"));view.putBoolean("mine",q.hasUUID("owner")&&q.getUUID("owner").equals(p.getUUID()));view.putBoolean("taken",state.equals(TAKEN));
   view.putLong("left",Math.max(0,q.getLong("deadline")-SettlementData.get(p.server).clock().ticks()));
   // AD-075: the board names the place an adventure leads to and what the last report from it said.
   view.putBoolean("handover",handsOver(q));
   // AD-097..AD-099: the trust an errand needs, whose request or whose kin it is, and where in its chain it stands.
   boolean reserved=q.hasUUID("reserved")&&!q.getUUID("reserved").equals(p.getUUID());
   view.putInt("trust",trust(q.getString("template")));view.putLong("score",score);view.putBoolean("reserved",reserved);
   view.putBoolean("locked",state.equals(OPEN)&&!q.hasUUID("reserved")&&score<trust(q.getString("template")));
   view.putString("giver",q.getString("giver_name"));
   if(q.contains("chain")){view.putString("chain",q.getString("chain"));view.putInt("stage",q.getInt("stage"));view.putInt("stages",q.getInt("stages"));}
   // AD-106: the player's own people out there, one by one.
   if(state.equals(TAKEN)&&q.hasUUID("owner")&&q.getUUID("owner").equals(p.getUUID())&&escort(q.getString("template")))view.put("people",people(p,q));
   view.putString("kind",q.getString("kind"));view.putString("status",q.getString("status"));view.putInt("status_left",q.getInt("status_left"));
   // AD-080: the board says which way and how far, never the exact numbers; the cross is on the chart.
   var e=SettlementData.get(p.server).entry(village);
   if(q.contains("site")&&e!=null){var site=BlockPos.of(q.getLong("site"));
    view.putString("dir",Adventures.bearing(e.center(),site));view.putInt("away",Adventures.away(e.center(),site));}
   list.add(view);}
  tag.put("quests",list);
  // QUEST-005: the people already brought home who are still waiting for a bed, so the mayor sees the hour running out.
  var e=SettlementData.get(p.server).entry(village);
  if(e!=null){var guests=Camps.waiting(l,e,SettlementData.get(p.server).clock().ticks());
   if(!guests.isEmpty()){var block=new CompoundTag();block.put("list",guests);block.putBoolean("bed",Camps.bedFree(e));tag.put("guests",block);}}
 }
}
