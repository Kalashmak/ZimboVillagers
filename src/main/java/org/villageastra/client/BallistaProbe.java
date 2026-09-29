package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.Difficulty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Observes normal Walls.tick: level V is quiet, VI launches a real piercing bolt and damages a distant target. */
final class BallistaProbe {
 private static int ticks,phase,frameWait;private static volatile boolean busy,done;private static volatile String failure;private static UUID village;private static Zombie target;private static BlockPos center;private static long since;private static boolean bolt,spawned;private static float full;
 static boolean enabled(){return Boolean.getBoolean("villageastra.ballistaSmoke");}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);
  if(mc.getMainRenderTarget().width<100||mc.getMainRenderTarget().height<100){if(++frameWait>200)throw new IllegalStateException("Client window stayed minimized");long window=mc.getWindow().getWindow();org.lwjgl.glfw.GLFW.glfwRestoreWindow(window);org.lwjgl.glfw.GLFW.glfwShowWindow(window);org.lwjgl.glfw.GLFW.glfwSetWindowSize(window,854,480);return;}
  if(++ticks>2400)throw new IllegalStateException("Ballista timeout phase="+phase+" bolt="+bolt);
  if(done){var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-ballista.png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_BALLISTA screenshot {}",path);LogUtils.getLogger().info("ASTRA_BALLISTA VERIFIED naturalTicks=true vQuiet=true viBolt=true pierce=3 damage=true range=40");mc.stop();return;}
  if(ticks%5!=0||busy)return;busy=true;mc.getSingleplayerServer().execute(()->{try{step(mc.getSingleplayerServer().overworld());}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_BALLISTA FAILED",ex);mc.stop();}}
 private static void research(ServerLevel l,SettlementData.Entry e,int n){var r=BookResearch.inspect(l,e);var ids=new ListTag();for(int i=1;i<=n;i++)ids.add(StringTag.valueOf("defense."+i));r.put("legacyDone",ids);BookResearch.store(l,e,r);}
 private static void step(ServerLevel l){var data=SettlementData.get(l.getServer());
  if(phase==0){center=data.entries().iterator().next().center().offset(220,0,0);var s=new Settlement(UUID.randomUUID());village=s.id();var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);data.add(e);
   l.getServer().setDifficulty(Difficulty.NORMAL,true);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());l.setDayTime(18000);
   for(int x=-28;x<=55;x++)for(int z=-28;z<=28;z++){var at=center.offset(x,0,z);l.setBlock(at,Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=22;y++)l.setBlock(at.above(y),Blocks.AIR.defaultBlockState(),2);}
   var tower=new Settlement.Building(UUID.randomUUID(),Walls.TOWER,0,0,0);s.addBuilding(tower);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",-12,0,0);s.addBuilding(hall);
   for(var b:List.of(tower,hall))BuildingPlacement.layout(e,b,b.type()).forEach((p,state)->l.setBlock(p,state,3));
   var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());r.trainMilitary();s.assign(r.id(),Profession.ARCHER_GUARD,tower.id());
   var archer=VillageAstra.RESIDENT.get().create(l);archer.bind(village,r);archer.setNoAi(true);archer.moveTo(center.getX()+2.5,center.getY()+8,center.getZ()+3.5,0,0);l.addFreshEntity(archer);
   research(l,e,5);if(!Boolean.getBoolean("villageastra.ballistaStandalone")){String reason=Walls.order(l,e,Walls.Shape.SQUARE,22);require(reason.isEmpty(),"Wall registry fixture: "+reason);}
   var from=Ballistas.mount(e,tower);target=EntityType.ZOMBIE.create(l);target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);target.moveTo(from.x+40,center.getY()+1,from.z,90,0);full=target.getHealth();
   var p=l.getServer().getPlayerList().getPlayers().get(0);s.appointPlayerMayor(p.getUUID());p.setGameMode(GameType.SPECTATOR);p.teleportTo(l,center.getX()+20.5,center.getY()+20,center.getZ()-32,0,24);since=l.getGameTime();phase=1;return;
  }
  // The player has just moved to this arena; block access alone does not make the target chunk entity-ticking.
  if(!spawned){if(!l.isPositionEntityTicking(target.blockPosition()))return;require(l.addFreshEntity(target),"Target added after its chunk is ready");spawned=true;since=l.getGameTime();return;}
  require(l.getEntity(target.getUUID())==target,"Target is loaded");
  var bolts=l.getEntitiesOfClass(Arrow.class,new AABB(center).inflate(70),a->a.getPierceLevel()==Ballistas.PIERCE&&a.getBaseDamage()==Ballistas.DAMAGE);bolt|=!bolts.isEmpty();
  if(phase==1){require(!bolt&&target.getHealth()==full,"No ballista at Defence V");if(l.getGameTime()-since<220)return;research(l,data.entry(village),6);since=l.getGameTime();phase=2;return;}
  if(bolt&&target.getHealth()<=full-8){require(target.distanceToSqr(center.getCenter())>=35*35,"Hit at long range");LogUtils.getLogger().info("ASTRA_BALLISTA impact health={} elapsed={}",target.getHealth(),l.getGameTime()-since);done=true;}
 }
}
