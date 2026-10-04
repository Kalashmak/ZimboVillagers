package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HarvestReturnGameTests {
 @GameTest(template="empty",batch="harvest_return") public static void quarryRestoresRemovedFootholdWithPaidCargo(GameTestHelper h){check(h,false);}
 @GameTest(template="empty",batch="harvest_return") public static void repairedFootholdCannotDuplicateCargoAfterCrash(GameTestHelper h){check(h,true);}
 private static void check(GameTestHelper h,boolean crash){
  var t=ResearchV2Town.town(h,null);var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{
   var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);t.l.addFreshEntity(npc);
   var base=t.e.center().offset(40,8,0);for(int x=-3;x<=7;x++)for(int z=-3;z<=3;z++)for(int y=-4;y<=4;y++)t.l.setBlock(base.offset(x,y,z),y==-4?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
   for(int x=0;x<=4;x++)t.l.setBlock(base.offset(x,0,0),Blocks.STONE.defaultBlockState(),2);
   var at=base.offset(2,0,0);var back=base.above();npc.moveTo(base.getX()+4.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);
   var original=npc.routeTo(back,0,32);h.assertTrue(HarvestAccess.reversible(original),"Intact bridge offers a reversible route");h.assertTrue(!HarvestAccess.survivesExtraction(original,at),"A route relying on the target block is not safe to harvest");
   var id=UUID.randomUUID();var pick=new ItemStack(Items.STONE_PICKAXE);var loot=WorldJournal.harvest(t.l,id,at,Blocks.STONE.defaultBlockState(),pick);h.assertTrue(loot!=null&&loot.size()==1&&loot.get(0).is(Items.COBBLESTONE),"Real stone yielded a real cobblestone");
   h.assertTrue(!HarvestAccess.reversible(npc.routeTo(back,0,32)),"Harvest removed the only reversible support");
   var trip=new CompoundTag();trip.putUUID("id",id);trip.putBoolean("quarry",true);trip.putString("stage","carry");trip.putLong("target",at.asLong());var cargo=new ListTag();cargo.add(loot.get(0).save(new CompoundTag()));trip.put("cargo",cargo);
   if(crash){h.assertTrue(WorldJournal.place(t.l,HarvestAccessRepair.operation(trip),at,Blocks.AIR.defaultBlockState(),Blocks.COBBLESTONE.defaultBlockState()),"Restore committed before cargo checkpoint");h.assertTrue(NaturalSupplyGoal.cargo(t.l,trip).isEmpty(),"Death/reassignment recovery cannot drop the stone already placed");h.assertTrue(HarvestAccessRepair.reconcile(t.l,t.e,npc,trip),"Reload reconciles payment");}
   else h.assertTrue(HarvestAccessRepair.restore(t.l,t.e,npc,trip),"Nearby blocked worker replaces the support from its own harvest");
   h.assertTrue(t.l.getBlockState(at).is(Blocks.COBBLESTONE)&&trip.getList("cargo",Tag.TAG_COMPOUND).isEmpty(),"One carried stone becomes one world block");
   h.assertTrue(!HarvestAccessRepair.reconcile(t.l,t.e,npc,trip)&&NaturalSupplyGoal.cargo(t.l,trip).isEmpty(),"Repeated recovery cannot recreate cargo");
   h.assertTrue(HarvestAccess.reversible(npc.routeTo(back,0,32)),"The same worker can physically return along the restored route");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
}
