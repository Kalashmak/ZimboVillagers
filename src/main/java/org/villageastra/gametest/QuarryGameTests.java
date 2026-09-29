package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-053: a quarry takes real blocks out of one claimed chunk, layer by layer, and everything it takes lands in the mine stock. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class QuarryGameTests {
 private record Pit(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building mine,ChunkPos chunk){}
 private static Pit pit(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var mine=new Settlement.Building(Settlement.childId(s.id(),"building/mine"),"mine",6,0,0);s.addBuilding(mine);
  for(int x=-2;x<12;x++)for(int z=-2;z<12;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=0;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var chestPos=LogisticsRoutes.position(e,mine);l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);
  l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  // The claimed chunk is a bare field of stone inside this test's own area, with one ore in it.
  var chunk=new ChunkPos(center.offset(20,0,20));
  // Where the chunk borders fall depends on where the runner puts this plot: step out until the mine's buffer is behind.
  while(Quarry.refuses(l,e,chunk).equals("building"))chunk=new ChunkPos(chunk.x+1,chunk.z+1);
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){
   var top=new BlockPos(chunk.getMinBlockX()+dx,center.getY(),chunk.getMinBlockZ()+dz);
   l.setBlock(top,Blocks.STONE.defaultBlockState(),2);
   for(int y=1;y<4;y++)l.setBlock(top.above(y),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(new BlockPos(chunk.getMinBlockX()+3,center.getY(),chunk.getMinBlockZ()+3),Blocks.IRON_ORE.defaultBlockState(),2);
  // The quarry starts from the chunk's highest block: a known layer of stone lies over whatever sea or forest is above the test grid.
  int surface=Integer.MIN_VALUE;
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)surface=Math.max(surface,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,chunk.getMinBlockX()+dx,chunk.getMinBlockZ()+dz));
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)l.setBlock(new BlockPos(chunk.getMinBlockX()+dx,surface,chunk.getMinBlockZ()+dz),Blocks.STONE.defaultBlockState(),2);
  Quarry.clear(l,s.id());
  return new Pit(l,e,mine,chunk);
 }
 @GameTest(template="empty",timeoutTicks=300) public static void quarryWorksOutRealBlocksOfOneChunk(GameTestHelper h){
  var t=pit(h);
  h.assertTrue(Quarry.claim(t.l,t.e,new ChunkPos(t.e.center())).equals("building"),"The chunk of the hall itself is no quarry");
  h.assertTrue(Quarry.claim(t.l,t.e,t.chunk).isEmpty(),"A bare chunk of the settlement land is claimed");
  h.assertTrue(Quarry.claim(t.l,t.e,t.chunk.equals(new ChunkPos(0,0))?new ChunkPos(1,1):t.chunk).equals("claimed"),"Only one quarry is worked at a time");
  var chest=LogisticsRoutes.chest(t.l,t.e,t.mine);
  int dug=0;var seen=new HashSet<Long>();
  for(int i=0;i<40;i++){
   var next=Quarry.next(t.l,t.e);if(next==null)break;
   h.assertTrue(new ChunkPos(next).equals(t.chunk),"The quarry never digs outside its own chunk: "+next.toShortString());
   h.assertTrue(seen.add(next.asLong()),"The same block is never offered twice: "+next.toShortString());
   var before=t.l.getBlockState(next);
   String result=Quarry.dig(t.l,t.e,t.mine,next);
   h.assertTrue(result.isEmpty(),"The block is taken and stocked: "+result);
   h.assertTrue(t.l.getBlockState(next).isAir(),"The block really left the world at "+next.toShortString());
   h.assertTrue(Quarry.dig(t.l,t.e,t.mine,next).equals("not_diggable"),"An emptied cell is not dug again");
   if(before.is(Blocks.IRON_ORE))h.assertTrue(chest.countItem(Items.RAW_IRON)>0,"Ore yields its real drop");
   if(before.is(Blocks.STONE))h.assertTrue(chest.countItem(Items.COBBLESTONE)>0,"Stone worked with a pick yields cobblestone, not the block itself");
   dug++;
  }
  h.assertTrue(dug>=20,"The quarry really works: "+dug+" blocks");
  h.assertTrue(Quarry.taken(t.l,t.e.settlement().id())==dug,"Every taken block is counted: "+Quarry.taken(t.l,t.e.settlement().id()));
  int stocked=0;for(int slot=0;slot<chest.getContainerSize();slot++)stocked+=chest.getItem(slot).getCount();
  h.assertTrue(stocked>0,"Everything the quarry took landed in the mine stock: "+stocked);
  Quarry.clear(t.l,t.e.settlement().id());SettlementData.get(h.getLevel().getServer()).remove(t.e.settlement().id());h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void quarryRefusesProtectedAndUnsafeBlocks(GameTestHelper h){
  var t=pit(h);
  var claim=Quarry.claim(t.l,t.e,t.chunk);h.assertTrue(claim.isEmpty(),"The quarry is claimed: "+claim);
  var pos=new BlockPos(t.chunk.getMinBlockX()+8,t.e.center().getY(),t.chunk.getMinBlockZ()+8);
  t.l.setBlock(pos,Blocks.WATER.defaultBlockState(),3);
  h.assertTrue(!Quarry.diggable(t.l,pos),"Water is never quarried");
  t.l.setBlock(pos,Blocks.BEDROCK.defaultBlockState(),3);
  h.assertTrue(!Quarry.diggable(t.l,pos),"Bedrock is never quarried");
  t.l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  h.assertTrue(!Quarry.diggable(t.l,pos),"A container is never quarried");
  t.l.setBlock(pos,VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState(),3);
  h.assertTrue(!Quarry.diggable(t.l,pos),"Somebody else's scaffold is never quarried");
  h.assertTrue(Quarry.dig(t.l,t.e,t.mine,t.e.center().above()).equals("outside"),"Blocks outside the claim are refused");
  // A full stock stops the quarry before the block is taken: the stone stays in the world and nothing is lost.
  var chest=LogisticsRoutes.chest(t.l,t.e,t.mine);for(int slot=0;slot<chest.getContainerSize();slot++)chest.setItem(slot,new net.minecraft.world.item.ItemStack(Items.DIRT,64));
  var next=Quarry.next(t.l,t.e);h.assertTrue(next!=null,"There is still stone to quarry");var kept=t.l.getBlockState(next);
  h.assertTrue(Quarry.dig(t.l,t.e,t.mine,next).equals("stock_full")&&t.l.getBlockState(next).equals(kept)&&Quarry.taken(t.l,t.e.settlement().id())==0,"With no room the block stays where it is and is not counted");
  chest.setItem(0,net.minecraft.world.item.ItemStack.EMPTY);
  h.assertTrue(Quarry.dig(t.l,t.e,t.mine,next).isEmpty()&&t.l.getBlockState(next).isAir(),"With room again the same block is taken");
  Quarry.clear(t.l,t.e.settlement().id());SettlementData.get(h.getLevel().getServer()).remove(t.e.settlement().id());h.succeed();
 }
}
