package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-135 (owner 2026-09-23): an annex is extra equipment beside a building — no room kept for it, built only while its site is free, the
 *  site named in advance by the building type, the annex in the building's style, keeping no level and suiting every level of its building. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class AnnexGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building hut){}
 private static final Annexes.Kind CARPENTRY=Annexes.kind("carpentry_annex"),MASONRY=Annexes.kind("masonry_annex"),MILL=Annexes.kind("mill_annex"),KENNEL=Annexes.kind("kennel_annex");
 /** A hall and a forester's hut of this level and turn on flat stone, the hut 20 blocks east of the hall (its annex clear of the hall's buffer). */
 private static Town town(GameTestHelper h,int level,int rotation){return town(h,ForesterHut.TYPE,level,rotation);}
 /** The same with any parent type standing where the hut would. */
 private static Town town(GameTestHelper h,String parent,int level,int rotation){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var hut=new Settlement.Building(Settlement.childId(s.id(),"building/"+parent),parent,20,0,8,rotation,level);s.addBuilding(hut);
  for(int x=-4;x<48;x++)for(int z=-4;z<40;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<14;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,s,e,hut);
 }
 private static void learn(Town t,String... nodes){var record=BookResearch.inspect(t.l,t.e);var done=record.getList("legacyDone",Tag.TAG_STRING);for(var n:nodes)done.add(StringTag.valueOf(n));record.put("legacyDone",done);BookResearch.store(t.l,t.e,record);ResearchKnobs.forget(t.s.id());}
 private static void done(Town t){
  SettlementData.get(t.l.getServer()).remove(t.s.id());
  try{java.nio.file.Files.deleteIfExists(t.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+t.s.id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 private static String state(Town t){return Annexes.view(t.l,t.e,t.hut).getCompound(0).getString("state");}
 private static String state(Town t,Annexes.Kind k){for(var raw:Annexes.view(t.l,t.e,t.hut))if(((CompoundTag)raw).getString("type").equals(k.type()))return ((CompoundTag)raw).getString("state");return "none";}
 /** Runs a queued project's operations the way the builder places them, then completes it. */
 private static boolean execute(Town t,CompoundTag state){
  for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var step=HallConstructionPlan.step((CompoundTag)raw);if(!step.before().equals(step.after()))t.l.setBlock(step.pos(),step.after(),2);}
  return BuildingOrders.complete(t.l,t.e,state);
 }
 @GameTest(template="empty",timeoutTicks=100) public static void annexSitesLieBesideTheirParents(GameTestHelper h){
  h.assertTrue(!Annexes.kinds().isEmpty()&&AnnexTypes.types().stream().allMatch(type->Annexes.kind(type)!=null),"Every annex type names its site");
  for(var k:Annexes.kinds()){
   h.assertTrue(BuildingBlueprints.design(k.type())!=null&&BuildingBlueprints.design(k.parent())!=null,k.type()+": its design and its parent's are in the catalogue");
   h.assertTrue(Annexes.besideLot(k),k.type()+": its site lies outside the parent's lot and touches it");
   h.assertTrue(!BuildingOrders.ORDERABLE.contains(k.type()),k.type()+": ordered only beside its building, never on its own");
   h.assertTrue(!BuildingTiers.upgradable(k.type())&&CoreCatalog.coreId(k.type())==null,k.type()+": keeps no level and no core");
   var chest=BuildingBlueprints.layout(k.type(),BlockPos.ZERO).get(new BlockPos(1,1,4));
   h.assertTrue(chest!=null&&chest.is(VillageAstra.OWNED_CHEST.get()),k.type()+": its workshop station, the stock chest, stands at (1,1,4)");
   if(AnnexTypes.workshop(k.type()))h.assertTrue(Workshops.spec(k.type())!=null&&Workshops.spec(k.type())==Workshops.spec(AnnexTypes.workplace(k.type())),k.type()+": works as "+AnnexTypes.workplace(k.type()));
   else h.assertTrue(Workshops.spec(k.type())==null&&Population.role(k.type())==null,k.type()+": equipment, no workshop and no trade");}
  h.succeed();
 }
 /** Every turn of the parent: each annex cell found through the annex is the cell its site names beside the parent, and the annex stays off the parent's lot. */
 @GameTest(template="empty",timeoutTicks=100) public static void anAnnexTurnsWithItsParent(GameTestHelper h){
  var center=new BlockPos(1000,64,1000);var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,"minecraft:overworld",center);
  for(var k:Annexes.kinds())for(int turn=0;turn<4;turn++){
   var parent=new Settlement.Building(UUID.randomUUID(),k.parent(),30,0,-12,turn,k.parentLevel());
   var o=Annexes.origin(e,parent,k).subtract(center);var annex=new Settlement.Building(UUID.randomUUID(),k.type(),o.getX(),o.getY(),o.getZ(),turn);
   var lot=new HashSet<BlockPos>();var size=BuildingPlacement.size(k.parent(),turn);var po=BuildingPlacement.origin(e,parent);
   for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++)lot.add(po.offset(x,0,z));
   for(int x=0;x<k.width();x++)for(int z=0;z<k.depth();z++){
    var mine=BuildingPlacement.at(e,annex,x,0,z);var site=BuildingPlacement.at(e,parent,k.site().getX()+x,k.site().getY(),k.site().getZ()+z);
    h.assertTrue(mine.equals(site),k.type()+" turn "+turn+": cell "+x+","+z+" at "+mine+", its site says "+site);
    h.assertTrue(!lot.contains(mine.atY(po.getY())),k.type()+" turn "+turn+": the annex stays off the parent's lot");}}
  h.succeed();
 }
 /** The annex keeps no level, so the side of the parent it leans on must be the same closed wall at every level it may be built beside. */
 @GameTest(template="empty",timeoutTicks=100) public static void anAnnexLeansOnTheSameWallAtEveryLevel(GameTestHelper h){
  for(var k:Annexes.kinds()){
   int x0=k.site().getX(),z0=k.site().getZ(),x1=x0+k.width()-1,z1=z0+k.depth()-1;var design=BuildingBlueprints.design(k.parent());
   // The parent's cells right beside the annex: its column on the side the annex touches.
   var faced=new ArrayList<int[]>();
   for(int z=Math.max(0,z0);z<=Math.min(design.depth()-1,z1);z++){if(x1==-1)faced.add(new int[]{0,z});if(x0==design.width())faced.add(new int[]{design.width()-1,z});}
   for(int x=Math.max(0,x0);x<=Math.min(design.width()-1,x1);x++){if(z1==-1)faced.add(new int[]{x,0});if(z0==design.depth())faced.add(new int[]{x,design.depth()-1});}
   h.assertTrue(!faced.isEmpty(),k.type()+": the annex touches a side of its parent");
   for(int level=k.parentLevel();level<=BuildingTiers.MAX;level++){var layout=BuildingBlueprints.layout(BuildingTiers.layoutId(k.parent(),level),BlockPos.ZERO);
    for(var c:faced)for(int y=1;y<=3;y++){var st=layout.getOrDefault(new BlockPos(c[0],y,c[1]),Blocks.AIR.defaultBlockState());
     h.assertTrue(!st.isAir()&&!(st.getBlock() instanceof DoorBlock),k.type()+": the parent's side it leans on is closed at level "+level+" ("+c[0]+","+y+","+c[1]+" is "+st+")");}}
   // The annex's own cells never climb over the parent's lot edge (its roof stays on its own lot).
   for(var cell:BuildingBlueprints.layout(k.type(),BlockPos.ZERO).keySet())h.assertTrue(cell.getX()>=0&&cell.getX()<k.width()&&cell.getZ()>=0&&cell.getZ()<k.depth(),k.type()+": every cell on its own lot");}
  h.succeed();
 }
 private static void orderedBuiltAndWorking(GameTestHelper h,Annexes.Kind k,int rotation){
  var first=town(h,k.parent(),k.parentLevel()-1,rotation);var t=first;
  try{
   h.assertTrue(state(t,k).equals("parent_level")&&Annexes.order(t.l,t.e,t.hut,k).equals("parent_level"),"A "+k.parent()+" below level "+k.parentLevel()+" takes no "+k.type());
   first.s.raiseBuildingLevel(first.hut.id(),k.parentLevel());t=new Town(first.l,first.s,first.e,first.s.buildings().stream().filter(b->b.id().equals(first.hut.id())).findFirst().orElseThrow());
   if(ResearchCatalog.NODES.containsKey(k.research())){h.assertTrue(state(t,k).equals("research"),"Without its research the annex waits: "+state(t,k));learn(t,k.research());}
   h.assertTrue(state(t,k).equals("ready"),"Level reached, research done, site free: ready ("+state(t,k)+")");
   h.assertTrue(Annexes.order(t.l,t.e,t.hut,k).isEmpty(),"The mayor orders it");
   h.assertTrue(Annexes.queued(t.l,t.e,t.hut,k)&&state(t,k).equals("queued"),"The crew has it queued");
   var project=HallUpgradeGoal.inspect(t.l,t.s.id());
   h.assertTrue(project.getUUID("annexOf").equals(t.hut.id())&&project.getString("design").equals(k.type()),"The project is the annex of this building");
   h.assertTrue(execute(t,project),"Built as surveyed, the project completes");
   var annex=t.s.buildings().stream().filter(b->b.type().equals(k.type())).findFirst().orElse(null);
   h.assertTrue(annex!=null&&t.hut.id().equals(t.s.annexParent(annex.id()))&&t.s.annexes(t.hut.id()).size()==1,"The annex is a building of the village, linked to its building");
   h.assertTrue(annex.rotation()==t.hut.rotation()&&annex.level()==1,"It turns with its building and keeps no level");
   h.assertTrue(t.l.getBlockEntity(LogisticsRoutes.position(t.e,annex))!=null,"Its stock chest stands at its station");
   h.assertTrue(state(t,k).equals("built")&&Annexes.order(t.l,t.e,t.hut,k).equals("exists"),"Built once only");
   h.assertTrue(Relocations.quick(t.l,t.e,annex,false,false).equals("annex"),"An annex does not move away from its building");
   if(!AnnexTypes.workshop(k.type()))h.assertTrue(BuildingCards.slots(t.s,annex)==0&&Population.slots(t.s,annex)==0,"Equipment: nobody's place");
   else{h.assertTrue(BuildingCards.slots(t.s,annex)==1&&Population.slots(t.s,annex)==1,"One worker's place");
   var trade=Arrays.stream(Profession.values()).filter(p->p.workplace().equals(AnnexTypes.workplace(k.type()))).findFirst().orElseThrow();
   var worker=new Resident(UUID.randomUUID(),Resident.Life.ADULT,trade.educationRequired(),null,null,-1);var room=new Settlement.Home(UUID.randomUUID(),1,2,true);t.s.addHome(room);t.s.admit(worker,room.id());
   t.s.assign(worker.id(),trade,annex.id());
   h.assertTrue(t.s.workplace(worker.id())!=null&&t.s.workplace(worker.id()).id().equals(annex.id()),"A "+trade.id()+" works in the annex");}
   var loaded=SettlementData.load(SettlementData.get(t.l.getServer()).save(new CompoundTag())).entry(t.s.id());
   h.assertTrue(loaded!=null&&t.hut.id().equals(loaded.settlement().annexParent(annex.id())),"The link survives save and load");
  }finally{done(t);}
  h.succeed();
 }
 private static void orderedBuiltAndWorking(GameTestHelper h,int rotation){orderedBuiltAndWorking(h,CARPENTRY,rotation);}
 @GameTest(template="empty",timeoutTicks=100) public static void aMineTakesItsStoneworks(GameTestHelper h){orderedBuiltAndWorking(h,MASONRY,0);}
 @GameTest(template="empty",timeoutTicks=100) public static void aTurnedMineTakesItsStoneworksTurned(GameTestHelper h){orderedBuiltAndWorking(h,MASONRY,3);}
 @GameTest(template="empty",timeoutTicks=100) public static void aRestaurantTakesItsMill(GameTestHelper h){orderedBuiltAndWorking(h,MILL,0);}
 @GameTest(template="empty",timeoutTicks=100) public static void aYardTakesItsKennel(GameTestHelper h){orderedBuiltAndWorking(h,KENNEL,0);}
 @GameTest(template="empty",timeoutTicks=100) public static void aTurnedYardTakesItsKennelTurned(GameTestHelper h){orderedBuiltAndWorking(h,KENNEL,2);}
 @GameTest(template="empty",timeoutTicks=100) public static void aTurnedRestaurantTakesItsMillTurned(GameTestHelper h){orderedBuiltAndWorking(h,MILL,1);}
 /** The miller in the mill beside the restaurant and the cook in the restaurant: the village bakes its bread the cheap way, not by hand. */
 @GameTest(template="empty",timeoutTicks=100) public static void theMillBesideTheRestaurantStaffsTheBreadChain(GameTestHelper h){
  var t=town(h,MILL.parent(),MILL.parentLevel(),0);
  try{
   var o=Annexes.origin(t.e,t.hut,MILL).subtract(t.e.center());var mill=new Settlement.Building(Settlement.childId(t.s.id(),"building/mill_annex"),MILL.type(),o.getX(),o.getY(),o.getZ(),t.hut.rotation());
   t.s.addBuilding(mill);t.s.linkAnnex(mill.id(),t.hut.id());
   for(var trade:List.of(Profession.MILLER,Profession.BAKER)){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,trade.educationRequired(),null,null,-1);var room=new Settlement.Home(UUID.randomUUID(),1,2,true);t.s.addHome(room);t.s.admit(r,room.id());
    t.s.assign(r.id(),trade,trade==Profession.MILLER?mill.id():t.hut.id());}
   h.assertTrue(HandBread.chainStaffed(t.l,t.e),"A miller in the mill shed and a cook in the restaurant: the bread chain works");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void anAnnexIsOrderedBuiltAndWorked(GameTestHelper h){orderedBuiltAndWorking(h,0);}
 @GameTest(template="empty",timeoutTicks=100) public static void aTurnedHutTakesItsAnnexTurned(GameTestHelper h){orderedBuiltAndWorking(h,1);}
 @GameTest(template="empty",timeoutTicks=100) public static void aHutTurnedTwiceTakesItsAnnex(GameTestHelper h){orderedBuiltAndWorking(h,2);}
 @GameTest(template="empty",timeoutTicks=100) public static void aHutTurnedThriceTakesItsAnnex(GameTestHelper h){orderedBuiltAndWorking(h,3);}
 /** No room is kept: another building beside the hut, or a chest on the site, and the annex cannot be ordered — nothing is queued. */
 @GameTest(template="empty",timeoutTicks=100) public static void aTakenSiteRefusesTheAnnex(GameTestHelper h){
  var t=town(h,3,0);
  try{
   learn(t,CARPENTRY.research());
   var site=Annexes.origin(t.e,t.hut,CARPENTRY);
   t.l.setBlock(site.offset(2,1,2),Blocks.CHEST.defaultBlockState(),2);
   h.assertTrue(state(t).equals("site")&&Annexes.view(t.l,t.e,t.hut).getCompound(0).getInt("conflicts")>0,"A chest on the site: taken ("+state(t)+")");
   h.assertTrue(Annexes.order(t.l,t.e,t.hut,CARPENTRY).equals("conflicts")&&!HallUpgradeGoal.pending(t.l,t.s.id()),"Refused, nothing queued");
   t.l.setBlock(site.offset(2,1,2),Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(state(t).equals("ready"),"Cleared, the site is free again");
   // A house put up beside the hut first takes the place: its three-block buffer covers the site.
   var o=site.subtract(t.e.center());t.s.addBuilding(new Settlement.Building(Settlement.childId(t.s.id(),"building/home-west"),"home",o.getX()-8,0,o.getZ()));
   h.assertTrue(state(t).equals("site"),"Another building beside the hut takes the site");
  }finally{done(t);}
  h.succeed();
 }
}
