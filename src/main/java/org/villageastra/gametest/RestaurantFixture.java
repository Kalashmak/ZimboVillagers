package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-139: a village for the restaurant's GameTests — a hall with its chest (the pantry) and a restaurant standing on its lot at a level
 *  (its whole design: tables and seats of the level, equipment and core), on a stone floor long enough for a courier's round. The village
 *  has its own time of day (Dining.testDay, noon) so no other test's clock or the level's is touched. */
final class RestaurantFixture {
 private RestaurantFixture(){}
 record Village(ServerLevel l,SettlementData.Entry e,Settlement s,Settlement.Building hall,Settlement.Building restaurant,BlockPos center){
  Settlement.Building kept(){return s.buildings().stream().filter(b->b.id().equals(restaurant.id())).findFirst().orElseThrow();}
  OwnedChestEntity pantry(){return LogisticsRoutes.chest(l,e,hall);}
  OwnedChestEntity kitchen(){return LogisticsRoutes.chest(l,e,kept());}
 }
 /** A test clock far ahead of the frozen active clock, so meals can be due without advancing the server's clock. */
 static final long NOW=200_000;
 static Village village(GameTestHelper h,int level){return village(h,level,"restaurant",60);}
 static Village village(GameTestHelper h,int level,String type,int length){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  hold(l,s.id(),center.offset(-2,0,-2),center.offset(Math.max(26,length)-1,0,13));
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var shop=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,10,0,0);s.addBuilding(shop);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,40,true));
  for(int x=-2;x<Math.max(26,length);x++)for(int z=-2;z<14;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=0;y<16;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,0,4),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  Dining.testDay(s.id(),6000L);
  var v=new Village(l,e,s,hall,shop,center);lay(v,1);raise(v,level);return v;
 }
 /** The floor runs further than the test's plot (48 blocks: a courier's round goes 50 out). The runner holds only the plot's chunks, so a body
  *  past it stood in a chunk that was loaded by the floor's blocks but not entity-ticking ("away", no entity) when this plot was the last of
  *  its row or the spawn area had not ticked yet. The village's own ticket holds every chunk of its floor entity-ticking until done(). */
 private static final TicketType<UUID> HOLD=TicketType.create("villageastra_test_village",UUID::compareTo);
 private static final Map<UUID,List<ChunkPos>> HELD=new java.util.concurrent.ConcurrentHashMap<>();
 static void hold(ServerLevel l,UUID village,BlockPos from,BlockPos to){
  var chunks=new ArrayList<ChunkPos>();
  for(int cx=from.getX()>>4;cx<=to.getX()>>4;cx++)for(int cz=from.getZ()>>4;cz<=to.getZ()>>4;cz++){var cp=new ChunkPos(cx,cz);
   // Distance 2: ticket level 31, entity ticking (as a forced chunk).
   l.getChunkSource().addRegionTicket(HOLD,cp,2,village);chunks.add(cp);}
  HELD.put(village,chunks);
  long until=System.nanoTime()+30_000_000_000L;
  while(!chunks.stream().allMatch(cp->l.isPositionEntityTicking(cp.getWorldPosition()))){
   if(!l.getChunkSource().pollTask()){
    if(System.nanoTime()>until)throw new IllegalStateException("The test village's chunks are not entity-ticking after 30 s: "+chunks);
    java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);}}
 }
 static void lay(Village v,int level){var b=v.kept();var design=BuildingTiers.layoutId(b.type(),level);
  for(var cell:BuildingPlacement.layout(design,BuildingPlacement.origin(v.e,b),b.rotation()).entrySet())v.l.setBlock(cell.getKey(),cell.getValue(),2);}
 /** Kept at this level with its whole design standing; the caches of levels and seats forgotten. */
 static Settlement.Building raise(Village v,int level){
  for(int n=v.kept().level()+1;n<=level;n++)v.s.raiseBuildingLevel(v.restaurant.id(),n);
  if(level>1)lay(v,level);BuildingLevels.forgetBest(v.s.id());Dining.forgetSeats();return v.kept();
 }
 /** Research of the restaurant's levels II..upTo finished (the catalogue's names for them). */
 static void research(Village v,int upTo){var research=BookResearch.inspect(v.l,v.e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  for(int level=1;level<=upTo;level++)for(var id:BuildingTiers.research("restaurant",level))done.add(StringTag.valueOf(id));
  research.put("legacyDone",done);BookResearch.store(v.l,v.e,research);}
 /** An adult of the village, due for its meal at NOW (it last ate a meal interval before), standing at a local cell of the village. */
 static Resident adult(Village v,String name,Profession role,Settlement.Building work){
  var r=new Resident(Settlement.childId(v.s.id(),"adult/"+name),Resident.Life.ADULT,false,null,null,-1);v.s.admit(r,v.s.homes().iterator().next().id());
  if(role!=null)v.s.assign(r.id(),role,work.id());r.ate(NOW-Population.MEAL_INTERVAL);return r;
 }
 /** The resident's body at a local cell of the village, with only the goals given (and floating) — no other village life. */
 static ResidentEntity body(Village v,Resident r,BlockPos local,Goal... goals){
  var npc=VillageAstra.RESIDENT.get().create(v.l);npc.bind(v.s.id(),r);var at=v.center.offset(local);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
  npc.onlyGoals(g->g instanceof FloatGoal||g instanceof ResidentDoorGoal,0,new FloatGoal(npc));
  v.l.addFreshEntity(npc);return npc;
 }
 static void give(ResidentEntity npc,int priority,Goal goal){npc.onlyGoals(g->g instanceof FloatGoal||g instanceof ResidentDoorGoal,priority,goal);}
 static int count(OwnedChestEntity c,net.minecraft.world.item.Item item){int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;}
 static void put(OwnedChestEntity c,ItemStack s){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty()){c.setItem(i,s);return;}throw new IllegalStateException("Chest full");}
 static void release(ServerLevel l,UUID village){var held=HELD.remove(village);if(held!=null)for(var cp:held)l.getChunkSource().removeRegionTicket(HOLD,cp,2,village);}
 static void done(Village v){
  for(var r:v.s.residents())if(v.l.getEntity(r.id()) instanceof ResidentEntity npc)npc.discard();
  Dining.testDay(v.s.id(),null);SettlementData.get(v.l.getServer()).remove(v.s.id());BuildingLevels.forgetBest(v.s.id());Dining.forget();
  release(v.l,v.s.id());
 }
}
