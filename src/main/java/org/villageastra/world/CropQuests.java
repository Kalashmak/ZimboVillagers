package org.villageastra.world;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
/** AD-088: the quests that open a crop or a sapling for the village (the rule itself is CropUnlocks, AD-093). The village asks for real seeds
 *  or saplings of a kind it cannot plant yet; they are handed over at its stock through the journal, and the moment the last of them lies
 *  there, the farm and the nursery may plant that kind. A sapling that grows only in some country comes with a chart whose cross stands on
 *  a real spot of that country. */
public final class CropQuests {
 public static final String CROP="crop";
 public static final int EVERY=Quests.spec(CROP).get("every").getAsInt();
 /** Where each sapling grows by nature, so the chart can point at a real grove of it. */
 private static final Map<String,Predicate<Holder<Biome>>> GROVES=new LinkedHashMap<>();
 static{
  GROVES.put("minecraft:birch_sapling",h->h.is(Biomes.BIRCH_FOREST)||h.is(Biomes.OLD_GROWTH_BIRCH_FOREST));
  GROVES.put("minecraft:spruce_sapling",h->h.is(BiomeTags.IS_TAIGA));
  GROVES.put("minecraft:jungle_sapling",h->h.is(BiomeTags.IS_JUNGLE));
  GROVES.put("minecraft:acacia_sapling",h->h.is(BiomeTags.IS_SAVANNA));
  GROVES.put("minecraft:cherry_sapling",h->h.is(Biomes.CHERRY_GROVE));
  GROVES.put("minecraft:mangrove_propagule",h->h.is(Biomes.MANGROVE_SWAMP));
 }
 private CropQuests(){}
 private static Item item(String id){return BuiltInRegistries.ITEM.get(new ResourceLocation(id));}
 /** How many of a kind the village asks for: a handful of saplings, a stack of seeds. */
 static int count(String id){
  var spec=Quests.spec(CROP);
  if(CropUnlocks.SAPLINGS.contains(id))return spec.get("sapling_count").getAsInt();
  if(id.equals("minecraft:sugar_cane"))return spec.get("sugar_cane").getAsInt();
  return spec.get("count").getAsInt();
 }
 /** Posts one quest for the first kind the village cannot plant yet. AD-131 (check fix 9): crops and saplings have a slot each — one quest for
  *  a crop of the farm and one for a sapling may stand on the board together (within Quests.MAX_OPEN) while the village has a forester's hut of
  *  level III or more; without one a sapling is asked for only once every crop is open, as before (the mangrove never: no forester plants it). */
 public static CompoundTag post(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();var board=Quests.board(l,village);int open=0;boolean crop=false,sapling=false;
  for(var raw:board.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var state=q.getString("state");
   if(!state.equals(Quests.OPEN)&&!state.equals(Quests.TAKEN))continue;open++;
   if(q.getString("template").equals(CROP)){if(CropUnlocks.SAPLINGS.contains(q.getString("item")))sapling=true;else crop=true;}}
  if(open>=Quests.MAX_OPEN)return null;
  String id=null;var locked=CropUnlocks.locked(l,e);
  if(!crop)for(var c:locked)if(CropUnlocks.CROPS.contains(c)){id=c;break;}
  // The crops' slot passes on to the saplings once every crop is open (AD-088); the saplings' own slot needs a hut of III.
  if(id==null&&!sapling&&(!crop||saplingsAsked(l,e)))for(var c:locked)if(CropUnlocks.SAPLINGS.contains(c)&&ForestWork.PLANTED.contains(c)){id=c;break;}
  if(id==null)return null;int count=count(id);
  var q=Quests.blank(village,CROP,now,Quests.deadline(CROP));
  q.putString("item",id);q.putInt("target",count);
  q.putLong("coins",Quests.coins(CROP)+Quests.value(item(id),count));q.putLong("reputation",Quests.reputation(CROP));
  var grove=GROVES.get(id);
  if(grove!=null){
   var found=l.findClosestBiome3d(grove,e.center(),Quests.spec(CROP).get("biome_radius").getAsInt(),32,64);
   var at=found==null?null:inside(l,grove,found.getFirst());
   if(at!=null){q.putLong("site",at.asLong());q.putString("kind","grove");}
  }
  Quests.store(l,village,q);
  for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
   player.sendSystemMessage(Component.translatable("quest.villageastra.crop_posted",new ItemStack(item(id)).getHoverName(),count));
  return q;
 }
 /** AD-131: whether the village asks for saplings — it has a forester's hut working at level III or more. */
 public static boolean saplingsAsked(ServerLevel l,SettlementData.Entry e){for(var b:e.settlement().buildings())if(b.type().equals(ForesterHut.TYPE)&&BuildingLevels.level(l,e,b)>=3)return true;return false;}
 /** AD-131: the sapling a quest on the village board asks for now ("" when none). */
 public static String saplingQuest(ServerLevel l,SettlementData.Entry e){
  for(var raw:Quests.board(l,e.settlement().id()).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var state=q.getString("state");
   if((state.equals(Quests.OPEN)||state.equals(Quests.TAKEN))&&q.getString("template").equals(CROP)&&CropUnlocks.SAPLINGS.contains(q.getString("item")))return q.getString("item");}
  return "";
 }
 /** The biome search works on quarter-chunk cells, while the world blends neighbouring cells at their edges: the cross goes on the first
  *  spot around the found cell where the world itself says the country is, or nowhere. */
 private static BlockPos inside(ServerLevel l,Predicate<Holder<Biome>> grove,BlockPos from){
  for(int r=0;r<=16;r+=4)for(int dx=-r;dx<=r;dx+=4)for(int dz=-r;dz<=r;dz+=4){
   if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
   var at=from.offset(2+dx,0,2+dz);if(grove.test(l.getBiome(at)))return at;}
  return null;
 }
 /** Seeds or saplings handed over at the village stock, one at a time through the journal; the last of them opens the kind. */
 public static String handOver(ServerPlayer p,UUID village,UUID id){
  var l=p.serverLevel();var q=Quests.quest(l,village,id);var e=SettlementData.get(p.server).entry(village);
  if(q==null||e==null||!q.getString("state").equals(Quests.TAKEN)||!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID()))return "unknown";
  var stock=Caravans.stock(e);var chest=stock==null?null:LogisticsRoutes.position(e,stock);if(chest==null)return "unknown";
  if(p.distanceToSqr(chest.getX()+.5,chest.getY()+.5,chest.getZ()+.5)>64)return "far";
  var kind=item(q.getString("item"));int carried=0;
  for(var s:p.getInventory().items)if(s.is(kind)&&Trade.plain(s))carried+=s.getCount();
  if(carried<=0)return "empty";int moved=0;
  for(int i=0;i<carried&&q.getInt("progress")+moved<q.getInt("target");i++){
   if(!WorldJournal.deposit(l,Settlement.childId(id,"crop/"+(q.getInt("progress")+moved)),chest,new ItemStack(kind,1)))break;
   for(var s:p.getInventory().items)if(s.is(kind)&&Trade.plain(s)){s.shrink(1);break;}
   moved++;}
  if(moved==0)return "full";
  q.putInt("progress",q.getInt("progress")+moved);Quests.store(l,village,q);
  if(q.getInt("progress")>=q.getInt("target")&&CropUnlocks.unlock(l,e,q.getString("item"),"quest"))
   for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
    player.sendSystemMessage(Component.translatable("quest.villageastra.crop_unlocked",new ItemStack(kind).getHoverName()));
  Quests.complete(p,village,id);return "ok";
 }
}
