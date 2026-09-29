package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-041: an expeditioner walks out and brings back real leads — places that actually exist in the world. Distant quests point only at leads someone has visited. */
public final class Expeditions {
 public static final int MIN_RANGE=64,MAX_RANGE=120,SECTORS=8,CLEAR_RADIUS=3,SURVEY_TICKS=60;
 public static final String CAMP="camp",RESOURCE="resource";
 private Expeditions(){}
 public static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-expeditions/"+village+".bin");}
 public static CompoundTag record(ServerLevel l,UUID village){var p=path(l,village);if(Files.exists(p))return NbtRecord.read(p);var t=new CompoundTag();t.putInt("schema",1);t.put("leads",new ListTag());return t;}
 public static void save(ServerLevel l,UUID village,CompoundTag t){NbtRecord.write(path(l,village),t);}
 public static List<CompoundTag> leads(ServerLevel l,UUID village){var out=new ArrayList<CompoundTag>();for(var raw:record(l,village).getList("leads",Tag.TAG_COMPOUND))out.add((CompoundTag)raw);return out;}
 public static CompoundTag lead(ServerLevel l,UUID village,String kind,boolean freeOnly){
  for(var t:leads(l,village))if(t.getString("kind").equals(kind)&&(!freeOnly||!t.getBoolean("used")))return t;return null;}
 public static void useLead(ServerLevel l,UUID village,BlockPos pos,boolean used){
  var t=record(l,village);for(var raw:t.getList("leads",Tag.TAG_COMPOUND)){var lead=(CompoundTag)raw;if(BlockPos.of(lead.getLong("pos")).equals(pos))lead.putBoolean("used",used);}save(l,village,t);}
 /** AD-091: a lead written down from somebody else's walk — a player's survey of far land. Sectors 8..15 are the outer ring, so the scout's own 0..7 stay untouched. */
 public static CompoundTag addLead(ServerLevel l,UUID village,BlockPos at,String kind,int sector,long now){
  var t=record(l,village);for(var raw:t.getList("leads",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getInt("sector")==sector)return (CompoundTag)raw;
  var lead=new CompoundTag();lead.putLong("pos",at.asLong());lead.putString("kind",kind);lead.putInt("sector",sector);lead.putLong("tick",now);lead.putBoolean("used",false);
  var list=t.getList("leads",Tag.TAG_COMPOUND);list.add(lead);t.put("leads",list);save(l,village,t);return lead;
 }
 /** AD-112: how far from the centre an expeditioner walks at this working level of the expedition house ((MIN_RANGE+MAX_RANGE)/2 at level I). */
 public static int range(int level){return CoreEffects.value("expedition","range",level);}
 /** How far this village's expeditioners walk, by its best expedition house. */
 public static int range(ServerLevel l,SettlementData.Entry e){return range(BuildingLevels.best(l,e,"expedition"));}
 /** The next unexplored sector around the settlement; the expeditioner goes there in person. */
 public static BlockPos target(ServerLevel l,SettlementData.Entry e,int sector){
  double angle=Math.PI*2*sector/SECTORS;int range=range(l,e);
  int x=e.center().getX()+(int)Math.round(Math.cos(angle)*range),z=e.center().getZ()+(int)Math.round(Math.sin(angle)*range);
  return new BlockPos(x,l.hasChunkAt(new BlockPos(x,0,z))?l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z):e.center().getY(),z);
 }
 public static int nextSector(ServerLevel l,UUID village){var done=new HashSet<Integer>();for(var t:leads(l,village))done.add(t.getInt("sector"));for(int i=0;i<SECTORS;i++)if(!done.contains(i))return i;return -1;}
 public static BlockPos surface(ServerLevel l,int x,int z,int nearY){return surface(l,x,z,nearY,2,4);}
 /** Ground a walker can really reach from the given level: at most {@code up} blocks higher and {@code down} lower, with two free cells above. */
 public static BlockPos surface(ServerLevel l,int x,int z,int nearY,int up,int down){
  for(int y=nearY+up;y>=nearY-down;y--){var pos=new BlockPos(x,y,z);
   if(l.getBlockState(pos).isFaceSturdy(l,pos,net.minecraft.core.Direction.UP)&&l.getBlockState(pos.above()).isAir()&&l.getBlockState(pos.above(2)).isAir())return pos;}
  return null;
 }
 /** What the world really offers at the visited spot: an open clearing that can hold a camp, or exposed stone and ore for the miners. */
 public static String inspect(ServerLevel l,BlockPos at){
  int open=0,stone=0;
  for(int dx=-CLEAR_RADIUS;dx<=CLEAR_RADIUS;dx++)for(int dz=-CLEAR_RADIUS;dz<=CLEAR_RADIUS;dz++){
   if(!l.hasChunkAt(new BlockPos(at.getX()+dx,0,at.getZ()+dz)))return "";
   var ground=surface(l,at.getX()+dx,at.getZ()+dz,at.getY());if(ground==null)continue;open++;
   if(l.getBlockState(ground).is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)||l.getBlockState(ground).is(net.minecraft.tags.BlockTags.IRON_ORES)||l.getBlockState(ground).is(net.minecraft.tags.BlockTags.COAL_ORES))stone++;
  }
  int cells=(CLEAR_RADIUS*2+1)*(CLEAR_RADIUS*2+1);
  return open>=cells*3/4?CAMP:stone>=cells/3?RESOURCE:"";
 }
 /** Records one lead for real supplies from the expedition building: bread for the road and paper for the report. */
 public static CompoundTag report(ServerLevel l,SettlementData.Entry e,Settlement.Building office,BlockPos at,int sector,long now){
  var kind=inspect(l,at);if(kind.isEmpty())return null;
  var chest=LogisticsRoutes.chest(l,e,office);if(chest==null)return null;var pos=LogisticsRoutes.position(e,office);var village=e.settlement().id();
  var id=Settlement.childId(village,"expedition/"+sector+"/"+at.asLong());
  if(!WorldJournal.exists(l,id)){
   int paperSlot=-1,breadSlot=-1;
   for(int slot=0;slot<chest.getContainerSize();slot++){var s=chest.getItem(slot);if(s.is(Items.PAPER)&&paperSlot<0)paperSlot=slot;if(s.is(Items.BREAD)&&breadSlot<0)breadSlot=slot;}
   if(paperSlot<0||breadSlot<0)return null;
   if(WorldJournal.takeAmount(l,id,pos,paperSlot,chest.getItem(paperSlot).copy(),1).isEmpty())return null;
   var breadId=Settlement.childId(id,"rations");if(!WorldJournal.exists(l,breadId)&&WorldJournal.takeAmount(l,breadId,pos,breadSlot,chest.getItem(breadSlot).copy(),1).isEmpty())return null;
  }
  var t=record(l,village);for(var raw:t.getList("leads",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getInt("sector")==sector)return (CompoundTag)raw;
  var lead=new CompoundTag();lead.putLong("pos",at.asLong());lead.putString("kind",kind);lead.putInt("sector",sector);lead.putLong("tick",now);lead.putBoolean("used",false);
  t.getList("leads",Tag.TAG_COMPOUND).add(lead);t.put("leads",t.getList("leads",Tag.TAG_COMPOUND));save(l,village,t);return lead;
 }
}
