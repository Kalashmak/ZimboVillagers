package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.server.SettlementData;
/** AD-063: once the world around a generated village has grown its trees, every tree standing on the village territory — the lots, their strips,
 *  the roads and their strips — is taken away whole: trunk, branches and the crown that no other trunk holds. */
public final class VillageClearing {
 /** How far a tree may reach out from the territory, how high a column is searched, and the most blocks one village clears. */
 static final int REACH=8,HEIGHT=40,LIMIT=20000;
 private VillageClearing(){}
 public static boolean tree(BlockState s){
  return s.is(BlockTags.LOGS)||s.is(BlockTags.LEAVES)||s.is(Blocks.VINE)||s.is(Blocks.COCOA)||s.is(Blocks.BEE_NEST)||s.is(Blocks.MANGROVE_ROOTS)
   ||s.is(Blocks.MUSHROOM_STEM)||s.is(Blocks.BROWN_MUSHROOM_BLOCK)||s.is(Blocks.RED_MUSHROOM_BLOCK);
 }
 private static boolean trunk(BlockState s){return s.is(BlockTags.LOGS)||s.is(Blocks.MUSHROOM_STEM)||s.is(Blocks.MANGROVE_ROOTS);}
 /** Clears the trees of one village; road columns are absolute positions. Returns how many blocks were taken away. */
 public static int clear(ServerLevel l,SettlementData.Entry e,long[] roads){
  var origin=e.center();var roadColumns=new ArrayList<BlockPos>();
  for(long raw:roads){var p=BlockPos.of(raw);roadColumns.add(new BlockPos(p.getX()-origin.getX(),0,p.getZ()-origin.getZ()));}
  var territory=NaturalVillage.territory(e.settlement(),roadColumns);
  // A building's own timber is logs too: nothing inside a lot is ever taken for a tree.
  var lots=new HashSet<BlockPos>();
  for(var b:e.settlement().buildings())lots.addAll(NaturalVillage.lot(e.settlement(),b));
  var removed=new int[]{0};var trunks=new ArrayDeque<BlockPos>();var seen=new HashSet<Long>();
  var cursor=new BlockPos.MutableBlockPos();
  for(var column:territory){
   if(lots.contains(column))continue;
   int x=origin.getX()+column.getX(),z=origin.getZ()+column.getZ();
   if(!l.hasChunkAt(cursor.set(x,0,z)))continue;
   int top=l.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
   for(int y=top;y>top-HEIGHT&&y>l.getMinBuildHeight();y--){
    var state=l.getBlockState(cursor.set(x,y,z));
    if(state.isAir()||state.is(Blocks.SNOW)){continue;}
    if(!tree(state)){if(state.getCollisionShape(l,cursor).isEmpty()||state.canBeReplaced())continue;break;}
    if(trunk(state)&&seen.add(cursor.asLong()))trunks.add(cursor.immutable());
    take(l,cursor.immutable(),removed);
   }
  }
  // Whole trees: the trunk goes on beyond the territory, and so does the crown it holds.
  var fallen=new ArrayList<BlockPos>(trunks);
  while(!trunks.isEmpty()&&removed[0]<LIMIT){
   var p=trunks.remove();
   for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
    var n=p.offset(dx,dy,dz);if(!seen.add(n.asLong()))continue;
    if(!near(n,origin,territory)||!l.hasChunkAt(n)||lots.contains(new BlockPos(n.getX()-origin.getX(),0,n.getZ()-origin.getZ())))continue;
    if(!trunk(l.getBlockState(n)))continue;
    take(l,n,removed);trunks.add(n);fallen.add(n);
   }
  }
  for(var log:fallen){
   for(int dx=-5;dx<=5&&removed[0]<LIMIT;dx++)for(int dy=-5;dy<=5;dy++)for(int dz=-5;dz<=5;dz++){
    var n=log.offset(dx,dy,dz);if(!l.hasChunkAt(n)||lots.contains(new BlockPos(n.getX()-origin.getX(),0,n.getZ()-origin.getZ())))continue;var state=l.getBlockState(n);
    if(!state.is(BlockTags.LEAVES)&&!state.is(Blocks.VINE)&&!state.is(Blocks.COCOA)&&!state.is(Blocks.BEE_NEST))continue;
    if(state.hasProperty(LeavesBlock.PERSISTENT)&&state.getValue(LeavesBlock.PERSISTENT))continue;
    if(held(l,n,origin,lots))continue;
    take(l,n,removed);
   }
  }
  // AD-104: over a farm's field nothing of a tree is left: a crown reaching over from a tree decorated after the village shades the wheat,
  // and its falling leaves would shake it loose there. Taken from the top down without touching the neighbours' shapes, so no crop falls
  // while the light below is still that of the shade.
  for(var b:e.settlement().buildings())if(b.type().equals("farm")){
   // AD-112: the land laid and the field kept free for the builders (level II; AD-130: from layout 6 the barn's whole footprint).
   var columns=new LinkedHashSet<BlockPos>(NaturalVillage.farmClearing(e.settlement(),b));columns.addAll(FarmField.localColumns(FarmField.modules(e,b)));
   for(var column:columns){
    var ground=BuildingPlacement.at(e,b,column.getX(),0,column.getZ());if(!l.hasChunkAt(ground))continue;
    int top=l.getHeight(Heightmap.Types.WORLD_SURFACE,ground.getX(),ground.getZ())-1;
    for(int y=top;y>ground.getY()+1;y--){var at=cursor.set(ground.getX(),y,ground.getZ());var state=l.getBlockState(at);
     if(tree(state)||state.is(Blocks.SNOW)){l.setBlock(at,Blocks.AIR.defaultBlockState(),2|16);removed[0]++;}}
   }
   // A tree of a chunk decorated after the village shook loose the wheat beside its new leaves: the world had no light yet, so the crop
   // fell without a trace. Until the farmer has reaped anything the field is still the generator's, so that wheat is sown again as laid;
   // after his first harvest a bare plot may be his, and he sows it from his own seed — no crop comes from nowhere.
   if(b.rotation()==0&&!reaped(l,b)){var base=e.center().offset(b.x(),b.y(),b.z());
    for(var cell:FarmField.layout(base,FarmField.modules(1),NaturalVillage.fieldSeed(e.settlement(),base)).entrySet()){var at=cell.getKey();
     if(cell.getValue().is(Blocks.WHEAT)&&l.hasChunkAt(at)&&l.getBlockState(at).isAir()&&l.getBlockState(at.below()).is(Blocks.FARMLAND))l.setBlock(at,cell.getValue(),2|16);}}
  }
  return removed[0];
 }
 /** Tree blocks still standing on the territory outside the lots, road columns absolute: zero right after the clearing. */
 public static int remaining(ServerLevel l,SettlementData.Entry e,long[] roads){return remaining(l,e,roads,b->true);}
 /** The same count over some of the buildings only: a building added after generation was never part of the cleared territory. */
 public static int remaining(ServerLevel l,SettlementData.Entry e,long[] roads,java.util.function.Predicate<org.villageastra.domain.Settlement.Building> generated){
  var origin=e.center();var roadColumns=new ArrayList<BlockPos>();
  for(long raw:roads){var p=BlockPos.of(raw);roadColumns.add(new BlockPos(p.getX()-origin.getX(),0,p.getZ()-origin.getZ()));}
  var lots=new HashSet<BlockPos>();
  for(var b:e.settlement().buildings())lots.addAll(NaturalVillage.lot(e.settlement(),b));
  var village=new org.villageastra.domain.Settlement(e.settlement().id());village.lotLayout(e.settlement().lotLayout());for(var b:e.settlement().buildings())if(generated.test(b)){village.addBuilding(b);if(e.settlement().westField(b.id()))village.turnFieldWest(b.id());}
  int count=0;var cursor=new BlockPos.MutableBlockPos();
  for(var column:NaturalVillage.territory(village,roadColumns)){
   if(lots.contains(column))continue;int x=origin.getX()+column.getX(),z=origin.getZ()+column.getZ();
   if(!l.hasChunkAt(cursor.set(x,0,z)))continue;int top=l.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
   for(int y=top;y>top-HEIGHT;y--){var st=l.getBlockState(cursor.set(x,y,z));if(tree(st)&&!st.is(BlockTags.SAPLINGS))count++;else if(!st.isAir()&&!st.getCollisionShape(l,cursor).isEmpty()&&!st.canBeReplaced())break;}
  }
  return count;
 }
 /** Whether this farm's farmer has reaped anything yet (his work record keeps its last harvest); an unreadable record counts as yes. */
 private static boolean reaped(ServerLevel l,org.villageastra.domain.Settlement.Building b){
  var file=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+b.id()+".bin");
  try{return java.nio.file.Files.exists(file)&&org.villageastra.persistence.NbtRecord.read(file).hasUUID("lastHarvest");}catch(RuntimeException ex){return true;}
 }
 /** AD-104: a tree block beside a crop is taken without touching the crop's shape: the crop may still stand in the tree's shade, and a crop
  *  asked whether it can stand before the light comes back would fall. */
 private static void take(ServerLevel l,BlockPos p,int[] removed){
  boolean crop=false;for(var d:net.minecraft.core.Direction.values())if(l.getBlockState(p.relative(d)).getBlock() instanceof net.minecraft.world.level.block.CropBlock)crop=true;
  l.setBlock(p,Blocks.AIR.defaultBlockState(),crop?2|16:2);removed[0]++;
 }
 /** A leaf still held by a trunk that stays. */
 private static boolean held(ServerLevel l,BlockPos leaf,BlockPos origin,Set<BlockPos> lots){
  for(int dx=-4;dx<=4;dx++)for(int dy=-4;dy<=4;dy++)for(int dz=-4;dz<=4;dz++){var n=leaf.offset(dx,dy,dz);
   if(lots.contains(new BlockPos(n.getX()-origin.getX(),0,n.getZ()-origin.getZ())))continue;
   if(l.hasChunkAt(n)&&trunk(l.getBlockState(n)))return true;}
  return false;
 }
 private static boolean near(BlockPos p,BlockPos origin,Set<BlockPos> territory){
  int x=p.getX()-origin.getX(),z=p.getZ()-origin.getZ();
  for(int dx=-REACH;dx<=REACH;dx+=REACH)for(int dz=-REACH;dz<=REACH;dz+=REACH)if(territory.contains(new BlockPos(x+dx,0,z+dz)))return true;
  return territory.contains(new BlockPos(x,0,z));
 }
}
