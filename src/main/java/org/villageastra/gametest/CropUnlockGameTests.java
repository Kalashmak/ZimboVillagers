package org.villageastra.gametest;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-093: a village grows only what it has got hold of — wheat and the oak from the start, every other crop and sapling after its quest. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CropUnlockGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void aCropIsPlantedOnlyAfterTheVillageHasGotHoldOfIt(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var e=SettlementData.get(l.getServer()).entry(s.id());
  var farm=s.buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();
  var mayor=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"CropMayor"));mayor.setPos(origin.getX(),origin.getY()+1,origin.getZ());
  s.appointPlayerMayor(mayor.getUUID());long epoch=s.governance().epoch();
  h.assertTrue(CropUnlocks.unlocked(l,e,"minecraft:wheat_seeds")&&CropUnlocks.unlocked(l,e,"minecraft:oak_sapling"),"Wheat and the oak are known from the first day");
  h.assertTrue(!CropUnlocks.unlocked(l,e,"minecraft:carrot")&&CropUnlocks.locked(l,e).contains("minecraft:carrot"),"Carrots wait for their quest");
  h.assertTrue(CropUnlocks.locked(l,e).contains("minecraft:birch_sapling")&&!CropUnlocks.locked(l,e).contains("minecraft:oak_sapling"),"So do the saplings the forester does not know yet");
  h.assertTrue(!FarmPolicies.order(mayor,s.id(),farm.id(),epoch,s.governance().revision(),"carrot"),"The mayor cannot order a crop the village has never had");
  h.assertTrue(FarmPolicies.get(l.getServer()).crop(s.id(),farm.id())==FarmCrops.WHEAT,"And the farm keeps its wheat");
  // The quest brings carrots to the hall: from then on the farm may grow them.
  h.assertTrue(CropUnlocks.unlock(l,e,"minecraft:carrot","quest"),"The quest opens carrots");
  h.assertTrue(!CropUnlocks.unlock(l,e,"minecraft:carrot","quest"),"Opening it twice changes nothing");
  h.assertTrue(!CropUnlocks.locked(l,e).contains("minecraft:carrot"),"Carrots are off the list of what the board may still ask for");
  h.assertTrue(FarmPolicies.order(mayor,s.id(),farm.id(),epoch,s.governance().revision(),"carrot"),"Now the mayor orders carrots");
  h.assertTrue(FarmPolicies.get(l.getServer()).crop(s.id(),farm.id())==FarmCrops.CARROT,"And the farm grows them");
  boolean refused=false;try{CropUnlocks.unlock(l,e,"minecraft:diamond","quest");}catch(IllegalArgumentException ex){refused=true;}
  h.assertTrue(refused,"Only crops and saplings of the village can be opened");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aCropAFarmGrewBeforeTheRuleStaysOpenedAfterTheFarmSwitchesAway(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var e=SettlementData.get(l.getServer()).entry(s.id());
  var farm=s.buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();
  var mayor=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"LegacyMayor"));mayor.setPos(origin.getX(),origin.getY()+1,origin.getZ());
  s.appointPlayerMayor(mayor.getUUID());long epoch=s.governance().epoch();
  try{
   // A save from before AD-093: the farm already grows carrots and the village has no record of opened crops at all.
   CropUnlocks.unlock(l,e,"minecraft:carrot","quest");
   h.assertTrue(FarmPolicies.order(mayor,s.id(),farm.id(),epoch,s.governance().revision(),"carrot"),"The fixture farm grows carrots");
   try{java.nio.file.Files.deleteIfExists(l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-unlocks/"+s.id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
   h.assertTrue(CropUnlocks.unlocked(l,e,"minecraft:carrot"),"The crop the farm already grows counts as opened");
   h.assertTrue(FarmPolicies.order(mayor,s.id(),farm.id(),epoch,s.governance().revision(),"wheat")&&FarmPolicies.get(l.getServer()).crop(s.id(),farm.id())==FarmCrops.WHEAT,"The farm switches back to wheat");
   h.assertTrue(CropUnlocks.unlocked(l,e,"minecraft:carrot")&&!CropUnlocks.locked(l,e).contains("minecraft:carrot"),"Carrots stay opened after the farm switched away");
   h.assertTrue(FarmPolicies.order(mayor,s.id(),farm.id(),epoch,s.governance().revision(),"carrot"),"So the mayor may order them again");
  }finally{for(var r:s.residents())if(l.getEntity(r.id())!=null)l.getEntity(r.id()).discard();SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theHutPrefersOnlyAKindOfTreeTheVillageHasOpened(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var e=SettlementData.get(l.getServer()).entry(s.id());
  var sawmill=s.buildings().stream().filter(b->b.type().equals("forester")).findFirst().orElseThrow();
  var mayor=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"NurseryMayor"));mayor.setPos(origin.getX(),origin.getY()+1,origin.getZ());
  s.appointPlayerMayor(mayor.getUUID());
  try{
   var policies=ForestPolicies.get(l.getServer());
   h.assertTrue(policies.preferred(s.id(),sawmill.id()).equals("minecraft:oak_sapling"),"The hut starts with the oak");
   var card=BuildingCards.card(l,e,sawmill).getCompound("forest");
   h.assertTrue(card.getString("preferred").equals("minecraft:oak_sapling")&&card.getInt("kinds")==1,"The card shows the oak and one opened kind: "+card.getInt("kinds"));
   h.assertTrue(ForestPolicies.orderSpecies(mayor,s.id(),sawmill.id(),s.governance().epoch(),s.governance().revision()).equals("locked"),"With only the oak opened there is nothing else to plant");
   CropUnlocks.unlock(l,e,"minecraft:spruce_sapling","quest");
   h.assertTrue(BuildingCards.card(l,e,sawmill).getCompound("forest").getInt("kinds")==2,"The spruce's quest adds a kind to choose from");
   h.assertTrue(ForestPolicies.orderSpecies(mayor,s.id(),sawmill.id(),s.governance().epoch(),s.governance().revision()).isEmpty(),"The mayor picks the next kind");
   h.assertTrue(policies.preferred(s.id(),sawmill.id()).equals("minecraft:spruce_sapling"),"The birch it skipped is still locked, so the spruce comes next: "+policies.preferred(s.id(),sawmill.id()));
   h.assertTrue(ForestPolicies.orderSpecies(mayor,s.id(),sawmill.id(),s.governance().epoch(),s.governance().revision()).isEmpty()
     &&policies.preferred(s.id(),sawmill.id()).equals("minecraft:oak_sapling"),"And round again to the oak");
   h.assertTrue(ForestPolicies.orderSpecies(mayor,s.id(),sawmill.id(),s.governance().epoch()+1,s.governance().revision()).equals("mayor"),"An order from an old term is refused");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
