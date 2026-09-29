package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.villageastra.VillageAstra;
import org.villageastra.server.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MayorSurveyGameTests {
 @GameTest(template="empty") public static void roadStylesAlwaysHaveFullWidth(GameTestHelper h){
  for(int style=0;style<3;style++){var roads=MayorSurvey.road(BlockPos.ZERO,new BlockPos(12,0,7),style,p->0);var covered=new HashSet<BlockPos>();for(var p:roads.keySet()){boolean full=true;for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(!roads.containsKey(p.offset(x,0,z)))full=false;if(full)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)covered.add(p.offset(x,0,z));}h.assertTrue(covered.equals(roads.keySet()),"Every style retains width through bends and endpoints");}
  h.assertTrue(MayorSurvey.road(BlockPos.ZERO,new BlockPos(97,0,0),0,p->0).isEmpty(),"Bounded route rejects oversized request");h.succeed();
 }
 @GameTest(template="empty") public static void serverRejectsVisitorsStaleOfficeAndForgedSelection(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));org.villageastra.world.StarterVillage.create(l,origin);
  var p=FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"MayorSurveyTest"));p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(VillageAstra.MAYOR_SHOVEL.get()));p.setPos(origin.getX(),origin.getY()+1,origin.getZ());
  h.assertTrue(!MayorSurvey.mark(p,origin,false),"Visitors holding copied tools have no authority");var e=SettlementData.get(l.getServer()).entries().stream().filter(v->v.center().equals(origin)).findFirst().orElseThrow();e.settlement().appointPlayerMayor(p.getUUID());
  h.assertTrue(MayorSurvey.mark(p,origin,false),"Live mayor can inspect a building projection even at an obstructed site");var tag=new net.minecraft.nbt.CompoundTag();MayorSurvey.addView(p,tag);h.assertTrue(tag.getBoolean("survey")&&tag.getInt("conflicts")>0,"Protection conflicts are reported in the real projection");
  h.assertTrue(!MayorSurvey.choose(p,UUID.randomUUID(),"home",0,0),"Forged proposal rejected");h.assertTrue(l.getBlockState(origin).is(Blocks.COBBLESTONE),"Projection never modifies the world");
  h.assertTrue(!MayorSurvey.mark(p,origin.offset(30,0,0),false),"Unreachable click rejected");e.settlement().governance().appointNpc();h.assertTrue(!MayorSurvey.choose(p,tag.getUUID("id"),"home",1,0),"Office change invalidates the old palette");
  h.assertTrue(MayorSurvey.mark(p,origin,false)==false,"A former mayor cannot mark again");
  MayorSurvey.revalidate(l.getServer());h.assertTrue(!MayorSurvey.selecting(p.getUUID()),"The once-a-second check drops the palette of a player who lost the office");h.succeed();
 }
 @GameTest(template="empty") public static void sectorMathIncludesNegativeWorldCoordinates(GameTestHelper h){
  h.assertTrue(org.villageastra.world.SettlementSectors.SIZE==1000,"One kilometre sector size");int[][] cases={{-1001,-2},{-1000,-1},{-1,-1},{0,0},{999,0},{1000,1}};for(var c:cases)h.assertTrue(org.villageastra.world.SettlementSectors.sector(c[0])==c[1],"Exact positive and negative sector boundary");h.succeed();
 }
}
