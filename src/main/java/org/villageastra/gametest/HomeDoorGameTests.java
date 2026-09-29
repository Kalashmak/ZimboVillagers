package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** probe-fix-01: both residents of the village-style home (AD-129) walk in from the street through its door and lie down in their own beds. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HomeDoorGameTests {
 @GameTest(template="empty",timeoutTicks=600) public static void bothResidentsOfAHomeReachTheirBedsThroughTheDoor(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,2,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var home=new Settlement.Building(Settlement.childId(s.id(),"building/home-door"),"home",12,0,0);s.addBuilding(home);
  s.addHome(new Settlement.Home(home.id(),1,2,true));
  var base=center.offset(12,0,0);
  // A street of stone under the lot and in front of it, the village-style home (the design residents really live in) on it.
  for(int x=-3;x<=9;x++)for(int z=-6;z<=9;z++){l.setBlock(base.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);for(int y=0;y<=12;y++)l.setBlock(base.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);}
  for(var cell:BuildingPlacement.layout("home",base,0).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var npcs=new ArrayList<ResidentEntity>();var goals=new ArrayList<SleepGoal>();var started=new boolean[2];
  for(int i=0;i<2;i++){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));var at=base.offset(2+2*i,0,-3);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(npc);
   npcs.add(npc);goals.add(new SleepGoal(npc,true,()->18000));}
  h.onEachTick(()->{for(int i=0;i<2;i++){var g=goals.get(i);if(!started[i]){if(g.canUse()){g.start();started[i]=true;}}else if(g.canContinueToUse())g.tick();}});
  h.succeedWhen(()->{
   var where=new StringBuilder();for(var n:npcs)where.append(String.format(Locale.ROOT,"%.2f %.2f %.2f sleeping=%s; ",n.getX()-base.getX(),n.getY()-base.getY(),n.getZ()-base.getZ(),n.isSleeping()));
   h.assertTrue(npcs.stream().allMatch(ResidentEntity::isSleeping),"Both residents lie in their beds (positions from the home's origin): "+where);
   for(var n:npcs)n.discard();SettlementData.get(l.getServer()).remove(s.id());
  });
 }
}
