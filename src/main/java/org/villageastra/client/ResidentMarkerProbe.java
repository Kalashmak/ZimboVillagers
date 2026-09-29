package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
import org.villageastra.persistence.NbtRecord;
/** Real S2C snapshots and rendered markers; the client never installs its own label cache. */
final class ResidentMarkerProbe {
 private static UUID village,resident;private static Settlement.Building clinic;private static CompoundTag quest;private static int ticks,phase,settle;private static boolean positioned;private static volatile boolean ready;private static volatile String failure;
 static boolean enabled(){return Boolean.getBoolean("villageastra.residentMarkersSmoke");}
 static void setup(net.minecraft.server.MinecraftServer server){var l=server.overworld();var center=new BlockPos(80,-61,0);var s=new Settlement(UUID.randomUUID());village=s.id();var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));clinic=new Settlement.Building(UUID.randomUUID(),"clinic",0,0,0);s.addBuilding(clinic);var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.DOCTOR,clinic.id());resident=r.id();var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(server).add(e);l.setBlock(LogisticsRoutes.position(e,clinic),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(village,r);npc.setNoAi(true);npc.moveTo(80.5,-60,.5,0,0);l.addFreshEntity(npc);var p=server.getPlayerList().getPlayers().get(0);p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);camera(server);
  quest=new CompoundTag();quest.putUUID("id",UUID.randomUUID());quest.putUUID("giver",resident);quest.putUUID("reserved",p.getUUID());quest.putString("template",Pleas.PLEA);quest.putString("state",Quests.OPEN);quest.putString("item","villageastra:bandage");quest.putInt("target",4);quest.putLong("deadline",SettlementData.get(server).clock().ticks()+24000);board(server);ready=true;
 }
 private static void board(net.minecraft.server.MinecraftServer server){var b=new CompoundTag();b.putInt("schema",1);var list=new ListTag();list.add(quest.copy());b.put("quests",list);NbtRecord.write(Quests.path(server.overworld(),village),b);}
 private static void camera(net.minecraft.server.MinecraftServer server){var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(server.overworld(),80.5,-59.2,7.5,180,7);}
 private static void shot(Minecraft mc,String suffix)throws Exception{var file=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-markers-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(file);}LogUtils.getLogger().info("ASTRA_MARKERS screenshot {}",file);}
 static void tick(Minecraft mc){try{if(failure!=null)throw new IllegalStateException(failure);if(++ticks>3600)throw new IllegalStateException("Marker timeout phase="+phase);if(!ready)return;var server=mc.getSingleplayerServer();if(!positioned){positioned=true;server.execute(()->camera(server));return;}var label=ResidentLabels.label(resident);
  if(phase==0&&label!=null&&label.profession().equals("doctor")&&label.quest()&&label.needs().stream().anyMatch(i->i.is(VillageAstra.BANDAGE.get()))){if(++settle<40)return;shot(mc,"needed");phase=1;settle=0;server.execute(()->{try{var e=SettlementData.get(server).entry(village);LogisticsRoutes.chest(server.overworld(),e,clinic).setItem(0,new ItemStack(VillageAstra.BANDAGE.get(),64));quest.putString("state",Quests.TAKEN);quest.putUUID("owner",server.getPlayerList().getPlayers().get(0).getUUID());board(server);}catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&label!=null&&label.needs().isEmpty()&&!label.quest()){if(++settle<30)return;shot(mc,"satisfied");phase=2;settle=0;server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(server.overworld(),180.5,-59.2,7.5,180,7);SettlementData.get(server).entry(village).settlement().resident(resident).assign(null);});}
  else if(phase==2&&label==null){phase=3;server.execute(()->camera(server));}
  else if(phase==3&&label!=null&&label.profession().equals("unemployed")&&label.needs().isEmpty()&&!label.quest()){if(++settle<30)return;shot(mc,"returned");LogUtils.getLogger().info("ASTRA_MARKERS VERIFIED profession=true realNeed=true quest=true cleared=true rangeRefresh=true reassignment=true");mc.stop();phase=4;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_MARKERS FAILED",ex);mc.stop();}}
}
