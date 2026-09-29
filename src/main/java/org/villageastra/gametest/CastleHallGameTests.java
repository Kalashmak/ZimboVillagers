package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-121 (docs/plans/hall-castle-spec.md): the castle hall's kit chests are the village's own; a castle village keeps its whole 37x37 lot
 *  from growth, a 7x7 hall only its design. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CastleHallGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void theCastleKitChestsAndLotAreTheVillages(GameTestHelper h){
  var shell=CastleArchitecture.shell(1,BlockPos.ZERO);var stock=new BlockPos(HallSite.CASTLE_STOCK_X,HallSite.CASTLE_STOCK_Y,HallSite.CASTLE_STOCK_Z);
  h.assertTrue(shell.get(stock)!=null&&shell.get(stock).is(VillageAstra.OWNED_CHEST.get()),"The castle's stock is an owned chest: "+shell.get(stock));
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",OrganicLots.CASTLE_X,0,OrganicLots.CASTLE_Z);s.addBuilding(hall);
  var e=new SettlementData.Entry(s,h.getLevel().dimension().location().toString(),BlockPos.ZERO);
  s.lotLayout(OrganicLots.BARN_LOTS);var small=GrowthPlots.reserved(e,hall);
  s.lotLayout(OrganicLots.CASTLE_LOTS);var castle=GrowthPlots.reserved(e,hall);
  h.assertTrue(castle.east()-castle.west()==OrganicLots.CASTLE-1+2*GrowthPlots.EXTENSION&&castle.south()-castle.north()==OrganicLots.CASTLE-1+2*GrowthPlots.EXTENSION,"A castle village keeps the whole castle lot: "+castle);
  h.assertTrue(small.east()-small.west()<castle.east()-castle.west(),"A 7x7 hall keeps only its design: "+small);
  h.assertTrue(HallSite.stock(e).equals(HallSite.castleStock(BlockPos.ZERO)),"And the village's stock is the castle's chest");
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=100) public static void theCastleHallDesignsAreTheCastlePlan(GameTestHelper h){
  var s=new Settlement(UUID.randomUUID());s.lotLayout(OrganicLots.BARN_LOTS);
  h.assertTrue(BuildingTiers.layoutId(s,"town_hall",2).equals("town_hall_2")&&BuildingTiers.layoutId(s,"home",2).equals("home@2"),"A 7x7 village keeps its hall ladder");
  s.lotLayout(OrganicLots.CASTLE_LOTS);
  for(int level=1;level<=6;level++){var id=BuildingTiers.layoutId(s,"town_hall",level);
   h.assertTrue(id.equals("castle@"+level),"Level "+level+" of a castle village's hall is the castle: "+id);
   var laid=BuildingBlueprints.layout(id,BlockPos.ZERO);var shell=CastleArchitecture.shell(level,BlockPos.ZERO);
   var plaque=BuildingSigns.slot(id,BlockPos.ZERO,shell);h.assertTrue(plaque!=null&&laid.get(plaque).is(net.minecraft.world.level.block.Blocks.OAK_WALL_SIGN),"Castle has its entrance plaque");
   var expected=new HashSet<>(shell.keySet());expected.add(plaque);
   h.assertTrue(laid.keySet().equals(expected),"castle@"+level+" lays exactly the CastlePlan cells plus its civic plaque: "+laid.size()+" / "+expected.size());}
  h.assertTrue(BuildingBlueprints.design("castle@4").width()==OrganicLots.CASTLE,"Its footprint is the castle lot");
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=200) public static void aCastleHallWorksAtTheLevelItsKitStands(GameTestHelper h){
  var l=h.getLevel();var center=new BlockPos(9000,l.getMinBuildHeight()+100,-7300);var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  var corner=center.offset(OrganicLots.CASTLE_X,0,OrganicLots.CASTLE_Z);
  for(int cx=(corner.getX()-4)>>4;cx<=(corner.getX()+OrganicLots.CASTLE+4)>>4;cx++)for(int cz=(corner.getZ()-4)>>4;cz<=(corner.getZ()+OrganicLots.CASTLE+4)>>4;cz++){l.setChunkForced(cx,cz,true);l.getChunk(cx,cz);chunks.add(new net.minecraft.world.level.ChunkPos(cx,cz));}
  var s=new Settlement(UUID.randomUUID());s.lotLayout(OrganicLots.CASTLE_LOTS);
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",OrganicLots.CASTLE_X,0,OrganicLots.CASTLE_Z);s.addBuilding(hall);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   for(int n=2;n<=3;n++)s.raiseBuildingLevel(hall.id(),n);var kept=s.buildings().stream().filter(b->b.id().equals(hall.id())).findFirst().orElseThrow();
   for(var cell:BuildingBlueprints.layout(BuildingTiers.layoutId(s,"town_hall",3),corner).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
   h.assertTrue(BuildingTiers.level(l,e,kept)==3,"The castle laid at III works at III: "+BuildingTiers.level(l,e,kept));
   h.assertTrue(l.getBlockEntity(HallSite.stock(e)) instanceof OwnedChestEntity,"Its stock is the kit's owned chest at "+HallSite.stock(e));
   var seal=BuildingTiers.coreCell(l,e,kept);var sealAt=BuildingPlacement.at(e,kept,seal.getX(),seal.getY(),seal.getZ());
   h.assertTrue(sealAt.equals(center.offset(0,1,0))&&Cores.isCoreOf(l.getBlockState(sealAt),"town_hall")&&Cores.grade(l.getBlockState(sealAt))==3,"Its core is the seal at the village centre, grade III: "+l.getBlockState(sealAt));
   var station=CastlePlan.kitAt(3).keySet().iterator().next();l.setBlock(corner.offset(station.x(),station.y(),station.z()),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(BuildingTiers.level(l,e,kept)==2,"Without a station of III it works at II: "+BuildingTiers.level(l,e,kept));
  }finally{SettlementData.get(l.getServer()).remove(s.id());BuildingLevels.forgetBest(s.id());for(var c:chunks)l.setChunkForced(c.x,c.z,false);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=200) public static void theCastleHallUpgradeIsSurveyedFromTheCastle(GameTestHelper h){
  var l=h.getLevel();var center=new BlockPos(9200,l.getMinBuildHeight()+100,-7300);var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  var corner=center.offset(OrganicLots.CASTLE_X,0,OrganicLots.CASTLE_Z);
  for(int cx=(corner.getX()-4)>>4;cx<=(corner.getX()+OrganicLots.CASTLE+4)>>4;cx++)for(int cz=(corner.getZ()-4)>>4;cz<=(corner.getZ()+OrganicLots.CASTLE+4)>>4;cz++){l.setChunkForced(cx,cz,true);l.getChunk(cx,cz);chunks.add(new net.minecraft.world.level.ChunkPos(cx,cz));}
  var s=new Settlement(UUID.randomUUID());s.lotLayout(OrganicLots.CASTLE_LOTS);
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",OrganicLots.CASTLE_X,0,OrganicLots.CASTLE_Z);s.addBuilding(hall);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   for(int x=-2;x<OrganicLots.CASTLE+2;x++)for(int z=-2;z<OrganicLots.CASTLE+2;z++)for(int y=0;y<40;y++)l.setBlock(corner.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
   for(var cell:BuildingBlueprints.layout(BuildingTiers.layoutId(s,"town_hall",1),corner).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
   h.assertTrue(HallSite.base(e).equals(corner),"The castle is laid from its corner");
   var plan=HallUpgradeGoal.preview(l,e);var ops=plan.getList("ops",net.minecraft.nbt.Tag.TAG_COMPOUND);
   h.assertTrue(plan.getInt("level")==2&&!ops.isEmpty(),"Level II of the castle is surveyed: "+ops.size()+" operations");
   boolean inside=true;for(var raw:ops){var p=BlockPos.of(((net.minecraft.nbt.CompoundTag)raw).getLong("pos"));int x=p.getX()-corner.getX(),z=p.getZ()-corner.getZ();if(x<0||z<0||x>=OrganicLots.CASTLE||z>=OrganicLots.CASTLE)inside=false;}
   h.assertTrue(inside,"Every operation lies on the castle lot");
   h.assertTrue(plan.getCompound("cost").getAllKeys().stream().anyMatch(k->k.contains("core")),"The seal comes at II: "+plan.getCompound("cost").getAllKeys());
  }finally{SettlementData.get(l.getServer()).remove(s.id());HallUpgradeGoal.drop(l,s.id());BuildingLevels.forgetBest(s.id());for(var c:chunks)l.setChunkForced(c.x,c.z,false);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=200) public static void theCastlePortcullisComesDownInDanger(GameTestHelper h){
  var l=h.getLevel();var center=new BlockPos(9400,l.getMinBuildHeight()+100,-7300);var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  var corner=center.offset(OrganicLots.CASTLE_X,0,OrganicLots.CASTLE_Z);
  for(int cx=(corner.getX()-4)>>4;cx<=(corner.getX()+OrganicLots.CASTLE+4)>>4;cx++)for(int cz=(corner.getZ()-4)>>4;cz<=(corner.getZ()+OrganicLots.CASTLE+4)>>4;cz++){l.setChunkForced(cx,cz,true);l.getChunk(cx,cz);chunks.add(new net.minecraft.world.level.ChunkPos(cx,cz));}
  var s=new Settlement(UUID.randomUUID());s.lotLayout(OrganicLots.CASTLE_LOTS);
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",OrganicLots.CASTLE_X,0,OrganicLots.CASTLE_Z);s.addBuilding(hall);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   for(int n=2;n<=5;n++)s.raiseBuildingLevel(hall.id(),n);
   for(var cell:BuildingBlueprints.layout(BuildingTiers.layoutId(s,"town_hall",5),corner).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
   var kept=s.buildings().stream().filter(b->b.id().equals(hall.id())).findFirst().orElseThrow();
   h.assertTrue(BuildingTiers.level(l,e,kept)==5,"The castle works at V: "+BuildingTiers.level(l,e,kept));
   var passage=corner.offset(18,1,CastleGate.GRATE_Z);
   h.assertTrue(!CastleGate.tick(l,e)&&l.getBlockState(passage).isAir(),"In peace the passage is open");
   h.assertTrue(CastleGate.set(l,corner,true)&&l.getBlockState(passage).is(net.minecraft.world.level.block.Blocks.IRON_BARS)&&l.getBlockState(passage.above()).is(net.minecraft.world.level.block.Blocks.IRON_BARS),"In danger the portcullis comes down across the passage");
   CastleGate.tick(l,e);h.assertTrue(l.getBlockState(passage).isAir(),"And goes up again when the danger is over");
  }finally{SettlementData.get(l.getServer()).remove(s.id());BuildingLevels.forgetBest(s.id());for(var c:chunks)l.setChunkForced(c.x,c.z,false);}
  h.succeed();
 }
}
