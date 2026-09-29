package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
/** A village owes the depositor equivalent goods, even after its workers move or consume the original stack. */
public final class OwnDeposits extends SavedData {
 private static final class Balance {UUID village,player;ItemStack item;long held,returned,unowned;}
 private final List<Balance> balances=new ArrayList<>();
 public static OwnDeposits get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(OwnDeposits::load,OwnDeposits::new,"villageastra_deposits");}
 private Balance balance(UUID village,UUID player,ItemStack item){for(var b:balances)if(b.village.equals(village)&&b.player.equals(player)&&ItemStack.isSameItemSameTags(b.item,item))return b;var b=new Balance();b.village=village;b.player=player;b.item=item.copyWithCount(1);balances.add(b);return b;}
 /** Returns the part eligible for a first donation reward; returning one's withdrawal earns nothing twice. */
 public int deposit(UUID village,UUID player,ItemStack item,int count){if(count<=0||item.isEmpty())throw new IllegalArgumentException("Empty deposit");var b=balance(village,player,item);int villageReturn=(int)Math.min(count,b.unowned);b.unowned-=villageReturn;int personal=count-villageReturn;int recycled=(int)Math.min(personal,b.returned);b.returned-=recycled;b.held=Math.addExact(b.held,personal);setDirty();return personal-recycled;}
 /** Only the excess over the player's outstanding contribution is village property. */
 public int withdraw(UUID village,UUID player,ItemStack item,int count){if(count<=0||item.isEmpty())throw new IllegalArgumentException("Empty withdrawal");var b=balance(village,player,item);int own=(int)Math.min(count,b.held);b.held-=own;b.returned=Math.addExact(b.returned,own);b.unowned=Math.addExact(b.unowned,count-own);setDirty();return count-own;}
 public static OwnDeposits load(CompoundTag t){if(t.getInt("schema")!=1)throw new IllegalArgumentException("Invalid deposits schema");var d=new OwnDeposits();for(var raw:t.getList("balances",Tag.TAG_COMPOUND)){var x=(CompoundTag)raw;var item=ItemStack.of(x.getCompound("item"));if(item.isEmpty()||x.getLong("held")<0||x.getLong("returned")<0||x.getLong("unowned")<0)throw new IllegalArgumentException("Invalid deposit");var b=d.balance(x.getUUID("village"),x.getUUID("player"),item);b.held=x.getLong("held");b.returned=x.getLong("returned");b.unowned=x.getLong("unowned");}return d;}
 @Override public CompoundTag save(CompoundTag t){t.putInt("schema",1);var list=new ListTag();for(var b:balances){if(b.held==0&&b.returned==0&&b.unowned==0)continue;var x=new CompoundTag();x.putUUID("village",b.village);x.putUUID("player",b.player);x.put("item",b.item.save(new CompoundTag()));x.putLong("held",b.held);x.putLong("returned",b.returned);x.putLong("unowned",b.unowned);list.add(x);}t.put("balances",list);return t;}
}
