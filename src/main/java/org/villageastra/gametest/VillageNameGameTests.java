package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
/** Owner 2026-09-24: a village has its own name from the moment it appears, never the same as another's in the world; it survives a save;
 *  the villages of a save made before names get names when it is read, and two with one name are told apart. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class VillageNameGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void everyVillageHasANameOfItsOwn(GameTestHelper h){
  var l=h.getLevel();var data=SettlementData.get(l.getServer());var a=new Settlement(UUID.randomUUID());var b=new Settlement(UUID.randomUUID());
  var dim=l.dimension().location().toString();
  try{
   data.add(new SettlementData.Entry(a,dim,h.absolutePos(new BlockPos(1,2,1))));data.add(new SettlementData.Entry(b,dim,h.absolutePos(new BlockPos(3,2,3))));
   h.assertTrue(!a.name().isEmpty()&&!b.name().isEmpty()&&!a.name().equals(b.name()),"Two new villages, two names: "+a.name()+" / "+b.name());
   var all=new ArrayList<String>();for(var e:data.entries())all.add(e.settlement().name());
   h.assertTrue(all.stream().noneMatch(String::isEmpty)&&new HashSet<>(all).size()==all.size(),"No village of the world nameless or named twice: "+all);
   var saved=data.save(new CompoundTag());
   var loaded=SettlementData.load(saved);
   h.assertTrue(loaded.entry(a.id()).settlement().name().equals(a.name())&&loaded.entry(b.id()).settlement().name().equals(b.name()),"The names survive a save");
   // A save made before names: every village named when it is read, the same way twice.
   var old=saved.copy();for(var raw:old.getList("settlements",Tag.TAG_COMPOUND))((CompoundTag)raw).remove("name");
   var first=SettlementData.load(old.copy());var second=SettlementData.load(old.copy());
   var names=new HashSet<String>();for(var e:first.entries()){h.assertTrue(!e.settlement().name().isEmpty(),"An old village gets a name");names.add(e.settlement().name());
    h.assertTrue(e.settlement().name().equals(second.entry(e.settlement().id()).settlement().name()),"The same save, the same names");}
   h.assertTrue(names.size()==first.entries().size(),"Old villages never share a name");
   // Two saved with one name: the later one is told apart.
   var twin=saved.copy();for(var raw:twin.getList("settlements",Tag.TAG_COMPOUND))((CompoundTag)raw).putString("name","Дубовка");
   var told=SettlementData.load(twin);var seen=new HashSet<String>();for(var e:told.entries())h.assertTrue(seen.add(e.settlement().name()),"Twins told apart: "+seen);
  }finally{data.remove(a.id());data.remove(b.id());}
  h.succeed();
 }
}
