package org.villageastra.gametest;
import java.util.*;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.world.Workshops;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkshopReadCacheGameTests {
 @GameTest(template="empty",batch="workshop_read_cache",timeoutTicks=100)
 public static void repeatedSensingReadsOnceButWritesAndNextTickRemainAuthoritative(GameTestHelper h)throws Exception{
  var l=h.getLevel();var actor=UUID.randomUUID();var file=Workshops.path(l,actor);var original=new CompoundTag();original.putUUID("id",UUID.randomUUID());original.putString("stage","dig");original.putBoolean("complete",false);original.putInt("surveyCursor",123);
  var cargo=new ListTag();var item=new CompoundTag();item.putString("id","minecraft:sand");item.putByte("Count",(byte)3);cargo.add(item);original.put("cargo",cargo);NbtRecord.write(file,original);var stamp=Files.getLastModifiedTime(file);long size=Files.size(file);
  long before=Workshops.inspectReads();
  for(int i=0;i<100;i++){var seen=Workshops.inspect(l,actor);h.assertTrue(seen.equals(original),"Every sensing caller sees original paid job state");seen.getList("cargo",Tag.TAG_COMPOUND).getCompound(0).putByte("Count",(byte)64);seen.putInt("surveyCursor",999);}
  long reads=Workshops.inspectReads()-before;h.assertTrue(reads==1,"100 same-tick sensing calls must use one verified read, observed="+reads);
  var changed=original.copy();changed.putBoolean("complete",true);NbtRecord.write(file,changed);h.assertTrue(Files.size(file)==size,"Authoritative change preserves size");Files.setLastModifiedTime(file,stamp);
  h.assertTrue(Workshops.inspect(l,actor).getBoolean("complete"),"Same-time same-size authoritative write immediately releases completed job");
  Files.delete(file);h.assertTrue(Workshops.inspect(l,actor).isEmpty(),"Deleted record is not held by cache");NbtRecord.write(file,original);Files.setLastModifiedTime(file,stamp);h.assertTrue(!Workshops.inspect(l,actor).getBoolean("complete"),"Recreated same-time record is read immediately");
  var other=UUID.randomUUID();var otherFile=Workshops.path(l,other);var different=original.copy();different.putInt("surveyCursor",321);NbtRecord.write(otherFile,different);h.assertTrue(Workshops.inspect(l,other).equals(different)&&Workshops.inspect(l,actor).equals(original),"Independent actor paths never share mutable job state");
  h.runAtTickTime(2,()->{
   long tickReads=Workshops.inspectReads();h.assertTrue(Workshops.inspect(l,actor).equals(original)&&Workshops.inspectReads()==tickReads+1,"Next actual server tick revalidates unchanged record");
   try{var bytes=Files.readAllBytes(file);bytes[bytes.length-1]^=1;var sameTime=Files.getLastModifiedTime(file);Files.write(file,bytes);Files.setLastModifiedTime(file,sameTime);}catch(Exception ex){throw new IllegalStateException(ex);}
  });
  h.runAtTickTime(4,()->{
   boolean refused=false;try{Workshops.inspect(l,actor);}catch(IllegalStateException expected){refused=true;}
   h.assertTrue(refused,"A corrupt same-stamp external record is rejected on next tick, never treated as empty");
   try{Files.delete(file);Files.delete(otherFile);}catch(Exception ex){throw new IllegalStateException(ex);}
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_WORKSHOP_READ_CACHE verified100Calls1Read copies=true revision=true delete=true nextTick=true corruption=true");h.succeed();
  });
 }
 @GameTest(template="empty",batch="workshop_read_cache",timeoutTicks=100)
 public static void cachedJournalKeepsActualInventoryAndPhysicalFurnaceUpdatesImmediate(GameTestHelper h){
  var t=ResearchV2Town.town(h,"carpentry");var l=t.l;var chest=org.villageastra.world.LogisticsRoutes.chest(l,t.e,t.shop);chest.clearContent();
  var wants=List.of(new Workshops.Want(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.OAK_PLANKS),4,t.shop.id()));
  var state=new CompoundTag();state.putString("stage","idle");state.putInt("fuelBank",10);NbtRecord.write(Workshops.path(l,t.shop.id()),state);
  h.assertTrue(Workshops.plan(l,t.e,t.shop,chest,wants)==null,"Empty real chest cannot fund cached idle journal");
  chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG));
  h.assertTrue(Workshops.plan(l,t.e,t.shop,chest,wants)!=null,"Same-tick delivery enables a real paid plan");
  chest.clearContent();h.assertTrue(Workshops.plan(l,t.e,t.shop,chest,wants)==null,"Same-tick withdrawal prevents spending absent input");
  var reset=state.copy();reset.putInt("fuelBank",20);org.villageastra.world.NaturalFurnace.release(l,t.shop.id(),reset);
  h.assertTrue(Workshops.inspect(l,t.shop.id()).getInt("fuelBank")==20,"Physical furnace journal commit immediately invalidates sensing");
  ResearchV2Town.done(t);h.succeed();
 }
 @GameTest(template="empty",batch="workshop_read_cache",timeoutTicks=100)
 public static void workshopSensingCacheIsBoundedAndMetadataChangesInvalidate(GameTestHelper h)throws Exception{
  var l=h.getLevel();var ids=new ArrayList<UUID>();var state=new CompoundTag();state.putString("stage","idle");
  for(int i=0;i<257;i++){var id=UUID.randomUUID();ids.add(id);NbtRecord.write(Workshops.path(l,id),state);Workshops.inspect(l,id);}
  long before=Workshops.inspectReads();Workshops.inspect(l,ids.get(0));h.assertTrue(Workshops.inspectReads()==before+1,"Oldest of 257 records is evicted from 256-entry cache");
  var id=ids.get(256);var file=Workshops.path(l,id);var changed=state.copy();changed.putInt("fuelBank",30);
  // External valid replacement does not use AtomicRecord.write; changed metadata alone must invalidate.
  var other=UUID.randomUUID();var otherFile=Workshops.path(l,other);NbtRecord.write(otherFile,changed);Files.copy(otherFile,file,StandardCopyOption.REPLACE_EXISTING);
  h.assertTrue(Workshops.inspect(l,id).equals(changed),"External size-changing replacement is visible in the same server tick");
  for(var saved:ids)Files.delete(Workshops.path(l,saved));Files.delete(otherFile);h.succeed();
 }
}
