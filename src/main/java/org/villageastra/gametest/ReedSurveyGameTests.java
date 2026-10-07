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
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ReedSurveyGameTests {
 @GameTest(template="empty",batch="reed_survey_budget",timeoutTicks=2000)
 public static void nurserySurveyYieldsAndResumesWithoutSpendingSeed(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+73728,100,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-124)>>4;x<=(base.getX()+124)>>4;x++)for(int z=(base.getZ()-124)>>4;z<=(base.getZ()+124)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  // Loaded stone plateau rules out accidental ponds in generated ground below it.
  for(int x=-124;x<=124;x++)for(int z=-124;z<=124;z++)l.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.setNoAi(true);npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5);
  var state=new CompoundTag();state.putUUID("id",UUID.randomUUID());state.putString("stage","carry");var cargo=new ListTag();cargo.add(new ItemStack(Items.SUGAR_CANE).save(new CompoundTag()));state.put("cargo",cargo);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),state);int[] first={0};
  h.startSequence().thenWaitUntil(()->h.assertTrue(TouchLoad.ticking(l,base),"Fixture ready for native body ticks"))
   .thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Seed carrier added"))
   .thenWaitUntil(()->h.assertTrue(npc.tickCount>0&&npc.tickCount%20==0,"At a native planning turn"))
   .thenExecute(()->{
    h.assertTrue(ReedNursery.tick(npc,e,state),"Pending survey retains the carried seed");
    h.assertTrue(!state.getBoolean("nurseryChecked")&&!state.contains("nurseryTarget"),"A full shore search is not exhausted in one body tick");
   }).thenWaitUntil(()->{
    if(state.getInt("nurseryCursor")==0&&npc.tickCount%20==0)ReedNursery.tick(npc,e,state);
    h.assertTrue(state.getInt("nurseryCursor")>0,"Bounded search eventually saves progress despite an exhausted first CPU allowance");
   }).thenExecute(()->first[0]=state.getInt("nurseryCursor")).thenIdle(20)
   .thenWaitUntil(()->{
    var reloaded=NaturalSupplyGoal.inspect(l,npc.getUUID()).copy();ReedNursery.resume(reloaded);
    if(npc.tickCount%20==0)ReedNursery.tick(npc,e,reloaded);
    h.assertTrue(reloaded.getInt("nurseryCursor")>first[0],"Reload continues the next columns instead of starting over");
   }).thenExecute(()->{
    var reloaded=NaturalSupplyGoal.inspect(l,npc.getUUID());
    h.assertTrue(reloaded.getList("cargo",Tag.TAG_COMPOUND).equals(cargo)&&reloaded.getInt("delivered")==0&&ReedNursery.planted(l,s.id()).isEmpty(),"Survey neither consumes nor plants the seed");
    npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);
   }).thenSucceed();
 }
}
