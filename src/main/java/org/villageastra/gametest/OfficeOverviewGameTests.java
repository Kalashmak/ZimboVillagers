package org.villageastra.gametest;
import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** The office's snapshot data for the redrawn town hall: overview{} (food against a day of meals, missed meals, beds; read once per 40 ticks),
 *  a building's upgrade cost item by item against the hall chest, a card's status from what its worker last said, and the election's term. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class OfficeOverviewGameTests {
 private static void delete(Path p){try{Files.deleteIfExists(p);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}}
 private record Bench(ServerLevel l,Settlement s,SettlementData.Entry e,Map<String,Settlement.Building> buildings){
  Settlement.Building building(String type){return buildings.get(type);}
  OwnedChestEntity chest(String type){return LogisticsRoutes.chest(l,e,buildings.get(type));}
 }
 /** WorkshopGameTests' bench: each station on its own lot with its real chest, and one house of two beds. */
 private static Bench bench(GameTestHelper h,String... types){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());var map=new LinkedHashMap<String,Settlement.Building>();int i=0;
  for(var type:types){var b=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,(i%3)*14,0,(i/3)*12);s.addBuilding(b);map.put(type,b);
   var chest=center.offset(b.x()+1,b.y()+1,b.z()+4);l.setBlock(chest.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);i++;}
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"house/office"),1,2,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);OfficeOverview.clear();
  return new Bench(l,s,e,map);
 }
 private static Resident adult(Bench b,String name){var r=new Resident(Settlement.childId(b.s.id(),"resident/"+name),Resident.Life.ADULT,false,null,null,-1);b.s.admit(r,Settlement.childId(b.s.id(),"house/office"));return r;}
 private static void done(Bench b){
  for(var x:b.buildings.values())delete(Workshops.path(b.l,x.id()));delete(BookResearch.path(b.l,b.s.id()));
  SettlementData.get(b.l.getServer()).remove(b.s.id());OfficeOverview.clear();
 }
 private static CompoundTag view(Bench b,net.minecraft.server.level.ServerPlayer p){var tag=new CompoundTag();tag.putUUID("village",b.s.id());OfficeOverview.addView(p,tag);return tag.getCompound("overview");}
 /** Food is the rations in the hall chest, need is a day of meals for everyone alive (the hand-bread gate's reserve), and a resident who missed
  *  a meal is counted; a second look within 40 ticks is the cached one, so pantries are not walked for every player's pass. */
 @GameTest(template="empty",timeoutTicks=100) public static void overviewCountsFoodAgainstADayOfMeals(GameTestHelper h){
  var b=bench(h,"town_hall");
  try{
   var fed=adult(b,"fed");var missed=adult(b,"missed");missed.restoreNeeds(missed.born(),missed.lastMeal(),1,missed.schoolTicks());
   var hall=b.chest("town_hall");for(int i=0;i<hall.getContainerSize();i++)hall.setItem(i,ItemStack.EMPTY);hall.setItem(0,new ItemStack(Items.BREAD,7));
   int bread=Population.nutrition(new ItemStack(Items.BREAD));
   var p=FakePlayerFactory.get(b.l,new GameProfile(UUID.randomUUID(),"OfficeViewer"));
   var o=view(b,p);
   h.assertTrue(o.getInt("food")==7*bread,"Food is the rations of the bread in the hall: "+o.getInt("food")+" of "+7*bread);
   h.assertTrue(o.getInt("need")==HandBread.reserveRations(b.e)&&o.getInt("need")>0,"Need is one day of meals for both: "+o.getInt("need")+" vs "+HandBread.reserveRations(b.e));
   h.assertTrue(o.getInt("residents")==2&&o.getInt("adults")==2&&o.getInt("missed")==1,"Two adults, one missed a meal: "+o);
   h.assertTrue(o.getInt("beds")==2&&o.getInt("bedsFree")==0&&o.getInt("homes")==1,"One house of two beds, both taken: "+o);
   h.assertTrue(!o.getBoolean("raid")&&!o.getBoolean("besieged")&&!o.getBoolean("project")&&o.getInt("builders")==0,"No raid, siege, project or builder: "+o);
   hall.setItem(1,new ItemStack(Items.BREAD,3));
   h.assertTrue(view(b,p).getInt("food")==7*bread,"Within 40 ticks the office shows the cached reading");
   OfficeOverview.clear();
   h.assertTrue(view(b,p).getInt("food")==10*bread,"A fresh reading counts the new bread: "+view(b,p).getInt("food"));
   h.assertTrue(fed.missedMeals()==0,"The fed resident is untouched");
  }finally{done(b);}
  h.succeed();
 }
 /** The next level's cost is listed item by item as have/need against the hall chest, the most missing first, at most 12; the totals stay. */
 @GameTest(template="empty",timeoutTicks=100) public static void upgradeCostListsHaveAndNeedFromTheHallChest(GameTestHelper h){
  var b=bench(h,"town_hall","mill");
  try{
   var mill=b.building("mill");h.assertTrue(BuildingTiers.upgradable("mill"),"The mill has levels");
   var cost=BuildingTiers.cost("mill",BuildingTiers.built(b.e,mill)+1);h.assertTrue(!cost.isEmpty(),"The mill's second level costs something");
   var first=cost.entrySet().iterator().next();var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(first.getKey()));
   var hall=b.chest("town_hall");for(int i=0;i<hall.getContainerSize();i++)hall.setItem(i,ItemStack.EMPTY);int put=Math.min(first.getValue(),item.getMaxStackSize());hall.setItem(0,new ItemStack(item,put));
   var view=BuildingTiers.view(b.l,b.e,mill);var list=view.getList("cost",Tag.TAG_COMPOUND);
   h.assertTrue(list.size()==Math.min(cost.size(),BuildingTiers.COST_ROWS),"One row per item, at most "+BuildingTiers.COST_ROWS+": "+list.size()+" of "+cost.size());
   CompoundTag row=null;int lack=0,prev=Integer.MAX_VALUE;
   for(int i=0;i<list.size();i++){var r=list.getCompound(i);int missing=r.getInt("need")-r.getInt("have");h.assertTrue(missing<=prev,"The most missing first: "+list);prev=missing;
    h.assertTrue(cost.get(r.getString("item"))==r.getInt("need"),"Need is the design's count of "+r.getString("item"));
    if(r.getString("item").equals(first.getKey()))row=r;else h.assertTrue(r.getInt("have")==0,"Only the item put in the hall is held: "+r);lack+=Math.max(0,missing);}
   if(cost.size()<=BuildingTiers.COST_ROWS){h.assertTrue(row!=null&&row.getInt("have")==put,"The hall holds "+put+" of "+first.getKey()+": "+row);h.assertTrue(lack==view.getInt("lack"),"The rows add up to the total lack: "+lack+" vs "+view.getInt("lack"));}
   h.assertTrue(view.getInt("items")==BuildingTiers.count(cost),"The total item count stays for older readers");
  }finally{done(b);}
  h.succeed();
 }
 /** A staffed bakery whose baker found no flour shows missing_input (from the worker's own status, set the way WorkshopGoal sets it);
  *  a mill with nobody posted shows no_worker; a house shows its beds. */
 @GameTest(template="empty",timeoutTicks=100) public static void cardStatusComesFromTheWorkersStatus(GameTestHelper h){
  var b=bench(h,"town_hall","mill","restaurant");ResidentEntity worker=null;
  try{
   var baker=adult(b,"baker");b.s.assign(baker.id(),Profession.BAKER,b.building("restaurant").id());
   var bakery=b.building("restaurant");var at=LogisticsRoutes.position(b.e,bakery);
   worker=VillageAstra.RESIDENT.get().create(b.l);worker.bind(b.s.id(),baker);worker.setNoAi(true);worker.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5,0,0);b.l.addFreshEntity(worker);
   var status=Workshops.advance(b.l,b.e,bakery,1000,Workshops.wants(b.l,b.e));worker.workStatus(status);
   h.assertTrue(status.equals("workshop_missing_inputs"),"The bakery lacks flour: "+status);
   var card=BuildingCards.card(b.l,b.e,bakery);
   h.assertTrue(card.getString("status").equals("missing_input")&&card.getInt("staff")==1&&card.getInt("slots")==1,"The bakery card waits for its input: "+card.getString("status")+" staff="+card.getInt("staff")+" slots="+card.getInt("slots"));
   var mill=BuildingCards.card(b.l,b.e,b.building("mill"));
   h.assertTrue(mill.getString("status").equals("no_worker")&&mill.getInt("staff")==0,"Nobody works the mill: "+mill.getString("status"));
   h.assertTrue(BuildingCards.status("workshop_missing_chest").equals("blocked")&&BuildingCards.status("science_missing_desk").equals("blocked")&&BuildingCards.status("unsafe_ground").equals("blocked")&&BuildingCards.status("road_missing_materials").equals("missing_input")&&BuildingCards.status("workshop_idle").equals("idle")&&BuildingCards.status("workshop_working").isEmpty(),"Statuses group as blocked, idle and fine");
   var hall=BuildingCards.card(b.l,b.e,b.building("town_hall"));h.assertTrue(!hall.contains("slots"),"The hall posts nobody by opening");
   var house=new Settlement.Building(Settlement.childId(b.s.id(),"house/office"),"home",0,0,24);b.s.addBuilding(house);
   var home=BuildingCards.card(b.l,b.e,house);h.assertTrue(home.getInt("beds")==2&&home.getInt("bedsFree")==1,"The house has two beds, one taken: "+home.getInt("beds")+"/"+home.getInt("bedsFree"));
  }finally{if(worker!=null)worker.discard();done(b);}
  h.succeed();
 }
 /** The election card gets the term's length and the number standing. */
 @GameTest(template="empty",timeoutTicks=100) public static void electionViewCarriesPeriodAndCandidates(GameTestHelper h){
  var b=bench(h,"town_hall");
  try{
   var p=FakePlayerFactory.get(b.l,new GameProfile(UUID.randomUUID(),"OfficeVoter"));var roll=PropertyLedger.get(b.l.getServer()).roll(b.s.id());
   var tag=new CompoundTag();tag.putUUID("village",b.s.id());Elections.addView(p,tag);var v=tag.getCompound("election");
   h.assertTrue(v.getLong("period")==ElectionRoll.PERIOD&&v.getInt("candidates")==0,"A term of "+ElectionRoll.PERIOD+" and nobody standing: "+v);
   roll.candidate(p.getUUID(),true);
   tag=new CompoundTag();tag.putUUID("village",b.s.id());Elections.addView(p,tag);
   h.assertTrue(tag.getCompound("election").getInt("candidates")==1,"One candidate: "+tag.getCompound("election"));
   roll.candidate(p.getUUID(),false);
  }finally{done(b);}
  h.succeed();
 }
}
