package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Physical level III delivery with the resident's unmodified AI and normal server ticks. */
final class SmithyCourierProbe {
 private static int ticks,phase;private static volatile boolean busy,done;private static volatile String failure;private static UUID village,smithyId,mineId;private static ResidentEntity courier;private static BlockPos start;private static double walked;private static boolean loaded;
 static boolean enabled(){return Boolean.getBoolean("villageastra.smithyCourierSmoke");}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>3600)throw new IllegalStateException("Smithy courier timeout phase="+phase+" loaded="+loaded+" walked="+walked);
  if(done){var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-smithy-courier.png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_SMITHY_COURIER screenshot {}",path);LogUtils.getLogger().info("ASTRA_SMITHY_COURIER VERIFIED naturalTicks=true loaded=true delivered=1 source=0 cargo=0 walked=true stable=true");mc.stop();return;}
  if(ticks%20!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_SMITHY_COURIER FAILED",ex);mc.stop();}}
 private static void step(ServerLevel l){var data=SettlementData.get(l.getServer());
  if(phase==0){var original=data.entries().iterator().next();var center=original.center().offset(200,0,0);var s=new Settlement(UUID.randomUUID());village=s.id();var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);data.add(e);
   l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   smithyId=Settlement.childId(village,"smithy");mineId=Settlement.childId(village,"mine");var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,-18);s.addBuilding(hall);
   s.addBuilding(new Settlement.Building(smithyId,"smithy",0,0,0));for(int n=2;n<=3;n++)s.raiseBuildingLevel(smithyId,n);var smithy=building(e,smithyId);var mine=new Settlement.Building(mineId,"mine",24,0,0);s.addBuilding(mine);
   for(int x=-5;x<=44;x++)for(int z=-5;z<=42;z++){var at=center.offset(x,0,z);l.setBlock(at,Blocks.STONE.defaultBlockState(),3);for(int y=1;y<=7;y++)l.setBlock(at.above(y),Blocks.AIR.defaultBlockState(),3);}
   for(var b:List.of(smithy,mine))for(var cell:BuildingPlacement.layout(e,b,b==smithy?BuildingTiers.layoutId("smithy",3):b.type()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   BuildingLevels.forgetBest(village);for(var b:List.of(hall,smithy,mine)){l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);LogisticsRoutes.chest(l,e,b).clearContent();}
   var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);var house=new Settlement.Building(home.id(),"home_2",0,0,26);s.addBuilding(house);for(var cell:BuildingPlacement.layout(e,house,"home_2").entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   var miner=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(miner,home.id());s.assign(miner.id(),Profession.MINER,mineId);var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(village,miner);npc.moveTo(center.getX()+30.5,center.getY()+1,center.getZ()+10.5,0,0);npc.setNoAi(true);l.addFreshEntity(npc);
   var person=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home.id());s.assign(person.id(),Profession.PORTER,smithyId);courier=VillageAstra.RESIDENT.get().create(l);courier.bind(village,person);
   var state=MineWork.read(l,mine);state.remove("tool");state.putString("stage","tool");MineWork.write(l,mine,state);
   start=center.offset(42,1,20);require(l.getBlockState(start).isAir()&&l.getBlockState(start.above()).isAir(),"Clear initial courier position");courier.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5,0,0);l.addFreshEntity(courier);
   var p=l.getServer().getPlayerList().getPlayers().get(0);s.appointPlayerMayor(p.getUUID());p.setGameMode(GameType.SPECTATOR);p.teleportTo(l,center.getX()+18.5,center.getY()+22,center.getZ()-16,0,42);LogisticsRoutes.chest(l,e,smithy).setItem(0,new ItemStack(Items.STONE_PICKAXE));require(LogisticsRoutes.choose(l,e,smithy,start)!=null,"Initial route absent: level="+BuildingLevels.level(l,e,smithy)+" residents="+s.residents().stream().map(r->r.id()+":"+r.profession()+":"+s.workplace(r.id())).toList()+" wants="+WorkerSupplies.wants(l,e)+" hall="+LogisticsRoutes.chest(l,e,hall));phase=1;LogUtils.getLogger().info("ASTRA_SMITHY_COURIER setup courier={} village={}",courier.getUUID(),village);return;
  }
  var e=data.entry(village);require(e.settlement().residents().stream().anyMatch(r->r.alive()&&r.profession()==Profession.MINER&&building(e,mineId).equals(e.settlement().workplace(r.id()))),"Miner lost assignment");var source=LogisticsRoutes.chest(l,e,building(e,smithyId));var dest=LogisticsRoutes.chest(l,e,building(e,mineId));var trip=PorterWork.inspect(l,courier.getUUID());loaded|=trip.getString("stage").equals("deliver");walked=Math.max(walked,Math.sqrt(courier.distanceToSqr(start.getX()+.5,start.getY(),start.getZ()+.5)));
  if(ticks==200&&trip.isEmpty())throw new IllegalStateException("Lost route: level="+BuildingLevels.level(l,e,building(e,smithyId))+" residents="+e.settlement().residents().stream().map(r->r.id()+":"+r.profession()+":"+r.alive()+":"+e.settlement().workplace(r.id())).toList()+" wants="+WorkerSupplies.wants(l,e)+" mine="+MineWork.read(l,building(e,mineId)));
  if(ticks%200==0)LogUtils.getLogger().info("ASTRA_SMITHY_COURIER progress stage={} at={} source={} destination={} walked={}",trip.getString("stage"),courier.blockPosition()+" "+courier.workStatus()+" "+courier.runningGoals(),source.countItem(Items.STONE_PICKAXE),dest.countItem(Items.STONE_PICKAXE),walked);
  if(phase==2){require(dest.countItem(Items.STONE_PICKAXE)==1&&source.countItem(Items.STONE_PICKAXE)==0&&PorterWork.cargo(l,trip).isEmpty(),"Delivered parcel duplicated or retained");if(++stable>=5)done=true;return;}
  if(trip.getString("stage").equals("complete")&&dest.countItem(Items.STONE_PICKAXE)==1){require(loaded&&walked>=8,"Real loaded journey observed");require(source.countItem(Items.STONE_PICKAXE)==0&&PorterWork.cargo(l,trip).isEmpty(),"Source paid once and cargo released");var viewer=l.getServer().getPlayerList().getPlayers().get(0);viewer.teleportTo(l,courier.getX()-6,courier.getY()+4,courier.getZ()-6,-45,25);phase=2;}
 }
 private static int stable;
}
