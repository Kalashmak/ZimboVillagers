package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-035: breeding uses real feed, shearing gives wool and wears shears, pen drops are stored. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LivestockGameTests {
 private record Yard(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building building,OwnedChestEntity chest){}
 private static Yard yard(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  var b=new Settlement.Building(Settlement.childId(s.id(),"building/livestock"),"livestock",0,0,0);s.addBuilding(b);
  for(int x=-6;x<22;x++)for(int z=-6;z<28;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Yard(l,s,e,b,LogisticsRoutes.chest(l,e,b));
 }
 /** An owned animal of pen 1 standing inside it (pen 1: fence x0..6, z8..14; inside x1..5, z9..13). */
 private static <T extends Animal> T animal(Yard y,EntityType<T> type,int dx){var a=type.create(y.l);a.moveTo(y.e.center().getX()+dx+.5,y.e.center().getY()+1,y.e.center().getZ()+11.5,0,0);a.setNoAi(true);
  LivestockPens.tag(a,y.s.id(),y.building,LivestockPens.pen(1));y.l.addFreshEntity(a);return a;}
 /** AD-138: pen 1's feeder standing where the yard's plan puts it. */
 private static BlockPos feeder(Yard y){var at=LivestockPens.at(y.e,y.building,LivestockPens.pen(1).feeder());y.l.setBlock(at,VillageAstra.FEEDER.get().defaultBlockState(),2);return at;}
 /** AD-138: breeding spends one step of the pen's feeder (two real items the keeper put in it), never the chest directly. */
 @GameTest(template="empty",timeoutTicks=100) public static void breedingSpendsRealFeed(GameTestHelper h){
  var y=yard(h);var at=feeder(y);var a=animal(y,EntityType.SHEEP,2);var b=animal(y,EntityType.SHEEP,4);var p=LivestockPens.pen(1);
  h.assertTrue(!LivestockGoal.breed(y.l,y.e,y.building,p,100),"An empty feeder, no breeding");
  h.assertTrue(!LivestockGoal.fill(y.l,y.e,y.building,p,110),"No wheat in the chest, nothing to fill");
  y.chest.setItem(0,new ItemStack(Items.WHEAT,3));
  h.assertTrue(LivestockGoal.fill(y.l,y.e,y.building,p,120)&&y.chest.countItem(Items.WHEAT)==1&&y.l.getBlockState(at).getValue(FeederBlock.FEED)==1,"Two real wheat fill one step");
  h.assertTrue(LivestockGoal.breed(y.l,y.e,y.building,p,200)&&a.isInLove()&&b.isInLove()&&y.chest.countItem(Items.WHEAT)==1&&y.l.getBlockState(at).getValue(FeederBlock.FEED)==0,"One step puts both sheep in love; the chest is not touched");
  a.discard();b.discard();h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void shearingStoresWoolAndWearsShears(GameTestHelper h){
  var y=yard(h);var sheep=animal(y,EntityType.SHEEP,3);
  h.assertTrue(LivestockGoal.shear(y.l,y.e,y.building,sheep,100)==0&&!sheep.isSheared(),"No shears, no shearing");
  y.chest.setItem(0,new ItemStack(Items.SHEARS));int wool=LivestockGoal.shear(y.l,y.e,y.building,sheep,200);
  int stored=0;for(int i=0;i<y.chest.getContainerSize();i++)if(y.chest.getItem(i).is(net.minecraft.tags.ItemTags.WOOL))stored+=y.chest.getItem(i).getCount();
  int wear=0;for(int i=0;i<y.chest.getContainerSize();i++)if(y.chest.getItem(i).is(Items.SHEARS))wear=y.chest.getItem(i).getDamageValue();
  h.assertTrue(wool>=1&&wool<=3&&stored==wool&&sheep.isSheared()&&wear==1,"Wool in chest ("+stored+"), sheep sheared, shears worn "+wear);
  h.assertTrue(LivestockGoal.shear(y.l,y.e,y.building,sheep,300)==0,"A sheared sheep gives nothing until wool regrows");
  sheep.discard();h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void penDropsAreGatheredIntoTheChest(GameTestHelper h){
  var y=yard(h);var pos=LogisticsRoutes.position(y.e,y.building);
  var egg=new ItemEntity(y.l,pos.getX()+3.5,pos.getY(),pos.getZ()+2.5,new ItemStack(Items.EGG,2));y.l.addFreshEntity(egg);
  var far=new ItemEntity(y.l,pos.getX()+30.5,pos.getY(),pos.getZ(),new ItemStack(Items.EGG,1));y.l.addFreshEntity(far);
  h.assertTrue(LivestockGoal.gather(y.l,y.e,y.building,100)==2&&y.chest.countItem(Items.EGG)==2&&!egg.isAlive()&&far.isAlive(),"Only pen drops are moved, exactly once");
  far.discard();h.succeed();
 }
}
