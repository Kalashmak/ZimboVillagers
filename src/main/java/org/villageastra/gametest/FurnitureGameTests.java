package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
/** AD-143 (owner 2026-09-23): chairs and tables of every wood — registered with their items, crafted from planks and sticks, a chair's shape
 *  turned by its facing, sitting (a player sits and stands, a chair someone sits on refuses, no second seat, the seat goes with its sitter
 *  and with its chair, a resident's lease), a diner seated on a chair in the restaurant who stands up after the meal, at dusk and when the
 *  chair breaks, tables that join, a sturdy board a lantern stands on, the restaurant's and the houses' furniture in their designs (seats per
 *  level, the door jambs, the kits of old houses unmoved), old halls' stair seats still counted, and the village making and pricing it all. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FurnitureGameTests {
 private static Item item(String id){return BuiltInRegistries.ITEM.get(new ResourceLocation(id));}
 private static ChairBlock chair(String wood){return VillageAstra.CHAIRS.get(wood).get();}
 private static TableBlock table(String wood){return VillageAstra.TABLES.get(wood).get();}
 private static boolean inside(VoxelShape s,double x,double y,double z){return s.toAabbs().stream().anyMatch(a->a.contains(x,y,z));}
 private static List<SeatEntity> seats(GameTestHelper h,BlockPos abs){return h.getLevel().getEntitiesOfClass(SeatEntity.class,new AABB(abs).inflate(2));}
 private static ItemStack craft(GameTestHelper h,Item... cells){var grid=NonNullList.withSize(9,ItemStack.EMPTY);for(int i=0;i<9;i++)if(cells[i]!=null)grid.set(i,new ItemStack(cells[i]));
  var box=new TransientCraftingContainer(null,3,3,grid);var l=h.getLevel();
  return l.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,box,l).map(r->r.assemble(box,l.registryAccess())).orElse(ItemStack.EMPTY);}
 private static ResidentEntity bare(GameTestHelper h,BlockPos rel){var l=h.getLevel();var npc=VillageAstra.RESIDENT.get().create(l);var at=h.absolutePos(rel);
  npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);npc.setNoAi(true);l.addFreshEntity(npc);return npc;}
 /** A stone floor under a 5x5 round a cell, so whoever stands up has ground beside the chair. */
 private static void floor(GameTestHelper h,BlockPos rel){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)h.setBlock(rel.offset(x,-1,z),Blocks.STONE);}

 @GameTest(template="empty") public static void everyWoodHasAChairAndATable(GameTestHelper h){
  h.assertTrue(VillageAstra.CHAIRS.size()==11&&VillageAstra.TABLES.size()==11,"Eleven woods of each");
  for(var wood:Furniture.WOODS){
   for(var id:List.of(Furniture.chairId(wood),Furniture.tableId(wood))){var b=BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
    h.assertTrue(b==(Furniture.chair(id)?chair(wood):table(wood)),"Block "+id);
    h.assertTrue(item(id) instanceof BlockItem bi&&bi.getBlock()==b,"Item of "+id+" places its block");
    h.assertTrue(new ItemStack(item(id)).is(net.minecraft.tags.ItemTags.create(new ResourceLocation(Furniture.chair(id)?Furniture.CHAIRS:Furniture.TABLES))),"Tagged "+id);
    h.assertTrue(b.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE),"An axe cuts "+id);
    var drops=Block.getDrops(b.defaultBlockState(),h.getLevel(),h.absolutePos(new BlockPos(1,2,1)),null);
    h.assertTrue(drops.size()==1&&drops.get(0).is(item(id)),"Drops itself: "+id+" "+drops);}
   h.assertTrue(chair(wood).wood().equals(wood)&&table(wood).wood().equals(wood),"The blocks know their wood");}
  h.assertTrue(BuiltInRegistries.ENTITY_TYPE.getKey(VillageAstra.SEAT.get()).toString().equals("villageastra:seat"),"The seat entity");
  h.succeed();
 }
 @GameTest(template="empty") public static void everyWoodCraftsItsChairAndTable(GameTestHelper h){
  var s=Items.STICK;
  for(var wood:Furniture.WOODS){var p=item(Furniture.planks(wood));
   var c=craft(h,s,null,null,s,p,p,s,null,s);h.assertTrue(c.is(item(Furniture.chairId(wood)))&&c.getCount()==Furniture.CHAIR_YIELD,"Sticks and "+wood+" planks: a chair, got "+c);
   var m=craft(h,null,null,s,p,p,s,s,null,s);h.assertTrue(m.is(item(Furniture.chairId(wood))),"The mirrored chair too, got "+m);
   var t=craft(h,p,p,p,s,null,s,null,null,null);h.assertTrue(t.is(item(Furniture.tableId(wood)))&&t.getCount()==Furniture.TABLE_YIELD,"Three "+wood+" planks over two sticks: a table, got "+t);
   var low=craft(h,null,null,null,p,p,p,s,null,s);h.assertTrue(low.is(item(Furniture.tableId(wood))),"The table in the lower rows, got "+low);}
  // Vanilla shapes of planks and sticks still make vanilla things.
  var o=Items.OAK_PLANKS;
  h.assertTrue(craft(h,o,o,o,null,s,null,null,s,null).is(Items.WOODEN_PICKAXE),"A pickaxe is still a pickaxe");
  h.assertTrue(craft(h,o,s,o,o,s,o,null,null,null).is(Items.OAK_FENCE),"A fence is still a fence");
  h.succeed();
 }
 /** Placed facing the player (the sitter looks back the way the player came); the back stands behind the sitter; the seat is half a block
  *  up; a structure's turn turns it. */
 @GameTest(template="empty") public static void aChairFacesThePlayerAndItsShapeTurnsWithIt(GameTestHelper h){
  var l=h.getLevel();var player=h.makeMockPlayer();var at=h.absolutePos(new BlockPos(2,2,2));var stack=new ItemStack(item(Furniture.chairId("oak")));
  for(var d:Direction.Plane.HORIZONTAL){player.setYRot(d.toYRot());
   var placed=chair("oak").getStateForPlacement(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false)));
   h.assertTrue(placed.getValue(ChairBlock.FACING)==d.getOpposite(),"Walking "+d+", the chair faces "+d.getOpposite()+": "+placed);
   var s=chair("oak").defaultBlockState().setValue(ChairBlock.FACING,d);l.setBlock(at,s,3);var shape=s.getShape(l,at);var back=d.getOpposite();
   h.assertTrue(!Block.isShapeFullBlock(shape)&&shape.equals(s.getCollisionShape(l,at,CollisionContext.empty()))&&shape.max(Direction.Axis.Y)==1.0,"A chair, not a block: "+d);
   h.assertTrue(inside(shape,.5+.3*back.getStepX(),.8,.5+.3*back.getStepZ()),"The back stands behind a sitter facing "+d);
   h.assertTrue(!inside(shape,.5+.3*d.getStepX(),.8,.5+.3*d.getStepZ())&&!inside(shape,.5,.7,.5),"Open in front of and over the seat, facing "+d);
   h.assertTrue(inside(shape,.5,.45,.5)&&!inside(shape,.5,.55,.5),"The seat's top is half a block up");
   h.assertTrue(!s.isPathfindable(l,at,net.minecraft.world.level.pathfinder.PathComputationType.LAND),"Residents walk round it");}
  var n=chair("oak").defaultBlockState();
  h.assertTrue(n.rotate(Rotation.CLOCKWISE_90).getValue(ChairBlock.FACING)==Direction.EAST&&n.mirror(Mirror.LEFT_RIGHT).getValue(ChairBlock.FACING)==Direction.SOUTH,"Turned and mirrored with a structure");
  h.succeed();
 }
 /** A player uses an empty chair and sits, turned the way it faces; a second player is refused; using it again makes no second seat;
  *  sneaking off (stopRiding) removes the seat; a sitter on a broken chair stands up and the seat goes. */
 @GameTest(template="empty",timeoutTicks=100) public static void aPlayerSitsAndStandsUp(GameTestHelper h){
  var l=h.getLevel();floor(h,new BlockPos(2,2,2));var at=h.absolutePos(new BlockPos(2,2,2));var s=chair("spruce").defaultBlockState().setValue(ChairBlock.FACING,Direction.EAST);l.setBlock(at,s,3);
  var one=h.makeMockPlayer();one.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5,0,0);var two=h.makeMockPlayer();two.moveTo(at.getX()-.5,at.getY(),at.getZ()+.5,0,0);
  var hit=new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false);
  h.assertTrue(l.getBlockState(at).use(l,one,InteractionHand.MAIN_HAND,hit).consumesAction()&&SeatEntity.seated(one),"The player sits down");
  one.getVehicle().positionRider(one);
  h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(one.getYRot()-Direction.EAST.toYRot()))<1&&Math.abs(one.getY()-(at.getY()+Furniture.SEAT_HEIGHT-one.getBbHeight()*.325))<.05,"Turned east, hips on the seat: yaw="+one.getYRot()+" y="+one.getY());
  l.getBlockState(at).use(l,two,InteractionHand.MAIN_HAND,hit);
  h.assertTrue(!SeatEntity.seated(two)&&SeatEntity.sitter(l,at)==one,"A chair somebody sits on refuses");
  h.assertTrue(SeatEntity.sit(l,at,one)!=null&&seats(h,at).size()==1,"Sitting again makes no second seat: "+seats(h,at).size());
  h.startSequence()
   .thenExecute(()->one.stopRiding())
   .thenExecuteAfter(3,()->{h.assertTrue(!SeatEntity.seated(one)&&seats(h,at).isEmpty(),"Standing up removes the seat: "+seats(h,at).size());
    h.assertTrue(one.getY()>=at.getY()&&!new AABB(at).contains(one.position()),"It stands beside the chair, not in it: "+one.position());
    h.assertTrue(SeatEntity.sit(l,at,two)!=null&&SeatEntity.seated(two),"The chair is free again");})
   .thenExecute(()->l.destroyBlock(at,false))
   .thenExecuteAfter(2,()->h.assertTrue(!SeatEntity.seated(two)&&seats(h,at).isEmpty(),"A broken chair stands its sitter up and the seat goes"))
   .thenSucceed();
 }
 /** A resident sits only while a goal renews its lease: left alone it stands up after LEASE_TICKS and the seat goes. */
 @GameTest(template="empty",timeoutTicks=200) public static void aResidentWithoutALeaseStandsUp(GameTestHelper h){
  var l=h.getLevel();floor(h,new BlockPos(2,2,2));var at=h.absolutePos(new BlockPos(2,2,2));l.setBlock(at,chair("oak").defaultBlockState(),3);
  var npc=bare(h,new BlockPos(2,2,3));
  h.assertTrue(SeatEntity.sit(l,at,npc)!=null&&SeatEntity.seated(npc),"The resident sits");
  int[] kept={0};
  h.startSequence()
   .thenExecuteFor(SeatEntity.LEASE_TICKS*2,()->{SeatEntity.keep(npc);kept[0]++;})
   .thenExecute(()->h.assertTrue(SeatEntity.seated(npc),"Renewed, it keeps sitting ("+kept[0]+" ticks)"))
   .thenExecuteAfter(SeatEntity.LEASE_TICKS+5,()->h.assertTrue(!SeatEntity.seated(npc)&&seats(h,at).isEmpty(),"No renewal: it stands up and the seat goes"))
   .thenExecute(npc::discard).thenSucceed();
 }
 /** A resident invited to the restaurant sits on a chair of the hall (it rides the chair's seat), eats and stands up; no seat is left. */
 @GameTest(template="empty",timeoutTicks=1600) public static void aDinerSitsOnAChairEatsAndStandsUp(GameTestHelper h){
  var v=RestaurantFixture.village(h,1);var b=v.kept();RestaurantFixture.put(v.kitchen(),new ItemStack(Items.BREAD,4));
  var r=RestaurantFixture.adult(v,"sitter",null,null);var npc=RestaurantFixture.body(v,r,new BlockPos(3,0,10));RestaurantFixture.give(npc,4,new DineGoal(npc,()->RestaurantFixture.NOW));
  long due=r.lastMeal()+Population.MEAL_INTERVAL;
  h.assertTrue(Dining.meal(v.l(),v.e(),r,RestaurantFixture.NOW,due)==Dining.Outcome.WAIT,"Invited: "+Dining.why(v.l(),v.e(),r,b));
  boolean[] onChair={false};
  h.onEachTick(()->{if(npc.getVehicle() instanceof SeatEntity s&&v.l().getBlockState(s.chair()).getBlock() instanceof ChairBlock&&"dining".equals(npc.workStatus()))onChair[0]=true;});
  h.succeedWhen(()->{
   h.assertTrue(r.lastMeal()==due,"Not eaten yet ("+npc.workStatus()+", seated "+SeatEntity.seated(npc)+")");
   h.assertTrue(onChair[0],"It ate sitting on a chair");
   h.assertTrue(!SeatEntity.seated(npc)&&v.l().getEntitiesOfClass(SeatEntity.class,new AABB(npc.blockPosition()).inflate(16)).isEmpty(),"It stood up and no seat is left");
   RestaurantFixture.done(v);});
 }
 /** Seated at a table, a diner stands up at dusk; another, when its chair is broken under it (and no seat is left behind). */
 @GameTest(template="empty",timeoutTicks=900) public static void aDinerStandsUpAtDuskAndWhenItsChairBreaks(GameTestHelper h){
  var v=RestaurantFixture.village(h,1);var b=v.kept();RestaurantFixture.put(v.kitchen(),new ItemStack(Items.BREAD,16));
  var one=RestaurantFixture.adult(v,"dusk",null,null);var two=RestaurantFixture.adult(v,"broken",null,null);
  // In the hall's centre aisle, a step from the seats nearest the door.
  var a=RestaurantFixture.body(v,one,new BlockPos(15,1,2));var c=RestaurantFixture.body(v,two,new BlockPos(15,1,3));
  RestaurantFixture.give(a,4,new DineGoal(a,()->RestaurantFixture.NOW));RestaurantFixture.give(c,4,new DineGoal(c,()->RestaurantFixture.NOW));
  for(var r:List.of(one,two))h.assertTrue(Dining.meal(v.l(),v.e(),r,RestaurantFixture.NOW,r.lastMeal()+Population.MEAL_INTERVAL)==Dining.Outcome.WAIT,"Invited: "+Dining.why(v.l(),v.e(),r,b));
  BlockPos[] chairOf={null};
  h.startSequence()
   .thenWaitUntil(()->h.assertTrue(SeatEntity.seated(a)&&SeatEntity.seated(c),"Both sit down: "+a.workStatus()+"/"+c.workStatus()))
   .thenExecute(()->{chairOf[0]=((SeatEntity)c.getVehicle()).chair();v.l().destroyBlock(chairOf[0],false);})
   .thenExecuteAfter(2,()->h.assertTrue(!SeatEntity.seated(c)&&v.l().getEntitiesOfClass(SeatEntity.class,new AABB(chairOf[0])).isEmpty(),"A broken chair stands its diner up, no seat left"))
   .thenExecute(()->Dining.testDay(v.s().id(),13000L))
   .thenExecuteAfter(5,()->h.assertTrue(!SeatEntity.seated(a),"At dusk the diner stands up"))
   .thenExecuteAfter(SeatEntity.LEASE_TICKS,()->h.assertTrue(!SeatEntity.seated(a)&&!SeatEntity.seated(c)&&v.l().getEntitiesOfClass(SeatEntity.class,new AABB(a.blockPosition()).inflate(16)).isEmpty(),"Nobody is left riding a seat"))
   .thenExecute(()->RestaurantFixture.done(v)).thenSucceed();
 }
 /** Tables side by side join: legs only at the outer corners of the joined board, a table of another wood joins too, anything else not. */
 @GameTest(template="empty") public static void tablesJoinIntoOneBoard(GameTestHelper h){
  var l=h.getLevel();var row=new BlockPos(1,2,1);var woods=List.of("oak","spruce","dark_oak");
  for(int i=0;i<3;i++){var p=h.absolutePos(row.east(i));l.setBlock(p,table(woods.get(i)).joined(l,p),3);}
  var west=l.getBlockState(h.absolutePos(row));var mid=l.getBlockState(h.absolutePos(row.east()));var east=l.getBlockState(h.absolutePos(row.east(2)));
  h.assertTrue(west.getValue(TableBlock.EAST)&&!west.getValue(TableBlock.WEST)&&mid.getValue(TableBlock.EAST)&&mid.getValue(TableBlock.WEST)&&east.getValue(TableBlock.WEST)&&!east.getValue(TableBlock.EAST),"A row of three joins, woods mixed: "+west+" "+mid+" "+east);
  var midShape=mid.getShape(l,h.absolutePos(row.east()));
  h.assertTrue(midShape.min(Direction.Axis.Y)>=TableBlock.TOP/16.0-1e-6,"The middle of a row has no legs: "+midShape);
  var westShape=west.getShape(l,h.absolutePos(row));
  h.assertTrue(inside(westShape,.15,.4,.15)&&inside(westShape,.15,.4,.85)&&!inside(westShape,.85,.4,.15),"The west end stands on its two west legs");
  // A square of four: one leg under each outer corner.
  var sq=new BlockPos(1,2,4);for(var o:List.of(BlockPos.ZERO,new BlockPos(1,0,0),new BlockPos(0,0,1),new BlockPos(1,0,1))){var p=h.absolutePos(sq.offset(o));l.setBlock(p,table("birch").joined(l,p),3);}
  for(var o:List.of(BlockPos.ZERO,new BlockPos(1,0,0),new BlockPos(0,0,1),new BlockPos(1,0,1))){var p=h.absolutePos(sq.offset(o));var s=l.getBlockState(p);
   int legs=0;for(double x:new double[]{.15,.85})for(double z:new double[]{.15,.85})if(inside(s.getShape(l,p),x,.4,z))legs++;
   h.assertTrue(legs==1,"A table of the square stands on one leg: "+o+" "+s);}
  // Breaking one of the row splits it; a fence beside a table joins nothing.
  l.destroyBlock(h.absolutePos(row.east()),false);
  h.assertTrue(!l.getBlockState(h.absolutePos(row)).getValue(TableBlock.EAST)&&!l.getBlockState(h.absolutePos(row.east(2))).getValue(TableBlock.WEST),"Broken apart, each end has four legs again");
  l.setBlock(h.absolutePos(row.east()),Blocks.OAK_FENCE.defaultBlockState(),3);
  h.assertTrue(!l.getBlockState(h.absolutePos(row)).getValue(TableBlock.EAST),"A fence is no table");
  var n=table("oak").defaultBlockState().setValue(TableBlock.NORTH,true);
  h.assertTrue(n.rotate(Rotation.CLOCKWISE_90).getValue(TableBlock.EAST)&&!n.rotate(Rotation.CLOCKWISE_90).getValue(TableBlock.NORTH),"Turned with a structure");
  h.succeed();
 }
 /** The board's top face is sturdy: a lantern, a candle and a carpet stand on it and stay. */
 @GameTest(template="empty",timeoutTicks=40) public static void aLanternStandsOnATable(GameTestHelper h){
  var l=h.getLevel();var t=h.absolutePos(new BlockPos(2,2,2));l.setBlock(t,table("spruce").defaultBlockState(),3);
  h.assertTrue(l.getBlockState(t).isFaceSturdy(l,t,Direction.UP),"The top is sturdy");
  h.setBlock(new BlockPos(2,3,2),Blocks.LANTERN.defaultBlockState());
  h.assertTrue(Blocks.LANTERN.defaultBlockState().canSurvive(l,t.above())&&Blocks.CANDLE.defaultBlockState().canSurvive(l,t.above())&&Blocks.WHITE_CARPET.defaultBlockState().canSurvive(l,t.above()),"A lantern, a candle, a carpet may stand on it");
  // A neighbour's update makes the lantern check its support: it stays.
  h.setBlock(new BlockPos(3,2,2),table("oak").defaultBlockState());
  h.runAfterDelay(10,()->{h.assertBlockPresent(Blocks.LANTERN,new BlockPos(2,3,2));h.succeed();});
 }
 /** The restaurant of every level seats its diners on chairs at tables (4/8/8/12/12/16), joined in pairs; the cottage has a table and a
  *  chair under its jetty, the big house a table and two chairs; the kits of the houses and of the restaurant stand where they stood. */
 @GameTest(template="empty") public static void theDesignsAreFurnished(GameTestHelper h){
  for(int level=1;level<=6;level++){var layout=BuildingPlacement.layout(BuildingTiers.layoutId("restaurant",level),BlockPos.ZERO,0);
   var cells=Dining.seatCells("restaurant");int chairs=0;
   for(int seat=0;seat<cells.size();seat++){var s=layout.getOrDefault(cells.get(seat),Blocks.AIR.defaultBlockState());var g=seat/2;
    if(g>=Dining.groups(level)){h.assertTrue(!(s.getBlock() instanceof StairBlock),"No stair seat left at "+level);continue;}
    h.assertTrue(s.getBlock() instanceof ChairBlock c&&c.wood().equals(Furniture.RESTAURANT_WOOD)&&s.getValue(ChairBlock.FACING)==(seat%2==0?Direction.EAST:Direction.WEST),"Level "+level+" seat "+seat+" is a chair facing its table: "+s);chairs++;
    var t=VillageStyle.RESTAURANT_TABLES[g];var ts=layout.get(new BlockPos(t[0],1,t[1]));boolean north=VillageStyle.restaurantJoinsNorth(g);
    h.assertTrue(ts!=null&&ts.getBlock() instanceof TableBlock&&ts.getValue(TableBlock.NORTH)==north&&ts.getValue(TableBlock.SOUTH)==!north,"Level "+level+" table "+g+" joins its pair: "+ts);}
   h.assertTrue(chairs==Dining.designSeats(level)&&chairs==CoreEffects.value("restaurant","seats",level),"Level "+level+" seats "+chairs+" = "+Dining.designSeats(level));}
  for(var type:List.of("home","home_2"))for(int level=1;level<=6;level++){var layout=BuildingPlacement.layout(BuildingTiers.layoutId(type,level),BlockPos.ZERO,0);
   long chairs=layout.values().stream().filter(s->s.getBlock() instanceof ChairBlock).count(),tables=layout.values().stream().filter(s->s.getBlock() instanceof TableBlock).count();
   h.assertTrue(tables==1&&chairs==(type.equals("home")?1:2),type+"@"+level+": "+tables+" table, "+chairs+" chairs");}
  for(var type:List.of("home","home_2","restaurant"))
   // AD-148: a frozen design's equipment no longer follows its furniture at all (FrozenEquipmentGameTests keeps its cells free).
   h.assertTrue(LevelArchitecture.frozen(type)||LevelArchitecture.equipmentBeforeFurniture(type).equals(LevelArchitecture.equipment(type)),"The furniture moved no kit of "+type+": before "+LevelArchitecture.equipmentBeforeFurniture(type).stream().map(p->p.local().toShortString()+"@"+p.level()).toList()+" now "+LevelArchitecture.equipment(type).stream().map(p->p.local().toShortString()+"@"+p.level()).toList());
  h.succeed();
 }
 /** Owner rule 2026-09-23 in every design AD-143 touched: beside a door, at both its halves, a full block — no pane, fence, bars, wall or
  *  trapdoor. */
 @GameTest(template="empty") public static void doorJambsAreFullBlocks(GameTestHelper h){
  var l=h.getLevel();var problems=new ArrayList<String>();
  for(var type:List.of("home","home_2","restaurant"))for(int level=1;level<=6;level++){var id=BuildingTiers.layoutId(type,level);var layout=BuildingPlacement.layout(id,BlockPos.ZERO,0);
   for(var en:layout.entrySet()){var s=en.getValue();if(!(s.getBlock() instanceof DoorBlock)||s.getValue(DoorBlock.HALF)!=net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)continue;
    var side=s.getValue(DoorBlock.FACING).getClockWise();
    for(int y=0;y<=1;y++)for(var d:List.of(side,side.getOpposite())){var p=en.getKey().above(y).relative(d);var n=layout.getOrDefault(p,Blocks.AIR.defaultBlockState());
     if(!n.isCollisionShapeFullBlock(l,BlockPos.ZERO)||n.getBlock() instanceof IronBarsBlock||n.getBlock() instanceof FenceBlock||n.getBlock() instanceof WallBlock||n.getBlock() instanceof TrapDoorBlock||n.getBlock() instanceof FenceGateBlock)
      problems.add(id+" door "+en.getKey()+" beside it "+p+": "+n);}}}
  h.assertTrue(problems.isEmpty(),"Door jambs: "+problems);
  h.succeed();
 }
 /** An old hall's stair seat and fence table still seat a diner, and the builders do not swap old furniture for new. */
 @GameTest(template="empty") public static void oldHallsKeepTheirStairSeats(GameTestHelper h){
  var stair=Blocks.SPRUCE_STAIRS.defaultBlockState();var fence=Blocks.SPRUCE_FENCE.defaultBlockState();
  var c=chair("spruce").defaultBlockState();var t=table("spruce").defaultBlockState();
  h.assertTrue(Dining.seat(stair)&&Dining.seat(c)&&Dining.table(fence)&&Dining.table(t)&&Dining.table(Blocks.BARREL.defaultBlockState()),"Stairs and chairs seat, fences, barrels and tables serve");
  h.assertTrue(BuildingRepairs.present(stair,c)&&BuildingRepairs.present(fence,t)&&BuildingRepairs.present(chair("oak").defaultBlockState(),c),"Old or other furniture stands for the design's");
  h.assertTrue(!BuildingRepairs.present(Blocks.AIR.defaultBlockState(),c)&&!BuildingRepairs.present(Blocks.STONE.defaultBlockState(),t),"A missing chair is missing");
  // In the world: a level-I hall whose first seat is a stair by a fence still counts it.
  var v=RestaurantFixture.village(h,1);
  try{var b=v.kept();v.l().setBlock(Dining.seatPos(v.e(),b,0),stair.setValue(StairBlock.FACING,Direction.WEST),2);v.l().setBlock(Dining.tablePos(v.e(),b,0),fence,2);Dining.forgetSeats();
   h.assertTrue(Dining.standing(v.l(),v.e(),b,0)&&Dining.seats(v.l(),v.e(),b)==4,"The old seat still counts: "+Dining.seats(v.l(),v.e(),b));
  }finally{RestaurantFixture.done(v);}
  h.succeed();
 }
 /** The village makes every chair and table from its own wood (bamboo only from bamboo it holds) and the estimate prices each. */
 @GameTest(template="empty",timeoutTicks=200) public static void theVillageMakesAndPricesAllFurniture(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   h.assertTrue(!Workshops.producible(t.l,t.e,Furniture.chairId("spruce")),"Without wood the village makes no chair");
   t.s.addBuilding(new Settlement.Building(Settlement.childId(t.s.id(),"record/forester"),ForesterHut.TYPE,-60,0,40));
   for(var wood:Furniture.WOODS)for(var id:List.of(Furniture.chairId(wood),Furniture.tableId(wood))){
    h.assertTrue(BuildingTiers.value(id)==(Furniture.chair(id)?2:3),"The estimate prices "+id);
    if(!wood.equals("bamboo"))h.assertTrue(Workshops.producible(t.l,t.e,id),"The forester's wood makes "+id);}
   h.assertTrue(!Workshops.producible(t.l,t.e,Furniture.chairId("bamboo")),"Nobody grows bamboo");
   LogisticsRoutes.chest(t.l,t.e,t.hall()).setItem(0,new ItemStack(Items.BAMBOO_BLOCK,2));
   h.assertTrue(Workshops.producible(t.l,t.e,Furniture.chairId("bamboo"))&&Workshops.producible(t.l,t.e,Furniture.tableId("bamboo")),"Bamboo in the hall makes bamboo furniture");
   var chairs=net.minecraft.tags.ItemTags.create(new ResourceLocation(Furniture.CHAIRS));var tables=net.minecraft.tags.ItemTags.create(new ResourceLocation(Furniture.TABLES));
   h.assertTrue(Workshops.spec("carpentry").outputTags().contains(chairs)&&Workshops.spec("carpentry").outputTags().contains(tables),"The carpentry makes furniture");
   h.assertTrue(Workshops.hallCrafts("engineering.1").containsAll(List.of("#"+Furniture.CHAIRS,"#"+Furniture.TABLES)),"Engineering I teaches the hall furniture");
  }finally{ResearchV2Town.done(t);}
  h.succeed();
 }

 /** A resident of the big house with nothing to do climbs to the table upstairs, sits on a chair of its home, rests and stands up; the seat
  *  goes with it. */
 @GameTest(template="empty",timeoutTicks=900) public static void aResidentRestsOnAChairAtHome(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());
  for(int x=-2;x<14;x++)for(int z=-2;z<12;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<17;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var house=new Settlement.Building(Settlement.childId(s.id(),"building/home_2"),"home_2",0,0,0);s.addBuilding(house);s.addHome(new Settlement.Home(house.id(),1,4,true));
  var e=new org.villageastra.server.SettlementData.Entry(s,l.dimension().location().toString(),center);org.villageastra.server.SettlementData.get(l.getServer()).add(e);
  for(var cell:BuildingPlacement.layout(e,house,"home_2").entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  var r=new Resident(Settlement.childId(s.id(),"adult/rest"),Resident.Life.ADULT,false,null,null,-1);s.admit(r,house.id());
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),r);var at=BuildingPlacement.at(e,house,7,5,5);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
  npc.onlyGoals(g->g instanceof net.minecraft.world.entity.ai.goal.FloatGoal,7,new HomeRestGoal(npc,()->11000L).eager(60));l.addFreshEntity(npc);
  boolean[] sat={false};
  h.onEachTick(()->{if(npc.getVehicle() instanceof SeatEntity seat&&l.getBlockState(seat.chair()).getBlock() instanceof ChairBlock&&"resting".equals(npc.workStatus()))sat[0]=true;});
  h.succeedWhen(()->{h.assertTrue(sat[0],"Not seated yet: "+npc.workStatus()+" at "+npc.blockPosition().toShortString());
   h.assertTrue(!SeatEntity.seated(npc)&&l.getEntitiesOfClass(SeatEntity.class,new AABB(center).inflate(16)).isEmpty(),"Rested and stood up, no seat left");
   npc.discard();org.villageastra.server.SettlementData.get(l.getServer()).remove(s.id());});
 }

 /** A new resident of a house appears in free air on a floor at every level (the cottage's door jambs became posts: its people now appear on
  *  the porch, not in the wall where they would suffocate). */
 @GameTest(template="empty") public static void newResidentsAppearInFreeAir(GameTestHelper h){
  var l=h.getLevel();var problems=new ArrayList<String>();
  for(var type:List.of("home","home_2"))for(int level=1;level<=6;level++){var layout=BuildingPlacement.layout(BuildingTiers.layoutId(type,level),BlockPos.ZERO,0);
   for(int n=0;n<2;n++){var c=HousingLadder.spawnCell(type,n);var air=Blocks.AIR.defaultBlockState();
    var feet=layout.getOrDefault(c,air);var head=layout.getOrDefault(c.above(),air);var floor=layout.getOrDefault(c.below(),air);
    if(!feet.getCollisionShape(l,BlockPos.ZERO).isEmpty()||!head.getCollisionShape(l,BlockPos.ZERO).isEmpty()||!floor.isCollisionShapeFullBlock(l,BlockPos.ZERO))
     problems.add(type+"@"+level+" "+c.toShortString()+": "+floor+"/"+feet+"/"+head);}}
  h.assertTrue(problems.isEmpty(),"Spawn cells: "+problems);
  h.succeed();
 }
}
