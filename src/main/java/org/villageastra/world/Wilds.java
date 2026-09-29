package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.villageastra.domain.CoreEffects;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
/** AD-091: adventures that follow the world as it already is rather than a site the mod builds. The caravan escort walks with a real
 *  trade trip and meets an ambush on its road; the embassy carries a sealed letter to a real neighbouring village and brings its answer
 *  back; the ruin sends the player into a real structure of the world; the survey walks unknown land until the village knows it. */
public final class Wilds {
 public static final String ESCORT="escort",EMBASSY="embassy",RUIN="ruin",SURVEY="survey";
 /** The ones posted in the board's window; the escort is posted when a caravan really leaves. */
 public static final List<String> TEMPLATES=List.of(EMBASSY,RUIN,SURVEY,ESCORT);
 public static final String LETTER="AstraLetter",REPLY="AstraReply";
 /** Structures of the vanilla world a village may send somebody into, by the name the board gives them. */
 private static final Map<String,List<ResourceKey<Structure>>> RUINS=new LinkedHashMap<>();
 static{
  RUINS.put("mineshaft",List.of(BuiltinStructures.MINESHAFT,BuiltinStructures.MINESHAFT_MESA));
  RUINS.put("portal",List.of(BuiltinStructures.RUINED_PORTAL_STANDARD,BuiltinStructures.RUINED_PORTAL_DESERT,BuiltinStructures.RUINED_PORTAL_JUNGLE,
   BuiltinStructures.RUINED_PORTAL_SWAMP,BuiltinStructures.RUINED_PORTAL_MOUNTAIN));
  RUINS.put("outpost",List.of(BuiltinStructures.PILLAGER_OUTPOST));
  RUINS.put("pyramid",List.of(BuiltinStructures.DESERT_PYRAMID));
  RUINS.put("temple",List.of(BuiltinStructures.JUNGLE_TEMPLE));
  RUINS.put("igloo",List.of(BuiltinStructures.IGLOO));
  RUINS.put("hut",List.of(BuiltinStructures.SWAMP_HUT));
  RUINS.put("shipwreck",List.of(BuiltinStructures.SHIPWRECK_BEACHED));
  RUINS.put("trail",List.of(BuiltinStructures.TRAIL_RUINS));
 }
 private Wilds(){}
 static boolean handsOver(String template){return template.equals(EMBASSY);}
 private static void announce(ServerLevel l,SettlementData.Entry e,String template,Component place){
  for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
   player.sendSystemMessage(Component.translatable("quest.villageastra.posted",Component.translatable("quest.villageastra.template."+template),place));
 }
 private static CompoundTag fresh(SettlementData.Entry e,String template,long now){
  var q=Quests.blank(e.settlement().id(),template,now,Quests.deadline(template));
  q.putInt("target",Quests.spec(template).get("count").getAsInt());
  q.putLong("coins",Quests.coins(template));q.putLong("reputation",Quests.reputation(template));return q;
 }
 // ---------------------------------------------------------------- posting
 public static CompoundTag post(ServerLevel l,SettlementData.Entry e,String template,long now){
  if(!TEMPLATES.contains(template)||!Adventures.room(Quests.board(l,e.settlement().id()),template))return null;
  return switch(template){case EMBASSY->embassy(l,e,now);case RUIN->ruin(l,e,now);case SURVEY->survey(l,e,now);default->null;};
 }
 /** A letter to the nearest village of the same world within reach; the chart's cross stands on its hall. */
 private static CompoundTag embassy(ServerLevel l,SettlementData.Entry e,long now){
  int reach=Quests.spec(EMBASSY).get("reach").getAsInt();
  // AD-158 III: the letters go only to neighbours the village's own second cartographer has found. While the ladder's scouting knob
  // is off (a world without the cartography ladder), any neighbour within reach is a neighbour it knows of.
  boolean scouted=CoreEffects.active(CartographyLadder.CORE,"scouts");
  var target=SettlementData.get(l.getServer()).entries().stream()
   .filter(x->!x.settlement().id().equals(e.settlement().id())&&x.dimension().equals(e.dimension())&&x.center().distSqr(e.center())<=(long)reach*reach)
   .filter(x->!scouted||CartographyLadder.knows(l,e.settlement().id(),x.settlement().id()))
   .min(Comparator.comparingDouble(x->x.center().distSqr(e.center()))).orElse(null);
  if(target==null)return null;
  var q=fresh(e,EMBASSY,now);q.putUUID("neighbour",target.settlement().id());q.putLong("site",target.center().asLong());q.putString("kind","embassy");
  Quests.store(l,e.settlement().id(),q);announce(l,e,EMBASSY,Adventures.place(q));return q;
 }
 /** The nearest real structure of one kind; kinds take turns, and a place once explored is not asked for again. */
 private static CompoundTag ruin(ServerLevel l,SettlementData.Entry e,long now){
  int radius=Quests.spec(RUIN).get("radius_chunks").getAsInt();
  var registry=l.registryAccess().registryOrThrow(Registries.STRUCTURE);var kinds=new ArrayList<>(RUINS.keySet());
  var board=Quests.board(l,e.settlement().id());
  int start=(int)Math.floorMod(now/Math.max(1,Adventures.EVERY),kinds.size());
  for(int i=0;i<kinds.size();i++){var kind=kinds.get((start+i)%kinds.size());
   var holders=new ArrayList<net.minecraft.core.Holder<Structure>>();
   for(var key:RUINS.get(kind))registry.getHolder(key).ifPresent(holders::add);
   if(holders.isEmpty())continue;
   var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holders),e.center(),radius,false);
   if(found==null)continue;var at=found.getFirst();
   if(board.getList("quests",Tag.TAG_COMPOUND).stream().anyMatch(x->((CompoundTag)x).getString("template").equals(RUIN)&&((CompoundTag)x).getLong("site")==at.asLong()))continue;
   var q=fresh(e,RUIN,now);q.putLong("site",at.asLong());q.putString("kind","ruin_"+kind);
   q.putString("structure",found.getSecond().unwrapKey().map(k->k.location().toString()).orElse(""));
   Quests.store(l,e.settlement().id(),q);announce(l,e,RUIN,Adventures.place(q));return q;
  }
  return null;
 }
 /** Unknown land in one of eight directions beyond the scout's own ring: nine chunks, of which the player has to walk through six. */
 private static CompoundTag survey(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();int distance=Quests.spec(SURVEY).get("distance").getAsInt();
  var known=new HashSet<Integer>();for(var lead:Expeditions.leads(l,village))known.add(lead.getInt("sector"));
  var board=Quests.board(l,village);
  for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var x=(CompoundTag)raw;if(x.getString("template").equals(SURVEY)&&x.getString("state").equals(Quests.DONE))known.add(x.getInt("sector"));}
  int start=(int)Math.floorMod(now/Math.max(1,Adventures.EVERY),8);
  for(int i=0;i<8;i++){int d=(start+i)%8,sector=8+d;if(known.contains(sector))continue;
   double angle=Math.PI*2*d/8;
   int x=e.center().getX()+(int)Math.round(Math.cos(angle)*distance),z=e.center().getZ()+(int)Math.round(Math.sin(angle)*distance);
   var centre=new ChunkPos(new BlockPos(x,0,z));var region=new ListTag();
   for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)region.add(LongTag.valueOf(ChunkPos.asLong(centre.x+dx,centre.z+dz)));
   var q=fresh(e,SURVEY,now);q.putLong("site",new BlockPos(x,e.center().getY(),z).asLong());q.putString("kind","survey");q.putInt("sector",sector);
   q.put("region",region);q.put("visited",new ListTag());
   Quests.store(l,village,q);announce(l,e,SURVEY,Adventures.place(q));return q;
  }
  return null;
 }
 /** A trade trip of this village is on the road: the board asks for somebody to walk with it. */
 private static void postEscorts(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();var spec=Quests.spec(ESCORT);
  if(l.getDifficulty()==Difficulty.PEACEFUL)return;
  for(var contract:Caravans.contracts(l.getServer())){
   if(!contract.getString("kind").equals("trade")||!contract.getUUID("source").equals(village)||!contract.getString("state").equals(Caravans.TRANSIT))continue;
   if(Caravans.length(contract)<spec.get("shortest").getAsInt())continue;
   if(escortFor(l,e,contract,now)!=null)return;
  }
 }
 /** The escort of one named trip on the road (the board's own choice, or an explicit request): once per trip, while the board has room. */
 public static CompoundTag escortFor(ServerLevel l,SettlementData.Entry e,CompoundTag contract,long now){
  var village=e.settlement().id();var board=Quests.board(l,village);var id=contract.getUUID("id");
  if(!contract.getString("state").equals(Caravans.TRANSIT)||!contract.getUUID("source").equals(village))return null;
  if(board.getList("quests",Tag.TAG_COMPOUND).stream().anyMatch(x->((CompoundTag)x).hasUUID("contract")&&((CompoundTag)x).getUUID("contract").equals(id)))return null;
  if(!Adventures.room(board,ESCORT))return null;
  var q=fresh(e,ESCORT,now);q.putUUID("contract",id);q.putLong("site",contract.getLong("to"));q.putString("kind","caravan");
  Quests.store(l,village,q);announce(l,e,ESCORT,Adventures.place(q));return q;
 }
 // ---------------------------------------------------------------- the embassy's letter and its answer
 /** The sealed letter the board hands over with the embassy: it names its quest, so no other paper can stand in for it. */
 static void onTake(ServerPlayer p,UUID village,CompoundTag q){
  if(!q.getString("template").equals(EMBASSY))return;
  var letter=new ItemStack(Items.PAPER);letter.getOrCreateTag().putString(LETTER,q.getUUID("id").toString());letter.getOrCreateTag().putString("AstraVillage",village.toString());
  letter.setHoverName(Component.translatable("quest.villageastra.letter"));
  if(!p.getInventory().add(letter))p.drop(letter,false);
 }
 private static boolean carries(ItemStack stack,String key,UUID quest){return stack.is(Items.PAPER)&&stack.hasTag()&&quest.toString().equals(stack.getTag().getString(key));}
 /** A resident of the village the letter is for takes it to their mayor and hands back the answer. */
 public static String deliver(ServerPlayer p,ResidentEntity npc,InteractionHand hand){
  var stack=p.getItemInHand(hand);if(!stack.is(Items.PAPER)||!stack.hasTag()||!stack.getTag().contains(LETTER))return "";
  if(npc.settlementId()==null)return "";
  UUID quest,home;
  try{quest=UUID.fromString(stack.getTag().getString(LETTER));home=UUID.fromString(stack.getTag().getString("AstraVillage"));}catch(IllegalArgumentException ex){return "";}
  var l=p.serverLevel();var q=Quests.quest(l,home,quest);
  if(q==null||!q.getString("state").equals(Quests.TAKEN)||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))return "letter_stale";
  if(!npc.settlementId().equals(q.getUUID("neighbour")))return "letter_elsewhere";
  var data=SettlementData.get(p.server);var e=data.entry(home);var target=data.entry(q.getUUID("neighbour"));if(e==null||target==null)return "letter_stale";
  // The answer: a reply to carry home, a little standing among the hosts, and a caravan of what the sender wants if they have it to spare.
  var reply=new ItemStack(Items.PAPER);reply.getOrCreateTag().putString(REPLY,quest.toString());reply.setHoverName(Component.translatable("quest.villageastra.reply"));
  p.setItemInHand(hand,reply);
  // AD-105: a letter the hosts already answered for this need, before the card came back to the board, is only answered again on paper.
  if(q.getBoolean("answered"))return "letter_answered";
  long guest=Quests.reputation(EMBASSY)*Quests.spec(EMBASSY).get("guest_share").getAsInt()/100;
  if(guest>0){var ledger=PropertyLedger.get(p.server);ledger.roll(target.settlement().id()).decay(data.clock().ticks());ledger.gift(target.settlement().id(),p.getUUID(),(int)guest);}
  var caravan=Caravans.propose(l,e,target,data.clock().ticks());
  q.putBoolean("answered",true);q.putBoolean("caravan",caravan!=null);q.putString("status","reply");Quests.store(l,home,q);
  QuestRelations.change(p.server,home,target.settlement().id(),EMBASSY);
  return caravan!=null?"letter_caravan":"letter_answered";
 }
 /** The answer is handed over at the home hall, through the journal like every other delivery. */
 static String handOver(ServerPlayer p,UUID village,UUID id){
  var l=p.serverLevel();var q=Quests.quest(l,village,id);var e=SettlementData.get(p.server).entry(village);
  if(q==null||e==null||!q.getString("state").equals(Quests.TAKEN)||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))return "unknown";
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.position(e,hall);if(chest==null)return "unknown";
  if(p.distanceToSqr(chest.getX()+.5,chest.getY()+.5,chest.getZ()+.5)>64)return "far";
  ItemStack found=ItemStack.EMPTY;for(var s:p.getInventory().items)if(carries(s,REPLY,id)){found=s;break;}
  if(found.isEmpty())return "no_reply";
  if(!WorldJournal.deposit(l,Settlement.childId(id,"reply"),chest,found.copyWithCount(1)))return "full";
  found.shrink(1);q.putInt("progress",1);Quests.store(l,village,q);Quests.complete(p,village,id);return "ok";
 }
 // ---------------------------------------------------------------- following the quests out there
 public static void tick(ServerLevel l,SettlementData.Entry e,long now){
  postEscorts(l,e,now);
  var village=e.settlement().id();var board=Quests.board(l,village);boolean changed=false;
  for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
   // AD-105: an untaken escort whose caravan is already off the road is called off, so nobody takes it only to fail.
   if(q.getString("template").equals(ESCORT)&&q.getString("state").equals(Quests.OPEN)){var gone=Quests.impossible(l,q);
    if(!gone.isEmpty()){Quests.close(l,e.settlement().id(),q,Quests.CANCELLED,gone,null);changed=true;}continue;}
   if(!TEMPLATES.contains(q.getString("template"))||!q.getString("state").equals(Quests.TAKEN)||!q.hasUUID("owner"))continue;
   changed|=step(l,e,q,l.getServer().getPlayerList().getPlayer(q.getUUID("owner")),now);}
  if(changed)Quests.saveBoard(l,village,board);
 }
 /** One step of one quest with its owner given, not looked up: the tick passes the online player, a test passes its own. */
 public static boolean advance(ServerLevel l,SettlementData.Entry e,UUID id,ServerPlayer owner,long now){
  var village=e.settlement().id();var board=Quests.board(l,village);boolean changed=false;
  for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
   if(q.getUUID("id").equals(id)&&q.getString("state").equals(Quests.TAKEN))changed|=step(l,e,q,owner,now);}
  if(changed)Quests.saveBoard(l,village,board);
  return changed;
 }
 private static boolean step(ServerLevel l,SettlementData.Entry e,CompoundTag q,ServerPlayer owner,long now){
  return switch(q.getString("template")){
   case ESCORT->escort(l,e,q,owner,now);
   case RUIN->ruin(l,q,owner);
   case SURVEY->survey(l,e,q,owner,now);
   case EMBASSY->embassy(l,e,q,owner);
   default->false;
  };
 }
 /** Walking with the caravan: the share of the road the player really kept near it, the ambush on the way and the arrival. */
 private static boolean escort(ServerLevel l,SettlementData.Entry e,CompoundTag q,ServerPlayer owner,long now){
  var spec=Quests.spec(ESCORT);var contract=Caravans.contract(l.getServer(),q.getUUID("contract"));
  // AD-105: a lost record is the mod's fault and a trip the villages called off is the world's; only a caravan lost on the road is a failure.
  if(contract==null){Quests.close(l,e.settlement().id(),q,Quests.BROKEN,"contract_missing",owner);return true;}
  if(contract.getString("state").equals(Caravans.CANCELLED)){Quests.close(l,e.settlement().id(),q,Quests.CANCELLED,"caravan_cancelled",owner);return true;}
  // A fatal outbound trip is closed after recording LOST; closing must not turn death into arrival.
  if(contract.getString("state").equals(Caravans.LOST)||contract.getCompound("stamps").contains(Caravans.LOST)){Quests.fail(l,e.settlement().id(),q);return true;}
  var npc=l.getEntity(contract.getUUID("caravaneer")) instanceof LivingEntity r&&r.isAlive()?r:null;
  String state=contract.getString("state");
  if(state.equals(Caravans.TRANSIT)){
   boolean near=npc!=null&&owner!=null&&owner.level()==l&&owner.distanceToSqr(npc)<=Math.pow(spec.get("near").getAsInt(),2);
   if(near)q.putBoolean("joined",true);
   if(q.getBoolean("joined")){q.putInt("samples",q.getInt("samples")+1);if(near)q.putInt("escorted",q.getInt("escorted")+1);}
   int share=q.getInt("samples")==0?0:q.getInt("escorted")*100/q.getInt("samples");
   // Halfway along the road, with the player there to meet it, the ambush comes out ahead of the caravan.
   if(near&&!q.getBoolean("ambushed")&&contract.getDouble("progress")*100>=Caravans.length(contract)*spec.get("ambush_at").getAsInt()){
    ambush(l,q,npc,Caravans.position(l,contract,Math.min(Caravans.length(contract),contract.getDouble("progress")+14)));q.putBoolean("ambushed",true);}
   for(var mob:ambushers(l,q,npc))if(npc!=null&&(mob.getTarget()==null||!mob.getTarget().isAlive()))mob.setTarget(npc);
   q.putString("status",q.getBoolean("joined")?"escorting":"on_road");q.putInt("status_left",share);return true;
  }
  // The trip has reached the other village: it counts if the player kept with it and no ambusher still stands.
  if(q.getInt("progress")>=q.getInt("target"))return false;
  int share=q.getInt("samples")==0?0:q.getInt("escorted")*100/q.getInt("samples");
  if(q.getBoolean("joined")&&share>=spec.get("share").getAsInt()&&(!q.getBoolean("ambushed")||ambushers(l,q,npc).isEmpty())){
   q.putInt("progress",q.getInt("target"));q.putString("status","arrived");
   QuestRelations.change(l.getServer(),contract.getUUID("source"),contract.getUUID("destination"),ESCORT);return true;}
  Quests.fail(l,e.settlement().id(),q);return true;
 }
 /** A letter to a village that is no longer there cannot be answered: the errand is called off, not failed (AD-105). */
 private static boolean embassy(ServerLevel l,SettlementData.Entry e,CompoundTag q,ServerPlayer owner){
  if(q.getBoolean("answered")||!q.hasUUID("neighbour")||SettlementData.get(l.getServer()).entry(q.getUUID("neighbour"))!=null)return false;
  Quests.close(l,e.settlement().id(),q,Quests.CANCELLED,"neighbour_gone",owner);return true;
 }
 private static void ambush(ServerLevel l,CompoundTag q,LivingEntity caravaneer,BlockPos ahead){
  int count=Quests.spec(ESCORT).get("fighters").getAsInt();var list=new ListTag();
  for(int i=0;i<count;i++){int x=ahead.getX()+(i-count/2)*2,z=ahead.getZ()+((i%2)*2-1);
   if(!l.hasChunkAt(new BlockPos(x,0,z)))continue;var spot=new BlockPos(x,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
   var mob=(i==0?EntityType.VINDICATOR:EntityType.PILLAGER).create(l);if(mob==null)continue;
   mob.moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,0,0);mob.finalizeSpawn(l,l.getCurrentDifficultyAt(spot),MobSpawnType.EVENT,null,null);
   mob.setPersistenceRequired();mob.addTag(QuestSites.MOB);mob.getPersistentData().putUUID(QuestSites.QUEST,q.getUUID("id"));
   if(caravaneer!=null)mob.setTarget(caravaneer);
   if(l.addFreshEntity(mob))list.add(NbtUtils.createUUID(mob.getUUID()));}
  q.put("ambush",list);
 }
 private static List<Mob> ambushers(ServerLevel l,CompoundTag q,LivingEntity around){
  var out=new ArrayList<Mob>();
  for(var raw:q.getList("ambush",Tag.TAG_INT_ARRAY))if(l.getEntity(NbtUtils.loadUUID(raw)) instanceof Mob m&&m.isAlive())out.add(m);
  return out;
 }
 /** The player really stands inside the structure: in one of its pieces, not merely near the cross. */
 private static boolean ruin(ServerLevel l,CompoundTag q,ServerPlayer owner){
  if(owner==null||owner.level()!=l||q.getInt("progress")>=q.getInt("target"))return false;
  var site=BlockPos.of(q.getLong("site"));if(owner.blockPosition().distSqr(site)>160*160)return false;
  var structure=l.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.tryParse(q.getString("structure")));
  if(structure==null){Quests.close(l,q.getUUID("village"),q,Quests.BROKEN,"structure_unknown",owner);return true;}
  if(!l.structureManager().getStructureWithPieceAt(owner.blockPosition(),structure).isValid())return false;
  q.putInt("progress",q.getInt("target"));q.putString("status","explored");return true;
 }
 /** Chunks of the far land the player has really walked into; once enough are known the village writes down a new lead there. */
 private static boolean survey(ServerLevel l,SettlementData.Entry e,CompoundTag q,ServerPlayer owner,long now){
  if(owner==null||owner.level()!=l||q.getInt("progress")>=q.getInt("target"))return false;
  long here=new ChunkPos(owner.blockPosition()).toLong();
  boolean inside=q.getList("region",Tag.TAG_LONG).stream().anyMatch(x->((LongTag)x).getAsLong()==here);
  var visited=q.getList("visited",Tag.TAG_LONG);
  if(!inside||visited.stream().anyMatch(x->((LongTag)x).getAsLong()==here))return false;
  visited.add(LongTag.valueOf(here));q.put("visited",visited);q.putInt("progress",visited.size());q.putString("status","chunks");q.putInt("status_left",Math.max(0,q.getInt("target")-visited.size()));
  if(visited.size()>=q.getInt("target")){
   var at=owner.blockPosition();var kind=Expeditions.inspect(l,at);
   Expeditions.addLead(l,e.settlement().id(),at,kind.isEmpty()?Expeditions.CAMP:kind,q.getInt("sector"),now);
   owner.displayClientMessage(Component.translatable("quest.villageastra.survey_lead",Component.translatable("quest.villageastra.side."+Adventures.bearing(e.center(),at))),false);}
  return true;
 }
}
