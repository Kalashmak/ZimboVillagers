package org.villageastra.gametest;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-028: field-ordered houses on the paid construction queue. Site stays inside the 48×32×40 template. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BuildingOrderGameTests {
 private record Fixture(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos site){}
 private static Fixture fixture(GameTestHelper h){return fixture(h,16,16);}
 /** The pad cleared w x d blocks from the site (the livestock yard of AD-138 takes 17x25). */
 private static Fixture fixture(GameTestHelper h,int w,int d){
  var l=h.getLevel();var site=h.absolutePos(new BlockPos(4,3,4));var center=h.absolutePos(new BlockPos(40,3,30));
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(int x=-1;x<w;x++)for(int z=-1;z<d;z++){
   for(int y=-3;y<0;y++)l.setBlock(site.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(site.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<=20;y++)l.setBlock(site.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
  }
  return new Fixture(l,s,e,site);
 }
 private static Map<BlockPos,net.minecraft.world.level.block.state.BlockState> snapshot(Fixture f){
  var result=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
  for(int x=-1;x<16;x++)for(int z=-1;z<16;z++)for(int y=-3;y<=12;y++){var pos=f.site.offset(x,y,z);result.put(pos,f.l.getBlockState(pos));}
  return result;
 }
 /** Same order and journal operations as the builder, without walking. */
 private static void execute(Fixture f,CompoundTag state){
  var ops=state.getList("ops",Tag.TAG_COMPOUND);var id=state.getUUID("id");var origin=BlockPos.of(state.getLong("origin"));
  for(int i=0;i<ops.size();i++){
   var op=ops.getCompound(i);if(!BuildingOrders.reconcile(f.l,op,origin))throw new IllegalStateException("Unexpected drift at operation "+i);
   var step=HallConstructionPlan.step(op);if(step.before().equals(step.after()))continue;
   if(!WorldJournal.place(f.l,Settlement.childId(id,"block/"+i),step.pos(),step.before(),step.after()))throw new IllegalStateException("Operation "+i+" at "+step.pos()+" found "+f.l.getBlockState(step.pos()));
  }
 }
 /** The flower pots of the cottage's design (design review 2026-09-24, AD-148: more than one now), each paid as a real pot. */
 private static int pots(){return (int)BuildingBlueprints.layout("home",BlockPos.ZERO).values().stream().filter(st->st.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock).count();}
 @GameTest(template="empty",timeoutTicks=100) public static void surveyPlansPaidHouseWithFoundationAndTemporaryLadder(GameTestHelper h){
  var f=fixture(h);f.l.setBlock(f.site.offset(0,-1,0),Blocks.AIR.defaultBlockState(),2);f.l.setBlock(f.site.offset(3,1,3),Blocks.POPPY.defaultBlockState(),2);
  var before=snapshot(f);var survey=BuildingOrders.survey(f.l,f.e,"home",0,f.site);
  h.assertTrue(survey.ok(),"Flat supported site is orderable: "+survey.reason()+" "+survey.conflicts());
  h.assertTrue(snapshot(f).equals(before)&&!HallUpgradeGoal.exists(f.l,f.s.id()),"Survey changes no block and queues nothing");
  var cost=survey.state().getCompound("cost");
  h.assertTrue(cost.getInt("minecraft:cobblestone")==50,"49 floor cells plus one foundation fill: "+cost.getInt("minecraft:cobblestone"));
  h.assertTrue(cost.getInt("minecraft:oak_door")==1&&cost.getInt("minecraft:white_bed")==2&&cost.getInt("minecraft:chest")==1&&cost.getInt("minecraft:flower_pot")==pots(),"Two-cell blocks are paid once; chest and pot use real items: "+cost);
  h.assertTrue(cost.getInt("villageastra:timber_scaffold")>0&&cost.getInt("minecraft:ladder")==0,"Temporary scaffold, not a ladder, is part of the estimate: "+cost);
  var ops=survey.state().getList("ops",Tag.TAG_COMPOUND);long returns=ops.stream().filter(t->((CompoundTag)t).contains("return")).count();
  h.assertTrue(returns==cost.getInt("villageastra:timber_scaffold")&&HallConstructionPlan.step(ops.getCompound(0)).pos().equals(f.site.offset(3,1,3)),"Plants are cleared first and every scaffold is taken back");
  h.assertTrue(BuildingOrders.capacity("home")==2&&BuildingOrders.capacity("home_2")==4&&BuildingOrders.homeLevel("home_2")==2,"Housing capacity comes from real beds");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void ordersRequireLiveMayorNearbyAndOneActiveProject(GameTestHelper h){
  var f=fixture(h);var p=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"FieldOrderMayor"));
  p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(VillageAstra.MAYOR_SHOVEL.get()));p.setPos(f.site.getX()+3,f.site.getY()+1,f.site.getZ()-2);
  h.assertTrue(!MayorSurvey.mark(p,f.site,false),"Visitor with a copied shovel cannot mark a site");
  f.s.appointPlayerMayor(p.getUUID());h.assertTrue(MayorSurvey.mark(p,f.site,false),"Mayor marks the site");
  var view=new CompoundTag();MayorSurvey.addView(p,view);h.assertTrue(view.getBoolean("orderable")&&view.getBoolean("canOrder")&&view.getInt("orderItems")>0,"Palette shows an orderable estimate");var token=view.getUUID("id");
  h.assertTrue(!MayorSurvey.choose(p,UUID.randomUUID(),"home",0,3),"Forged token rejected");
  h.assertTrue(!MayorSurvey.choose(p,token,"forester",0,3)&&!HallUpgradeGoal.exists(f.l,f.s.id()),"Preview-only design cannot be ordered");
  h.assertTrue(!MayorSurvey.choose(p,token,"nothing_like_this",0,3)&&!HallUpgradeGoal.exists(f.l,f.s.id()),"A design that does not exist is never queued");
  p.setPos(f.site.getX()+3,f.site.getY()+1,f.site.getZ()-20);h.assertTrue(!MayorSurvey.choose(p,token,"home",0,3)&&!HallUpgradeGoal.exists(f.l,f.s.id()),"Order requires standing at the site");
  p.setPos(f.site.getX()+3,f.site.getY()+1,f.site.getZ()-2);h.assertTrue(MayorSurvey.choose(p,token,"home",0,3)&&HallUpgradeGoal.pending(f.l,f.s.id()),"Live mayor queues the paid project");
  var queued=HallUpgradeGoal.inspect(f.l,f.s.id());h.assertTrue(BuildingOrders.isBuilding(queued)&&!queued.getBoolean("funded")&&queued.getString("design").equals("home"),"Queue waits for real materials");
  h.assertTrue(BuildingOrders.survey(f.l,f.e,"masonry",0,f.site).reason().equals("busy"),"Only one active construction project per settlement");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void unsafeSitesAreRefused(GameTestHelper h){
  var f=fixture(h);
  h.assertTrue(BuildingOrders.survey(f.l,f.e,"farm",0,f.site).reason().equals("design")&&BuildingOrders.survey(f.l,f.e,"town_hall_2",0,f.site).reason().equals("design"),"Field areas and hall tiers are not field orders");
  h.assertTrue(BuildingOrders.survey(f.l,f.e,"home",4,f.site).reason().equals("rotation")&&BuildingOrders.survey(f.l,f.e,"home",-1,f.site).reason().equals("rotation"),"Only quarter turns 0-3 are orders (AD-068)");
  h.assertTrue(BuildingOrders.survey(f.l,f.e,"home",0,f.site.offset(-400,0,0)).reason().equals("reach"),"Sites over 150 blocks from settlement buildings are refused");
  for(int y=-1;y>=-4;y--)f.l.setBlock(f.site.offset(1,y,1),Blocks.AIR.defaultBlockState(),2);
  f.l.setBlock(f.site.offset(2,1,2),Blocks.WATER.defaultBlockState(),2);f.l.setBlock(f.site.offset(4,2,4),Blocks.DIRT.defaultBlockState(),2);
  var survey=BuildingOrders.survey(f.l,f.e,"home",0,f.site);
  h.assertTrue(!survey.ok()&&survey.reason().equals("conflicts"),"Deep void, water and terrain inside the volume block the order");
  h.assertTrue(survey.conflicts().contains(f.site.offset(1,-4,1))&&survey.conflicts().contains(f.site.offset(2,1,2))&&survey.conflicts().contains(f.site.offset(4,2,4)),"Each obstacle is shown as a red cell: "+survey.conflicts());
  var buffer=BuildingOrders.survey(f.l,f.e,"home",0,f.e.center().offset(-8,0,2));
  h.assertTrue(!buffer.ok()&&!buffer.conflicts().isEmpty(),"Existing building and its 3-block buffer are never cleared");
  h.succeed();
 }
 // A batch of its own and a pad as large as the largest design: the livestock yard (17x25) reached the next test's platform in a small batch
 // (-PgtOnly=order: conflicts at z23-24), green only where the batch left that ground empty.
 @GameTest(template="empty",timeoutTicks=100,batch="order_every_design") public static void everyOrderableDesignIsBuildableAndRegistersAWorkplace(GameTestHelper h){
  var problems=new ArrayList<String>();
  int w=16,d=16;for(var design:BuildingOrders.ORDERABLE){var size=BuildingPlacement.size(design,0);w=Math.max(w,size[0]+1);d=Math.max(d,size[1]+1);}
  for(var design:BuildingOrders.ORDERABLE.stream().sorted().toList()){
   var f=fixture(h,w,d);var survey=BuildingOrders.survey(f.l,f.e,design,0,f.site);
   if(!survey.ok()){var local=BuildingBlueprints.layout(design,BlockPos.ZERO);var bad=BuildingOrders.lastImpossible;problems.add(design+":"+survey.reason()+(survey.reason().equals("access")&&bad!=null?"@"+bad.toShortString()+"="+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(local.get(bad).getBlock()).getPath():"")+survey.conflicts().stream().map(c->c.subtract(f.site).toShortString()).toList());if(bad!=null){var slice=new StringBuilder(" slice:");for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){slice.append("[").append(dx).append(",").append(dz).append(":");for(int y=3;y<=14;y++){var st=local.get(bad.offset(dx,0,dz).atY(y));slice.append(st==null?".":st.isAir()?"_":st.isSolid()?"#":"o");}slice.append("]");}problems.add(slice.toString());}BuildingOrders.lastImpossible=null;continue;}
   HallUpgradeGoal.enqueue(f.l,f.e,survey.state());var state=HallUpgradeGoal.inspect(f.l,f.s.id());execute(f,state);
   if(!BuildingOrders.complete(f.l,f.e,state))problems.add(design+":geometry");
   long scaffolds=state.getList("ops",10).stream().filter(t->((net.minecraft.nbt.CompoundTag)t).getString("return").equals("villageastra:timber_scaffold")).count();
   if(scaffolds!=state.getCompound("cost").getInt("villageastra:timber_scaffold"))problems.add(design+":scaffold-return");
   for(int x=-2;x<w;x++)for(int z=-2;z<d;z++)for(int y=0;y<=16;y++)if(f.l.getBlockState(f.site.offset(x,y,z)).is(VillageAstra.TIMBER_SCAFFOLD.get()))problems.add(design+":temporary-left@"+x+","+y+","+z);
   var id=BuildingOrders.buildingId(state);if(f.s.buildings().stream().noneMatch(b->b.id().equals(id)&&b.type().equals(design)))problems.add(design+":registration");
   if(BuildingOrders.HOUSING.contains(design)!=f.s.homes().stream().anyMatch(x->x.id().equals(id)))problems.add(design+":housing");
   // Next design uses a fresh fixture on the same pad: remove the finished project and this settlement's buildings from protection.
   com.mojang.logging.LogUtils.getLogger().info("ASTRA_ORDER_DESIGN checked={} problems={}",design,problems.size());
   SettlementData.get(f.l.getServer()).remove(f.s.id());
   try{java.nio.file.Files.deleteIfExists(f.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+f.s.id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  }
  h.assertTrue(problems.isEmpty(),"Orderable designs must build and register: "+problems);h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void completedHouseRegistersHousingProtectionAndReload(GameTestHelper h){complete(h,"home");}
 @GameTest(template="empty",timeoutTicks=100) public static void completedLargeHouseUsesAndRemovesScaffolds(GameTestHelper h){complete(h,"home_2");}
 private static void complete(GameTestHelper h,String design){
  var f=fixture(h);var survey=BuildingOrders.survey(f.l,f.e,design,0,f.site);h.assertTrue(survey.ok(),design+" orderable: "+survey.reason()+" "+survey.conflicts());
  HallUpgradeGoal.enqueue(f.l,f.e,survey.state());var state=HallUpgradeGoal.inspect(f.l,f.s.id());
  execute(f,state);
  for(int x=-2;x<16;x++)for(int z=-2;z<16;z++)for(int y=0;y<=16;y++)h.assertTrue(!f.l.getBlockState(f.site.offset(x,y,z)).is(VillageAstra.TIMBER_SCAFFOLD.get()),"Scaffold removed at "+x+","+y+","+z);
  h.assertTrue(BuildingOrders.complete(f.l,f.e,state)&&BuildingOrders.complete(f.l,f.e,state),"Completion verifies geometry and is idempotent");
  var id=BuildingOrders.buildingId(state);
  h.assertTrue(f.s.buildings().stream().filter(b->b.id().equals(id)&&b.type().equals(design)).count()==1&&f.s.homes().stream().filter(x->x.id().equals(id)&&x.capacity()==BuildingOrders.capacity(design)&&x.usable()).count()==1,"One building and one usable home registered");
  h.assertTrue(OwnershipEvents.disallowedPlacement(f.l,f.site.offset(3,1,3))&&OwnershipEvents.disallowedPlacement(f.l,f.site.offset(-3,0,0)),"New house and its buffer are protected");
  h.assertTrue(BuildingIntegrity.home(f.l,f.site,design)==BuildingIntegrity.Result.USABLE,"Built "+design+" passes housing integrity");
  var loaded=SettlementData.load(SettlementData.get(f.l.getServer()).save(new CompoundTag())).entry(f.s.id());
  h.assertTrue(loaded.settlement().buildings().stream().anyMatch(b->b.id().equals(id)&&b.type().equals(design))&&loaded.settlement().homes().stream().anyMatch(x->x.id().equals(id)),"Registration survives save/load");
  f.l.setBlock(f.site.offset(2,1,3),Blocks.AIR.defaultBlockState(),3);
  h.assertTrue(BuildingIntegrity.home(f.l,f.site,design)==BuildingIntegrity.Result.DAMAGED,"Broken bed makes "+design+" unusable");
  h.succeed();
 }
 /** AD-030/AD-028: whatever the plan builds, the next ground operation must stay reachable for a worker standing at the previous one. */
 @GameTest(template="empty",timeoutTicks=400) public static void largeHousePlanKeepsEveryGroundOperationReachable(GameTestHelper h){
  var f=fixture(h);var survey=BuildingOrders.survey(f.l,f.e,"home_2",0,f.site);h.assertTrue(survey.ok(),"home_2 orderable: "+survey.reason());
  var state=survey.state();var ops=state.getList("ops",Tag.TAG_COMPOUND);var origin=BlockPos.of(state.getLong("origin"));var id=state.getUUID("id");
  var worker=VillageAstra.RESIDENT.get().create(f.l);worker.setNoAi(true);worker.moveTo(f.site.getX()-1.5,f.site.getY()+1,f.site.getZ()-1.5,0,0);f.l.addFreshEntity(worker);
  int[] levels={origin.getY()-2,origin.getY()-1,origin.getY(),origin.getY()+1};
  var at=worker.blockPosition();int checked=0;
  for(int i=0;i<ops.size();i++){
   var op=ops.getCompound(i);var step=HallConstructionPlan.step(op);
   if(!op.contains("stand")&&!step.before().equals(step.after())&&step.pos().getY()<=origin.getY()+1){
    // Every so often the builder starts from outside, as it does after working in a scaffold column beside the wall.
    var from=checked%25==24?new BlockPos(f.site.getX()-1,f.site.getY()+1,f.site.getZ()-1):at;
    worker.moveTo(from.getX()+.5,from.getY(),from.getZ()+.5,0,0);worker.setOnGround(true);
    var stand=HallUpgradeGoal.stand(f.l,worker,step.pos(),levels);
    h.assertTrue(stand!=null,"Operation "+i+" at "+step.pos().toShortString()+" ("+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(step.after().getBlock()).getPath()+") is unreachable from "+from.toShortString()+": "+HallUpgradeGoal.lastStand);
    at=stand;checked++;}
   if(!step.before().equals(step.after())&&!WorldJournal.place(f.l,Settlement.childId(id,"block/"+i),step.pos(),step.before(),step.after()))throw new GameTestAssertException("Operation "+i+" at "+step.pos()+" found "+f.l.getBlockState(step.pos()));
  }
  h.assertTrue(checked>50,"Ground operations were really checked: "+checked);
  worker.discard();h.succeed();
 }
 /** AD-030: scaffold tops carry the builder, so a worker inside a column is standing and must still get a work position instead of counting as airborne. */
 @GameTest(template="empty",timeoutTicks=100) public static void builderInsideAScaffoldColumnStillFindsAWorkPosition(GameTestHelper h){
  var f=fixture(h);var column=f.site.offset(5,1,5);var target=f.site.offset(8,1,5);
  for(int y=0;y<4;y++)f.l.setBlock(column.above(y),VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState(),3);
  var worker=VillageAstra.RESIDENT.get().create(f.l);worker.setNoAi(true);
  worker.moveTo(column.getX()+.5,column.getY(),column.getZ()+.5,0,0);f.l.addFreshEntity(worker);worker.setOnGround(false);
  int[] levels={f.site.getY()+1,f.site.getY()+2};
  h.assertTrue(HallUpgradeGoal.stand(f.l,worker,target,levels)!=null,"At the foot of a column the builder finds a work position: "+HallUpgradeGoal.lastStand);
  worker.moveTo(column.getX()+.5,column.getY()+1,column.getZ()+.5,0,0);worker.setOnGround(false);
  h.assertTrue(HallUpgradeGoal.stand(f.l,worker,target,new int[]{f.site.getY()+1,f.site.getY()+2})!=null,"One level up the column still carries the builder: "+HallUpgradeGoal.lastStand);
  // Walls and neighbouring columns must still leave a free cell to step out to, in the direction of the work.
  worker.moveTo(column.getX()+.5,column.getY(),column.getZ()+.5,0,0);
  for(int y=0;y<3;y++){f.l.setBlock(column.offset(0,y,-1),Blocks.OAK_PLANKS.defaultBlockState(),3);f.l.setBlock(column.offset(-1,y,0),Blocks.OAK_PLANKS.defaultBlockState(),3);}
  for(int y=0;y<4;y++)f.l.setBlock(column.offset(0,y,1),VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState(),3);
  var out=HallUpgradeGoal.exit(f.l,worker,target);
  h.assertTrue(out!=null&&!f.l.getBlockState(out).is(VillageAstra.TIMBER_SCAFFOLD.get())&&out.getX()>column.getX(),"The builder leaves the column towards the work: "+out);
  worker.discard();h.succeed();
 }
 /** AD-030: half-built home_2 is a forest of columns — a builder standing in one really gets out and reaches the next wall cell. */
 @GameTest(template="empty",timeoutTicks=300) public static void builderLeavesAColumnOnTheCrowdedSite(GameTestHelper h){
  var f=fixture(h);var survey=BuildingOrders.survey(f.l,f.e,"home_2",0,f.site);h.assertTrue(survey.ok(),"home_2 orderable: "+survey.reason());
  HallUpgradeGoal.enqueue(f.l,f.e,survey.state());
  var state=HallUpgradeGoal.inspect(f.l,f.s.id());var ops=state.getList("ops",Tag.TAG_COMPOUND);var origin=BlockPos.of(state.getLong("origin"));
  // The site is built forward until the builder really stands among columns — that is the moment this test is about.
  var columns=new ArrayList<BlockPos>();int applied=0;
  for(int done=40;done<=ops.size()&&columns.isEmpty();done+=40){
   applied=HallUpgradeGoal.advanceForProbe(f.l,f.s.id(),done);
   for(int x=-2;x<16;x++)for(int z=-2;z<16;z++)for(int y=1;y<14;y++){var c=f.site.offset(x,y,z);
    if(f.l.getBlockState(c).is(VillageAstra.TIMBER_SCAFFOLD.get())&&!f.l.getBlockState(c.below()).is(VillageAstra.TIMBER_SCAFFOLD.get()))columns.add(c);}
  }
  state=HallUpgradeGoal.inspect(f.l,f.s.id());ops=state.getList("ops",Tag.TAG_COMPOUND);
  h.assertTrue(applied>=40,"The fixture really builds the site forward: "+applied+" operations");
  var target=(BlockPos)null;
  for(int i=state.getInt("index");i<ops.size()&&target==null;i++){var step=HallConstructionPlan.step(ops.getCompound(i));
   if(!ops.getCompound(i).contains("stand")&&!step.before().equals(step.after()))target=step.pos();}
  h.assertTrue(target!=null&&!columns.isEmpty(),"The half-built site really has a standing column and work left: "+columns.size()+" "+target);
  var worker=VillageAstra.RESIDENT.get().create(f.l);worker.moveTo(columns.get(0).getX()+.5,columns.get(0).getY(),columns.get(0).getZ()+.5,0,0);f.l.addFreshEntity(worker);
  // Whatever column the builder happens to stand in, it must have a way out.
  var trapped=new ArrayList<BlockPos>();
  for(var c:columns){worker.moveTo(c.getX()+.5,c.getY(),c.getZ()+.5,0,0);if(HallUpgradeGoal.exit(f.l,worker,target)==null)trapped.add(c);}
  h.assertTrue(trapped.isEmpty(),"No column traps the builder, trapped: "+trapped);
  var cell=columns.get(0);worker.moveTo(cell.getX()+.5,cell.getY(),cell.getZ()+.5,0,0);
  var out=HallUpgradeGoal.exit(f.l,worker,target);
  h.assertTrue(out!=null,"A free cell beside the crowded column exists");
  final BlockPos work=target;
  h.startSequence().thenExecuteFor(60,()->{if(f.l.getBlockState(worker.blockPosition()).is(VillageAstra.TIMBER_SCAFFOLD.get()))HallUpgradeGoal.stepOut(f.l,worker,out);})
   .thenExecute(()->{
    h.assertTrue(!f.l.getBlockState(worker.blockPosition()).is(VillageAstra.TIMBER_SCAFFOLD.get()),"The builder really left the column, standing at "+worker.blockPosition().toShortString());
    h.assertTrue(!worker.isInWall(),"Leaving a column never pushes the builder into a block: "+worker.blockPosition().toShortString());
    worker.setOnGround(true);
    var stand=HallUpgradeGoal.stand(f.l,worker,work,new int[]{origin.getY()-2,origin.getY()-1,origin.getY(),origin.getY()+1});
    h.assertTrue(stand!=null,"From outside the column the wall cell is reachable: "+HallUpgradeGoal.lastStand);
    worker.discard();SettlementData.get(f.l.getServer()).remove(f.s.id());
    try{java.nio.file.Files.deleteIfExists(f.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+f.s.id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
   }).thenSucceed();
 }
}
