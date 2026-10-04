package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Paid local placement blocked by an idle crew member, not a complete building fixture. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BuilderYieldGameTests {
 @GameTest(template="empty",batch="builder_yield",timeoutTicks=600)
 public static void idleHelperLeavesPaidPlacementCell(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));
  var forced=PhysicalFixtureChunks.force(l,center,-2,15,-2,15);
  for(int x=-2;x<=15;x++)for(int z=-2;z<=15;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,4,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var learned=BookResearch.inspect(l,e);var done=new ListTag();ResearchCatalog.NODES.keySet().forEach(id->done.add(StringTag.valueOf(id)));learned.put("legacyDone",done);BookResearch.store(l,e,learned);ResearchKnobs.forget(s.id());
  var target=center.offset(5,1,5);var core=VillageAstra.CORES.get("forester").get();var item=VillageAstra.CORE_ITEMS.get("forester").get();var op=new CompoundTag();op.putLong("pos",target.asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(core.defaultBlockState()));op.putString("item",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());var ops=new ListTag();ops.add(op);
  var members=new ArrayList<ResidentEntity>();
  for(int i=0;i<2;i++){var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),r);npc.moveTo(target.getX()+(i==0?-1.5:.5),target.getY(),target.getZ()+.5,0,0);npc.onlyGoals(g->false,5,new HallUpgradeGoal(npc,true));members.add(npc);}
  var id=UUID.randomUUID();var job=new CompoundTag();job.putInt("schema",2);job.putString("kind","building");job.putUUID("id",id);job.putUUID("project",id);job.putUUID("worker",members.get(0).getUUID());job.putString("design","forester");job.putLong("origin",center.asLong());job.putLong("hatch",center.asLong());job.putBoolean("noHatch",true);job.putBoolean("funded",true);job.put("ops",ops);var cargo=new ListTag();cargo.add(new ItemStack(item).save(new CompoundTag()));job.put("cargo",cargo);HallUpgradeGoal.enqueue(l,e,job);members.forEach(l::addFreshEntity);
  boolean[] ended={false};Runnable cleanup=()->{ended[0]=true;PhysicalFixtureChunks.release(l,forced);members.forEach(ResidentEntity::discard);HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());ResearchKnobs.forget(s.id());};
  h.onEachTick(()->{if(ended[0]||!l.getBlockState(target).is(core))return;h.assertTrue(!members.get(1).getBoundingBox().intersects(core.defaultBlockState().getCollisionShape(l,target).bounds().move(target)),"Idle helper physically left the placement cell");h.assertTrue(HallUpgradeGoal.inspect(l,s.id()).getList("cargo",Tag.TAG_COMPOUND).stream().allMatch(t->ItemStack.of((CompoundTag)t).isEmpty()),"Exactly one paid core consumed");cleanup.run();h.succeed();});
  h.runAtTickTime(550,()->{if(ended[0])return;String why=members.stream().map(n->n.workStatus()+"/"+n.position()).toList().toString();cleanup.run();throw new GameTestAssertException("Idle crew still blocks paid placement: "+why);});
 }
}
