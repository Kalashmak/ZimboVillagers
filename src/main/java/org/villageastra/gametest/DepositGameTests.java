package org.villageastra.gametest;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.server.OwnDeposits;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DepositGameTests {
 @GameTest(template="empty") public static void stolenGoodsCannotBecomePersonalContributions(GameTestHelper h){
  var d=new OwnDeposits();var v=UUID.randomUUID();var p=UUID.randomUUID();var bread=new ItemStack(Items.BREAD);
  h.assertTrue(d.withdraw(v,p,bread,4)==4,"Village goods are attributed");d=OwnDeposits.load(d.save(new CompoundTag()));
  h.assertTrue(d.deposit(v,p,bread,4)==0,"Returning village goods earns no donation reward");
  h.assertTrue(d.withdraw(v,p,bread,4)==4,"Returning stolen goods does not establish personal ownership");h.succeed();
 }
 @GameTest(template="empty") public static void ownReturnsSurviveReloadAndCannotBecomeNewDonations(GameTestHelper h){
  var d=new OwnDeposits();var v=UUID.randomUUID();var p=UUID.randomUUID();var other=UUID.randomUUID();var sand=new ItemStack(Items.SAND);
  h.assertTrue(d.deposit(v,p,sand,64)==64,"First contribution is new");d=OwnDeposits.load(d.save(new CompoundTag()));
  h.assertTrue(d.withdraw(v,other,sand,8)==8,"Another player does not own the contribution");
  h.assertTrue(d.withdraw(v,p,sand,32)==0,"Owner may retrieve half without theft");
  d=OwnDeposits.load(d.save(new CompoundTag()));h.assertTrue(d.deposit(v,p,sand,32)==0,"Reload does not turn a returned stack into a new gift");
  h.assertTrue(d.withdraw(v,p,sand,70)==6,"Only excess over 64 is village property");h.succeed();
 }
 @GameTest(template="empty") public static void contributionOwnershipIncludesItemTagsAndVillage(GameTestHelper h){
  var d=new OwnDeposits();var v=UUID.randomUUID();var p=UUID.randomUUID();var named=new ItemStack(Items.DIAMOND_PICKAXE);named.setHoverName(net.minecraft.network.chat.Component.literal("Mine"));d.deposit(v,p,named,1);
  h.assertTrue(d.withdraw(v,p,new ItemStack(Items.DIAMOND_PICKAXE),1)==1,"A different tool cannot consume the named tool credit");
  h.assertTrue(d.withdraw(UUID.randomUUID(),p,named,1)==1,"Village accounts are independent");h.assertTrue(d.withdraw(v,p,named,1)==0,"Original tool is returned without a penalty");h.succeed();
 }
}
