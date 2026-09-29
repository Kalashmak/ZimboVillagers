package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Real chunk unload/reload while the village centre keeps ticking. Only the initial mob positions are a fixture. */
final class RaidUnloadProbe {
 private static final BlockPos REMOTE=new BlockPos(1024,-60,1024);
 private static final TicketType<UUID> HOLD=TicketType.create("astra_raid_unload_probe",UUID::compareTo);
 private static final UUID TICKET=UUID.randomUUID();
 private static final Map<UUID,Float> MOBS=new LinkedHashMap<>();
 private static final List<ChunkPos> CHUNKS=new ArrayList<>();
 private static int phase,ticks;private static long since;private static UUID village;private static int wave;
 private static volatile boolean busy,done;private static volatile String failure,capture;
 private RaidUnloadProbe(){}
 static boolean enabled(){return Boolean.getBoolean("villageastra.raidUnloadSmoke");}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Raid unload timeout phase="+phase);
  if(capture!=null){var suffix=capture;capture=null;var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-raid-unload-"+suffix+".png");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}LogUtils.getLogger().info("ASTRA_RAID_UNLOAD screenshot {}",path);}
  if(done){LogUtils.getLogger().info("ASTRA_RAID_UNLOAD VERIFIED spawned={} centreActive=true unloaded=true resumed=true identities=true withdrew=true retiredAbsent=true",MOBS.size());mc.stop();return;}
  if(ticks%20!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_RAID_UNLOAD FAILED",ex);mc.stop();}}
 private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
 private static void camera(ServerPlayer p,ServerLevel l,BlockPos at){p.teleportTo(l,at.getX()+4.5,at.getY()+10,at.getZ()-12.5,0,35);}
 private static boolean missing(ServerLevel l){return MOBS.keySet().stream().allMatch(id->l.getEntity(id)==null)&&CHUNKS.stream().noneMatch(cp->l.hasChunkAt(cp.getWorldPosition()));}
 private static void release(ServerLevel l){for(var cp:CHUNKS)l.getChunkSource().removeRegionTicket(HOLD,cp,2,TICKET);}
 private static void step(ServerLevel l){var s=l.getServer();var p=s.getPlayerList().getPlayers().get(0);long now=l.getGameTime();
  if(phase==0){var e=SettlementData.get(s).entries().iterator().next();village=e.settlement().id();s.setDifficulty(Difficulty.NORMAL,true);l.setDayTime(18000);
   l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,s);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);
   for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc)npc.setNoAi(true);
   require(Raids.start(l,e,SettlementData.get(s).clock().ticks(),Raids.Kind.MONSTERS).isEmpty(),"Could not start real wave");
   for(var mob:Raids.raiders(l,village)){mob.setNoAi(true);mob.setInvulnerable(true);MOBS.put(mob.getUUID(),mob.getHealth());}require(!MOBS.isEmpty(),"Empty fixture wave");wave=Raids.record(l,village).getInt("waves");
   for(int x=63;x<=65;x++)for(int z=63;z<=65;z++){var cp=new ChunkPos(x,z);CHUNKS.add(cp);l.getChunkSource().addRegionTicket(HOLD,cp,2,TICKET);l.getChunk(x,z);}camera(p,l,REMOTE);phase=1;since=now;
  }else if(phase==1&&CHUNKS.stream().allMatch(cp->TouchLoad.ticking(l,cp.getWorldPosition()))){int i=0;for(var id:MOBS.keySet()){var mob=(Mob)l.getEntity(id);require(mob!=null,"Lost raider before fixture relocation");mob.moveTo(REMOTE.getX()+i++*2+.5,REMOTE.getY(),REMOTE.getZ()+.5,0,0);}phase=2;since=now;
  }else if(phase==2&&now-since>100){require(MOBS.keySet().stream().allMatch(id->l.getEntity(id)!=null),"Fixture mobs not visible");var e=SettlementData.get(s).entry(village);release(l);camera(p,l,e.center());phase=3;since=now;
  }else if(phase==3&&missing(l)){var e=SettlementData.get(s).entry(village);require(TouchLoad.ticking(l,e.center()),"Centre must stay active");require(Raids.update(l,e,SettlementData.get(s).clock().ticks()).isEmpty()&&Raids.active(l,village),"Unloading falsely defeated the wave");phase=4;since=now;LogUtils.getLogger().info("ASTRA_RAID_UNLOAD first unload: centre active, roster retained");
  }else if(phase==4&&now-since>100){require(Raids.active(l,village),"Raid ended while all mobs unloaded");camera(p,l,REMOTE);phase=5;since=now;
  }else if(phase==5&&MOBS.keySet().stream().allMatch(id->l.getEntity(id) instanceof Mob)){
   for(var pair:MOBS.entrySet())require(((Mob)l.getEntity(pair.getKey())).getHealth()==pair.getValue(),"Reload changed raider health");require(Raids.active(l,village)&&Raids.record(l,village).getInt("waves")==wave,"Reload spawned a different wave");capture="resumed";phase=6;since=now;
  }else if(phase==6&&now-since>100){camera(p,l,SettlementData.get(s).entry(village).center());phase=7;since=now;
  }else if(phase==7&&missing(l)){var e=SettlementData.get(s).entry(village);var a=Raids.record(l,village).getCompound("active");require(Raids.update(l,e,a.getLong("started")+Raids.GIVE_UP+1).equals("withdrew"),"Unloaded raiders must withdraw, not win");require(!Raids.active(l,village),"Retreat left active wave");camera(p,l,REMOTE);phase=8;since=now;
  }else if(phase==8&&now-since>100&&TouchLoad.ticking(l,REMOTE)){require(MOBS.keySet().stream().allMatch(id->l.getEntity(id)==null),"Retired raider resurrected from old chunk");require(Raids.record(l,village).getCompound("last").getString("outcome").equals("withdrew"),"Retreat outcome changed");capture="retired";done=true;}
  if(now-since>1800)throw new IllegalStateException("Stage stalled "+phase+" loaded="+CHUNKS.stream().filter(cp->l.hasChunkAt(cp.getWorldPosition())).count()+" bodies="+MOBS.keySet().stream().filter(id->l.getEntity(id)!=null).count());
  LogUtils.getLogger().info("ASTRA_RAID_UNLOAD phase={} elapsed={} bodies={}",phase,now-since,MOBS.keySet().stream().filter(id->l.getEntity(id)!=null).count());
 }
}
