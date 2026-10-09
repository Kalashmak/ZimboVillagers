package org.villageastra.gametest;
import java.util.*;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ToolReadCacheGameTests {
 @GameTest(template="empty",batch="tool_read_cache",timeoutTicks=100)
 public static void repeatedFundingViewsReadOnceButToolAndStockChangesRemainImmediate(GameTestHelper h)throws Exception{
  var t=ResearchV2Town.town(h,"mine");var l=t.l;
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());
  var stock=LogisticsRoutes.chest(l,t.e,t.hall());stock.clearContent();stock.setItem(0,new ItemStack(Items.COBBLESTONE,64));
  var own=LogisticsRoutes.chest(l,t.e,t.shop);if(own!=null)own.clearContent();
  var pick=new ItemStack(Items.STONE_PICKAXE);pick.setDamageValue(pick.getMaxDamage());var original=new CompoundTag();original.put("tool",pick.save(new CompoundTag()));
  var file=MineWork.path(l,t.shop.id());NbtRecord.write(file,original);var stamp=Files.getLastModifiedTime(file);long size=Files.size(file),before=ToolSupplyReserve.workReads();
  for(int i=0;i<100;i++)h.assertTrue(HallReserve.buildView(l,t.e,stock).countItem(Items.COBBLESTONE)==61,"Every funding view keeps the three actual repair stones");
  h.assertTrue(ToolSupplyReserve.workReads()-before==1,"100 same-tick funding views must read unchanged work once, observed="+(ToolSupplyReserve.workReads()-before));
  ToolSupplyReserve.inspectWork(l,t.shop.id()).getCompound("tool").getCompound("tag").putInt("Damage",0);
  h.assertTrue(ToolSupplyReserve.needed(l,t.e),"Caller mutation cannot repair the stored tool");
  stock.setItem(1,new ItemStack(Items.STONE_PICKAXE));h.assertTrue(!ToolSupplyReserve.needed(l,t.e),"Real available stock is checked again in the same tick");stock.setItem(1,ItemStack.EMPTY);h.assertTrue(ToolSupplyReserve.needed(l,t.e),"Removing the actual spare restores the repair need immediately");
  var changed=original.copy();changed.getCompound("tool").getCompound("tag").putInt("Damage",pick.getMaxDamage()-1);NbtRecord.write(file,changed);h.assertTrue(Files.size(file)==size,"Tool wear change retains record size");Files.setLastModifiedTime(file,stamp);
  h.assertTrue(!ToolSupplyReserve.needed(l,t.e),"Same-time same-size authoritative tool update is immediately visible");
  Files.delete(file);h.assertTrue(ToolSupplyReserve.needed(l,t.e),"Deleted work cannot keep an old usable tool");NbtRecord.write(file,changed);Files.setLastModifiedTime(file,stamp);h.assertTrue(!ToolSupplyReserve.needed(l,t.e),"Recreated tool record is read immediately");
  var other=UUID.randomUUID();var otherFile=MineWork.path(l,other);NbtRecord.write(otherFile,original);h.assertTrue(ToolSupplyReserve.inspectWork(l,other).equals(original)&&ToolSupplyReserve.inspectWork(l,t.shop.id()).equals(changed),"Workplaces never share mutable tool records");
  h.runAtTickTime(2,()->{
   long reads=ToolSupplyReserve.workReads();h.assertTrue(ToolSupplyReserve.inspectWork(l,t.shop.id()).equals(changed)&&ToolSupplyReserve.workReads()==reads+1,"Next actual server tick revalidates the tool record");
   try{var raw=Files.readAllBytes(file);raw[raw.length-1]^=1;var time=Files.getLastModifiedTime(file);Files.write(file,raw);Files.setLastModifiedTime(file,time);}catch(Exception ex){throw new IllegalStateException(ex);}
  });
  h.runAtTickTime(4,()->{
   boolean refused=false;try{ToolSupplyReserve.needed(l,t.e);}catch(IllegalStateException expected){refused=true;}
   h.assertTrue(refused,"Corrupt external tool record is rejected next tick, never treated as a missing tool");
   try{Files.delete(file);Files.delete(otherFile);}catch(Exception ex){throw new IllegalStateException(ex);}ResearchV2Town.done(t);h.succeed();
  });
 }
}
