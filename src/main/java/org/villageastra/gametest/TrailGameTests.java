package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-123 (Roads II, C10): a trail to a neighbour's gate is its own project for a crew of one builder, laid with hall materials through the journal,
 *  refused at water without Roads V, registered as trail cells that village upkeep leaves alone, and it speeds a caravan on the gate line. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class TrailGameTests {
 record Pair(ServerLevel l,Settlement a,SettlementData.Entry e,SettlementData.Entry b,Settlement.Building hall,BlockPos center,BlockPos from,BlockPos to){
  /** Cells between the two gates. */
  int length(){return Math.abs(to.getX()-from.getX());}
 }
 /** Two villages 64 blocks apart on flat grass cleared high above (earlier batches may have built there); the first has a hall chest full of trail material and one builder. */
 static Pair pair(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,16));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  for(int x=-4;x<80;x++)for(int z=-8;z<9;z++){l.setBlock(center.offset(x,-2,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);// The GameTest world has real terrain: everything above the slab goes, up to the column's own top (hills, sea water).
   int top=Math.max(center.getY()+32,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,center.getX()+x,center.getZ()+z));
   for(int y=center.getY()+1;y<=top;y++)l.setBlock(new BlockPos(center.getX()+x,y,center.getZ()+z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.DIRT,64));chest.setItem(1,new ItemStack(Items.OAK_PLANKS,64));chest.setItem(2,new ItemStack(Items.OAK_FENCE,64));chest.setItem(3,new ItemStack(Items.LANTERN,16));chest.setItem(4,new ItemStack(Items.COBBLESTONE,16));
  var home=new Settlement.Home(UUID.randomUUID(),1,1,true);s.addHome(home);var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home.id());s.assign(r.id(),Profession.BUILDER,hall.id());
  var other=new Settlement(UUID.randomUUID());var b=new SettlementData.Entry(other,l.dimension().location().toString(),center.offset(64,0,0));SettlementData.get(l.getServer()).add(b);
  return new Pair(l,s,e,b,hall,center,Caravans.gate(l,e,b.center()),Caravans.gate(l,b,center));
 }
 static void learn(Pair p,String... nodes){var record=BookResearch.inspect(p.l,p.e);var done=record.getList("legacyDone",Tag.TAG_STRING);for(var n:nodes)done.add(StringTag.valueOf(n));record.put("legacyDone",done);BookResearch.store(p.l,p.e,record);ResearchKnobs.forget(p.a.id());}
 static void done(Pair p){try{java.nio.file.Files.deleteIfExists(Trails.path(p.l,p.a.id()));java.nio.file.Files.deleteIfExists(Roads.projectPath(p.l,p.a.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  SettlementData.get(p.l.getServer()).remove(p.a.id());SettlementData.get(p.l.getServer()).remove(p.b.settlement().id());}
 /** Runs the crew until the trail is finished or stopped (or the step budget ends); returns the last state. */
 static String run(Pair p,int steps){String state="";for(int i=0;i<steps;i++){state=Trails.step(p.l,p.e,8);if(state.equals(Trails.COMPLETE)||state.equals(Trails.BLOCKED))break;}return state;}
 /** The ground cell k steps from this village's gate along the trail (the route runs along +x). */
 static BlockPos at(Pair p,int k){return new BlockPos(p.from.getX()+k,p.center.getY(),p.from.getZ());}
 static String why(Pair p){var t=Trails.record(p.l,p.a.id());var out=new StringBuilder(t==null?"no record":t.getString("state")+"/"+t.getString("reason")+" laid "+t.getInt("laid")+" skipped "+t.getInt("skipped")+" walk "+t.getInt("walk"));
  out.append(" gates ").append(p.from.toShortString()).append(" -> ").append(p.to.toShortString()).append(" center ").append(p.center.toShortString());
  if(t!=null&&t.contains("blockedAt")){var b=BlockPos.of(t.getLong("blockedAt"));out.append(" at ").append(b.toShortString()).append(" k=").append(b.getX()-p.from.getX());
   out.append(" top ").append(p.l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,b.getX(),b.getZ())).append(" states ");for(int y=-2;y<=2;y++)out.append(p.l.getBlockState(b.atY(p.center.getY()+y)).getBlock().getDescriptionId()).append(',');}
  return out.toString();}
 @GameTest(template="empty",batch="trail_research",timeoutTicks=100) public static void trailNeedsRoadsTwo(GameTestHelper h){
  var p=pair(h);
  try{
   var target=p.b.settlement().id();
   h.assertTrue(Trails.order(p.l,p.e,target,0,false,List.of()).equals("research"),"Without Roads II no trail");
   learn(p,"roads.1","roads.2");
   h.assertTrue(Trails.order(p.l,p.e,target,1,true,List.of()).equals("research"),"Lamps still take Roads III");
   h.assertTrue(Trails.order(p.l,p.e,target,0,false,List.of(p.center.offset(30_000,0,0))).equals("distance")&&Trails.record(p.l,p.a.id())==null,"A waypoint far off (the client sends them) is refused before any route is built");
   h.assertTrue(Trails.order(p.l,p.e,target,0,false,List.of()).isEmpty(),"With Roads II the crew sets out");
   h.assertTrue(Trails.order(p.l,p.e,target,0,false,List.of()).equals("busy"),"One trail at a time");
   h.assertTrue(Trails.order(p.l,p.e,p.a.id(),0,false,List.of()).equals("target"),"Not to itself");
  }finally{done(p);}
  h.succeed();
 }
 @GameTest(template="empty",batch="trail_water",timeoutTicks=200) public static void trailRefusesWaterWithoutRoadsFive(GameTestHelper h){
  var p=pair(h);
  try{
   int mid=p.length()/2;for(int k=mid-1;k<=mid+1;k++)for(int z=-8;z<9;z++)p.l.setBlock(at(p,k).offset(0,0,z),Blocks.WATER.defaultBlockState(),2);
   learn(p,"roads.1","roads.2");h.assertTrue(Trails.order(p.l,p.e,p.b.settlement().id(),0,false,List.of()).isEmpty(),"Ordered");
   var state=run(p,60);var t=Trails.record(p.l,p.a.id());
   h.assertTrue(state.equals(Trails.BLOCKED)&&t.getString("reason").equals("needs_bridge"),"The river stops the trail: "+why(p));
   h.assertTrue(t.getInt("laid")>0&&p.l.getBlockState(at(p,mid-3)).is(Blocks.DIRT_PATH)&&p.l.getBlockState(at(p,mid)).is(Blocks.WATER),"The cells before the river are laid, the river is untouched: "+why(p));
   h.assertTrue(!Trails.onTrail(p.l,p.e,t.getUUID("builder")),"The builder is free again");
  }finally{done(p);}
  h.succeed();
 }
 /** Trail cells may lie outside the atlas area, where no village road may be ordered; they carry the trail mark. */
 @GameTest(template="empty",batch="trail_area",timeoutTicks=100) public static void trailCellsOutsideAreaRegistered(GameTestHelper h){
  var p=pair(h);
  try{
   var far=p.center.offset(200,0,0);h.assertTrue(!Atlas.area(p.l,p.e).contains(new ChunkPos(far)),"The cell lies outside the atlas area");
   p.l.setBlock(far,Blocks.GRAVEL.defaultBlockState(),2);
   h.assertTrue(Roads.registerTrail(p.l,p.a.id(),far)&&Roads.cell(p.l,far).trail&&Roads.cell(p.l,far).village.equals(p.a.id()),"A trail cell outside the area is registered with the mark");
   learn(p,"roads.1");h.assertTrue(MapOrders.road(p.l,p.e,far,far.offset(4,0,0),1<<2).equals("outside"),"A village road there is still refused");
  }finally{done(p);}
  h.succeed();
 }
 /** A caravan on a gravel trail along its gate line gains ×1.2 (±0.01); half the line on gravel gives half that bonus. */
 @GameTest(template="empty",batch="trail_caravan",timeoutTicks=100) public static void caravanFasterOnGravelTrail(GameTestHelper h){
  var p=pair(h);
  try{
   var a=Caravans.gate(p.l,p.e,p.b.center());var b=Caravans.gate(p.l,p.b,p.center);
   h.assertTrue(Trails.routeBonus(p.l,a,b,p.a.id(),p.b.settlement().id())==0,"No road, no bonus");
   int n=Math.abs(b.getX()-a.getX());
   for(int k=0;k<=n/2;k++){var cell=a.offset(Integer.signum(b.getX()-a.getX())*k,0,0).atY(p.center.getY());p.l.setBlock(cell,Blocks.GRAVEL.defaultBlockState(),2);Roads.registerTrail(p.l,p.a.id(),cell);}
   double half=Trails.routeBonus(p.l,a,b,p.a.id(),p.b.settlement().id());
   for(int k=n/2+1;k<=n;k++){var cell=a.offset(Integer.signum(b.getX()-a.getX())*k,0,0).atY(p.center.getY());p.l.setBlock(cell,Blocks.GRAVEL.defaultBlockState(),2);Roads.registerTrail(p.l,p.a.id(),cell);}
   double full=Trails.routeBonus(p.l,a,b,p.a.id(),p.b.settlement().id());
   h.assertTrue(Math.abs(1+full-1.2)<0.01,"The whole line on gravel: ×"+(1+full));
   h.assertTrue(half>0.05&&half<0.15,"Half the line on gravel: +"+half);
   h.assertTrue(new CompoundTag().getDouble("routeBonus")==0,"An old caravan without the field travels at the old speed");
  }finally{done(p);}
  h.succeed();
 }
 /** The record, its cargo and the crew's place are on disk: a new read goes on where the last one stopped; an unknown schema is refused. */
 @GameTest(template="empty",batch="trail_reload",timeoutTicks=100) public static void trailSurvivesReload(GameTestHelper h){
  var p=pair(h);
  try{
   learn(p,"roads.1","roads.2");Trails.order(p.l,p.e,p.b.settlement().id(),0,false,List.of());
   Trails.step(p.l,p.e,1);Trails.step(p.l,p.e,1);var before=Trails.record(p.l,p.a.id());
   h.assertTrue(before.getString("state").equals(Trails.BUILD)&&!before.getList("cargo",Tag.TAG_COMPOUND).isEmpty(),"Surveyed and supplied: "+why(p));
   Trails.step(p.l,p.e,2);var mid=Trails.record(p.l,p.a.id());
   h.assertTrue(mid.getInt("index")>0&&mid.getInt("laid")>0&&mid.getUUID("builder").equals(before.getUUID("builder")),"The crew's place and builder are kept");
   h.assertTrue(run(p,60).equals(Trails.COMPLETE),"The crew finishes from where the record stood: "+why(p));
   var bad=Trails.record(p.l,p.a.id());bad.putInt("schema",2);Trails.save(p.l,p.a.id(),bad);
   boolean refused=false;try{Trails.record(p.l,p.a.id());}catch(IllegalStateException ex){refused=true;}h.assertTrue(refused,"An unknown schema is refused");
  }finally{done(p);}
  h.succeed();
 }
 @GameTest(template="empty",batch="trail_slot",timeoutTicks=100) public static void trailDoesNotBlockWallOrder(GameTestHelper h){
  var p=pair(h);
  try{
   learn(p,"roads.1","roads.2");h.assertTrue(Trails.order(p.l,p.e,p.b.settlement().id(),0,false,List.of()).isEmpty()&&Trails.active(p.l,p.a.id()),"The trail is under way");
   h.assertTrue(!Roads.active(p.l,p.a.id())&&Roads.order(p.l,p.e,Roads.clearing(p.l,p.e,List.of(p.center.offset(3,1,-3)))),"The village's own road slot stays free for walls and other orders");
  }finally{done(p);}
  h.succeed();
 }
 @GameTest(template="empty",batch="trail_upkeep",timeoutTicks=100) public static void trailCellsSkippedByVillageUpkeep(GameTestHelper h){
  var p=pair(h);
  try{
   var cell=p.center.offset(20,0,6);p.l.setBlock(cell,Blocks.DIRT_PATH.defaultBlockState(),2);Roads.registerTrail(p.l,p.a.id(),cell);Roads.cell(p.l,cell).wear=Roads.REPAIR_WEAR+10;
   h.assertTrue(Roads.maintenance(p.l,p.e)==null,"A worn trail cell is not the village's upkeep");
   var road=p.center.offset(22,0,6);p.l.setBlock(road,Blocks.DIRT_PATH.defaultBlockState(),2);Roads.register(p.l,p.a.id(),road);Roads.cell(p.l,road).wear=Roads.REPAIR_WEAR+10;
   var round=Roads.maintenance(p.l,p.e);h.assertTrue(round!=null&&round.getList("ops",Tag.TAG_COMPOUND).size()==1,"The worn village road is");
  }finally{done(p);}
  h.succeed();
 }
}
