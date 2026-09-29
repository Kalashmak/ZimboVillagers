package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-127: the wall fitted to the village — round, clear of every building by its margin, opened where roads cross, pushed out past a
 *  pond, never over a building, grown with the village (new stretch first, the old one taken down and its stone back in the hall), and
 *  laid whole through a tree. Each test has its own far meadow (z −6000, slots from 20), apart from the AD-094 meadows. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WallRingGameTests {
 private static final int REACH=92;
 private record Site(ServerLevel l,SettlementData.Entry e,Settlement s,BlockPos center,List<net.minecraft.world.level.ChunkPos> chunks){}
 private static void meadowColumn(ServerLevel l,BlockPos ground){
  int top=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,ground.getX(),ground.getZ());
  l.setBlock(ground.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(ground,Blocks.GRASS_BLOCK.defaultBlockState(),2);
  for(int y=ground.getY()+1;y<top;y++)l.setBlock(new BlockPos(ground.getX(),y,ground.getZ()),Blocks.AIR.defaultBlockState(),2);
 }
 private static Site site(GameTestHelper h,int slot){
  var l=h.getLevel();var far=new BlockPos(slot*256,0,-6000);var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int cx=(far.getX()-REACH)>>4;cx<=(far.getX()+REACH)>>4;cx++)for(int cz=(far.getZ()-REACH)>>4;cz<=(far.getZ()+REACH)>>4;cz++){
   l.setChunkForced(cx,cz,true);l.getChunk(cx,cz);chunks.add(new net.minecraft.world.level.ChunkPos(cx,cz));}
  var center=new BlockPos(far.getX(),l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,far.getX(),far.getZ())-1,far.getZ());
  for(int x=-REACH;x<=REACH;x++)for(int z=-REACH;z<=REACH;z++)meadowColumn(l,center.offset(x,0,z));
  var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Site(l,e,s,center,chunks);
 }
 private static void done(Site t){
  HallUpgradeGoal.drop(t.l,t.s.id());SettlementData.get(t.l.getServer()).remove(t.s.id());Walls.forget();
  for(var c:t.chunks)t.l.setChunkForced(c.x,c.z,false);
 }
 /** AD-157: the AD-127 look (a cobblestone plinth, stone brick, brick crenels) is the wall of Defence IV. */
 private static final String[] STONE={"defense.1","defense.2","defense.3","defense.4"};
 private static void research(Site t,String... nodes){
  var record=BookResearch.inspect(t.l,t.e);var done=record.getList("legacyDone",Tag.TAG_STRING);for(var n:nodes)done.add(StringTag.valueOf(n));
  record.put("legacyDone",done);BookResearch.store(t.l,t.e,record);ResearchKnobs.forget(t.s.id());
 }
 private static void home(Site t,String name,int x,int z){t.s.addBuilding(new Settlement.Building(Settlement.childId(t.s.id(),"building/"+name),"home",x,0,z));}
 /** An L of houses round the hall: two to the east, two to the south. */
 private static void village(Site t){home(t,"e1",24,0);home(t,"e2",40,0);home(t,"s1",0,24);home(t,"s2",0,40);}
 private static long key(int x,int z){return BlockPos.asLong(x,0,z);}
 /** The inside of the ring flooded from the hall side by side; null when the flood gets out. */
 private static Set<Long> inside(Site t,List<BlockPos> ring){
  var wall=new HashSet<Long>();for(var p:ring)wall.add(key(p.getX(),p.getZ()));var c=t.center;int edge=REACH+4;
  var seen=new HashSet<Long>();var queue=new ArrayDeque<int[]>();queue.add(new int[]{c.getX(),c.getZ()});seen.add(key(c.getX(),c.getZ()));
  while(!queue.isEmpty()){var at=queue.poll();if(Math.abs(at[0]-c.getX())>=edge||Math.abs(at[1]-c.getZ())>=edge)return null;
   for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int x=at[0]+d[0],z=at[1]+d[1];long k=key(x,z);if(wall.contains(k)||!seen.add(k))continue;queue.add(new int[]{x,z});}}
  return seen;
 }
 /** Every column of every building's growth plot lies inside the ring and keeps the margin from it. */
 private static String clearance(Site t,List<BlockPos> ring){
  var in=inside(t,ring);if(in==null)return "the ring is open";
  for(var b:t.s.buildings()){if(b.type().equals(Walls.TOWER))continue;var box=GrowthPlots.box(b.type(),BuildingPlacement.origin(t.e,b),b.rotation());
   for(var p:WallOutline.perimeter(box.west(),box.north(),box.east(),box.south())){
    if(!in.contains(key(p[0],p[1])))return b.type()+" at "+b.x()+","+b.z()+" sticks out at "+p[0]+","+p[1];
    for(var c:ring)if(Math.hypot(c.getX()-p[0],c.getZ()-p[1])<Walls.FIT.margin())return b.type()+" at "+b.x()+","+b.z()+" is closer than the margin to "+c.toShortString();}}
  return "";
 }
 /** The builders' work done at once: each op gets its item, returns go to the hall (or are dropped when there is no hall chest). */
 private static void build(Site t){
  WorldJournal.batch(t.l,()->{for(int i=0;i<40000;i++){var project=Roads.project(t.l,t.s.id());if(project==null||project.getBoolean("complete"))return null;
   var op=Roads.current(project);
   if(op!=null&&!op.getString("item").isEmpty()){var cargo=new ListTag();cargo.add(new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(op.getString("item"))),64).save(new CompoundTag()));project.put("cargo",cargo);Roads.save(t.l,t.s.id(),project);}
   var r=Roads.apply(t.l,t.e,project);
   if(r.equals("unload")){var hall=Workshops.hall(t.e);if(LogisticsRoutes.chest(t.l,t.e,hall)!=null)Roads.load(t.l,t.e,hall,project);else{project.put("returns",new ListTag());Roads.save(t.l,t.s.id(),project);}}
   if(r.equals("missing"))throw new IllegalStateException("A builder could not lay "+op);}
   throw new IllegalStateException("The wall never finished");});
 }
 private static OwnedChestEntity chest(Site t){
  var pos=LogisticsRoutes.position(t.e,Workshops.hall(t.e));t.l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  // The hall's storage, as a real hall has it: room for the old stretch's stone coming back.
  var chest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));chest.expandHall();int slot=0;
  // As much as a whole extension costs: an automatic one waits until the hall holds all of it (CF10).
  for(var item:List.of(Items.COBBLESTONE,Items.STONE_BRICKS,Items.STONE_BRICK_WALL,Items.STRIPPED_SPRUCE_LOG)){int stacks=item==Items.STONE_BRICKS?12:item==Items.COBBLESTONE?8:item==Items.STONE_BRICK_WALL?4:3;
   for(int i=0;i<stacks&&slot<chest.getContainerSize();i++)chest.setItem(slot++,new ItemStack(item,64));}
  return chest;
 }
 private static List<CompoundTag> ops(Site t){var out=new ArrayList<CompoundTag>();for(var raw:Roads.project(t.l,t.s.id()).getList("ops",Tag.TAG_COMPOUND))out.add((CompoundTag)raw);return out;}

 @GameTest(template="empty",timeoutTicks=400) public static void aFittedWallIsRoundAndClearsEveryBuilding(GameTestHelper h){
  var t=site(h,20);
  try{
   village(t);research(t,STONE);
   var plan=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   h.assertTrue(plan.reason().isEmpty()&&!plan.blocks().isEmpty(),"The wall fits round the village: "+plan.reason());
   var clear=clearance(t,plan.ring());h.assertTrue(clear.isEmpty(),"Every building keeps the margin inside the ring: "+clear);
   var ring=plan.ring().stream().map(p->new int[]{p.getX(),p.getZ()}).toList();int limit=WallOutline.straightLimit(Walls.FIT.curvature(plan.radius()));
   h.assertTrue(WallOutline.straightRun(ring)<=limit,"The ring is round, no straight stretch longer than its widest arc allows: "+WallOutline.straightRun(ring)+" > "+limit);
   h.assertTrue(plan.gates().size()==4,"Without roads it opens on its four sides: "+plan.gates().size());
   h.assertTrue(plan.towers().size()>=4,"And has its towers: "+plan.towers().size());
   h.assertTrue(plan.blocks().values().stream().anyMatch(s->s.is(Blocks.COBBLESTONE))&&plan.blocks().values().stream().anyMatch(s->s.is(Blocks.STRIPPED_SPRUCE_LOG))
     &&plan.blocks().values().stream().anyMatch(s->s.is(Blocks.STONE_BRICK_WALL)),"A plinth of cobblestone, timber pilasters and crenels");
   var dry=MapOrders.preview(t.l,t.e,MapOrders.WALL,t.center,t.center,2,Walls.FIT.headroom());
   var cells=new ArrayList<Long>();for(long c:dry.getLongArray("ringCells"))cells.add(c);
   h.assertTrue(dry.getString("reason").isEmpty()&&cells.equals(plan.ring().stream().map(BlockPos::asLong).toList())&&dry.getInt("perimeter")==plan.ring().size(),"The map draws the same ring: "+dry.getString("reason"));
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=600) public static void theFittedWallGrowsWithTheVillage(GameTestHelper h){
  var t=site(h,21);
  try{
   home(t,"e1",24,0);home(t,"s1",0,24);research(t,STONE);
   var hall=Workshops.hall(t.e);var home=new Settlement.Home(Settlement.childId(t.s.id(),"home"),1,16,true);t.s.addHome(home);
   for(int i=0;i<MayorPlanner.WALL_AT;i++){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,i%2==0,null,null,-1);t.s.admit(r,home.id());if(i==0)t.s.assign(r.id(),Profession.MAYOR,hall.id());}
   var chest=chest(t);
   var first=MayorPlanner.wall(t.l,t.e);
   h.assertTrue(first.equals("fitted")&&Roads.active(t.l,t.s.id()),"A village with houses gets a wall fitted to it: "+first);
   build(t);Walls.tick(t.l,t.e);
   var record=Walls.record(t.l.getServer(),t.s.id());var before=new HashSet<Long>();for(long c:record.getLongArray("cells"))before.add(c);
   h.assertTrue(record.getInt("schema")==2&&record.getString("shape").equals("fitted")&&record.getIntArray("radii").length==Walls.FIT.sectors(),"The village remembers its fitted wall");
   h.assertTrue(!Walls.outgrown(t.e,record),"The village it was drawn for has not outgrown it");
   int bricks=LogisticsRoutes.count(chest,s->s.is(Items.STONE_BRICKS));
   // A house goes up beyond the ring to the east.
   home(t,"far_east",record.getInt("radius"),-4);
   h.assertTrue(Walls.outgrown(t.e,record),"Now the village has outgrown its wall");
   Walls.forget();// the planner's pause between two looks at the wall has passed
   var grown=MayorPlanner.wall(t.l,t.e);
   h.assertTrue(grown.equals("extend:2"),"The mayor extends the wall: '"+grown+"' generation "+Walls.record(t.l.getServer(),t.s.id()).getInt("generation")+" active "+Roads.active(t.l,t.s.id())+(grown.isEmpty()?" follow '"+Walls.follow(t.l,t.e,true)+"'":""));
   var ops=ops(t);int firstClear=-1;for(int i=0;i<ops.size();i++)if(ops.get(i).getString("kind").equals("wall_clear")){firstClear=i;break;}
   h.assertTrue(firstClear>0&&ops.stream().limit(firstClear).allMatch(o->o.getString("kind").equals("wall")&&!before.contains(o.getLong("pos"))),"The new stretch is laid first, only where no wall stands");
   boolean topDown=true;for(int i=firstClear+1;i<ops.size();i++){BlockPos a=BlockPos.of(ops.get(i-1).getLong("pos")),b=BlockPos.of(ops.get(i).getLong("pos"));if(a.getX()==b.getX()&&a.getZ()==b.getZ()&&b.getY()>a.getY())topDown=false;}
   h.assertTrue(ops.stream().skip(firstClear).allMatch(o->o.getString("kind").equals("wall_clear"))&&topDown,"Then the old stretch comes down, each column from its top");
   var retired=ops.stream().skip(firstClear).map(o->BlockPos.of(o.getLong("pos"))).toList();
   var kept=before.stream().map(BlockPos::of).filter(p->!retired.contains(p)).findFirst().orElseThrow();
   build(t);Walls.tick(t.l,t.e);
   var after=Walls.record(t.l.getServer(),t.s.id());var cells=new HashSet<Long>();for(long c:after.getLongArray("cells"))cells.add(c);
   h.assertTrue(retired.stream().allMatch(p->t.l.getBlockState(p).isAir()&&!cells.contains(p.asLong())),"The old stretch is gone and no longer the village's");
   h.assertTrue(LogisticsRoutes.count(chest,s->s.is(Items.STONE_BRICKS))>bricks,"Its stone came back to the hall");
   h.assertTrue(Walls.palette(t.l.getBlockState(kept))&&OwnershipEvents.protectedBlock(t.l,kept),"The stretch that did not move still stands and is protected");
   h.assertTrue(clearance(t,Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom()).ring()).isEmpty(),"The new house is inside the wall");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void gatesOpenWhereRoadsCross(GameTestHelper h){
  var t=site(h,22);
  try{
   home(t,"e1",24,0);research(t,STONE);
   var plan=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   var north=plan.ring().stream().filter(p->p.getX()==t.center.getX()).min(Comparator.comparingInt(BlockPos::getZ)).orElseThrow();
   for(int x=-1;x<=1;x++)for(int dz=-4;dz<=4;dz++){var cell=new BlockPos(t.center.getX()+x,t.center.getY(),north.getZ()+dz);t.l.setBlock(cell,Blocks.COBBLESTONE.defaultBlockState(),2);Roads.register(t.l,t.s.id(),cell);}
   var se=plan.ring().stream().min(Comparator.comparingDouble(p->Math.abs(Math.atan2(p.getZ()-t.center.getZ(),p.getX()-t.center.getX())-Math.PI/4))).orElseThrow();
   for(int d=-4;d<=4;d++){var cell=new BlockPos(se.getX()+d,t.center.getY(),se.getZ()+d);t.l.setBlock(cell,Blocks.COBBLESTONE.defaultBlockState(),2);Roads.register(t.l,t.s.id(),cell);}
   var roads=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   h.assertTrue(roads.reason().isEmpty()&&roads.ring().equals(plan.ring()),"Roads do not move the ring: "+roads.reason());
   h.assertTrue(roads.gates().size()==2,"Exactly two gates, where the two roads cross: "+roads.gates().stream().map(p->p.subtract(t.center).toShortString()).toList());
   for(var column:roads.ring()){if(Roads.cell(t.l,column.atY(t.center.getY()))==null)continue;
    for(int y=1;y<=Walls.HEIGHT;y++)h.assertTrue(!roads.blocks().containsKey(column.atY(t.center.getY()+y)),"The road keeps its way at "+column.subtract(t.center).toShortString());
    h.assertTrue(roads.blocks().containsKey(column.atY(t.center.getY()+Walls.HEIGHT+1)),"Under a lintel at "+column.subtract(t.center).toShortString());}
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aFittedWallGoesRoundAPond(GameTestHelper h){
  var t=site(h,23);
  try{
   home(t,"e1",24,0);research(t,STONE);
   var plan=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   var east=plan.ring().stream().filter(p->p.getZ()==t.center.getZ()).max(Comparator.comparingInt(BlockPos::getX)).orElseThrow();
   int y=t.center.getY();
   for(int dx=-3;dx<=2;dx++)for(int dz=-3;dz<=2;dz++){var c=east.offset(dx,0,dz);t.l.setBlock(c.atY(y-2),Blocks.STONE.defaultBlockState(),2);t.l.setBlock(c.atY(y-1),Blocks.WATER.defaultBlockState(),2);t.l.setBlock(c.atY(y),Blocks.WATER.defaultBlockState(),2);}
   var pond=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   h.assertTrue(pond.reason().isEmpty()&&!pond.pushed().isEmpty(),"The ring goes round the pond: "+pond.reason()+" pushed "+pond.pushed().size());
   h.assertTrue(pond.ring().stream().noneMatch(p->t.l.getBlockState(p.atY(y)).is(Blocks.WATER)),"No column of the wall stands in the water");
   // A lake over the whole east side, wider than the ring may be pushed.
   for(int dx=-6;dx<=Walls.FIT.obstaclePush()+8;dx++)for(int dz=-30;dz<=30;dz++){var c=east.offset(dx,0,dz);if(c.getX()-t.center.getX()>=REACH)continue;
    t.l.setBlock(c.atY(y-2),Blocks.STONE.defaultBlockState(),2);t.l.setBlock(c.atY(y-1),Blocks.WATER.defaultBlockState(),2);t.l.setBlock(c.atY(y),Blocks.WATER.defaultBlockState(),2);}
   var lake=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   h.assertTrue(lake.reason().startsWith("water@"),"A lake the ring cannot go round is refused by its place: "+lake.reason());
   h.assertTrue(Walls.reasonKey(lake.reason()).equals("water_at")&&Walls.reasonArgs(lake.reason()).length==2,"And the refusal names where");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aFittedWallNeverCutsABuilding(GameTestHelper h){
  var t=site(h,24);Site other=null;
  try{
   home(t,"e1",24,0);research(t,STONE);
   var plan=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   var east=plan.ring().stream().filter(p->p.getZ()==t.center.getZ()).max(Comparator.comparingInt(BlockPos::getX)).orElseThrow();
   // A neighbour's buildings along the whole east side: the ring cannot be pushed past them.
   var s=new Settlement(UUID.randomUUID());var neighbour=new BlockPos(east.getX()+40,t.center.getY(),t.center.getZ());
   for(int i=0;i<5;i++)for(int j=-3;j<=3;j++)s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/h"+i+"_"+j),"home",-40+i*10-12,0,j*10));
   var e=new SettlementData.Entry(s,t.l.dimension().location().toString(),neighbour);SettlementData.get(t.l.getServer()).add(e);other=new Site(t.l,e,s,neighbour,List.of());
   var foreign=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   h.assertTrue(foreign.reason().startsWith("crosses@"),"A ring over another village's buildings is refused by its place: "+foreign.reason());
   SettlementData.get(t.l.getServer()).remove(s.id());other=null;
   // A house of the village right where the ring passed: the ring goes round it.
   home(t,"on_ring",east.getX()-t.center.getX()-4,-4);
   var round=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   var clear=clearance(t,round.ring());
   h.assertTrue(round.reason().isEmpty()&&clear.isEmpty(),"The ring passes outside the village's own house: "+round.reason()+" "+clear);
  }finally{if(other!=null)SettlementData.get(t.l.getServer()).remove(other.s.id());done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=600) public static void fittedWallBlocksAreProtectedAndTreesCleared(GameTestHelper h){
  var t=site(h,25);
  try{
   home(t,"e1",24,0);research(t,STONE);
   var plan=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   var tree=plan.ring().get(plan.ring().size()/8);
   var trunk=new ArrayList<BlockPos>();for(int y=1;y<=6;y++){var p=tree.atY(t.center.getY()+y);t.l.setBlock(p,Blocks.OAK_LOG.defaultBlockState(),3);trunk.add(p);}
   for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int y=3;y<=6;y++){var p=tree.offset(dx,0,dz).atY(t.center.getY()+y);if(t.l.getBlockState(p).isAir())t.l.setBlock(p,Blocks.OAK_LEAVES.defaultBlockState(),3);}
   var wooded=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   h.assertTrue(wooded.reason().isEmpty()&&wooded.ring().equals(plan.ring()),"A tree does not move the ring: "+wooded.reason());
   boolean site=wooded.towers().stream().anyMatch(s->Math.abs(s.getX()-tree.getX())<=2&&Math.abs(s.getZ()-tree.getZ())<=2);
   h.assertTrue(site||wooded.clear().containsAll(trunk),"The whole trunk on the ring is to be cleared");
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom()).isEmpty(),"The wall is ordered");
   var ops=ops(t);int lastClear=-1,firstWall=-1;
   for(int i=0;i<ops.size();i++){var p=BlockPos.of(ops.get(i).getLong("pos"));if(p.getX()!=tree.getX()||p.getZ()!=tree.getZ())continue;
    if(ops.get(i).getString("kind").equals("clear"))lastClear=i;else if(firstWall<0&&ops.get(i).getString("kind").equals("wall"))firstWall=i;}
   h.assertTrue(site||lastClear>=0&&firstWall>lastClear,"On the tree's column the crew clears first, then lays: clear "+lastClear+" wall "+firstWall);
   h.assertTrue(ops.stream().filter(o->o.getString("kind").equals("clear")).allMatch(o->o.getBoolean("loose")),"Clearing is loose: leaves may change once their trunk is gone");
   build(t);
   // Every column of the ring outside the gateways and tower sites carries its wall, the tree's too (CF3).
   var towers=wooded.towers();int holes=0;String first="";
   for(int i=0;i<wooded.ring().size();i++){var col=wooded.ring().get(i);
    if(towers.stream().anyMatch(s->Math.abs(s.getX()-col.getX())<=2&&Math.abs(s.getZ()-col.getZ())<=2))continue;
    if(wooded.gates().stream().anyMatch(g->Math.abs(g.getX()-col.getX())<=Walls.GATE/2&&Math.abs(g.getZ()-col.getZ())<=Walls.GATE/2))continue;
    var mid=col.atY(t.center.getY()+2);if(!Walls.palette(t.l.getBlockState(mid))){holes++;if(first.isEmpty())first=mid.subtract(t.center).toShortString()+"="+t.l.getBlockState(mid).getBlock();}}
   h.assertTrue(holes==0,"No hole in the wall: "+holes+" first "+first);
   var plinth=wooded.blocks().entrySet().stream().filter(x->x.getValue().is(Blocks.COBBLESTONE)).findFirst().orElseThrow().getKey();
   var pilaster=wooded.blocks().entrySet().stream().filter(x->x.getValue().is(Blocks.STRIPPED_SPRUCE_LOG)).findFirst().orElseThrow().getKey();
   var breaker=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(t.l);
   for(var p:List.of(plinth,pilaster)){h.assertTrue(Walls.wallBlock(t.l,p)&&OwnershipEvents.protectedBlock(t.l,p),"A laid "+t.l.getBlockState(p).getBlock()+" is the village's");
    var event=new net.minecraftforge.event.level.BlockEvent.BreakEvent(t.l,p,t.l.getBlockState(p),breaker);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
    h.assertTrue(event.isCanceled(),"Nobody breaks the "+t.l.getBlockState(p).getBlock());}
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void playerMayorOrdersAFittedWallFromTheMap(GameTestHelper h){
  var t=site(h,26);
  try{
   home(t,"e1",24,0);research(t,STONE);
   var square=MapOrders.preview(t.l,t.e,MapOrders.WALL,t.center,t.center,0,24);var plan=Walls.plan(t.l,t.e,Walls.Shape.SQUARE,24);
   h.assertTrue(square.getInt("cells")==plan.blocks().size()&&!square.contains("perimeter")&&square.getLongArray("ringCells").length==plan.ring().size(),"The square wall's dry run is as before, with its ring to draw");
   var dry=MapOrders.preview(t.l,t.e,MapOrders.WALL,t.center,t.center,2,1000000);
   h.assertTrue(dry.getString("reason").isEmpty()&&dry.getInt("radius")==1000000&&dry.getInt("rmax")<=Walls.FIT.maxRadius()&&dry.getInt("rmin")>=Walls.FIT.minRadius(),"Any headroom a client sends is clamped: "+dry.getInt("rmin")+"–"+dry.getInt("rmax"));
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.FITTED,8).isEmpty(),"The mayor orders the fitted wall from the map");
   var record=Walls.record(t.l.getServer(),t.s.id());
   h.assertTrue(record.getString("shape").equals("fitted")&&record.getInt("headroom")==8&&record.getInt("generation")==1&&Roads.project(t.l,t.s.id()).contains("wallRecord"),"It is remembered with its headroom, and its project carries the record");
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.FITTED,8).equals("busy"),"One wall project at a time");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=600) public static void aConvertedSquareWallKeepsItsStandingTowersOnly(GameTestHelper h){
  var t=site(h,27);
  try{
   research(t,"defense.1","defense.2");
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.SQUARE,16).isEmpty(),"A square wall is ordered first");
   var project=Roads.project(t.l,t.s.id());project.putBoolean("complete",true);Roads.save(t.l,t.s.id(),project);
   var old=Walls.record(t.l.getServer(),t.s.id());var sites=Arrays.stream(old.getLongArray("towers")).mapToObj(BlockPos::of).toList();
   var standing=sites.get(0);var origin=standing.offset(-2,0,-2).atY(t.center.getY()).subtract(t.center);
   t.s.addBuilding(new Settlement.Building(Settlement.childId(t.s.id(),"building/wall_tower/0"),Walls.TOWER,origin.getX(),origin.getY(),origin.getZ()));
   village(t);
   h.assertTrue(Walls.outgrown(t.e,old),"The village has outgrown its square wall");
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom()).isEmpty(),"It is replaced by a wall fitted to the village");
   var record=Walls.record(t.l.getServer(),t.s.id());var towers=Arrays.stream(record.getLongArray("towers")).mapToObj(BlockPos::of).toList();
   h.assertTrue(towers.contains(standing),"The standing tower stays a watchtower");
   h.assertTrue(sites.stream().skip(1).noneMatch(towers::contains),"No unbuilt site of the old ring is kept: it would raise a tower inside the village");
   h.assertTrue(record.getLongArray("retiring").length>0,"The old ring is to come down");
  }finally{done(t);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=600) public static void somethingPutOnTheWallDoesNotMoveIt(GameTestHelper h){
  var t=site(h,28);
  try{
   home(t,"e1",24,0);research(t,STONE);
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom()).isEmpty(),"The wall is ordered");
   build(t);Walls.tick(t.l,t.e);
   var before=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());int y=t.center.getY();
   // A chest on a crenel and one between two crenels, on stretches of plain wall.
   BlockPos even=null,odd=null;
   for(var col:before.ring()){boolean body=true;for(int k=1;k<=Walls.HEIGHT;k++)body&=before.blocks().containsKey(col.atY(y+k))&&Walls.palette(t.l.getBlockState(col.atY(y+k)));if(!body)continue;
    boolean crenel=before.blocks().containsKey(col.atY(y+Walls.HEIGHT+1));
    if(crenel&&even==null)even=col.atY(y+Walls.HEIGHT+2);else if(!crenel&&odd==null&&even!=null&&Math.abs(col.getX()-even.getX())+Math.abs(col.getZ()-even.getZ())>8)odd=col.atY(y+Walls.HEIGHT+1);}
   h.assertTrue(even!=null&&odd!=null,"The ring has plain stretches with and without a crenel");
   for(var p:List.of(even,odd))t.l.setBlock(p,Blocks.CHEST.defaultBlockState(),3);
   var after=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   h.assertTrue(after.reason().isEmpty()&&after.pushed().isEmpty()&&after.ring().equals(before.ring()),"A chest on the wall does not push the ring away: "+after.reason()+" pushed "+after.pushed().size());
   h.assertTrue(after.blocks().equals(before.blocks()),"Nor is a second wall planned on top of it");
   h.assertTrue(!after.clear().contains(even)&&!after.clear().contains(odd),"And nobody's chest is cleared away");
   var ops=Walls.project(t.l,after).getList("ops",Tag.TAG_COMPOUND);
   h.assertTrue(ops.isEmpty(),"The standing wall needs no work: "+ops.size()+" ops");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void anAutomaticExtensionNeedsRoomForWhatComesBack(GameTestHelper h){
  var t=site(h,29);
  try{
   var pos=LogisticsRoutes.position(t.e,Workshops.hall(t.e));t.l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
   var chest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));chest.expandHall();
   for(int i=0;i<chest.getContainerSize();i++)chest.setItem(i,new ItemStack(Items.DIRT,64));
   var project=new CompoundTag();var ops=new ListTag();var op=new CompoundTag();op.putString("kind","wall_clear");op.putLong("pos",t.center.above(40).asLong());
   op.put("before",NbtUtils.writeBlockState(Blocks.STONE_BRICKS.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.putString("item","");op.putBoolean("loose",true);
   ops.add(op);project.put("ops",ops);project.put("cost",new CompoundTag());
   h.assertTrue(!Walls.room(t.l,t.e,project),"A full hall cannot take the old stretch's stone back");
   chest.setItem(0,new ItemStack(Items.STONE_BRICKS,63));h.assertTrue(Walls.room(t.l,t.e,project),"It fits on a stack of its own kind");
   chest.setItem(0,new ItemStack(Items.STONE_BRICKS,64));h.assertTrue(!Walls.room(t.l,t.e,project),"Not on a full stack");
   var cost=new CompoundTag();cost.putInt("minecraft:dirt",64);project.put("cost",cost);
   h.assertTrue(Walls.room(t.l,t.e,project),"The stone taken out for the new stretch makes room");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aGatewayKeepsItsRoadLamp(GameTestHelper h){
  var t=site(h,30);
  try{
   home(t,"e1",24,0);research(t,STONE);int y=t.center.getY();
   var plan=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   var north=plan.ring().stream().filter(p->p.getX()==t.center.getX()).min(Comparator.comparingInt(BlockPos::getZ)).orElseThrow();
   for(int x=-1;x<=1;x++)for(int dz=-4;dz<=4;dz++){var cell=new BlockPos(t.center.getX()+x,y,north.getZ()+dz);t.l.setBlock(cell,Blocks.COBBLESTONE.defaultBlockState(),2);Roads.register(t.l,t.s.id(),cell);}
   var roads=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   // A gateway column beside the road (no road under it): only its lintel is laid.
   var side=roads.ring().stream().filter(c->Roads.cell(t.l,c.atY(y))==null&&roads.blocks().containsKey(c.atY(y+Walls.HEIGHT+1))&&!roads.blocks().containsKey(c.atY(y+1))).findFirst().orElse(null);
   h.assertTrue(side!=null,"The gateway is wider than the road");
   var fence=side.atY(y+1);var lamp=side.atY(y+2);
   var post=new CompoundTag();var ops=new ListTag();
   for(var at:List.of(fence,lamp)){var op=new CompoundTag();var block=at==fence?Blocks.OAK_FENCE:Blocks.LANTERN;op.putString("kind","post");op.putLong("pos",at.asLong());
    op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(block.defaultBlockState()));op.putString("item",BuiltInRegistries.ITEM.getKey(block.asItem()).toString());ops.add(op);}
   post.putInt("schema",1);post.putUUID("id",UUID.randomUUID());post.putString("kind","build");post.put("ops",ops);post.put("cost",new CompoundTag());post.putInt("index",0);
   post.put("cargo",new ListTag());post.put("returns",new ListTag());post.putInt("withdrawals",0);post.putInt("deposits",0);post.putBoolean("complete",false);post.put("cells",new ListTag());
   h.assertTrue(Roads.order(t.l,t.e,post),"The road's lamp post is ordered");build(t);
   h.assertTrue(t.l.getBlockState(lamp).is(Blocks.LANTERN),"The lamp stands beside the road in the gateway");
   var lit=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   h.assertTrue(lit.reason().isEmpty()&&!lit.clear().contains(lamp)&&!lit.clear().contains(fence),"The wall's crew leaves the road's lamp post in its gateway: "+lit.reason());
  }finally{done(t);}
  h.succeed();
 }

 /** AD-157 (owner's Defence ladder): Defence I-II lays a wooden palisade, III small stone, IV and on big stone; a palisade standing when
  *  the village learns III is taken down (its wood back to the hall) and laid again in stone. */
 @GameTest(template="empty",timeoutTicks=400) public static void aPalisadeWallTurnsToStoneWithTheDefence(GameTestHelper h){
  var t=site(h,31);
  try{
   h.assertTrue(Walls.style(1)==Walls.WOOD&&Walls.style(2)==Walls.WOOD&&Walls.style(3)==Walls.SMALL_STONE&&Walls.style(4)==Walls.BIG_STONE&&Walls.style(6)==Walls.BIG_STONE,"Wood I-II, small stone III, big stone IV-VI");
   home(t,"e1",24,0);research(t,"defense.1");
   var wood=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   h.assertTrue(wood.reason().isEmpty()&&wood.blocks().values().stream().anyMatch(s->s.is(Blocks.SPRUCE_LOG))&&wood.blocks().values().stream().anyMatch(s->s.is(Blocks.SPRUCE_FENCE))
     &&wood.blocks().values().stream().noneMatch(s->s.is(Blocks.STONE_BRICKS)||s.is(Blocks.COBBLESTONE)),"Defence I: a palisade of spruce logs with fence crenels "+wood.reason());
   for(var cell:wood.blocks().entrySet())t.l.setBlock(cell.getKey(),cell.getValue(),2);
   research(t,"defense.2","defense.3");
   var stone=Walls.plan(t.l,t.e,Walls.Shape.FITTED,Walls.FIT.headroom());
   h.assertTrue(stone.reason().isEmpty()&&stone.blocks().values().stream().anyMatch(s->s.is(Blocks.COBBLESTONE_WALL))&&stone.blocks().values().stream().noneMatch(s->s.is(Blocks.SPRUCE_FENCE)||s.is(Blocks.STONE_BRICKS)),"Defence III: small stone "+stone.reason());
   var ops=Walls.project(t.l,stone).getList("ops",Tag.TAG_COMPOUND);var blocks=t.l.holderLookup(net.minecraft.core.registries.Registries.BLOCK);int down=0,laid=0;
   for(var raw:ops){var op=(CompoundTag)raw;var before=NbtUtils.readBlockState(blocks,op.getCompound("before"));var after=NbtUtils.readBlockState(blocks,op.getCompound("after"));
    if(after.isAir()&&(before.is(Blocks.SPRUCE_LOG)||before.is(Blocks.SPRUCE_FENCE)))down++;if(op.getString("kind").equals("wall")&&after.is(Blocks.COBBLESTONE_WALL))laid++;}
   h.assertTrue(down>0&&laid>0,"The palisade comes down ("+down+") and small stone goes up ("+laid+")");
  }finally{done(t);}
  h.succeed();
 }
}
