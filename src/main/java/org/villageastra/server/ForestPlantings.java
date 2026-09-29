package org.villageastra.server;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
/** AD-131: who planted a tree. A sapling a real player sets (PlantingEvents) and whatever the village code sets for show ("decor") is never a
 *  forester's to fell; what the foresters plant themselves is theirs, and counts for the mix of kinds a level-III hut keeps. Overworld storage
 *  (villageastra_plantings, schema 1), keyed by dimension and cell; a record whose cell holds neither a sapling nor a log any more is dropped the
 *  next time it is asked about. */
public final class ForestPlantings extends SavedData {
 public static final String NAME="villageastra_plantings";
 public record Planting(String dimension,long pos,UUID village,UUID building,String species,long tick){}
 private record Key(String dimension,long pos){}
 private final Set<Key> player=new LinkedHashSet<>(),decor=new LinkedHashSet<>();
 private final Map<Key,Planting> forester=new LinkedHashMap<>();
 public static ForestPlantings get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(ForestPlantings::load,ForestPlantings::new,NAME);}
 private static Key key(ServerLevel l,BlockPos p){return new Key(l.dimension().location().toString(),p.asLong());}
 /** Whether the cell still holds what a planting leaves there: a sapling, or the log of the tree it grew into. */
 private static boolean living(ServerLevel l,BlockPos p){if(!l.hasChunkAt(p))return true;BlockState s=l.getBlockState(p);return s.is(BlockTags.SAPLINGS)||s.is(BlockTags.LOGS)||s.is(net.minecraft.world.level.block.Blocks.MANGROVE_PROPAGULE);}
 private boolean lookup(Set<Key> set,ServerLevel l,BlockPos p){var k=key(l,p);if(!set.contains(k))return false;if(living(l,p))return true;set.remove(k);setDirty();return false;}
 public boolean isPlayer(ServerLevel l,BlockPos p){return lookup(player,l,p);}
 public boolean isDecor(ServerLevel l,BlockPos p){return lookup(decor,l,p);}
 public void recordPlayer(ServerLevel l,BlockPos p){if(player.add(key(l,p)))setDirty();}
 public void recordDecor(ServerLevel l,BlockPos p){if(decor.add(key(l,p)))setDirty();}
 public void recordForester(ServerLevel l,BlockPos p,UUID village,UUID building,String species,long tick){
  var k=key(l,p);player.remove(k);forester.put(k,new Planting(k.dimension(),k.pos(),village,building,species,tick));setDirty();}
 public Planting forester(ServerLevel l,BlockPos p){var k=key(l,p);var r=forester.get(k);if(r==null)return null;if(living(l,p))return r;forester.remove(k);setDirty();return null;}
 /** The kinds a hut's foresters have planted within radius of a centre (horizontal), with their counts. */
 public Map<String,Integer> planted(ServerLevel l,UUID building,BlockPos centre,int radius){
  var out=new TreeMap<String,Integer>();String dim=l.dimension().location().toString();long r2=(long)radius*radius;var gone=new ArrayList<Key>();
  for(var e:forester.entrySet()){var p=e.getValue();if(!p.dimension().equals(dim)||!p.building().equals(building))continue;var at=BlockPos.of(p.pos());
   long dx=at.getX()-centre.getX(),dz=at.getZ()-centre.getZ();if(dx*dx+dz*dz>r2)continue;
   if(!living(l,at)){gone.add(e.getKey());continue;}out.merge(p.species(),1,Integer::sum);}
  if(!gone.isEmpty()){gone.forEach(forester::remove);setDirty();}
  return out;
 }
 /** Forgets every record inside a box of one dimension (a test that lays out its own wood where another one stood). */
 public void forgetInside(ServerLevel l,BlockPos a,BlockPos b){String dim=l.dimension().location().toString();
  java.util.function.Predicate<Key> in=k->{if(!k.dimension().equals(dim))return false;var p=BlockPos.of(k.pos());return p.getX()>=Math.min(a.getX(),b.getX())&&p.getX()<=Math.max(a.getX(),b.getX())&&p.getY()>=Math.min(a.getY(),b.getY())&&p.getY()<=Math.max(a.getY(),b.getY())&&p.getZ()>=Math.min(a.getZ(),b.getZ())&&p.getZ()<=Math.max(a.getZ(),b.getZ());};
  if(player.removeIf(in)|decor.removeIf(in)|forester.keySet().removeIf(in))setDirty();}
 public static ForestPlantings load(CompoundTag tag){
  if(tag.getInt("schema")!=1)throw new IllegalArgumentException("Invalid plantings record");
  var d=new ForestPlantings();
  for(var raw:tag.getList("player",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;d.player.add(new Key(t.getString("dimension"),t.getLong("pos")));}
  for(var raw:tag.getList("decor",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;d.decor.add(new Key(t.getString("dimension"),t.getLong("pos")));}
  for(var raw:tag.getList("forester",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var k=new Key(t.getString("dimension"),t.getLong("pos"));
   d.forester.put(k,new Planting(k.dimension(),k.pos(),t.getUUID("village"),t.getUUID("building"),t.getString("species"),t.getLong("tick")));}
  return d;
 }
 @Override public CompoundTag save(CompoundTag tag){
  tag.putInt("schema",1);
  var p=new ListTag();for(var k:player)p.add(cell(k));tag.put("player",p);
  var d=new ListTag();for(var k:decor)d.add(cell(k));tag.put("decor",d);
  var f=new ListTag();for(var r:forester.values()){var t=cell(new Key(r.dimension(),r.pos()));t.putUUID("village",r.village());t.putUUID("building",r.building());t.putString("species",r.species());t.putLong("tick",r.tick());f.add(t);}
  tag.put("forester",f);return tag;
 }
 private static CompoundTag cell(Key k){var t=new CompoundTag();t.putString("dimension",k.dimension());t.putLong("pos",k.pos());return t;}
}
