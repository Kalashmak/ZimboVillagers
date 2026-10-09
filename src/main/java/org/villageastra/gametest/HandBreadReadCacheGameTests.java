package org.villageastra.gametest;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
/** AD468: copied same-actual-tick sensing, with immediate authoritative bread journal writes. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HandBreadReadCacheGameTests {
 @GameTest(template="empty",batch="handbread_read_baseline",timeoutTicks=100)
 public static void aHundredUnchangedHandBreadInspectorsNeedOnlyOneVerifiedRead(GameTestHelper h)throws Exception{
  var l=h.getLevel();var id=UUID.randomUUID();var p=HandBread.path(l,id);var original=new CompoundTag();original.putUUID("id",UUID.randomUUID());original.putString("stage","work");original.putLong("labor",20);var nested=new CompoundTag();nested.putInt("paid",5);original.put("nested",nested);NbtRecord.write(p,original);
  try{long before=HandBread.inspectReads();for(int i=0;i<100;i++){var seen=HandBread.inspect(l,id);h.assertTrue(seen.equals(original),"Each sensing caller sees the same unchanged durable job");seen.getCompound("nested").putInt("paid",999);seen.putLong("labor",999);}
   long count=HandBread.inspectReads()-before;com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_HANDBREAD_READ calls=100 verifiedReads={} actualServerTick={}",count,l.getServer().getTickCount());h.assertTrue(count==1,"100 same-tick inspectors require one verified read, observed="+count);
  }finally{Files.deleteIfExists(p);}h.succeed();
 }
 private record Town(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s,Settlement.Building hall,ResidentEntity npc,List<net.minecraft.world.level.ChunkPos> held){
  void close(){npc.discard();HandBread.release(l,s.id(),npc.getUUID());SettlementData.get(l.getServer()).remove(s.id());try{Files.deleteIfExists(HandBread.path(l,s.id()));}catch(Exception ex){throw new IllegalStateException(ex);}PhysicalFixtureChunks.release(l,held);}
 }
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var center=new BlockPos(at.getX()+2621440,110,at.getZ());var held=PhysicalFixtureChunks.force(l,center,-3,12,-3,12);for(var c:held)l.getChunk(c.x,c.z);for(var p:BlockPos.betweenClosed(center.offset(-3,-1,-3),center.offset(12,16,12)))l.setBlock(p,p.getY()<center.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);for(var cell:BuildingPlacement.layout(e,hall,"town_hall").entrySet())l.setBlock(cell.getKey(),BuildingOrders.payable(cell.getValue()),2);var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());s.assign(r.id(),Profession.MAYOR,hall.id());npc.bind(s.id(),r);npc.moveTo(center.getX()+2.5,center.getY()+1,center.getZ()+4.5);npc.setNoAi(true);l.setDayTime(1000);return new Town(l,e,s,hall,npc,held);
 }
 private static long paid(GameTestHelper h,Town t){var stock=LogisticsRoutes.chest(t.l,t.e,t.hall);stock.clearContent();stock.setItem(0,new ItemStack(Items.WHEAT,5));long now=1000;for(int i=0;i<4&&!HandBread.inspect(t.l,t.s.id()).getString("stage").equals("work");i++){HandBread.advance(t.l,t.e,t.npc.getUUID(),now);now+=HandBread.TURN;}var job=HandBread.inspect(t.l,t.s.id());h.assertTrue(job.getString("stage").equals("work")&&job.getInt("paid")==5&&job.getLong("labor")==0&&stock.countItem(Items.WHEAT)==0&&stock.countItem(Items.BREAD)==0,"Actual journal has paid five wheat and no fictional labor/output");return now;}
 @GameTest(template="empty",batch="handbread_read_baseline",timeoutTicks=200)
 public static void actualWorkshopAndBreadGoalsShareTheirUnchangedEligibilityRead(GameTestHelper h){
  var t=town(h);paid(h,t);h.startSequence().thenWaitUntil(()->h.assertTrue(t.l.isPositionEntityTicking(t.npc.blockPosition()),"Actual fixture chunk is entity-ticking")).thenExecute(()->{h.assertTrue(t.l.getEntity(t.npc.getUUID())==null,"No competing fixture body before admission");SettlementData.get(t.l.getServer()).add(t.e);boolean added=t.l.addFreshEntity(t.npc);h.assertTrue(added&&t.l.getEntity(t.npc.getUUID())==t.npc,"Ready canonical body is added immediately after publishing registry");}).thenWaitUntil(()->h.assertTrue(t.npc.tickCount>0&&t.l.getEntity(t.npc.getUUID())==t.npc,"Canonical admitted body is actually ticking")).thenExecute(()->{try{
   var before=Files.readAllBytes(HandBread.path(t.l,t.s.id()));var job=HandBread.inspect(t.l,t.s.id());NbtRecord.write(HandBread.path(t.l,t.s.id()),job);long reads=HandBread.inspectReads();int tick=t.l.getServer().getTickCount();var workshop=new WorkshopGoal(t.npc,true);var bread=new HandBreadGoal(t.npc,true,()->1000L);
   h.assertTrue(!workshop.canUse()&&bread.canUse(),"Actual workshop gives way and actual bread goal accepts the same paid village job");long count=HandBread.inspectReads()-reads;com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_HANDBREAD_GOALS verifiedReads={} actualServerTick={} bodyTicks={} identity=true",count,tick,t.npc.tickCount);h.assertTrue(Arrays.equals(before,Files.readAllBytes(HandBread.path(t.l,t.s.id())))&&tick==t.l.getServer().getTickCount(),"Two real eligibility decisions neither write nor advance the real server tick");h.assertTrue(count==1,"Actual workshop and bread eligibility require one verified read, observed="+count);
  }catch(java.io.IOException ex){throw new IllegalStateException(ex);}finally{t.close();}}).thenSucceed();
 }
 @GameTest(template="empty",batch="handbread_read_guards",timeoutTicks=100)
 public static void breadJournalWritesDeletionAndExternalReplacementInvalidateImmediately(GameTestHelper h)throws Exception{
  var l=h.getLevel();var id=UUID.randomUUID();var p=HandBread.path(l,id);var state=new CompoundTag();state.putString("stage","work");state.putLong("labor",20);NbtRecord.write(p,state);HandBread.inspect(l,id);var time=Files.getLastModifiedTime(p);long size=Files.size(p);long revision=AtomicRecord.revision(p);
  try{var next=state.copy();next.putLong("labor",40);NbtRecord.write(p,next);Files.setLastModifiedTime(p,time);h.assertTrue(Files.size(p)==size&&AtomicRecord.revision(p)>revision&&HandBread.inspect(l,id).equals(next),"Same-time same-size authoritative commit is visible in this tick");
   Files.delete(p);var absent=HandBread.inspect(l,id);absent.putInt("fake",1);h.assertTrue(HandBread.inspect(l,id).isEmpty(),"Deleted records evict and missing tags are independent");NbtRecord.write(p,state);Files.setLastModifiedTime(p,time);h.assertTrue(HandBread.inspect(l,id).equals(state),"Same-time recreated signed record is read immediately");
   var other=UUID.randomUUID();var replacement=HandBread.path(l,other);next.putString("stage","output");NbtRecord.write(replacement,next);Files.copy(replacement,p,StandardCopyOption.REPLACE_EXISTING);Files.delete(replacement);h.assertTrue(HandBread.inspect(l,id).equals(next),"External valid metadata-changing replacement is visible immediately");
   var sibling=UUID.randomUUID();var siblingPath=HandBread.path(l,sibling);NbtRecord.write(siblingPath,state);h.assertTrue(HandBread.inspect(l,sibling).equals(state)&&HandBread.inspect(l,id).equals(next),"Different village paths never share mutable job state");Files.delete(siblingPath);
  }finally{Files.deleteIfExists(p);}h.succeed();
 }
 @GameTest(template="empty",batch="handbread_read_guards",timeoutTicks=100)
 public static void anUnchangedNextRealTickRevalidatesAndRejectsHiddenChecksumDamage(GameTestHelper h)throws Exception{
  var l=h.getLevel();var id=UUID.randomUUID();var p=HandBread.path(l,id);var state=new CompoundTag();state.putString("stage","work");state.putLong("labor",20);NbtRecord.write(p,state);HandBread.inspect(l,id);int tick=l.getServer().getTickCount();
  h.runAtTickTime(2,()->{h.assertTrue(l.getServer().getTickCount()!=tick,"Actual server tick advanced independently of bread job clock");long before=HandBread.inspectReads();h.assertTrue(HandBread.inspect(l,id).equals(state)&&HandBread.inspectReads()==before+1,"Unchanged next-tick file receives a fresh verified read");try{var stamp=Files.getLastModifiedTime(p);var bytes=Files.readAllBytes(p);bytes[bytes.length-1]^=1;Files.write(p,bytes);Files.setLastModifiedTime(p,stamp);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}});
  h.runAtTickTime(4,()->{try{boolean refused=false;try{HandBread.inspect(l,id);}catch(IllegalStateException expected){refused=true;}h.assertTrue(refused,"Hidden same-size same-mtime external corruption must fail validation next tick");h.succeed();}finally{try{Files.deleteIfExists(p);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}}});
 }
 @GameTest(template="empty",batch="handbread_read_guards",timeoutTicks=200)
 public static void breadSensingIsBoundedAndBelongsToTheActualServerContext(GameTestHelper h)throws Exception{
  var l=h.getLevel();var ids=new ArrayList<UUID>();var state=new CompoundTag();state.putString("stage","idle");try{
   for(int i=0;i<257;i++){var id=UUID.randomUUID();ids.add(id);NbtRecord.write(HandBread.path(l,id),state);HandBread.inspect(l,id);}long before=HandBread.inspectReads();HandBread.inspect(l,ids.get(0));h.assertTrue(HandBread.inspectReads()==before+1,"Oldest record is evicted after 257 distinct villages");
   var field=HandBread.class.getDeclaredField("BREAD_READS");field.setAccessible(true);var contexts=(Map<?,?>)field.get(null);h.assertTrue(contexts instanceof WeakHashMap&&contexts.containsKey(l.getServer())&&contexts.keySet().stream().allMatch(k->k instanceof net.minecraft.server.MinecraftServer),"Sensing namespace is the actual server identity, retained weakly rather than a UUID or clock");var cache=(Map<?,?>)contexts.get(l.getServer());h.assertTrue(cache.size()<=256&&cache.keySet().stream().allMatch(k->k instanceof Path p&&p.isAbsolute()&&p.equals(p.normalize())),"Actual server context retains only bounded normalized absolute record paths");
   var id=ids.get(0);long reads=HandBread.inspectReads();var nether=l.getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(nether!=null&&nether.getServer()==l.getServer()&&HandBread.inspect(nether,id).equals(state)&&HandBread.inspectReads()==reads,"Other dimension of this same actual server correctly shares the same world-global village record");
  }finally{for(var id:ids)Files.deleteIfExists(HandBread.path(l,id));}h.succeed();
 }
 @GameTest(template="empty",batch="handbread_read_guards",timeoutTicks=200)
 public static void cachedPaidBreadPaysEveryLaborTurnAndReplaysItsRealOutputOnlyOnce(GameTestHelper h){
  var t=town(h);try{long now=paid(h,t);var stock=LogisticsRoutes.chest(t.l,t.e,t.hall);var original=HandBread.inspect(t.l,t.s.id());var job=original.getUUID("id");long revision=AtomicRecord.revision(HandBread.path(t.l,t.s.id()));
   h.assertTrue(HandBread.advance(t.l,t.e,t.npc.getUUID(),now).equals("hand_bread_working"),"First real paid labor turn advances");var state=HandBread.inspect(t.l,t.s.id());h.assertTrue(state.getLong("labor")==HandBread.TURN&&AtomicRecord.revision(HandBread.path(t.l,t.s.id()))>revision,"Same-tick inspection immediately sees durable paid labor");long reads=HandBread.inspectReads();revision=AtomicRecord.revision(HandBread.path(t.l,t.s.id()));HandBread.advance(t.l,t.e,t.npc.getUUID(),now);h.assertTrue(HandBread.inspect(t.l,t.s.id()).getLong("labor")==HandBread.TURN&&HandBread.inspectReads()==reads&&AtomicRecord.revision(HandBread.path(t.l,t.s.id()))==revision,"Same job-clock turn cannot duplicate labor or commit again");
   int turns=1;for(;turns<400&&!HandBread.inspect(t.l,t.s.id()).getString("stage").equals("output");turns++){now+=HandBread.TURN;HandBread.advance(t.l,t.e,t.npc.getUUID(),now);}var output=HandBread.inspect(t.l,t.s.id());h.assertTrue(output.getString("stage").equals("output")&&output.getLong("labor")==output.getLong("needLabor")&&output.getLong("labor")==2L*HandBread.LABOR_PER_BREAD&&turns*HandBread.TURN>=output.getLong("needLabor")&&stock.countItem(Items.BREAD)==0,"Every full configured labor turn precedes actual output");
   now+=HandBread.TURN;HandBread.advance(t.l,t.e,t.npc.getUUID(),now);h.assertTrue(stock.countItem(Items.BREAD)==2&&HandBread.inspect(t.l,t.s.id()).getInt("output")==1,"Real journal-backed deposit is immediately visible");NbtRecord.write(HandBread.path(t.l,t.s.id()),output);now+=HandBread.TURN;HandBread.advance(t.l,t.e,t.npc.getUUID(),now);h.assertTrue(stock.countItem(Items.BREAD)==2,"Replayed original paid output snapshot reuses receipt without duplicating bread");now+=HandBread.TURN;HandBread.advance(t.l,t.e,t.npc.getUUID(),now);var done=HandBread.inspect(t.l,t.s.id());h.assertTrue(done.getString("stage").equals("idle")&&done.getInt("baked")==2&&done.getUUID("id").equals(job)&&done.getLong("labor")==done.getLong("needLabor")&&stock.countItem(Items.WHEAT)==0&&stock.countItem(Items.BREAD)==2,"Job identity, paid wheat, full labor and exactly-once output survive sensing and replay");
  }finally{t.close();}h.succeed();
 }
 @GameTest(template="empty",batch="handbread_read_guards",timeoutTicks=100)
 public static void aValidSameSizeAndMtimeExternalReplacementUsesTheAvailableIdentityMetadata(GameTestHelper h)throws Exception{
  var l=h.getLevel();var id=UUID.randomUUID();var other=UUID.randomUUID();var p=HandBread.path(l,id);var replacement=HandBread.path(l,other);var state=new CompoundTag();state.putString("stage","work");state.putLong("labor",20);NbtRecord.write(p,state);HandBread.inspect(l,id);var original=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class);long revision=AtomicRecord.revision(p);
  try{var changed=state.copy();changed.putLong("labor",40);NbtRecord.write(replacement,changed);Files.setAttribute(replacement,"basic:creationTime",java.nio.file.attribute.FileTime.fromMillis(original.creationTime().toMillis()+10000));Files.setLastModifiedTime(replacement,original.lastModifiedTime());Files.move(replacement,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);var after=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class);
   h.assertTrue(after.size()==original.size()&&after.lastModifiedTime().equals(original.lastModifiedTime())&&AtomicRecord.revision(p)==revision,"External replacement preserves size, mtime and the watched authoritative revision");h.assertTrue(!after.creationTime().equals(original.creationTime())||!Objects.equals(after.fileKey(),original.fileKey()),"Available file identity metadata really changes");long reads=HandBread.inspectReads();h.assertTrue(HandBread.inspect(l,id).equals(changed)&&HandBread.inspectReads()==reads+1,"Identity metadata invalidates same-tick verified sensing even with preserved size/mtime/revision");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_HANDBREAD_IDENTITY creationChanged={} nonNullFileKey={} sameSize=true sameMtime=true unchangedRevision=true",!after.creationTime().equals(original.creationTime()),after.fileKey()!=null);
  }finally{Files.deleteIfExists(p);Files.deleteIfExists(replacement);}h.succeed();
 }
}
