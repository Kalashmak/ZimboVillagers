package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-037: the shared map opens chunk by chunk for real paper and is lost only with the last cartographer. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class AtlasGameTests {
 private record Office(net.minecraft.server.level.ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building office,Settlement.Home home){}
 private static Office office(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  var office=new Settlement.Building(Settlement.childId(s.id(),"building/cartographer"),"cartographer",0,0,0);s.addBuilding(office);
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/farm"),"farm",0,0,-6));
  for(int x=-4;x<12;x++)for(int z=-8;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,6,true);s.addHome(home);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Office(l,s,e,office,home);
 }
 private static UUID cartographer(Office o){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);o.s.admit(r,o.home.id());o.s.assign(r.id(),Profession.CARTOGRAPHER,o.office.id());return r.id();}
 @GameTest(template="empty",timeoutTicks=100) public static void surveySpendsOnePaperPerChunkAndRecordsRealHeights(GameTestHelper h){
  var o=office(h);var chest=LogisticsRoutes.chest(o.l,o.e,o.office);chest.setItem(0,new ItemStack(Items.PAPER,2));var home=new ChunkPos(o.e.center());var next=new ChunkPos(home.x+1,home.z);var third=new ChunkPos(home.x,home.z+1);
  for(var c:List.of(home,next,third))o.l.getChunk(c.x,c.z);
  var area=Atlas.area(o.e);h.assertTrue(area.contains(new ChunkPos(home.x+Atlas.MARGIN,home.z-Atlas.MARGIN))&&!area.contains(new ChunkPos(home.x+Atlas.MARGIN+2,home.z)),"Area is the settlement plus two chunks");
  var id=UUID.randomUUID();h.assertTrue(Atlas.survey(o.l,o.e,o.office,id,home,100)&&chest.countItem(Items.PAPER)==1,"One paper opens one chunk");
  h.assertTrue(Atlas.survey(o.l,o.e,o.office,id,home,200)&&chest.countItem(Items.PAPER)==1,"An opened chunk is never paid twice");
  var t=Atlas.inspect(o.l,o.s.id());var c=t.getList("chunks",10).getCompound(0);int dx=o.e.center().getX()-home.getMinBlockX(),dz=o.e.center().getZ()-home.getMinBlockZ();
  h.assertTrue(c.getIntArray("heights")[dx*16+dz]==o.l.getHeight(Heightmap.Types.WORLD_SURFACE,o.e.center().getX(),o.e.center().getZ())-1,"Recorded height is the real surface");
  h.assertTrue(!Atlas.survey(o.l,o.e,o.office,id,new ChunkPos(home.x+Atlas.MARGIN+3,home.z),300),"Chunks outside the area are not surveyed");
  h.assertTrue(Atlas.survey(o.l,o.e,o.office,id,next,400)&&chest.countItem(Items.PAPER)==0&&!Atlas.survey(o.l,o.e,o.office,id,third,500),"No paper, no new survey");
  h.assertTrue(Atlas.surveyed(Atlas.inspect(o.l,o.s.id())).size()==2,"Opened map is kept without paper");
  h.assertTrue(Atlas.view(o.l,o.e,0,0,16).getList("chunks",10).size()==2,"View pages only opened chunks");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void onlyTheLastCartographersDeathDestroysTheMap(GameTestHelper h){
  var o=office(h);var chest=LogisticsRoutes.chest(o.l,o.e,o.office);chest.setItem(0,new ItemStack(Items.PAPER,4));var home=new ChunkPos(o.e.center());o.l.getChunk(home.x,home.z);
  var first=cartographer(o);var second=cartographer(o);Atlas.tick(o.l,o.e);h.assertTrue(Atlas.survey(o.l,o.e,o.office,first,home,100),"Surveyed");
  o.s.resident(first).die();h.assertTrue(!Atlas.tick(o.l,o.e)&&Atlas.surveyed(Atlas.inspect(o.l,o.s.id())).size()==1,"Death of one of two cartographers keeps shared progress");
  var farm=o.s.buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();o.s.assign(second,Profession.FARMER,farm.id());
  h.assertTrue(!Atlas.tick(o.l,o.e)&&Atlas.surveyed(Atlas.inspect(o.l,o.s.id())).size()==1,"A profession change is not a death and keeps the map");
  // OWNER_REQUEST 9.14: projects ordered from the map are the settlement's, not the map's — a lost map leaves them standing.
  var mound=o.e.center().offset(6,1,6);o.l.setBlock(mound,Blocks.DIRT.defaultBlockState(),2);
  h.assertTrue(Roads.order(o.l,o.e,Roads.clearing(o.l,o.e,List.of(mound)))&&Roads.active(o.l,o.s.id()),"A clearing is ordered while the map is open");
  var third=cartographer(o);Atlas.tick(o.l,o.e);o.s.resident(third).die();
  h.assertTrue(Atlas.tick(o.l,o.e)&&Atlas.surveyed(Atlas.inspect(o.l,o.s.id())).isEmpty()&&Atlas.inspect(o.l,o.s.id()).getLong("generation")==1,"Death of the last cartographer clears the map and starts a new generation");
  h.assertTrue(Roads.active(o.l,o.s.id())&&Roads.project(o.l,o.s.id()).getList("ops",10).size()==1,"The ordered project outlives the map");
  h.assertTrue(Atlas.survey(o.l,o.e,o.office,third,home,200)&&chest.countItem(Items.PAPER)==2,"The next generation surveys again for new paper");
  h.succeed();
 }
}
