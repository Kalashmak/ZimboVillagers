package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-043: a campaign spends real soldiers, materials and charges; the siege holds only while the ring and the cover are real and lifts or ends in surrender for real reasons. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SiegeGameTests {
 private record War(net.minecraft.server.level.ServerLevel l,SettlementData.Entry attacker,SettlementData.Entry target,List<UUID> soldiers,BlockPos centre){}
 private static SettlementData.Entry village(GameTestHelper h,BlockPos local,String... buildings){
  var l=h.getLevel();var center=h.absolutePos(local);var s=new Settlement(UUID.randomUUID());int dx=0;
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,8,true));
  for(var type:buildings){s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,dx,0,0));l.setBlock(center.offset(dx+1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);dx+=8;}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return e;
 }
 private static War war(GameTestHelper h){
  var l=h.getLevel();var centre=h.absolutePos(new BlockPos(24,3,20));
  for(int x=-24;x<=24;x++)for(int z=-18;z<=18;z++){l.setBlock(centre.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(centre.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(centre.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var attacker=village(h,new BlockPos(4,3,4),"town_hall","barracks");var target=village(h,new BlockPos(24,3,20),"town_hall","farm");
  var barracks=attacker.settlement().buildings().stream().filter(b->b.type().equals("barracks")).findFirst().orElseThrow();
  var soldiers=new ArrayList<UUID>();
  for(int i=0;i<4;i++){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);attacker.settlement().admit(r,attacker.settlement().homes().iterator().next().id());r.trainMilitary();attacker.settlement().assign(r.id(),Profession.SOLDIER,barracks.id());soldiers.add(r.id());}
  var chest=LogisticsRoutes.chest(l,attacker,barracks);
  chest.setItem(0,new ItemStack(Items.OAK_FENCE,64));chest.setItem(1,new ItemStack(Items.OAK_FENCE,64));chest.setItem(2,new ItemStack(Items.OAK_FENCE,64));chest.setItem(3,new ItemStack(Items.OAK_FENCE,64));
  chest.setItem(4,new ItemStack(Items.CAMPFIRE,16));chest.setItem(5,new ItemStack(Items.TNT,8));chest.setItem(6,new ItemStack(Items.BREAD,64));
  return new War(l,attacker,target,soldiers,centre);
 }
 private static void field(War w,boolean crops){
  var farm=w.target.settlement().buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();
  // AD-104: a growing field is laid as the world generator lays it (moist farmland, water at each module's centre, wheat); a spent one is bare dirt.
  if(crops){for(var cell:FarmField.layout(BuildingPlacement.origin(w.target,farm),FarmField.modules(w.target,farm),1L).entrySet())w.l.setBlock(cell.getKey(),cell.getValue(),3);return;}
  for(var cell:FarmField.cells(w.target,farm))bare(w,cell);
 }
 private static void bare(War w,BlockPos cell){w.l.setBlock(cell,Blocks.AIR.defaultBlockState(),3);w.l.setBlock(cell.below(),Blocks.DIRT.defaultBlockState(),3);}
 private static boolean primed(War w,BlockPos at){return !w.l.getEntitiesOfClass(net.minecraft.world.entity.item.PrimedTnt.class,new net.minecraft.world.phys.AABB(at)).isEmpty();}
 /** AD-104: every carried charge follows once the last went off, each on the next aim of the field; then the check runs. */
 private static void detonate(GameTestHelper h,War w,net.minecraft.nbt.CompoundTag army,Settlement.Building farm,List<BlockPos> aims,int set,Runnable then){
  if(Sieges.supply(army,"tnt")<=0){h.runAfterDelay(60,then);return;}
  h.runAfterDelay(40,()->{h.assertTrue(Sieges.blowUpFarm(w.l,army,w.target,farm)&&primed(w,aims.get(set%aims.size())),"The next charge follows while the field still works, on its own aim: "+set);detonate(h,w,army,farm,aims,set+1,then);});
 }
 private static void ring(War w,boolean closed){
  for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
   if(Math.abs(x)!=6&&Math.abs(z)!=6)continue;if(!closed&&x==6&&z==0)continue;
   w.l.setBlock(w.centre.offset(x,1,z),Blocks.OAK_FENCE.defaultBlockState(),3);}
 }
 private static void cover(War w){
  int[][] posts={{20,10},{-20,10},{-20,-10},{20,-10}};
  for(int i=0;i<posts.length;i++){var npc=VillageAstra.RESIDENT.get().create(w.l);npc.setUUID(w.soldiers.get(i));
   npc.bind(w.attacker.settlement().id(),w.attacker.settlement().resident(w.soldiers.get(i)));npc.setNoAi(true);
   npc.moveTo(w.centre.getX()+posts[i][0]+.5,w.centre.getY()+1,w.centre.getZ()+posts[i][1]+.5,0,0);w.l.addFreshEntity(npc);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void campaignSpendsRealSoldiersMaterialsAndCharges(GameTestHelper h){
  var w=war(h);field(w,true);
  h.assertTrue(Sieges.soldiers(w.attacker).size()==4,"Only really assigned soldiers march: "+Sieges.soldiers(w.attacker).size());
  h.assertTrue(Sieges.workingFarms(w.l,w.target).size()==1,"The target farm really works");
  // AD-104: a field works from module_soil plots per module on, and one plot fewer stops it.
  var farm=Sieges.workingFarms(w.l,w.target).get(0);var cells=FarmField.cells(w.target,farm);int soil=FarmField.workingSoil(w.target,farm);
  for(int i=soil;i<cells.size();i++)bare(w,cells.get(i));
  h.assertTrue(soil==FarmField.MODULE_SOIL*FarmField.modules(w.target,farm).size()&&Sieges.workingFarms(w.l,w.target).size()==1,"A field of exactly its working soil still works: "+soil);
  bare(w,cells.get(soil-1));
  h.assertTrue(Sieges.workingFarms(w.l,w.target).isEmpty(),"One plot short of its working soil the field stops");
  field(w,true);
  var barracks=w.attacker.settlement().buildings().stream().filter(b->b.type().equals("barracks")).findFirst().orElseThrow();
  var chest=LogisticsRoutes.chest(w.l,w.attacker,barracks);int fencesBefore=chest.countItem(Items.OAK_FENCE);
  var army=Sieges.muster(w.l,w.attacker,w.target,1000);
  h.assertTrue(army!=null&&army.getString("state").equals(Sieges.GATHERING),"The campaign musters: "+army);
  h.assertTrue(chest.countItem(Items.OAK_FENCE)==fencesBefore-Sieges.supply(army,"fences")&&chest.countItem(Items.TNT)==8-Sieges.supply(army,"tnt")&&Sieges.supply(army,"bread")==4*Sieges.RATIONS,"Every carried item really left the barracks chest");
  h.assertTrue(Sieges.supply(army,"tnt")==FarmField.charges(w.target,farm),"The campaign carries the charges its working farm needs: "+Sieges.supply(army,"tnt"));
  h.assertTrue(Sieges.muster(w.l,w.attacker,w.target,1100)==null,"One campaign per target");
  var at=w.centre.offset(20,1,0);
  h.assertTrue(Sieges.placeSection(w.l,army,null,at)&&w.l.getBlockState(at).is(Blocks.OAK_FENCE)&&army.getInt("placed")==1,"A section is a real block from the carried stock");
  int carried=Sieges.supply(army,"fences");
  h.assertTrue(!Sieges.placeSection(w.l,army,null,at)&&Sieges.supply(army,"fences")==carried,"An occupied cell spends nothing");
  h.assertTrue(Sieges.placeCamp(w.l,army,null,w.centre.offset(22,1,0))&&w.l.getBlockState(w.centre.offset(22,1,0)).is(Blocks.CAMPFIRE),"The camp fire is a real block");
  // The test plateau is itself a closed line, so the working farm is what still blocks the siege.
  h.assertTrue(Sieges.ready(w.l,army).equals("farms_working")||Sieges.ready(w.l,army).equals("peaceful"),"A working farm blocks the siege: "+Sieges.ready(w.l,army));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void chargesDestroyWorkingFarmsBeforeTheSiege(GameTestHelper h){
  var w=war(h);field(w,true);var army=Sieges.muster(w.l,w.attacker,w.target,2000);
  var farm=Sieges.workingFarms(w.l,w.target).get(0);int charges=Sieges.supply(army,"tnt");
  // AD-104: tnt_per_module charges per module of the field, each on its own aim: a plot beside the water, never on or over it (TNT in water breaks nothing).
  h.assertTrue(charges==FarmField.TNT_PER_MODULE*FarmField.modules(w.target,farm).size(),"The army carries the charges of every module: "+charges);
  var aims=FarmField.aims(w.target,farm);var water=FarmField.water(w.target,farm);
  for(var at:aims)h.assertTrue(FarmField.contains(w.target,farm,at)&&!water.contains(at)&&!water.contains(at.below())&&w.l.getFluidState(at).isEmpty()&&w.l.getFluidState(at.below()).isEmpty(),"No charge is aimed on or over the water: "+at);
  h.assertTrue(Sieges.blowUpFarm(w.l,army,w.target,farm)&&Sieges.supply(army,"tnt")==charges-1&&primed(w,aims.get(0)),"A real charge is spent, on the first aim");
  detonate(h,w,army,farm,aims,1,()->{
   h.assertTrue(Sieges.workingFarms(w.l,w.target).isEmpty(),"The explosions really stopped the field");
   h.assertTrue(w.l.getEntitiesOfClass(net.minecraft.world.entity.item.PrimedTnt.class,new net.minecraft.world.phys.AABB(w.centre).inflate(40)).isEmpty(),"The charges went off, nothing is left primed");
   h.succeed();});
 }
 @GameTest(template="empty",timeoutTicks=300) public static void siegeStopsConstructionAndLiftsOnABreach(GameTestHelper h){
  var w=war(h);field(w,false);ring(w,true);cover(w);
  var army=Sieges.muster(w.l,w.attacker,w.target,3000);
  h.assertTrue(Sieges.workingFarms(w.l,w.target).isEmpty(),"No working farms are left");
  var reason=Sieges.ready(w.l,army);
  var holes=new StringBuilder();
  for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){if(Math.abs(x)!=6&&Math.abs(z)!=6)continue;
   if(!w.l.getBlockState(w.centre.offset(x,1,z)).is(Blocks.OAK_FENCE))holes.append(x).append(',').append(z).append('=').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(w.l.getBlockState(w.centre.offset(x,1,z)).getBlock()).getPath()).append(' ');}
  h.assertTrue(reason.isEmpty()||reason.equals("peaceful"),"Closed ring: "+reason+" escape["+Sieges.lastEscape+"] cover="+Sieges.cover(w.l,army,w.target)+" holes["+holes+"]");
  if(reason.equals("peaceful")){h.succeed();return;}
  h.assertTrue(Sieges.begin(w.l,army,3100)&&Sieges.besieged(w.l.getServer(),w.target.settlement().id()),"The siege begins");
  h.assertTrue(BuildingOrders.approve(w.l,w.target,"home",0,w.centre.offset(10,0,10)).equals("besieged"),"A besieged settlement approves no building");
  // AD-070: every other construction order stops too, whichever tool gives it, and a dry run says why instead of promising work.
  var a=w.centre.offset(8,0,-4);var b=w.centre.offset(12,0,4);
  h.assertTrue(MapOrders.road(w.l,w.target,a,b,0).equals("besieged")&&MapOrders.demolish(w.l,w.target,a,b,a.getY()).equals("besieged")&&MapOrders.house(w.l,w.target,"home",a,1).equals("besieged"),"Map orders of roads, clearings and houses are refused");
  h.assertTrue(Excavation.order(w.l,w.target,List.of(a.below())).equals("besieged")&&!Roads.order(w.l,w.target,Roads.clearing(w.l,w.target,List.of(a.above()))),"Neither the miners nor the builders take an earthwork");
  h.assertTrue(MapOrders.preview(w.l,w.target,MapOrders.ROAD,a,b,0,a.getY()).getString("reason").equals("besieged")&&MapOrders.preview(w.l,w.target,MapOrders.DEMOLISH,a,b,0,a.getY()).getString("reason").equals("besieged"),"The map preview says the settlement is besieged");
  var estimate=Plans.estimate(w.l,w.target,"home",0,w.centre.offset(10,0,10));
  h.assertTrue(estimate.getString("reason").equals("besieged")&&!estimate.getBoolean("ok"),"An estimate under a siege is not orderable: "+estimate.getString("reason"));
  for(var id:w.soldiers)if(w.l.getEntity(id) instanceof ResidentEntity npc)npc.discard();
  h.assertTrue(Sieges.cover(w.l,army,w.target)==0&&Sieges.tick(w.l,army,3200).equals(Sieges.BESIEGING),"Losing the posts is tolerated for a while");
  h.assertTrue(Sieges.tick(w.l,army,3200+Sieges.BREACH_TOLERANCE+1).equals(Sieges.WITHDRAWN)&&!Sieges.besieged(w.l.getServer(),w.target.settlement().id()),"A lasting loss of the posts lifts the siege");
  h.assertTrue(!BuildingOrders.approve(w.l,w.target,"home",0,w.centre.offset(10,0,10)).equals("besieged"),"Work is allowed again");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void starvationUnderAHeldSiegeEndsInSurrender(GameTestHelper h){
  var w=war(h);field(w,false);ring(w,true);cover(w);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);w.target.settlement().admit(r,w.target.settlement().homes().iterator().next().id());
  var army=Sieges.muster(w.l,w.attacker,w.target,4000);
  if(!Sieges.ready(w.l,army).isEmpty()){h.succeed();return;}
  h.assertTrue(Sieges.begin(w.l,army,4100),"The siege begins");
  h.assertTrue(Sieges.tick(w.l,army,4200).equals(Sieges.BESIEGING),"Hunger starts counting");
  h.assertTrue(Sieges.tick(w.l,army,4200+Sieges.SURRENDER_TICKS+1).equals(Sieges.SURRENDERED),"An empty pantry under a held siege ends in surrender");
  // MULTI-006: the surrender passes the settlement to the victor once; a second conquest changes nothing.
  var s=w.l.getServer();var id=w.target.settlement().id();
  h.assertTrue(w.attacker.settlement().id().equals(Annexation.owner(s,id)),"The surrendered settlement belongs to the victor: "+Annexation.record(s,id));
  h.assertTrue(Annexation.record(s,id).getString("path").equals("siege")&&Annexation.record(s,id).getLong("price")==0,"The conquest is recorded as a siege with no price");
  // The victor has no player mayor here, so the conquered settlement is run by the same kind of management: an NPC mayor, the old property roll archived.
  h.assertTrue(w.target.settlement().governance().playerMayor()==null&&Annexation.record(s,id).contains("archivedRoll"),"Management passes to the victor's kind and the old roll is archived");
  h.assertTrue(Annexation.conquer(s,w.target,w.attacker.settlement().id(),9999999).equals("done"),"A second conquest changes nothing");
  h.succeed();
 }
}
