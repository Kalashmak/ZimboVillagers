package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-130: who works a farm of each level — 1/1/1/2/3 farmers and none at VI, where the barn's machine sows and reaps every floor. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmStaffGameTests {
 private static Settlement.Building add(Settlement s,String type,int x,int z){var b=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,x,0,z);s.addBuilding(b);return b;}
 private static void adults(Settlement s,int n){var home=Settlement.childId(s.id(),"home");if(s.homes().isEmpty())s.addHome(new Settlement.Home(home,1,16,true));
  int have=s.residents().size();for(int i=0;i<n;i++){var r=new Resident(Settlement.childId(s.id(),"adult/"+(have+i)),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);}}
 private static long farmers(Settlement s,Settlement.Building farm){return s.employees(farm.id()).size();}
 /** Slots by the kept level; the second farmer of a IV farm is posted only once the bakery and the mill have theirs; a farm raised to VI lets
  *  its farmers go and the next pass posts them elsewhere; the farmers of a farm split its worked fields and never share one. */
 @GameTest(template="empty",timeoutTicks=100) public static void farmersFollowTheKeptLevel(GameTestHelper h){
  var s=new Settlement(UUID.randomUUID());s.lotLayout(OrganicLots.BARN_LOTS);add(s,"town_hall",0,0);var farm=add(s,"farm",20,0);add(s,"restaurant",0,20);add(s,"mill",12,20);
  int[] want={1,1,1,2,3,0};for(int level=1;level<=6;level++)h.assertTrue(Population.slots(s,farm.withLevel(level))==want[level-1]&&FarmField.farmers(level)==want[level-1],"Farmers at level "+level);
  // AD-130 (old worlds): a village that keeps the AD-104 field table has no barn and no level-VI machine, so its farm keeps its one farmer.
  var old=new Settlement(UUID.randomUUID());add(old,"town_hall",0,0);var oldFarm=add(old,"farm",20,0);
  h.assertTrue(FarmField.legacy(old)&&!FarmField.legacy(s),"The one village keeps the old field table, the other does not");
  for(int level=1;level<=6;level++)h.assertTrue(Population.slots(old,oldFarm.withLevel(level))==1,"An old village's farm keeps its farmer at level "+level);
  h.assertTrue(BuildingCards.slots(old,oldFarm.withLevel(6))==1,"And its card still shows the one post");
  for(int n=2;n<=4;n++)s.raiseBuildingLevel(farm.id(),n);farm=s.buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();
  h.assertTrue(BuildingCards.slots(s,farm)==2,"The office card shows the farm's two posts");
  var e=new SettlementData.Entry(s,h.getLevel().dimension().location().toString(),h.absolutePos(BlockPos.ZERO));
  // Builder, porter, farmer, baker, miller: five adults fill every first post; the sixth is the farm's second farmer.
  adults(s,5);Population.assign(e);
  h.assertTrue(farmers(s,farm)==1&&s.residents().stream().filter(r->r.profession()==Profession.BAKER).count()==1&&s.residents().stream().filter(r->r.profession()==Profession.MILLER).count()==1,"Five adults: one farmer, one baker, one miller");
  adults(s,1);Population.assign(e);
  h.assertTrue(farmers(s,farm)==2,"The sixth adult is the farm's second farmer: "+farmers(s,farm));
  // Their shares of a IV farm working all 12 fields: one floor each; of a IV farm whose core works only the ground: three fields each.
  var twelve=FarmField.modules(4);var a=FarmField.share(twelve,0,2);var b=FarmField.share(twelve,1,2);
  h.assertTrue(a.size()==6&&b.size()==6&&FarmField.floors(a).equals(List.of(0))&&FarmField.floors(b).equals(List.of(1)),"Two farmers, two floors");
  var ground=FarmField.modules(3);var c=FarmField.share(ground,0,2);var d=FarmField.share(ground,1,2);
  h.assertTrue(c.size()==3&&d.size()==3&&Collections.disjoint(keys(c),keys(d)),"Two farmers on one floor split its fields");
  var all=FarmField.modules(5);var seen=new HashSet<String>();for(int r=0;r<3;r++)for(var m:FarmField.share(all,r,3))h.assertTrue(seen.add(key(m)),"A field worked by two farmers: "+key(m));
  h.assertTrue(seen.size()==18,"Three farmers cover the 18 fields");
  // Raised to VI: the machine works alone, the farmers are let go and posted anew elsewhere or left free.
  s.raiseBuildingLevel(farm.id(),5);s.raiseBuildingLevel(farm.id(),6);Population.assign(e);
  h.assertTrue(farmers(s,farm)==0&&s.residents().stream().noneMatch(r->r.profession()==Profession.FARMER),"A VI farm has no farmer");
  h.succeed();
 }
 @GameTest(template="empty",batch="farm_illness_coverage",timeoutTicks=100)
 public static void healthyFarmersCoverTheirSickColleaguesFields(GameTestHelper h){
  var s=new Settlement(UUID.randomUUID());s.lotLayout(OrganicLots.BARN_LOTS);var farm=add(s,"farm",20,0);for(int level=2;level<=5;level++)s.raiseBuildingLevel(farm.id(),level);adults(s,3);for(var r:s.residents())s.assign(r.id(),Profession.FARMER,farm.id());var crew=List.copyOf(s.residents());
  h.assertTrue(FarmField.farmers(s,farm).size()==3,"Three healthy farmers initially cover all floors");crew.get(0).fallIll();var active=FarmField.farmers(s,farm);h.assertTrue(active.size()==2&&!active.contains(crew.get(0).id()),"The resting farmer leaves no unworked share assigned to himself");var seen=new HashSet<String>();for(int rank=0;rank<active.size();rank++)for(var m:FarmField.share(FarmField.modules(5),rank,active.size()))h.assertTrue(seen.add(key(m)),"Healthy workers have disjoint fields");h.assertTrue(seen.size()==18,"Healthy workers cover every one of the eighteen fields");
  crew.get(0).cure();h.assertTrue(FarmField.farmers(s,farm).equals(crew.stream().map(Resident::id).toList()),"Recovery restores the original staffing order and normal division");crew.get(1).die();h.assertTrue(FarmField.farmers(s,farm).size()==2,"A dead resident does not retain a field share");for(var r:crew)r.fallIll();h.assertTrue(FarmField.farmers(s,farm).isEmpty(),"All sick: no working crew is invented");h.succeed();
 }
 private static String key(int[] m){return m[0]+","+m[1]+","+FarmField.floor(m);}
 private static Set<String> keys(List<int[]> ms){var out=new HashSet<String>();for(var m:ms)out.add(key(m));return out;}
 /** The core's "machine_fields" (0 below VI, 18 at VI): at V a layout-6 farm's machine does nothing, at VI it sows one plot on every floor in
  *  a turn — the upper fields' dirt tilled first — from the seed in the farm chest; the porters are asked for its seed. */
 @GameTest(template="empty",batch="farm_staff_vi",timeoutTicks=900) public static void theMachineWorksEveryFloorAtSix(GameTestHelper h){
  for(int level=1;level<=6;level++)h.assertTrue(Machines.automaticFields(level)==CoreEffects.value("farm","machine_fields",level),"Machine fields at level "+level);
  var t=FarmBarnGameTests.yard(h);
  try{
   FarmBarnGameTests.raise(h,t,5);
   var chest=LogisticsRoutes.chest(t.l(),t.e(),FarmBarnGameTests.farm(t));h.assertTrue(chest!=null,"The farm has its chest");chest.setItem(0,new ItemStack(Items.WHEAT_SEEDS,32));
  }catch(RuntimeException ex){FarmBarnGameTests.done(t.l(),t.s());throw ex;}
  h.startSequence().thenIdle(40).thenExecute(()->{
   try{
    int before=sown(t);Machines.tick(t.l(),t.e(),120,Workshops.wants(t.l(),t.e()));
    h.assertTrue(sown(t)==before,"At V the barn's machine does not sow: "+before+" -> "+sown(t));
    FarmBarnGameTests.upgrade(h,t);var farm=FarmBarnGameTests.farm(t);
    h.assertTrue(BuildingTiers.level(t.l(),t.e(),farm)==6,"The farm works at VI: "+BuildingTiers.level(t.l(),t.e(),farm));
    int[] perFloor=new int[3];for(int f=0;f<3;f++)perFloor[f]=sownOn(t,f);
    h.assertTrue(Machines.tick(t.l(),t.e(),60,Workshops.wants(t.l(),t.e()))>0,"The machine takes its turn: "+Machines.lastReason);
    for(int f=0;f<3;f++)h.assertTrue(sownOn(t,f)==perFloor[f]+1,"One plot sown on floor "+f+": "+perFloor[f]+" -> "+sownOn(t,f)+" why="+Machines.lastReason);
    LogisticsRoutes.chest(t.l(),t.e(),farm).clearContent();
    h.assertTrue(Workshops.wants(t.l(),t.e()).stream().anyMatch(w->w.destination().equals(farm.id())&&w.matches(new ItemStack(Items.WHEAT_SEEDS))),"An empty VI farm asks the porters for its seed");
   }finally{FarmBarnGameTests.done(t.l(),t.s());}
  }).thenSucceed();
 }
 private static int sown(FarmBarnGameTests.Yard t){int n=0;for(int f=0;f<3;f++)n+=sownOn(t,f);return n;}
 private static int sownOn(FarmBarnGameTests.Yard t,int floor){int n=0;for(var p:FarmBarnGameTests.plots(t,floor))if(t.l().getBlockState(p).getBlock() instanceof CropBlock)n++;return n;}
}
