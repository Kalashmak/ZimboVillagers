package org.villageastra.world;

import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.server.PropertyLedger;
import java.util.*;

/** Snapshot only around an authenticated player click, never infer a nearby culprit. */
public final class OwnedChestMenu extends ChestMenu {
    private final OwnedChestEntity chest;
    public OwnedChestMenu(int id,Inventory inventory,OwnedChestEntity chest) {
        super(MenuType.GENERIC_9x3,id,inventory,chest,3); this.chest=chest;
    }
    public OwnedChestMenu(int id,Inventory inventory,OwnedChestEntity chest,int page){super(MenuType.GENERIC_9x6,id,inventory,HallStorage.page(chest,page),6);this.chest=chest;}
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        List<ItemStack> before=new ArrayList<>();
        for(int i=0;i<chest.getContainerSize();i++) before.add(chest.getItem(i).copy());
        super.clicked(slot,button,type,player);
        if (!(player instanceof ServerPlayer serverPlayer) || !chest.getPersistentData().hasUUID("AstraSettlement")) return;
        long missing=0;var village=chest.getPersistentData().getUUID("AstraSettlement");var deposits=org.villageastra.server.OwnDeposits.get(serverPlayer.server);
        List<ItemStack> after=new ArrayList<>(),keys=new ArrayList<>(),fresh=new ArrayList<>();
        for(int i=0;i<chest.getContainerSize();i++) after.add(chest.getItem(i).copy());
        for(var source:List.of(before,after))for(var s:source)if(!s.isEmpty()&&keys.stream().noneMatch(k->ItemStack.isSameItemSameTags(k,s)))keys.add(s.copyWithCount(1));
        for(var key:keys){int old=before.stream().filter(s->ItemStack.isSameItemSameTags(s,key)).mapToInt(ItemStack::getCount).sum(),now=after.stream().filter(s->ItemStack.isSameItemSameTags(s,key)).mapToInt(ItemStack::getCount).sum();
            // AD-128 CF-1: a withdrawn unit weighs what it would earn as a gift (at least 1), so a gift cannot be taken back and given again.
            if(now<old)missing+=(long)deposits.withdraw(village,player.getUUID(),key,old-now)*org.villageastra.server.Gifts.theftWeight(key);
            else if(now>old){int first=deposits.deposit(village,player.getUUID(),key,now-old);if(first>0)fresh.add(key.copyWithCount(first));}}
        if(missing>0) {
            PropertyLedger.get(serverPlayer.server).theft(chest.getPersistentData().getUUID("AstraSettlement"),player.getUUID(),(int)Math.min(Integer.MAX_VALUE,missing));
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.villageastra.theft",missing),false);
        }
        DonationAccounting.record(serverPlayer,chest,before,fresh);
    }
}
