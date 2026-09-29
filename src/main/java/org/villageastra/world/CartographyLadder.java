package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

import org.villageastra.domain.CoreEffects;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-158, the cartography ladder (owner's ladder of 2026-09-23): what each level of the cartographer's house adds beyond the survey
 *  margin that AD-123 already gives it.
 *  <p>II — once the whole atlas area is on the map, the cartographer lights the dark spots of his village, taking torches from his own
 *  chest ({@link CartographerLightGoal}); VI — the survey goes on by itself, without anybody walking out to the chunk, and still pays its
 *  paper. The levels in between are the margin the atlas already grows by (core effect cartographer.survey). */
public final class CartographyLadder {
 private CartographyLadder(){}
 public static final String CORE="cartographer",HOUSE="cartographer";
 /** How often the level-VI atlas draws one more chunk by itself, and how far a cartographer looks for a dark spot. */
 public static final int AUTO_EVERY=200,LOOK=48,STEP=4,LIT=7,ABOVE=6,BELOW=4,PAW=3;
 /** The best working level of this village's cartographer houses, or 0 without one. */
 public static int level(ServerLevel l,SettlementData.Entry e){
  int best=0;for(var b:e.settlement().buildings())if(b.type().equals(HOUSE))best=Math.max(best,BuildingLevels.level(l,e,b));
  return best;
 }
 /** The cartographer's house of this village that works best, or null. */
 public static Settlement.Building house(ServerLevel l,SettlementData.Entry e){
  Settlement.Building best=null;int level=0;
  for(var b:e.settlement().buildings())if(b.type().equals(HOUSE)){int n=BuildingLevels.level(l,e,b);if(n>level){level=n;best=b;}}
  return best;
 }
 private static boolean knob(ServerLevel l,SettlementData.Entry e,String effect){return on(effect,level(l,e))>0;}
 /** The ladder's knob at a house level (1 = the step is open), as the game reads it. */
 public static int on(String effect,int level){return level>0&&CoreEffects.active(CORE,effect)?CoreEffects.value(CORE,effect,level):0;}
 /** II: the cartographer lights his village once it is all on the map. */
 public static boolean lights(ServerLevel l,SettlementData.Entry e){return knob(l,e,"lighting");}
 /** III: the house keeps a second cartographer, and he goes looking for the villages around (staff.json gives him his seat). */
 public static boolean scouts(ServerLevel l,SettlementData.Entry e){return knob(l,e,"scouts");}
 /** V: the cartographer trains one of the kennel's wolves to walk the village and carry his torches. */
 public static boolean patrols(ServerLevel l,SettlementData.Entry e){return knob(l,e,"patrol");}
 /** VI: the atlas draws itself. */
 public static boolean draws(ServerLevel l,SettlementData.Entry e){return knob(l,e,"auto");}
 /** Whether every chunk of the atlas area is already on the map. */
 public static boolean mapped(ServerLevel l,SettlementData.Entry e){
  var done=Atlas.surveyed(Atlas.inspect(l,e.settlement().id()));
  for(var c:Atlas.area(l,e))if(!done.contains(c.toLong()))return false;
  return true;
 }

 // ---------------------------------------------------------------- II: the dark spots of the village
 /** A spot of the village's own land that no light reaches: standing ground under the open sky or a roof, dark, with nothing burning
  *  near it. The nearest such spot to where the cartographer stands, or null when the village is lit. */
 public static BlockPos dark(ServerLevel l,SettlementData.Entry e,BlockPos from){
  var area=new HashSet<Long>();for(var c:Atlas.area(l,e))area.add(c.toLong());
  BlockPos best=null;double nearest=Double.MAX_VALUE;
  for(int dx=-LOOK;dx<=LOOK;dx+=STEP)for(int dz=-LOOK;dz<=LOOK;dz+=STEP){
   int x=from.getX()+dx,z=from.getZ()+dz;
   if(!area.contains(new ChunkPos(x>>4,z>>4).toLong())||!l.hasChunkAt(new BlockPos(x,from.getY(),z)))continue;
   double away=dx*(double)dx+dz*(double)dz;if(away>=nearest)continue;
   // The village's own level, not whatever stands over it: a cartographer lights his streets, not the surface of the lake above them.
   // A column is looked at whole: a lit landing over a dark yard must not hide the yard.
   var spot=darkIn(l,x,z,e.center().getY());
   if(spot==null)continue;
   best=spot;nearest=away;}
  return best;
 }
 /** The dark standing spot of that column within the village's own band of height, or null: every spot of the band is looked at, from a
  *  little above the village's level downwards. */
 private static BlockPos darkIn(ServerLevel l,int x,int z,int level){
  for(int y=level+ABOVE;y>=level-BELOW;y--){var spot=new BlockPos(x,y,z);
   if(standing(l,spot)&&l.getBrightness(LightLayer.BLOCK,spot)==0&&!lit(l,spot))return spot;}
  return null;
 }
 /** Ground a torch can stand on: solid under, room for the torch and a head above it, no water. */
 private static boolean standing(ServerLevel l,BlockPos spot){
  if(spot.getY()<=l.getMinBuildHeight()+1||spot.getY()>=l.getMaxBuildHeight()-2)return false;
  var under=l.getBlockState(spot.below());
  if(!under.isFaceSturdy(l,spot.below(),net.minecraft.core.Direction.UP)||!l.getFluidState(spot.below()).isEmpty())return false;
  return l.getBlockState(spot).isAir()&&l.getBlockState(spot.above()).isAir()&&l.getFluidState(spot).isEmpty();
 }
 /** Something already burning nearby: one torch lights its own corner, not every block of it. */
 private static boolean lit(ServerLevel l,BlockPos spot){
  for(int dx=-LIT;dx<=LIT;dx+=1)for(int dz=-LIT;dz<=LIT;dz+=1)for(int dy=-2;dy<=2;dy++){
   var at=spot.offset(dx,dy,dz);
   if(l.getBlockState(at).is(net.minecraft.tags.BlockTags.CANDLES)||l.getBlockState(at).is(Blocks.TORCH)||l.getBlockState(at).is(Blocks.WALL_TORCH)
    ||l.getBlockState(at).is(Blocks.LANTERN)||l.getBlockState(at).is(Blocks.CAMPFIRE))return true;}
  return false;
 }
 /** Puts one torch of the house's own chest on that spot. False when the chest has none or the spot will not hold it. */
 public static boolean light(ServerLevel l,SettlementData.Entry e,Settlement.Building office,BlockPos spot){
  var chest=LogisticsRoutes.chest(l,e,office);if(chest==null)return false;
  int slot=-1;
  for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(Items.TORCH)&&!chest.getItem(i).hasTag()){slot=i;break;}
  if(slot<0||!standing(l,spot))return false;
  var torch=Blocks.TORCH.defaultBlockState();
  if(!torch.canSurvive(l,spot))return false;
  // The village lights its own land: this is the settlement's own work, not a quest site laid over somebody's blocks.
  if(!l.setBlock(spot,torch,3))return false;
  chest.getItem(slot).shrink(1);chest.setChanged();
  return true;
 }

 // ---------------------------------------------------------------- III: the villages around, and the errands that follow them
 /** Where a village keeps what its second cartographer has found. */
 public static java.nio.file.Path path(ServerLevel l,UUID village){
  return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-cartography/"+village+".bin");
 }
 private static net.minecraft.nbt.CompoundTag record(ServerLevel l,UUID village){
  var p=path(l,village);return java.nio.file.Files.exists(p)?org.villageastra.persistence.NbtRecord.read(p):new net.minecraft.nbt.CompoundTag();
 }
 /** The villages this one has been told of by its own cartographers, in the order they were found. */
 public static List<UUID> neighbours(ServerLevel l,UUID village){
  var out=new ArrayList<UUID>();
  for(var raw:record(l,village).getList("neighbours",net.minecraft.nbt.Tag.TAG_INT_ARRAY))out.add(net.minecraft.nbt.NbtUtils.loadUUID(raw));
  return out;
 }
 /** Whether this village has been found by the cartographers of that one. */
 public static boolean knows(ServerLevel l,UUID village,UUID neighbour){return neighbours(l,village).contains(neighbour);}
 /** Writes one more village down. */
 public static void found(ServerLevel l,UUID village,UUID neighbour){
  var t=record(l,village);var list=t.getList("neighbours",net.minecraft.nbt.Tag.TAG_INT_ARRAY);
  for(var raw:list)if(net.minecraft.nbt.NbtUtils.loadUUID(raw).equals(neighbour))return;
  list.add(net.minecraft.nbt.NbtUtils.createUUID(neighbour));t.putInt("schema",1);t.put("neighbours",list);
  org.villageastra.persistence.NbtRecord.write(path(l,village),t);
 }
 /** One journey of the second cartographer: the nearest village within the embassy's reach that this one has not heard of yet.
  *  Nothing is found before the village's own land is wholly on the map — the map comes first at every level. */
 public static UUID scout(ServerLevel l,SettlementData.Entry e){
  if(!scouts(l,e)||!mapped(l,e))return null;
  long reach=(long)Quests.spec(Wilds.EMBASSY).get("reach").getAsInt();
  var known=neighbours(l,e.settlement().id());
  var target=SettlementData.get(l.getServer()).entries().stream()
   .filter(x->!x.settlement().id().equals(e.settlement().id())&&x.dimension().equals(e.dimension())
    &&x.center().distSqr(e.center())<=reach*reach&&!known.contains(x.settlement().id()))
   .min(Comparator.comparingDouble(x->x.center().distSqr(e.center()))).orElse(null);
  if(target==null)return null;
  found(l,e.settlement().id(),target.settlement().id());
  for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
   player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("quest.villageastra.carto.found",
    target.settlement().name(),net.minecraft.network.chat.Component.translatable("quest.villageastra.side."+Adventures.bearing(e.center(),target.center())),
    Adventures.away(e.center(),target.center())));
  return target.settlement().id();
 }

 // ---------------------------------------------------------------- V: the wolf the cartographer trains
 /** The mark a trained wolf carries: the village whose land it walks. */
 public static final String PATROL="AstraPatrolWolf";
 /** A trained specialist is not simultaneously offered to a courier, cart or cull. */
 public static boolean reserved(ServerLevel l,SettlementData.Entry e,net.minecraft.world.entity.animal.Wolf wolf){
  return wolf.getPersistentData().hasUUID(PATROL)&&wolf.getPersistentData().getUUID(PATROL).equals(e.settlement().id())&&patrols(l,e);
 }
 /** The village's patrol wolf, or null: one of its own kennel wolves, alive and still marked. */
 public static net.minecraft.world.entity.animal.Wolf patrolWolf(ServerLevel l,SettlementData.Entry e){
  for(var id:VillageWolves.wolves(l,e))
   if(l.getEntity(id) instanceof net.minecraft.world.entity.animal.Wolf wolf&&wolf.isAlive()
    &&wolf.getPersistentData().hasUUID(PATROL)&&wolf.getPersistentData().getUUID(PATROL).equals(e.settlement().id()))return wolf;
  return null;
 }
 /** V: the cartographer trains one wolf of the kennel — the first that has no other duty. One at a time, and only at his fifth level. */
 public static net.minecraft.world.entity.animal.Wolf train(ServerLevel l,SettlementData.Entry e){
  if(!patrols(l,e))return null;
  var t=record(l,e.settlement().id());var roster=VillageWolves.wolves(l,e);
  // The registry survives chunk unload: an absent trained wolf is not a vacant job.
  if(t.hasUUID("patrol")&&roster.contains(t.getUUID("patrol"))){
   return l.getEntity(t.getUUID("patrol")) instanceof net.minecraft.world.entity.animal.Wolf w&&w.isAlive()?w:null;}
  var already=patrolWolf(l,e);if(already!=null){t.putUUID("patrol",already.getUUID());org.villageastra.persistence.NbtRecord.write(path(l,e.settlement().id()),t);return already;}
  var wolf=VillageWolves.freeWolf(l,e);if(wolf!=null){
   wolf.getPersistentData().putUUID(PATROL,e.settlement().id());
   t.putInt("schema",1);t.putUUID("patrol",wolf.getUUID());org.villageastra.persistence.NbtRecord.write(path(l,e.settlement().id()),t);
   for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("quest.villageastra.carto.patrol"));
   return wolf;}
  return null;
 }
 /** One round of the patrol: the wolf is sent to the nearest dark corner of the village, and the torch it carries is put there when it
  *  stands by it. Returns the corner it is walking to, or null when the village is lit or nothing is left to light it with. */
 public static BlockPos patrol(ServerLevel l,SettlementData.Entry e,net.minecraft.world.entity.animal.Wolf wolf){
  // The wolf is not the mapmaker: it walks and carries torches whether or not the last chunk of the area is drawn yet.
  if(wolf==null||!reserved(l,e,wolf)||!VillageWolves.ready(l,wolf))return null;
  var office=house(l,e);if(office==null)return null;
  var spot=dark(l,e,wolf.blockPosition());if(spot==null)return null;
  if(wolf.blockPosition().distSqr(spot)<=(long)PAW*PAW){light(l,e,office,spot);return spot;}
  wolf.getNavigation().moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,1.1);
  return spot;
 }

 // ---------------------------------------------------------------- VI: the atlas that draws itself
 /** The level-VI pass: one more chunk of the area on the map, paid for out of the house's chest like any other. */
 public static void tick(MinecraftServer server,long now){
  if(Math.floorMod(now,AUTO_EVERY)!=0)return;
  var data=SettlementData.get(server);
  for(var e:List.copyOf(data.entries())){
   var l=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
    new net.minecraft.resources.ResourceLocation(e.dimension())));
   if(l==null)continue;
   // III: the second cartographer's journeys — one village found at a time, and only once the village's own land is mapped.
   scout(l,e);
   // V: the trained wolf walks the village and carries the cartographer's torches into its dark corners.
   train(l,e); // WolfPatrolGoal owns movement, rather than fighting WolfKennelGoal every 200 ticks.
   if(!draws(l,e))continue;
   var office=house(l,e);if(office==null)continue;
   var next=Atlas.next(l,e,e.center(),Set.of());if(next==null)continue;
   if(!l.hasChunk(next.x,next.z))continue;
   Atlas.survey(l,e,office,e.settlement().id(),next,now);}
 }
}
