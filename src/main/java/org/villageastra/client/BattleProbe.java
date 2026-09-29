package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-065: a battle in the real client — a soldier of the player's army marching on a neighbour walks up to its guard and strikes, the guard takes his sword
 *  from the guard house and fights back, nobody's hand moves them; the fallen soldier leaves the army with no one and it withdraws. Then the neighbour is
 *  conquered (MULTI-006) and its management passes to the player once. Fixture: normal difficulty, day, a flat stone field between the two. */
final class BattleProbe {
 private static int phase,ticks;private static volatile String failure,progress="";
 private static volatile UUID soldierId,guardId,neighbourId;private static volatile boolean ready,soldierFought,guardFought,soldierFell;
 private static volatile float guardFull=-1,soldierFull=-1,guardLeast=-1,soldierLeast=-1;private static volatile String state="",reason="",transfer="";
 static boolean enabled(){return Boolean.getBoolean("villageastra.battleSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-battle-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_BATTLE screenshot {}",path);}
 private static volatile UUID armyId,homeId;
 /** The player's settlement: the first one before the fixture adds the neighbour, then found by its id. */
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return homeId!=null?SettlementData.get(s).entry(homeId):SettlementData.get(s).entries().iterator().next();}
 private static void fixture(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{
  var l=server.overworld();var e=entry(server);var settlement=e.settlement();var p=server.getPlayerList().getPlayers().get(0);var c=e.center();homeId=settlement.id();
  server.setDifficulty(Difficulty.NORMAL,true);l.setDayTime(6000);settlement.appointPlayerMayor(p.getUUID());
  // A flat stone field east of the hall, where the neighbour's guard house stands.
  for(int x=14;x<=58;x++)for(int z=-10;z<=10;z++){l.setBlock(c.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);for(int y=1;y<=4;y++)l.setBlock(c.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
  var neighbour=new Settlement(UUID.randomUUID());neighbourId=neighbour.id();var home=c.offset(64,0,0);
  neighbour.addBuilding(new Settlement.Building(Settlement.childId(neighbour.id(),"building/town_hall"),"town_hall",0,0,0));
  var post=new Settlement.Building(Settlement.childId(neighbour.id(),"building/guard_house"),"guard_house",-24,0,-4);neighbour.addBuilding(post);
  neighbour.addHome(new Settlement.Home(Settlement.childId(neighbour.id(),"home"),2,2,true));
  var target=new SettlementData.Entry(neighbour,e.dimension(),home);SettlementData.get(server).add(target);
  var chestPos=LogisticsRoutes.position(target,post);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  var chest=LogisticsRoutes.chest(l,target,post);if(chest==null)throw new IllegalStateException("Guard house chest missing");chest.setItem(0,new ItemStack(Items.IRON_SWORD));
  // The neighbour has its own mayor: otherwise its only adult, the guard, would be taken into the hall.
  var elder=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);neighbour.admit(elder,neighbour.homes().iterator().next().id());neighbour.appointNpcMayor();
  var guard=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);neighbour.admit(guard,neighbour.homes().iterator().next().id());guard.trainMilitary();neighbour.assign(guard.id(),Profession.GUARD,post.id());
  var guardNpc=VillageAstra.RESIDENT.get().create(l);guardNpc.bind(neighbour.id(),neighbour.resident(guard.id()));guardNpc.moveTo(c.getX()+38.5,c.getY()+1,c.getZ()+.5,90,0);l.addFreshEntity(guardNpc);guardId=guard.id();
  // The player's own soldier, already marching on the neighbour.
  var barracks=new Settlement.Building(Settlement.childId(settlement.id(),"building/barracks-battle"),"barracks",10,0,-6);settlement.addBuilding(barracks);
  var quarters=new Settlement.Home(Settlement.childId(settlement.id(),"home/barracks-battle"),1,1,true);settlement.addHome(quarters);
  var soldier=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);settlement.admit(soldier,quarters.id());soldier.trainMilitary();settlement.assign(soldier.id(),Profession.SOLDIER,barracks.id());
  var soldierNpc=VillageAstra.RESIDENT.get().create(l);soldierNpc.bind(settlement.id(),settlement.resident(soldier.id()));soldierNpc.moveTo(c.getX()+26.5,c.getY()+1,c.getZ()+.5,-90,0);l.addFreshEntity(soldierNpc);soldierId=soldier.id();
  var army=new CompoundTag();army.putInt("schema",1);armyId=UUID.randomUUID();army.putUUID("id",armyId);army.putUUID("attacker",settlement.id());army.putUUID("target",neighbour.id());
  army.putString("dimension",e.dimension());army.put("stamps",new CompoundTag());army.put("supply",new CompoundTag());
  var soldiers=new ListTag();soldiers.add(NbtUtils.createUUID(soldier.id()));army.put("soldiers",soldiers);army.putString("state",Sieges.MARCHING);Sieges.save(server,army);
  SettlementData.get(server).setDirty();guardFull=guardNpc.getHealth();soldierFull=soldierNpc.getHealth();
  p.teleportTo(l,c.getX()+32.5,c.getY()+6,c.getZ()-9.5,0,30);
  LogUtils.getLogger().info("ASTRA_BATTLE fixture normal difficulty, day, soldier at {} marching on the neighbour at {}, its guard at {}",soldierNpc.blockPosition().toShortString(),home.toShortString(),guardNpc.blockPosition().toShortString());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 /** Health, work and records sampled on the server thread. */
 private static void sample(net.minecraft.server.MinecraftServer server){server.execute(()->{
  var l=server.overworld();var e=entry(server);
  if(l.getEntity(soldierId) instanceof ResidentEntity s){if(s.workStatus().equals("soldier_fighting"))soldierFought=true;soldierLeast=soldierLeast<0?s.getHealth():Math.min(soldierLeast,s.getHealth());}
  if(l.getEntity(guardId) instanceof ResidentEntity g){if(g.workStatus().equals("guard_fighting"))guardFought=true;guardLeast=guardLeast<0?g.getHealth():Math.min(guardLeast,g.getHealth());}
  var r=e.settlement().resident(soldierId);soldierFell=r!=null&&!r.alive();
  var army=Sieges.army(server,armyId);if(army!=null){state=army.getString("state");reason=army.getString("reason");}
  String seen="";
  if(l.getEntity(soldierId) instanceof ResidentEntity s&&l.getEntity(guardId) instanceof ResidentEntity g){var field=Battle.armyOf(l,soldierId);
   seen=" status="+s.workStatus()+"/"+g.workStatus()+" at="+s.blockPosition().toShortString()+"/"+g.blockPosition().toShortString()+" foe="+(Battle.foe(l,field,s)!=null)+" invader="+(Battle.invader(l,SettlementData.get(server).entry(neighbourId),g,GuardGoal.SIGHT)!=null)+" inArmy="+(field!=null);
   var ne=SettlementData.get(server).entry(neighbourId);var gr=ne.settlement().resident(guardId);var post=ne.settlement().workplace(guardId);
   var chest=post==null?null:LogisticsRoutes.chest(l,ne,post);
   seen+=" guard="+(gr==null?"none":gr.life()+"/"+gr.profession())+" post="+(post==null?"none":post.type())+" sameVillage="+neighbourId.equals(g.settlementId())+" defender="+Battle.defender(ne,guardId)
    +" chest="+(chest==null?"none":chest.getItem(0).toString())+" record="+GuardGoal.inspect(l,guardId);}
  progress="soldierFought="+soldierFought+" guardFought="+guardFought+" guard="+guardFull+"->"+guardLeast+" soldier least="+soldierLeast+" fell="+soldierFell+" army="+state+"/"+reason+seen;
 });}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>8000)throw new IllegalStateException("Battle timeout phase="+phase+" "+progress);
  var server=mc.getSingleplayerServer();
  if(phase==0&&ticks>80){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%10==0){sample(server);
   if(ticks%400==0)LogUtils.getLogger().info("ASTRA_BATTLE progress {}",progress);
   // Both sides have struck: the frame shows the fight.
   if(soldierFought&&guardFought&&guardLeast<guardFull&&soldierLeast>=0&&soldierLeast<soldierFull){capture(mc,"fight");phase=2;ticks=0;}
   else if(ticks>3000)throw new IllegalStateException("The two never fought: "+progress);}
  else if(phase==2&&ticks%10==0){sample(server);
   if(soldierFell&&state.equals(Sieges.WITHDRAWN)){phase=3;ticks=0;}
   else if(ticks>4000)throw new IllegalStateException("The soldier did not fall or the army did not withdraw: "+progress);}
  else if(phase==3&&ticks==20){
   // MULTI-006: the neighbour is conquered once; the player's settlement takes over its management.
   server.execute(()->{var l=server.overworld();var e=entry(server);var data=SettlementData.get(server);var target=data.entry(neighbourId);var p=server.getPlayerList().getPlayers().get(0);
    long now=data.clock().ticks();String first=Annexation.conquer(server,target,e.settlement().id(),now),again=Annexation.conquer(server,target,e.settlement().id(),now+20);
    var owner=Annexation.owner(server,neighbourId);
    transfer="first="+(first.isEmpty()?"ok":first)+" again="+again+" owner="+(e.settlement().id().equals(owner))+" mayor="+(p.getUUID().equals(target.settlement().governance().playerMayor()));});}
  else if(phase==3&&ticks>60){
   if(transfer.isEmpty())return;
   LogUtils.getLogger().info("ASTRA_BATTLE VERIFIED soldier fought={} guard fought={} guard health {}->{} soldier least {} soldier fell={} army={} reason={} conquest {}; reload=false",
    soldierFought,guardFought,guardFull,guardLeast,soldierLeast,soldierFell,state,reason,transfer);
   mc.stop();phase=4;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_BATTLE FAILED",ex);mc.stop();}}
}
