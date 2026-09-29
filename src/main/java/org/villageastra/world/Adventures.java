package org.villageastra.world;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
/** AD-075: the adventures a village sends the player on. Four of them, each pointing at a real place the expeditioner scouted and
 *  the mod then built: mine out a far seam, break a captive out of a bandit stockade, treat and walk home a wrecked expedition,
 *  open a sealed barrow for its ancient volume. They pay in coin and, above all, in the reputation that leads to the office. */
public final class Adventures {
 public static final String LODE="lode",CAPTIVE="captive",LOST="lost",RELIC="relic";
 /** AD-089/AD-090: the raiders' lair, a mine collapse, a beast's den and a signal tower. */
 public static final String LAIR="lair",COLLAPSE="collapse",BEAST="beast",BEACON="beacon";
 /** Adventures that stand at a site the mod builds. */
 public static final List<String> SITES=List.of(LODE,CAPTIVE,LOST,RELIC,LAIR,COLLAPSE,BEAST,BEACON,Chains.SANCTUARY,Chains.CRYPT,Chains.WARCAMP,Chains.RICHLODE);
 /** Every adventure on the board: the built sites and the ones that follow the world as it is (AD-091). */
 public static final List<String> TEMPLATES;
 /** AD-099: what the board's own windows offer — every adventure but the stages only a chain leads to. */
 public static final List<String> ROTATION;
 static{var all=new ArrayList<>(SITES);all.addAll(Wilds.TEMPLATES);TEMPLATES=List.copyOf(all);
  var offered=new ArrayList<>(TEMPLATES);offered.removeAll(Chains.STAGES);ROTATION=List.copyOf(offered);}
 public static final int EVERY=Quests.number("adventure_every"),MAX_OPEN=Quests.number("adventure_max_open");
 /** How much reading an ancient volume saves the scholars of the village. */
 public static final long RESEARCH_TICKS=Quests.spec(RELIC).get("research_ticks").getAsLong();
 /** A seam the mine really exposes: the ore in the wall and the material it gives a pickaxe. */
 public record Seam(Block stone,Block deep,Item drop){}
 private static final List<Seam> SEAMS=List.of(
  new Seam(Blocks.IRON_ORE,Blocks.DEEPSLATE_IRON_ORE,Items.RAW_IRON),
  new Seam(Blocks.COAL_ORE,Blocks.DEEPSLATE_COAL_ORE,Items.COAL),
  new Seam(Blocks.COPPER_ORE,Blocks.DEEPSLATE_COPPER_ORE,Items.RAW_COPPER),
  new Seam(Blocks.GOLD_ORE,Blocks.DEEPSLATE_GOLD_ORE,Items.RAW_GOLD),
  new Seam(Blocks.REDSTONE_ORE,Blocks.DEEPSLATE_REDSTONE_ORE,Items.REDSTONE),
  new Seam(Blocks.LAPIS_ORE,Blocks.DEEPSLATE_LAPIS_ORE,Items.LAPIS_LAZULI));
 private Adventures(){}
 public static boolean adventure(String template){return TEMPLATES.contains(template);}
 /** Templates finished by carrying something back to the village. */
 public static boolean handsOver(String template){return template.equals(LODE)||template.equals(Chains.RICHLODE)||relicLike(template)||Wilds.handsOver(template);}
 /** Templates finished by bringing the one written thing of their site to the scholars. */
 static boolean relicLike(String template){return template.equals(RELIC)||template.equals(Chains.SANCTUARY)||template.equals(Chains.CRYPT);}
 /** Templates finished by walking people home. */
 public static boolean escorts(String template){return template.equals(CAPTIVE)||template.equals(LOST)||template.equals(COLLAPSE);}
 /** A rescue pays for the survivors who really arrived, not only for a full party. */
 public static boolean partial(String template){return template.equals(LOST)||template.equals(COLLAPSE);}
 /** True for the adventures that stand on fighting: a world without monsters never gets one. */
 public static boolean needsFighters(String template){
  var spec=adventure(template)?Quests.spec(template):null;
  return spec!=null&&spec.has("fighters")&&spec.get("fighters").getAsInt()>0;
 }
 public static String site(String template){
  return switch(template){case LODE->QuestSites.ADIT;case CAPTIVE->QuestSites.STOCKADE;case LOST->QuestSites.WRECK;case RELIC->QuestSites.BARROW;
   case LAIR->QuestSites.HIDEOUT;case COLLAPSE->QuestSites.COLLAPSE;case BEAST->QuestSites.DEN;case BEACON->QuestSites.TOWER;
   case Chains.SANCTUARY->QuestSites.SANCTUM;case Chains.CRYPT->QuestSites.CRYPT;case Chains.WARCAMP->QuestSites.WARCAMP;case Chains.RICHLODE->QuestSites.ADIT;default->"";};
 }
 /** The adventure this window offers; they take turns so a village never repeats itself. */
 public static String rotate(long now){return ROTATION.get((int)Math.floorMod(now/Math.max(1,EVERY),ROTATION.size()));}
 /** One window of the board: starting from this window's turn, the first adventure the world really allows is posted. */
 public static CompoundTag postNext(ServerLevel l,SettlementData.Entry e,long now){
  int start=(int)Math.floorMod(now/Math.max(1,EVERY),ROTATION.size());
  for(int i=0;i<ROTATION.size();i++){var template=ROTATION.get((start+i)%ROTATION.size());
   var q=SITES.contains(template)?post(l,e,template,now):Wilds.post(l,e,template,now);if(q!=null)return q;}
  return null;
 }
 /** Whether the board has room for this adventure: the caps are shared by every kind, and one kind is never posted twice at once. */
 static boolean room(CompoundTag board,String template){
  int open=0,adventures=0;
  for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var state=q.getString("state");
   if(!state.equals(Quests.OPEN)&&!state.equals(Quests.TAKEN))continue;
   open++;if(adventure(q.getString("template")))adventures++;
   if(q.getString("template").equals(template))return false;}
  return open<Quests.MAX_OPEN&&adventures<MAX_OPEN;
 }
 // ---------------------------------------------------------------- posting
 private static CompoundTag lead(ServerLevel l,UUID village,String template){
  boolean rock=template.equals(LODE)||template.equals(COLLAPSE);
  String first=rock?Expeditions.RESOURCE:Expeditions.CAMP,second=rock?Expeditions.CAMP:Expeditions.RESOURCE;
  var lead=Expeditions.lead(l,village,first,true);return lead!=null?lead:Expeditions.lead(l,village,second,true);
 }
 /** The ore the village would really use, or the next one in turn when it wants nothing of the kind. */
 private static Seam seam(ServerLevel l,SettlementData.Entry e,long now){
  for(var want:Workshops.wants(l,e))for(var seam:SEAMS)if(want.matches(new ItemStack(seam.drop())))return seam;
  return SEAMS.get((int)Math.floorMod(now/Math.max(1,EVERY),SEAMS.size()));
 }
 /** Goods the bandits took or the expedition carried: something this village actually wants. */
 private static ItemStack loot(ServerLevel l,SettlementData.Entry e){
  for(var want:Workshops.wants(l,e))for(var stack:want.ingredient().getItems())if(Trade.price(stack.getItem())!=null)return new ItemStack(stack.getItem(),8);
  return new ItemStack(Items.IRON_INGOT,4);
 }
 // ---------------------------------------------------------------- the chart the board hands out
 private static final String[] POINTS={"east","southeast","south","southwest","west","northwest","north","northeast"};
 /** Which way a place lies from the village, in eight points. */
 public static String bearing(BlockPos from,BlockPos to){
  double angle=Math.toDegrees(Math.atan2(to.getZ()-from.getZ(),to.getX()-from.getX()));
  return POINTS[(int)Math.floorMod(Math.round(angle/45.0),8L)];
 }
 public static int away(BlockPos from,BlockPos to){return (int)Math.round(Math.sqrt(from.distSqr(to)));}
 /** AD-080: a real filled map of the country between the village and the place, with a cross standing on the place itself. */
 public static ItemStack chart(ServerLevel l,SettlementData.Entry e,CompoundTag q){
  var site=BlockPos.of(q.getLong("site"));var home=e.center();var frame=frame(site,home);
  var stack=MapItem.create(l,frame[0],frame[1],(byte)frame[2],true,true);
  MapItem.renderBiomePreviewMap(l,stack);
  // A place lying exactly on the map grid's edge would lose its cross at every scale: the cross is held one block inside the chart instead.
  int cx=snapped(frame[0],frame[2]),cz=snapped(frame[1],frame[2]),r=63<<frame[2];
  var mark=new BlockPos(Math.max(cx-r,Math.min(cx+r,site.getX())),site.getY(),Math.max(cz-r,Math.min(cz+r,site.getZ())));
  MapItemSavedData.addTargetDecoration(stack,mark,"astra_quest",MapDecoration.Type.RED_X);
  // QUEST-005: the village itself is marked too — the place people are brought back to — whenever it fits on the same chart.
  boolean homeShown=onMap(home,snapped(frame[0],frame[2]),snapped(frame[1],frame[2]),frame[2]);
  if(homeShown)MapItemSavedData.addTargetDecoration(stack,home,"astra_home",MapDecoration.Type.TARGET_POINT);
  stack.setHoverName(Component.translatable("quest.villageastra.chart",place(q)));
  var lore=new ListTag();
  lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("quest.villageastra.chart_lore").withStyle(net.minecraft.ChatFormatting.GRAY))));
  if(homeShown&&Quests.escort(q.getString("template")))
   lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("quest.villageastra.chart_home",Camps.ARRIVE).withStyle(net.minecraft.ChatFormatting.GRAY))));
  stack.getOrCreateTagElement("display").put("Lore",lore);
  return stack;
 }
 /** Vanilla snaps a map's centre to a grid of 128·2^scale blocks, and a mark more than 63 map pixels from that centre is dropped. */
 private static int snapped(int x,int scale){return org.villageastra.domain.Charts.snapped(x,scale);}
 private static boolean onMap(BlockPos p,int cx,int cz,int scale){return org.villageastra.domain.Charts.onMap(p.getX(),p.getZ(),cx,cz,scale);}
 /** AD-105/QUEST-002: where a chart is drawn and how far out — {x, z, scale}; the arithmetic itself is domain/Charts. */
 static int[] frame(BlockPos site,BlockPos home){return org.villageastra.domain.Charts.frame(site.getX(),site.getZ(),home.getX(),home.getZ());}
 /** The name of the place a quest points at: its design out in the world, or a camp for the older distant errands. */
 public static Component place(CompoundTag q){
  var kind=q.getString("kind");
  return Component.translatable("quest.villageastra.place."+(kind.isEmpty()?"camp":kind));
 }
 /** Handing the chart over is how a quest gives its place away: the chat never carries coordinates. */
 public static void giveChart(ServerPlayer p,UUID village,CompoundTag q){
  var e=SettlementData.get(p.server).entry(village);if(e==null||!q.contains("site"))return;
  var site=BlockPos.of(q.getLong("site"));var stack=chart(p.serverLevel(),e,q);
  if(!p.getInventory().add(stack))p.drop(stack,false);
  p.displayClientMessage(Component.translatable("quest.villageastra.chart_given",stack.getHoverName(),
   Component.translatable("quest.villageastra.side."+bearing(e.center(),site)),away(e.center(),site)),false);
 }
 /** The one volume of this barrow: it carries the quest it belongs to, so no copy from the village can be handed over instead. */
 public static ItemStack relic(UUID quest){return relic(quest,RELIC);}
 /** AD-099: the tablet of the sanctuary and the chronicle of the crypt are the same kind of thing, each named for its place. */
 static ItemStack relic(UUID quest,String template){
  var stack=new ItemStack(VillageAstra.RESEARCH_VOLUME.get());
  stack.getOrCreateTag().putString(QuestSites.RELIC,quest.toString());
  stack.setHoverName(Component.translatable(template.equals(RELIC)?"quest.villageastra.relic":"quest.villageastra.relic_"+template));
  return stack;
 }
 /** True when the volume of this quest really lies in the chest of the barrow. */
 private static boolean holdsRelic(ServerLevel l,CompoundTag site,UUID quest){
  if(!(l.getBlockEntity(BlockPos.of(site.getLong("chest"))) instanceof net.minecraft.world.Container chest))return false;
  for(int slot=0;slot<chest.getContainerSize();slot++)if(isRelic(chest.getItem(slot),quest))return true;
  return false;
 }
 /** AD-105: the written thing of an abandoned barrow, sanctuary or crypt goes back into its chest from the player who gave it up. True when
  *  it lies there now, so the need can be posted again; false when it is lost to the village. */
 static boolean returnRelic(ServerLevel l,ServerPlayer p,CompoundTag q){
  var root=Quests.root(q);var site=QuestSites.site(l,root);if(site==null)return false;
  if(holdsRelic(l,site,root))return true;
  var inventory=p.getInventory();
  for(int slot=0;slot<inventory.getContainerSize();slot++){var stack=inventory.getItem(slot);if(!isRelic(stack,root))continue;
   if(!(l.getBlockEntity(BlockPos.of(site.getLong("chest"))) instanceof net.minecraft.world.Container chest))return false;
   for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).isEmpty()){chest.setItem(i,stack.copyWithCount(1));chest.setChanged();stack.shrink(1);return true;}
   return false;}
  return false;
 }
 private static boolean isRelic(ItemStack stack,UUID quest){
  return stack.is(VillageAstra.RESEARCH_VOLUME.get())&&stack.hasTag()&&quest.toString().equals(stack.getTag().getString(QuestSites.RELIC));
 }
 /** Posts one adventure: the site is built first, and only a site that really stands puts a quest on the board. */
 public static CompoundTag post(ServerLevel l,SettlementData.Entry e,String template,long now){
  if(!SITES.contains(template)||Chains.STAGES.contains(template))return null;
  var village=e.settlement().id();var board=Quests.board(l,village);
  if(!room(board,template))return null;
  var spec=Quests.spec(template);
  // The raiders' lair follows a real wave: only after one was fought, and only once for it.
  long raid=0;
  if(template.equals(LAIR)){var last=Raids.record(l,village).getCompound("last");raid=last.getLong("ended");
   if(raid<=0||now-raid>spec.get("after_raid").getAsLong())return null;
   final long wave=raid;
   if(board.getList("quests",Tag.TAG_COMPOUND).stream().anyMatch(x->((CompoundTag)x).getString("template").equals(LAIR)&&((CompoundTag)x).getLong("raid")==wave))return null;}
  // One signal tower per village: once one burns, the board asks for no other.
  if(template.equals(BEACON)&&board.getList("quests",Tag.TAG_COMPOUND).stream().anyMatch(x->((CompoundTag)x).getString("template").equals(BEACON)&&((CompoundTag)x).getString("state").equals(Quests.DONE)))return null;
  // A quest that stands on fighting is never posted in a world without monsters.
  if(needsFighters(template)&&l.getDifficulty()==Difficulty.PEACEFUL)return null;
  var lead=lead(l,village,template);if(lead==null)return null;var anchor=BlockPos.of(lead.getLong("pos"));
  var q=Quests.blank(village,template,now,spec.get("deadline").getAsLong());var quest=q.getUUID("id");
  var seam=template.equals(LODE)?seam(l,e,now):null;
  var order=order(l,e,template,spec,quest,seam,anchor);
  var built=QuestSites.build(l,e,quest,order,anchor,now);if(built==null)return null;
  if(!sound(l,template,built,quest))return null;
  // AD-098: the people out there are somebody's kin: they carry the family name of a resident and go home to that house.
  if(escorts(template))Pleas.kin(l,e,q,built);
  Expeditions.useLead(l,village,anchor,true);
  q.putLong("site",QuestSites.origin(built).asLong());q.putString("kind",order.kind());
  q.putInt("target",spec.get("count").getAsInt());
  if(seam!=null)q.putString("item",BuiltInRegistries.ITEM.getKey(seam.drop()).toString());
  else if(template.equals(RELIC))q.putString("item",BuiltInRegistries.ITEM.getKey(VillageAstra.RESEARCH_VOLUME.get()).toString());
  if(raid>0)q.putLong("raid",raid);
  // AD-088: time paid like trade, and what the player brings or spends paid at the village's own prices.
  long goods=seam!=null?Quests.value(seam.drop(),spec.get("count").getAsInt())
   :template.equals(BEACON)?Quests.value(Items.STONE_BRICKS,spec.get("stone").getAsInt())+Quests.value(Items.COAL,spec.get("coal").getAsInt()):0;
  q.putLong("coins",Quests.coins(template)+goods);q.putLong("reputation",Quests.reputation(template));
  Quests.store(l,village,q);
  // The village tells the players nearby where its news comes from; the board keeps the details.
  for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
   player.sendSystemMessage(Component.translatable("quest.villageastra.posted",Component.translatable("quest.villageastra.template."+template),
    Component.translatable("quest.villageastra.place."+order.kind())));
  return q;
 }
 /** A site that came out without its people, its written thing or its fighters is scenery, not a quest: the board stays silent about it. */
 static boolean sound(ServerLevel l,String template,CompoundTag built,UUID quest){
  if(escorts(template)&&QuestSites.people(built).isEmpty())return false;
  if(relicLike(template)&&!holdsRelic(l,built,quest))return false;
  if((template.equals(BEAST)||template.equals(LAIR)||template.equals(Chains.WARCAMP)||template.equals(Chains.CRYPT))&&built.getList("mobs",Tag.TAG_INT_ARRAY).isEmpty())return false;
  return true;
 }
 static QuestSites.Order order(ServerLevel l,SettlementData.Entry e,String template,JsonObject spec,UUID quest,Seam seam,BlockPos anchor){
  int people=spec.get("count").getAsInt(),fighters=spec.has("fighters")?spec.get("fighters").getAsInt():0;
  return switch(template){
   case LODE->new QuestSites.Order(QuestSites.ADIT,0,0,spec.get("seam").getAsInt(),anchor.getY()-7<0?seam.deep():seam.stone(),ItemStack.EMPTY,ItemStack.EMPTY);
   case CAPTIVE->new QuestSites.Order(QuestSites.STOCKADE,people,fighters,0,Blocks.AIR,loot(l,e),ItemStack.EMPTY);
   case LOST->new QuestSites.Order(QuestSites.WRECK,people,fighters,0,Blocks.AIR,loot(l,e),ItemStack.EMPTY);
   case RELIC->new QuestSites.Order(QuestSites.BARROW,0,fighters,0,Blocks.AIR,ItemStack.EMPTY,relic(quest));
   case LAIR->new QuestSites.Order(QuestSites.HIDEOUT,0,fighters,0,Blocks.AIR,loot(l,e),ItemStack.EMPTY);
   case COLLAPSE->new QuestSites.Order(QuestSites.COLLAPSE,people,0,0,Blocks.AIR,ItemStack.EMPTY,ItemStack.EMPTY);
   case BEAST->new QuestSites.Order(QuestSites.DEN,0,fighters,0,Blocks.AIR,ItemStack.EMPTY,ItemStack.EMPTY);
   case BEACON->new QuestSites.Order(QuestSites.TOWER,0,0,0,Blocks.AIR,ItemStack.EMPTY,ItemStack.EMPTY);
   // AD-099: the stages a chain leads to.
   case Chains.RICHLODE->new QuestSites.Order(QuestSites.ADIT,0,0,spec.get("seam").getAsInt(),anchor.getY()-7<0?Blocks.DEEPSLATE_GOLD_ORE:Blocks.GOLD_ORE,ItemStack.EMPTY,ItemStack.EMPTY);
   case Chains.SANCTUARY->new QuestSites.Order(QuestSites.SANCTUM,0,fighters,0,Blocks.AIR,ItemStack.EMPTY,relic(quest,template));
   case Chains.CRYPT->new QuestSites.Order(QuestSites.CRYPT,0,fighters,0,Blocks.AIR,ItemStack.EMPTY,relic(quest,template));
   case Chains.WARCAMP->new QuestSites.Order(QuestSites.WARCAMP,0,fighters,0,Blocks.AIR,loot(l,e),ItemStack.EMPTY);
   default->null;
  };
 }
 // ---------------------------------------------------------------- handing over what was brought back
 /** Where the village takes this delivery: the scholars take a volume, everything else goes into the stock. */
 private static Settlement.Building receiver(SettlementData.Entry e,String template){
  if(relicLike(template)){var lab=e.settlement().buildings().stream().filter(b->b.type().equals("laboratory")).findFirst().orElse(null);if(lab!=null)return lab;}
  return Caravans.stock(e);
 }
 /** Mined ore and the ancient volume are handed over at the village, item by item through the journal. */
 public static String handOver(ServerPlayer p,UUID village,UUID id){
  var l=p.serverLevel();var q=Quests.quest(l,village,id);var e=SettlementData.get(p.server).entry(village);
  if(q!=null&&Wilds.handsOver(q.getString("template")))return Wilds.handOver(p,village,id);
  if(q==null||e==null||!q.getString("state").equals(Quests.TAKEN)||!handsOver(q.getString("template"))
   ||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))return "unknown";
  var building=receiver(e,q.getString("template"));var chest=building==null?null:LogisticsRoutes.position(e,building);
  if(chest==null)return "unknown";
  if(p.distanceToSqr(chest.getX()+.5,chest.getY()+.5,chest.getZ()+.5)>64)return "far";
  if(relicLike(q.getString("template")))return relic(p,e,q,chest);
  var item=BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(q.getString("item")));
  int carried=0;for(var s:p.getInventory().items)if(s.is(item)&&Trade.plain(s))carried+=s.getCount();
  if(carried<=0)return "empty";int moved=0;
  // QUEST-002: the village asked for the ore of that far seam. It takes as much as the seam really gave and no more:
  // ore bought at a stall or dug at home is the player's own, and the errand stays unfinished until the seam is worked.
  int allowed=q.getInt("target");
  if(q.getString("template").equals(LODE)||q.getString("template").equals(Chains.RICHLODE)){
   var site=QuestSites.site(l,Quests.root(q));
   // What the seam gave: the mark kept while its ground was in the world, and what the wall shows right now if it is in the world again.
   allowed=site==null?0:site.getInt("mined");
   if(site!=null&&l.hasChunkAt(QuestSites.origin(site)))allowed=Math.max(allowed,site.getList("seam",Tag.TAG_LONG).size()-QuestSites.seamLeft(l,site));
   if(q.getInt("progress")>=allowed)return "not_from_seam";}
  for(int i=0;i<carried&&q.getInt("progress")+moved<Math.min(q.getInt("target"),allowed);i++){
   var deposit=Settlement.childId(id,"lode/"+(q.getInt("progress")+moved));
   if(!WorldJournal.deposit(l,deposit,chest,new ItemStack(item,1)))break;
   for(var s:p.getInventory().items)if(s.is(item)&&Trade.plain(s)){s.shrink(1);break;}
   moved++;}
  if(moved==0)return "full";
  q.putInt("progress",q.getInt("progress")+moved);Quests.store(l,village,q);Quests.complete(p,village,id);return "ok";
 }
 /** The volume of the barrow: only the one this quest put there counts, and the scholars start reading it at once. */
 private static String relic(ServerPlayer p,SettlementData.Entry e,CompoundTag q,BlockPos chest){
  var l=p.serverLevel();var id=q.getUUID("id");ItemStack found=ItemStack.EMPTY;
  for(var s:p.getInventory().items)if(isRelic(s,Quests.root(q))){found=s;break;}
  if(found.isEmpty())return q.getString("template").equals(RELIC)?"no_relic":"no_relic_"+q.getString("template");
  var deposit=Settlement.childId(id,"relic");
  if(!WorldJournal.deposit(l,deposit,chest,found.copyWithCount(1)))return "full";
  found.shrink(1);q.putInt("progress",1);Quests.store(l,village(q),q);
  long ticks=Quests.spec(q.getString("template")).get("research_ticks").getAsLong();
  var civilization=e.settlement().civilization();var studied=civilization.active();
  if(!studied.isEmpty()){
   civilization.work(ticks,true,true);SettlementData.get(p.server).setDirty();
   p.displayClientMessage(Component.translatable("quest.villageastra.relic_studied",Component.translatable("research.villageastra."+studied),ticks/20),false);}
  Quests.complete(p,village(q),id);return "ok";
 }
 private static UUID village(CompoundTag q){return q.getUUID("village");}
 // ---------------------------------------------------------------- people met out there
 /** Why a companion of an adventure cannot walk with the player yet: the bars, the chief, or an untreated wound. */
 public static String hold(ResidentEntity npc){
  if(!(npc.level() instanceof ServerLevel l))return "";
  var data=npc.getPersistentData();if(!data.hasUUID(Camps.QUEST_TAG))return "";
  var site=QuestSites.site(l,data.getUUID(Camps.QUEST_TAG));if(site==null)return "";
  if(data.getBoolean(QuestSites.INJURED))return "injured";
  if(data.getBoolean(QuestSites.TRAPPED)){
   if(!WildSites.dugOut(l,site))return "trapped";
   data.putBoolean(QuestSites.TRAPPED,false);npc.workStatus("prospector_free");return "";}
  if(!data.getBoolean(QuestSites.CAGED))return "";
  if(!QuestSites.cageOpened(l,site))return "caged";
  if(!QuestSites.chiefDown(l,site))return "chief";
  if(data.getBoolean(QuestSites.CAGED)){data.putBoolean(QuestSites.CAGED,false);npc.workStatus("captive_free");}
  return "";
 }
 /** A bandage or real food given by hand to a survivor; three units of care put one of them back on their feet. */
 public static String treat(ServerPlayer p,ResidentEntity npc,InteractionHand hand){
  var data=npc.getPersistentData();
  if(!data.getBoolean(QuestSites.INJURED)||!data.hasUUID(Camps.QUEST_TAG))return "";
  if(!Quests.owned(p,data.getUUID(Camps.QUEST_TAG)))return "treat_other";
  var spec=Quests.spec(LOST);var stack=p.getItemInHand(hand);
  int care=stack.is(VillageAstra.BANDAGE.get())?spec.get("bandage_care").getAsInt():stack.isEdible()?spec.get("food_care").getAsInt():0;
  if(care<=0)return "treat_needed";
  int need=spec.get("care").getAsInt(),given=Math.min(need,data.getInt(QuestSites.CARE)+care);
  stack.shrink(1);data.putInt(QuestSites.CARE,given);
  if(given<need){npc.workStatus("survivor_treated_part");return "treat_more";}
  data.putBoolean(QuestSites.INJURED,false);npc.setNoAi(false);npc.setHealth(npc.getMaxHealth());npc.workStatus("survivor_treated");
  return "treated";
 }
 /** AD-106: a companion hurt on the way is bandaged by the player it follows, or fed while badly wounded; a well one is not treated. */
 public static String treatWound(ServerPlayer p,ResidentEntity npc,InteractionHand hand){
  if(!p.getUUID().equals(npc.escortPlayer())||npc.getHealth()>=npc.getMaxHealth())return "";
  var spec=Quests.spec(LOST);var stack=p.getItemInHand(hand);boolean wounded=npc.getHealth()<=npc.getMaxHealth()*EscortGoal.WOUNDED;
  int care=stack.is(VillageAstra.BANDAGE.get())?spec.get("bandage_care").getAsInt():wounded&&stack.isEdible()?spec.get("food_care").getAsInt():0;
  if(care<=0)return "";
  stack.shrink(1);npc.heal(npc.getMaxHealth()*care/spec.get("care").getAsInt());
  return npc.getHealth()<=npc.getMaxHealth()*EscortGoal.WOUNDED?"escort_treat_more":"escort_treated";
 }
 // ---------------------------------------------------------------- what the board shows and when a site is done with
 private record Progress(String status,int left){}
 private static Progress progress(ServerLevel l,CompoundTag site,CompoundTag q){
  var origin=QuestSites.origin(site);
  if(!l.hasChunkAt(origin))return new Progress(q.getString("status"),q.getInt("status_left"));
  return switch(q.getString("template")){
   case LODE,Chains.RICHLODE->new Progress("seam",QuestSites.seamLeft(l,site));
   case Chains.SANCTUARY->new Progress(holdsRelic(l,site,Quests.root(q))?"altar":"tablet_taken",0);
   case Chains.CRYPT->new Progress(!QuestSites.chiefDown(l,site)?"guard":holdsRelic(l,site,Quests.root(q))?"vault_open":"chronicle_taken",0);
   case Chains.WARCAMP->new Progress(QuestSites.chiefDown(l,site)?"band":"warlord",band(l,site,q).size());
   case CAPTIVE->new Progress(!QuestSites.cageOpened(l,site)?"caged":!QuestSites.chiefDown(l,site)?"chief":"freed",0);
   case LOST->new Progress("wounded",untreated(l,site));
   case RELIC->new Progress(QuestSites.sealBroken(l,site)?"opened":"sealed",0);
   case LAIR->new Progress("band",band(l,site,q).size());
   case COLLAPSE->new Progress(WildSites.dugOut(l,site)?"dug":"fall",0);
   case BEAST->new Progress(QuestSites.chiefDown(l,site)?"slain":"prowls",0);
   case BEACON->{var have=WildSites.towerSupplies(l,site);var spec=Quests.spec(BEACON);
    int missing=Math.max(0,spec.get("stone").getAsInt()-have[0])+Math.max(0,spec.get("coal").getAsInt()-have[1]);
    yield site.getBoolean("finished")?new Progress(WildSites.lit(l,site)?"lit":"unlit",0):new Progress("supplies",missing);}
   default->new Progress("",0);
  };
 }
 private static int untreated(ServerLevel l,CompoundTag site){
  int left=0;
  for(var id:QuestSites.people(site))if(l.getEntity(id) instanceof ResidentEntity npc&&npc.isAlive()&&npc.getPersistentData().getBoolean(QuestSites.INJURED))left++;
  return left;
 }
 /** The fighters of a site still on their feet: counted only near a player, where the world really holds them. */
 static List<net.minecraft.world.entity.Mob> band(ServerLevel l,CompoundTag site,CompoundTag q){
  var id=Quests.root(q);
  return l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(QuestSites.origin(site)).inflate(32),
   m->m.isAlive()&&m.getPersistentData().hasUUID(QuestSites.QUEST)&&m.getPersistentData().getUUID(QuestSites.QUEST).equals(id));
 }
 private static boolean ownerNear(ServerLevel l,CompoundTag site,ServerPlayer owner){
  return owner!=null&&owner.level()==l&&owner.blockPosition().distSqr(QuestSites.origin(site))<=48*48;
 }
 /** AD-090: the lit brazier of a finished signal tower lets the village see a wave from farther out: raids gather this much farther away. */
 public static int watch(ServerLevel l,SettlementData.Entry e){
  for(var raw:Quests.board(l,e.settlement().id()).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
   if(!q.getString("template").equals(BEACON)||!q.getString("state").equals(Quests.DONE))continue;
   var site=QuestSites.site(l,Quests.root(q));if(site==null)continue;
   boolean lit=l.hasChunkAt(BlockPos.of(site.getLong("brazier")))?WildSites.lit(l,site):site.getBoolean("lit");
   if(lit)return Quests.spec(BEACON).get("reach").getAsInt();}
  return 0;
 }
 /** While the beast lives, it takes one animal of the village's herd now and then: the quest has a real cost of waiting. */
 private static void prey(ServerLevel l,SettlementData.Entry e,long now){
  long every=Quests.spec(BEAST).get("prey_every").getAsLong();if(Math.floorMod(now,every)>=100)return;
  var village=e.settlement().id();
  var herd=l.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,new net.minecraft.world.phys.AABB(e.center()).inflate(LivestockGoal.SEARCH),a->a.isAlive()&&LivestockGoal.owned(a,village));
  if(herd.isEmpty())return;var taken=herd.get((int)Math.floorMod(now/every,herd.size()));
  var name=taken.getDisplayName();taken.kill();
  for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)player.sendSystemMessage(Component.translatable("quest.villageastra.beast_prey",name));
 }
 /** Keeps the board honest about the places out there and lets a scouted lead be used again once its site is finished with. */
 public static void tick(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();var board=Quests.board(l,village);boolean changed=false;
  for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
   if(!SITES.contains(q.getString("template")))continue;
   var owner=q.hasUUID("owner")?l.getServer().getPlayerList().getPlayer(q.getUUID("owner")):null;
   changed|=step(l,e,board,q,owner,now);}
  if(changed)Quests.saveBoard(l,village,board);
 }
 /** One step of one quest with its owner given, not looked up: the tick passes the online player, a test passes its own. */
 public static boolean advance(ServerLevel l,SettlementData.Entry e,UUID id,ServerPlayer owner,long now){
  var village=e.settlement().id();var board=Quests.board(l,village);boolean changed=false;
  for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;if(q.getUUID("id").equals(id))changed|=step(l,e,board,q,owner,now);}
  if(changed)Quests.saveBoard(l,village,board);
  return changed;
 }
 private static boolean step(ServerLevel l,SettlementData.Entry e,CompoundTag board,CompoundTag q,ServerPlayer owner,long now){
  var village=e.settlement().id();var template=q.getString("template");boolean changed=false;
  var state=q.getString("state");var root=Quests.root(q);
  var site=QuestSites.site(l,root);
  // AD-105: an abandoned card that came back to the board as a new one leaves the place as it is: the new card, on the same board, uses it.
  if(state.equals(Quests.ABANDONED)){
   if(site==null||site.getString("state").equals("closed"))return false;
   boolean living=board.getList("quests",Tag.TAG_COMPOUND).stream().map(x->(CompoundTag)x).anyMatch(x->!x.getUUID("id").equals(q.getUUID("id"))
    &&(x.getString("state").equals(Quests.OPEN)||x.getString("state").equals(Quests.TAKEN))&&Quests.root(x).equals(root));
   if(living)return false;}
  if(site==null){
   // A stage still waiting for its land has no place yet; any other open errand without its place is a record the mod lost.
   if((state.equals(Quests.OPEN)||state.equals(Quests.TAKEN))&&!q.getBoolean("pending")){Quests.close(l,village,q,Quests.BROKEN,"site_missing",owner);return true;}
   return false;}
  if(state.equals(Quests.OPEN)||state.equals(Quests.TAKEN)){
   if(template.equals(BEAST)&&!(l.hasChunkAt(QuestSites.origin(site))&&ownerNear(l,site,owner)&&QuestSites.chiefDown(l,site)))prey(l,e,now);
   if(state.equals(Quests.TAKEN)&&l.hasChunkAt(QuestSites.origin(site))&&q.getInt("progress")<q.getInt("target")){
    boolean done=false;
    // The band and the beast count as beaten only where their player stands to see it: the world then really holds them.
    if((template.equals(LAIR)||template.equals(Chains.WARCAMP))&&ownerNear(l,site,owner)&&band(l,site,q).isEmpty()){done=true;
     Raids.calm(l,village,now+Quests.spec(template).get("calm").getAsLong());
     for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)player.sendSystemMessage(Component.translatable("quest.villageastra."+template+"_calm"));}
    if(template.equals(BEAST)&&ownerNear(l,site,owner)&&QuestSites.chiefDown(l,site))done=true;
    if(template.equals(BEACON)){var spec=Quests.spec(BEACON);
     if(!site.getBoolean("finished")&&WildSites.finishTower(l,site,spec.get("stone").getAsInt(),spec.get("coal").getAsInt()))QuestSites.save(l,Quests.root(q),site);
     if(WildSites.lit(l,site)){done=true;site.putBoolean("lit",true);QuestSites.save(l,Quests.root(q),site);}}
    if(done){q.putInt("progress",q.getInt("target"));changed=true;}
   }
   // QUEST-002: what the seam really gave is remembered while its ground is in the world, so the village takes only ore that came out of it.
  if((template.equals(LODE)||template.equals(Chains.RICHLODE))&&l.hasChunkAt(QuestSites.origin(site))){
   int mined=site.getList("seam",Tag.TAG_LONG).size()-QuestSites.seamLeft(l,site);
   if(mined>site.getInt("mined")){site.putInt("mined",mined);QuestSites.save(l,root,site);}}
  var progress=progress(l,site,q);
   if(!progress.status().equals(q.getString("status"))||progress.left()!=q.getInt("status_left")){
    q.putString("status",progress.status());q.putInt("status_left",progress.left());changed=true;}
   return changed;
  }
  // A burning tower keeps its fire on record, so the raids can read it while its chunk sleeps.
  if(template.equals(BEACON)&&state.equals(Quests.DONE)&&l.hasChunkAt(BlockPos.of(site.getLong("brazier")))){
   boolean lit=WildSites.lit(l,site);
   if(lit!=site.getBoolean("lit")){site.putBoolean("lit",lit);QuestSites.save(l,Quests.root(q),site);}
   var active=Raids.record(l,village).getCompound("active");
   if(lit&&active.contains("started")&&active.getLong("started")!=site.getLong("seen")){site.putLong("seen",active.getLong("started"));QuestSites.save(l,Quests.root(q),site);
    for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)player.sendSystemMessage(Component.translatable("quest.villageastra.tower_seen"));}
  }
  if(site.getString("state").equals("closed"))return false;
  // A fall that nobody dug out in time keeps those who were still behind it.
  if(template.equals(COLLAPSE))for(var id:QuestSites.people(site))if(l.getEntity(id) instanceof ResidentEntity npc&&npc.getPersistentData().getBoolean(QuestSites.TRAPPED))npc.discard();
  site.putString("state","closed");site.putLong("closed",now);QuestSites.save(l,Quests.root(q),site);
  // The lead goes back into the expedition book only while there is still room for another site near it.
  var anchor=BlockPos.of(site.getLong("anchor"));
  if(!Chains.STAGES.contains(template)&&QuestSites.place(l,e,anchor,site.getString("kind"))!=null)Expeditions.useLead(l,village,anchor,false);
  return false;
 }
}
