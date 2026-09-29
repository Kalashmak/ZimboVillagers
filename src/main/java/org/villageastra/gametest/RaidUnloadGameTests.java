package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
import org.villageastra.world.Raids;
/** Real entity removal/join events and on-disk legacy raid records; no missing mob is presumed dead. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RaidUnloadGameTests {
 private record Fixture(ServerLevel l,SettlementData.Entry e,List<Mob> mobs){}
 private static Fixture setup(GameTestHelper h){
  var l=h.getLevel();var c=h.absolutePos(new BlockPos(2,2,2));var e=new SettlementData.Entry(new Settlement(UUID.randomUUID()),l.dimension().location().toString(),c);var mobs=new ArrayList<Mob>();var ids=new ListTag();
  for(int i=0;i<2;i++){var m=EntityType.ZOMBIE.create(l);m.setNoAi(true);m.setPersistenceRequired();m.addTag("AstraRaid");m.moveTo(c.getX()+i+.5,c.getY(),c.getZ()+.5,0,0);l.addFreshEntity(m);mobs.add(m);ids.add(NbtUtils.createUUID(m.getUUID()));}
  var a=new CompoundTag();a.put("mobs",ids);a.putInt("spawned",2);a.putString("kind","MONSTERS");a.putLong("started",1000);var t=new CompoundTag();t.putInt("schema",1);t.putInt("waves",1);t.put("active",a);NbtRecord.write(path(l,e),t);Raids.clear();return new Fixture(l,e,mobs);
 }
 private static java.nio.file.Path path(ServerLevel l,SettlementData.Entry e){return l.getServer().getWorldPath(LevelResource.ROOT).resolve("data/astra-raids/"+e.settlement().id()+".bin");}
 private static CompoundTag unload(Mob m){var t=new CompoundTag();m.save(t);m.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);return t;}
 private static Entity restore(ServerLevel l,CompoundTag t){var m=EntityType.loadEntityRecursive(t,l,x->x);return l.addFreshEntity(m)?m:null;}
 private static void cleanup(Fixture f){for(var m:f.mobs){var body=f.l.getEntity(m.getUUID());if(body!=null)body.discard();}try{java.nio.file.Files.deleteIfExists(path(f.l,f.e));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}Raids.clear();}
 @GameTest(template="empty",batch="raid_unload",timeoutTicks=100) public static void unloadedRaidIsNotRepelled(GameTestHelper h){var f=setup(h);try{
  var saved=f.mobs.stream().map(RaidUnloadGameTests::unload).toList();
  h.assertTrue(Raids.update(f.l,f.e,1100).isEmpty()&&Raids.active(f.l,f.e.settlement().id()),"Unloaded raiders are not dead and cannot win the raid for the village");
  Raids.clear();h.assertTrue(Raids.update(f.l,f.e,1120).isEmpty(),"Missing roster survives disk reload");
  for(var t:saved)h.assertTrue(restore(f.l,t)!=null,"The same active raider can rejoin");
  h.assertTrue(Raids.raiders(f.l,f.e.settlement().id()).size()==2,"Both original identities return");
 }finally{cleanup(f);}h.succeed();}
 @GameTest(template="empty",batch="raid_unload",timeoutTicks=100) public static void killedAndUnloadedRaidersAreDifferent(GameTestHelper h){var f=setup(h);try{
  var saved=unload(f.mobs.get(0));f.mobs.get(1).kill();
  h.assertTrue(Raids.update(f.l,f.e,1100).isEmpty(),"One kill does not defeat the unloaded survivor");
  Raids.clear();var survivor=restore(f.l,saved);h.assertTrue(survivor!=null,"Unloaded survivor returns after reload");survivor.kill();
  h.assertTrue(Raids.update(f.l,f.e,1140).equals("repelled"),"Only actual removal of both raiders defeats the wave");
 }finally{cleanup(f);}h.succeed();}
 @GameTest(template="empty",batch="raid_unload",timeoutTicks=100) public static void withdrawnUnloadedRaidersCannotReturn(GameTestHelper h){var f=setup(h);try{
  var saved=f.mobs.stream().map(RaidUnloadGameTests::unload).toList();
  h.assertTrue(Raids.update(f.l,f.e,1001+Raids.GIVE_UP).equals("withdrew"),"Unloaded living raiders withdraw, never count as repelled");
  Raids.clear();for(var t:saved)h.assertTrue(restore(f.l,t)==null,"Retired mob from old chunk must not resurrect after reload");
  h.assertTrue(Raids.record(f.l,f.e.settlement().id()).getCompound("last").getInt("spawned")==2,"Original wave size remains in history");
 }finally{cleanup(f);}h.succeed();}
 @GameTest(template="empty",batch="raid_unload",timeoutTicks=100) public static void killedRaidersCannotRejoinAnActiveWave(GameTestHelper h){var f=setup(h);try{
  var old=new CompoundTag();f.mobs.get(0).save(old);f.mobs.get(0).remove(Entity.RemovalReason.KILLED);Raids.clear();
  h.assertTrue(f.l.getEntity(f.mobs.get(0).getUUID())==null,"Dead body has actually left entity lookup");
  h.assertTrue(restore(f.l,old)==null,"A killed identity stays dead even while another raider lives");
  h.assertTrue(Raids.update(f.l,f.e,1100).isEmpty(),"Live second raider keeps attacking");
 }finally{cleanup(f);}h.succeed();}
 @GameTest(template="empty",batch="raid_unload",timeoutTicks=100) public static void retiredRosterSurvivesAnotherWave(GameTestHelper h){var f=setup(h);Mob next=null;try{
  var old=f.mobs.stream().map(RaidUnloadGameTests::unload).toList();Raids.update(f.l,f.e,1001+Raids.GIVE_UP);
  next=EntityType.ZOMBIE.create(f.l);next.setNoAi(true);next.addTag("AstraRaid");next.moveTo(f.e.center().getX()+.5,f.e.center().getY(),f.e.center().getZ()+.5,0,0);f.l.addFreshEntity(next);
  var t=Raids.record(f.l,f.e.settlement().id());var a=new CompoundTag();var ids=new ListTag();ids.add(NbtUtils.createUUID(next.getUUID()));a.put("mobs",ids);a.putInt("spawned",1);a.putLong("started",9000);a.putString("kind","MONSTERS");t.put("active",a);t.putInt("waves",2);NbtRecord.write(path(f.l,f.e),t);Raids.clear();
  for(var saved:old)h.assertTrue(restore(f.l,saved)==null,"A new active wave cannot revive an older retired roster");
  h.assertTrue(Raids.raiders(f.l,f.e.settlement().id()).size()==1&&Raids.update(f.l,f.e,9020).isEmpty(),"New wave stays intact");
 }finally{if(next!=null)next.discard();cleanup(f);}h.succeed();}
}
