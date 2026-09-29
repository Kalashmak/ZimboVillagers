package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-064: raids are real waves from one side that grow with the settlement, go for its residents, send the peaceful into the hall,
 *  and end when beaten off or when they give up; peaceful difficulty brings none. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RaidGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void aRaidDoesNotStartInBlockOnlyChunks(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2048,3,2048));var s=new Settlement(UUID.randomUUID());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);
  // Outside the runner's entity-ticking area: readable terrain alone must not count as a live wave.
  for(int x=-Raids.FAR-1;x<=Raids.FAR+1;x++)for(int z=-Raids.FAR-1;z<=Raids.FAR+1;z++){
   int y=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,center.getX()+x,center.getZ()+z);
   l.setBlock(new BlockPos(center.getX()+x,y,center.getZ()+z),Blocks.STONE.defaultBlockState(),2);
  }
  try{
   h.assertTrue(!TouchLoad.ticking(l,center),"The remote terrain has no entity-ticking ticket");
   String result=Raids.start(l,e,1000,Raids.Kind.MONSTERS);
   h.assertTrue(result.equals(Raids.allowed(l.getDifficulty())?"nowhere":"peaceful"),"No phantom wave in block-only chunks: "+result);
   h.assertTrue(!Raids.active(l,s.id())&&Raids.record(l,s.id()).getInt("waves")==0,"A deferred wave is not recorded as started");
  }finally{
   try{java.nio.file.Files.deleteIfExists(l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-raids/"+s.id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
   Raids.clear();
  }
  h.succeed();
 }
 private static final net.minecraft.server.level.TicketType<UUID> HOLD=net.minecraft.server.level.TicketType.create("villageastra_test_raid",UUID::compareTo);
 private record Village(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,ResidentEntity farmer,ResidentEntity guard,List<net.minecraft.world.level.ChunkPos> chunks){}
 private static List<net.minecraft.world.level.ChunkPos> hold(net.minecraft.server.level.ServerLevel l,BlockPos center,UUID id){
  var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();int reach=Raids.FAR+2;
  for(int x=(center.getX()-reach)>>4;x<=(center.getX()+reach)>>4;x++)for(int z=(center.getZ()-reach)>>4;z<=(center.getZ()+reach)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);chunks.add(cp);l.getChunkSource().addRegionTicket(HOLD,cp,2,id);
  }
  long until=System.nanoTime()+30_000_000_000L;
  while(!chunks.stream().allMatch(cp->TouchLoad.ticking(l,cp.getWorldPosition())))if(!l.getChunkSource().pollTask()){
   if(System.nanoTime()>until){release(l,id,chunks);throw new IllegalStateException("Raid fixture chunks did not become entity-ticking");}
   java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
  }
  return chunks;
 }
 private static void release(net.minecraft.server.level.ServerLevel l,UUID id,List<net.minecraft.world.level.ChunkPos> chunks){
  for(var cp:chunks)l.getChunkSource().removeRegionTicket(HOLD,cp,2,id);
 }
 private static Village village(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  // The 56-block spawn ring extends beyond the runner's plot. Hold it with this fixture's own tickets.
  var chunks=hold(l,center,s.id());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var post=new Settlement.Building(Settlement.childId(s.id(),"building/guard_house"),"guard_house",14,0,0);s.addBuilding(post);
  for(int x=-2;x<24;x++)for(int z=-2;z<12;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,8,true);s.addHome(home);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  // Firm ground where raiders gather on every side, whatever sea or wood lies over the test grid.
  for(double angle=0;angle<Math.PI*2;angle+=Math.PI/8){int x=center.getX()+(int)Math.round(Math.cos(angle)*50),z=center.getZ()+(int)Math.round(Math.sin(angle)*50);
   for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){int y=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x+dx,z+dz);
    l.setBlock(new BlockPos(x+dx,y,z+dz),Blocks.STONE.defaultBlockState(),2);}}
  ResidentEntity farmer=null,guard=null;
  for(int i=0;i<7;i++){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());
   if(i==0){var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.moveTo(center.getX()+6.5,center.getY(),center.getZ()+6.5,0,0);npc.setNoAi(true);l.addFreshEntity(npc);farmer=npc;}
   if(i==1){r.trainMilitary();s.assign(r.id(),Profession.GUARD,post.id());var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.moveTo(center.getX()+8.5,center.getY(),center.getZ()+6.5,0,0);npc.setNoAi(true);l.addFreshEntity(npc);guard=npc;}}
  return new Village(l,e,farmer,guard,chunks);
 }
 private static void done(Village v){
  for(var mob:Raids.raiders(v.l,v.e.settlement().id()))mob.discard();
  try{java.nio.file.Files.deleteIfExists(v.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-raids/"+v.e.settlement().id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  Raids.clear();v.farmer.discard();v.guard.discard();SettlementData.get(v.l.getServer()).remove(v.e.settlement().id());
  release(v.l,v.e.settlement().id(),v.chunks);
 }
 @GameTest(template="empty",timeoutTicks=100) public static void raidsGrowWithTheSettlementAndPeacefulBringsNone(GameTestHelper h){
  h.assertTrue(Raids.size(0)==Raids.BASE&&Raids.size(9)==Raids.BASE+3&&Raids.size(500)==Raids.MAX,"A wave grows with the living residents and has a ceiling");
  h.assertTrue(!Raids.allowed(Difficulty.PEACEFUL)&&Raids.allowed(Difficulty.EASY)&&Raids.allowed(Difficulty.HARD),"Peaceful difficulty brings no raid");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aRaidComesFromOneSideGoesForTheResidentsAndIsBeatenOff(GameTestHelper h){
  var v=village(h);var id=v.e.settlement().id();
  try{
   if(!Raids.allowed(v.l.getDifficulty())){h.assertTrue(Raids.start(v.l,v.e,1000,Raids.Kind.MONSTERS).equals("peaceful"),"A peaceful world refuses the raid");h.succeed();return;}
   var started=Raids.start(v.l,v.e,1000,Raids.Kind.MONSTERS);h.assertTrue(started.isEmpty(),"The raid starts: "+started);
   var raiders=Raids.raiders(v.l,id);
   h.assertTrue(!raiders.isEmpty()&&raiders.size()<=Raids.size(7),"Real raiders stand in the world: "+raiders.size());
   h.assertTrue(Raids.start(v.l,v.e,1001,Raids.Kind.MONSTERS).equals("active"),"One raid at a time");
   var c=v.e.center();
   for(var mob:raiders){double dx=mob.getX()-c.getX(),dz=mob.getZ()-c.getZ(),d=Math.sqrt(dx*dx+dz*dz);
    h.assertTrue(d>=Raids.NEAR-1.5&&d<=Raids.FAR+1.5,"The raiders gather at the edge of the settlement: "+d);
    h.assertTrue(mob.getTags().contains("AstraRaid")&&mob.isPersistenceRequired(),"A raider is marked and stays until the raid ends");}
   // One raider reaches the houses: it goes for the resident, the farmer hides in the hall, the guard does not.
   var first=raiders.get(0);first.moveTo(c.getX()+6.5,c.getY(),c.getZ()+9.5,0,0);first.setTarget(null);
   h.assertTrue(Raids.update(v.l,v.e,1100).isEmpty(),"The raid goes on while raiders live");
   h.assertTrue(first.getTarget() instanceof ResidentEntity,"The raider goes for a resident");
   var hide=RaidShelterGoal.needsShelter(v.l,v.e,v.farmer);
   h.assertTrue(hide!=null&&hide.equals(RaidShelterGoal.shelter(v.e)),"The farmer runs for the town hall");
   h.assertTrue(RaidShelterGoal.needsShelter(v.l,v.e,v.guard)==null,"The guard stays out to fight");
   for(var mob:raiders)mob.kill();
   h.assertTrue(Raids.update(v.l,v.e,1200).equals("repelled")&&!Raids.active(v.l,id),"With every raider dead the raid is beaten off");
   var record=Raids.record(v.l,id);
   h.assertTrue(record.getCompound("last").getInt("spawned")==raiders.size()&&record.getLong("nextAt")>=1200+Raids.INTERVAL_MIN,"The raid is recorded and the next one scheduled");
  }finally{done(v);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aRaidThatCannotWinGivesUp(GameTestHelper h){
  var v=village(h);var id=v.e.settlement().id();
  try{
   if(!Raids.allowed(v.l.getDifficulty())){h.succeed();return;}
   h.assertTrue(Raids.start(v.l,v.e,5000,Raids.Kind.BANDITS).isEmpty(),"The bandits come");
   var raiders=Raids.raiders(v.l,id);
   h.assertTrue(!raiders.isEmpty()&&raiders.stream().allMatch(m->m instanceof net.minecraft.world.entity.monster.AbstractIllager),"By day it is a real, nonempty band of illagers");
   h.assertTrue(Raids.update(v.l,v.e,5000+Raids.GIVE_UP+1).equals("withdrew")&&!Raids.active(v.l,id),"After their time the raiders withdraw");
   h.assertTrue(raiders.stream().noneMatch(net.minecraft.world.entity.Entity::isAlive),"Withdrawn raiders leave the world");
  }finally{done(v);}
  h.succeed();
 }
}
