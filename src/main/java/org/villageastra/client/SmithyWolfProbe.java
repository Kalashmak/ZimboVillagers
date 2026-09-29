package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Real kennel source and normal AI for AD-155; no stand-in VillageDogs and no manual delivery ticks. */
final class SmithyWolfProbe {
 private static int ticks,phase;private static volatile boolean busy,done;private static volatile String failure;private static UUID village,smithyId,mineId;private static Wolf wolf;private static BlockPos start;private static double walked;private static boolean loaded;
 static boolean enabled(){return Boolean.getBoolean("villageastra.smithyWolfSmoke");}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>3600)throw new IllegalStateException("Smithy wolf timeout phase="+phase+" loaded="+loaded+" walked="+walked);
  if(done){var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-smithy-wolf.png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_SMITHY_WOLF screenshot {}",path);LogUtils.getLogger().info("ASTRA_SMITHY_WOLF VERIFIED armourInteraction=true halfDamage=true wear=63 realDog=true loaded=true delivered=1 source=0 released=true walked=true");mc.stop();return;}
  if(ticks%20!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_SMITHY_WOLF FAILED",ex);mc.stop();}}
 private static void step(ServerLevel l){var data=SettlementData.get(l.getServer());
  if(phase==0){var original=data.entries().iterator().next();var center=original.center().offset(200,0,0);var s=new Settlement(UUID.randomUUID());village=s.id();var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);data.add(e);
   l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   smithyId=Settlement.childId(village,"smithy");mineId=Settlement.childId(village,"mine");var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,-18);s.addBuilding(hall);
   s.addBuilding(new Settlement.Building(smithyId,"smithy",0,0,0));for(int n=2;n<=5;n++)s.raiseBuildingLevel(smithyId,n);var smithy=building(e,smithyId);var mine=new Settlement.Building(mineId,"mine",24,0,0);s.addBuilding(mine);
   var farm=new Settlement.Building(UUID.randomUUID(),"livestock",0,0,30);s.addBuilding(farm);var kennel=new Settlement.Building(UUID.randomUUID(),VillageWolves.TYPE,18,0,24);s.addBuilding(kennel);s.linkAnnex(kennel.id(),farm.id());
   for(int x=-5;x<=44;x++)for(int z=-5;z<=32;z++){var at=center.offset(x,0,z);l.setBlock(at,Blocks.STONE.defaultBlockState(),3);for(int y=1;y<=7;y++)l.setBlock(at.above(y),Blocks.AIR.defaultBlockState(),3);}
   for(var b:List.of(smithy,mine,kennel))for(var cell:BuildingPlacement.layout(e,b,b==smithy?BuildingTiers.layoutId("smithy",5):b.type()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   BuildingLevels.forgetBest(village);for(var b:List.of(hall,smithy,mine)){l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);LogisticsRoutes.chest(l,e,b).clearContent();}
   var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);var miner=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(miner,home.id());s.assign(miner.id(),Profession.MINER,mineId);var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(village,miner);npc.moveTo(center.getX()+30.5,center.getY()+1,center.getZ()+10.5,0,0);npc.setNoAi(true);l.addFreshEntity(npc);
   var state=MineWork.read(l,mine);state.remove("tool");state.putString("stage","tool");MineWork.write(l,mine,state);
   start=center.offset(42,1,20);require(l.getBlockState(start).isAir()&&l.getBlockState(start.above()).isAir(),"Clear initial dog position");wolf=EntityType.WOLF.create(l);wolf.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5,0,0);l.addFreshEntity(wolf);wolf.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l));require(VillageWolves.enlist(l,e,wolf,kennel),"Real registered dog");
   var p=l.getServer().getPlayerList().getPlayers().get(0);wolf.tame(p);p.setGameMode(GameType.SURVIVAL);p.teleportTo(l,start.getX()+1.5,start.getY(),start.getZ()+.5,90,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VillageAstra.WOLF_ARMOR.get()));p.interactOn(wolf,InteractionHand.MAIN_HAND);
   require(wolf.getPersistentData().getInt(VillageWolves.ARMOUR)==VillageWolves.ARMOUR_HITS&&wolf.getCollarColor()==DyeColor.GRAY&&p.getMainHandItem().isEmpty(),"Actual interaction equips and consumes one armour");float health=wolf.getHealth();require(wolf.hurt(l.damageSources().generic(),4),"Damage accepted");require(Math.abs(wolf.getHealth()-(health-2))<.01&&wolf.getPersistentData().getInt(VillageWolves.ARMOUR)==63,"Damage halved and one armour use spent");
   p.setGameMode(GameType.SPECTATOR);p.teleportTo(l,center.getX()+18.5,center.getY()+22,center.getZ()-16,0,42);LogisticsRoutes.chest(l,e,smithy).setItem(0,new ItemStack(Items.STONE_PICKAXE));phase=1;LogUtils.getLogger().info("ASTRA_SMITHY_WOLF setup wolf={} armour=63 village={}",wolf.getUUID(),village);return;
  }
  var e=data.entry(village);var source=LogisticsRoutes.chest(l,e,building(e,smithyId));var dest=LogisticsRoutes.chest(l,e,building(e,mineId));var trip=SmithyDelivery.inspect(l,smithyId);loaded|=trip.getString("stage").equals("go");walked=Math.max(walked,Math.sqrt(wolf.distanceToSqr(start.getX()+.5,start.getY(),start.getZ()+.5)));
  if(ticks%200==0)LogUtils.getLogger().info("ASTRA_SMITHY_WOLF progress stage={} at={} source={} destination={} walked={}",trip.getString("stage"),wolf.blockPosition(),source.countItem(Items.STONE_PICKAXE),dest.countItem(Items.STONE_PICKAXE),walked);
  if(trip.getString("stage").equals("complete")&&dest.countItem(Items.STONE_PICKAXE)==1){require(loaded&&walked>=8,"Real loaded journey observed");require(source.countItem(Items.STONE_PICKAXE)==0&&VillageWolves.sentTo(wolf)==null,"Source paid once and dog released");wolf.setNoAi(true);if(phase==1){var viewer=l.getServer().getPlayerList().getPlayers().get(0);viewer.teleportTo(l,wolf.getX()-6,wolf.getY()+4,wolf.getZ()-6,-45,25);phase=2;return;}done=true;}
 }
}
