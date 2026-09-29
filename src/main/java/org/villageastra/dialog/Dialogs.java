package org.villageastra.dialog;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.world.ResidentEntity;
/** AD-146 (owner 2026-09-24: "all dialogues with NPCs from the chat into dialogue windows — the fewest notes, the most talk"): the one
 *  window of a conversation with a resident. A right click on a resident opens its greeting with the answers every resident has (trade and
 *  gifts, «Как дела?», goodbye) and those other work adds ({@link #addTopic}: the quest board, errands, a companion…); an answer goes to the
 *  handler of its topic ({@link #register}). The server keeps each player's open page: only an answer that page offered, enabled, is
 *  taken, whatever the client sends. {@link #say} replaces a resident's message in the chat with a line in the window. */
public final class Dialogs {
 private Dialogs(){}
 public interface Handler{void choose(ServerPlayer p,ResidentEntity npc,String action,CompoundTag context);}
 public interface Topic{List<DialogOption> options(ServerPlayer p,ResidentEntity npc);}
 private static final Map<String,Handler> HANDLERS=new ConcurrentHashMap<>();
 private static final List<Topic> TOPICS=new CopyOnWriteArrayList<>();
 private record Open(long serial,DialogPage page){}
 private static final Map<UUID,Open> OPEN=new ConcurrentHashMap<>();
 private static final AtomicLong SERIAL=new AtomicLong();
 /** A resident further than this from the player no longer talks with them. */
 public static final double REACH=8;
 public static void register(String topic,Handler handler){if(topic.equals("close"))throw new IllegalArgumentException("close is reserved");HANDLERS.put(topic,handler);}
 public static void addTopic(Topic topic){TOPICS.add(topic);}
 /** Opens the window with this page, or replaces the page of the one open. */
 public static void open(ServerPlayer p,DialogPage page){
  long serial=SERIAL.incrementAndGet();OPEN.put(p.getUUID(),new Open(serial,page));DialogNetwork.send(p,write(p,serial,page));
 }
 /** A resident's word to the player in the window, with «Понятно» — instead of a line in the chat. */
 public static void say(ServerPlayer p,ResidentEntity npc,Component line){
  open(p,new DialogPage(npc==null?null:npc.getUUID(),List.of(line),List.of(DialogOption.of("close/ok",Component.translatable("dialog.villageastra.ok"))),null));
 }
 /** The greeting of a right click: what the resident says first and every answer the player has with them. */
 public static void greet(ServerPlayer p,ResidentEntity npc){open(p,ResidentTalk.greeting(p,npc,options(p,npc)));}
 /** The answers of the greeting: the resident's own, then those of every topic, goodbye last. */
 static List<DialogOption> options(ServerPlayer p,ResidentEntity npc){
  var out=new ArrayList<>(ResidentTalk.own(p,npc));
  for(var t:TOPICS)try{out.addAll(t.options(p,npc));}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(Dialogs.class).error("Dialog topic failed",ex);}
  out.add(DialogOption.of("close/bye",Component.translatable("dialog.villageastra.bye")));return out;
 }
 /** The client closed the window. */
 static void closed(ServerPlayer p,long serial){var o=OPEN.get(p.getUUID());if(o!=null&&o.serial()==serial)OPEN.remove(p.getUUID());}
 /** The serial of the page open for a player (tests), or -1. */
 public static long serial(ServerPlayer p){var o=OPEN.get(p.getUUID());return o==null?-1:o.serial();}
 /** The page open for a player (tests), or null. */
 public static DialogPage page(ServerPlayer p){var o=OPEN.get(p.getUUID());return o==null?null:o.page();}
 /** The player's answer: taken only if the page open for them offered it, enabled, and the resident is still there. */
 public static boolean choose(ServerPlayer p,long serial,String id){
  var o=OPEN.get(p.getUUID());if(o==null||o.serial()!=serial)return false;
  var option=o.page().options().stream().filter(x->x.id().equals(id)).findFirst().orElse(null);if(option==null||!option.enabled())return false;
  ResidentEntity npc=null;
  if(o.page().speaker()!=null){if(!(p.serverLevel().getEntity(o.page().speaker()) instanceof ResidentEntity r)||!r.isAlive()||r.distanceToSqr(p)>REACH*REACH){OPEN.remove(p.getUUID());DialogNetwork.close(p);return false;}npc=r;}
  if(option.topic().equals("close")){OPEN.remove(p.getUUID());DialogNetwork.close(p);return true;}
  var handler=HANDLERS.get(option.topic());if(handler==null){OPEN.remove(p.getUUID());DialogNetwork.close(p);return false;}
  handler.choose(p,npc,option.action(),o.page().context());return true;
 }
 public static void forget(UUID player){OPEN.remove(player);}
 /** The page as the client draws it: the speaker's name and trade, its lines and answers as text components. */
 static CompoundTag write(ServerPlayer p,long serial,DialogPage page){
  var t=new CompoundTag();t.putLong("serial",serial);
  if(page.speaker()!=null){t.putUUID("speaker",page.speaker());
   if(p.serverLevel().getEntity(page.speaker()) instanceof ResidentEntity npc){t.putString("name",Component.Serializer.toJson(npc.getDisplayName()));t.putString("role",Component.Serializer.toJson(ResidentTalk.role(p,npc)));}}
  var lines=new ListTag();for(var l:page.lines())lines.add(StringTag.valueOf(Component.Serializer.toJson(l)));t.put("lines",lines);
  var options=new ListTag();for(var o:page.options()){var x=new CompoundTag();x.putString("id",o.id());x.putString("label",Component.Serializer.toJson(o.label()));x.putBoolean("enabled",o.enabled());
   if(o.tooltip()!=null)x.putString("tooltip",Component.Serializer.toJson(o.tooltip()));options.add(x);}
  t.put("options",options);return t;
 }
}
