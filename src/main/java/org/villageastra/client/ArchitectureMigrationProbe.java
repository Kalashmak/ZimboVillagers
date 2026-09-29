package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
import org.villageastra.persistence.NbtRecord;
/** Real saved-world migration, followed by a separate client process with -PreloadSmoke. */
final class ArchitectureMigrationProbe {
 private static int phase,ticks,initial;private static UUID village,building;private static volatile boolean ready,migrated;private static volatile String failure;private static BlockPos core;
 static boolean enabled(){return Boolean.getBoolean("villageastra.architectureMigrationSmoke");}
 private static boolean reload(){return Boolean.getBoolean("villageastra.reloadSmoke");}
 private static java.nio.file.Path path(MinecraftServer s){return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/ad119-probe.bin");}
 private static SettlementData.Entry entry(MinecraftServer s){return SettlementData.get(s).entry(village);}
 private static Settlement.Building building(MinecraftServer s){return entry(s).settlement().buildings().stream().filter(b->b.id().equals(building)).findFirst().orElseThrow();}
 private static int count(MinecraftServer s){var e=entry(s);var b=building(s);int total=0;for(var p:BuildingBlueprints.layout("school@4",BlockPos.ZERO).keySet())if(s.overworld().getBlockEntity(BuildingPlacement.at(e,b,p.getX(),p.getY(),p.getZ())) instanceof Container c)total+=c.countItem(Items.DIAMOND);return total;}
 private static void camera(MinecraftServer s){var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);p.teleportTo(s.overworld(),e.center().getX()+5.5,e.center().getY()+2.5,e.center().getZ()+5.5,0,15);}
 private static void setup(MinecraftServer s){try{
  var l=s.overworld();if(reload()){var t=NbtRecord.read(path(s));village=t.getUUID("village");building=t.getUUID("building");initial=t.getInt("stock");var e=entry(s);var b=building(s);l.getChunkAt(e.center());
   if(e.settlement().legacyArchitecture().contains(building)||ArchitectureMigration.recover(l,b)||BuildingTiers.level(l,e,b)!=4||count(s)!=initial)throw new IllegalStateException("Reload changed revision, grade or withdrawn stock");migrated=true;
  }else{var domain=new Settlement(UUID.randomUUID());village=domain.id();var b=new Settlement.Building(UUID.randomUUID(),"school",0,0,0,0,4);building=b.id();domain.addBuilding(b);domain.markLegacyArchitecture(building);var e=new SettlementData.Entry(domain,l.dimension().location().toString(),new BlockPos(80,-60,60));SettlementData.get(s).add(e);
   for(var cell:ArchitectureMigration.legacyLayout("school@4").entrySet()){var p=BuildingPlacement.at(e,b,cell.getKey().getX(),cell.getKey().getY(),cell.getKey().getZ());l.setBlock(p,cell.getValue(),2);if(l.getBlockEntity(p) instanceof Container c)c.setItem(0,new ItemStack(Items.DIAMOND,3));}
   initial=count(s);if(initial<=0)throw new IllegalStateException("No inventory fixture");var hold=new CompoundTag();hold.putUUID("id",UUID.randomUUID());hold.putUUID("project",UUID.randomUUID());hold.putString("kind","building");hold.putString("design","school");hold.putLong("origin",e.center().asLong());hold.put("ops",new ListTag());hold.put("cost",new CompoundTag());hold.put("cargo",new ListTag());hold.putBoolean("complete",false);HallUpgradeGoal.enqueue(l,e,hold);
  }
  var p=LevelArchitecture.core("school");core=BuildingPlacement.at(entry(s),building(s),p.getX(),p.getY(),p.getZ());camera(s);ready=true;
 }catch(Exception ex){failure=ex.toString();}}
 private static void verifyAndSave(MinecraftServer s){try{var e=entry(s);var b=building(s);if(e.settlement().legacyArchitecture().contains(building))return;
  if(BuildingTiers.level(s.overworld(),e,b)!=4||count(s)!=initial)throw new IllegalStateException("Migration lost working level or inventory");
  // A later withdrawal must stay withdrawn after a real server restart; a completed journal must never re-credit it.
  for(var p:BuildingBlueprints.layout("school@4",BlockPos.ZERO).keySet())if(s.overworld().getBlockEntity(BuildingPlacement.at(e,b,p.getX(),p.getY(),p.getZ())) instanceof Container c&&c.countItem(Items.DIAMOND)>0){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(Items.DIAMOND)){c.removeItem(i,1);c.setChanged();break;}break;}
  initial--;var t=new CompoundTag();t.putUUID("village",village);t.putUUID("building",building);t.putInt("stock",initial);s.saveEverything(false,true,true);NbtRecord.write(path(s),t);migrated=true;
 }catch(Exception ex){failure=ex.toString();}}
 private static void shot(Minecraft mc,String suffix)throws Exception{var file=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-migration-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(file);}LogUtils.getLogger().info("ASTRA_MIGRATION_PROBE screenshot {}",file);}
 static void tick(Minecraft mc){try{if(failure!=null)throw new IllegalStateException(failure);if(++ticks>3000)throw new IllegalStateException("Migration timeout phase="+phase);var s=mc.getSingleplayerServer();
  if(phase==0&&ticks>40){phase=1;ticks=0;s.execute(()->setup(s));}
  else if(phase==1&&ready&&ticks>60){if(reload()){shot(mc,"reload");LogUtils.getLogger().info("ASTRA_MIGRATION_PROBE VERIFIED level=4 inventory=true journal=true reload=true");mc.stop();phase=4;}else{shot(mc,"before");phase=2;ticks=0;s.execute(()->HallUpgradeGoal.drop(s.overworld(),village));}}
  else if(phase==2&&ticks%20==0){phase=3;s.execute(()->verifyAndSave(s));}
  else if(phase==3&&migrated&&ticks>80&&Cores.grade(mc.level.getBlockState(core))==4){shot(mc,"after");LogUtils.getLogger().info("ASTRA_MIGRATION_PROBE VERIFIED level=4 inventory=true journal=true reload=false");mc.stop();phase=4;}
  else if(phase==3&&!migrated&&ticks%20==0)s.execute(()->verifyAndSave(s));
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_MIGRATION_PROBE FAILED",ex);mc.stop();}}
}
