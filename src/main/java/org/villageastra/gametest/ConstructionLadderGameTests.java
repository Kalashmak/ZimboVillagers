package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-153: the owner's construction ladder — builders of the hall by research (1, then 2/2/4/6/8/10), the builder's bag (II, x3), the reach
 *  (IV x2, VI x3), the wolf that fetches road materials (V), and a crew that shares one project without two builders on one block. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ConstructionLadderGameTests {
 private static void learn(ServerLevel l,SettlementData.Entry e,String... nodes){var record=BookResearch.inspect(l,e);var done=record.getList("legacyDone",Tag.TAG_STRING);for(var n:nodes)done.add(StringTag.valueOf(n));record.put("legacyDone",done);BookResearch.store(l,e,record);ResearchKnobs.forget(e.settlement().id());}
 private static void upTo(ServerLevel l,SettlementData.Entry e,int rung){for(int t=1;t<=rung;t++)learn(l,e,"construction."+t);}
 private static long builders(Settlement s){return s.residents().stream().filter(r->r.alive()&&r.profession()==Profession.BUILDER).count();}
 private static boolean says(String id,String key){return ResearchEffects.describe(id).stream().anyMatch(c->c.getContents() instanceof TranslatableContents t&&t.getKey().endsWith(key));}
 /** The labour office posts as many builders as the construction research allows, the later ones after the first posts are filled. */
 @GameTest(template="empty",timeoutTicks=100) public static void builderSlotsFollowConstruction(GameTestHelper h){
  var l=h.getLevel();var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var home=Settlement.childId(s.id(),"home");s.addHome(new Settlement.Home(home,1,16,true));
  for(int i=0;i<12;i++)s.admit(new Resident(Settlement.childId(s.id(),"adult/"+i),Resident.Life.ADULT,true,null,null,-1),home);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(4,3,4)));SettlementData.get(l.getServer()).add(e);
  try{
   int[] want={1,2,2,4,6,8,10};
   for(int rung=0;rung<=6;rung++){if(rung>0)learn(l,e,"construction."+rung);
    h.assertTrue(ResearchKnobs.builders(l,e)==want[rung],"Construction "+rung+": "+want[rung]+" builders allowed, got "+ResearchKnobs.builders(l,e));
    Population.assign(e,ResearchKnobs.builders(l,e));h.assertTrue(builders(s)==want[rung],"Construction "+rung+": the hall posts "+want[rung]+" builders: "+builders(s));}
   h.assertTrue(s.residents().stream().filter(r->r.profession()==Profession.PORTER).count()==1,"The porter's post came before the extra builders");
   h.assertTrue(ResearchKnobs.bagAt(1)==1&&ResearchKnobs.bagAt(2)==3&&ResearchKnobs.reachAt(3)==1&&ResearchKnobs.reachAt(4)==2&&ResearchKnobs.reachAt(5)==2&&ResearchKnobs.reachAt(6)==3
     &&!ResearchKnobs.builderWolfAt(4)&&ResearchKnobs.builderWolfAt(5),"Bag x3 from II, reach x2 from IV and x3 at VI, the wolf from V");
   h.assertTrue(says("construction.1","knob.builders")&&says("construction.2","knob.bag")&&says("construction.4","knob.reach")&&says("construction.5","knob.builder_wolf")&&says("construction.6","knob.reach"),"Each card names what its rung does");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building hall,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  for(int x=-4;x<40;x++)for(int z=-4;z<16;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Town(l,s,e,hall,center);
 }
 private static CompoundTag road(Town t,int from,int length){var cells=new LinkedHashMap<BlockPos,BlockState>();for(int x=from;x<from+length;x++)cells.put(t.center.offset(x,0,13),Blocks.DIRT_PATH.defaultBlockState());
  var project=Roads.plan(t.l,t.e,List.copyOf(cells.keySet()),cells,2,false);Roads.order(t.l,t.e,project);return Roads.project(t.l,t.s.id());}
 private static int carried(CompoundTag project){int n=0;for(var raw:project.getList("cargo",Tag.TAG_COMPOUND))n+=ItemStack.of((CompoundTag)raw).getCount();return n;}
 /** Construction II: the builder's bag takes three times the plain road load from the hall chest. */
 @GameTest(template="empty",timeoutTicks=100) public static void builderBagCarriesThreeTimes(GameTestHelper h){
  var t=town(h);
  try{
   var project=road(t,-3,40);var chest=LogisticsRoutes.chest(t.l,t.e,t.hall);chest.setItem(0,new ItemStack(Items.COBBLESTONE,64));
   h.assertTrue(ResearchKnobs.bag(t.l,t.e)==1&&Roads.load(t.l,t.e,t.hall,project)==Roads.CARRY&&carried(project)==Roads.CARRY,"The plain builder carries "+Roads.CARRY+": "+carried(project));
   upTo(t.l,t.e,2);
   int more=Roads.load(t.l,t.e,t.hall,project);
   h.assertTrue(ResearchKnobs.bag(t.l,t.e)==3&&carried(project)==40&&more==40-Roads.CARRY,"With the bag he takes all 40 the road needs at once (up to "+3*Roads.CARRY+"): "+carried(project));
  }finally{SettlementData.get(t.l.getServer()).remove(t.s.id());}
  h.succeed();
 }
 /** Construction V: a kennel wolf runs to the hall chest, the load is taken there, and it brings it to the builder, who never walks back. */
 @GameTest(template="empty",timeoutTicks=100) public static void builderWolfFetchesRoadMaterials(GameTestHelper h){
  var t=town(h);var dog=UUID.randomUUID();var sent=new HashMap<UUID,BlockPos>();var released=new ArrayList<UUID>();var builder=UUID.randomUUID();
  VillageDogs.provide(new VillageDogs.Source(){
   @Override public List<UUID> free(ServerLevel l,SettlementData.Entry e){return sent.containsKey(dog)?List.of():List.of(dog);}
   @Override public boolean send(ServerLevel l,SettlementData.Entry e,UUID d,BlockPos to){sent.put(d,to);return true;}
   @Override public boolean near(ServerLevel l,SettlementData.Entry e,UUID d,BlockPos at,double r){var p=sent.get(d);return p!=null&&p.distSqr(at)<=r*r;}
   @Override public void release(ServerLevel l,SettlementData.Entry e,UUID d){sent.remove(d);released.add(d);}
   @Override public boolean pulls(){return true;}});
  try{
   var project=road(t,30,3);var chest=LogisticsRoutes.chest(t.l,t.e,t.hall);chest.setItem(0,new ItemStack(Items.COBBLESTONE,3));var at=t.center.offset(30,1,12);
   upTo(t.l,t.e,4);
   h.assertTrue(BuilderWolf.step(t.l,t.e,t.hall,project,builder,at,true).isEmpty()&&sent.isEmpty(),"Before Construction V the builder walks himself");
   learn(t.l,t.e,"construction.5");
   h.assertTrue(BuilderWolf.step(t.l,t.e,t.hall,project,builder,t.center.offset(3,1,4),true).isEmpty()&&sent.isEmpty(),"Beside the hall he fetches it himself");
   var chestPos=LogisticsRoutes.position(t.e,t.hall);
   h.assertTrue(BuilderWolf.step(t.l,t.e,t.hall,project,builder,at,true).equals("road_wolf_fetching")&&chestPos.equals(sent.get(dog)),"The wolf is sent to the hall chest");
   h.assertTrue(BuilderWolf.step(t.l,t.e,t.hall,project,builder,at,true).equals("road_wolf_bringing")&&carried(project)==3&&chest.countItem(Items.COBBLESTONE)==0&&at.equals(sent.get(dog)),
     "At the chest the load is taken and the wolf runs back to the builder: carried "+carried(project));
   h.assertTrue(BuilderWolf.step(t.l,t.e,t.hall,project,builder,at,false).isEmpty()&&released.contains(dog)&&BuilderWolf.dog(builder)==null,"The wolf brings it and is let go");
  }finally{VillageDogs.provide(VillageWolves.DOGS);BuilderWolf.clear();SettlementData.get(t.l.getServer()).remove(t.s.id());}
  h.succeed();
 }
 private static long scaffolds(CompoundTag state){return state.getList("ops",Tag.TAG_COMPOUND).stream().filter(x->HallConstructionPlan.step((CompoundTag)x).after().is(VillageAstra.TIMBER_SCAFFOLD.get())).count();}
 /** Construction IV/VI: a longer reach leaves the plan fewer scaffold columns — a cell the plain builder works from a column is worked from the ground. */
 @GameTest(template="empty",timeoutTicks=200) public static void longerReachNeedsFewerScaffolds(GameTestHelper h){
  var l=h.getLevel();var site=h.absolutePos(new BlockPos(4,3,4));var center=h.absolutePos(new BlockPos(40,3,30));
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(int x=-1;x<16;x++)for(int z=-1;z<16;z++){for(int y=-3;y<0;y++)l.setBlock(site.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(site.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=20;y++)l.setBlock(site.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  try{
   var plain=BuildingOrders.survey(l,e,"home_2",0,site);h.assertTrue(plain.ok(),"The two-storey house is orderable: "+plain.reason()+" "+plain.conflicts());
   upTo(l,e,4);var twice=BuildingOrders.survey(l,e,"home_2",0,site);
   upTo(l,e,6);var thrice=BuildingOrders.survey(l,e,"home_2",0,site);
   long a=scaffolds(plain.state()),b=scaffolds(twice.state()),c=scaffolds(thrice.state());
   h.assertTrue(twice.ok()&&thrice.ok()&&a>0&&b<a&&c<=b,"Scaffold columns: plain "+a+", x2 "+b+", x3 "+c);
   var free=new HashSet<Long>();for(var raw:twice.state().getList("ops",Tag.TAG_COMPOUND)){var op=(CompoundTag)raw;if(!op.contains("stand"))free.add(op.getLong("pos"));}
   long moved=plain.state().getList("ops",Tag.TAG_COMPOUND).stream().filter(x->((CompoundTag)x).contains("stand")&&free.contains(((CompoundTag)x).getLong("pos"))).count();
   h.assertTrue(moved>0,"Cells the plain builder sets from a column are set from the ground at x2: "+moved);
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 /** Construction IV: two builders of the hall build one project together — each block by one of them, never both on the same one — and the
  *  far cell ten blocks up is set from the ground with no scaffold. */
 @GameTest(template="empty",timeoutTicks=4000) public static void twoBuildersShareAProjectWithoutDoublePlacing(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var bed=new Settlement.Building(Settlement.childId(s.id(),"building/builder-home"),"home",-60,0,-60);s.addBuilding(bed);s.addHome(new Settlement.Home(bed.id(),1,4,true));
  for(int x=-2;x<=24;x++)for(int z=-2;z<=24;z++){for(int y=-3;y<0;y++)l.setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=14;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  upTo(l,e,4);
  var site=center.offset(10,0,10);var ops=new ListTag();var far=site.offset(1,10,1);var cells=new ArrayList<BlockPos>();
  for(int x=0;x<4;x++)for(int z=0;z<4;z++)cells.add(site.offset(x,1,z));cells.add(far);
  for(var p:cells){var op=new CompoundTag();op.putLong("pos",p.asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(Blocks.COBBLESTONE.defaultBlockState()));op.putString("item","minecraft:cobblestone");ops.add(op);}
  var state=new CompoundTag();var id=UUID.randomUUID();state.putInt("schema",2);state.putString("kind","building");state.putUUID("id",id);state.putUUID("project",id);state.putString("design","home");
  state.putLong("origin",site.asLong());state.putBoolean("noHatch",true);state.putLong("hatch",site.offset(-100,0,-100).asLong());state.putInt("level",0);state.put("ops",ops);
  var cost=new CompoundTag();cost.putInt("minecraft:cobblestone",cells.size());state.put("cost",cost);var cargo=new ListTag();cargo.add(new ItemStack(Items.COBBLESTONE,cells.size()).save(new CompoundTag()));state.put("cargo",cargo);state.putBoolean("funded",true);
  HallUpgradeGoal.store(l,s.id(),state);
  var npcs=new ArrayList<ResidentEntity>();var goals=new ArrayList<HallUpgradeGoal>();
  for(int i=0;i<2;i++){var r=new Resident(Settlement.childId(s.id(),"builder/"+i),Resident.Life.ADULT,true,null,null,-1);s.admit(r,bed.id());s.assign(r.id(),Profession.BUILDER,hall.id());
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));var at=site.offset(-3+9*i,1,-3);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
   var goal=new HallUpgradeGoal(npc,true);npc.onlyGoals(g->g instanceof FloatGoal||g instanceof ResidentDoorGoal||g instanceof DoorwayGoal,5,goal);l.addFreshEntity(npc);npcs.add(npc);goals.add(goal);}
  boolean[] clash={false};String[] seen={""};
  h.onEachTick(()->{
   var now=HallUpgradeGoal.inspect(l,s.id()).getList("ops",Tag.TAG_COMPOUND);int a=goals.get(0).working,b=goals.get(1).working;
   if(a>=0&&a==b&&!now.getCompound(a).getBoolean("done")&&HallUpgradeGoal.claimant(id,a,l.getGameTime())!=null){clash[0]=true;seen[0]="both on op "+a+" at "+l.getGameTime();}
  });
  h.succeedWhen(()->{
   var now=HallUpgradeGoal.inspect(l,s.id());var list=now.getList("ops",Tag.TAG_COMPOUND);var by=new HashSet<UUID>();int done=0;
   for(var raw:list){var op=(CompoundTag)raw;if(op.getBoolean("done")){done++;if(op.hasUUID("by"))by.add(op.getUUID("by"));}}
   h.assertTrue(!clash[0],"Two builders never work the same block: "+seen[0]);
   h.assertTrue(done==list.size(),"All "+list.size()+" blocks set: "+done+" status "+npcs.get(0).workStatus()+"/"+npcs.get(1).workStatus());
   h.assertTrue(by.size()==2,"Both builders set blocks: "+by.size());
   h.assertTrue(l.getBlockState(far).is(Blocks.COBBLESTONE)&&carried(now)==0,"The far cell ten blocks up is set from the ground and every stone is spent once");
   for(var p:cells)h.assertTrue(l.getBlockState(p).is(Blocks.COBBLESTONE),"Set: "+p.toShortString());
   for(var n:npcs)n.discard();SettlementData.get(l.getServer()).remove(s.id());HallUpgradeGoal.drop(l,s.id());
  });
 }
}
