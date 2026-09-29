package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Natural server ticks queue the project; a live builder collects the bill and rebuilds II -> IV -> V. No skipped operations. */
final class TowerStagesProbe {
 private static int ticks,phase,target=4;private static volatile boolean busy,done;private static volatile String failure;
 private static UUID village,towerId,project;private static BlockPos center,start;private static ResidentEntity builder;
 private static Container stock,towerChest;private static long since,fundedAt;private static boolean fundingDiagnostic;private static double walked;private static Item paid;private static int amount;
 static boolean enabled(){return Boolean.getBoolean("villageastra.towerStagesSmoke");}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>36000)throw new IllegalStateException("Tower timeout phase="+phase+" target="+target);
  if(done){var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-tower-stages.png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_TOWER_STAGES screenshot {}",path);LogUtils.getLogger().info("ASTRA_TOWER_STAGES VERIFIED naturalTicks=true paid=true walked=true tiers=2,4,5 chest=true noFreeUpgrade=true");mc.stop();return;}
  if(ticks%10!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_TOWER_STAGES FAILED",ex);mc.stop();}}
 private static void research(ServerLevel l,SettlementData.Entry e,int n){var r=BookResearch.inspect(l,e);var ids=new ListTag();for(int i=1;i<=n;i++)ids.add(StringTag.valueOf("defense."+i));for(int i=1;i<=6;i++)ids.add(StringTag.valueOf("construction."+i));r.put("legacyDone",ids);BookResearch.store(l,e,r);ResearchKnobs.forget(e.settlement().id());}
 private static Settlement.Building tower(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.id().equals(towerId)).findFirst().orElseThrow();}
 private static void fund(CompoundTag cost){stock.clearContent();int slot=0;
  for(var key:new TreeSet<>(cost.getAllKeys())){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));int left=cost.getInt(key);while(left>0){int n=Math.min(left,item.getMaxStackSize());require(slot<stock.getContainerSize()-1,"Funding fixture capacity");stock.setItem(slot++,new ItemStack(item,n));left-=n;}}
  stock.setItem(stock.getContainerSize()-1,new ItemStack(Items.BREAD,64));stock.setChanged();
 }
 private static void step(ServerLevel l){var data=SettlementData.get(l.getServer());
  if(phase==0){center=data.entries().iterator().next().center().offset(230,0,0);var s=new Settlement(UUID.randomUUID());village=s.id();var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);data.add(e);
   l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   for(int x=-20;x<=38;x++)for(int z=-12;z<=32;z++){var at=center.offset(x,0,z);l.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(at,Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=22;y++)l.setBlock(at.above(y),Blocks.AIR.defaultBlockState(),2);}
   var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);towerId=UUID.randomUUID();var tower=new Settlement.Building(towerId,Walls.TOWER,20,0,4,0,2);s.addBuilding(tower);
   var home=new Settlement.Building(UUID.randomUUID(),"home",-14,0,14);s.addBuilding(home);s.addHome(new Settlement.Home(home.id(),1,2,true));
   for(var b:List.of(hall,tower,home))BuildingPlacement.layout(e,b,b==tower?"wall_tower@2":b.type()).forEach((p,state)->l.setBlock(p,state,3));
   stock=LogisticsRoutes.chest(l,e,hall);require(stock!=null,"Hall inventory");stock.clearContent();stock.setItem(stock.getContainerSize()-1,new ItemStack(Items.BREAD,64));
   towerChest=(Container)l.getBlockEntity(BuildingPlacement.at(e,tower,1,1,4));towerChest.setItem(0,new ItemStack(Items.DIAMOND,7));
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());s.assign(r.id(),Profession.BUILDER,hall.id());builder=VillageAstra.RESIDENT.get().create(l);builder.bind(village,r);start=center.offset(10,1,-3);builder.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5,0,0);
   builder.onlyGoals(g->g instanceof FloatGoal||g instanceof ReturnCargoGoal||g instanceof ResidentDoorGoal||g instanceof DoorwayGoal,5,new HallUpgradeGoal(builder));l.addFreshEntity(builder);
   var p=l.getServer().getPlayerList().getPlayers().get(0);s.appointPlayerMayor(p.getUUID());p.setGameMode(GameType.SPECTATOR);p.teleportTo(l,center.getX()+30,center.getY()+16,center.getZ()-16,35,24);research(l,e,4);data.setDirty();phase=1;return;
  }
  var e=data.entry(village);var b=tower(e);var state=HallUpgradeGoal.exists(l,village)?HallUpgradeGoal.inspect(l,village):new CompoundTag();
  require(l.getEntity(builder.getUUID())==builder,"Builder remains loaded");require(towerChest==l.getBlockEntity(BuildingPlacement.at(e,b,1,1,4))&&towerChest.getItem(0).getCount()==7,"Tower inventory survives every observed step");
  walked=Math.max(walked,Math.sqrt(builder.distanceToSqr(start.getCenter())));
  if(phase==1){if(state.isEmpty()||state.getBoolean("complete")||state.getInt("upgradeLevel")!=target)return;project=state.getUUID("id");since=l.getGameTime();phase=2;return;}
  require(state.hasUUID("id")&&state.getUUID("id").equals(project),"Queued project is stable");
  if(phase==2){require(!state.getBoolean("funded")&&b.level()==(target==4?2:4),"Unfunded upgrade cannot grant a tier");if(l.getGameTime()-since<100)return;
   paid=target==4?Items.COBBLESTONE:Items.STONE_BRICKS;amount=state.getCompound("cost").getInt(BuiltInRegistries.ITEM.getKey(paid).toString());require(amount>0,"Real material bill");fund(state.getCompound("cost"));fundedAt=l.getGameTime();fundingDiagnostic=false;LogUtils.getLogger().info("ASTRA_TOWER_STAGES funding tier={} cost={} slots={}",target,state.getCompound("cost"),stock.getContainerSize());phase=3;return;}
  if(!fundingDiagnostic&&l.getGameTime()-fundedAt>400&&state.getInt("withdrawals")==0){fundingDiagnostic=true;var missing=new ArrayList<String>();for(var key:state.getCompound("cost").getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));missing.add(key+" need="+state.getCompound("cost").getInt(key)+" have="+stock.countItem(item));}LogUtils.getLogger().info("ASTRA_TOWER_STAGES waiting for physical funding: {} sameStock={} cargo={} goals={} navigationDone={}",missing,stock==l.getBlockEntity(HallSite.stock(e)),state.getList("cargo",Tag.TAG_COMPOUND),builder.runningGoals(),builder.getNavigation().isDone());}
  if(ticks%200==0){var route=builder.getNavigation().getPath();var nodes=new ArrayList<String>();if(route!=null)for(int i=Math.max(0,route.getNextNodeIndex()-1);i<Math.min(route.getNodeCount(),route.getNextNodeIndex()+3);i++)nodes.add(route.getNodePos(i).subtract(center).toShortString());LogUtils.getLogger().info("ASTRA_TOWER_STAGES progress target={} index={}/{} withdrawals={} status={} at={} walk={} route={} nodes={} doorCanUse={}",target,state.getInt("index"),state.getList("ops",Tag.TAG_COMPOUND).size(),state.getInt("withdrawals"),builder.workStatus(),builder.position().subtract(center.getX(),center.getY(),center.getZ()),HallUpgradeGoal.lastWalk,route==null?"none":route.getNextNodeIndex()+"/"+route.getNodeCount()+" target="+route.getTarget().subtract(center),nodes,new ResidentDoorGoal(builder).canUse());}
  if(!state.getBoolean("complete"))return;
  require(b.level()==target&&state.getBoolean("funded")&&state.getInt("withdrawals")>0,"Live builder funds and completes the tier");require(stock.countItem(paid)==0&&walked>3,"Charged material consumed and builder walked");
  LogUtils.getLogger().info("ASTRA_TOWER_STAGES completed tier={} material={} spent={} walked={}",target,paid,amount,walked);
  if(target==4){target=5;research(l,e,5);phase=1;return;}
  org.villageastra.server.ProbeWarp.end("tower stages complete");done=true;
 }
}
