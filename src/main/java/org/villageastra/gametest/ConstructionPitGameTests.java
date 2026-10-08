package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ConstructionPitGameTests {
 @GameTest(template="empty",batch="construction_pit",timeoutTicks=100)
 public static void unfinishedHouseFloorIsNotAnEscapePit(GameTestHelper h){check(h,true,false);}
 @GameTest(template="empty",batch="construction_pit",timeoutTicks=100)
 public static void unfundedSiteStillAllowsNaturalPitRecovery(GameTestHelper h){check(h,false,false);}
 @GameTest(template="empty",batch="construction_pit",timeoutTicks=100)
 public static void outsidePaidSiteStillAllowsNaturalPitRecovery(GameTestHelper h){check(h,true,true);}
 private static void check(GameTestHelper h,boolean funded,boolean outside){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(4,3,4));
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-1;y<=5;y++)l.setBlock(foot.offset(x,y,z),y<=2&&(x!=0||z!=0||y==-1)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),foot.offset(-32,0,0));SettlementData.get(l.getServer()).add(e);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);npc.setOnGround(true);
  h.assertTrue(PitEscapeGoal.escape(npc)!=null,"The same physical hollow is escapable without a construction site");
  var t=new CompoundTag();var id=UUID.randomUUID();t.putUUID("id",id);t.putUUID("project",id);t.putString("kind","building");t.putString("design","home");t.putInt("progress",352);t.putBoolean("funded",funded);t.putLong("origin",foot.offset(outside?30:-3,-4,-3).asLong());HallUpgradeGoal.store(l,s.id(),t);
  try{h.assertTrue((PitEscapeGoal.escape(npc)==null)==(funded&&!outside),"Only a funded unfinished architectural floor refuses the roof climb");}
  finally{HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());npc.discard();}h.succeed();
 }
}
