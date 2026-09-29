package org.villageastra.gametest;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-131: the starter village with its forester's hut raised to a level, a wood of wild trees behind the hut (south of its lot, out of every
 *  lot's ground) and a driver that takes the forester's goal call by call, standing him where each stage wants him. Not a test itself. */
final class ForestFixture {
 private static final int CLEAR=40;
 final ServerLevel l;final Settlement s;final SettlementData.Entry e;final ResidentEntity forester;final Path work;final BlockPos origin;final UUID hutId;
 /** What the wood's ground held before (outside the test's own structure): put back when the test is done, so no later test finds it. */
 private final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> before=new LinkedHashMap<>();
 private ForestFixture(ServerLevel l,Settlement s,SettlementData.Entry e,ResidentEntity forester,BlockPos origin,UUID hut){this.l=l;this.s=s;this.e=e;this.forester=forester;this.origin=origin;this.hutId=hut;work=MineWork.path(l,hut);}
 Settlement.Building hut(){return s.buildings().stream().filter(b->b.id().equals(hutId)).findFirst().orElseThrow();}
 static ForestFixture create(GameTestHelper h,int level){return create(h,level,0);}
 static ForestFixture create(GameTestHelper h,int level,int raise){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3+raise,2));if(raise>0)for(var p:StarterVillage.layout(origin).keySet())l.setBlock(p,Blocks.AIR.defaultBlockState(),2);var s=StarterVillage.create(l,origin);var e=SettlementData.get(l.getServer()).entry(s.id());
  var hut=s.buildings().stream().filter(b->b.type().equals(ForesterHut.TYPE)).findFirst().orElseThrow();
  var resident=s.residents().stream().filter(r->r.profession()==Profession.FORESTER).findFirst().orElseThrow();var forester=(ResidentEntity)l.getEntity(resident.id());
  // The resident spawned into a chunk whose entities the world does not show yet (a village of the same id stood on this spot before): the
  // goal is driven call by call here, so a forester of the same identity that the world does not hold does the same work.
  if(forester==null){forester=org.villageastra.VillageAstra.RESIDENT.get().create(l);forester.bind(s.id(),resident);var at=ForesterHut.door(e,hut).north(2);
   forester.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);if(!forester.getUUID().equals(resident.id()))throw new IllegalStateException("The forester has another identity");}
  var t=new ForestFixture(l,s,e,forester,origin,hut.id());t.level(level);ForestWork.forget(hut.id());t.forgetPlantings();
  try{Files.deleteIfExists(t.work);Files.deleteIfExists(ForestryMachines.path(l,hut.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  // The wood's ground: grass behind the hut, clear above.
  // Clear to well over a crown: another test on this spot (a trail's tunnel, a bridge) may have left a roof over the wood, and the search
  // reads a column by its top.
  for(int x=-8;x<=24;x++)for(int z=-2;z<=14;z++){var g=t.wood(x,z).below();for(int y=0;y<=CLEAR;y++)t.before.put(g.above(y),l.getBlockState(g.above(y)));}
  for(int x=-8;x<=24;x++)for(int z=0;z<=14;z++){var g=t.wood(x,z).below();l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=CLEAR;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),2);}
  return t;
 }
 /** Raises the hut's kept level and sets the equipment and core of every level up to it. */
 void level(int level){
  for(int i=2;i<=level;i++)s.raiseBuildingLevel(hutId,i);var hut=hut();
  for(int i=2;i<=level;i++)for(var placed:BuildingLevels.equipment(ForesterHut.TYPE,i))l.setBlock(BuildingPlacement.at(e,hut,placed.local().getX(),placed.local().getY(),placed.local().getZ()),BuildingPlacement.state(placed.state(),hut.rotation()),3);
  BuildingLevels.forgetBest(s.id());
 }
 /** A cell of the wood at ground level +1 (a foot's cell): x across from 8 blocks west of the door, z from 25 blocks behind it. */
 BlockPos wood(int x,int z){var door=ForesterHut.door(e,hut());return new BlockPos(door.getX()-4+x,origin.getY()+1,door.getZ()+25+z);}
 BlockPos door(){return ForesterHut.door(e,hut());}
 BlockPos chest(){return LogisticsRoutes.position(e,hut());}
 Container chestBlock(){return (Container)l.getBlockEntity(chest());}
 BlockPos hall(){return LogisticsRoutes.position(e,Workshops.hall(e));}
 CompoundTag record(){return Files.exists(work)?NbtRecord.read(work):new CompoundTag();}
 void write(CompoundTag t){NbtRecord.write(work,t);}
 static int count(ListTag items,Item item){return items.stream().map(x->ItemStack.of((CompoundTag)x)).filter(i->item==null||i.is(item)).mapToInt(ItemStack::getCount).sum();}
 /** Every wild leaf round these logs (within 3) — what a crown holds before it is felled. */
 List<BlockPos> leavesAround(List<BlockPos> logs){var out=new ArrayList<BlockPos>();var seen=new HashSet<BlockPos>();for(var p:logs)for(var q:BlockPos.betweenClosed(p.offset(-3,-3,-3),p.offset(3,3,3)))if(seen.add(q.immutable())&&l.getBlockState(q).getBlock() instanceof LeavesBlock)out.add(q.immutable());return out;}
 /** Calls the goal until the record satisfies {@code until} (or the calls run out); the forester is set down where each stage works. */
 CompoundTag drive(ResourceWorkGoal goal,int calls,Predicate<CompoundTag> until){
  var standing=wood(-6,0);
  for(int call=0;call<calls;call++){var r=record();var stage=r.getString("stage");
   BlockPos at=switch(stage){case "tool","upgrade_tool"->hall().east();case "dig","replant"->BlockPos.of(r.getLong("target")).north();
    case "sapling"->r.contains("plantSource")?BlockPos.of(r.getLong("plantSource")).east():chest().east();case "deliver"->chest().east();default->standing;};
   forester.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);forester.setOnGround(true);goal.tick();var after=record();if(until.test(after))return after;}
  return record();
 }
 /** A test before on this spot (a village of the same id, the same wood) may have noted plantings here: they are not this test's. */
 void forgetPlantings(){ForestPlantings.get(l.getServer()).forgetInside(l,origin.offset(-64,-16,-64),origin.offset(96,32,112));}
 void done(){forgetPlantings();
  try{Files.deleteIfExists(work);Files.deleteIfExists(ForestryMachines.path(l,hutId));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  ForestWork.forget(hutId);
  for(var r:s.residents()){var npc=l.getEntity(r.id());if(npc!=null)npc.discard();}
  SettlementData.get(l.getServer()).remove(s.id());
  // Top down, so nothing falls or pops while the ground goes back.
  var cells=new ArrayList<>(before.entrySet());cells.sort(Comparator.comparingInt((Map.Entry<BlockPos,net.minecraft.world.level.block.state.BlockState> c)->-c.getKey().getY()));
  for(var c:cells){l.removeBlockEntity(c.getKey());l.setBlock(c.getKey(),c.getValue(),2|16);}
  for(var item:l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(wood(-10,-4),wood(26,16).above(16))))item.discard();
 }
}
