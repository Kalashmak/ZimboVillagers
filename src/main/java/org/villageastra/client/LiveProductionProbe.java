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
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Real workshop production during a paid renovation, interrupted in mid-job by a complete JVM restart. */
final class LiveProductionProbe {
 private static int ticks,phase;private static volatile boolean busy,done;private static volatile String failure;
 private static UUID village,smithyId;private static BlockPos center,start;private static ResidentEntity smith,builder;
 private static Container stock;private static CompoundTag saved;private static boolean producedDuringUpgrade;private static double walked;
 static boolean enabled(){return Boolean.getBoolean("villageastra.liveProductionSmoke");}
 static boolean reloading(){return Boolean.getBoolean("villageastra.liveProductionReloadSmoke");}
 private static boolean smelting(){return Boolean.getBoolean("villageastra.liveProductionSmeltSmoke")||saved!=null&&saved.getBoolean("smelting");}
 private static Item result(){return smelting()?Items.IRON_PICKAXE:Items.STONE_PICKAXE;}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private static java.nio.file.Path record(ServerLevel l){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-live-production-probe.bin");}
 private static Settlement.Building building(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.id().equals(smithyId)).findFirst().orElseThrow();}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>48000)throw new IllegalStateException("Production timeout phase="+phase);
  if(done){var file=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-live-production-"+(reloading()?"reload":"source")+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(file);}LogUtils.getLogger().info("ASTRA_LIVE_PRODUCTION screenshot {}",file);LogUtils.getLogger().info("ASTRA_LIVE_PRODUCTION VERIFIED reload={} paid=true physicalWorker=true stockIdentity=true",reloading());mc.stop();return;}
  if(ticks%10!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_LIVE_PRODUCTION FAILED",ex);mc.stop();}}
 private static ResidentEntity worker(ServerLevel l,SettlementData.Entry e,Settlement.Home home,Profession profession,UUID workplace,BlockPos at){
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);e.settlement().admit(r,home.id());e.settlement().assign(r.id(),profession,workplace);
  var mob=VillageAstra.RESIDENT.get().create(l);mob.bind(e.settlement().id(),r);mob.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);goals(mob,profession);l.addFreshEntity(mob);return mob;
 }
 private static void goals(ResidentEntity mob,Profession p){mob.onlyGoals(g->g instanceof FloatGoal||g instanceof ReturnCargoGoal||g instanceof ResidentDoorGoal||g instanceof DoorwayGoal,5,p==Profession.BUILDER?new HallUpgradeGoal(mob):new WorkshopGoal(mob));}
 private static void observe(ServerLevel l,SettlementData.Entry e){var p=l.getServer().getPlayerList().getPlayers().get(0);e.settlement().appointPlayerMayor(p.getUUID());p.setGameMode(GameType.SPECTATOR);p.teleportTo(l,e.center().getX()+40,e.center().getY()+18,e.center().getZ()-14,28,30);}
 private static int operations(CompoundTag t){int n=0;for(var raw:t.getList("ops",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getBoolean("done"))n++;return n;}
 private static void inputs(){var items=new ArrayList<ItemStack>();items.add(new ItemStack(smelting()?Items.RAW_IRON:Items.COBBLESTONE,3));items.add(new ItemStack(Items.STICK,2));if(smelting())items.add(new ItemStack(Items.COAL,4));for(var item:items){int slot=0;while(slot<stock.getContainerSize()&&!stock.getItem(slot).isEmpty())slot++;require(slot<stock.getContainerSize(),"Free input slot");stock.setItem(slot,item);}stock.setChanged();}
 private static void step(ServerLevel l){var data=SettlementData.get(l.getServer());
  if(phase==0&&reloading()){
   if(saved==null){saved=NbtRecord.read(record(l));village=saved.getUUID("village");smithyId=saved.getUUID("smithy");}
   var e=data.entry(village);require(e!=null,"Persisted settlement");center=e.center();observe(l,e);
   if(!(l.getEntity(saved.getUUID("smith")) instanceof ResidentEntity loadedSmith)||!(l.getEntity(saved.getUUID("builder")) instanceof ResidentEntity loadedBuilder))return;
   smith=loadedSmith;builder=loadedBuilder;goals(smith,Profession.BLACKSMITH);goals(builder,Profession.BUILDER);stock=LogisticsRoutes.chest(l,e,building(e));require(stock!=null,"Persisted smithy inventory");
   var job=Workshops.inspect(l,smithyId);require(job.getUUID("id").equals(saved.getUUID("job")),"The paid job survived restart");require(smelting()?stock.countItem(Items.RAW_IRON)==saved.getInt("rawRemaining")&&stock.countItem(Items.COAL)==saved.getInt("fuelRemaining")&&stock.countItem(Items.STICK)==2:stock.countItem(Items.COBBLESTONE)==0&&stock.countItem(Items.STICK)==0,"Paid ingredients were not restored");phase=3;return;
  }
  if(phase==0){center=data.entries().iterator().next().center().offset(230,0,0);var s=new Settlement(UUID.randomUUID());village=s.id();var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);data.add(e);
   l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   for(int x=-8;x<=78;x++)for(int z=-16;z<=35;z++){var p=center.offset(x,0,z);l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=27;y++)l.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);}
   var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0,0,3);smithyId=UUID.randomUUID();var b=new Settlement.Building(smithyId,"smithy",26,0,4,0,2);var mine=new Settlement.Building(UUID.randomUUID(),"mine",56,0,4);s.addBuilding(hall);s.addBuilding(b);s.addBuilding(mine);
   for(var at:List.of(hall,b,mine))BuildingPlacement.layout(e,at,at==hall?"town_hall_3":at==b?"smithy@2":"mine").forEach((p,st)->l.setBlock(p,st,3));
   var home=new Settlement.Home(UUID.randomUUID(),1,6,true);s.addHome(home);var miner=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(miner,home.id());s.assign(miner.id(),Profession.MINER,mine.id());
   var mineState=MineWork.read(l,mine);mineState.remove("tool");mineState.putString("stage","tool");if(smelting())mineState.put("requiredToolState",NbtUtils.writeBlockState(Blocks.GOLD_ORE.defaultBlockState()));MineWork.write(l,mine,mineState);
   builder=worker(l,e,home,Profession.BUILDER,hall.id(),center.offset(9,1,-3));start=BuildingPlacement.origin(e,b).offset(4,1,-12);smith=worker(l,e,home,Profession.BLACKSMITH,b.id(),start);
   stock=LogisticsRoutes.chest(l,e,b);require(stock!=null,"Smithy inventory");stock.clearContent();
   var r=BookResearch.inspect(l,e);var known=new ListTag();for(int n=2;n<=3;n++)for(var node:BuildingTiers.research("smithy",n))known.add(StringTag.valueOf(node));r.put("legacyDone",known);BookResearch.store(l,e,r);observe(l,e);
   require(BuildingTiers.order(l,e,b).isEmpty(),"Ordinary smithy upgrade order");var project=HallUpgradeGoal.inspect(l,village);var hallStock=LogisticsRoutes.chest(l,e,hall);hallStock.clearContent();int slot=0;
   for(var key:new TreeSet<>(project.getCompound("cost").getAllKeys())){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));int left=project.getCompound("cost").getInt(key);while(left>0){int count=Math.min(left,item.getMaxStackSize());require(slot<hallStock.getContainerSize()-1,"Building funding capacity");hallStock.setItem(slot++,new ItemStack(item,count));left-=count;}}hallStock.setItem(hallStock.getContainerSize()-1,new ItemStack(Items.BREAD,64));
   saved=new CompoundTag();saved.putBoolean("smelting",smelting());saved.putUUID("village",village);saved.putUUID("smithy",smithyId);saved.putUUID("smith",smith.getUUID());saved.putUUID("builder",builder.getUUID());saved.putUUID("project",project.getUUID("id"));data.setDirty();phase=1;return;
  }
  var e=data.entry(village);var b=building(e);var project=HallUpgradeGoal.inspect(l,village);var job=Workshops.inspect(l,smithyId);
  require(project.getUUID("id").equals(saved.getUUID("project")),"Same construction project");require(stock==LogisticsRoutes.chest(l,e,b),"Same physical inventory throughout renovation");
  if(!project.getBoolean("complete"))require(BuildingTiers.level(l,e,b)==2,"No premature grade while renovating");
  if(ticks%200==0)LogUtils.getLogger().info("ASTRA_LIVE_PRODUCTION progress phase={} operations={} complete={} job={} labor={}/{} output={} smith={} builder={}",phase,operations(project),project.getBoolean("complete"),job.getString("stage"),job.getLong("labor"),job.getLong("needLabor"),stock.countItem(result()),smith.workStatus(),builder.workStatus());
  if(phase==1){if(!project.getBoolean("funded")||operations(project)==0)return;require(!project.getBoolean("complete"),"Production starts during renovation");inputs();phase=2;return;}
  if(phase==2){walked=Math.max(walked,Math.sqrt(smith.distanceToSqr(start.getCenter())));
   if(smelting()){if(!job.getString("stage").equals("smelt_wait")||!job.contains("furnace")||!(l.getBlockEntity(BlockPos.of(job.getLong("furnace"))) instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity f)||!f.getItem(0).is(Items.RAW_IRON)||f.saveWithoutMetadata().getShort("BurnTime")==0)return;require(stock.countItem(Items.COAL)<4&&stock.countItem(Items.RAW_IRON)<3&&stock.countItem(Items.STICK)==2,"Real raw iron and coal are inside the lit furnace; crafting sticks are kept");LogUtils.getLogger().info("ASTRA_LIVE_PRODUCTION furnace physicalRaw=true paidCoal=true lit=true");}
   else{if(!job.getString("stage").equals("work")||job.getLong("labor")==0)return;require(stock.countItem(Items.COBBLESTONE)==0&&stock.countItem(Items.STICK)==0,"Ingredients paid once before producing anything");}
   require(!project.getBoolean("complete")&&walked>6&&stock.countItem(result())==0,"Real smith walks and works while builder renovates");
   saved.putInt("rawRemaining",stock.countItem(Items.RAW_IRON));saved.putInt("fuelRemaining",stock.countItem(Items.COAL));saved.putUUID("job",job.getUUID("id"));saved.putInt("operations",operations(project));NbtRecord.write(record(l),saved);done=true;return;
  }
  if(phase==3){if(stock.countItem(result())==0)return;require(stock.countItem(result())==1&&!project.getBoolean("complete"),"Exactly one output from saved job during renovation");producedDuringUpgrade=true;phase=4;return;}
  if(phase==4){require(stock.countItem(result())==1,"No duplicate output after restart");if(!project.getBoolean("complete"))return;
   require(producedDuringUpgrade&&BuildingTiers.level(l,e,b)==3&&operations(project)>saved.getInt("operations"),"Builder physically completes upgrade after production resumes");inputs();phase=5;return;}
  if(phase==5&&stock.countItem(result())==2){require(stock.countItem(smelting()?Items.RAW_IRON:Items.COBBLESTONE)==0&&stock.countItem(Items.STICK)==0,"Second real recipe after upgrade is paid");LogUtils.getLogger().info("ASTRA_LIVE_PRODUCTION completed upgraded=3 outputs=2 noReplay=true");if(smelting())LogUtils.getLogger().info("ASTRA_LIVE_PRODUCTION ironChain raw=6 picks=2 vanillaFurnace=true");done=true;}
 }
}
