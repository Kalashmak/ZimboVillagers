package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-138 VI: the yard's machine, one operation a turn — drops into the chest first, then a feeder from the chest's feed, then the kennel's
 *  bin from the chest's meat, then a surplus beast given to a free wolf; without a wolf nothing is culled. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LivestockMachineGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void theYardMachineFillsBinsAndSendsAWolf(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,2,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int n=s.civilization().level()+1;n<=6;n++)s.civilization().completedHallUpgrade(n);
  var yard=new Settlement.Building(Settlement.childId(s.id(),"building/livestock"),"livestock",8,0,4);s.addBuilding(yard);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var spawned=new ArrayList<net.minecraft.world.entity.Entity>();
  try{
   for(int n=2;n<=6;n++)s.raiseBuildingLevel(yard.id(),n);yard=s.buildings().stream().filter(b->b.type().equals("livestock")).findFirst().orElseThrow();
   var o=BuildingPlacement.origin(e,yard);
   for(int x=-8;x<20;x++)for(int z=-2;z<28;z++){l.setBlock(o.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),2);l.setBlock(o.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<10;y++)l.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   for(var cell:BuildingPlacement.layout(e,yard,BuildingTiers.layoutId("livestock",6)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
   var chest=LogisticsRoutes.chest(l,e,yard);h.assertTrue(chest!=null,"The yard chest stands");
   chest.setItem(0,new ItemStack(Items.WHEAT,2));
   // Drops first.
   var egg=new ItemEntity(l,o.getX()+8.5,o.getY()+1,o.getZ()+8.5,new ItemStack(Items.EGG,3));l.addFreshEntity(egg);
   h.assertTrue(LivestockMachine.cycle(l,e,yard)&&LivestockMachine.lastReason.equals("gathered")&&LogisticsRoutes.count(chest,x->x.is(Items.EGG))==3,"Drops into the chest first: "+LivestockMachine.lastReason);
   // Then pen 1's feeder from the chest's wheat.
   var feeder=LivestockPens.at(e,yard,LivestockPens.pen(1).feeder());
   h.assertTrue(LivestockMachine.cycle(l,e,yard)&&l.getBlockState(feeder).getValue(FeederBlock.FEED)==1&&LogisticsRoutes.count(chest,x->x.is(Items.WHEAT))==0,"A feeder step from two wheat: "+LivestockMachine.lastReason);
   // Then the kennel's bin from the chest's meat.
   var k=Annexes.kind(VillageWolves.TYPE);var ko=Annexes.origin(e,yard,k).subtract(center);
   var kennel=new Settlement.Building(Settlement.childId(s.id(),"building/kennel_annex"),VillageWolves.TYPE,ko.getX(),ko.getY(),ko.getZ(),yard.rotation());s.addBuilding(kennel);s.linkAnnex(kennel.id(),yard.id());
   for(var cell:BuildingPlacement.layout(e,kennel,VillageWolves.TYPE).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
   var wolf=EntityType.WOLF.create(l);var lane=LivestockPens.at(e,yard,new BlockPos(8,1,12));wolf.moveTo(lane.getX()+.5,lane.getY(),lane.getZ()+.5);l.addFreshEntity(wolf);spawned.add(wolf);
   h.assertTrue(VillageWolves.enlist(l,e,wolf,kennel),"A kennel wolf");wolf.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l));
   chest.setItem(1,new ItemStack(Items.BEEF,2));
   h.assertTrue(LivestockMachine.cycle(l,e,yard)&&LivestockMachine.lastReason.equals("bin")&&LogisticsRoutes.count(LogisticsRoutes.chest(l,e,kennel),x->x.is(Items.BEEF))==1,"One piece into the bin: "+LivestockMachine.lastReason);
   // A pen above its cap: a free wolf is sent to cull.
   var p=LivestockPens.pen(1);
   for(int i=0;i<=LivestockPens.PEN_CAP;i++){var at=LivestockPens.at(e,yard,new BlockPos(p.x()+1+i%5,1,p.z()+1+(i/5)%5));var sheep=EntityType.SHEEP.create(l);sheep.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);l.addFreshEntity(sheep);LivestockPens.tag(sheep,s.id(),yard,p);spawned.add(sheep);}
   h.assertTrue(LivestockMachine.cycle(l,e,yard)&&LivestockMachine.lastReason.equals("cull 1")&&VillageWolves.cullOrder(wolf)!=null,"A wolf sent to cull: "+LivestockMachine.lastReason);
   VillageWolves.culled(wolf);wolf.discard();
   h.assertTrue(!LivestockMachine.cycle(l,e,yard)&&LivestockMachine.lastReason.equals("no wolves"),"No wolf, no cull: "+LivestockMachine.lastReason);
  }finally{for(var x:spawned)x.discard();SettlementData.get(l.getServer()).remove(s.id());try{java.nio.file.Files.deleteIfExists(VillageWolves.path(l,s.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}}
  h.succeed();
 }
}
