package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-138 IV: the wolves' registry — room only with a built kennel, four places, a wolf marked for its village and kennel, never taken
 *  from another village, and a dead wolf's place freed. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class VillageWolvesGameTests {
 /** A kennel wolf eats one piece of raw meat a day from the kennel's bin, never twice the same day, and the yard asks for the bin's meat. */
 @GameTest(template="empty",timeoutTicks=100) public static void aKennelWolfEatsOnePieceADay(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,2,4));var s=new Settlement(UUID.randomUUID());
  var yard=new Settlement.Building(Settlement.childId(s.id(),"building/livestock"),"livestock",0,0,0,0,4);s.addBuilding(yard);
  var kennel=new Settlement.Building(Settlement.childId(s.id(),"building/kennel_annex"),VillageWolves.TYPE,-5,0,0);s.addBuilding(kennel);s.linkAnnex(kennel.id(),yard.id());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var wolf=EntityType.WOLF.create(l);
  try{
   var at=LogisticsRoutes.position(e,kennel);l.setBlock(at,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var bin=LogisticsRoutes.chest(l,e,kennel);
   bin.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF,3));
   h.assertTrue(VillageWolves.wants(l,e,yard).isEmpty(),"No wolves yet: the bin asks for nothing");
   wolf.moveTo(at.getX()+.5,at.getY()+1,at.getZ()+.5);l.addFreshEntity(wolf);h.assertTrue(VillageWolves.enlist(l,e,wolf,kennel),"Enlisted");
   h.assertTrue(!VillageWolves.fed(l,wolf),"Not fed yet");
   h.assertTrue(VillageWolves.eat(l,e,wolf)&&bin.getItem(0).getCount()==2&&VillageWolves.fed(l,wolf),"One piece eaten: "+bin.getItem(0));
   h.assertTrue(VillageWolves.eat(l,e,wolf)&&bin.getItem(0).getCount()==2,"Not twice the same day");
   var want=VillageWolves.wants(l,e,yard);
   h.assertTrue(want.size()==1&&want.get(0).destination().equals(kennel.id())&&want.get(0).ingredient().test(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MUTTON)),"The yard asks meat for the bin: "+want);
   h.assertTrue(LogisticsRoutes.reserve(kennel,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF))==VillageWolves.MEAT_STOCK,"The bin keeps its meat");
   // AD-139: the fed kennel wolf is the village's free dog; sent with a courier it is taken, released it is free again.
   h.assertTrue(VillageDogs.provided()&&VillageDogs.available(l,e).equals(List.of(wolf.getUUID())),"The fed kennel wolf is a free dog: "+VillageDogs.available(l,e));
   var courier=VillageAstra.RESIDENT.get().create(l);courier.moveTo(at.getX()+3.5,at.getY()+1,at.getZ()+.5);l.addFreshEntity(courier);
   try{VillageDogs.follow(l,e,wolf.getUUID(),courier);
    h.assertTrue(courier.getUUID().equals(VillageWolves.courier(wolf))&&VillageDogs.available(l,e).isEmpty(),"With a courier it is not free");
    VillageDogs.release(l,e,wolf.getUUID());
    h.assertTrue(VillageWolves.courier(wolf)==null&&VillageDogs.available(l,e).size()==1,"Back, free again");
    wolf.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l)-2);
    h.assertTrue(VillageDogs.available(l,e).isEmpty(),"A hungry wolf pulls no cart");
    wolf.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l));
    // AD-141 (logistics): hitched to a cart it pulls it and is not free; sent to a place it goes; released, the cart is unhitched.
    var cart=new CartEntity(l,at.offset(2,1,0),s.id());l.addFreshEntity(cart);
    try{h.assertTrue(VillageDogs.harness(l,e,wolf.getUUID(),cart)&&wolf.getUUID().equals(cart.puller())&&VillageDogs.available(l,e).isEmpty(),"Hitched: the cart's puller, not free");
     var place=at.offset(5,1,0);h.assertTrue(VillageWolves.sendDog(l,e,wolf.getUUID(),place)&&place.equals(VillageWolves.sentTo(wolf)),"Sent to a place");
     h.assertTrue(VillageWolves.nearDog(l,wolf.getUUID(),wolf.blockPosition(),1)&&!VillageWolves.nearDog(l,wolf.getUUID(),place.offset(30,0,0),2),"Near: by distance");
     VillageDogs.release(l,e,wolf.getUUID());
     h.assertTrue(cart.puller()==null&&VillageWolves.sentTo(wolf)==null&&VillageDogs.available(l,e).size()==1,"Released: unhitched, no place, free again");
    }finally{cart.discard();}
   }finally{courier.discard();}
  }finally{wolf.discard();SettlementData.get(l.getServer()).remove(s.id());try{java.nio.file.Files.deleteIfExists(VillageWolves.path(l,s.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}}
  h.succeed();
 }
 /** WolfKennelGoal: every wolf of a full kennel has its own bed, and a wolf's day spots are the yard's lane and walks, never a pen. */
 @GameTest(template="empty",timeoutTicks=100) public static void kennelWolvesSleepApartAndKeepOutOfThePens(GameTestHelper h){
  var ids=new ArrayList<UUID>();for(int i=0;i<VillageWolves.CAPACITY;i++)ids.add(UUID.randomUUID());
  var beds=new HashSet<Integer>();for(var id:ids)beds.add(WolfKennelGoal.bed(ids,id));
  h.assertTrue(beds.size()==VillageWolves.CAPACITY&&WolfKennelGoal.BEDS.length>=VillageWolves.CAPACITY,"Four wolves, four beds: "+beds);
  var center=new BlockPos(2000,64,2000);var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,"minecraft:overworld",center);var r=new Random(7);
  for(int turn=0;turn<4;turn++){var yard=new Settlement.Building(UUID.randomUUID(),"livestock",0,0,0,turn,4);
   for(int i=0;i<400;i++){var spot=WolfKennelGoal.daySpot(e,yard,r);var local=BuildingPlacement.local(e,yard,spot);
    h.assertTrue(local.getX()>=0&&local.getX()<17&&local.getZ()>=0&&local.getZ()<25,"Turn "+turn+": a day spot on the yard's lot, not "+local);
    for(var p:LivestockPens.built(6))h.assertTrue(!LivestockPens.fenced(e,yard,p,spot),"Turn "+turn+": a day spot never in pen "+p.index()+" ("+local+")");}}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void aKennelHousesFourWolvesOfItsVillage(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,2,4));var s=new Settlement(UUID.randomUUID());
  var yard=new Settlement.Building(Settlement.childId(s.id(),"building/livestock"),"livestock",0,0,0,0,4);s.addBuilding(yard);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var other=new Settlement(UUID.randomUUID());var oe=new SettlementData.Entry(other,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(oe);
  var wolves=new ArrayList<Wolf>();
  try{
   h.assertTrue(VillageWolves.room(l,e)==0&&VillageWolves.kennel(e)==null,"No kennel, no room");
   var stray=EntityType.WOLF.create(l);stray.moveTo(center.getX()+.5,center.getY(),center.getZ()+.5);l.addFreshEntity(stray);wolves.add(stray);
   h.assertTrue(!VillageWolves.enlist(l,e,stray,yard),"Only a kennel takes a wolf, not the yard itself");
   var kennel=new Settlement.Building(Settlement.childId(s.id(),"building/kennel_annex"),VillageWolves.TYPE,-5,0,0);s.addBuilding(kennel);s.linkAnnex(kennel.id(),yard.id());
   h.assertTrue(VillageWolves.kennel(e)!=null&&VillageWolves.room(l,e)==VillageWolves.CAPACITY,"A built kennel: four places");
   for(int i=0;i<VillageWolves.CAPACITY;i++){var w=i==0?stray:EntityType.WOLF.create(l);if(i>0){w.moveTo(center.getX()+.5+i,center.getY(),center.getZ()+.5);l.addFreshEntity(w);wolves.add(w);}
    h.assertTrue(VillageWolves.enlist(l,e,w,kennel),"Wolf "+i+" enlisted");}
   h.assertTrue(VillageWolves.enlist(l,e,stray,kennel)&&VillageWolves.wolves(l,e).size()==VillageWolves.CAPACITY,"Enlisting again changes nothing");
   h.assertTrue(s.id().equals(VillageWolves.village(stray))&&kennel.id().equals(stray.getPersistentData().getUUID(VillageWolves.KENNEL))&&stray.isPersistenceRequired(),"Marked for its village and kennel, kept");
   var fifth=EntityType.WOLF.create(l);fifth.moveTo(center.getX()+.5,center.getY(),center.getZ()+2.5);l.addFreshEntity(fifth);wolves.add(fifth);
   h.assertTrue(VillageWolves.room(l,e)==0&&!VillageWolves.enlist(l,e,fifth,kennel),"A full kennel takes no fifth");
   var okennel=new Settlement.Building(Settlement.childId(other.id(),"building/kennel_annex"),VillageWolves.TYPE,-5,0,0);var oyard=new Settlement.Building(Settlement.childId(other.id(),"building/livestock"),"livestock",0,0,0,0,4);
   other.addBuilding(oyard);other.addBuilding(okennel);other.linkAnnex(okennel.id(),oyard.id());
   h.assertTrue(!VillageWolves.enlist(l,oe,stray,okennel),"Another village's wolf is not taken");
   stray.kill();
   h.assertTrue(VillageWolves.wolves(l,e).size()==VillageWolves.CAPACITY-1&&VillageWolves.room(l,e)==1,"A dead wolf's place is free");
  }finally{
   for(var w:wolves)w.discard();SettlementData.get(l.getServer()).remove(s.id());SettlementData.get(l.getServer()).remove(other.id());
   for(var id:List.of(s.id(),other.id()))try{java.nio.file.Files.deleteIfExists(VillageWolves.path(l,id));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  }
  h.succeed();
 }
}
