package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-058/AD-059: the map grows from the hall outwards, roads take the width they are given, a quarry is a building that works out its own chunk,
 *  and dynamite is the one block allowed in and beside buildings. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SettlementToolsGameTests {
 private static SettlementData.Entry settlement(GameTestHelper h,BlockPos center){
  var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,h.getLevel().dimension().location().toString(),center);SettlementData.get(h.getLevel().getServer()).add(e);return e;
 }
 @GameTest(template="empty",timeoutTicks=100) public static void cartographerMapsInRingsFromTheHall(GameTestHelper h){
  var center=h.absolutePos(new BlockPos(8,3,8));var e=settlement(h,center);
  // A far building stretches the area; a cartographer standing out there still starts at the hall.
  e.settlement().addBuilding(new Settlement.Building(Settlement.childId(e.settlement().id(),"building/far"),"warehouse",64,0,0));
  var hall=new ChunkPos(center);var far=center.offset(64,0,0);
  var first=Atlas.next(h.getLevel(),e,far,Set.of());
  h.assertTrue(first!=null&&first.equals(hall),"The first chunk is the hall's own: "+first);
  var claimed=new HashSet<Long>();claimed.add(hall.toLong());
  for(int i=0;i<8;i++){var next=Atlas.next(h.getLevel(),e,far,claimed);
   h.assertTrue(next!=null&&Atlas.ring(hall,next)==1,"The eight chunks around the hall come next: "+next);claimed.add(next.toLong());}
  var ring2=Atlas.next(h.getLevel(),e,far,claimed);
  h.assertTrue(ring2!=null&&Atlas.ring(hall,ring2)==2,"Only then the second ring: "+ring2);
  SettlementData.get(h.getLevel().getServer()).remove(e.settlement().id());h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void roadsTakeTheWidthTheyAreGiven(GameTestHelper h){
  var a=new BlockPos(0,0,0);var b=new BlockPos(10,0,0);
  for(int width=1;width<=5;width++){
   var cells=MayorSurvey.road(a,b,0,width,pos->64);
   var across=new TreeSet<Integer>();for(var cell:cells.keySet())if(cell.getX()==5)across.add(cell.getZ());
   h.assertTrue(across.size()==width,"A road of width "+width+" is that wide across: "+across);
  }
  h.assertTrue(MayorSurvey.road(a,b,0,6,pos->64).isEmpty()&&MayorSurvey.road(a,b,0,0,pos->64).isEmpty(),"Widths outside one to five are refused");
  h.assertTrue(MayorSurvey.width(0)==3&&MayorSurvey.width(5<<6)==5&&MayorSurvey.width(1<<6)==1,"The width sits in the variant; an old variant keeps three");
  h.assertTrue(MapOrders.valid(5<<6|3<<2|1<<4|1<<5|2)&&!MapOrders.valid(6<<6),"A valid road variant carries route, surface, lamps, fence and width");
  h.succeed();
 }
 private static void openSky(net.minecraft.server.level.ServerLevel l,BlockPos from){
  int top=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,from.getX(),from.getZ());
  for(int y=from.getY();y<top;y++)l.setBlock(new BlockPos(from.getX(),y,from.getZ()),Blocks.AIR.defaultBlockState(),2);
 }
 @GameTest(template="empty",timeoutTicks=200) public static void roadsRunBetweenBuildingsButNeverOverOrUnderThem(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(14,3,14));var e=settlement(h,center);
  for(int x=-8;x<14;x++)for(int z=-8;z<14;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  // The strip beside the hall, where a player may not build, and a cell inside the hall's own footprint.
  var strip=center.offset(-2,0,3);var inside=center.offset(3,0,3);
  for(var cell:java.util.List.of(strip,inside))for(var side:java.util.List.of(cell,cell.north(),cell.south(),cell.east(),cell.west()))openSky(l,side.above());
  h.assertTrue(OwnershipEvents.disallowedPlacement(l,strip),"Nobody may build in the strip beside the hall");
  h.assertTrue(!MayorSurvey.blocked(l,strip,true),"A road may still be laid there");
  h.assertTrue(MayorSurvey.blocked(l,inside,true)&&MayorSurvey.underBuilding(l,inside),"A road never goes into the hall's footprint");
  h.assertTrue(MayorSurvey.blocked(l,strip,false),"A house may not stand in the strip");
  // A paved yard: every cell of the rectangle between the two corners.
  var yard=MayorSurvey.road(center.offset(-6,0,-6),center.offset(-3,0,3),MayorSurvey.AREA,0,pos->center.getY());
  h.assertTrue(yard.size()==4*10,"A yard fills its rectangle: "+yard.size());
  h.assertTrue(MayorSurvey.road(center,center.offset(40,0,0),MayorSurvey.AREA,0,pos->center.getY()).isEmpty(),"A yard wider than the limit is refused");
  h.assertTrue(!MapOrders.valid(MayorSurvey.AREA|1<<5)&&MapOrders.valid(MayorSurvey.AREA|1<<4|2<<2),"A yard takes lamps and a surface, never a fence");
  SettlementData.get(l.getServer()).remove(e.settlement().id());h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void quarryBuildingWorksOutItsOwnChunkAroundItself(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var e=settlement(h,center);var s=e.settlement();
  var quarry=new Settlement.Building(Settlement.childId(s.id(),"building/quarry"),Quarry.BUILDING,24,0,0);s.addBuilding(quarry);
  var chestPos=LogisticsRoutes.position(e,quarry);l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  var mine=new Settlement.Building(Settlement.childId(s.id(),"building/mine"),"mine",0,0,12);s.addBuilding(mine);
  Quarry.clear(l,s.id());
  var pit=new net.minecraft.world.level.ChunkPos(center.offset(24,0,0));int surface=Integer.MIN_VALUE;
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)surface=Math.max(surface,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,pit.getMinBlockX()+dx,pit.getMinBlockZ()+dz));
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)l.setBlock(new BlockPos(pit.getMinBlockX()+dx,surface,pit.getMinBlockZ()+dz),Blocks.STONE.defaultBlockState(),2);
  h.assertTrue(Quarry.building(e)!=null&&Quarry.open(l,e).isEmpty(),"A built quarry opens its chunk");
  var record=Quarry.record(l,s.id());var at=center.offset(24,0,0);
  h.assertTrue(record.getUUID("building").equals(quarry.id())&&record.getLong("chunk")==new ChunkPos(at).toLong(),"The quarry works the chunk it stands in");
  h.assertTrue(Quarry.open(l,e).isEmpty()&&Quarry.record(l,s.id()).getInt("taken")==0,"Opening again changes nothing");
  var chest=LogisticsRoutes.chest(l,e,quarry);int dug=0,expected=0;
  for(int i=0;i<30;i++){var next=Quarry.next(l,e);if(next==null)break;
   boolean spared=false;for(var b:s.buildings()){var base=center.offset(b.x(),b.y(),b.z());var d=BuildingBlueprints.design(b.type());
    if(next.getX()>=base.getX()-Quarry.BUFFER&&next.getX()<base.getX()+d.width()+Quarry.BUFFER&&next.getZ()>=base.getZ()-Quarry.BUFFER&&next.getZ()<base.getZ()+d.depth()+Quarry.BUFFER)spared=true;}
   h.assertTrue(!spared,"The quarry never digs into a building or its buffer: "+next.toShortString());
   for(var drop:net.minecraft.world.level.block.Block.getDrops(l.getBlockState(next),l,next,null,null,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE)))expected+=drop.getCount();
   var result=Quarry.dig(l,e,mine,next);h.assertTrue(result.isEmpty(),"The block is taken: "+result);dug++;}
  int stocked=0;for(int slot=0;slot<chest.getContainerSize();slot++)stocked+=chest.getItem(slot).getCount();
  h.assertTrue(dug>0&&Quarry.taken(l,s.id())==dug,"Blocks were really taken: "+dug);
  // The mine has no chest in this fixture: every successful dig proves the yield went to the quarry's own stock.
  h.assertTrue(LogisticsRoutes.chest(l,e,mine)==null&&(expected==0||stocked>0),"What a pick yields lands in the quarry's own stock: "+stocked+" (a pick roll gave "+expected+")");
  Quarry.clear(l,s.id());SettlementData.get(l.getServer()).remove(s.id());h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void dynamiteIsTheOneBlockAllowedAtBuildings(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var e=settlement(h,center);
  var pos=center.offset(2,1,-2);
  h.assertTrue(OwnershipEvents.disallowedPlacement(l,pos),"The spot beside the hall is protected");
  var player=FakePlayerFactory.getMinecraft(l);
  l.setBlock(pos,Blocks.COBBLESTONE.defaultBlockState(),3);
  boolean stone=ForgeEventFactory.onBlockPlace(player,BlockSnapshot.create(l.dimension(),l,pos),Direction.UP);
  l.setBlock(pos,Blocks.TNT.defaultBlockState(),3);
  boolean tnt=ForgeEventFactory.onBlockPlace(player,BlockSnapshot.create(l.dimension(),l,pos),Direction.UP);
  h.assertTrue(stone,"An ordinary block beside a building is refused");
  h.assertTrue(!tnt,"Dynamite beside a building is allowed");
  var broken=new net.minecraftforge.event.level.BlockEvent.BreakEvent(l,pos,l.getBlockState(pos),player);
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(broken);
  h.assertTrue(!broken.isCanceled(),"Placed dynamite may be taken back");
  l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
  SettlementData.get(l.getServer()).remove(e.settlement().id());h.succeed();
 }
}
