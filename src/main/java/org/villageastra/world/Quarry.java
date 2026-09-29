package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-053: a quarry is one claimed chunk worked out layer by layer — every block the settlement gets from it really leaves the world. */
public final class Quarry {
 /** How deep a quarry goes under the surface of its own chunk, and how far it keeps away from buildings. */
 public static final int DEPTH=24,BUFFER=3;
 /** AD-058: a quarry is a building of its own; the chunk it stands in is worked out around it. */
 public static final String BUILDING="quarry";
 private Quarry(){}
 private static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-quarry/"+village+".bin");}
 public static CompoundTag record(ServerLevel l,UUID village){var p=path(l,village);return Files.exists(p)?NbtRecord.read(p):null;}
 private static void save(ServerLevel l,UUID village,CompoundTag t){NbtRecord.write(path(l,village),t);}
 public static void clear(ServerLevel l,UUID village){try{Files.deleteIfExists(path(l,village));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
 /** Why this chunk cannot be a quarry; empty when it can. */
 public static String refuses(ServerLevel l,SettlementData.Entry e,ChunkPos chunk){
  if(!l.hasChunk(chunk.x,chunk.z))return "unloaded";
  if(!Atlas.area(l,e).contains(chunk))return "outside";
  for(var b:e.settlement().buildings()){
   var at=e.center().offset(b.x(),b.y(),b.z());var design=BuildingBlueprints.design(b.type());
   var size=BuildingPlacement.size(b.type(),b.rotation());int w=size[0],d=size[1];
   if(new ChunkPos(at.offset(-BUFFER,0,-BUFFER)).x<=chunk.x&&chunk.x<=new ChunkPos(at.offset(w+BUFFER,0,d+BUFFER)).x
    &&new ChunkPos(at.offset(-BUFFER,0,-BUFFER)).z<=chunk.z&&chunk.z<=new ChunkPos(at.offset(w+BUFFER,0,d+BUFFER)).z)return "building";
  }
  for(var cell:Roads.cells(l,e.settlement().id()))if(new ChunkPos(cell.getKey()).equals(chunk))return "road";
  var existing=record(l,e.settlement().id());
  if(existing!=null&&!existing.getBoolean("worked"))return "claimed";
  return "";
 }
 /** Claims one chunk; the quarry starts from the highest block of that chunk and goes down. */
 public static String claim(ServerLevel l,SettlementData.Entry e,ChunkPos chunk){
  String reason=refuses(l,e,chunk);if(!reason.isEmpty())return reason;
  int top=Integer.MIN_VALUE;
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)
   top=Math.max(top,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,chunk.getMinBlockX()+dx,chunk.getMinBlockZ()+dz)-1);
  if(top==Integer.MIN_VALUE)return "unloaded";
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",Settlement.childId(e.settlement().id(),"quarry/"+chunk.toLong()));
  t.putLong("chunk",chunk.toLong());t.putInt("layer",top);t.putInt("taken",0);t.putBoolean("worked",false);
  // The floor is measured from this chunk's own surface, never from an absolute height: a quarry cannot fall out of the world.
  // AD-057: and never deeper than the settlement lets anybody dig — ten blocks under its lowest foundation.
  t.putInt("floor",Math.max(l.getMinBuildHeight()+1,Math.max(top-ResearchKnobs.quarryDepth(l,e),MapOrders.floor(e))));
  save(l,e.settlement().id(),t);return "";
 }
 /** The quarry building of the settlement, if one has been built. */
 public static Settlement.Building building(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.type().equals(BUILDING)).findFirst().orElse(null);}
 /** A built quarry claims the chunk it stands in, once: its own walls and every building's buffer are left standing. */
 public static String open(ServerLevel l,SettlementData.Entry e){
  var b=building(e);if(b==null)return "no_building";
  var t=record(l,e.settlement().id());
  if(t!=null&&t.hasUUID("building")&&t.getUUID("building").equals(b.id()))return "";
  if(t!=null&&!t.getBoolean("worked"))return "claimed";
  var chunk=new ChunkPos(e.center().offset(b.x(),b.y(),b.z()));
  if(!l.hasChunk(chunk.x,chunk.z))return "unloaded";
  int top=Integer.MIN_VALUE;
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)
   top=Math.max(top,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,chunk.getMinBlockX()+dx,chunk.getMinBlockZ()+dz)-1);
  var n=new CompoundTag();n.putInt("schema",1);n.putUUID("id",Settlement.childId(e.settlement().id(),"quarry/"+b.id()));n.putUUID("building",b.id());
  n.putLong("chunk",chunk.toLong());n.putInt("layer",top);n.putInt("taken",0);n.putBoolean("worked",false);
  n.putInt("floor",Math.max(l.getMinBuildHeight()+1,Math.max(top-ResearchKnobs.quarryDepth(l,e),MapOrders.floor(e))));
  save(l,e.settlement().id(),n);return "";
 }
 /** Cells a quarry never takes: inside or beside a building (its buffer) and registered road cells. */
 private static boolean spared(ServerLevel l,SettlementData.Entry e,BlockPos pos){
  for(var b:e.settlement().buildings()){
   var at=e.center().offset(b.x(),b.y(),b.z());var design=BuildingBlueprints.design(b.type());
   var size=BuildingPlacement.size(b.type(),b.rotation());int w=size[0],d=size[1];
   if(pos.getX()>=at.getX()-BUFFER&&pos.getX()<at.getX()+w+BUFFER&&pos.getZ()>=at.getZ()-BUFFER&&pos.getZ()<at.getZ()+d+BUFFER)return true;
  }
  return Roads.cell(l,pos)!=null;
 }
 /** The next block the quarry works out: the current layer is finished before the one below it starts. */
 public static BlockPos next(ServerLevel l,SettlementData.Entry e){
  var t=record(l,e.settlement().id());if(t==null||t.getBoolean("worked"))return null;
  var chunk=new ChunkPos(t.getLong("chunk"));if(!l.hasChunk(chunk.x,chunk.z))return null;
  int floor=t.contains("floor")?t.getInt("floor"):l.getMinBuildHeight()+1;
  for(int layer=t.getInt("layer");layer>=floor;layer--){
   for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){
    var pos=new BlockPos(chunk.getMinBlockX()+dx,layer,chunk.getMinBlockZ()+dz);
    if(diggable(l,pos)&&!(t.hasUUID("building")&&spared(l,e,pos)))return pos;
   }
   if(layer!=t.getInt("layer")){t.putInt("layer",layer);save(l,e.settlement().id(),t);}
  }
  t.putBoolean("worked",true);save(l,e.settlement().id(),t);return null;
 }
 /** Blocks a quarry may take: solid natural ground, never liquids, bedrock, block entities or anything the settlement protects. */
 public static boolean diggable(ServerLevel l,BlockPos pos){
  var state=l.getBlockState(pos);
  if(state.isAir()||!state.getFluidState().isEmpty())return false;
  if(state.is(Blocks.BEDROCK)||state.getBlock().defaultDestroyTime()<0)return false;
  if(l.getBlockEntity(pos)!=null)return false;
  if(state.is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get()))return false;
  // The settlement protects its land from strangers, not from its own quarry: the claim already keeps buildings and roads out.
  return true;
 }
 /** Takes one block out of the world exactly once and puts what it yields into the mine stock. */
 public static String dig(ServerLevel l,SettlementData.Entry e,Settlement.Building mine,BlockPos pos){
  var t=record(l,e.settlement().id());if(t==null||t.getBoolean("worked"))return "no_quarry";
  if(!new ChunkPos(pos).equals(new ChunkPos(t.getLong("chunk"))))return "outside";
  if(!diggable(l,pos)||t.hasUUID("building")&&spared(l,e,pos))return "not_diggable";
  // A quarry building keeps its own stock; a chunk claimed the old way fills the mine.
  var own=t.hasUUID("building")?e.settlement().buildings().stream().filter(b->b.id().equals(t.getUUID("building"))).findFirst().orElse(null):null;
  if(own!=null&&LogisticsRoutes.chest(l,e,own)!=null)mine=own;
  var chest=LogisticsRoutes.chest(l,e,mine);if(chest==null)return "no_chest";
  var state=l.getBlockState(pos);
  // The quarry works with a real pick: stone gives cobblestone, ore gives its raw drop, nothing gives the untouched block itself.
  var drops=net.minecraft.world.level.block.Block.getDrops(state,l,pos,null,null,new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
  var id=Settlement.childId(t.getUUID("id"),"block/"+pos.asLong());
  // A full stock stops the quarry before the block leaves the world: nothing dug is ever lost for want of room.
  if(!WorldJournal.exists(l,id)&&!LogisticsRoutes.fits(chest,drops))return "stock_full";
  if(!WorldJournal.place(l,id,pos,state,Blocks.AIR.defaultBlockState()))return "taken";
  var stock=LogisticsRoutes.position(e,mine);int kept=0;
  for(var drop:drops){
   if(drop.isEmpty())continue;
   if(WorldJournal.deposit(l,Settlement.childId(id,"drop/"+kept),stock,drop))kept++;
  }
  t.putInt("taken",t.getInt("taken")+1);save(l,e.settlement().id(),t);
  return drops.isEmpty()||kept>0?"":"stock_full";
 }
 /** Blocks already taken out of this quarry. */
 public static int taken(ServerLevel l,UUID village){var t=record(l,village);return t==null?0:t.getInt("taken");}
 public static boolean worked(ServerLevel l,UUID village){var t=record(l,village);return t!=null&&t.getBoolean("worked");}
}
