package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-094: the castle wall — a closed ring, square or round, opened where the roads go out, put up by the builders from the hall's stone,
 *  guarded like a building, and with archer towers that follow as buildings of their own, where archers are assigned. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WallGameTests {
 private static final int R=16,MARGIN=8;
 private record Site(ServerLevel l,SettlementData.Entry e,Settlement s,BlockPos center,List<net.minecraft.world.level.ChunkPos> chunks){}
 private static void meadowColumn(ServerLevel l,BlockPos ground){
  int top=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,ground.getX(),ground.getZ());
  l.setBlock(ground.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(ground,Blocks.GRASS_BLOCK.defaultBlockState(),2);
  for(int y=ground.getY()+1;y<top;y++)l.setBlock(new BlockPos(ground.getX(),y,ground.getZ()),Blocks.AIR.defaultBlockState(),2);
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theWallFixtureClearsACliffAboveFortyBlocks(GameTestHelper h){
  var l=h.getLevel();var ground=h.absolutePos(new BlockPos(2,1,2));l.setBlock(ground.above(60),Blocks.STONE.defaultBlockState(),2);
  meadowColumn(l,ground);
  h.assertTrue(l.getBlockState(ground.above(60)).isAir()&&l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,ground.getX(),ground.getZ())==ground.getY()+1,"No floating cliff above the fixture road");h.succeed();
 }
 /** A flat meadow far from every test plot (the ring is wider than a plot), its chunks held loaded while the test runs. */
 private static Site site(GameTestHelper h,int slot){
  // Each wall test has its own meadow far from the test grid, apart from the others whatever places the runner gives the tests
  // (a meadow beside another test's town hall would find its ring crossing that village).
  var l=h.getLevel();var far=new BlockPos(slot*256,0,-4000);
  int reach=R+MARGIN;var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int cx=(far.getX()-reach)>>4;cx<=(far.getX()+reach)>>4;cx++)for(int cz=(far.getZ()-reach)>>4;cz<=(far.getZ()+reach)>>4;cz++){
   l.setChunkForced(cx,cz,true);l.getChunk(cx,cz);chunks.add(new net.minecraft.world.level.ChunkPos(cx,cz));}
  // Out there the world is the generated one: the meadow is levelled at the height of its own ground, hills and trees cleared.
  var center=new BlockPos(far.getX(),l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,far.getX(),far.getZ())-1,far.getZ());
  for(int x=-reach;x<=reach;x++)for(int z=-reach;z<=reach;z++){
   // A random seed can put a cliff more than 40 blocks above the centre. Clear the whole column,
   // otherwise its floating summit makes the heightmap place the gate above the fixture road.
   meadowColumn(l,center.offset(x,0,z));}
  var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Site(l,e,s,center,chunks);
 }
 private static void done(Site t){
  HallUpgradeGoal.drop(t.l,t.s.id());SettlementData.get(t.l.getServer()).remove(t.s.id());Walls.forget();
  for(var c:t.chunks)t.l.setChunkForced(c.x,c.z,false);
 }
 private static void research(Site t,String... nodes){
  var record=BookResearch.inspect(t.l,t.e);var done=record.getList("legacyDone",Tag.TAG_STRING);for(var n:nodes)done.add(StringTag.valueOf(n));
  record.put("legacyDone",done);BookResearch.store(t.l,t.e,record);
 }
 /** No gap a mob could walk through: the inside, flooded from the centre side by side, never reaches the outside. */
 private static boolean closed(List<BlockPos> ring,int radius){
  var wall=new HashSet<Long>();for(var p:ring)wall.add(BlockPos.asLong(p.getX(),0,p.getZ()));
  var seen=new HashSet<Long>();var queue=new ArrayDeque<int[]>();queue.add(new int[]{0,0});seen.add(BlockPos.asLong(0,0,0));int edge=radius+3;
  while(!queue.isEmpty()){var at=queue.poll();if(Math.abs(at[0])>=edge||Math.abs(at[1])>=edge)return false;
   for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int x=at[0]+d[0],z=at[1]+d[1];long key=BlockPos.asLong(x,0,z);if(wall.contains(key)||!seen.add(key))continue;queue.add(new int[]{x,z});}}
  return true;
 }
 @GameTest(template="empty",timeoutTicks=100) public static void aWallRingIsClosedWhetherSquareOrRound(GameTestHelper h){
  for(int radius:new int[]{Walls.MIN_RADIUS,23,40,Walls.MAX_RADIUS}){
   var square=Walls.ring(BlockPos.ZERO,Walls.Shape.SQUARE,radius);var round=Walls.ring(BlockPos.ZERO,Walls.Shape.ROUND,radius);
   h.assertTrue(square.size()==8*radius&&new HashSet<>(square).size()==square.size(),"A square ring is one column thick all round: "+square.size());
   h.assertTrue(square.stream().allMatch(p->Math.max(Math.abs(p.getX()),Math.abs(p.getZ()))==radius),"Every column of the square lies on its sides");
   h.assertTrue(round.stream().allMatch(p->Math.abs(Math.sqrt(p.getX()*p.getX()+p.getZ()*p.getZ())-radius)<=0.5),"Every column of the round wall lies on its circle");
   h.assertTrue(closed(square,radius)&&closed(round,radius),"Neither ring leaves a gap at radius "+radius);
   // The round wall is really round: it is shorter than the square around it and longer than the square inside it.
   h.assertTrue(round.size()<square.size()&&round.size()>4*radius,"A round wall of radius "+radius+" has "+round.size()+" columns");
  }
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void aWallBlockIsNotLaidOnAnybody(GameTestHelper h){
  var l=h.getLevel();var cell=h.absolutePos(new BlockPos(2,2,2));
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(cell.getX()+.5,cell.getY(),cell.getZ()+.5,0,0);l.addFreshEntity(npc);
  try{
   h.assertTrue(RoadWorkGoal.occupant(l,cell)==npc,"Whoever stands in a wall cell is found there");
   h.assertTrue(RoadWorkGoal.occupant(l,cell.east(2))==null,"An empty cell is free to build");
  }finally{npc.discard();}
  h.succeed();
 }
 /** wall-client-04: the archer stood under the platform for twenty minutes — its stair must really lead a resident up, for every turn. */
 @GameTest(template="empty",timeoutTicks=100) public static void theTowerStairLeadsAResidentUpToThePlatform(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(4,1,4));
  for(int x=-3;x<8;x++)for(int z=-3;z<8;z++){l.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=1;y<13;y++)l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  for(var cell:BuildingPlacement.layout(Walls.TOWER,origin,0).entrySet())if(cell.getKey().getY()>origin.getY()||!cell.getValue().isAir())l.setBlock(cell.getKey(),cell.getValue(),3);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.setNoAi(true);npc.moveTo(origin.getX()+2.5,origin.getY()+1,origin.getZ()-1.5,0,0);l.addFreshEntity(npc);npc.setOnGround(true);
  try{
   for(var stand:List.of(origin.offset(3,8,3),origin.offset(3,8,1))){
    var path=npc.getNavigation().createPath(stand,0);
    h.assertTrue(path!=null&&path.canReach(),"From the door a resident finds its way up the stair to "+stand.subtract(origin).toShortString()+": "+(path==null?"no path":"ends at "+path.getEndNode().asBlockPos().subtract(origin).toShortString()));}
  }finally{npc.discard();}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theMayorOrdersAWallAndTheBuildersPutItUpWithAGateOverTheRoad(GameTestHelper h){
  var t=site(h,0);
  try{
   // A road runs out of the village to the north, three blocks wide, across the line where the wall will stand.
   for(int x=-1;x<=1;x++)for(int z=-R-4;z<=-R+4;z++){var cell=t.center.offset(x,0,z);t.l.setBlock(cell,Blocks.COBBLESTONE.defaultBlockState(),2);Roads.register(t.l,t.s.id(),cell);}
   var crossing=t.center.offset(0,0,-R);
   h.assertTrue(Roads.cell(t.l,crossing)!=null&&t.l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,crossing.getX(),crossing.getZ())-1==crossing.getY(),
     "The road is laid where the wall will cross it: "+Roads.cell(t.l,crossing)+" "+t.l.getBlockState(crossing)+" surface "+t.l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,crossing.getX(),crossing.getZ()));
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.SQUARE,R).equals("research"),"A wall takes guard posts researched first");
   var dry=MapOrders.preview(t.l,t.e,MapOrders.WALL,t.center,t.center,0,R);
   h.assertTrue(dry.getString("reason").equals("research"),"And the map says so before the order: "+dry.getString("reason"));
   research(t,"defense.1");
   h.assertTrue(Walls.plan(t.l,t.e,Walls.Shape.SQUARE,Walls.MIN_RADIUS-1).reason().equals("radius"),"A wall is not squeezed around the hall");
   var plan=Walls.plan(t.l,t.e,Walls.Shape.SQUARE,R);
   h.assertTrue(plan.reason().isEmpty(),"The square wall fits: "+plan.reason());
   h.assertTrue(plan.gates().size()==1&&Math.abs(plan.gates().get(0).getX()-t.center.getX())<=1&&plan.gates().get(0).getZ()==t.center.getZ()-R,
     "Its one gate is where the road goes out, not on the other sides: "+plan.gates());
   h.assertTrue(plan.towers().size()==4,"Archer towers stand at the four corners: "+plan.towers().size());
   int ground=t.center.getY();
   for(int x=-1;x<=1;x++){var column=t.center.offset(x,0,-R);
    for(int y=1;y<=Walls.HEIGHT;y++)h.assertTrue(!plan.blocks().containsKey(column.above(y)),"The road keeps its way through the wall at "+x);
    h.assertTrue(plan.blocks().get(column.above(Walls.HEIGHT+1))!=null,"And the gateway has its lintel");}
   var east=t.center.offset(R,0,3);
   for(int y=1;y<=Walls.HEIGHT;y++)h.assertTrue(plan.blocks().get(east.above(y)).is(Blocks.STONE_BRICKS),"The wall stands three bricks high on the east side");
   h.assertTrue(plan.blocks().keySet().stream().allMatch(p->p.getY()>ground&&p.getY()<=ground+Walls.HEIGHT+1),"Nothing of the wall is dug in or piled higher than its crenellations");
   h.assertTrue(plan.blocks().values().stream().anyMatch(s->s.is(Blocks.STONE_BRICK_WALL)),"The top is crenellated");
   dry=MapOrders.preview(t.l,t.e,MapOrders.WALL,t.center,t.center,0,R);
   h.assertTrue(dry.getString("reason").isEmpty()&&dry.getInt("cells")==plan.blocks().size()&&dry.getInt("gates")==1&&dry.getInt("towers")==4&&!dry.getBoolean("towerResearch"),
     "The map's dry run shows the same wall and that its towers wait for their research: "+dry);
   // A house outside the ring: a wider wall would go over it and is refused.
   t.s.addBuilding(new Settlement.Building(Settlement.childId(t.s.id(),"building/home_east"),"home",R+3,0,-2));
   h.assertTrue(Walls.plan(t.l,t.e,Walls.Shape.SQUARE,R+5).reason().equals("crosses"),"A ring over a house is refused");
   h.assertTrue(Walls.plan(t.l,t.e,Walls.Shape.ROUND,R).reason().isEmpty(),"The round wall inside it fits");
   // The order: one builders' project, paid block for block in stone brick from the hall.
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.SQUARE,R).isEmpty(),"The mayor orders the wall");
   var project=Roads.project(t.l,t.s.id());
   h.assertTrue(project!=null&&project.getString("kind").equals("wall")&&Roads.active(t.l,t.s.id()),"The builders have the wall to put up");
   var ops=project.getList("ops",Tag.TAG_COMPOUND);var cost=project.getCompound("cost");
   h.assertTrue(ops.size()==plan.blocks().size(),"One operation per block: "+ops.size()+" of "+plan.blocks().size());
   h.assertTrue(cost.getInt("minecraft:stone_bricks")>=ops.size()/2&&cost.getInt("minecraft:stone_brick_wall")>0,"Paid in stone bricks and brick wall: "+cost);
   // The wall is laid round the ring once, each column from the ground up, and one load carries what the next columns take.
   h.assertTrue(BlockPos.of(ops.getCompound(0).getLong("pos")).getY()<BlockPos.of(ops.getCompound(1).getLong("pos")).getY(),"The first column goes up course by course");
   var load=Roads.upcoming(project,Roads.CARRY);
   h.assertTrue(load.values().stream().mapToInt(Integer::intValue).sum()==Roads.CARRY&&load.containsKey(Items.STONE_BRICKS)&&load.containsKey(Items.STONE_BRICK_WALL),
     "A builder's load is bricks and crenellations in the measure the next columns take: "+load);
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.ROUND,R).equals("busy"),"One wall at a time");
   var record=Walls.record(t.l.getServer(),t.s.id());
   h.assertTrue(record!=null&&record.getString("shape").equals("square")&&record.getLongArray("towers").length==4&&record.getLongArray("gates").length==1,"The village remembers its wall");
   // The first block goes up and nobody may take it down; the cells still empty are nobody's.
   var cargo=new ListTag();cargo.add(new ItemStack(Items.STONE_BRICKS,8).save(new CompoundTag()));project.put("cargo",cargo);Roads.save(t.l,t.s.id(),project);
   var first=BlockPos.of(ops.getCompound(0).getLong("pos"));
   h.assertTrue(Roads.apply(t.l,t.e,project).equals("done")&&t.l.getBlockState(first).is(Blocks.STONE_BRICKS),"A builder lays the first brick");
   h.assertTrue(OwnershipEvents.protectedBlock(t.l,first),"The laid brick belongs to the village");
   var later=BlockPos.of(ops.getCompound(ops.size()-1).getLong("pos"));
   h.assertTrue(!OwnershipEvents.protectedBlock(t.l,later),"A cell of the wall not yet built is open ground");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void archerTowersFollowTheWallAndTakeArchers(GameTestHelper h){
  var t=site(h,1);
  try{
   research(t,"defense.1");
   h.assertTrue(Walls.tick(t.l,t.e).isEmpty(),"No wall, nothing to follow");
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.ROUND,R).isEmpty(),"A round wall is ordered");
   var record=Walls.record(t.l.getServer(),t.s.id());var towers=record.getLongArray("towers");
   h.assertTrue(towers.length>=4&&record.getLongArray("gates").length==4,"Without roads the round wall opens on its four sides and has its towers: "+towers.length);
   h.assertTrue(Walls.tick(t.l,t.e).equals("walling"),"The towers wait while the wall goes up");
   var project=Roads.project(t.l,t.s.id());project.putBoolean("complete",true);Roads.save(t.l,t.s.id(),project);
   h.assertTrue(Walls.tick(t.l,t.e).equals("research"),"Then for archer stations");
   h.assertTrue(!ResearchGate.designRefusal(t.l,t.e,Walls.TOWER).isEmpty(),"Nor may the mayor order a tower by hand before them");
   research(t,"defense.2");
   h.assertTrue(ResearchGate.designRefusal(t.l,t.e,Walls.TOWER).isEmpty(),"With them a tower can be ordered");
   var first=Walls.tick(t.l,t.e);
   var site=BlockPos.of(towers[0]);var at=new BlockPos(site.getX()-2,t.center.getY(),site.getZ()-2);
   h.assertTrue(first.equals("tower")&&HallUpgradeGoal.pending(t.l,t.s.id()),"The first tower goes to the building queue: "+first+" "
     +BuildingOrders.survey(t.l,t.e,Walls.TOWER,0,at).conflicts().stream().limit(6).map(p->p.subtract(at).toShortString()+"="+t.l.getBlockState(p).getBlock()).toList());
   h.assertTrue(Walls.tick(t.l,t.e).equals("busy"),"One at a time");
   // The crew raises every tower; each becomes a building of the village on its site.
   HallUpgradeGoal.drop(t.l,t.s.id());int i=0;
   for(long raw:towers){var column=BlockPos.of(raw);var origin=new BlockPos(column.getX()-2,t.center.getY(),column.getZ()-2).subtract(t.center);
    t.s.addBuilding(new Settlement.Building(Settlement.childId(t.s.id(),"building/wall_tower/"+i++),Walls.TOWER,origin.getX(),origin.getY(),origin.getZ()));}
   h.assertTrue(Walls.tick(t.l,t.e).equals("done"),"With all towers standing the wall is finished");
   var tower=t.s.buildings().stream().filter(b->b.type().equals(Walls.TOWER)).findFirst().orElseThrow();
   var home=new Settlement.Home(Settlement.childId(t.s.id(),"home"),1,4,true);t.s.addHome(home);
   var recruit=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(recruit,home.id());
   boolean refused=false;try{t.s.assign(recruit.id(),Profession.ARCHER_GUARD,tower.id());}catch(IllegalStateException ex){refused=true;}
   h.assertTrue(refused,"An untrained villager is not sent up a tower");
   h.assertTrue(Walls.postRefusal(t.s,tower).equals("no_archer")&&Walls.post(t.s,tower).equals("no_archer"),"Nor does the mayor's order send one up");
   // The mayor's «Send an archer up»: a trained adult without a trade goes to the tower.
   recruit.trainMilitary();h.assertTrue(Walls.post(t.s,tower).isEmpty(),"The mayor sends the trained one up");
   h.assertTrue(recruit.profession()==Profession.ARCHER_GUARD&&t.s.workplace(recruit.id()).id().equals(tower.id()),"A trained archer serves on the wall tower");
   refused=false;var soldier=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(soldier,home.id());soldier.trainMilitary();
   try{t.s.assign(soldier.id(),Profession.SOLDIER,tower.id());}catch(IllegalStateException ex){refused=true;}
   h.assertTrue(refused,"A tower is an archers' post, not a soldiers'");
   h.assertTrue(Walls.post(t.s,tower).isEmpty()&&soldier.profession()==Profession.ARCHER_GUARD&&Walls.archers(t.s,tower)==2,"The trained one left without a post goes up as its second archer");
   var third=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(third,home.id());third.trainMilitary();
   h.assertTrue(Walls.post(t.s,tower).equals("full")&&third.profession()==null,"The platform holds two");
  }finally{done(t);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=200) public static void aGrownVillageOfItsOwnMayorWallsItselfIn(GameTestHelper h){
  var t=site(h,2);
  try{
   var hall=t.s.buildings().stream().filter(b->b.type().equals("town_hall")).findFirst().orElseThrow();
   var home=new Settlement.Home(Settlement.childId(t.s.id(),"home"),1,16,true);t.s.addHome(home);
   for(int i=0;i<MayorPlanner.WALL_AT-1;i++){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,i%2==0,null,null,-1);t.s.admit(r,home.id());if(i==0)t.s.assign(r.id(),Profession.MAYOR,hall.id());}
   research(t,"defense.1");
   h.assertTrue(MayorPlanner.wall(t.l,t.e).isEmpty()&&Walls.record(t.l.getServer(),t.s.id())==null,"A village of eleven adults builds no wall yet");
   var twelfth=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(twelfth,home.id());
   var ordered=MayorPlanner.wall(t.l,t.e);
   h.assertTrue(ordered.matches("(round|square)[0-9]+")&&Walls.record(t.l.getServer(),t.s.id())!=null&&Roads.active(t.l,t.s.id()),"With twelve its mayor orders the smallest wall that stands clear: "+ordered);
   h.assertTrue(MayorPlanner.wall(t.l,t.e).isEmpty(),"One wall is enough");
  }finally{done(t);}
  h.succeed();
 }

 /** Review 2026-09-18: the corner towers of a square wall opened their doors straight into the wall's other arm. Every tower's door must
  *  open onto ground the wall leaves free, and the queue must take the tower. */
 @GameTest(template="empty",timeoutTicks=200) public static void everyTowerOfASquareWallOpensIntoTheVillage(GameTestHelper h){
  var t=site(h,3);
  try{
   research(t,"defense.1","defense.2");
   var plan=Walls.plan(t.l,t.e,Walls.Shape.SQUARE,R);
   var near=new ArrayList<String>();for(var other:SettlementData.get(t.l.getServer()).entries())if(other!=t.e&&other.center().distSqr(t.center)<160*160)near.add(other.center().subtract(t.center).toShortString());
   h.assertTrue(plan.reason().isEmpty()&&plan.towers().size()==4,"A square wall of radius 16 has its four towers: "+plan.reason()+" "+plan.towers().stream().map(p->p.subtract(t.center).toShortString()).toList()+" villages near "+near);
   var columns=new HashSet<Long>();for(var p:plan.blocks().keySet())columns.add(BlockPos.asLong(p.getX(),0,p.getZ()));
   for(var site:plan.towers()){
    int turns=Walls.towerTurns(t.center,site);var origin=site.offset(-2,0,-2);BlockPos door=null;
    for(var cell:BuildingPlacement.layout(Walls.TOWER,origin,turns).entrySet())if(cell.getValue().getBlock() instanceof net.minecraft.world.level.block.DoorBlock&&cell.getKey().getY()==origin.getY()+1)door=cell.getKey();
    h.assertTrue(door!=null,"The tower has a door");
    BlockPos front=null;for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL){var n=door.relative(d);if(Math.abs(n.getX()-site.getX())>2||Math.abs(n.getZ()-site.getZ())>2)front=n;}
    h.assertTrue(front!=null&&!columns.contains(BlockPos.asLong(front.getX(),0,front.getZ())),"The door of the tower at "+site.subtract(t.center).toShortString()+" opens onto free ground, not the wall");
    h.assertTrue(front.distSqr(t.center)<site.distSqr(t.center),"And it looks into the village");
   }
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.SQUARE,R).isEmpty(),"The wall is ordered");
   var project=Roads.project(t.l,t.s.id());project.putBoolean("complete",true);Roads.save(t.l,t.s.id(),project);
   h.assertTrue(Walls.tick(t.l,t.e).equals("tower"),"And its first tower is taken by the crew");
  }finally{done(t);}
  h.succeed();
 }
 /** Review 2026-09-18: a laid wall block was protected only on paper; a player could mine it. Ordering the same ring again repairs it
  *  and never stacks a second wall on the first; a site where no tower can stand is walled through instead of left open. */
 @GameTest(template="empty",timeoutTicks=200) public static void aLaidWallIsKeptWholeAndRepairedInPlace(GameTestHelper h){
  var t=site(h,4);
  try{
   research(t,"defense.1");
   var first=Walls.plan(t.l,t.e,Walls.Shape.ROUND,R);var blocked=first.towers().get(0);
   // Someone has put a stone pillar where the first tower would stand.
   for(int y=1;y<=4;y++)t.l.setBlock(blocked.atY(t.center.getY()+y),Blocks.COBBLESTONE.defaultBlockState(),2);
   var plan=Walls.plan(t.l,t.e,Walls.Shape.ROUND,R);
   h.assertTrue(plan.towers().size()==first.towers().size()-1&&!plan.towers().contains(blocked),"A site where no tower can stand gives up its tower: "+plan.towers().size());
   var near=Walls.ring(t.center,Walls.Shape.ROUND,R).stream().filter(c->Math.abs(c.getX()-blocked.getX())<=2&&Math.abs(c.getZ()-blocked.getZ())<=2&&!c.equals(blocked)).findFirst().orElseThrow();
   h.assertTrue(plan.blocks().containsKey(near.atY(t.center.getY()+1)),"And the wall runs through it, no gap");
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.ROUND,R).isEmpty(),"The wall is ordered");
   var project=Roads.project(t.l,t.s.id());var ops=project.getList("ops",Tag.TAG_COMPOUND);
   var cargo=new ListTag();cargo.add(new ItemStack(Items.STONE_BRICKS,64).save(new CompoundTag()));cargo.add(new ItemStack(Items.STONE_BRICK_WALL,64).save(new CompoundTag()));project.put("cargo",cargo);Roads.save(t.l,t.s.id(),project);
   for(int i=0;i<6;i++){var r=Roads.apply(t.l,t.e,project);h.assertTrue(r.equals("done"),"A builder lays block "+i+": "+r);}
   var laid=BlockPos.of(ops.getCompound(0).getLong("pos"));
   var breaker=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(t.l);
   var event=new net.minecraftforge.event.level.BlockEvent.BreakEvent(t.l,laid,t.l.getBlockState(laid),breaker);
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
   h.assertTrue(event.isCanceled(),"Nobody breaks a laid block of the wall");
   project.putBoolean("complete",true);Roads.save(t.l,t.s.id(),project);
   // A breach: one laid block is gone. The same ring ordered again fills just that, at its own height.
   t.l.setBlock(laid,Blocks.AIR.defaultBlockState(),2);
   var again=Walls.plan(t.l,t.e,Walls.Shape.ROUND,R);
   h.assertTrue(again.reason().isEmpty()&&again.blocks().keySet().equals(plan.blocks().keySet()),"The ring planned again lies where the first one does: "+again.reason());
   h.assertTrue(Walls.order(t.l,t.e,Walls.Shape.ROUND,R).isEmpty(),"The mayor orders it again");
   var repair=Roads.project(t.l,t.s.id()).getList("ops",Tag.TAG_COMPOUND);
   boolean onlyGaps=true;for(int i=0;i<repair.size();i++){var at=BlockPos.of(repair.getCompound(i).getLong("pos"));if(!plan.blocks().containsKey(at))onlyGaps=false;}
   h.assertTrue(onlyGaps&&repair.stream().anyMatch(raw->((CompoundTag)raw).getLong("pos")==laid.asLong())&&repair.size()<ops.size(),"Only the breach and the unbuilt cells are work, nothing is stacked on top: "+repair.size()+" of "+ops.size());
  }finally{done(t);}
  h.succeed();
 }
 /** Review 2026-09-18: a tower archer on a wall wider than the watch's reach ignored every mob outside the ring. */
 @GameTest(template="empty",timeoutTicks=100) public static void aTowerArcherSeesTheGroundBeforeItsTower(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2)).offset(-60,0,0);var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var tower=new Settlement.Building(Settlement.childId(s.id(),"building/wall_tower"),Walls.TOWER,58,0,0);s.addBuilding(tower);
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,2,true);s.addHome(home);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());r.trainMilitary();s.assign(r.id(),Profession.ARCHER_GUARD,tower.id());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var archer=VillageAstra.RESIDENT.get().create(l);var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(l);
  try{
   var stand=GuardGoal.platform(e,tower);archer.setUUID(r.id());archer.bind(s.id(),r);archer.setNoAi(true);archer.moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5,0,0);l.addFreshEntity(archer);
   zombie.setNoAi(true);zombie.moveTo(stand.getX()+8.5,h.absolutePos(new BlockPos(2,2,2)).getY(),stand.getZ()+.5,0,0);l.addFreshEntity(zombie);
   h.assertTrue(zombie.blockPosition().distSqr(center)>GuardGoal.DEFEND_RADIUS*GuardGoal.DEFEND_RADIUS,"The zombie is beyond the watch's usual reach");
   h.assertTrue(GuardGoal.threat(l,e,archer)==zombie,"The archer on the tower sees it all the same");
  }finally{archer.discard();zombie.discard();SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
