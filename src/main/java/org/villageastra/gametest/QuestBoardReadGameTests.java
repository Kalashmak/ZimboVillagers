package org.villageastra.gametest;
import java.util.*;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.world.Quests;
/** Real checksummed board reads in one server tick, with replacement and mutation guards. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class QuestBoardReadGameTests {
 @GameTest(template="empty",batch="quest_board_reads",timeoutTicks=100)
 public static void repeatedBoardQueriesReadDiskOnceWithoutSharingMutableState(GameTestHelper h){
  var l=h.getLevel();var village=UUID.randomUUID();var p=Quests.path(l,village);NbtRecord.write(p,board("old"));long before=Quests.boardReads();
  for(int i=0;i<200;i++){var b=Quests.board(l,village);h.assertTrue(b.getString("marker").equals("old"),"An unsaved caller mutation never leaks to another reader");b.putString("marker","unsaved");b.getList("quests",Tag.TAG_COMPOUND).clear();}
  long reads=Quests.boardReads()-before;com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_BOARD_READS queries=200 actualDiskReads={}",reads);
  h.assertTrue(reads==1,"Unchanged board should be parsed once per actual server tick, got "+reads);h.assertTrue(Quests.board(l,village).getList("quests",Tag.TAG_COMPOUND).size()==40,"Nested quest lists are independent copies");h.succeed();
 }
 @GameTest(template="empty",batch="quest_board_reads",timeoutTicks=100)
 public static void sameTickSameTimestampReplacementDeletionAndCorruptionAreFresh(GameTestHelper h){
  var l=h.getLevel();var village=UUID.randomUUID();var p=Quests.path(l,village);
  try{
   NbtRecord.write(p,board("old"));var time=Files.getLastModifiedTime(p);long size=Files.size(p);Quests.board(l,village);
   NbtRecord.write(p,board("new"));Files.setLastModifiedTime(p,time);h.assertTrue(Files.size(p)==size&&Quests.board(l,village).getString("marker").equals("new"),"Same size/time atomic write is fresh immediately");
   Files.delete(p);h.assertTrue(Quests.board(l,village).getList("quests",Tag.TAG_COMPOUND).isEmpty(),"Deletion returns the normal missing board, not old quests");
   NbtRecord.write(p,board("end"));Quests.board(l,village);
   byte[] bytes=Files.readAllBytes(p);bytes[bytes.length-1]^=1;var corrupt=p.resolveSibling(p.getFileName()+".corrupt");Files.write(corrupt,bytes);Files.setLastModifiedTime(corrupt,time);Files.move(corrupt,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
   boolean rejected=false;try{Quests.board(l,village);}catch(IllegalStateException ex){rejected=true;}h.assertTrue(rejected,"Corrupt same-size replacement cannot fall back to a previous valid cache");
   NbtRecord.write(p,board("fix"));h.assertTrue(Quests.board(l,village).getString("marker").equals("fix"),"Authoritative repair is fresh");
  }catch(java.io.IOException ex){throw new IllegalStateException(ex);}h.succeed();
 }
 @GameTest(template="empty",batch="quest_board_reads",timeoutTicks=100)
 public static void boardIsReverifiedOnTheNextServerTick(GameTestHelper h){
  var l=h.getLevel();var village=UUID.randomUUID();var p=Quests.path(l,village);NbtRecord.write(p,board("old"));Quests.board(l,village);long before=Quests.boardReads();int tick=l.getServer().getTickCount();
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.getServer().getTickCount()!=tick,"Actual next server tick")).thenExecute(()->{
   h.assertTrue(Quests.board(l,village).getString("marker").equals("old")&&Quests.boardReads()>before,"Unchanged metadata does not avoid next-tick checksum verification");
  }).thenSucceed();
 }
 private static CompoundTag board(String marker){var b=new CompoundTag();b.putInt("schema",1);b.putString("marker",marker);var list=new ListTag();for(int i=0;i<40;i++){var q=new CompoundTag();q.putUUID("id",UUID.randomUUID());q.putString("state","open");q.putString("item","minecraft:paper");q.putInt("target",16);list.add(q);}b.put("quests",list);return b;}
}
