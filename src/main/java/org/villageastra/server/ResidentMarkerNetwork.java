package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.network.*;
import org.villageastra.VillageAstra;
/** Dedicated server-safe S2C channel. Client renderer subscribes to the received event. */
public final class ResidentMarkerNetwork {
 private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(VillageAstra.ID,"resident_markers"),()->"1","1"::equals,"1"::equals);
 public record View(CompoundTag data){}
 public static final class Received extends Event {public final CompoundTag data;Received(CompoundTag data){this.data=data;}}
 public static void init(){CHANNEL.registerMessage(0,View.class,(m,b)->b.writeNbt(m.data),b->new View(Objects.requireNonNull(b.readNbt())),(m,c)->{var context=c.get();context.enqueueWork(()->MinecraftForge.EVENT_BUS.post(new Received(m.data)));context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
 public static void send(ServerPlayer player,CompoundTag data){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new View(data));}
}
