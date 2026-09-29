package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.villageastra.VillageAstra;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Actual survival placement events and ordinary entity ticks, not a direct call to trap damage. */
final class EngineeringTrapProbe {
 private static int ticks;private static volatile boolean setup,done;private static volatile String failure;private static LivingEntity zombie,cow;private static float zombieBefore,cowBefore;
 private EngineeringTrapProbe(){}
 static boolean enabled(){return Boolean.getBoolean("villageastra.engineeringTrapSmoke");}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>2400)throw new IllegalStateException("Engineering trap timeout");
  if(ticks==20)mc.getSingleplayerServer().execute(()->{try{LogUtils.getLogger().info("ASTRA_ENGINEERING_TRAP setup entered");var s=mc.getSingleplayerServer();var l=s.overworld();var e=SettlementData.get(s).entries().iterator().next();var p=s.getPlayerList().getPlayers().get(0);var at=e.center().offset(55,0,20);
   l.setDayTime(18000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,s);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);s.setDifficulty(Difficulty.NORMAL,true);
   p.setGameMode(GameType.SURVIVAL);p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(l,at.getX()+.5,at.getY()+1,at.getZ()-2.5,0,25);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VillageAstra.MOB_TRAP.get(),2));
   var hit=new BlockHitResult(Vec3.atCenterOf(at.below()).add(0,.5,0),Direction.UP,at.below(),false);
   require(l.getBlockState(at).isAir()&&!MobTrapBlock.allowed(l,at),"Fixture must start without Engineering IV on empty ground");
   p.gameMode.useItemOn(p,l,p.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
   require(l.getBlockState(at).isAir()&&p.getMainHandItem().getCount()==2,"Locked trap placement must roll back without spending the item");
   var record=BookResearch.inspect(l,e);var knowledge=record.getList("legacyDone",Tag.TAG_STRING);for(int i=1;i<=4;i++)knowledge.add(StringTag.valueOf("engineering."+i));record.put("legacyDone",knowledge);BookResearch.store(l,e,record);ResearchKnobs.forget(e.settlement().id());
   p.gameMode.useItemOn(p,l,p.getMainHandItem(),InteractionHand.MAIN_HAND,hit);require(l.getBlockState(at).is(VillageAstra.MOB_TRAP.get())&&p.getMainHandItem().getCount()==1,"Unlocked survival placement must spend exactly one trap");
   var other=at.east(3);p.gameMode.useItemOn(p,l,p.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(other.below()).add(0,.5,0),Direction.UP,other.below(),false));require(l.getBlockState(other).is(VillageAstra.MOB_TRAP.get())&&p.getMainHandItem().isEmpty(),"Second real trap for the animal");
   for(var cell:java.util.List.of(at.north(2),other.north(2)))l.setBlock(cell,net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState(),3);
   var z=EntityType.ZOMBIE.create(l);var c=EntityType.COW.create(l);z.goalSelector.removeAllGoals(g->true);z.targetSelector.removeAllGoals(g->true);c.goalSelector.removeAllGoals(g->true);c.targetSelector.removeAllGoals(g->true);z.setPersistenceRequired();c.setPersistenceRequired();z.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);c.moveTo(other.getX()+.5,other.getY(),other.getZ()+.5,0,0);l.addFreshEntity(z);l.addFreshEntity(c);zombie=z;cow=c;zombieBefore=z.getHealth();cowBefore=c.getHealth();
   p.teleportTo(l,at.getX()+2,at.getY()+6,at.getZ()-8,0,35);setup=true;LogUtils.getLogger().info("ASTRA_ENGINEERING_TRAP placed traps, ordinary physics enabled");
  }catch(Exception ex){failure=ex.toString();}});
  if(ticks%200==0)LogUtils.getLogger().info("ASTRA_ENGINEERING_TRAP progress setup={} health={} cow={}",setup,zombie==null?"absent":zombie.getHealth(),cow==null?"absent":cow.getHealth());
  if(setup&&!done&&ticks%10==0)mc.getSingleplayerServer().execute(()->{try{require(cow.getHealth()==cowBefore,"Trap hurt a peaceful animal");if(zombie.getHealth()<zombieBefore-MobTrapBlock.DAMAGE/2){done=true;LogUtils.getLogger().info("ASTRA_ENGINEERING_TRAP damage zombie={}->{} cow={}->{}",zombieBefore,zombie.getHealth(),cowBefore,cow.getHealth());}}catch(Exception ex){failure=ex.toString();}});
  if(done){var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-engineering-trap.png");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}LogUtils.getLogger().info("ASTRA_ENGINEERING_TRAP screenshot {}",path);LogUtils.getLogger().info("ASTRA_ENGINEERING_TRAP VERIFIED lockedRefund=true survivalPlaced=2 spent=2 monsterDamaged=true animalSafe=true naturalTicks=true");mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_ENGINEERING_TRAP FAILED",ex);mc.stop();}}
}
