package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** relocate-fix: a real builder (HallUpgradeGoal on a live resident, walking, climbing the scaffold columns) moves a village-style building
 *  (AD-129): it takes the standing building apart (phase 1) and builds it again at the new place (phase 2) to the end. The run fails as soon as
 *  no operation gets done for two game minutes, with where the builder hangs and what it is trying to do. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class StyleBuilderGameTests {
 /** No operation done for this long is a stall (a deferred operation waits 2400 ticks before it is retried). */
 static final int STALL=3000;
 private StyleBuilderGameTests(){}
 private static final Set<String> NOT_MOVABLE=Set.of("livestock","wall_tower","quarry");
 /** The tallest movable design at level VI (castle previews are gallery-only and are not ordered). */
 static String tallestAtSix(){
  String best=null;int top=-1;
  for(var type:BuildingOrders.ORDERABLE){if(NOT_MOVABLE.contains(type)||!BuildingTiers.upgradable(type))continue;
   int y=BuildingPlacement.layout(BuildingTiers.layoutId(type,6),BlockPos.ZERO,0).entrySet().stream().filter(c->!c.getValue().isAir()).mapToInt(c->c.getKey().getY()).max().orElse(0);
   if(y>top||y==top&&type.compareTo(best)<0){top=y;best=type;}}
  return best;
 }
 @GameTest(template="empty",batch="style_builder",timeoutTicks=144000) public static void builderMovesStyleHome(GameTestHelper h){move(h,"home",1);}
 @GameTest(template="empty",batch="style_builder",timeoutTicks=144000) public static void builderMovesStyleHome2(GameTestHelper h){move(h,"home_2",1);}
 @GameTest(template="empty",batch="style_builder",timeoutTicks=144000) public static void builderMovesStyleSmithy(GameTestHelper h){move(h,"smithy",1);}
 @GameTest(template="empty",batch="style_builder",timeoutTicks=144000) public static void builderMovesStyleBakery(GameTestHelper h){move(h,"restaurant",1);}
 @GameTest(template="empty",batch="style_builder",timeoutTicks=144000) public static void builderMovesStyleMill(GameTestHelper h){move(h,"mill",1);}
 @GameTest(template="empty",batch="style_builder",timeoutTicks=144000) public static void builderMovesStyleWarehouse(GameTestHelper h){move(h,"warehouse",1);}
 /** AD-147: the level-VI warehouse — the castle store of 23x17 with its round tower and cone — is moved and rebuilt by a live builder. Some 4450 operations
  *  (twice the tallest level-VI design's): ~215000 ticks, so it has a longer limit than the rest of the batch. */
 @GameTest(template="empty",batch="style_builder",timeoutTicks=360000) public static void builderMovesStyleWarehouseSix(GameTestHelper h){move(h,"warehouse",6);}
 @GameTest(template="empty",batch="style_builder",timeoutTicks=144000) public static void builderMovesTallestLevelSix(GameTestHelper h){move(h,tallestAtSix(),6);}
 /** A move that also turns the building a quarter: the mayor's own relocation offers turned spots, and the probe takes one (AD-125). */
 @GameTest(template="empty",batch="style_builder",timeoutTicks=144000) public static void builderMovesStyleHomeTurned(GameTestHelper h){move(h,"home",1,1);}

 /** The batch counts ticks only: while it runs, the GameTest server does not wait between ticks (server/ProbeWarp), which takes a run of
  *  ~80000 ticks from over an hour to a few minutes. Tick counts, and so every result, stay the same. */
 @BeforeBatch(batch="style_builder") public static void fast(ServerLevel l){org.villageastra.server.ProbeWarp.batchWarp=0;}
 @AfterBatch(batch="style_builder") public static void normal(ServerLevel l){org.villageastra.server.ProbeWarp.batchWarp=1;}
 /** Dev run {@code runGameTestServer -PstyleAll -PgtOnly=style_all}: every movable design at level I and, where it has levels, at level VI,
  *  nine to a batch. Not part of the regression (the seven above are). */
 @GameTestGenerator public static Collection<TestFunction> allDesigns(){
  if(!Boolean.getBoolean("villageastra.styleAll"))return List.of();
  var out=new ArrayList<TestFunction>();
  for(var type:Relocations.MOVABLE)for(int level:new int[]{1,6}){if(level==6&&!BuildingTiers.upgradable(type))continue;final int lv=level;
   out.add(new TestFunction("style_all_"+out.size()/9,"style_all_"+type+"_"+level,VillageAstra.ID+":empty",144000,0,true,h->move(h,type,lv)));}
  return out;
 }
 private static void move(GameTestHelper h,String type,int level){move(h,type,level,0);}
 /** {@code turn}: the quarter turns the building is given at the new place (0 = the same way round as it stands). */
 private static void move(GameTestHelper h,String type,int level,int turn){
  // Tick-counted only: the server need not wait between ticks while these run (also for the dev batches, which have no BeforeBatch).
  org.villageastra.server.ProbeWarp.batchWarp=0;
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var turned=BuildingPlacement.size(type,turn);var plain=BuildingPlacement.size(type,0);
  var size=new int[]{Math.max(plain[0],turned[0]),Math.max(plain[1],turned[1])};int shift=size[1]+6;
  for(int x=-2;x<=14+size[0]+4;x++)for(int z=-2;z<=shift+size[1]+4;z++){for(int y=-3;y<0;y++)l.setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   // Walls and a glass roof round the patch: the GameTest level is shared by every run of the session, and water, lava and gravel left
   // round the test area by other tests ran onto the new lot during the hour of game time a move takes (the builder rightly refused to
   // build over them).
   boolean rim=x==-2||z==-2||x==14+size[0]+4||z==shift+size[1]+4;
   for(int y=1;y<=28;y++)l.setBlock(center.offset(x,y,z),(rim?Blocks.STONE_BRICKS:y==28?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  // A reserve in the hall for top-ups: work taken out of turn (handy work, a deferred cell) can run the cargo short of scaffolds for a while.
  if(l.getBlockEntity(center.offset(1,1,4)) instanceof net.minecraft.world.Container chest){chest.setItem(0,new ItemStack(VillageAstra.TIMBER_SCAFFOLD.get(),64));chest.setItem(1,new ItemStack(VillageAstra.TIMBER_SCAFFOLD.get(),64));chest.setItem(2,new ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));}
  // AD-125 permits inaccessible old cells to remain. This movement fixture has no
  // production workers: supply finite replacement stock before work begins, so the
  // builder can perform the real top-up instead of waiting forever for a nonexistent workshop.
  if(type.equals("warehouse")&&level==6&&l.getBlockEntity(center.offset(1,1,4)) instanceof net.minecraft.world.Container chest){
   chest.setItem(3,new ItemStack(net.minecraft.world.item.Items.DEEPSLATE_TILE_STAIRS,64));
   chest.setItem(4,new ItemStack(net.minecraft.world.item.Items.SPRUCE_STAIRS,64));
  }
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  // The lots lie in the ground, as a survey on flat ground lays them (a lot standing a block proud of its surroundings is a village
  // matter: an outside scaffold column would then have no ground under its foot, and the survey refuses the site).
  var b=new Settlement.Building(UUID.randomUUID(),type,12,0,0,0,level);s.addBuilding(b);
  var from=BuildingPlacement.origin(e,b);var to=from.offset(0,0,shift);
  for(var cell:BuildingPlacement.layout(BuildingTiers.layoutId(type,level),from,0).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  // The builder's own home is a record only (no blocks), unless the moved building is a house: then he lives in it.
  UUID home;if(BuildingOrders.HOUSING.contains(type)){s.addHome(new Settlement.Home(b.id(),1,BuildingOrders.capacity(type),true));home=b.id();}
  else{var bed=new Settlement.Building(Settlement.childId(s.id(),"building/builder-home"),"home",-60,0,-60);s.addBuilding(bed);s.addHome(new Settlement.Home(bed.id(),1,2,true));home=bed.id();}
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());
  var why=Relocations.order(l,e,from,to,turn);
  if(!why.isEmpty()){SettlementData.get(l.getServer()).remove(s.id());throw new GameTestAssertException(type+"@"+level+": the move is not ordered: "+why);}
  // The hall gives the net cost at once (the funding walk is covered by HallFundingGameTests); the rest is the builder's own work.
  var state=HallUpgradeGoal.inspect(l,s.id());var cost=state.getCompound("cost");
  for(var k:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(k));int need=cost.getInt(k);
   while(need>0){int n=Math.min(need,item.getMaxStackSize());state.getList("cargo",Tag.TAG_COMPOUND).add(new ItemStack(item,n).save(new CompoundTag()));need-=n;}}
  state.putBoolean("funded",true);HallUpgradeGoal.store(l,s.id(),state);
  int total=state.getList("ops",Tag.TAG_COMPOUND).size();
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));var at=from.offset(-2,1,-2);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
  var goal=new HallUpgradeGoal(npc,true);npc.onlyGoals(g->g instanceof FloatGoal||g instanceof ResidentDoorGoal||g instanceof DoorwayGoal,5,goal);l.addFreshEntity(npc);
  var log=com.mojang.logging.LogUtils.getLogger();String name=type+"@"+level+(turn==0?"":" turn "+turn);
  int top=BuildingPlacement.layout(BuildingTiers.layoutId(type,level),BlockPos.ZERO,0).entrySet().stream().filter(c->!c.getValue().isAir()).mapToInt(c->c.getKey().getY()).max().orElse(0);
  if(top>=26)throw new GameTestAssertException(name+" is "+top+" high: the fixture's glass roof at 28 is too low for it");
  log.info("ASTRA_STYLE_BUILD {} start ops={} top={} from={} to={}",name,total,top,from.toShortString(),to.toShortString());
  int[] seen={-1,0,0};long start=l.getGameTime();boolean[] finished={false};var trace=new ArrayList<String>();var ring=new ArrayDeque<String>();int[] skips={0};
  h.onEachTick(()->{
   long t=l.getGameTime()-start;
   // The last forty ticks before a stall are traced one by one: height, status, sneaking, the goals that run.
   if(t-seen[1]>STALL-40&&trace.size()<40)trace.add(String.format(Locale.ROOT,"%.2f/%s/%s/%s/%s",npc.getY()-from.getY(),npc.workStatus(),npc.isShiftKeyDown()?"S":"-",npc.onGround()?"G":"-",npc.runningGoals()));
   ring.addLast(String.format(Locale.ROOT,"%.2f,%.2f,%.2f/%s/%s%s",npc.getX()-from.getX(),npc.getY()-from.getY(),npc.getZ()-from.getZ(),npc.workStatus(),npc.onClimbable()?"C":"-",npc.onGround()?"G":"-"));if(ring.size()>80)ring.removeFirst();
   if(t%100!=0)return;
   var now=HallUpgradeGoal.inspect(l,s.id());int done=0;for(var raw:now.getList("ops",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getBoolean("done"))done++;
   // Carrying the leftovers back and fetching a top-up are progress too.
   int moved=done+now.getInt("returns")+now.getInt("withdrawals");
   if(moved!=seen[0]){seen[0]=moved;seen[1]=(int)t;trace.clear();}
   if(now.getBoolean("complete")){finished[0]=true;return;}
   // A cell given up: the last four seconds of the builder, for the log.
   int sk=0;for(var raw:now.getList("ops",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getBoolean("skipped"))sk++;
   if(sk>skips[0]){skips[0]=sk;log.info("ASTRA_STYLE_BUILD {} GAVE_UP t={} skipped={} op={} ring={}",name,t,skipped(now),goal.opDiag,ring);}
   if(t%2400==0)log.info("ASTRA_STYLE_BUILD {} t={} done={}/{} deferrals={} status={} at={}",name,t,done,total,now.getInt("deferrals"),npc.workStatus(),npc.blockPosition().subtract(from).toShortString());
   if(!now.getBoolean("complete")&&t-seen[1]>STALL){
    String where=String.format(Locale.ROOT,"%.2f %.2f %.2f",npc.getX()-from.getX(),npc.getY()-from.getY(),npc.getZ()-from.getZ());
    log.info("ASTRA_STYLE_BUILD {} STALLED t={} done={}/{} status={} at(from)={} lastOp={} lastStand={} walk={} column={} next={} cost={} skipped={} around={} trace={} ring={} floor={} path={}",name,t,done,total,npc.workStatus(),where,goal.opDiag,goal.standDiag,goal.walkDiag,column(l,npc.blockPosition(),from),next(l,now,from),now.getCompound("cost"),skipped(now),around(l,center,14+size[0]+4,shift+size[1]+4),trace,ring,floor(l,from,size,npc),path(npc,from));
    cleanup(l,s,npc);
    throw new GameTestAssertException(name+" stalls at "+done+"/"+total+" after "+t+" ticks: status="+npc.workStatus()+" at "+where+" (from the old origin) op "+goal.opDiag+" stand "+goal.standDiag+" skipped "+skipped(now));}
  });
  h.succeedWhen(()->{
   h.assertTrue(finished[0],name+" not finished yet");var now=HallUpgradeGoal.inspect(l,s.id());
   var moved=s.buildings().stream().filter(x->x.id().equals(b.id())).findFirst().orElseThrow();var offset=to.subtract(center);
   h.assertTrue(moved.x()==offset.getX()&&moved.z()==offset.getZ(),name+" is registered at its new place: "+moved);
   int missing=0;for(var cell:BuildingPlacement.layout(BuildingTiers.layoutId(type,level),to,turn).entrySet())if(!cell.getValue().isAir()&&!BuildingRepairs.present(l.getBlockState(cell.getKey()),BuildingOrders.payable(cell.getValue()))&&!(cell.getValue().getBlock() instanceof BuildingCoreBlock))missing++;
   h.assertTrue(missing==0,name+": "+missing+" cells of the design are missing at the new place");
   var scaffolds=new ArrayList<String>();for(var p:BlockPos.betweenClosed(center.offset(-2,0,-2),center.offset(14+size[0]+4,27,shift+size[1]+4)))if(l.getBlockState(p).is(VillageAstra.TIMBER_SCAFFOLD.get()))scaffolds.add(p.subtract(from).toShortString());
   h.assertTrue(scaffolds.isEmpty(),name+": "+scaffolds.size()+" scaffold cells are left standing (from the old origin): "+scaffolds.subList(0,Math.min(8,scaffolds.size()))+" skipped="+skipped(now));
   log.info("ASTRA_STYLE_BUILD {} VERIFIED ops={} ticks={} deferrals={}",name,total,l.getGameTime()-start,now.getInt("deferrals"));
   cleanup(l,s,npc);
  });
 }
 /** A plan of the old lot's ground storey at a stall, row by row (z) from the old origin: '#' a block the worker bumps into at feet or head
  *  height, 'S' a scaffold, 'B' the worker, '.' free. */
 private static String floor(ServerLevel l,BlockPos from,int[] size,ResidentEntity npc){var out=new StringBuilder();var at=npc.blockPosition();
  for(int z=-2;z<=size[1]+1;z++){out.append(" /").append(z).append(':');
   for(int x=-2;x<=size[0]+1;x++){var p=from.offset(x,1,z);char c='.';
    for(var q:List.of(p,p.above())){var st=l.getBlockState(q);if(st.is(VillageAstra.TIMBER_SCAFFOLD.get()))c='S';else if(c=='.'&&!st.getCollisionShape(l,q).isEmpty())c='#';}
    if(at.getX()==p.getX()&&at.getZ()==p.getZ())c='B';out.append(c);}}
  return out.toString();}
 /** The route the worker follows at a stall, node by node from the old origin, the next node marked. */
 private static String path(ResidentEntity npc,BlockPos from){var route=npc.getNavigation().getPath();if(route==null)return "none";var out=new StringBuilder();
  for(int i=0;i<route.getNodeCount();i++){var n=route.getNode(i);out.append(i==route.getNextNodeIndex()?" >":" ").append(n.asBlockPos().subtract(from).toShortString()).append(':').append(n.type);}
  return out+" reach="+route.canReach();}
 /** The blocks of the worker's own column, from the floor up, for the stall message. */
 private static String column(ServerLevel l,BlockPos at,BlockPos from){var out=new StringBuilder();
  for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){out.append(" [").append(at.getX()+dx-from.getX()).append(',').append(at.getZ()+dz-from.getZ()).append("]");
   for(int y=0;y<=14;y++){var st=l.getBlockState(new BlockPos(at.getX()+dx,from.getY()+y,at.getZ()+dz));if(!st.isAir())out.append(' ').append(y).append('=').append(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath());}}
  return out.toString();}
 /** What stands round the fixture (outside its rim, and over it): something ran water and gravel onto the lots. */
 private static String around(ServerLevel l,BlockPos center,int maxX,int maxZ){var count=new TreeMap<String,Integer>();
  for(int x=-5;x<=maxX+3;x++)for(int z=-5;z<=maxZ+3;z++)for(int y=-4;y<=40;y++){boolean inside=x>=-2&&x<=maxX&&z>=-2&&z<=maxZ&&y<=29;if(inside)continue;
   var st=l.getBlockState(center.offset(x,y,z));if(!st.isAir())count.merge(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath()+(y>29?"^":""),1,Integer::sum);}
  var gravel=new ArrayList<String>();for(int x=-2;x<=maxX;x++)for(int z=-2;z<=maxZ;z++)for(int y=1;y<=29;y++){var st=l.getBlockState(center.offset(x,y,z));if(st.is(Blocks.GRAVEL)||st.is(Blocks.WATER))gravel.add(x+","+y+","+z);}
  return count+" gravel/water inside (from centre) "+gravel.subList(0,Math.min(12,gravel.size()));}
 /** Operations the move gave up on (skipped), for the failure message. */
 private static String skipped(CompoundTag state){var out=new ArrayList<String>();
  for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.getBoolean("skipped"))out.add(BlockPos.of(t.getLong("pos")).toShortString()+" "+BuiltInRegistries.BLOCK.getKey(HallConstructionPlan.step(t).before().getBlock()).getPath()
   +(t.contains("stand")?" stand "+BlockPos.of(t.getLong("stand")).toShortString():"")+" why "+t.getString("why"));}
  return out.size()+" "+out.subList(0,Math.min(8,out.size()));}
 /** The first unfinished operations, relative to the old origin. */
 private static String next(ServerLevel l,CompoundTag state,BlockPos from){var out=new StringBuilder();int n=0;
  for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.getBoolean("done"))continue;var step=HallConstructionPlan.step(t);
   out.append(" | ").append(step.pos().subtract(from).toShortString()).append(' ').append(BuiltInRegistries.BLOCK.getKey(step.before().getBlock()).getPath()).append("->").append(BuiltInRegistries.BLOCK.getKey(step.after().getBlock()).getPath());
   var there=l.getBlockState(step.pos());if(!there.equals(step.before()))out.append(" now ").append(there);
   if(t.contains("stand"))out.append(" stand ").append(BlockPos.of(t.getLong("stand")).subtract(from).toShortString());if(t.getInt("deferred")>0)out.append(" d").append(t.getInt("deferred")).append(' ').append(t.getString("why"));
   if(++n>=12)break;}
  return out.toString();}
 private static void cleanup(ServerLevel l,Settlement s,ResidentEntity npc){
  npc.discard();SettlementData.get(l.getServer()).remove(s.id());Relocations.forget(s.id());HallUpgradeGoal.drop(l,s.id());
 }
}
