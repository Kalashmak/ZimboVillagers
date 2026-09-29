package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ScaffoldJourneyGameTests {
 @GameTest(template="empty",batch="scaffold_journey",timeoutTicks=5000)
 public static void builderKeepsStairDetourUntilReachingScaffold(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+61440,90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+46)>>4;x++)for(int z=(base.getZ()-12)>>4;z<=(base.getZ()+8)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-2;x<=46;x++)for(int z=-12;z<=8;z++)for(int y=0;y<=8;y++){
   boolean wall=x==-2||x==46||z==-12||z==8||x>=8&&x<=14&&z>=-5;
   boolean stair=x>=2&&x<=7&&z>=-6&&z<=-2;
   l.setBlock(base.offset(x,y,z),y==0||wall&&y<=5||stair&&y==1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
  var column=base.offset(40,1,0);for(int y=0;y<=4;y++)l.setBlock(column.above(y),VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState(),2);
  var target=column.offset(1,3,0);var settlement=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);settlement.addBuilding(hall);
  var home=new Settlement.Home(UUID.randomUUID(),1,2,true);settlement.addHome(home);var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(r,home.id());settlement.assign(r.id(),Profession.BUILDER,hall.id());
  var entry=new SettlementData.Entry(settlement,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(entry);
  var state=new CompoundTag();var id=UUID.randomUUID();state.putInt("schema",2);state.putUUID("id",id);state.putUUID("project",id);state.putString("kind","building");state.putString("design","home");state.putLong("origin",column.below().asLong());state.putLong("hatch",column.asLong());state.putBoolean("noHatch",true);state.putBoolean("funded",true);
  var op=new CompoundTag();op.putLong("pos",target.asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(Blocks.OAK_PLANKS.defaultBlockState()));op.putString("item","minecraft:oak_planks");op.putLong("stand",column.above(2).asLong());op.putInt("standBase",column.getY());var ops=new ListTag();ops.add(op);state.put("ops",ops);
  var cargo=new ListTag();cargo.add(new ItemStack(Items.OAK_PLANKS).save(new CompoundTag()));state.put("cargo",cargo);HallUpgradeGoal.store(l,settlement.id(),state);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(settlement.id(),settlement.resident(r.id()));npc.moveTo(base.getX()+4.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);var goal=new HallUpgradeGoal(npc,true);npc.onlyGoals(g->false,5,goal);
  boolean[] jumped={false};
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Entity chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Builder added"));
  h.onEachTick(()->{
   if(npc.tickCount>5&&!npc.onGround()&&!npc.onClimbable()&&npc.distanceToSqr(column.getCenter())>100){jumped[0]=true;h.assertTrue(npc.getNavigation().getPath()!=null,"Builder discarded the stair detour in mid-air: "+npc.position()+" status="+npc.workStatus());}
   if(!l.getBlockState(target).is(Blocks.OAK_PLANKS))return;
   h.assertTrue(jumped[0],"Approach exercised an actual stair jump");h.assertTrue(l.getBlockState(base.offset(8,2,0)).is(Blocks.STONE),"Obstacle was not removed");h.assertTrue(npc.distanceToSqr(target.getCenter())<25,"Block placed only after physically approaching scaffold");
   npc.discard();SettlementData.get(l.getServer()).remove(settlement.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(4800,()->h.assertTrue(false,"Scaffold journey stalled: "+npc.position()+" "+npc.workStatus()));
 }
}
