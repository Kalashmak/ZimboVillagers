package org.villageastra.gametest;
import java.util.*;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.world.NaturalSupplyGoal;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class NaturalReadCacheGameTests {
 @GameTest(template="empty",batch="natural_read_cache",timeoutTicks=100)
 public static void repeatedSensingReadsOnceButWritesAndNextTickRemainAuthoritative(GameTestHelper h)throws Exception{
  var l=h.getLevel();var actor=UUID.randomUUID();var file=NaturalSupplyGoal.path(l,actor);var original=new CompoundTag();original.putUUID("id",UUID.randomUUID());original.putString("stage","dig");original.putBoolean("complete",false);original.putInt("surveyCursor",123);
  var cargo=new ListTag();var item=new CompoundTag();item.putString("id","minecraft:sand");item.putByte("Count",(byte)3);cargo.add(item);original.put("cargo",cargo);NbtRecord.write(file,original);var stamp=Files.getLastModifiedTime(file);
  long before=NaturalSupplyGoal.inspectReads();
  for(int i=0;i<100;i++){var seen=NaturalSupplyGoal.inspect(l,actor);h.assertTrue(seen.equals(original),"Every sensing caller sees original paid job state");seen.getList("cargo",Tag.TAG_COMPOUND).getCompound(0).putByte("Count",(byte)64);seen.putInt("surveyCursor",999);}
  long reads=NaturalSupplyGoal.inspectReads()-before;h.assertTrue(reads==1,"100 same-tick sensing calls must use one verified read, observed="+reads);
  var changed=original.copy();changed.putBoolean("complete",true);NbtRecord.write(file,changed);Files.setLastModifiedTime(file,stamp);
  h.assertTrue(!NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,actor)),"Same-time same-size authoritative write immediately releases completed job");
  Files.delete(file);h.assertTrue(NaturalSupplyGoal.inspect(l,actor).isEmpty(),"Deleted record is not held by cache");NbtRecord.write(file,original);Files.setLastModifiedTime(file,stamp);h.assertTrue(NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,actor)),"Recreated same-time record is read immediately");
  var other=UUID.randomUUID();var otherFile=NaturalSupplyGoal.path(l,other);var different=original.copy();different.putInt("surveyCursor",321);NbtRecord.write(otherFile,different);h.assertTrue(NaturalSupplyGoal.inspect(l,other).equals(different)&&NaturalSupplyGoal.inspect(l,actor).equals(original),"Independent actor paths never share mutable job state");
  h.runAtTickTime(2,()->{
   long tickReads=NaturalSupplyGoal.inspectReads();h.assertTrue(NaturalSupplyGoal.inspect(l,actor).equals(original)&&NaturalSupplyGoal.inspectReads()==tickReads+1,"Next actual server tick revalidates unchanged record");
   try{var bytes=Files.readAllBytes(file);bytes[bytes.length-1]^=1;var sameTime=Files.getLastModifiedTime(file);Files.write(file,bytes);Files.setLastModifiedTime(file,sameTime);}catch(Exception ex){throw new IllegalStateException(ex);}
  });
  h.runAtTickTime(4,()->{
   boolean refused=false;try{NaturalSupplyGoal.inspect(l,actor);}catch(IllegalStateException expected){refused=true;}
   h.assertTrue(refused,"A corrupt same-stamp external record is rejected on next tick, never treated as empty");
   try{Files.delete(file);Files.delete(otherFile);}catch(Exception ex){throw new IllegalStateException(ex);}
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_NATURAL_READ_CACHE verified100Calls1Read copies=true revision=true delete=true nextTick=true corruption=true");h.succeed();
  });
 }
}
