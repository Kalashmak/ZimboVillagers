package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmTillingGameTests {
 @GameTest(template="empty",batch="farm_tilling") public static void spreadingGrassDoesNotFreezeFarmer(GameTestHelper h){check(h,false,false);}
 @GameTest(template="empty",batch="farm_tilling") public static void occupiedPlotIsReplannedWithoutOverwritingCrop(GameTestHelper h){check(h,false,true);}
 @GameTest(template="empty",batch="farm_tilling") public static void committedTillingPaysHoeOnceAfterCropAndMoistureChange(GameTestHelper h){check(h,true,true);}
 private static void check(GameTestHelper h,boolean paid,boolean occupied){
  var t=ResearchV2Town.town(h,"farm");var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{
   var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.FARMER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);t.l.addFreshEntity(npc);
   var target=t.e.center().offset(10,0,10);t.l.setBlock(target,Blocks.DIRT.defaultBlockState(),2);t.l.setBlock(target.above(),Blocks.AIR.defaultBlockState(),2);npc.moveTo(target.getX()+.5,target.getY()+1,target.getZ()+.5);npc.setOnGround(true);
   var id=UUID.randomUUID();var state=new CompoundTag();state.putInt("schema",1);state.putInt("width",1);state.putInt("height",3);state.putUUID("operation",id);state.putUUID("worker",npc.getUUID());state.putString("stage","till");state.putString("crop","wheat");state.putLong("target",target.asLong());state.put("before",NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()));state.put("tool",new ItemStack(Items.STONE_HOE).save(new CompoundTag()));
   if(paid){h.assertTrue(WorldJournal.place(t.l,id,target,Blocks.DIRT.defaultBlockState(),Blocks.FARMLAND.defaultBlockState()),"Committed till before checkpoint");t.l.setBlock(target,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),2);}
   else{t.l.setBlock(target,Blocks.GRASS_BLOCK.defaultBlockState(),2);h.assertTrue(!WorldJournal.place(t.l,id,target,Blocks.DIRT.defaultBlockState(),Blocks.FARMLAND.defaultBlockState()),"Old intent conflicts with naturally changed soil");}
   if(occupied)t.l.setBlock(target.above(),Blocks.WHEAT.defaultBlockState(),2);MineWork.write(t.l,t.shop,state);
   var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Farmer reloads pending stroke");goal.tick();
   var after=MineWork.read(t.l,t.shop);h.assertTrue(after.getString("stage").equals("choose")&&!after.getUUID("operation").equals(id),"Changed or completed plot releases the farmer for the next job");
   h.assertTrue(ItemStack.of(after.getCompound("tool")).getDamageValue()==(paid?1:0),"Only a committed stroke damages the hoe, once");
   h.assertTrue(t.l.getBlockState(target).is(paid?Blocks.FARMLAND:Blocks.GRASS_BLOCK)&&(!occupied||t.l.getBlockState(target.above()).is(Blocks.WHEAT)),"Current soil and crop are not overwritten by stale intent");
   if(!paid&&!occupied){after.putString("stage","till");after.putLong("target",target.asLong());after.put("before",NbtUtils.writeBlockState(t.l.getBlockState(target)));after.putInt("labor",0);MineWork.write(t.l,t.shop,after);goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"New survey uses current grass");for(int n=0;n<3;n++)goal.tick();h.assertTrue(t.l.getBlockState(target).is(Blocks.FARMLAND)&&ItemStack.of(MineWork.read(t.l,t.shop).getCompound("tool")).getDamageValue()==1,"Fresh real till succeeds without reusing old intent");}
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
}
