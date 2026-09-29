package org.villageastra.client;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Extends the real demand/delivery probe with a level-IV centre and two equipped, trained soldiers. */
final class CaravanEscortProbe {
 private CaravanEscortProbe(){}
 private static final List<UUID> soldiers=new ArrayList<>();
 private static boolean followed;private static int seen;private static volatile boolean returned;
 static boolean enabled(){return Boolean.getBoolean("villageastra.caravanEscortSmoke");}
 static void setup(ServerLevel l,SettlementData.Entry home,Settlement.Building yard,SettlementData.Entry destination){
  var s=home.settlement();int level=CaravanHorseProbe.enabled()?6:CaravanDogProbe.enabled()?5:enabled()?4:2;for(int n=2;n<=level;n++)s.raiseBuildingLevel(yard.id(),n);
  var kept=s.buildings().stream().filter(b->b.id().equals(yard.id())).findFirst().orElseThrow();
  for(var cell:BuildingPlacement.layout(home,kept,BuildingTiers.layoutId("caravan",level)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  BuildingLevels.forgetBest(s.id());
  var other=new Settlement.Building(UUID.randomUUID(),"caravan",-40,0,10);destination.settlement().addBuilding(other);
  destination.settlement().raiseBuildingLevel(other.id(),2);other=destination.settlement().buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
  for(var cell:BuildingPlacement.layout(destination,other,BuildingTiers.layoutId("caravan",2)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  BuildingLevels.forgetBest(destination.settlement().id());CartographyLadder.found(l,s.id(),destination.settlement().id());
  if(!enabled())return;
  var barracks=new Settlement.Building(UUID.randomUUID(),"barracks",-30,0,25);s.addBuilding(barracks);
  for(int i=0;i<2;i++){
   var id=UUID.randomUUID();var hid=UUID.randomUUID();s.addHome(new Settlement.Home(hid,1,4,true));
   var r=new Resident(id,Resident.Life.ADULT,false,null,null,-1);s.admit(r,hid);r.trainMilitary();s.assign(id,Profession.SOLDIER,barracks.id());
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),r);int x=home.center().getX()+20,z=home.center().getZ()-3-i*2;
   npc.moveTo(x+.5,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z+.5,0,0);npc.setNoAi(true);l.addFreshEntity(npc);
   var gear=new CompoundTag();gear.put("weapon",new ItemStack(Items.IRON_SWORD).save(new CompoundTag()));GuardGoal.write(l,id,gear);soldiers.add(id);
  }
 }
 static void observe(ServerLevel l,CompoundTag leader){
  if(!enabled())return;
  var driver=l.getEntity(leader.getUUID("caravaneer"));
  var trips=Caravans.contracts(l.getServer()).stream().filter(t->CaravanEscorts.KIND.equals(t.getString("kind"))&&t.hasUUID("leaderTrip")&&t.getUUID("leaderTrip").equals(leader.getUUID("id"))).toList();
  seen=Math.max(seen,trips.size());
  if(driver!=null&&leader.getDouble("progress")>12&&leader.getString("state").equals(Caravans.TRANSIT)){
   long near=soldiers.stream().map(l::getEntity).filter(n->n!=null&&n.isAlive()&&n.distanceToSqr(driver)<=12*12).count();if(near==2)followed=true;
  }
  returned=seen==2&&followed&&soldiers.stream().noneMatch(id->CaravanEscorts.reserved(l.getServer(),id));
 }
 static boolean finished(){
  if(!enabled())return true;
  if(!returned)return false;
  com.mojang.logging.LogUtils.getLogger().info("ASTRA_CARAVAN_ESCORT VERIFIED recruited=2 followed=true returned=2 reload=false");return true;
 }
}
