package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.server.SettlementData;

/** Keep paid work intact while the mayor builds the food chain needed to sustain it. */
public final class FoodConstruction {
 private FoodConstruction(){}
 public static String rescueDesign(ServerLevel l,SettlementData.Entry e){
  if(MayorPlanner.foodShortage(l,e)&&ResearchGate.designRefusal(l,e,"farm").isEmpty())return "farm";
  if(MayorPlanner.processingShortage(l,e)&&ResearchGate.designRefusal(l,e,"restaurant").isEmpty())return "restaurant";
  return "";
 }
 public static boolean maySuspend(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();var design=rescueDesign(l,e);if(s.governance().playerMayor()!=null||!HallUpgradeGoal.pending(l,s.id())||design.isEmpty())return false;
  var state=HallUpgradeGoal.headerView(l,s.id());
  return BuildingOrders.isBuilding(state)&&!state.getString("design").equals(design)&&!state.getBoolean("funded")&&state.getInt("index")==0
      &&!state.getBoolean("repair")&&!state.getBoolean("relocate")&&!state.getBoolean("upgrade")
      &&!state.getBoolean("foodRescue");
 }
 public static BuildingOrders.Survey survey(ServerLevel l,SettlementData.Entry e,BlockPos site){
  return survey(l,e,"farm",site);
 }
 public static BuildingOrders.Survey survey(ServerLevel l,SettlementData.Entry e,String design,BlockPos site){
  if(maySuspend(l,e)){
   if(!design.equals(rescueDesign(l,e)))return new BuildingOrders.Survey(new net.minecraft.nbt.CompoundTag(),java.util.Set.of(site),"changed_food_priority");
   var state=HallUpgradeGoal.headerView(l,e.settlement().id());
   var waiting=GrowthPlots.box(state.getString("design"),BlockPos.of(state.getLong("origin")),state.getInt("rotation"));
   if(!GrowthPlots.box(design,site,0).separated(waiting))return new BuildingOrders.Survey(new net.minecraft.nbt.CompoundTag(),java.util.Set.of(site),"waiting_site");
  }
  return BuildingOrders.foodSurvey(l,e,design,0,site);
 }
 public static String approve(ServerLevel l,SettlementData.Entry e,BlockPos site){
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  if(!maySuspend(l,e))return "busy";
  var expected=HallUpgradeGoal.inspect(l,e.settlement().id());var survey=survey(l,e,rescueDesign(l,e),site);
  if(!survey.ok())return survey.reason();
  return HallUpgradeGoal.suspendForFood(l,e,expected,survey.state())?"":"changed_project";
 }
 public static boolean resume(ServerLevel l,SettlementData.Entry e){
  if(!HallUpgradeGoal.resumeAfterFood(l,e))return false;
  SettlementData.get(l.getServer()).setDirty();return true;
 }
}
