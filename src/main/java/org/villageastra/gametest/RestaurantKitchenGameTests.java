package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.RestaurantFixture.*;
/** AD-139 §10.1 (9–12): the restaurant's kitchen by level — meat from II, the great oven of III (batch 16 on half the fuel), the automatic
 *  kitchen of VI that cooks with nobody at it, and the cook released at VI. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RestaurantKitchenGameTests {
 private static List<Workshops.Want> want(net.minecraft.world.item.Item item,int n,Settlement.Building to){return List.of(new Workshops.Want(Ingredient.of(item),n,to.id()));}
 /** 9. At level I the cook does not roast beef; at II he does. */
 @GameTest(template="empty",timeoutTicks=200) public static void meatIsRefusedBelowLevelTwo(GameTestHelper h){
  var v=village(h,1);put(v.kitchen(),new ItemStack(Items.BEEF,8));put(v.kitchen(),new ItemStack(Items.COAL,4));
  try{h.assertTrue(Workshops.plan(v.l(),v.e(),v.kept(),v.kitchen(),want(Items.COOKED_BEEF,8,v.hall()))==null,"Level I plans no roast beef");
   h.assertTrue(!Workshops.spec(v.l(),v.e(),v.kept()).outputItems().contains(Items.COOKED_BEEF)&&Workshops.spec("restaurant",2).outputItems().contains(Items.COOKED_BEEF),"Meat opens at II");
   raise(v,2);var job=Workshops.plan(v.l(),v.e(),v.kept(),v.kitchen(),want(Items.COOKED_BEEF,8,v.hall()));
   h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.COOKED_BEEF)),"Level II roasts beef: "+job);
  }finally{done(v);}
  h.succeed();
 }
 /** 10. The great oven (III): a bread job is up to 16 loaves and a loaf takes 100 ticks of fuel instead of 200. */
 @GameTest(template="empty",timeoutTicks=200) public static void theGreatOvenHalvesTheFuel(GameTestHelper h){
  var v=village(h,2);put(v.kitchen(),new ItemStack(VillageAstra.FLOUR.get(),32));put(v.kitchen(),new ItemStack(Items.COAL,4));
  try{var two=Workshops.plan(v.l(),v.e(),v.kept(),v.kitchen(),want(Items.BREAD,16,v.hall()));
   h.assertTrue(two!=null&&two.units()==8&&two.fuelTicks()==8*200,"Level II bakes 8 on 200 ticks a loaf: "+two);
   raise(v,3);var three=Workshops.plan(v.l(),v.e(),v.kept(),v.kitchen(),want(Items.BREAD,16,v.hall()));
   h.assertTrue(three!=null&&three.units()==16&&three.fuelTicks()==16*100,"Level III bakes 16 on 100 ticks a loaf: "+three);
   h.assertTrue(Arrays.equals(Workshops.custom("restaurant","bread",3),new int[]{16,100})&&Arrays.equals(Workshops.custom("restaurant","bread",1),new int[]{8,200}),"The card's numbers are the kitchen's");
  }finally{done(v);}
  h.succeed();
 }
 /** 11. A level-VI kitchen with nobody posted cooks by itself (Machines): flour and coal in its chest become bread the village asked for. */
 @GameTest(template="empty",timeoutTicks=400) public static void aLevelSixKitchenWorksWithoutACook(GameTestHelper h){
  var v=village(h,6);put(v.kitchen(),new ItemStack(VillageAstra.FLOUR.get(),16));put(v.kitchen(),new ItemStack(Items.COAL,2));
  try{research(v,6);var b=v.kept();
   h.assertTrue(BuildingLevels.level(v.l(),v.e(),b)==6&&Automation.at(v.l(),v.e(),b).bench(),"Level VI turns its own bench: level "+BuildingLevels.level(v.l(),v.e(),b));
   h.assertTrue(v.s().employees(b.id()).isEmpty(),"Nobody is posted at the kitchen");
   var wants=want(Items.BREAD,8,v.hall());long now=0;
   for(int turn=0;turn<400&&count(v.kitchen(),Items.BREAD)<8;turn++){now+=Machines.period(6);Machines.tick(v.l(),v.e(),now,wants);}
   h.assertTrue(count(v.kitchen(),Items.BREAD)>=8,"The machine baked the bread: "+count(v.kitchen(),Items.BREAD));
  }finally{done(v);}
  h.succeed();
 }
 /** 12. Kept at VI, the restaurant opens no cook's post and releases the cook it had; its couriers stay. */
 @GameTest(template="empty",timeoutTicks=200) public static void theSixthLevelReleasesTheCook(GameTestHelper h){
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/warehouse"),"warehouse",20,0,0));
  var rest=new Settlement.Building(Settlement.childId(s.id(),"building/restaurant"),"restaurant",10,0,0);s.addBuilding(rest);
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,20,true);s.addHome(home);
  var people=new ArrayList<Resident>();for(int i=0;i<8;i++){var r=new Resident(Settlement.childId(s.id(),"a/"+i),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home.id());people.add(r);}
  var e=new org.villageastra.server.SettlementData.Entry(s,h.getLevel().dimension().location().toString(),h.absolutePos(BlockPos.ZERO));
  for(int lv=2;lv<=5;lv++)s.raiseBuildingLevel(rest.id(),lv);
  for(int i=0;i<4;i++)Population.assign(e);
  long cooks=people.stream().filter(r->r.profession()==Profession.BAKER).count(),couriers=people.stream().filter(r->r.profession()==Profession.PORTER&&s.workplace(r.id()).id().equals(rest.id())).count();
  h.assertTrue(cooks==1&&couriers==2,"Level V: a cook and two couriers: cooks="+cooks+" couriers="+couriers);
  s.raiseBuildingLevel(rest.id(),6);Population.assign(e);
  cooks=people.stream().filter(r->r.profession()==Profession.BAKER).count();couriers=people.stream().filter(r->r.profession()==Profession.PORTER&&s.workplace(r.id())!=null&&s.workplace(r.id()).id().equals(rest.id())).count();
  h.assertTrue(cooks==0&&couriers==2,"Level VI: no cook, the couriers stay: cooks="+cooks+" couriers="+couriers);
  h.assertTrue(Population.slots(s,s.buildings().stream().filter(x->x.id().equals(rest.id())).findFirst().orElseThrow())==0,"No cook's post at VI");
  h.succeed();
 }
}
