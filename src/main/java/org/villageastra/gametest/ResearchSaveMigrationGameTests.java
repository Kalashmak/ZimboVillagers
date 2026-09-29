package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-136 (spec 3.4, 6.2, D12, R5, CF4): a world saved with the old tree opens. A schema-1 research record holding removed ids (civic,
 *  mechanics, exploration, engineering VI, old side levels) and a pending book payment is migrated once to schema 2 with the right credit;
 *  the strict check then holds for schema 2 only. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResearchSaveMigrationGameTests {
 private static ListTag strings(String... ids){var l=new ListTag();for(var id:ids)l.add(StringTag.valueOf(id));return l;}
 @GameTest(template="empty",timeoutTicks=100) public static void anOldRecordWithRemovedIdsOpensWithItsCredit(GameTestHelper h){
  var t=town(h,null);
  try{var old=new CompoundTag();old.putInt("schema",1);old.putUUID("village",t.s.id());old.putString("selected","mechanics.4");
   old.put("queue",strings("exploration.1","roads.2"));old.put("legacyDone",strings("civic.2","milling.3","agriculture.1"));old.putString("legacyActive","");
   var paid=new CompoundTag();paid.putInt("mechanics.4",10);paid.putInt("forestry.1",3);paid.putInt("roads.3",21);paid.putInt("engineering.6",4);old.put("paid",paid);
   // A book payment was half done when the world was saved: its receipt is not in the journal, so it did not happen.
   old.putString("pending","roads.2");old.putUUID("operation",UUID.randomUUID());
   NbtRecord.write(BookResearch.path(t.l,t.s.id()),old);BookResearch.clearCache();
   var r=BookResearch.inspect(t.l,t.e);
   h.assertTrue(r.getInt("schema")==2&&r.getInt("migratedFrom")==1,"Migrated to schema 2: "+r);
   // mechanics.4 10 + forestry.1 3 (level I: books are credit) + roads.3 21-14 + engineering.6 4 = 24.
   h.assertTrue(r.getInt("credit")==24,"Credit of the removed and level-I books and the rest over the new price: "+r.getInt("credit"));
   var done=BookResearch.completed(t.e,r);
   h.assertTrue(done.containsAll(Set.of("milling.2","agriculture.1","roads.3"))&&!done.contains("forestry.1"),"Done: the side node, the level I of the done list, roads III paid to its old price: "+done);
   h.assertTrue(r.getString("selected").equals("roads.2")&&r.getList("queue",Tag.TAG_STRING).isEmpty()&&!r.contains("pending"),"The removed target gives way to the queue's roads II; nothing left pending: "+r);
   h.assertTrue(r.getList("legacyDone",Tag.TAG_STRING).stream().noneMatch(x->x.getAsString().startsWith("civic.")),"The civic node is gone");
   BookResearch.clearCache();var again=BookResearch.inspect(t.l,t.e);
   h.assertTrue(again.getInt("credit")==24&&again.getInt("schema")==2,"Read again from disk: the same record, migrated once");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void aSchemaTwoRecordStaysStrict(GameTestHelper h){
  var t=town(h,null);
  try{var r=BookResearch.inspect(t.l,t.e);h.assertTrue(r.getInt("schema")==2&&r.getInt("credit")==0,"A new village starts at schema 2");
   var bad=r.copy();var paid=new CompoundTag();paid.putInt("mechanics.4",1);bad.put("paid",paid);NbtRecord.write(BookResearch.path(t.l,t.s.id()),bad);BookResearch.clearCache();
   boolean refused=false;try{BookResearch.inspect(t.l,t.e);}catch(IllegalArgumentException|IllegalStateException ex){refused=true;}
   h.assertTrue(refused,"An unknown id in a schema-2 record is refused");
   var tier1=r.copy();var p1=new CompoundTag();p1.putInt("agriculture.1",1);tier1.put("paid",p1);NbtRecord.write(BookResearch.path(t.l,t.s.id()),tier1);BookResearch.clearCache();
   refused=false;try{BookResearch.inspect(t.l,t.e);}catch(IllegalStateException ex){refused=true;}
   h.assertTrue(refused,"A level-I node in paid is refused (CF1)");
   NbtRecord.write(BookResearch.path(t.l,t.s.id()),r);BookResearch.clearCache();
  }finally{done(t);}
  h.succeed();
 }
}
