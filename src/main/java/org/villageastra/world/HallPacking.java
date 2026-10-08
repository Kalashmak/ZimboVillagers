package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** A communal hall job merges real fragmented stacks; held goods survive any worker leaving. */
public final class HallPacking {
 private HallPacking(){}
 public static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/zimbovillagers-packing/"+village+".bin");}
 public static CompoundTag inspect(ServerLevel l,UUID village){var p=path(l,village);return Files.exists(p)?NbtRecord.read(p):new CompoundTag();}
 /** Called by a worker already at the hall chest with line of sight. At most one shared turn per20ticks, one new sort per100ticks. */
 public static boolean advance(ServerLevel l,SettlementData.Entry e,long now){
  var hall=Workshops.hall(e);var c=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(c==null)return false;var p=path(l,e.settlement().id());var t=inspect(l,e.settlement().id());String stage=t.getString("stage");
  if(stage.equals("take")||stage.equals("put")){
   if(now>=t.getLong("lastTick")&&now-t.getLong("lastTick")<20)return true;t.putLong("lastTick",now);var id=t.getUUID("id");var pos=LogisticsRoutes.position(e,hall);
   if(stage.equals("take")){
    var got=WorldJournal.recoverAmount(l,Settlement.childId(id,"take"));
    if(got.isEmpty()&&!WorldJournal.exists(l,Settlement.childId(id,"take")))got=WorldJournal.takeAmount(l,Settlement.childId(id,"take"),pos,t.getInt("slot"),ItemStack.of(t.getCompound("before")),t.getInt("amount"));
    if(got.isEmpty()){t.putString("stage","idle");t.putLong("next",now+100);}else{t.put("held",got.save(new CompoundTag()));t.putString("stage","put");}NbtRecord.write(p,t);return true;
   }
   var deposit=Settlement.childId(id,"put/"+t.getInt("tries"));var held=ItemStack.of(t.getCompound("held"));boolean exists=WorldJournal.exists(l,deposit);
   if((exists?WorldJournal.recoverExisting(l,deposit)!=null:WorldJournal.deposit(l,deposit,pos,held))){t.remove("held");t.putString("stage","idle");t.putLong("next",now+100);}else if(exists)t.putInt("tries",t.getInt("tries")+1);
   NbtRecord.write(p,t);return true;
  }
  if(now<t.getLong("next"))return false;int empty=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty())empty++;if(empty>LogisticsRoutes.SURPLUS_EMPTY_RESERVE)return false;
  for(int dest=0;dest<c.getContainerSize();dest++){var target=c.getItem(dest);if(target.isEmpty()||target.getCount()>=target.getMaxStackSize())continue;
   for(int source=c.getContainerSize()-1;source>dest;source--){var item=c.getItem(source);if(item.isEmpty()||!ItemStack.isSameItemSameTags(target,item))continue;
    int amount=Math.min(item.getCount(),Math.min(c.getMaxStackSize(),target.getMaxStackSize())-target.getCount());if(amount<=0)continue;
    var job=new CompoundTag();job.putUUID("id",UUID.randomUUID());job.putString("stage","take");job.putInt("slot",source);job.putInt("amount",amount);job.put("before",item.save(new CompoundTag()));job.putLong("lastTick",now);NbtRecord.write(p,job);return true;
   }
  }t.putLong("next",now+100);NbtRecord.write(p,t);return false;
 }
}
