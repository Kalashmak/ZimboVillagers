package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-037: the shared settlement map. Cartographers personally survey chunks for paper; the last cartographer's death destroys the opened map. */
public final class Atlas {
 /** How deep under the surface one column is remembered: at least COLUMN blocks, always down to BELOW_GROUND under the real ground, never more than MAX_COLUMN. */
 public static final int COLUMN=24,BELOW_GROUND=16,MAX_COLUMN=96;
 public static final int MARGIN=2,RADIUS_LIMIT=12;
 /** Chunks per network page: deep columns are heavy, so a page stays far under the packet limit. */
 public static final int PAGE=4;
 private Atlas(){}
 public static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-atlas/"+village+".bin");}
 public static CompoundTag inspect(ServerLevel l,UUID village){var p=path(l,village);if(Files.exists(p))return NbtRecord.read(p);var t=new CompoundTag();t.putInt("schema",1);t.putLong("generation",0);t.put("chunks",new ListTag());t.put("cartographers",new ListTag());return t;}
 private static void write(ServerLevel l,UUID village,CompoundTag t){NbtRecord.write(path(l,village),t);}
 /** AD-123: chunks surveyed beyond the village at a cartographer level: the core effect cartographer.survey when active, else MARGIN. */
 public static int margin(int level){return CoreEffects.active("cartographer","survey")?CoreEffects.value("cartographer","survey",level):MARGIN;}
 private record Margin(long time,int value){}
 private static final Map<UUID,Margin> MARGINS=new java.util.concurrent.ConcurrentHashMap<>();
 /** The survey margin of a village: the best working level of its cartographer houses (AD-112 core grade included), read once per tick. */
 public static int margin(ServerLevel l,SettlementData.Entry e){
  long now=l.getGameTime();var id=e.settlement().id();var m=MARGINS.get(id);if(m!=null&&m.time==now)return m.value;
  int best=0;for(var b:e.settlement().buildings())if(b.type().equals("cartographer"))best=Math.max(best,BuildingLevels.level(l,e,b));
  int value=best<1?MARGIN:margin(best);MARGINS.put(id,new Margin(now,value));return value;
 }
 /** Drops the cached margins (server stop, tests that raise a level within one tick). */
 public static void forgetMargins(){MARGINS.clear();}
 /** The atlas area with the village's own survey margin (AD-123). */
 public static List<ChunkPos> area(ServerLevel l,SettlementData.Entry e){return area(e,margin(l,e));}
 /** Chunks of the settlement (centre and every building footprint) plus MARGIN chunks beyond the outermost ones. */
 public static List<ChunkPos> area(SettlementData.Entry e){return area(e,MARGIN);}
 public static List<ChunkPos> area(SettlementData.Entry e,int margin){
  var c=new ChunkPos(e.center());int minX=c.x,maxX=c.x,minZ=c.z,maxZ=c.z;
  for(var b:e.settlement().buildings()){var d=BuildingBlueprints.design(b.type());var a=new ChunkPos(e.center().offset(b.x(),0,b.z()));var size=BuildingPlacement.size(b.type(),b.rotation());var z=new ChunkPos(e.center().offset(b.x()+size[0]-1,0,b.z()+size[1]-1));
   minX=Math.min(minX,Math.min(a.x,z.x));maxX=Math.max(maxX,Math.max(a.x,z.x));minZ=Math.min(minZ,Math.min(a.z,z.z));maxZ=Math.max(maxZ,Math.max(a.z,z.z));}
  minX=Math.max(minX-margin,c.x-RADIUS_LIMIT);maxX=Math.min(maxX+margin,c.x+RADIUS_LIMIT);minZ=Math.max(minZ-margin,c.z-RADIUS_LIMIT);maxZ=Math.min(maxZ+margin,c.z+RADIUS_LIMIT);
  var result=new ArrayList<ChunkPos>();for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)result.add(new ChunkPos(x,z));return result;
 }
 public static Set<Long> surveyed(CompoundTag t){var s=new HashSet<Long>();for(var raw:t.getList("chunks",Tag.TAG_COMPOUND))s.add(((CompoundTag)raw).getLong("pos"));return s;}
 /** Next unsurveyed chunk of the area: the map grows in rings from the town hall's chunk outwards, and inside one ring the cartographer takes
  *  the chunk nearest to where they stand; chunks other cartographers are already walking to are skipped. */
 public static ChunkPos next(ServerLevel l,SettlementData.Entry e,BlockPos from,Set<Long> claimed){
  var done=surveyed(inspect(l,e.settlement().id()));var hall=new ChunkPos(e.center());
  return area(l,e).stream().filter(c->!done.contains(c.toLong())&&!claimed.contains(c.toLong()))
   .min(Comparator.<ChunkPos>comparingInt(c->ring(hall,c)).thenComparingDouble(c->from.distSqr(new BlockPos(c.getMiddleBlockX(),from.getY(),c.getMiddleBlockZ())))).orElse(null);
 }
 /** Ring of a chunk around the hall's chunk: 0 is the hall itself, 1 the eight chunks around it, and so on. */
 public static int ring(ChunkPos hall,ChunkPos c){return Math.max(Math.abs(c.x-hall.x),Math.abs(c.z-hall.z));}
 /** Surveys one loaded chunk: exactly one paper leaves the office chest per generation and chunk, then the real surface heights and map colours are recorded. */
 public static boolean survey(ServerLevel l,SettlementData.Entry e,Settlement.Building office,UUID cartographer,ChunkPos chunk,long now){
  var village=e.settlement().id();var t=inspect(l,village);if(surveyed(t).contains(chunk.toLong()))return true;
  if(!l.hasChunk(chunk.x,chunk.z)||area(l,e).stream().noneMatch(c->c.equals(chunk)))return false;
  var chest=LogisticsRoutes.chest(l,e,office);if(chest==null)return false;
  var id=Settlement.childId(village,"atlas/"+t.getLong("generation")+"/"+chunk.toLong());var paid=WorldJournal.recoverAmount(l,id);
  if(paid.isEmpty()&&!WorldJournal.exists(l,id))for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(Items.PAPER)&&!chest.getItem(slot).hasTag()){paid=WorldJournal.takeAmount(l,id,LogisticsRoutes.position(e,office),slot,chest.getItem(slot).copy(),1);break;}
  if(paid.isEmpty())return false;
  // AD-048: the map needs the material of the walls too, not only of the roof, and it has to know water to draw it flat.
  int[] heights=new int[256],colors=new int[256],walls=new int[256],water=new int[256],tops=new int[256],faces=new int[256],index=new int[256];
  var runs=new ArrayList<Integer>();
  // AD-056: one floor for the whole chunk — every column reaches it (unless that would pass MAX_COLUMN), so a chunk seen on its own has a level base.
  int floor=Integer.MAX_VALUE;
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){int x=chunk.getMinBlockX()+dx,z=chunk.getMinBlockZ()+dz,y=l.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
   floor=Math.min(floor,Math.min(y-COLUMN+1,ground(l,x,y,z)-BELOW_GROUND));}
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){int x=chunk.getMinBlockX()+dx,z=chunk.getMinBlockZ()+dz,y=l.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;var pos=new BlockPos(x,y,z);var state=l.getBlockState(pos);
   heights[dx*16+dz]=y;colors[dx*16+dz]=state.getMapColor(l,pos).id;
   var side=l.getBlockState(pos.below());walls[dx*16+dz]=side.isAir()?state.getMapColor(l,pos).id:side.getMapColor(l,pos.below()).id;
   tops[dx*16+dz]=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(state.getBlock());
   faces[dx*16+dz]=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(side.isAir()?state.getBlock():side.getBlock());
   int depth=0;for(int d=0;d<8&&!l.getBlockState(pos.below(d)).getFluidState().isEmpty();d++)depth=d+1;water[dx*16+dz]=depth;
   // AD-051: the cartographer writes down the whole column, not only its roof, so the map can show what stands under the trees.
   index[dx*16+dz]=runs.size();
   // AD-056: the column reaches the real ground under crowns and roofs and sixteen layers below it, block states included, so the map draws every block as it stands.
   int bottom=Math.max(l.getMinBuildHeight(),Math.max(y-MAX_COLUMN+1,floor)),last=-1,run=0;var cell=new ArrayList<int[]>();
   for(int cy=y;cy>=bottom;cy--){int block=net.minecraft.world.level.block.Block.getId(l.getBlockState(new BlockPos(x,cy,z)));
    if(block==last)run++;else{if(last>=0)cell.add(new int[]{last,run});last=block;run=1;}}
   if(last>=0)cell.add(new int[]{last,run});
   runs.add(cell.size());for(var r:cell){runs.add(r[0]);runs.add(r[1]);}}
  var entry=new CompoundTag();entry.putLong("pos",chunk.toLong());entry.putLong("tick",now);entry.putUUID("surveyor",cartographer);entry.putIntArray("heights",heights);entry.putIntArray("colors",colors);
  entry.putIntArray("walls",walls);entry.putIntArray("water",water);entry.putIntArray("tops",tops);entry.putIntArray("faces",faces);
  entry.putIntArray("index",index);entry.putIntArray("columns",runs.stream().mapToInt(Integer::intValue).toArray());entry.putInt("column",COLUMN);entry.putBoolean("states",true);
  t.getList("chunks",Tag.TAG_COMPOUND).add(entry);t.put("chunks",t.getList("chunks",Tag.TAG_COMPOUND));write(l,village,t);return true;
 }
 /** First block from the top that a person could stand on: crowns, trunks, plants and air are passed over. */
 private static int ground(ServerLevel l,int x,int top,int z){
  var cursor=new BlockPos.MutableBlockPos();
  for(int y=top;y>Math.max(l.getMinBuildHeight(),top-MAX_COLUMN);y--){var state=l.getBlockState(cursor.set(x,y,z));
   if(state.isAir()||state.is(net.minecraft.tags.BlockTags.LEAVES)||state.is(net.minecraft.tags.BlockTags.LOGS)||state.canBeReplaced()||state.getCollisionShape(l,cursor).isEmpty())continue;
   return y;}
  return top;
 }
 /** Tracks cartographers. Death of the last one clears the map and starts a new generation; a profession change or unloading does not. */
 public static boolean tick(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();var t=inspect(l,s.id());var known=new LinkedHashSet<UUID>();for(var raw:t.getList("cartographers",Tag.TAG_INT_ARRAY))known.add(NbtUtils.loadUUID(raw));
  var before=Set.copyOf(known);boolean death=false;
  for(var r:s.residents())if(r.alive()&&r.profession()==Profession.CARTOGRAPHER)known.add(r.id());
  for(var id:List.copyOf(known)){var r=s.resident(id);if(r==null||!r.alive()){known.remove(id);death=true;}else if(r.profession()!=Profession.CARTOGRAPHER)known.remove(id);}
  boolean reset=known.isEmpty()&&death&&!t.getList("chunks",Tag.TAG_COMPOUND).isEmpty();
  if(reset){t.putLong("generation",t.getLong("generation")+1);t.put("chunks",new ListTag());}
  if(!reset&&known.equals(before))return false;
  var list=new ListTag();known.forEach(id->list.add(NbtUtils.createUUID(id)));t.put("cartographers",list);write(l,s.id(),t);return reset;
 }
 /** One page of opened chunks plus live buildings, residents and hostiles inside opened chunks only. */
 public static CompoundTag view(ServerLevel l,SettlementData.Entry e,long generation,int from,int max){
  var t=inspect(l,e.settlement().id());var out=new CompoundTag();out.putUUID("village",e.settlement().id());out.putLong("generation",t.getLong("generation"));out.putLong("center",e.center().asLong());
  var chunks=t.getList("chunks",Tag.TAG_COMPOUND);out.putInt("total",chunks.size());out.putInt("area",area(l,e).size());
  if(generation!=t.getLong("generation"))from=0;out.putInt("from",from);var page=new ListTag();for(int i=from;i<Math.min(chunks.size(),from+max);i++)page.add(chunks.getCompound(i).copy());out.put("chunks",page);
  var open=surveyed(t);
  // AD-058: every opened building comes as its card, so the map is the settlement's building manager.
  var buildings=new ListTag();for(var b:e.settlement().buildings()){var p=e.center().offset(b.x(),b.y(),b.z());if(!open.contains(new ChunkPos(p).toLong()))continue;buildings.add(org.villageastra.server.BuildingCards.card(l,e,b));}
  out.put("buildings",buildings);
  // AD-057: the map knows how deep anything may be dug and whether the crews are free for a new order.
  out.putLong("epoch",e.settlement().governance().epoch());out.putInt("digFloor",MapOrders.floor(e));out.putInt("lowest",MapOrders.lowest(e));
  out.putBoolean("busyBuilders",Roads.active(l,e.settlement().id()));out.putBoolean("busyMiners",Excavation.pending(l,e.settlement().id()));
  out.putBoolean("miner",Excavation.miner(e)!=null);
  // AD-050: a resident on the map is a person — name, trade and what they are doing right now, so their movement can be followed.
  var people=new ListTag();
  for(var r:e.settlement().residents()){
   if(!r.alive()||!(l.getEntity(r.id()) instanceof ResidentEntity npc))continue;
   if(!open.contains(new ChunkPos(npc.blockPosition()).toLong()))continue;
   var person=new CompoundTag();person.putUUID("id",r.id());person.putLong("pos",npc.blockPosition().asLong());
   // AD-056: exact position and the face of the resident, so the map draws them where they really run and glides between updates.
   person.putDouble("x",npc.getX());person.putDouble("y",npc.getY());person.putDouble("z",npc.getZ());person.putInt("skin",npc.skinVariant());
   person.putString("name",npc.getName().getString());person.putString("status",npc.workStatus());
   person.putString("profession",r.profession()==null?"":r.profession().name().toLowerCase(java.util.Locale.ROOT));
   person.putBoolean("child",r.life()!=Resident.Life.ADULT);
   people.add(person);
  }
  out.put("residents",people);
  var enemies=new ListTag();if(!open.isEmpty()){var c=e.center();for(var m:l.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(c).inflate(16*(RADIUS_LIMIT+1),256,16*(RADIUS_LIMIT+1)),m->m instanceof Enemy&&m.isAlive()))if(open.contains(new ChunkPos(m.blockPosition()).toLong()))enemies.add(LongTag.valueOf(m.blockPosition().asLong()));}
  out.put("enemies",enemies);
  // AD-038: road cells inside opened chunks with tier, wear and real block light.
  var roadPos=new ArrayList<Long>();var roadMeta=new ArrayList<Integer>();for(var x:Roads.cells(l,e.settlement().id()))if(open.contains(new ChunkPos(x.getKey()).toLong())&&l.hasChunkAt(x.getKey())){roadPos.add(x.getKey().asLong());roadMeta.add(x.getValue().tier|x.getValue().wear<<4|(Roads.lit(l,x.getKey())?1<<12:0));}
  out.putLongArray("roads",roadPos.stream().mapToLong(Long::longValue).toArray());out.putIntArray("roadMeta",roadMeta.stream().mapToInt(Integer::intValue).toArray());
  int[] light=Roads.light(l,e.settlement().id());out.putInt("roadsLit",light[0]);out.putInt("roadsDark",light[1]);
  // AD-123: the neighbours a trail may go to and the state of the village's own trail.
  out.put("trails",Trails.view(l,e));
  // AD-125: which buildings may move now and the move under way.
  out.put("relocation",Relocations.view(l,e));return out;
 }
}
