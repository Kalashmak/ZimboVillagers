package org.villageastra.gametest;

import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** A repair may temporarily retire its own plaque, whatever timber its village uses. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class TimberSignJournalGameTests {
 private static SettlementData.Entry building(GameTestHelper h,String wood){
  var l=h.getLevel();var s=new Settlement(UUID.randomUUID());var b=new Settlement.Building(UUID.randomUUID(),"home",0,0,0,0,1,wood);s.addBuilding(b);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,4,2)));SettlementData.get(l.getServer()).add(e);
  BuildingPlacement.layout(e,b,"home").forEach((p,state)->l.setBlock(p,state,2));BuildingSigns.refresh(l,e);return e;
 }
 @GameTest(template="empty",batch="timber_sign_journal",timeoutTicks=200) public static void everyVillageTimberPlaqueCanBeTemporarilyRetired(GameTestHelper h){
  var l=h.getLevel();
  for(String wood:List.of("birch","spruce","dark_oak","jungle","acacia","cherry","oak")){
   var e=building(h,wood);
   try{var b=e.settlement().buildings().iterator().next();var p=BuildingSigns.position(e,b);var before=l.getBlockState(p);var id=UUID.randomUUID();
    h.assertTrue(BuildingSigns.target(l,p)!=null,"Registered "+wood+" plaque is genuinely owned");
    h.assertTrue(WorldJournal.place(l,id,p,before,Blocks.AIR.defaultBlockState()),"Real journal retirement accepts owned "+wood+" plaque");
    h.assertTrue(l.getBlockState(p).isAir()&&WorldJournal.inspectCommitted(l,id).getUUID("signBuilding").equals(b.id()),"Only the correct building authorizes retirement");
    h.assertTrue(WorldJournal.place(l,id,p,before,Blocks.AIR.defaultBlockState()),"Committed retirement replays once");
    var outside=e.center().offset(22,1,0);l.setBlock(outside,before,2);
    h.assertTrue(!WorldJournal.place(l,UUID.randomUUID(),outside,before,Blocks.AIR.defaultBlockState()),"An arbitrary "+wood+" sign remains protected");
   }finally{SettlementData.get(l.getServer()).remove(e.settlement().id());}
  }h.succeed();
 }
 @GameTest(template="empty",batch="timber_sign_journal",timeoutTicks=200) public static void legacyPendingTimberPlaqueIntentGainsOnlyItsMissingOwnership(GameTestHelper h){
  var l=h.getLevel();var e=building(h,"birch");
  try{var b=e.settlement().buildings().iterator().next();var p=BuildingSigns.position(e,b);var before=l.getBlockState(p);var id=UUID.randomUUID();
   var old=new CompoundTag();old.putInt("schema",1);old.putUUID("id",id);old.putString("dimension",l.dimension().location().toString());old.putLong("pos",p.asLong());old.putString("kind","block");old.put("before",NbtUtils.writeBlockState(before));old.put("after",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
   var file=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-journal/"+id+".bin");NbtRecord.write(file,old);
   h.assertTrue(WorldJournal.place(l,id,p,before,Blocks.AIR.defaultBlockState()),"An exact unapplied legacy birch retirement resumes without replacing its receipt identity");
   var receipt=WorldJournal.inspectCommitted(l,id);h.assertTrue(receipt!=null&&receipt.getUUID("signBuilding").equals(b.id()),"The real building is recorded before mutation");
   var same=receipt.copy();same.remove("committed");same.remove("signBuilding");h.assertTrue(same.equals(old),"No target, states or operation identity were changed");
   h.assertTrue(WorldJournal.place(l,id,p,before,Blocks.AIR.defaultBlockState()),"The repaired legacy receipt replays safely");
  }finally{SettlementData.get(l.getServer()).remove(e.settlement().id());}h.succeed();
 }
 @GameTest(template="empty",batch="timber_sign_journal",timeoutTicks=200) public static void legacyTimberAuthorizationCannotRewriteAnotherIntent(GameTestHelper h){
  var l=h.getLevel();var e=building(h,"birch");
  try{var b=e.settlement().buildings().iterator().next();var p=BuildingSigns.position(e,b);var before=l.getBlockState(p);var id=UUID.randomUUID();
   var old=new CompoundTag();old.putInt("schema",1);old.putUUID("id",id);old.putString("dimension",l.dimension().location().toString());old.putLong("pos",p.east().asLong());old.putString("kind","block");old.put("before",NbtUtils.writeBlockState(before));old.put("after",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
   var file=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-journal/"+id+".bin");NbtRecord.write(file,old);boolean refused=false;
   try{WorldJournal.place(l,id,p,before,Blocks.AIR.defaultBlockState());}catch(IllegalStateException expected){refused=expected.getMessage().contains("blocked operation");}
   h.assertTrue(refused&&NbtRecord.read(file).equals(old),"Ownership repair cannot move an old intent to another target");
   h.assertTrue(l.getBlockState(p).equals(before),"The actual plaque remains untouched after identity refusal");
  }finally{SettlementData.get(l.getServer()).remove(e.settlement().id());}h.succeed();
 }
}
