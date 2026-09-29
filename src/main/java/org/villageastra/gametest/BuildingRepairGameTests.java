package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-059: a blown-up home is noticed, queued for the builders as a repair of that very building, and is a home again once put back. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BuildingRepairGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void blownUpHomeIsRepairedByTheBuilders(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var home=new Settlement.Building(Settlement.childId(s.id(),"building/home-repair"),"home",14,0,0);s.addBuilding(home);
  s.addHome(new Settlement.Home(home.id(),1,2,true));
  var base=center.offset(14,0,0);
  for(int x=-1;x<9;x++)for(int z=-1;z<9;z++)for(int y=-3;y<0;y++)l.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
  for(var cell:BuildingBlueprints.layout("home",base).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());
  var upgrades=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+s.id()+".bin");
  try{
   h.assertTrue(BuildingRepairs.damage(l,e,home).isEmpty(),"An intact home has nothing to repair");
   h.assertTrue(BuildingRepairs.check(l.getServer(),e).isEmpty()&&!HallUpgradeGoal.pending(l,s.id()),"Nothing is queued for intact buildings");
   // The blast: a piece of wall, part of the roof and one bed are gone.
   var lost=List.of(base.offset(0,2,2),base.offset(0,3,2),base.offset(3,4,3),base.offset(2,1,3),base.offset(2,1,4));
   for(var pos:lost)l.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
   HousingMonitor.inspect(l.getServer(),SettlementData.get(l.getServer()),e);
   h.assertTrue(s.homes().stream().noneMatch(x->x.id().equals(home.id())&&x.usable()),"The broken home is no longer a home");
   var damage=BuildingRepairs.damage(l,e,home);
   h.assertTrue(damage.containsAll(lost),"Every lost block is noticed: "+damage.size());
   h.assertTrue(BuildingRepairs.check(l.getServer(),e).equals("queued")&&HallUpgradeGoal.pending(l,s.id()),"The repair is queued for the builders");
   var state=HallUpgradeGoal.inspect(l,s.id());
   h.assertTrue(state.getBoolean("repair")&&state.getUUID("building").equals(home.id())&&BuildingOrders.buildingId(state).equals(home.id()),"It is the repair of that very building");
   var placed=new HashSet<BlockPos>();
   for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var step=HallConstructionPlan.step((net.minecraft.nbt.CompoundTag)raw);if(!step.after().isAir()&&!step.after().is(VillageAstra.TIMBER_SCAFFOLD.get()))placed.add(step.pos());}
   h.assertTrue(placed.containsAll(lost),"The project puts back exactly what was lost");
   h.assertTrue(state.getList("ops",Tag.TAG_COMPOUND).size()<60,"An intact rest of the house is not rebuilt: "+state.getList("ops",Tag.TAG_COMPOUND).size()+" operations");
   // The builders finish: every cell holds its last planned block, and the home takes people again without a second registration.
   var last=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
   for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var step=HallConstructionPlan.step((net.minecraft.nbt.CompoundTag)raw);last.put(step.pos(),step.after());}
   for(var cell:last.entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
   int before=s.buildings().size();
   h.assertTrue(BuildingOrders.complete(l,e,state),"The repair completes against the real blocks");
   h.assertTrue(s.buildings().size()==before,"The repaired building is not registered twice");
   h.assertTrue(s.homes().stream().anyMatch(x->x.id().equals(home.id())&&x.usable()),"The repaired home is a home again");
   h.assertTrue(BuildingRepairs.damage(l,e,home).isEmpty(),"Nothing is missing any more");
  }finally{
   try{java.nio.file.Files.deleteIfExists(upgrades);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
   SettlementData.get(l.getServer()).remove(s.id());
  }
  h.succeed();
 }
}
