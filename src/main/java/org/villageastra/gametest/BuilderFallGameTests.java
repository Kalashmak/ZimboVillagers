package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
/** Merge probes 2026-09-24: a builder fell off the tower mill's scaffold and died. A village's builder takes no fall damage; a farmer does. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BuilderFallGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void aBuilderSurvivesAFallFromTheScaffold(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,2,4));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var farm=new Settlement.Building(Settlement.childId(s.id(),"building/farm"),"farm",12,0,0);s.addBuilding(farm);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var npcs=new ArrayList<org.villageastra.world.ResidentEntity>();
  try{
   var hurt=new HashMap<Profession,Float>();
   for(var trade:List.of(Profession.BUILDER,Profession.FARMER)){
    var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,trade.educationRequired(),null,null,-1);var room=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(room);s.admit(r,room.id());
    s.assign(r.id(),trade,trade==Profession.BUILDER?hall.id():farm.id());
    var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.setNoAi(true);npc.moveTo(center.getX()+.5+npcs.size()*2,center.getY(),center.getZ()+.5,0,0);l.addFreshEntity(npc);npcs.add(npc);
    float before=npc.getHealth();npc.causeFallDamage(20,1,l.damageSources().fall());hurt.put(trade,before-npc.getHealth());}
   h.assertTrue(npcs.get(0).builder()&&hurt.get(Profession.BUILDER)==0,"The builder falls twenty blocks unhurt: "+hurt);
   h.assertTrue(!npcs.get(1).builder()&&hurt.get(Profession.FARMER)>0,"A farmer is hurt by the same fall: "+hurt);
  }finally{for(var n:npcs)n.discard();SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
