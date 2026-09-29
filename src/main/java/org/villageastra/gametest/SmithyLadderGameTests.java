package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-155 (owner's Metallurgy ladder): the smithy makes iron at I, lanterns and diamond from II, wolf armour from IV; no smith at VI; wolf
 *  armour halves a wolf's damage for ARMOUR_HITS hits, its collar grey while it lasts. III-IV: the smithy's courier carries a worker's tool
 *  from the smithy straight into its workplace's chest; V-VI: a free wolf of the kennel does, no courier. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SmithyLadderGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void theSmithyMakesByItsLevel(GameTestHelper h){
  var one=Workshops.spec("smithy",1).outputItems();var two=Workshops.spec("smithy",2).outputItems();var four=Workshops.spec("smithy",4).outputItems();
  h.assertTrue(one.contains(Items.IRON_PICKAXE)&&one.contains(Items.IRON_CHESTPLATE)&&!one.contains(Items.DIAMOND_PICKAXE)&&!one.contains(Items.LANTERN),"I: iron, no diamond or lanterns");
  h.assertTrue(two.contains(Items.DIAMOND_PICKAXE)&&two.contains(Items.DIAMOND_CHESTPLATE)&&two.contains(Items.LANTERN)&&!two.contains(VillageAstra.WOLF_ARMOR.get()),"II: lanterns and diamond, no wolf armour yet");
  h.assertTrue(four.contains(VillageAstra.WOLF_ARMOR.get()),"IV: wolf armour");
  h.assertTrue(Staff.slots("smithy",1)==1&&Staff.slots("smithy",5)==1&&Staff.slots("smithy",6)==0,"A smith I..V, none at VI");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void wolfArmourHalvesTheDamageAndWearsOut(GameTestHelper h){
  var l=h.getLevel();var wolf=EntityType.WOLF.create(l);var at=h.absolutePos(new BlockPos(2,2,2));wolf.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);l.addFreshEntity(wolf);
  try{
   h.assertTrue(!VillageWolves.armour(wolf),"A wild wolf takes no armour");
   wolf.setTame(true);
   h.assertTrue(VillageWolves.armour(wolf)&&wolf.getCollarColor()==DyeColor.GRAY&&!VillageWolves.armour(wolf),"A tame wolf: armoured once, grey collar");
   h.assertTrue(VillageWolves.armoured(wolf,4F)==2F,"Half the damage");
   for(int i=1;i<VillageWolves.ARMOUR_HITS;i++)VillageWolves.armoured(wolf,1F);
   h.assertTrue(wolf.getPersistentData().getInt(VillageWolves.ARMOUR)==0&&wolf.getCollarColor()==DyeColor.RED&&VillageWolves.armoured(wolf,4F)==4F,"Worn out: full damage, red collar");
  }finally{wolf.discard();}
  h.succeed();
 }

 private record Town(ServerLevel l,SettlementData.Entry e,Settlement.Building hall,Settlement.Building smithy,Settlement.Building mine,Settlement.Home home){}
 /** A hall, a smithy laid at {@code level} with a stone pickaxe in its chest, and a mine with its miner and no tool (all chests empty). */
 private static Town town(GameTestHelper h,int level){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,2,2));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/hall"),"town_hall",0,0,0);
  var smithy=new Settlement.Building(Settlement.childId(s.id(),"building/smithy"),"smithy",0,0,6);
  var mine=new Settlement.Building(Settlement.childId(s.id(),"building/mine"),"mine",18,0,6);
  for(var b:List.of(hall,smithy,mine))s.addBuilding(b);
  for(int x=-2;x<30;x++)for(int z=-2;z<22;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<14;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(int n=2;n<=level;n++)s.raiseBuildingLevel(smithy.id(),n);var kept=s.buildings().stream().filter(b->b.id().equals(smithy.id())).findFirst().orElseThrow();
  for(var cell:BuildingPlacement.layout(BuildingTiers.layoutId("smithy",level),BuildingPlacement.origin(e,kept),kept.rotation()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  BuildingLevels.forgetBest(s.id());
  for(var b:List.of(hall,kept,mine)){var at=LogisticsRoutes.position(e,b);if(!(l.getBlockEntity(at) instanceof OwnedChestEntity))l.setBlock(at,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);LogisticsRoutes.chest(l,e,b).clearContent();}
  LogisticsRoutes.chest(l,e,kept).setItem(0,new ItemStack(Items.STONE_PICKAXE));
  var home=new Settlement.Home(UUID.randomUUID(),1,8,true);s.addHome(home);
  var miner=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(miner,home.id());s.assign(miner.id(),Profession.MINER,mine.id());
  var state=MineWork.read(l,mine);state.remove("tool");state.putString("stage","tool");MineWork.write(l,mine,state);
  return new Town(l,e,hall,kept,mine,home);
 }
 private static void done(Town t){SettlementData.get(t.l.getServer()).remove(t.e.settlement().id());BuildingLevels.forgetBest(t.e.settlement().id());}
 @GameTest(template="empty",timeoutTicks=200) public static void theSmithyCourierCarriesTheToolToTheMine(GameTestHelper h){
  h.assertTrue(SmithyDelivery.posts(2)==0&&SmithyDelivery.posts(3)==1&&SmithyDelivery.posts(4)==1&&SmithyDelivery.posts(5)==0,"A courier at III and IV only");
  var t=town(h,3);var l=t.l;var e=t.e;
  try{
   h.assertTrue(BuildingLevels.level(l,e,t.smithy)==3,"The smithy works at III: "+BuildingLevels.level(l,e,t.smithy));
   h.assertTrue(!SmithyDelivery.delivers(l,e)&&WorkerSupplies.wants(l,e).stream().anyMatch(w->w.matches(new ItemStack(Items.STONE_PICKAXE))&&w.destination().equals(t.hall.id())),"No courier yet: the tool is wanted at the hall");
   var porter=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);e.settlement().admit(porter,t.home.id());e.settlement().assign(porter.id(),Profession.PORTER,t.smithy.id());
   h.assertTrue(SmithyDelivery.delivers(l,e)&&WorkerSupplies.wants(l,e).stream().anyMatch(w->w.matches(new ItemStack(Items.STONE_PICKAXE))&&w.destination().equals(t.mine.id())),"With its courier the tool is wanted at the mine");
   var route=LogisticsRoutes.choose(l,e,t.smithy);
   h.assertTrue(route!=null&&route.source().equals(t.smithy)&&route.destination().equals(t.mine)&&route.item().is(Items.STONE_PICKAXE),"The courier's route: smithy to mine, "+route);
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(e.settlement().id(),e.settlement().resident(porter.id()));npc.setNoAi(true);l.addFreshEntity(npc);
   try{
    var at=LogisticsRoutes.position(e,t.smithy);npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);PorterWork.step(npc);PorterWork.step(npc);
    h.assertTrue(LogisticsRoutes.chest(l,e,t.smithy).countItem(Items.STONE_PICKAXE)==0&&PorterWork.cargo(l,PorterWork.inspect(l,npc.getUUID())).is(Items.STONE_PICKAXE),"The courier holds the pickaxe");
    at=LogisticsRoutes.position(e,t.mine);npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);PorterWork.step(npc);PorterWork.step(npc);
    h.assertTrue(LogisticsRoutes.chest(l,e,t.mine).countItem(Items.STONE_PICKAXE)==1&&LogisticsRoutes.chest(l,e,t.hall).countItem(Items.STONE_PICKAXE)==0,"Put in the mine's own chest, not the hall's");
    h.assertTrue(WorkerSupplies.wants(l,e).stream().noneMatch(w->w.matches(new ItemStack(Items.STONE_PICKAXE))),"Nothing more is wanted: the tool waits in the mine");
   }finally{npc.discard();}
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aWolfCarriesTheToolAtFive(GameTestHelper h){
  var t=town(h,5);var l=t.l;var e=t.e;var dog=UUID.randomUUID();var sent=new ArrayList<BlockPos>();var released=new boolean[1];
  VillageDogs.provide(new VillageDogs.Source(){
   @Override public List<UUID> free(ServerLevel x,SettlementData.Entry y){return released[0]||sent.isEmpty()?List.of(dog):List.of();}
   @Override public boolean send(ServerLevel x,SettlementData.Entry y,UUID d,BlockPos to){sent.add(to);return true;}
   @Override public boolean near(ServerLevel x,SettlementData.Entry y,UUID d,BlockPos at,double r){return !sent.isEmpty()&&sent.get(sent.size()-1).equals(at);}
   @Override public void release(ServerLevel x,SettlementData.Entry y,UUID d){released[0]=true;}
   @Override public boolean pulls(){return true;}});
  try{
   h.assertTrue(BuildingLevels.level(l,e,t.smithy)==5&&SmithyDelivery.delivers(l,e),"The smithy works at V and its wolves deliver");
   for(int i=0;i<8&&!released[0];i++)SmithyDelivery.tick(l,e);
   h.assertTrue(released[0]&&LogisticsRoutes.chest(l,e,t.mine).countItem(Items.STONE_PICKAXE)==1&&LogisticsRoutes.chest(l,e,t.smithy).countItem(Items.STONE_PICKAXE)==0,"The wolf took the pickaxe from the smithy and put it in the mine: sent "+sent);
   h.assertTrue(sent.contains(LogisticsRoutes.position(e,t.smithy))&&sent.contains(LogisticsRoutes.position(e,t.mine)),"It went to the smithy, then to the mine");
  }finally{VillageDogs.provide(VillageWolves.DOGS);done(t);}
  h.succeed();
 }
}
