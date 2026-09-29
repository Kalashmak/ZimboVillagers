package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-068 (A07-VIS-004): a design turned by quarter turns is planned, built, protected, lived in and worked in turned — every system finds its cells. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BuildingRotationGameTests {
 private record Fixture(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos site){}
 private static Fixture fixture(GameTestHelper h){
  var l=h.getLevel();var site=h.absolutePos(new BlockPos(4,3,4));var center=h.absolutePos(new BlockPos(40,3,30));
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(int x=-1;x<16;x++)for(int z=-1;z<16;z++){
   for(int y=-3;y<0;y++)l.setBlock(site.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(site.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<=20;y++)l.setBlock(site.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
  }
  return new Fixture(l,s,e,site);
 }
 private static void done(Fixture f){
  SettlementData.get(f.l.getServer()).remove(f.s.id());
  try{java.nio.file.Files.deleteIfExists(f.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+f.s.id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 /** Builds the order with the builder's own journal operations, without walking; returns the registered building. */
 private static Settlement.Building build(GameTestHelper h,Fixture f,String design,int turns){
  var survey=BuildingOrders.survey(f.l,f.e,design,turns,f.site);
  h.assertTrue(survey.ok(),design+" turned "+turns+" is orderable: "+survey.reason()+" "+survey.conflicts());
  HallUpgradeGoal.enqueue(f.l,f.e,survey.state());var state=HallUpgradeGoal.inspect(f.l,f.s.id());
  var ops=state.getList("ops",Tag.TAG_COMPOUND);var id=state.getUUID("id");var origin=BlockPos.of(state.getLong("origin"));
  for(int i=0;i<ops.size();i++){
   var op=ops.getCompound(i);h.assertTrue(BuildingOrders.reconcile(f.l,op,origin),"No drift at operation "+i);
   var step=HallConstructionPlan.step(op);if(step.before().equals(step.after()))continue;
   h.assertTrue(WorldJournal.place(f.l,Settlement.childId(id,"block/"+i),step.pos(),step.before(),step.after()),"Operation "+i+" at "+step.pos());
  }
  h.assertTrue(BuildingOrders.complete(f.l,f.e,state),"The turned "+design+" matches its plan");
  var bid=BuildingOrders.buildingId(state);
  return f.s.buildings().stream().filter(b->b.id().equals(bid)).findFirst().orElseThrow();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void aTurnStaysInTheCornerBoxAndEveryCellComesBack(GameTestHelper h){
  int w=11,d=9;
  for(int turns=0;turns<4;turns++){var size=turns%2==0?new int[]{w,d}:new int[]{d,w};
   for(int x=0;x<w;x++)for(int z=0;z<d;z++){var p=BuildingPlacement.turn(x,2,z,w,d,turns);
    h.assertTrue(p.getX()>=0&&p.getX()<size[0]&&p.getZ()>=0&&p.getZ()<size[1]&&p.getY()==2,"Cell "+x+","+z+" turned "+turns+" leaves the box: "+p);
    h.assertTrue(BuildingPlacement.unturn(p,w,d,turns).equals(new BlockPos(x,2,z)),"Cell "+x+","+z+" turned "+turns+" comes back");}}
  var door=Blocks.OAK_DOOR.defaultBlockState();
  h.assertTrue(BuildingPlacement.state(door,1).getValue(DoorBlock.FACING)==net.minecraft.core.Direction.EAST&&BuildingPlacement.state(door,3).getValue(DoorBlock.FACING)==net.minecraft.core.Direction.WEST,"A north door faces east after a clockwise turn and west after three");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void aTurnedWorkshopIsBuiltTurnedProtectedAndFound(GameTestHelper h){
  var f=fixture(h);
  try{
   var b=build(h,f,"masonry",1);
   h.assertTrue(b.rotation()==1,"The building keeps its turn: "+b.rotation());
   int wrong=0;for(var cell:BuildingPlacement.layout("masonry",f.site,1).entrySet())if(!cell.getValue().isAir()&&f.l.getBlockState(cell.getKey()).getBlock()!=cell.getValue().getBlock())wrong++;
   h.assertTrue(wrong==0,"Every block of the turned design stands where the turn puts it: "+wrong+" wrong");
   var chest=LogisticsRoutes.position(f.e,b);
   h.assertTrue(f.l.getBlockState(chest).is(VillageAstra.OWNED_CHEST.get())&&LogisticsRoutes.chest(f.l,f.e,b)!=null,"The stock chest is found in the turned building at "+chest.subtract(f.site).toShortString());
   // An 11×9 design turned once stands 9 wide and 11 deep.
   var card=BuildingCards.card(f.l,f.e,b);h.assertTrue(card.getInt("w")==9&&card.getInt("d")==11,"The map outline is turned: "+card.getInt("w")+"×"+card.getInt("d"));
   var far=BuildingPlacement.layout("masonry",f.site,1).entrySet().stream().filter(c->!c.getValue().isAir()&&c.getKey().getZ()-f.site.getZ()>=9).map(Map.Entry::getKey).findFirst().orElseThrow();
   h.assertTrue(OwnershipEvents.protectedBlock(f.l,far),"A wall beyond the unturned depth is protected: "+far.subtract(f.site).toShortString());
   h.assertTrue(!OwnershipEvents.protectedBlock(f.l,f.site.offset(10,1,1)),"Where the unturned design would stand but the turned one does not, nothing is protected");
   h.assertTrue(MayorSurvey.underBuilding(f.l,f.site.offset(8,0,10))&&!MayorSurvey.underBuilding(f.l,f.site.offset(10,0,1)),"Roads keep off the turned footprint only");
   var loaded=SettlementData.load(SettlementData.get(f.l.getServer()).save(new CompoundTag())).entry(f.s.id());
   h.assertTrue(loaded.settlement().buildings().stream().anyMatch(x->x.id().equals(b.id())&&x.rotation()==1),"The turn survives save and load");
   h.assertTrue(BuildingRepairs.damage(f.l,f.e,b).isEmpty(),"A whole turned building needs no repair");
   var design=BuildingBlueprints.layout("masonry",BlockPos.ZERO).entrySet().stream().filter(c->c.getKey().getY()==2&&c.getValue().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO)).map(Map.Entry::getKey).findFirst().orElseThrow();
   var wall=BuildingPlacement.at(f.e,b,design.getX(),design.getY(),design.getZ());var kept=f.l.getBlockState(wall);f.l.setBlock(wall,Blocks.AIR.defaultBlockState(),3);
   h.assertTrue(BuildingRepairs.damage(f.l,f.e,b).contains(wall),"A broken wall of the turned building is found missing at its turned place");
   f.l.setBlock(wall,kept,3);
   h.assertTrue(BuildingOrders.survey(f.l,f.e,"masonry",4,f.site.offset(0,0,40)).reason().equals("rotation"),"Only quarter turns exist");
  }finally{done(f);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void aTurnedHomeIsLivedIn(GameTestHelper h){
  var f=fixture(h);
  try{
   var b=build(h,f,"home",2);
   h.assertTrue(f.s.homes().stream().anyMatch(x->x.id().equals(b.id())&&x.usable()),"The turned house is a home");
   h.assertTrue(BuildingIntegrity.home(f.l,f.site,"home",2)==BuildingIntegrity.Result.USABLE,"The turned house passes the housing check");
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);f.s.admit(r,b.id());
   var bed=SleepGoal.bed(f.l,f.e,f.s.resident(r.id()));
   h.assertTrue(bed!=null&&f.l.getBlockState(bed).getBlock() instanceof BedBlock&&f.l.getBlockState(bed).getValue(BedBlock.PART)==BedPart.HEAD,"The resident finds the head of a bed in the turned house: "+bed);
   var head=f.l.getBlockState(bed);f.l.setBlock(bed,Blocks.AIR.defaultBlockState(),3);
   h.assertTrue(BuildingIntegrity.home(f.l,f.site,"home",2)==BuildingIntegrity.Result.DAMAGED,"A broken bed makes the turned house unusable");
   f.l.setBlock(bed,head,3);
  }finally{done(f);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void everyOrderableDesignCanBeTurned(GameTestHelper h){
  var problems=new ArrayList<String>();
  for(var design:BuildingOrders.ORDERABLE.stream().sorted().toList())for(int turns=1;turns<4;turns+=2){
   var f=fixture(h);var survey=BuildingOrders.survey(f.l,f.e,design,turns,f.site);
   if(!survey.ok())problems.add(design+"/"+turns+":"+survey.reason()+" "+survey.conflicts().size());
   done(f);
  }
  h.assertTrue(problems.isEmpty(),"Turned designs plan: "+problems);h.succeed();
 }
}
