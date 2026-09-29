package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Housing integrity/assignments after every ordered block transition and save/load snapshots; not a navigation/JVM restart test. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HousingUpgradeGameTests {
 @GameTest(template="empty",batch="housing_upgrade_small",timeoutTicks=600) public static void smallHomeKeepsResidentsWhileUpgrading(GameTestHelper h){audit(h,"home",0);}
 @GameTest(template="empty",batch="housing_upgrade_small_turned",timeoutTicks=600) public static void turnedSmallHomeKeepsResidentsWhileUpgrading(GameTestHelper h){audit(h,"home",1);}
 @GameTest(template="empty",batch="housing_upgrade_large",timeoutTicks=600) public static void largeHomeKeepsResidentsWhileUpgrading(GameTestHelper h){audit(h,"home_2",0);}
 @GameTest(template="empty",batch="housing_upgrade_large_turned",timeoutTicks=600) public static void turnedLargeHomeKeepsResidentsWhileUpgrading(GameTestHelper h){audit(h,"home_2",1);}
 private static Settlement.Home home(Settlement s,UUID id){return s.homes().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();}
 private static void audit(GameTestHelper h,String type,int turn){
  var l=h.getLevel();var data=SettlementData.get(l.getServer());var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,4,2)));data.add(e);
  var id=UUID.randomUUID();var b=new Settlement.Building(id,type,17,0,4,turn,1);s.addBuilding(b);s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0));
  var origin=BuildingPlacement.origin(e,b);var size=BuildingPlacement.size(type,turn);
  for(int x=-3;x<=size[0]+3;x++)for(int z=-3;z<=size[1]+3;z++){l.setBlock(origin.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=28;y++)l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  BuildingPlacement.layout(e,b,type).forEach((p,st)->l.setBlock(p,st,2));s.addHome(new Settlement.Home(id,BuildingOrders.homeLevel(type),HousingLadder.capacity(type,1),true));
  for(int n=0;n<home(s,id).capacity();n++)s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1),id);
  var people=List.copyOf(s.residents());var research=BookResearch.inspect(l,e);var known=new ListTag();ResearchCatalog.NODES.keySet().forEach(k->known.add(StringTag.valueOf(k)));research.put("legacyDone",known);BookResearch.store(l,e,research);
  try{for(int old=1;old<BuildingTiers.max(type);old++){
   var current=s.buildings().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();int capacity=home(s,id).capacity();HousingMonitor.inspect(l.getServer(),data,e);
   h.assertTrue(home(s,id).usable(),type+" fixture usable at "+old);
   var survey=BuildingTiers.survey(l,e,current);h.assertTrue(survey.ok(),type+" survey "+old+": "+survey.reason());HallUpgradeGoal.store(l,s.id(),survey.state());int stepNumber=0;
   for(var raw:survey.state().getList("ops",Tag.TAG_COMPOUND)){
    var step=HallConstructionPlan.step((CompoundTag)raw);l.setBlock(step.pos(),step.after(),3);stepNumber++;HousingMonitor.inspect(l.getServer(),data,e);
    String at=type+" "+old+"->"+(old+1)+" turn="+turn+" op="+stepNumber+" cell="+step.pos().subtract(origin);
    h.assertTrue(home(s,id).usable()&&home(s,id).capacity()==capacity,"Old housing retained: "+at);
    for(var r:people)h.assertTrue(id.equals(r.home())&&SleepGoal.bed(l,e,r)!=null,"Resident keeps home and real bed: "+at);
    int count=survey.state().getList("ops",Tag.TAG_COMPOUND).size();
    if(stepNumber==1||stepNumber==count/2||stepNumber==count){
     var loaded=SettlementData.load(data.save(new CompoundTag())).entry(s.id());
     h.assertTrue(loaded!=null&&home(loaded.settlement(),id).usable()&&home(loaded.settlement(),id).capacity()==capacity,"Save/load retains the old housing: "+at);
     for(var r:people){var restored=loaded.settlement().resident(r.id());h.assertTrue(restored!=null&&id.equals(restored.home())&&SleepGoal.bed(l,loaded,restored)!=null,"Save/load keeps the resident and real bed: "+at);}
    }
   }
   h.assertTrue(BuildingOrders.complete(l,e,survey.state()),type+" upgrade completes");HallUpgradeGoal.drop(l,s.id());HousingMonitor.inspect(l.getServer(),data,e);
   h.assertTrue(home(s,id).usable()&&home(s,id).capacity()==HousingLadder.capacity(type,old+1),type+" completed capacity at "+(old+1)+": "+home(s,id).capacity());
  }}finally{HallUpgradeGoal.drop(l,s.id());data.remove(s.id());ResearchKnobs.forget(s.id());BuildingLevels.forgetBest(s.id());}h.succeed();
 }
}
