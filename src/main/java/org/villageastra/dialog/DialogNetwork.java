package org.villageastra.dialog;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.network.*;
import org.villageastra.VillageAstra;
/** AD-146: the conversation window's own channel — a page to show or a close to the client, an answer or a close to the server. */
public final class DialogNetwork {
 private DialogNetwork(){}
 private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(VillageAstra.ID,"dialog"),()->"1","1"::equals,"1"::equals);
 public record Show(CompoundTag page){}
 public record Close(){}
 public record Choose(long serial,String id){}
 public record Closed(long serial){}
 /** Client side: a page arrived, or the server closed the window. */
 public static final class Shown extends Event{public final CompoundTag page;Shown(CompoundTag page){this.page=page;}}
 public static final class Ended extends Event{}
 public static void init(){
  CHANNEL.registerMessage(0,Show.class,(m,b)->b.writeNbt(m.page),b->new Show(Objects.requireNonNull(b.readNbt())),(m,c)->{c.get().enqueueWork(()->MinecraftForge.EVENT_BUS.post(new Shown(m.page)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  CHANNEL.registerMessage(1,Close.class,(m,b)->{},b->new Close(),(m,c)->{c.get().enqueueWork(()->MinecraftForge.EVENT_BUS.post(new Ended()));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  CHANNEL.registerMessage(2,Choose.class,(m,b)->{b.writeVarLong(m.serial);b.writeUtf(m.id,128);},b->new Choose(b.readVarLong(),b.readUtf(128)),(m,c)->{var ctx=c.get();ctx.enqueueWork(()->{var p=ctx.getSender();if(p!=null)Dialogs.choose(p,m.serial,m.id);});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
  CHANNEL.registerMessage(3,Closed.class,(m,b)->b.writeVarLong(m.serial),b->new Closed(b.readVarLong()),(m,c)->{var ctx=c.get();ctx.enqueueWork(()->{var p=ctx.getSender();if(p!=null)Dialogs.closed(p,m.serial);});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
 }
 static void send(ServerPlayer p,CompoundTag page){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Show(page));}
 static void close(ServerPlayer p){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Close());}
 public static void choose(long serial,String id){CHANNEL.sendToServer(new Choose(serial,id));}
 public static void closed(long serial){CHANNEL.sendToServer(new Closed(serial));}
}
