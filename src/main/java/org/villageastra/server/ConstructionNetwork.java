package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import org.villageastra.VillageAstra;
/** Bounded views and pause orders; authority is always checked against live server state. */
public final class ConstructionNetwork {
 // AD-166: protocol 17 adds the optional building UUID to a station-open message.
 private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(VillageAstra.ID,"construction_view"),()->"17","17"::equals,"17"::equals);
 public record SurveyOrder(UUID token,String design,int variant,int action){}
 public static void sendSurvey(SurveyOrder order){CHANNEL.sendToServer(order);}
 private static void initSurvey(){CHANNEL.registerMessage(7,SurveyOrder.class,(m,b)->{b.writeUUID(m.token);b.writeUtf(m.design,32);b.writeByte(m.variant);b.writeByte(m.action);},b->new SurveyOrder(b.readUUID(),b.readUtf(32),b.readByte(),b.readByte()),(m,c)->{var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p!=null&&admit(p)){MayorSurvey.choose(p,m.token,m.design,m.variant,m.action);send(p);}});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 // AD-036: NPC dialog card (server snapshot) and confirmed trade orders with a one-time client token.
 public record TradeView(CompoundTag tag){}
 public static final class TradeOpened extends Event {public final CompoundTag tag;public TradeOpened(CompoundTag tag){this.tag=tag;}}
 public record TradeOrder(UUID token,UUID npc,int side,String item,int count){}
 public static void sendTrade(TradeOrder order){CHANNEL.sendToServer(order);}
 public static void openTrade(net.minecraft.server.level.ServerPlayer p,org.villageastra.world.ResidentEntity npc,String result){var tag=Trade.view(p,npc);tag.putString("result",result);CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new TradeView(tag));}
 // AD-128: a gift of the item in the main hand to a resident, confirmed against the exact quote the card showed.
 public record GiftOrder(UUID token,UUID npc,int slot,String item,int count,long expected){}
 public static void sendGift(GiftOrder order){CHANNEL.sendToServer(order);}
 private static void initGift(){CHANNEL.registerMessage(60,GiftOrder.class,(m,b)->{b.writeUUID(m.token);b.writeUUID(m.npc);b.writeByte(m.slot);b.writeUtf(m.item,64);b.writeVarInt(m.count);b.writeVarLong(m.expected);},b->new GiftOrder(b.readUUID(),b.readUUID(),b.readByte(),b.readUtf(64),b.readVarInt(),b.readVarLong()),(m,c)->{var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p==null)return;String raw=admit(p)?Gifts.order(p,m.token,m.npc,m.slot,m.item,m.count,m.expected):"busy",result=raw.startsWith("gift:")?raw:"gift:"+raw;if(p.serverLevel().getEntity(m.npc) instanceof org.villageastra.world.ResidentEntity npc&&npc.distanceToSqr(p)<=Trade.REACH*Trade.REACH)openTrade(p,npc,result);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 private static void initTrade(){initGift();
  CHANNEL.registerMessage(8,TradeView.class,(m,b)->b.writeNbt(m.tag),b->new TradeView(Objects.requireNonNull(b.readNbt())),(m,c)->{c.get().enqueueWork(()->MinecraftForge.EVENT_BUS.post(new TradeOpened(m.tag)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  CHANNEL.registerMessage(9,TradeOrder.class,(m,b)->{b.writeUUID(m.token);b.writeUUID(m.npc);b.writeByte(m.side);b.writeUtf(m.item,64);b.writeVarInt(m.count);},b->new TradeOrder(b.readUUID(),b.readUUID(),b.readByte(),b.readUtf(64),b.readVarInt()),(m,c)->{var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p==null)return;String result=admit(p)?Trade.order(p,m.token,m.npc,m.side,m.item,m.count):"busy";if(p.serverLevel().getEntity(m.npc) instanceof org.villageastra.world.ResidentEntity npc&&npc.distanceToSqr(p)<=Trade.REACH*Trade.REACH)openTrade(p,npc,result);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
 }
 // AD-037: shared atlas pages. Any visitor may view; only opened chunks and live objects inside them are sent.
 public record AtlasRequest(long generation,int from){}
 public record AtlasPage(CompoundTag tag){}
 public static final class AtlasReceived extends Event {public final CompoundTag tag;public AtlasReceived(CompoundTag tag){this.tag=tag;}}
 public static void sendAtlas(AtlasRequest request){CHANNEL.sendToServer(request);}
 private static final Map<UUID,Integer> ATLAS_LAST=new HashMap<>();
 private static void initAtlas(){
  CHANNEL.registerMessage(10,AtlasRequest.class,(m,b)->{b.writeLong(m.generation);b.writeVarInt(m.from);},b->new AtlasRequest(b.readLong(),b.readVarInt()),(m,c)->{var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p==null)return;int now=p.server.getTickCount();var last=ATLAS_LAST.get(p.getUUID());if(last!=null&&now-last<2)return;ATLAS_LAST.put(p.getUUID(),now);
   var dim=p.serverLevel().dimension().location().toString();var e=SettlementData.get(p.server).entries().stream().filter(v->v.dimension().equals(dim)&&v.center().distSqr(p.blockPosition())<192*192).min(Comparator.comparingDouble(v->v.center().distSqr(p.blockPosition()))).orElse(null);
   var tag=e==null?new CompoundTag():org.villageastra.world.Atlas.view(p.serverLevel(),e,m.generation,Math.max(0,m.from),org.villageastra.world.Atlas.PAGE);if(e==null)tag.putBoolean("missing",true);
   else{tag.putString("name",e.settlement().name());var g=e.settlement().governance();tag.putBoolean("mayor",g.canManage(p.getUUID(),g.epoch())&&ManagementOrders.allowedContext(p,e));tag.putLong("revision",g.revision());}CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new AtlasPage(tag));});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
  CHANNEL.registerMessage(11,AtlasPage.class,(m,b)->b.writeNbt(m.tag),b->new AtlasPage(Objects.requireNonNull(b.readNbt())),(m,c)->{c.get().enqueueWork(()->MinecraftForge.EVENT_BUS.post(new AtlasReceived(m.tag)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
 }
 // AD-053: the mayor claims one chunk of the map as a quarry.
 public record QuarryOrder(UUID village,long epoch,long chunk){}
 public static void sendQuarry(QuarryOrder order){CHANNEL.sendToServer(order);}
 private static void initQuarry(){CHANNEL.registerMessage(17,QuarryOrder.class,(m,b)->{b.writeUUID(m.village);b.writeLong(m.epoch);b.writeLong(m.chunk);},
  b->new QuarryOrder(b.readUUID(),b.readLong(),b.readLong()),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null)return;
   var e=SettlementData.get(p.server).entry(m.village);
   String reason="village";
   if(e!=null){var g=e.settlement().governance();
    reason=!g.canManage(p.getUUID(),m.epoch)||!ManagementOrders.allowedContext(p,e)?"mayor"
      :org.villageastra.world.Quarry.claim(p.serverLevel(),e,new net.minecraft.world.level.ChunkPos(m.chunk));}
   p.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason.isEmpty()?"quarry.villageastra.claimed":"quarry.villageastra.refused."+reason),true);
   send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 // AD-057: orders given on the settlement map — a house, a road or a clearing; action 0 is a dry run that only reports what the order would do.
 public record MapOrder(UUID village,long epoch,int tool,int action,long a,long b,int variant,int level,String design){}
 public record MapResult(CompoundTag tag){}
 public static final class MapResultReceived extends Event {public final CompoundTag tag;public MapResultReceived(CompoundTag tag){this.tag=tag;}}
 public static void sendMapOrder(MapOrder order){CHANNEL.sendToServer(order);}
 private static final Map<UUID,Integer> MAP_LAST=new HashMap<>();
 private static void initMapOrders(){
  CHANNEL.registerMessage(18,MapOrder.class,(m,b)->{b.writeUUID(m.village);b.writeLong(m.epoch);b.writeByte(m.tool);b.writeByte(m.action);b.writeLong(m.a);b.writeLong(m.b);b.writeVarInt(m.variant);b.writeVarInt(m.level);b.writeUtf(m.design,32);},
   b->new MapOrder(b.readUUID(),b.readLong(),b.readByte(),b.readByte(),b.readLong(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readUtf(32)),(m,c)->{var context=c.get();context.enqueueWork(()->{
    var p=context.getSender();if(p==null)return;int now=p.server.getTickCount();var last=MAP_LAST.get(p.getUUID());
    // A dry run surveys real blocks and is rate limited like planning; a real order is never dropped by the limiter.
    if(m.action==0&&last!=null&&now-last<5)return;if(m.action==0)MAP_LAST.put(p.getUUID(),now);
    var e=SettlementData.get(p.server).entry(m.village);var l=p.serverLevel();
    var a=net.minecraft.core.BlockPos.of(m.a);var b=net.minecraft.core.BlockPos.of(m.b);
    CompoundTag tag;
    if(e==null){tag=new CompoundTag();tag.putString("reason","village");}
    else if(!e.settlement().governance().canManage(p.getUUID(),m.epoch)||!ManagementOrders.allowedContext(p,e)){tag=new CompoundTag();tag.putString("reason","mayor");}
    else if(m.action==0)tag=org.villageastra.world.MapOrders.preview(l,e,m.tool,a,b,m.variant,m.level);
    else{
     String result=switch(m.tool){
      case org.villageastra.world.MapOrders.HOUSE->org.villageastra.world.MapOrders.house(l,e,m.design,a,m.variant);
      case org.villageastra.world.MapOrders.ROAD->org.villageastra.world.MapOrders.road(l,e,a,b,m.variant);
      case org.villageastra.world.MapOrders.DEMOLISH->org.villageastra.world.MapOrders.demolish(l,e,a,b,m.level);
      case org.villageastra.world.MapOrders.WALL->org.villageastra.world.Walls.order(l,e,m.variant==2?org.villageastra.world.Walls.Shape.FITTED:m.variant==1?org.villageastra.world.Walls.Shape.ROUND:org.villageastra.world.Walls.Shape.SQUARE,m.level);
      case org.villageastra.world.MapOrders.RELOCATE->org.villageastra.world.Relocations.order(l,e,b,a,m.variant);
      default->"tool";};
     tag=new CompoundTag();tag.putString("reason",result);tag.putBoolean("ordered",result.isEmpty());SettlementData.get(p.server).setDirty();
     p.displayClientMessage(net.minecraft.network.chat.Component.translatable(result.isEmpty()?"maporder.villageastra.done."+m.tool:"maporder.villageastra.refused."+org.villageastra.world.Walls.reasonKey(result),org.villageastra.world.Walls.reasonArgs(result)),true);
     send(p);}
    tag.putInt("tool",m.tool);tag.putInt("action",m.action);
    CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MapResult(tag));});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
  CHANNEL.registerMessage(19,MapResult.class,(m,b)->b.writeNbt(m.tag),b->new MapResult(Objects.requireNonNull(b.readNbt())),(m,c)->{c.get().enqueueWork(()->MinecraftForge.EVENT_BUS.post(new MapResultReceived(m.tag)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
 }
 // AD-052: the mayor orders the second level of a workshop — real equipment installed by the village crew.
 public record UpgradeOrder(UUID village,long epoch,UUID building){}
 public static void sendUpgrade(UpgradeOrder order){CHANNEL.sendToServer(order);}
 private static void initUpgrades(){CHANNEL.registerMessage(16,UpgradeOrder.class,(m,b)->{b.writeUUID(m.village);b.writeLong(m.epoch);b.writeUUID(m.building);},
  b->new UpgradeOrder(b.readUUID(),b.readLong(),b.readUUID()),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null)return;
   String reason=org.villageastra.world.BuildingUpgrades.order(p,m.village,m.epoch,m.building);
   p.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason.isEmpty()?"upgrade.villageastra.done":"upgrade.villageastra.refused."+reason),true);
   send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 // AD-135: the mayor orders an annex of one building; the crew builds it at its site beside the building.
 public record AnnexOrder(UUID village,long epoch,UUID building,String type){}
 public static void sendAnnex(AnnexOrder order){CHANNEL.sendToServer(order);}
 private static void initAnnexes(){CHANNEL.registerMessage(26,AnnexOrder.class,(m,b)->{b.writeUUID(m.village);b.writeLong(m.epoch);b.writeUUID(m.building);b.writeUtf(m.type,64);},
  b->new AnnexOrder(b.readUUID(),b.readLong(),b.readUUID(),b.readUtf(64)),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null)return;
   String reason=org.villageastra.world.Annexes.order(p,m.village,m.epoch,m.building,m.type);
   p.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason.isEmpty()?"annex.villageastra.done":"annex.villageastra.refused."+reason),true);
   send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 // AD-049: the mayor runs campaigns, sieges and annexations from the office instead of operator commands.
 public record WarOrder(UUID village,long epoch,int action,UUID target){}
 public static void sendWar(WarOrder order){CHANNEL.sendToServer(order);}
 private static void initWar(){CHANNEL.registerMessage(15,WarOrder.class,(m,b)->{b.writeUUID(m.village);b.writeLong(m.epoch);b.writeByte(m.action);b.writeUUID(m.target);},
  b->new WarOrder(b.readUUID(),b.readLong(),b.readByte(),b.readUUID()),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null)return;
   String reason=org.villageastra.world.Warfare.order(p,m.village,m.epoch,m.action,m.target);
   p.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason.isEmpty()?"war.villageastra.done":"war.villageastra.refused."+reason),true);
   send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 // ISO-003/ISO-005: the mayor plans a design on the atlas and gets the estimate of that exact spot back.
 public record PlanRequest(String design,int variant,long origin){}
 public record PlanResult(CompoundTag tag){}
 public static final class PlanReceived extends Event {public final CompoundTag tag;public PlanReceived(CompoundTag tag){this.tag=tag;}}
 public static void sendPlan(PlanRequest request){CHANNEL.sendToServer(request);}
 private static final Map<UUID,Integer> PLAN_LAST=new HashMap<>();
 private static void initPlans(){
  CHANNEL.registerMessage(13,PlanRequest.class,(m,b)->{b.writeUtf(m.design,32);b.writeByte(m.variant);b.writeLong(m.origin);},b->new PlanRequest(b.readUtf(32),b.readByte(),b.readLong()),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null)return;int now=p.server.getTickCount();var last=PLAN_LAST.get(p.getUUID());
   // Planning is a survey of real blocks: it is rate limited like the atlas and never changes anything.
   if(last!=null&&now-last<5)return;PLAN_LAST.put(p.getUUID(),now);
   var dim=p.serverLevel().dimension().location().toString();
   var e=SettlementData.get(p.server).entries().stream().filter(v->v.dimension().equals(dim)&&v.center().distSqr(p.blockPosition())<192*192).min(Comparator.comparingDouble(v->v.center().distSqr(p.blockPosition()))).orElse(null);
   var tag=new CompoundTag();
   if(e==null)tag.putBoolean("missing",true);
   else if(!p.getUUID().equals(e.settlement().governance().playerMayor()))tag.putString("reason","mayor");
   else tag=org.villageastra.world.Plans.estimate(p.serverLevel(),e,m.design,m.variant,net.minecraft.core.BlockPos.of(m.origin));
   CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new PlanResult(tag));});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
  CHANNEL.registerMessage(14,PlanResult.class,(m,b)->b.writeNbt(m.tag),b->new PlanResult(Objects.requireNonNull(b.readNbt())),(m,c)->{c.get().enqueueWork(()->MinecraftForge.EVENT_BUS.post(new PlanReceived(m.tag)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
 }
 // AD-085: the mayor orders a trip to leave its stuck cart and go on on foot.
 public record CartOrder(UUID village,UUID trip,long epoch,long revision){}
 public static void sendCart(CartOrder order){CHANNEL.sendToServer(order);}
 private static void initCart(){CHANNEL.registerMessage(22,CartOrder.class,(m,b)->{b.writeUUID(m.village);b.writeUUID(m.trip);b.writeLong(m.epoch);b.writeLong(m.revision);},
  b->new CartOrder(b.readUUID(),b.readUUID(),b.readLong(),b.readLong()),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null||!admit(p))return;
   String reason=ManagementOrders.leaveCart(p,m.village,m.trip,m.epoch,m.revision);
   p.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason.isEmpty()?"cart.villageastra.left":"cart.villageastra.refused."+reason),true);
   send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 // AD-131: the mayor starts or stops a forester's sawmill (message 20, was the nursery's size; protocol 16).
 // AD-138: the mayor switches the kind of animal one pen of a livestock yard keeps (message 27).
 public record PenOrder(UUID village,UUID building,int pen,long epoch,long revision){}
 public static void sendPen(PenOrder order){CHANNEL.sendToServer(order);}
 private static void initPens(){CHANNEL.registerMessage(27,PenOrder.class,(m,b)->{b.writeUUID(m.village);b.writeUUID(m.building);b.writeVarInt(m.pen);b.writeLong(m.epoch);b.writeLong(m.revision);},
  b->new PenOrder(b.readUUID(),b.readUUID(),b.readVarInt(),b.readLong(),b.readLong()),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null||!admit(p))return;
   String reason=LivestockPolicies.order(p,m.village,m.building,m.pen,m.epoch,m.revision);
   p.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason.isEmpty()?"pen.villageastra.done":"pen.villageastra.refused."+reason),true);
   send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 public record SawOrder(UUID village,UUID building,long epoch,long revision){}
 public static void sendSaw(SawOrder order){CHANNEL.sendToServer(order);}
 private static void initNurseries(){CHANNEL.registerMessage(20,SawOrder.class,(m,b)->{b.writeUUID(m.village);b.writeUUID(m.building);b.writeLong(m.epoch);b.writeLong(m.revision);},
   b->new SawOrder(b.readUUID(),b.readUUID(),b.readLong(),b.readLong()),(m,c)->{var context=c.get();context.enqueueWork(()->{
    var p=context.getSender();if(p==null||!admit(p))return;
    String reason=ForestPolicies.orderSaw(p,m.village,m.building,m.epoch,m.revision);
    p.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason.isEmpty()?"forest.villageastra.saw_done":"forest.villageastra.refused."+reason),true);
    send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
  // AD-093, AD-131: the next opened kind of tree a forester's hut prefers (message 23).
  CHANNEL.registerMessage(23,SpeciesOrder.class,(m,b)->{b.writeUUID(m.village);b.writeUUID(m.building);b.writeLong(m.epoch);b.writeLong(m.revision);},
   b->new SpeciesOrder(b.readUUID(),b.readUUID(),b.readLong(),b.readLong()),(m,c)->{var context=c.get();context.enqueueWork(()->{
    var p=context.getSender();if(p==null||!admit(p))return;
    String reason=ForestPolicies.orderSpecies(p,m.village,m.building,m.epoch,m.revision);
    p.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason.isEmpty()?"forest.villageastra.species_done":"forest.villageastra.refused."+reason),true);
    send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 public record SpeciesOrder(UUID village,UUID building,long epoch,long revision){}
 // AD-094: an archer sent up a tower of the castle wall (message 24, protocol 15).
 public record ArcherOrder(UUID village,UUID tower,long epoch,long revision){}
 public static void sendArcher(ArcherOrder order){CHANNEL.sendToServer(order);}
 private static void initArchers(){CHANNEL.registerMessage(24,ArcherOrder.class,(m,b)->{b.writeUUID(m.village);b.writeUUID(m.tower);b.writeLong(m.epoch);b.writeLong(m.revision);},
  b->new ArcherOrder(b.readUUID(),b.readUUID(),b.readLong(),b.readLong()),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null||!admit(p))return;
   String reason=ManagementOrders.postArcher(p,m.village,m.tower,m.epoch,m.revision);
   p.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason.isEmpty()?"tower.villageastra.posted":"tower.villageastra.refused."+reason),true);
   send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 public static void sendSpecies(SpeciesOrder order){CHANNEL.sendToServer(order);}
 // AD-040: taking or abandoning a notice board quest.
 public record QuestOrder(UUID village,UUID quest,int action){}
 public static void sendQuest(QuestOrder order){CHANNEL.sendToServer(order);}
 private static void initQuests(){CHANNEL.registerMessage(12,QuestOrder.class,(m,b)->{b.writeUUID(m.village);b.writeUUID(m.quest);b.writeByte(m.action);},b->new QuestOrder(b.readUUID(),b.readUUID(),b.readByte()),(m,c)->{var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p==null||!admit(p))return;
   String result=m.action==2?org.villageastra.world.Quests.handOver(p,m.village,m.quest):m.action==1?org.villageastra.world.Quests.abandon(p,m.village,m.quest):org.villageastra.world.Quests.take(p,m.village,m.quest);
   p.displayClientMessage(net.minecraft.network.chat.Component.translatable("quest.villageastra.result."+result),true);send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 public record OpenStation(int section,UUID building){public OpenStation(int section){this(section,null);}}
 public static final class Opened extends Event {public final int section;public final UUID building;public Opened(int section){this(section,null);}public Opened(int section,UUID building){this.section=section;this.building=building;}}
 public static void open(net.minecraft.server.level.ServerPlayer p,int section){BUILDING_FOCUS.remove(p.getUUID());send(p);CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new OpenStation(section));}
 private static void initStation(){CHANNEL.registerMessage(6,OpenStation.class,(m,b)->{b.writeByte(m.section);b.writeNullable(m.building,FriendlyByteBuf::writeUUID);},b->new OpenStation(b.readByte(),b.readNullable(FriendlyByteBuf::readUUID)),(m,c)->{c.get().enqueueWork(()->MinecraftForge.EVENT_BUS.post(new Opened(m.section,m.building)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
 private static final Map<UUID,CompoundTag> SENT=new HashMap<>();private static long passes;private static final Map<UUID,Integer> LAST_REQUEST=new HashMap<>();
 // AD-078: the mayor calls off the project the crew has not started yet.
 public record CancelOrder(UUID village,UUID project,long epoch,long revision){}
 public static void sendCancel(CancelOrder order){CHANNEL.sendToServer(order);}
 private static void initCancel(){CHANNEL.registerMessage(21,CancelOrder.class,(m,b)->{b.writeUUID(m.village);b.writeUUID(m.project);b.writeLong(m.epoch);b.writeLong(m.revision);},
  b->new CancelOrder(b.readUUID(),b.readUUID(),b.readLong(),b.readLong()),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null||!admit(p))return;
   String reason=ManagementOrders.cancel(p,m.village,m.project,m.epoch,m.revision);
   p.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason.isEmpty()?"construction.villageastra.called_off":"construction.villageastra.cancel_refused."+reason),true);
   send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 public record PauseOrder(UUID village,UUID project,long epoch,long revision,boolean paused){}
 public static void sendPause(PauseOrder order){CHANNEL.sendToServer(order);}
 public record FarmOrder(UUID village,UUID building,long epoch,long revision,int field,String crop){public FarmOrder(UUID village,UUID building,long epoch,long revision,String crop){this(village,building,epoch,revision,0,crop);}}
 public static void sendFarm(FarmOrder order){CHANNEL.sendToServer(order);}
 public record ResearchOrder(UUID village,long epoch,long revision,String node,int action){public ResearchOrder(UUID village,long epoch,long revision,String node){this(village,epoch,revision,node,0);}}
 public static void sendResearch(ResearchOrder order){CHANNEL.sendToServer(order);}
 private static void initResearch(){CHANNEL.registerMessage(5,ResearchOrder.class,(m,b)->{b.writeUUID(m.village);b.writeLong(m.epoch);b.writeLong(m.revision);b.writeUtf(m.node,64);b.writeByte(m.action);},b->new ResearchOrder(b.readUUID(),b.readLong(),b.readLong(),b.readUtf(64),b.readByte()),(m,c)->{var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p!=null&&admit(p)){BookResearch.order(p,m.village,m.epoch,m.revision,m.node,m.action);send(p);}});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 private static void initFarm(){CHANNEL.registerMessage(4,FarmOrder.class,(m,b)->{b.writeUUID(m.village);b.writeUUID(m.building);b.writeLong(m.epoch);b.writeLong(m.revision);b.writeByte(m.field);b.writeUtf(m.crop,24);},b->new FarmOrder(b.readUUID(),b.readUUID(),b.readLong(),b.readLong(),b.readByte(),b.readUtf(24)),(m,c)->{var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p!=null&&admit(p)){FarmPolicies.order(p,m.village,m.building,m.epoch,m.revision,m.field,m.crop);send(p);}});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 public record DraftOrder(UUID village,long epoch,long revision,int action,UUID proposal){}
 public static void sendDraft(DraftOrder order){CHANNEL.sendToServer(order);}
 private static void initDraft(){CHANNEL.registerMessage(3,DraftOrder.class,(m,b)->{b.writeUUID(m.village);b.writeLong(m.epoch);b.writeLong(m.revision);b.writeByte(m.action);b.writeUUID(m.proposal);},b->new DraftOrder(b.readUUID(),b.readLong(),b.readLong(),b.readByte(),b.readUUID()),(m,c)->{var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p!=null&&admit(p)){ConstructionDrafts.order(p,m.village,m.epoch,m.revision,m.action,m.proposal);send(p);}});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 public record ElectionOrder(UUID village,long epoch,long sequence,int action){}
 public static void sendElection(ElectionOrder order){CHANNEL.sendToServer(order);}
 private static void initElection(){CHANNEL.registerMessage(2,ElectionOrder.class,(m,b)->{b.writeUUID(m.village);b.writeLong(m.epoch);b.writeLong(m.sequence);b.writeByte(m.action);},b->new ElectionOrder(b.readUUID(),b.readLong(),b.readLong(),b.readByte()),(m,c)->{var context=c.get();context.enqueueWork(()->{var player=context.getSender();if(player!=null&&admit(player)){Elections.order(player,m.village,m.epoch,m.sequence,m.action);send(player);}});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 public record Snapshot(CompoundTag tag){}
 /** The quests tab of the inventory: the client asks for the player's own errands, the server answers wherever the player stands. */
 public record QuestLogRequest(){}
 public record QuestLog(CompoundTag tag){}
 public static final class QuestLogReceived extends Event {public final CompoundTag tag;public QuestLogReceived(CompoundTag tag){this.tag=tag;}}
 public static void askQuestLog(){CHANNEL.sendToServer(new QuestLogRequest());}
 private static void initQuestLog(){
  CHANNEL.registerMessage(28,QuestLogRequest.class,(m,b)->{},b->new QuestLogRequest(),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null)return;CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new QuestLog(org.villageastra.world.Quests.log(p)));});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
  CHANNEL.registerMessage(29,QuestLog.class,(m,b)->b.writeNbt(m.tag),b->new QuestLog(Objects.requireNonNull(b.readNbt())),(m,c)->{c.get().enqueueWork(()->MinecraftForge.EVENT_BUS.post(new QuestLogReceived(m.tag)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
 }
 public static final class Received extends Event {public final CompoundTag tag;public Received(CompoundTag tag){this.tag=tag;}}
 // AD-123: a trail to a neighbour, or a crew pass over the finished trail.
 public record TrailOrder(UUID village,long epoch,UUID target,int action,int surface,boolean light,long[] waypoints){}
 public static void sendTrail(TrailOrder order){CHANNEL.sendToServer(order);}
 private static void initTrails(){CHANNEL.registerMessage(25,TrailOrder.class,(m,b)->{b.writeUUID(m.village);b.writeLong(m.epoch);b.writeUUID(m.target);b.writeByte(m.action);b.writeByte(m.surface);b.writeBoolean(m.light);b.writeLongArray(m.waypoints);},
  b->new TrailOrder(b.readUUID(),b.readLong(),b.readUUID(),b.readByte(),b.readByte(),b.readBoolean(),b.readLongArray(null,TrailOrders.MAX_WIRE)),(m,c)->{var context=c.get();context.enqueueWork(()->{
   var p=context.getSender();if(p==null||!admit(p))return;TrailOrders.handle(p,m.village,m.epoch,m.target,m.action,m.surface,m.light,m.waypoints);send(p);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 public static void init(){initQuestLog();initTrails();initCart();initArchers();initCancel();initMapOrders();initTrade();initAtlas();initPlans();initWar();initUpgrades();initPens();initAnnexes();initQuarry();initQuests();initNurseries();initSurvey();initStation();initResearch();initFarm();initDraft();initElection();CHANNEL.registerMessage(1,PauseOrder.class,(m,b)->{b.writeUUID(m.village);b.writeUUID(m.project);b.writeLong(m.epoch);b.writeLong(m.revision);b.writeBoolean(m.paused);},b->new PauseOrder(b.readUUID(),b.readUUID(),b.readLong(),b.readLong(),b.readBoolean()),(m,c)->{var context=c.get();context.enqueueWork(()->{var player=context.getSender();if(player!=null&&admit(player)){ManagementOrders.pause(player,m.village,m.project,m.epoch,m.revision,m.paused);send(player);}});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));CHANNEL.registerMessage(0,Snapshot.class,(m,b)->b.writeNbt(m.tag),b->new Snapshot(Objects.requireNonNull(b.readNbt())),(m,c)->{c.get().enqueueWork(()->MinecraftForge.EVENT_BUS.post(new Received(m.tag)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
 private static boolean admit(net.minecraft.server.level.ServerPlayer player){int now=player.server.getTickCount();var last=LAST_REQUEST.get(player.getUUID());if(last!=null&&now-last<5)return false;LAST_REQUEST.put(player.getUUID(),now);return true;}
 private static final Map<UUID,org.villageastra.world.BuildingSigns.Target> BUILDING_FOCUS=new HashMap<>();
 public static void openBuilding(net.minecraft.server.level.ServerPlayer p,org.villageastra.world.BuildingSigns.Target target){BUILDING_FOCUS.put(p.getUUID(),target);send(p);CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new OpenStation(2,target.building().id()));}
 private static CompoundTag view(net.minecraft.server.level.ServerPlayer player){
  var tag=ConstructionDrafts.view(player,ConstructionViews.nearby(player.serverLevel(),player.blockPosition()));
  var focused=BUILDING_FOCUS.get(player.getUUID());if(focused!=null){var e=SettlementData.get(player.server).entry(focused.entry().settlement().id());var b=e==null?null:e.settlement().buildings().stream().filter(x->x.id().equals(focused.building().id())).findFirst().orElse(null);if(b==null||!e.dimension().equals(player.serverLevel().dimension().location().toString())||org.villageastra.world.BuildingPlacement.origin(e,b).distSqr(player.blockPosition())>128*128)BUILDING_FOCUS.remove(player.getUUID());else if(!tag.hasUUID("village")||!tag.getUUID("village").equals(e.settlement().id())){tag=new CompoundTag();tag.putString("dimension",e.dimension());tag.putUUID("village",e.settlement().id());}}
  if(tag.hasUUID("village")){var entry=SettlementData.get(player.server).entry(tag.getUUID("village"));var g=entry.settlement().governance();boolean holder=g.canManage(player.getUUID(),g.epoch()),safe=ManagementOrders.allowedContext(player,entry);tag.putBoolean("canManage",holder&&safe);tag.putString("lockReason",!holder?"only_mayor":!safe?"unsafe":"manage");}
  Elections.addView(player,tag);FarmPolicies.addView(player,tag);BookResearch.addView(player,tag);MayorSurvey.addView(player,tag);org.villageastra.world.Quests.addView(player,tag);org.villageastra.world.Warfare.addView(player,tag);org.villageastra.world.BuildingUpgrades.addView(player,tag);BuildingCards.addView(player,tag);OfficeOverview.addView(player,tag);
  // Owner 2026-09-24: the office shows its village by name.
  if(tag.hasUUID("village")){var named=SettlementData.get(player.server).entry(tag.getUUID("village"));if(named!=null)tag.putString("name",named.settlement().name());}
  return tag;
 }
 public static void send(net.minecraft.server.level.ServerPlayer player){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(view(player)));}
 public static void clear(){BUILDING_FOCUS.clear();SENT.clear();LAST_REQUEST.clear();ATLAS_LAST.clear();passes=0;ConstructionDrafts.clear();MayorSurvey.clear();OfficeOverview.clear();}
 public static void tick(net.minecraft.server.MinecraftServer server){
  passes++;ConstructionDrafts.prune(server);var online=server.getPlayerList().getPlayers().stream().map(p->p.getUUID()).toList();
  SENT.keySet().retainAll(online);LAST_REQUEST.keySet().retainAll(online);BUILDING_FOCUS.keySet().retainAll(online);
  // Background cards share the existing five-tick heartbeat. Explicit orders
  // still call send immediately; custody and worker decisions do not use snapshots.
  if(passes%5!=0)return;
  for(var player:server.getPlayerList().getPlayers()){
   var tag=view(player);var previous=SENT.get(player.getUUID());
   if(Boolean.getBoolean("villageastra.constructionSmoke")&&passes%10==0)com.mojang.logging.LogUtils.getLogger().info("ASTRA_CONSTRUCTION server snapshot={} player={}",tag.getAllKeys(),player.blockPosition());
   if(!tag.equals(previous)||passes%5==0){SENT.put(player.getUUID(),tag.copy());CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(tag));}
  }
 }
}
