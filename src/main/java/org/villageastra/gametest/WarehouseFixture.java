package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-147: a village for the warehouse's GameTests - a hall with its chest, the warehouse standing on its 23x17 lot at a level (its whole plan:
 *  pages, stations and core; the store linked), and a mine east of it whose chest holds its output, on a stone floor. */
final class WarehouseFixture {
 private WarehouseFixture(){}
 record Village(ServerLevel l,SettlementData.Entry e,Settlement s,Settlement.Building hall,Settlement.Building store,Settlement.Building mine,BlockPos center){
  Settlement.Building kept(){return s.buildings().stream().filter(b->b.id().equals(store.id())).findFirst().orElseThrow();}
  OwnedChestEntity stock(){return LogisticsRoutes.chest(l,e,kept());}
  OwnedChestEntity pit(){return LogisticsRoutes.chest(l,e,mine);}
  OwnedChestEntity pantry(){return LogisticsRoutes.chest(l,e,hall);}
 }
 static Village village(GameTestHelper h,int level){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var store=new Settlement.Building(Settlement.childId(s.id(),"building/warehouse"),WarehouseStore.TYPE,12,0,0);s.addBuilding(store);
  var mine=new Settlement.Building(Settlement.childId(s.id(),"building/mine"),"mine",44,0,0);s.addBuilding(mine);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,40,true));
  for(int x=-6;x<56;x++)for(int z=-14;z<26;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<22;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(var b:List.of(hall,mine)){var p=LogisticsRoutes.position(e,b);l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(p,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);}
  var v=new Village(l,e,s,hall,store,mine,center);lay(v,1);raise(v,level);return v;
 }
 static void lay(Village v,int level){var b=v.kept();
  for(var cell:BuildingPlacement.layout(BuildingTiers.layoutId(b.type(),level),BuildingPlacement.origin(v.e,b),b.rotation()).entrySet())v.l.setBlock(cell.getKey(),cell.getValue(),2);}
 /** Kept at this level with its whole plan standing and its store linked. */
 static Settlement.Building raise(Village v,int level){
  for(int n=v.kept().level()+1;n<=level;n++)v.s.raiseBuildingLevel(v.store.id(),n);
  if(level>1)lay(v,level);BuildingLevels.forgetBest(v.s.id());WarehouseStorage.ensure(v.l,v.e,v.kept());return v.kept();
 }
 /** Research of the warehouse's levels I..upTo finished. */
 static void research(Village v,int upTo){var research=BookResearch.inspect(v.l,v.e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  for(int level=1;level<=upTo;level++)for(var id:BuildingTiers.research(WarehouseStore.TYPE,level))done.add(StringTag.valueOf(id));
  research.put("legacyDone",done);BookResearch.store(v.l,v.e,research);ResearchKnobs.forget(v.s.id());}
 static Resident adult(Village v,String name,Profession role,Settlement.Building work){
  var r=new Resident(Settlement.childId(v.s.id(),"adult/"+name),Resident.Life.ADULT,false,null,null,-1);v.s.admit(r,v.s.homes().iterator().next().id());
  if(role!=null)v.s.assign(r.id(),role,work.id());r.ate(RestaurantFixture.NOW);return r;
 }
 /** The resident's body at a local cell, floating only (the tests drive its work). */
 static ResidentEntity body(Village v,Resident r,BlockPos local){
  var npc=VillageAstra.RESIDENT.get().create(v.l);npc.bind(v.s.id(),r);var at=v.center.offset(local);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
  npc.onlyGoals(g->g instanceof FloatGoal,0,new FloatGoal(npc));v.l.addFreshEntity(npc);return npc;
 }
 /** A cart of the warehouse set down from a cart item in its chest (the warehouse's own order). */
 static CartEntity cart(Village v){put(v.stock(),new ItemStack(VillageAstra.CART_ITEM.get()));WarehouseCarts.ensure(v.l,v.e,v.kept());var carts=WarehouseCarts.carts(v.l,v.kept());return carts.isEmpty()?null:carts.get(carts.size()-1);}
 /** Carts are set down only where the entities of the bay's chunk are loaded (CF-I): the warehouse tries again every few ticks, as it does
  *  every 100 ticks in the game, until it has n carts; then the test goes on (or fails when they never stand). */
 static void withCarts(GameTestHelper h,Village v,int n,int tries,Runnable then){
  WarehouseCarts.ensure(v.l,v.e,v.kept());
  if(WarehouseCarts.carts(v.l,v.kept()).size()>=n){then.run();return;}
  if(tries<=0){var why=WarehouseCarts.records(v.l,v.kept().id()).toString();VillageDogs.provide(VillageWolves.DOGS);done(v);h.fail("Only "+WarehouseCarts.carts(v.l,v.kept()).size()+" of "+n+" carts stand: "+why);return;}
  h.runAfterDelay(5,()->withCarts(h,v,n,tries-1,then));
 }
 /** A cart item in the chest, the warehouse's cart set down (waiting for its bay's entities), then the test's body; the village is
  *  cleared after it and the test succeeds when the body throws nothing. */
 static void withCart(GameTestHelper h,Village v,java.util.function.Consumer<CartEntity> body){
  put(v.stock(),new ItemStack(VillageAstra.CART_ITEM.get()));
  withCarts(h,v,1,40,()->{try{var carts=WarehouseCarts.carts(v.l,v.kept());body.accept(carts.get(carts.size()-1));}finally{done(v);}h.succeed();});
 }
 static int count(net.minecraft.world.Container c,Item item){int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;}
 static int total(net.minecraft.world.Container c){int n=0;for(int i=0;i<c.getContainerSize();i++)n+=c.getItem(i).getCount();return n;}
 /** Items a trip's legs carry in all (one stack a leg). */
 static int planned(CompoundTag t){int n=0;var legs=t.getList("legs",Tag.TAG_COMPOUND);for(int i=0;i<legs.size();i++)n+=ItemStack.of(legs.getCompound(i).getCompound("item")).getCount();return n;}
 static void put(net.minecraft.world.Container c,ItemStack s){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty()){c.setItem(i,s);return;}throw new IllegalStateException("Chest full");}
 /** Drives a courier's trip to its end: before each step the courier stands where its stage takes it (the cart beside it); true when it ended. */
 static boolean drive(Village v,ResidentEntity c,int steps){
  for(int i=0;i<steps;i++){WarehouseTrips.step(c);var t=WarehouseTrips.inspect(v.l,c.getUUID());if(!WarehouseTrips.active(t))return true;
   String stage=t.getString("stage");BlockPos to;
   var stops=t.getList("stops",Tag.TAG_COMPOUND);int stop=t.getInt("stop");
   if(stage.equals("go")||stage.equals("work")||stage.equals("shuttle")){var id=stops.getCompound(Math.min(stop,stops.size()-1)).getUUID("building");to=LogisticsRoutes.position(v.e,v.s.buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow());}
   else if(stage.equals("return"))to=LogisticsRoutes.position(v.e,v.kept());
   else{var cart=t.hasUUID("cart")&&v.l.getEntity(t.getUUID("cart")) instanceof CartEntity k?k:null;to=cart!=null?cart.blockPosition().offset(-1,0,0):c.blockPosition().offset(-1,0,0);}
   c.moveTo(to.getX()+1.5,to.getY(),to.getZ()+.5,0,0);
   for(var key:List.of("cart","cart2"))if(t.hasUUID(key)&&v.l.getEntity(t.getUUID(key)) instanceof CartEntity k&&!stage.equals("hitch")&&!stage.equals("fetch")&&!stage.equals("rehitch"))k.moveTo(to.getX()+2.5,to.getY(),to.getZ()+1.5,0,0);}
  return !WarehouseTrips.active(WarehouseTrips.inspect(v.l,c.getUUID()));
 }
 static void done(Village v){
  for(var r:v.s.residents())if(v.l.getEntity(r.id()) instanceof ResidentEntity npc){WarehouseTrips.release(v.l,npc.getUUID());npc.discard();}
  for(var c:WarehouseCarts.carts(v.l,v.kept()))c.discard();
  CartWolves.forget(v.s.id());SettlementData.get(v.l.getServer()).remove(v.s.id());BuildingLevels.forgetBest(v.s.id());
 }
}
