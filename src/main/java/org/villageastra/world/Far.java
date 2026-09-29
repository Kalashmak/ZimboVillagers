package org.villageastra.world;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.villageastra.domain.Charts;
import org.villageastra.server.SettlementData;
/** QUEST-002 (owner's spec: "distances are thousands of blocks… the expedition office sends them where the needed resource or object is
 *  really known to be, not to a random unsuitable coordinate"): how a village comes to know of a place a thousand blocks and more away.
 *  <p>The office does not walk there — its people go a hundred and some blocks (Expeditions). What travels that far is word: a traveller's
 *  tale, a trader's map, a name on a wall. So a far lead is found by asking the world itself where such a place really stands, from a point
 *  already far out along one of eight roads, and it is kept only when the world answers with a real structure or a real country of the kind
 *  the village asked about, at a distance that is really far and not beyond reach, on ground above the sea.
 *  <p>Nothing is built and no chunk is generated to find it: the search reads the world's own plan of where its structures fall, and the
 *  height comes from the terrain's shape, not from loading the ground. The place itself is built only when somebody walks up to it. */
public final class Far {
 private Far(){}
 public static final String KIND="far";
 /** The eight roads out of a village, kept apart from the expedition office's own eight sectors (0..7) and the surveys' (8..15). */
 public static final int FIRST_SECTOR=16,SECTORS=8;
 private static int least(){return (int)Quests.setting(KIND,"least");}
 private static int most(){return (int)Quests.setting(KIND,"most");}
 private static int step(){return (int)Quests.setting(KIND,"step");}
 private static int tries(){return (int)Quests.setting(KIND,"tries");}
 private static int rings(){return (int)Quests.setting(KIND,"rings");}
 /** AD-112: how far out along a road this village's word reaches, by its best expedition house. */
 public static int reach(int level){return org.villageastra.domain.CoreEffects.value("expedition","far_reach",level);}
 public static int reach(ServerLevel l,SettlementData.Entry e){return reach(BuildingLevels.best(l,e,"expedition"));}
 /** What a far target may be: a real structure of the world, or a real country of it. */
 public record Target(String id,TagKey<Structure> structures,TagKey<Biome> country){
  public boolean built(){return structures!=null;}
 }
 /** The far places a village can come to know of, each by what the world itself calls it. */
 public static final List<Target> TARGETS=List.of(
  new Target("far_ruins",StructureTags.RUINED_PORTAL,null),
  new Target("far_village",StructureTags.VILLAGE,null),
  new Target("far_wreck",StructureTags.SHIPWRECK,null),
  new Target("far_mansion",StructureTags.ON_WOODLAND_EXPLORER_MAPS,null),
  new Target("far_badlands",null,BiomeTags.IS_BADLANDS),
  new Target("far_wood",null,BiomeTags.IS_FOREST),
  new Target("far_taiga",null,BiomeTags.IS_TAIGA),
  new Target("far_sands",null,BiomeTags.HAS_DESERT_PYRAMID));
 public static Target target(String id){for(var t:TARGETS)if(t.id().equals(id))return t;return null;}
 /** Where a road of this village leaves it: the point a far search starts from, already out at the village's reach. */
 public static BlockPos road(ServerLevel l,SettlementData.Entry e,int sector,int out){
  double angle=Math.PI*2*Math.floorMod(sector-FIRST_SECTOR,SECTORS)/SECTORS;
  return e.center().offset((int)Math.round(Math.cos(angle)*out),0,(int)Math.round(Math.sin(angle)*out));
 }
 /** How far a place lies from the village, as the board says it. */
 public static int away(SettlementData.Entry e,BlockPos at){return Adventures.away(e.center(),at);}
 /** The ground of a far place as the world's own shape gives it, without generating that ground. Negative when it lies under the sea. */
 public static int height(ServerLevel l,BlockPos at){
  var chunks=l.getChunkSource();
  return chunks.getGenerator().getBaseHeight(at.getX(),at.getZ(),Heightmap.Types.WORLD_SURFACE_WG,l,chunks.randomState());
 }
 /** Looks along one road for a real place of this kind, far enough out and not too far, standing above the sea; null when the world has none. */
 public static BlockPos probe(ServerLevel l,SettlementData.Entry e,Target target,int sector){
  int out=reach(l,e);
  for(int attempt=0;attempt<tries();attempt++,out+=step()){
   var from=road(l,e,sector,out);
   BlockPos found=null;
   if(target.built()){
    // The world's own plan of where its structures fall: the search starts from the far point, not from the village, so what it finds is far.
    var pair=l.getChunkSource().getGenerator().findNearestMapStructure(l,l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getOrCreateTag(target.structures()),from,rings(),false);
    if(pair!=null)found=pair.getFirst();
   }else{
    var country=l.findClosestBiome3d(h->h.is(target.country()),from,Math.max(64,(int)Quests.setting(KIND,"biome_radius")),32,64);
    if(country!=null)found=country.getFirst();
   }
   if(found==null)continue;
   int away=away(e,found);
   if(away<least()||away>most())continue;
   int y=height(l,found);
   if(y<=l.getSeaLevel())continue;
   var at=new BlockPos(found.getX(),y,found.getZ());
   // A chart is the whole worth of the word: vanilla snaps even the widest chart to its own grid and drops a cross more than 62 pixels
   // from that centre, so a place that falls in the outer ring of a grid square cannot be given one. Such a place is not written down.
   if(!charted(e,at))continue;
   return at;
  }
  return null;
 }
 /** The road this village has not sent word along yet, or -1 when every one of them is already known. */
 public static int nextSector(ServerLevel l,UUID village){
  var known=new HashSet<Integer>();for(var lead:Expeditions.leads(l,village))known.add(lead.getInt("sector"));
  for(int s=FIRST_SECTOR;s<FIRST_SECTOR+SECTORS;s++)if(!known.contains(s))return s;
  return -1;
 }
 /** What the village knows of a far place along one road, or null: its place, what kind it is and how far it lies. */
 public static CompoundTag learn(ServerLevel l,SettlementData.Entry e,Target target,int sector,long now){
  var at=probe(l,e,target,sector);if(at==null)return null;
  var village=e.settlement().id();Expeditions.addLead(l,village,at,KIND,sector,now);
  // What kind of place it is and how far it lies are written onto the record itself, not onto a copy of it.
  var record=Expeditions.record(l,village);CompoundTag lead=null;
  for(var raw:record.getList("leads",net.minecraft.nbt.Tag.TAG_COMPOUND)){var x=(CompoundTag)raw;if(x.getInt("sector")==sector)lead=x;}
  if(lead==null)return null;
  lead.putString("far_kind",target.id());lead.putInt("away",away(e,at));Expeditions.save(l,village,record);
  LogUtils.getLogger().info("ASTRA_FAR_TARGET village {} learns of {} at {} ({} blocks to the {})",village,target.id(),at.toShortString(),away(e,at),Adventures.bearing(e.center(),at));
  return lead;
 }
 /** A far lead the village knows and has not used, of this kind or of any ("" ). */
 public static CompoundTag known(ServerLevel l,UUID village,String kind){
  for(var lead:Expeditions.leads(l,village)){
   if(!lead.getString("kind").equals(KIND)||lead.getBoolean("used"))continue;
   if(kind.isEmpty()||lead.getString("far_kind").equals(kind))return lead;}
  return null;
 }
 // ---------------------------------------------------------------- the first far errand: word of what stands out there
 /** Bringing back word of a far place: go to it, see it with your own eyes, and tell the village at home. */
 public static final String TIDINGS="tidings";
 public static final int EVERY=(int)Quests.setting(KIND,"every");
 /** How close the traveller must come to the place for it to count as seen. */
 private static int seen(){return (int)Quests.setting(TIDINGS,"seen");}
 /** Posts word-gathering for a far place the village knows of but has never had word back from. One such card at a time. */
 public static CompoundTag post(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();var board=Quests.board(l,village);int open=0;
  for(var raw:board.getList("quests",net.minecraft.nbt.Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var state=q.getString("state");
   if(!state.equals(Quests.OPEN)&&!state.equals(Quests.TAKEN))continue;open++;
   if(q.getString("template").equals(TIDINGS))return null;}
  if(open>=Quests.MAX_OPEN)return null;
  // A road nobody has sent word along yet is learned first; the village can only ask about what it has heard of.
  int sector=nextSector(l,village);
  if(sector>=0)learn(l,e,TARGETS.get((int)Math.floorMod(now/EVERY,TARGETS.size())),sector,now);
  var lead=known(l,village,"");if(lead==null)return null;
  var at=BlockPos.of(lead.getLong("pos"));var kindOf=lead.getString("far_kind");
  var q=Quests.blank(village,TIDINGS,now,Quests.deadline(TIDINGS));
  q.putInt("target",1);q.putLong("coins",Quests.coins(TIDINGS));q.putLong("reputation",Quests.reputation(TIDINGS));
  q.putLong("site",at.asLong());q.putString("kind",kindOf);q.putString("status","road");
  Quests.store(l,village,q);Expeditions.useLead(l,village,at,true);
  for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
   player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("quest.villageastra.far_posted",
    net.minecraft.network.chat.Component.translatable("quest.villageastra.place."+kindOf),Adventures.away(e.center(),at),
    net.minecraft.network.chat.Component.translatable("quest.villageastra.side."+Adventures.bearing(e.center(),at))));
  return q;
 }
 /** The pass of the far errands: the place seen with the traveller's own eyes, and the word brought back home. */
 public static void tick(ServerLevel l,SettlementData.Entry e,long now){
  if(Math.floorMod(now,20L)!=0)return;
  var village=e.settlement().id();
  for(var raw:Quests.board(l,village).getList("quests",net.minecraft.nbt.Tag.TAG_COMPOUND)){var old=(CompoundTag)raw;
   if(!old.getString("template").equals(TIDINGS)||!old.getString("state").equals(Quests.TAKEN)||!old.hasUUID("owner"))continue;
   var owner=EscortGoal.owner(l.getServer(),old.getUUID("owner"));if(owner==null||owner.level()!=l)continue;
   var q=Quests.quest(l,village,old.getUUID("id"));if(q==null)continue;
   var at=BlockPos.of(q.getLong("site"));
   if(q.getInt("progress")<=0){
    if(owner.blockPosition().distSqr(at)>(long)seen()*seen())continue;
    q.putInt("progress",1);q.putString("status","seen");Quests.store(l,village,q);
    owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("quest.villageastra.far_seen",
     net.minecraft.network.chat.Component.translatable("quest.villageastra.place."+q.getString("kind"))),false);
    continue;}
   // Word is worth nothing out there: it counts when the traveller is back among the people who asked for it.
   if(owner.blockPosition().distSqr(e.center())>(long)Camps.ARRIVE*Camps.ARRIVE){
    if(!q.getString("status").equals("far_home")){q.putString("status","far_home");Quests.store(l,village,q);}
    continue;}
   Quests.complete(owner,village,q.getUUID("id"));
  }
 }
 // ---------------------------------------------------------------- the second far errand: the load of a caravan that never came home
 /** Bringing home goods from a far place: what the village asks for is really lying out there, in the crates of a stash. */
 public static final String FREIGHT="freight";
 public static boolean freight(String template){return FREIGHT.equals(template);}
 /** What a caravan would have been carrying from a place of this kind. */
 private static final Map<String,net.minecraft.world.item.Item> LOAD=Map.of(
  "far_ruins",net.minecraft.world.item.Items.OBSIDIAN,
  "far_village",net.minecraft.world.item.Items.EMERALD,
  "far_wreck",net.minecraft.world.item.Items.COPPER_INGOT,
  "far_mansion",net.minecraft.world.item.Items.BOOK,
  "far_badlands",net.minecraft.world.item.Items.GOLD_INGOT,
  "far_wood",net.minecraft.world.item.Items.HONEYCOMB,
  "far_taiga",net.minecraft.world.item.Items.SWEET_BERRIES,
  "far_sands",net.minecraft.world.item.Items.GLASS);
 public static net.minecraft.world.item.Item load(String kind){return LOAD.getOrDefault(kind,net.minecraft.world.item.Items.EMERALD);}
 /** What the site of a waiting freight card is made of: the crates hold the load the card asks for. */
 public static QuestSites.Order order(CompoundTag q){
  var goods=new net.minecraft.world.item.ItemStack(load(q.getString("kind")),q.getInt("target"));
  return new QuestSites.Order(FarSites.STASH,(int)Quests.setting(FREIGHT,"guards"),0,0,null,goods,net.minecraft.world.item.ItemStack.EMPTY);
 }
 /** A stash built without its crates is scenery: the place is tried again a little farther on. */
 public static boolean sound(CompoundTag built){return built.getInt("load")>0;}
 /** Posts the freight errand for a far place the village knows of. One such card at a time, and only once word has come back from
  *  somewhere: a village that has heard nothing of the world sends nobody after crates. */
 public static CompoundTag postFreight(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();int open=0;
  for(var raw:Quests.board(l,village).getList("quests",net.minecraft.nbt.Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var state=q.getString("state");
   if(!state.equals(Quests.OPEN)&&!state.equals(Quests.TAKEN))continue;open++;
   if(q.getString("template").equals(FREIGHT))return null;}
  if(open>=Quests.MAX_OPEN)return null;
  var lead=known(l,village,"");if(lead==null)return null;
  var at=BlockPos.of(lead.getLong("pos"));var kindOf=lead.getString("far_kind");
  int count=(int)Quests.setting(FREIGHT,"count");
  var item=load(kindOf);
  var q=Quests.blank(village,FREIGHT,now,Quests.deadline(FREIGHT));
  q.putInt("target",count);q.putString("item",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
  q.putLong("coins",Quests.coins(FREIGHT)+Quests.value(item,count));q.putLong("reputation",Quests.reputation(FREIGHT));
  // The crates are built where the word said they are, once somebody is there to see it (Chains).
  q.putLong("site",at.asLong());q.putBoolean("pending",true);q.putString("kind",kindOf);q.putString("status","pending");
  Quests.store(l,village,q);Expeditions.useLead(l,village,at,true);
  for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
   player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("quest.villageastra.far_freight",
    count,new net.minecraft.world.item.ItemStack(item).getHoverName(),
    net.minecraft.network.chat.Component.translatable("quest.villageastra.side."+Adventures.bearing(e.center(),at)),Adventures.away(e.center(),at)));
  return q;
 }

 /** Whether a chart of this place really carries its cross (the widest chart, centred on the place itself). */
 public static boolean charted(SettlementData.Entry e,BlockPos at){
  var frame=Charts.frame(at.getX(),at.getZ(),e.center().getX(),e.center().getZ());
  return Charts.onMap(at.getX(),at.getZ(),frame[0],frame[1],frame[2],true);
 }
}
